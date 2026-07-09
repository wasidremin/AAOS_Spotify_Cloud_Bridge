package com.cloudbridge.spotify.player

import com.cloudbridge.spotify.network.SpotifyApiService
import com.cloudbridge.spotify.network.model.PlayRequest
import com.cloudbridge.spotify.network.model.TransferPlaybackRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class DeviceWakeCoordinatorTest {

    private lateinit var api: SpotifyApiService
    private lateinit var store: InMemoryLastKnownDeviceStore
    private lateinit var recovery: RecordingRecovery
    private lateinit var coordinator: DeviceWakeCoordinator
    private val phases = mutableListOf<EstablishPhase>()

    private class RecordingRecovery(
        private val dispatch: Boolean = true
    ) : RecoveryStrategy {
        var calls = 0
        override fun attemptWakeup(): Boolean {
            calls++
            return dispatch
        }
    }

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        store = InMemoryLastKnownDeviceStore()
        recovery = RecordingRecovery()
        phases.clear()
        coordinator = DeviceWakeCoordinator(
            api = api,
            lastKnownStore = store,
            avrcp = recovery,
            companion = NoOpCompanionWake,
            onPhase = { phases += it },
            clock = { 1_000_000L }
        )
        coEvery { api.transferPlayback(any()) } returns Response.success(Unit)
        coEvery { api.play(any(), any()) } returns Response.success(Unit)
    }

    @Test
    fun `remembered transfer runs before AVRCP and can short-circuit`() = runTest {
        store.save(
            LastKnownDevice(
                deviceId = "phone_1",
                deviceName = "iPhone",
                deviceType = "Smartphone",
                lastSeenAtEpochMs = 1L
            )
        )

        var rediscovers = 0
        coordinator.runWakeLadder(phoneOriented = true) {
            rediscovers++
            // After first rediscover (post transfer), report Ready.
            rediscovers >= 2
        }

        coVerify(atLeast = 1) { api.transferPlayback(any<TransferPlaybackRequest>()) }
        assertEquals(0, recovery.calls)
        assertTrue(phases.contains(EstablishPhase.TryingLastDevice))
    }

    @Test
    fun `AVRCP runs when phone oriented and rediscover keeps failing`() = runTest {
        coordinator.runWakeLadder(phoneOriented = true) { false }

        assertTrue(recovery.calls >= 1)
        assertTrue(phases.contains(EstablishPhase.BluetoothWake))
        assertTrue(phases.contains(EstablishPhase.SoftResume))
        coVerify(atLeast = 1) { api.play(deviceId = null, body = any<PlayRequest>()) }
    }

    @Test
    fun `AVRCP skipped when not phone oriented`() = runTest {
        coordinator.runWakeLadder(phoneOriented = false) { false }

        assertEquals(0, recovery.calls)
        assertTrue(!phases.contains(EstablishPhase.BluetoothWake))
    }

    @Test
    fun `no settle burst when AVRCP reports not dispatched`() = runTest {
        val noBt = RecordingRecovery(dispatch = false)
        val localPhases = mutableListOf<EstablishPhase>()
        val coord = DeviceWakeCoordinator(
            api = api,
            lastKnownStore = store,
            avrcp = noBt,
            companion = NoOpCompanionWake,
            onPhase = { localPhases += it },
            clock = { 1L }
        )
        var rediscovers = 0
        coord.runWakeLadder(phoneOriented = true) {
            rediscovers++
            false
        }
        assertEquals(1, noBt.calls)
        // Soft resume + companion + final burst still rediscover; AVRCP itself adds no settle
        // when dispatch=false (burst after AVRCP is skipped).
        assertTrue(noBt.calls == 1)
        assertTrue(localPhases.contains(EstablishPhase.BluetoothWake))
        assertTrue(localPhases.contains(EstablishPhase.CompanionWake))
    }
}
