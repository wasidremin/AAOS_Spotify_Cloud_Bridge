package com.cloudbridge.spotify.player

import com.cloudbridge.spotify.network.SpotifyApiService
import com.cloudbridge.spotify.network.model.CurrentPlaybackResponse
import com.cloudbridge.spotify.network.model.DevicesResponse
import com.cloudbridge.spotify.network.model.SpotifyDevice
import com.cloudbridge.spotify.network.model.TransferPlaybackRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionManagerTest {

    private lateinit var api: SpotifyApiService
    private lateinit var deviceManager: DeviceManager
    private lateinit var recovery: RecordingRecovery
    private lateinit var scope: TestScope
    private lateinit var session: PlaybackSessionManager

    private class RecordingRecovery : RecoveryStrategy {
        var calls = 0
        override fun attemptWakeup(): Boolean {
            calls++
            return true // simulate BT-connected car head unit
        }
    }

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        deviceManager = DeviceManager()
        recovery = RecordingRecovery()
        scope = TestScope(UnconfinedTestDispatcher())
        session = PlaybackSessionManager(
            api = api,
            deviceManager = deviceManager,
            recoveryStrategy = recovery,
            lastKnownStore = InMemoryLastKnownDeviceStore(),
            companionWake = NoOpCompanionWake,
            scope = scope,
            clock = { 1_000_000L }
        )
    }

    private fun phone(
        id: String = "phone_1",
        active: Boolean = true,
        restricted: Boolean = false
    ) = SpotifyDevice(
        id = id,
        name = "My Phone",
        type = "Smartphone",
        isActive = active,
        isRestricted = restricted,
        volumePercent = 50
    )

    private fun car(
        id: String = "car_1",
        active: Boolean = true,
        restricted: Boolean = false
    ) = SpotifyDevice(
        id = id,
        name = "Car Spotify",
        type = "AVR",
        isActive = active,
        isRestricted = restricted,
        volumePercent = 40
    )

    private fun playbackResponse(device: SpotifyDevice?, playing: Boolean = true) =
        Response.success(
            CurrentPlaybackResponse(
                isPlaying = playing,
                progressMs = 0,
                item = null,
                device = device,
                shuffleState = false,
                repeatState = "off"
            )
        )

    @Test
    fun `selectDevice Auto prefers active phone`() {
        val selected = session.selectDevice(
            listOf(car(active = true), phone(active = true)),
            PlaybackTarget.Auto
        )
        assertEquals("phone_1", selected?.id)
    }

    @Test
    fun `selectDevice Phone ignores car even if active`() {
        val selected = session.selectDevice(
            listOf(car(active = true), phone(active = false)),
            PlaybackTarget.Phone
        )
        assertEquals("phone_1", selected?.id)
    }

    @Test
    fun `selectDevice CarPlayer prefers non-phone`() {
        val selected = session.selectDevice(
            listOf(phone(active = true), car(active = false)),
            PlaybackTarget.CarPlayer
        )
        assertEquals("car_1", selected?.id)
    }

    @Test
    fun `selectDevice Specific matches by id then name`() {
        val byId = session.selectDevice(
            listOf(phone(), car()),
            PlaybackTarget.Specific("car_1", "Car Spotify")
        )
        assertEquals("car_1", byId?.id)

        val byName = session.selectDevice(
            listOf(phone(id = "new_phone"), car(id = "new_car")),
            PlaybackTarget.Specific("stale_id", "Car Spotify")
        )
        assertEquals("new_car", byName?.id)
    }

    @Test
    fun `reconnect Ready when phone is active`() = runTest {
        coEvery { api.getDevices() } returns DevicesResponse(listOf(phone()))
        coEvery { api.getCurrentPlayback() } returns playbackResponse(phone())

        val state = session.reconnect(force = true)

        assertTrue(state is ConnectionState.Ready)
        assertEquals("phone_1", (state as ConnectionState.Ready).deviceId)
        assertTrue(state.verified)
    }

    @Test
    fun `reconnect Degraded NoDevices when empty and no memory`() = runTest {
        coEvery { api.getDevices() } returns DevicesResponse(emptyList())

        val state = session.reconnect(force = true)

        assertTrue(state is ConnectionState.Degraded)
        assertEquals(DegradedReason.NoDevices, (state as ConnectionState.Degraded).reason)
    }

    @Test
    fun `reconnect keeps remembered device across devices blind spot`() = runTest {
        deviceManager.registerActiveDevice("phone_1", "My Phone")
        coEvery { api.getDevices() } returns DevicesResponse(emptyList())

        val state = session.reconnect(force = true)

        assertTrue(state is ConnectionState.Ready)
        val ready = state as ConnectionState.Ready
        assertEquals("phone_1", ready.deviceId)
        assertFalse(ready.verified)
    }

    @Test
    fun `reconnect Offline on network failure`() = runTest {
        coEvery { api.getDevices() } throws UnknownHostException("dns")

        val state = session.reconnect(force = true)

        assertTrue(state is ConnectionState.Offline)
        assertEquals(OfflineReason.NoNetwork, (state as ConnectionState.Offline).reason)
    }

    @Test
    fun `reconnect Degraded RestrictedDevice`() = runTest {
        session.setPlaybackTarget(PlaybackTarget.Phone)
        coEvery { api.getDevices() } returns DevicesResponse(
            listOf(phone(restricted = true))
        )

        val state = session.reconnect(force = true)

        assertTrue(state is ConnectionState.Degraded)
        assertEquals(DegradedReason.RestrictedDevice, (state as ConnectionState.Degraded).reason)
    }

    @Test
    fun `executeCommand Success when command accepts`() = runTest {
        coEvery { api.getDevices() } returns DevicesResponse(listOf(phone()))
        coEvery { api.getCurrentPlayback() } returns playbackResponse(phone())

        val result = session.executeCommand("play") { deviceId ->
            assertEquals("phone_1", deviceId)
            PlaybackCommandResult.Success
        }

        assertTrue(result is CommandResult.Success)
        assertEquals(0, recovery.calls)
    }

    @Test
    fun `executeCommand does not AVRCP on IOException`() = runTest {
        coEvery { api.getDevices() } returns DevicesResponse(listOf(phone()))
        coEvery { api.getCurrentPlayback() } returns playbackResponse(phone())

        val result = session.executeCommand("play") {
            throw UnknownHostException("offline")
        }

        assertTrue(result is CommandResult.Failed)
        assertTrue((result as CommandResult.Failed).state is ConnectionState.Offline)
        assertEquals(0, recovery.calls)
    }

    @Test
    fun `executeCommand does not AVRCP on 403 Forbidden`() = runTest {
        coEvery { api.getDevices() } returns DevicesResponse(listOf(phone()))
        coEvery { api.getCurrentPlayback() } returns playbackResponse(phone())

        val result = session.executeCommand("previous") {
            PlaybackCommandResult.forbidden()
        }

        assertTrue(result is CommandResult.Failed)
        val failed = result as CommandResult.Failed
        assertTrue(failed.state is ConnectionState.Degraded)
        assertEquals(
            DegradedReason.CommandFailed,
            (failed.state as ConnectionState.Degraded).reason
        )
        assertEquals(0, recovery.calls)
    }

    @Test
    fun `executeCommand does not AVRCP when device stays Ready verified`() = runTest {
        coEvery { api.getDevices() } returns DevicesResponse(listOf(phone()))
        coEvery { api.getCurrentPlayback() } returns playbackResponse(phone())
        coEvery { api.transferPlayback(any<TransferPlaybackRequest>()) } returns
            Response.success(Unit)

        val result = session.executeCommand("play") {
            PlaybackCommandResult.other(500)
        }

        assertTrue(result is CommandResult.Failed)
        assertEquals(0, recovery.calls)
    }

    @Test
    fun `executeCommand uses AVRCP only after classifying DeviceAsleep`() = runTest {
        session.setPlaybackTarget(PlaybackTarget.Phone)
        // Counter-based mocks: first discovery is active; recovery sees inactive phone
        // (DeviceAsleep) then wake restores active.
        var devicesCalls = 0
        coEvery { api.getDevices() } coAnswers {
            devicesCalls++
            when (devicesCalls) {
                1 -> DevicesResponse(listOf(phone(active = true)))
                2 -> DevicesResponse(listOf(phone(active = false)))
                else -> DevicesResponse(listOf(phone(active = true)))
            }
        }
        var playbackCalls = 0
        coEvery { api.getCurrentPlayback() } coAnswers {
            playbackCalls++
            // Only the initial ensureReady verify succeeds; inactive rediscovery gets 204/null
            // until after AVRCP wake (devicesCalls already advanced past 2).
            if (devicesCalls <= 1) playbackResponse(phone(active = true))
            else if (devicesCalls == 2) Response.success(null)
            else playbackResponse(phone(active = true))
        }
        coEvery { api.transferPlayback(any<TransferPlaybackRequest>()) } returns
            Response.success(Unit)

        var attempts = 0
        val result = session.executeCommand("play") {
            attempts++
            if (attempts >= 2) PlaybackCommandResult.Success
            else PlaybackCommandResult.notFound()
        }

        assertTrue(
            "expected Success after AVRCP recovery, got $result (avrcp=${recovery.calls}, attempts=$attempts, devicesCalls=$devicesCalls)",
            result is CommandResult.Success
        )
        assertTrue(
            "AVRCP should run for inactive-phone DeviceAsleep (avrcp=${recovery.calls}, devicesCalls=$devicesCalls)",
            recovery.calls >= 1
        )
    }

    @Test
    fun `reconnect marks DeviceAsleep for inactive phone on Phone target`() = runTest {
        session.setPlaybackTarget(PlaybackTarget.Phone)
        coEvery { api.getDevices() } returns DevicesResponse(listOf(phone(active = false)))
        coEvery { api.getCurrentPlayback() } returns Response.success(null)

        val state = session.reconnect(force = true)

        assertTrue(state is ConnectionState.Degraded)
        assertEquals(DegradedReason.DeviceAsleep, (state as ConnectionState.Degraded).reason)
    }

    @Test
    fun `executeCommand tries null device when not Ready`() = runTest {
        coEvery { api.getDevices() } returns DevicesResponse(emptyList())

        var sawNull = false
        val result = session.executeCommand("play") { deviceId ->
            if (deviceId == null) {
                sawNull = true
                PlaybackCommandResult.Success
            } else {
                PlaybackCommandResult.notFound()
            }
        }

        assertTrue(sawNull)
        assertTrue(result is CommandResult.Success)
    }

    @Test
    fun `CarPlayer target does not use AVRCP recovery`() = runTest {
        session.setPlaybackTarget(PlaybackTarget.CarPlayer)
        coEvery { api.getDevices() } returns DevicesResponse(listOf(car()))
        coEvery { api.getCurrentPlayback() } returns playbackResponse(car())
        coEvery { api.transferPlayback(any()) } returns Response.success(Unit)

        val result = session.executeCommand("play") { PlaybackCommandResult.other() }

        assertTrue(result is CommandResult.Failed)
        assertEquals(0, recovery.calls)
    }

    @Test
    fun `observeActiveDevice promotes Ready`() = runTest {
        session.observeActiveDevice("phone_9", "Pixel", "Smartphone")
        val state = session.connectionState.value
        assertTrue(state is ConnectionState.Ready)
        assertEquals("phone_9", (state as ConnectionState.Ready).deviceId)
    }
}
