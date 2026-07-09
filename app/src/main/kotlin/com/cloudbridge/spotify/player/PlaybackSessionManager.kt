package com.cloudbridge.spotify.player

import com.cloudbridge.spotify.auth.GlobalRateLimitException
import com.cloudbridge.spotify.network.SpotifyApiService
import com.cloudbridge.spotify.network.model.SpotifyDevice
import com.cloudbridge.spotify.network.model.TransferPlaybackRequest
import com.cloudbridge.spotify.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicLong

/**
 * Single owner of Spotify Connect session health.
 *
 * Responsibilities:
 * - Maintain [connectionState] (Disconnected → Discovering → Ready / Degraded / Offline)
 * - Resolve [PlaybackTarget] to a live device_id
 * - Structured reconnect: discover → select → optional transfer → verify
 * - [establishSession]: full wake ladder when phone is not advertising
 * - Gate playback commands so they only fire when Ready (or after a bounded reconnect)
 * - Persist last successful Connect device for blind transfer
 *
 * UI / ViewModel must observe [connectionState] + [establishPhase] and call
 * [executeCommand] / [establishSession] rather than inventing connectivity flags.
 */
class PlaybackSessionManager(
    private val api: SpotifyApiService,
    private val deviceManager: DeviceManager,
    private val recoveryStrategy: RecoveryStrategy = NoOpRecoveryStrategy,
    private val lastKnownStore: LastKnownDeviceStore = InMemoryLastKnownDeviceStore(),
    private val companionWake: CompanionWakeStrategy = NoOpCompanionWake,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val TAG = "PlaybackSession"
        private const val HEALTH_POLL_MS = 12_000L
        private const val RECONNECT_COOLDOWN_MS = 2_500L
        private const val BT_SETTLE_DELAY_MS = 2_000L
        private const val AVRCP_SETTLE_MS = 2_000L
        private const val VERIFY_RETRY_DELAY_MS = 400L
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _playbackTarget = MutableStateFlow<PlaybackTarget>(PlaybackTarget.Auto)
    val playbackTarget: StateFlow<PlaybackTarget> = _playbackTarget.asStateFlow()

    private val _establishPhase = MutableStateFlow(EstablishPhase.Idle)
    /** Progressive wake steps for UI banners (Looking for phone… / Bluetooth wake…). */
    val establishPhase: StateFlow<EstablishPhase> = _establishPhase.asStateFlow()

    private val wakeCoordinator = DeviceWakeCoordinator(
        api = api,
        lastKnownStore = lastKnownStore,
        avrcp = recoveryStrategy,
        companion = companionWake,
        onPhase = { phase -> _establishPhase.value = phase },
        clock = clock
    )

    private val reconnectMutex = Mutex()
    private val establishMutex = Mutex()
    private val lastReconnectAttemptAt = AtomicLong(0L)
    private var healthJob: Job? = null
    private var establishJob: Job? = null

    // ── Target preference ────────────────────────────────────────────

    fun setPlaybackTarget(target: PlaybackTarget) {
        AppLogger.i(TAG, "Playback target set: $target")
        _playbackTarget.value = target
        when (target) {
            is PlaybackTarget.Specific -> deviceManager.lockedDeviceId = target.deviceId
            else -> deviceManager.lockedDeviceId = null
        }
    }

    /**
     * Load target preference from persisted Settings lock (legacy).
     * Specific lock → [PlaybackTarget.Specific]; otherwise Auto.
     */
    fun applyLockedDevicePreference(deviceId: String?, deviceName: String?) {
        if (!deviceId.isNullOrBlank()) {
            setPlaybackTarget(
                PlaybackTarget.Specific(
                    deviceId = deviceId,
                    deviceName = deviceName?.ifBlank { "Locked device" } ?: "Locked device"
                )
            )
        } else if (_playbackTarget.value is PlaybackTarget.Specific) {
            setPlaybackTarget(PlaybackTarget.Auto)
        }
    }

    // ── Lifecycle hooks ──────────────────────────────────────────────

    fun startHealthLoop() {
        if (healthJob?.isActive == true) return
        healthJob = scope.launch {
            while (isActive) {
                delay(HEALTH_POLL_MS)
                when (val state = _connectionState.value) {
                    is ConnectionState.Ready -> if (!state.verified) reconnect(force = false)
                    is ConnectionState.Degraded,
                    is ConnectionState.Disconnected -> reconnect(force = false)
                    is ConnectionState.Offline -> reconnect(force = false)
                    is ConnectionState.Discovering -> Unit
                }
            }
        }
        AppLogger.d(TAG, "Session health loop started")
    }

    fun stopHealthLoop() {
        healthJob?.cancel()
        healthJob = null
    }

    fun clearSession() {
        stopHealthLoop()
        establishJob?.cancel()
        establishJob = null
        _establishPhase.value = EstablishPhase.Idle
        _connectionState.value = ConnectionState.Disconnected
        AppLogger.i(TAG, "Session cleared")
    }

    /**
     * Trigger full session establish after Bluetooth ACL connect. Settles briefly so
     * Spotify Connect has time to re-advertise the phone.
     */
    fun onBluetoothConnected() {
        scope.launch {
            AppLogger.i(TAG, "BT connected — scheduling establish after settle")
            delay(BT_SETTLE_DELAY_MS)
            establishSession(force = true)
        }
    }

    fun onForegrounded() {
        scope.launch {
            when (val state = _connectionState.value) {
                is ConnectionState.Ready -> if (!state.verified) establishSession(force = false)
                else -> establishSession(force = false)
            }
        }
    }

    /**
     * Full session establish: discover, then wake ladder if phone is missing/asleep.
     * Prefer this over [reconnect] on cold start, BT connect, and user foreground.
     */
    fun establishSessionAsync(force: Boolean = true) {
        if (establishJob?.isActive == true && !force) return
        establishJob?.cancel()
        establishJob = scope.launch {
            establishSession(force = force)
        }
    }

    suspend fun establishSession(force: Boolean = true): ConnectionState = establishMutex.withLock {
        AppLogger.i(TAG, "establishSession(force=$force)")
        // Warm durable last-known into RAM before first discover.
        lastKnownStore.load()?.let { last ->
            deviceManager.registerActiveDevice(last.deviceId, last.deviceName)
        }

        _establishPhase.value = EstablishPhase.LookingForPhone
        var state = reconnect(force = force)

        if (state is ConnectionState.Ready && state.verified) {
            _establishPhase.value = EstablishPhase.Idle
            return state
        }
        if (state is ConnectionState.Offline) {
            _establishPhase.value = EstablishPhase.Idle
            return state
        }

        // Need wake ladder: empty devices, asleep, unverified ready, or command-unready degraded.
        val needsWake = when (state) {
            is ConnectionState.Ready -> !state.verified
            is ConnectionState.Degraded -> state.reason == DegradedReason.NoDevices ||
                state.reason == DegradedReason.DeviceAsleep
            is ConnectionState.Disconnected, is ConnectionState.Discovering -> true
            is ConnectionState.Offline -> false
        }

        if (!needsWake) {
            _establishPhase.value = EstablishPhase.Idle
            return state
        }

        wakeCoordinator.runWakeLadder(
            phoneOriented = isPhoneOriented(_playbackTarget.value),
            rediscover = {
                val s = reconnect(force = true)
                state = s
                s is ConnectionState.Ready && (s.verified || s.deviceId.isNotBlank())
            }
        )

        _establishPhase.value = EstablishPhase.Idle
        // Prefer verified Ready; otherwise keep last reconnect state.
        return _connectionState.value
    }

    // ── Reconnect pipeline ───────────────────────────────────────────

    /**
     * Idempotent reconnect. Concurrent callers share one flight via [reconnectMutex].
     * Respects a short cooldown unless [force] is true.
     */
    suspend fun reconnect(force: Boolean = false): ConnectionState = reconnectMutex.withLock {
        val now = clock()
        if (!force && now - lastReconnectAttemptAt.get() < RECONNECT_COOLDOWN_MS) {
            AppLogger.d(TAG, "Reconnect skipped (cooldown); current=${_connectionState.value}")
            return _connectionState.value
        }
        lastReconnectAttemptAt.set(now)

        val previous = _connectionState.value
        if (previous !is ConnectionState.Discovering) {
            transition(ConnectionState.Discovering)
        }

        return try {
            val devices = fetchDevices()
            val selected = selectDevice(devices, _playbackTarget.value)

            if (selected == null) {
                val durable = lastKnownStore.peek() ?: lastKnownStore.load()
                val remembered = durable?.deviceId ?: deviceManager.getCachedDeviceId()
                val rememberedName = durable?.deviceName ?: deviceManager.getCachedDeviceName()
                val rememberedType = durable?.deviceType ?: "Unknown"
                if (!remembered.isNullOrBlank()) {
                    // Blind spot: keep remembered target as unverified Ready so commands can still try.
                    deviceManager.registerActiveDevice(remembered, rememberedName ?: "Remembered device")
                    val ready = ConnectionState.Ready(
                        deviceId = remembered,
                        deviceName = rememberedName ?: "Remembered device",
                        deviceType = rememberedType,
                        verified = false
                    )
                    transition(ready)
                    return ready
                }
                // Phone-oriented + empty list after we previously had a phone → asleep, not "no devices"
                val degraded = if (isPhoneOriented(_playbackTarget.value) &&
                    previous is ConnectionState.Ready &&
                    previous.deviceType.equals("Smartphone", ignoreCase = true)
                ) {
                    ConnectionState.Degraded(
                        reason = DegradedReason.DeviceAsleep,
                        deviceId = previous.deviceId,
                        deviceName = previous.deviceName
                    )
                } else {
                    ConnectionState.Degraded(DegradedReason.NoDevices)
                }
                transition(degraded)
                return degraded
            }

            if (selected.isRestricted) {
                val degraded = ConnectionState.Degraded(
                    reason = DegradedReason.RestrictedDevice,
                    deviceId = selected.id,
                    deviceName = selected.name
                )
                transition(degraded)
                return degraded
            }

            val deviceId = selected.id!!
            deviceManager.registerActiveDevice(deviceId, selected.name)

            val active = devices.find { it.isActive }
            val needsTransfer = active == null || active.id != selected.id
            if (needsTransfer) {
                AppLogger.i(TAG, "Transferring playback to ${selected.name} ($deviceId)")
                try {
                    api.transferPlayback(
                        TransferPlaybackRequest(deviceIds = listOf(deviceId), play = false)
                    )
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Transfer failed (non-fatal): ${e.message}")
                }
            }

            val verified = verifyPlayerDevice(deviceId)
            if (!verified && active != null && active.id != selected.id && !needsTransfer) {
                val wrong = ConnectionState.Degraded(
                    reason = DegradedReason.WrongDevice,
                    deviceId = active.id,
                    deviceName = active.name
                )
                transition(wrong)
                return wrong
            }

            // Listed phone that is not active → treat as asleep for Phone/Auto when unverified
            if (!verified &&
                isPhoneOriented(_playbackTarget.value) &&
                selected.type.equals("Smartphone", ignoreCase = true) &&
                !selected.isActive
            ) {
                val asleep = ConnectionState.Degraded(
                    reason = DegradedReason.DeviceAsleep,
                    deviceId = deviceId,
                    deviceName = selected.name
                )
                transition(asleep)
                return asleep
            }

            val ready = ConnectionState.Ready(
                deviceId = deviceId,
                deviceName = selected.name,
                deviceType = selected.type,
                verified = verified
            )
            transition(ready)
            if (verified) persistLastKnown(deviceId, selected.name, selected.type)
            ready
        } catch (e: GlobalRateLimitException) {
            val offline = ConnectionState.Offline(OfflineReason.RateLimited)
            transition(offline)
            offline
        } catch (e: UnknownHostException) {
            val offline = ConnectionState.Offline(OfflineReason.NoNetwork)
            transition(offline)
            offline
        } catch (e: SocketTimeoutException) {
            val offline = ConnectionState.Offline(OfflineReason.SpotifyUnreachable)
            transition(offline)
            offline
        } catch (e: IOException) {
            val offline = ConnectionState.Offline(OfflineReason.SpotifyUnreachable)
            transition(offline)
            offline
        } catch (e: HttpException) {
            val offline = when (e.code()) {
                401, 403 -> ConnectionState.Offline(OfflineReason.AuthExpired)
                else -> ConnectionState.Offline(OfflineReason.SpotifyUnreachable)
            }
            transition(offline)
            offline
        } catch (e: Exception) {
            AppLogger.e(TAG, "Reconnect failed: ${e.message}", e)
            val degraded = ConnectionState.Degraded(DegradedReason.NoDevices)
            transition(degraded)
            degraded
        }
    }

    /**
     * Ensure session is Ready before a command. Runs reconnect if needed.
     * @return device_id when Ready, null otherwise
     */
    suspend fun ensureReady(): String? {
        when (val state = _connectionState.value) {
            is ConnectionState.Ready -> return state.deviceId
            is ConnectionState.Discovering -> reconnect(force = false)
            else -> reconnect(force = false)
        }
        return (_connectionState.value as? ConnectionState.Ready)?.deviceId
            ?: (_connectionState.value as? ConnectionState.Degraded)?.deviceId
    }

    // ── Command gate ─────────────────────────────────────────────────

    /**
     * Execute a playback API command with session gating.
     *
     * Flow:
     * 1. ensureReady()
     * 2. run command(deviceId)
     * 3. on [PlaybackCommandResult.Failure.Kind.Forbidden] → Degraded, **no** AVRCP
     * 4. on other failure → structured recovery (AVRCP only for DeviceAsleep) + one retry
     * 5. on IOException → Offline, no AVRCP
     */
    suspend fun executeCommand(
        commandName: String,
        command: suspend (deviceId: String?) -> PlaybackCommandResult
    ): CommandResult {
        return try {
            var deviceId = ensureReady()
            if (deviceId == null) {
                AppLogger.w(TAG, "$commandName blocked: session not Ready (${_connectionState.value})")
                val first = command(null)
                if (first is PlaybackCommandResult.Success) {
                    reconnect(force = true)
                    return CommandResult.Success
                }
                val failedState = when (val s = _connectionState.value) {
                    is ConnectionState.Degraded -> s
                    is ConnectionState.Offline -> s
                    else -> ConnectionState.Degraded(DegradedReason.NoDevices)
                }
                transition(failedState)
                return CommandResult.Failed(_connectionState.value)
            }

            val first = command(deviceId)
            if (first is PlaybackCommandResult.Success) {
                markReadyFromCommand(deviceId)
                return CommandResult.Success
            }

            // 403: Spotify rejected the command (Premium/policy/restricted). Rediscovery
            // and AVRCP cannot fix that — fail with a clear degraded reason.
            if (first is PlaybackCommandResult.Failure &&
                first.kind == PlaybackCommandResult.Failure.Kind.Forbidden
            ) {
                AppLogger.w(TAG, "$commandName forbidden (403); skipping AVRCP recovery")
                val degraded = ConnectionState.Degraded(
                    reason = DegradedReason.CommandFailed,
                    deviceId = deviceId,
                    deviceName = (_connectionState.value as? ConnectionState.Ready)?.deviceName
                        ?: (_connectionState.value as? ConnectionState.Degraded)?.deviceName
                )
                transition(degraded)
                return CommandResult.Failed(degraded)
            }

            AppLogger.w(TAG, "$commandName failed against $deviceId (${(first as? PlaybackCommandResult.Failure)?.kind}); running recovery")
            val recoveredId = recoverAfterCommandFailure(deviceId)
            if (recoveredId != null) {
                val retry = command(recoveredId)
                if (retry is PlaybackCommandResult.Success) {
                    markReadyFromCommand(recoveredId)
                    return CommandResult.Success
                }
                if (retry is PlaybackCommandResult.Failure &&
                    retry.kind == PlaybackCommandResult.Failure.Kind.Forbidden
                ) {
                    AppLogger.w(TAG, "$commandName forbidden on retry; no AVRCP")
                    val degraded = ConnectionState.Degraded(
                        reason = DegradedReason.CommandFailed,
                        deviceId = recoveredId,
                        deviceName = (_connectionState.value as? ConnectionState.Ready)?.deviceName
                    )
                    transition(degraded)
                    return CommandResult.Failed(degraded)
                }
            }

            AppLogger.w(TAG, "$commandName final fallback without device_id")
            val fallback = command(null)
            if (fallback is PlaybackCommandResult.Success) {
                reconnect(force = true)
                return CommandResult.Success
            }

            val degraded = ConnectionState.Degraded(
                reason = DegradedReason.CommandFailed,
                deviceId = deviceId,
                deviceName = (_connectionState.value as? ConnectionState.Ready)?.deviceName
                    ?: (_connectionState.value as? ConnectionState.Degraded)?.deviceName
            )
            transition(degraded)
            CommandResult.Failed(degraded)
        } catch (e: GlobalRateLimitException) {
            val offline = ConnectionState.Offline(OfflineReason.RateLimited)
            transition(offline)
            CommandResult.Failed(offline)
        } catch (e: IOException) {
            AppLogger.w(TAG, "$commandName network failure; no AVRCP: ${e.message}")
            val offline = ConnectionState.Offline(
                if (e is UnknownHostException) OfflineReason.NoNetwork
                else OfflineReason.SpotifyUnreachable
            )
            transition(offline)
            CommandResult.Failed(offline)
        }
    }

    /**
     * Observe an active device reported by metadata sync (GET /me/player).
     * Updates Ready state without a full reconnect.
     */
    fun observeActiveDevice(id: String, name: String, type: String = "Unknown") {
        if (id.isBlank()) return
        deviceManager.registerActiveDevice(id, name)
        val current = _connectionState.value
        if (current is ConnectionState.Offline) return
        transition(
            ConnectionState.Ready(
                deviceId = id,
                deviceName = name.ifBlank { "Active device" },
                deviceType = type,
                verified = true
            )
        )
        persistLastKnown(id, name.ifBlank { "Active device" }, type)
    }

    fun markOfflineFromTransport(reason: OfflineReason = OfflineReason.SpotifyUnreachable) {
        if (_connectionState.value is ConnectionState.Offline) return
        transition(ConnectionState.Offline(reason))
    }

    fun markOnlineFromMetadata() {
        val current = _connectionState.value
        if (current is ConnectionState.Offline && current.reason != OfflineReason.AuthExpired) {
            val cached = deviceManager.getCachedDeviceId()
            if (!cached.isNullOrBlank()) {
                transition(
                    ConnectionState.Ready(
                        deviceId = cached,
                        deviceName = deviceManager.getCachedDeviceName() ?: "Device",
                        deviceType = "Unknown",
                        verified = false
                    )
                )
            } else {
                transition(ConnectionState.Disconnected)
            }
        }
    }

    // ── Internals ────────────────────────────────────────────────────

    /**
     * Recovery after a semantic (non-transport) command failure.
     * AVRCP is used **only** when we classify the failure as [DegradedReason.DeviceAsleep]
     * on a phone-oriented target.
     */
    private suspend fun recoverAfterCommandFailure(previousDeviceId: String): String? {
        val target = _playbackTarget.value

        val afterRefresh = reconnect(force = true)
        if (afterRefresh is ConnectionState.Ready && afterRefresh.deviceId != previousDeviceId) {
            return afterRefresh.deviceId
        }

        if (afterRefresh is ConnectionState.Offline) return null
        if (afterRefresh is ConnectionState.Degraded &&
            afterRefresh.reason == DegradedReason.RestrictedDevice
        ) {
            return null
        }

        val asleep = classifyAsleepAfterCommandFailure(afterRefresh, previousDeviceId, target)
        if (asleep != null) {
            transition(asleep)
            AppLogger.i(TAG, "DeviceAsleep — attempting AVRCP recovery (if BT media linked)")
            val dispatched = recoveryStrategy.attemptWakeup()
            if (dispatched) {
                delay(AVRCP_SETTLE_MS)
                val afterWake = reconnect(force = true)
                if (afterWake is ConnectionState.Ready) return afterWake.deviceId
                if (afterWake is ConnectionState.Degraded && afterWake.deviceId != null) {
                    return afterWake.deviceId
                }
            } else {
                AppLogger.i(TAG, "AVRCP not dispatched; skipping settle delay")
            }
            return null
        }

        return (afterRefresh as? ConnectionState.Ready)?.deviceId
            ?: (afterRefresh as? ConnectionState.Degraded)?.deviceId
    }

    private fun classifyAsleepAfterCommandFailure(
        afterRefresh: ConnectionState,
        previousDeviceId: String,
        target: PlaybackTarget
    ): ConnectionState.Degraded? {
        // AVRCP only helps a sleeping *phone* Spotify process — never car players.
        if (!isPhoneOriented(target)) return null

        if (afterRefresh is ConnectionState.Degraded &&
            afterRefresh.reason == DegradedReason.DeviceAsleep
        ) {
            return afterRefresh
        }

        // Device still present and verified → command failed for another reason
        // (e.g. 403 policy). Do not pretend the phone is asleep.
        if (afterRefresh is ConnectionState.Ready && afterRefresh.verified) {
            return null
        }

        val deviceId = (afterRefresh as? ConnectionState.Ready)?.deviceId
            ?: (afterRefresh as? ConnectionState.Degraded)?.deviceId
            ?: previousDeviceId
        val deviceName = (afterRefresh as? ConnectionState.Ready)?.deviceName
            ?: (afterRefresh as? ConnectionState.Degraded)?.deviceName
        val deviceType = (afterRefresh as? ConnectionState.Ready)?.deviceType

        // If rediscovery landed on a non-phone (e.g. car head unit), do not AVRCP.
        if (!deviceType.isNullOrBlank() &&
            !deviceType.equals("Smartphone", ignoreCase = true)
        ) {
            return null
        }

        // Unverified / inactive phone-oriented target after rediscovery.
        return ConnectionState.Degraded(
            reason = DegradedReason.DeviceAsleep,
            deviceId = deviceId,
            deviceName = deviceName
        )
    }

    private fun isPhoneOriented(target: PlaybackTarget): Boolean = when (target) {
        is PlaybackTarget.Phone, is PlaybackTarget.Auto, is PlaybackTarget.Specific -> true
        is PlaybackTarget.CarPlayer -> false
    }

    private fun markReadyFromCommand(deviceId: String) {
        val name = deviceManager.getCachedDeviceName()
            ?: (_connectionState.value as? ConnectionState.Ready)?.deviceName
            ?: (_connectionState.value as? ConnectionState.Degraded)?.deviceName
            ?: lastKnownStore.peek()?.deviceName
            ?: "Device"
        val type = (_connectionState.value as? ConnectionState.Ready)?.deviceType
            ?: lastKnownStore.peek()?.deviceType
            ?: "Unknown"
        transition(
            ConnectionState.Ready(
                deviceId = deviceId,
                deviceName = name,
                deviceType = type,
                verified = true
            )
        )
        persistLastKnown(deviceId, name, type)
    }

    private fun persistLastKnown(deviceId: String, deviceName: String, deviceType: String) {
        if (deviceId.isBlank()) return
        val device = wakeCoordinator.rememberFromSession(deviceId, deviceName, deviceType)
        // Fire-and-forget persist; also update RAM cache immediately via peek path.
        scope.launch {
            try {
                lastKnownStore.save(device)
                deviceManager.registerActiveDevice(device.deviceId, device.deviceName)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to persist last Connect device: ${e.message}")
            }
        }
    }

    private suspend fun fetchDevices(): List<SpotifyDevice> {
        val response = api.getDevices()
        AppLogger.d(
            TAG,
            "Devices: ${response.devices.map { "${it.name}(${it.type},active=${it.isActive},restricted=${it.isRestricted})" }}"
        )
        return response.devices
    }

    internal fun selectDevice(
        devices: List<SpotifyDevice>,
        target: PlaybackTarget
    ): SpotifyDevice? {
        val usable = devices.filter { !it.id.isNullOrBlank() }
        if (usable.isEmpty()) return null

        return when (target) {
            is PlaybackTarget.Specific -> {
                usable.find { it.id == target.deviceId }
                    ?: usable.find { it.name.equals(target.deviceName, ignoreCase = true) }
                    ?: selectAuto(usable)
            }

            is PlaybackTarget.Phone -> {
                usable.find {
                    it.type.equals("Smartphone", ignoreCase = true) && it.isActive && !it.isRestricted
                } ?: usable.find {
                    it.type.equals("Smartphone", ignoreCase = true) && !it.isRestricted
                } ?: usable.find {
                    it.type.equals("Smartphone", ignoreCase = true)
                }
            }

            is PlaybackTarget.CarPlayer -> {
                usable.find {
                    !it.type.equals("Smartphone", ignoreCase = true) &&
                        it.isActive && !it.isRestricted
                } ?: usable.find {
                    !it.type.equals("Smartphone", ignoreCase = true) && !it.isRestricted
                } ?: usable.find {
                    !it.type.equals("Smartphone", ignoreCase = true)
                }
            }

            is PlaybackTarget.Auto -> selectAuto(usable)
        }
    }

    private fun selectAuto(usable: List<SpotifyDevice>): SpotifyDevice? {
        return usable.find {
            it.type.equals("Smartphone", ignoreCase = true) && it.isActive && !it.isRestricted
        } ?: usable.find {
            it.type.equals("Smartphone", ignoreCase = true) && !it.isRestricted
        } ?: usable.find {
            it.isActive && !it.isRestricted
        } ?: usable.find { !it.isRestricted }
            ?: usable.find { it.type.equals("Smartphone", ignoreCase = true) }
            ?: usable.firstOrNull()
    }

    private suspend fun verifyPlayerDevice(expectedDeviceId: String): Boolean {
        return try {
            val response = api.getCurrentPlayback()
            if (!response.isSuccessful) {
                return false
            }
            val body = response.body()
            val activeId = body?.device?.id
            if (activeId == expectedDeviceId) return true
            delay(VERIFY_RETRY_DELAY_MS)
            val retry = api.getCurrentPlayback()
            retry.body()?.device?.id == expectedDeviceId
        } catch (e: Exception) {
            AppLogger.w(TAG, "Verify player failed: ${e.message}")
            false
        }
    }

    private fun transition(next: ConnectionState) {
        val prev = _connectionState.value
        if (prev == next) return
        AppLogger.i(TAG, "State: ${prev::class.simpleName} → ${next::class.simpleName} ($next)")
        _connectionState.value = next
    }
}
