package com.cloudbridge.spotify.player

import com.cloudbridge.spotify.network.SpotifyApiService
import com.cloudbridge.spotify.network.model.TransferPlaybackRequest
import com.cloudbridge.spotify.util.AppLogger
import kotlinx.coroutines.delay

/**
 * Progressive steps while establishing a Connect session when the phone is
 * not yet advertising (empty `/devices` or inactive phone).
 *
 * UI can show [EstablishPhase] copy; this is separate from [ConnectionState]
 * so discover/ready/degraded semantics stay stable.
 */
enum class EstablishPhase {
    Idle,
    LookingForPhone,
    TryingLastDevice,
    SoftResume,
    BluetoothWake,
    CompanionWake,
    WaitingForDevice
}

fun EstablishPhase.bannerText(): String? = when (this) {
    EstablishPhase.Idle -> null
    EstablishPhase.LookingForPhone -> "Looking for phone Spotify…"
    EstablishPhase.TryingLastDevice -> "Trying last known device…"
    EstablishPhase.SoftResume -> "Asking Spotify to resume session…"
    EstablishPhase.BluetoothWake -> "Bluetooth wake — waiting for phone…"
    EstablishPhase.CompanionWake -> "Asking phone automation to open Spotify…"
    EstablishPhase.WaitingForDevice -> "Waiting for phone to appear…"
}

/**
 * Optional phone-side wake (Home Assistant webhook, companion app, etc.).
 * Default is no-op so the car app stays self-contained.
 */
fun interface CompanionWakeStrategy {
    /**
     * Fire-and-forget request that the phone ensure Spotify is running.
     * @return true if a wake signal was dispatched (not that Spotify is ready).
     */
    suspend fun requestWake(): Boolean
}

object NoOpCompanionWake : CompanionWakeStrategy {
    override suspend fun requestWake(): Boolean = false
}

/**
 * Ordered wake ladder used when a single [PlaybackSessionManager.reconnect]
 * is not enough (empty devices / asleep phone).
 *
 * Does **not** own connection state — the session manager runs rediscover
 * after each step and decides Ready vs Degraded.
 */
class DeviceWakeCoordinator(
    private val api: SpotifyApiService,
    private val lastKnownStore: LastKnownDeviceStore,
    private val avrcp: RecoveryStrategy = NoOpRecoveryStrategy,
    private val companion: CompanionWakeStrategy = NoOpCompanionWake,
    private val onPhase: (EstablishPhase) -> Unit = {},
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val TAG = "DeviceWake"
        private const val POST_TRANSFER_MS = 600L
        private const val POST_SOFT_RESUME_MS = 500L
        private const val AVRCP_SETTLE_MS = 2_000L
        private const val COMPANION_SETTLE_MS = 2_500L

        /** Burst rediscovery delays after a wake step (ms). */
        val BURST_DELAYS_MS: List<Long> = listOf(500L, 1_500L, 3_000L, 6_000L)
    }

    /**
     * Run wake ladder steps that may make a phone advertise.
     * Caller should [PlaybackSessionManager.reconnect] between/after steps
     * via [rediscover]; this method only performs side effects.
     *
     * @param phoneOriented whether AVRCP / companion are allowed
     * @param rediscover called after each step; stop when it returns true (session Ready)
     */
    suspend fun runWakeLadder(
        phoneOriented: Boolean,
        rediscover: suspend () -> Boolean
    ) {
        if (rediscover()) return

        // 1) Blind transfer to last known device (even if /devices was empty).
        val last = lastKnownStore.load()
        if (last != null && (!phoneOriented || last.isPhone || last.deviceType == "Unknown")) {
            onPhase(EstablishPhase.TryingLastDevice)
            AppLogger.i(TAG, "Remembered transfer → ${last.deviceName} (${last.deviceId})")
            tryRememberedTransfer(last)
            delay(POST_TRANSFER_MS)
            if (rediscover()) return
        }

        // 2) Soft resume without device_id — Spotify may revive last session.
        onPhase(EstablishPhase.SoftResume)
        AppLogger.i(TAG, "Soft resume (no device_id)")
        trySoftResume()
        delay(POST_SOFT_RESUME_MS)
        if (rediscover()) return

        if (phoneOriented) {
            // 3) AVRCP only when a real media Bluetooth link exists (not emulator / unpaired).
            onPhase(EstablishPhase.BluetoothWake)
            val avrcpDispatched = avrcp.attemptWakeup()
            if (avrcpDispatched) {
                AppLogger.i(TAG, "AVRCP wake dispatched; settling then rediscovering")
                delay(AVRCP_SETTLE_MS)
                if (burstRediscover(rediscover)) return
            } else {
                AppLogger.i(TAG, "AVRCP skipped (no BT media link or blocked)")
            }

            // 4) Optional companion wake (Home Assistant webhook, etc.) — advanced / opt-in.
            onPhase(EstablishPhase.CompanionWake)
            val companionDispatched = try {
                companion.requestWake()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Companion wake failed: ${e.message}")
                false
            }
            if (companionDispatched) {
                AppLogger.i(TAG, "Companion wake dispatched")
                delay(COMPANION_SETTLE_MS)
                if (burstRediscover(rediscover)) return
            } else {
                AppLogger.d(TAG, "Companion wake not configured or not dispatched")
            }
        }

        // 5) Final burst even if no wake step ran (race: phone still starting).
        onPhase(EstablishPhase.WaitingForDevice)
        burstRediscover(rediscover)
        onPhase(EstablishPhase.Idle)
    }

    private suspend fun burstRediscover(rediscover: suspend () -> Boolean): Boolean {
        onPhase(EstablishPhase.WaitingForDevice)
        for (waitMs in BURST_DELAYS_MS) {
            delay(waitMs)
            if (rediscover()) {
                onPhase(EstablishPhase.Idle)
                return true
            }
        }
        return false
    }

    private suspend fun tryRememberedTransfer(last: LastKnownDevice) {
        try {
            api.transferPlayback(
                TransferPlaybackRequest(deviceIds = listOf(last.deviceId), play = false)
            )
            AppLogger.i(TAG, "Remembered transfer accepted for ${last.deviceId}")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Remembered transfer failed (non-fatal): ${e.message}")
        }
    }

    private suspend fun trySoftResume() {
        try {
            // Empty body resumes current / last context when Spotify still has a session.
            val response = api.play(
                deviceId = null,
                body = com.cloudbridge.spotify.network.model.PlayRequest()
            )
            AppLogger.i(TAG, "Soft resume response: ${response.code()}")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Soft resume failed (non-fatal): ${e.message}")
        }
    }

    fun rememberFromSession(
        deviceId: String,
        deviceName: String,
        deviceType: String
    ): LastKnownDevice {
        return LastKnownDevice(
            deviceId = deviceId,
            deviceName = deviceName.ifBlank { "Device" },
            deviceType = deviceType.ifBlank { "Unknown" },
            lastSeenAtEpochMs = clock()
        )
    }
}
