package com.cloudbridge.spotify.player

import com.cloudbridge.spotify.network.SpotifyApiService
import com.cloudbridge.spotify.network.model.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DeviceManager].
 *
 * Uses MockK to stub [SpotifyApiService.getDevices] responses.
 * Verifies device-selection priority (locked device → active smartphone → any smartphone → null),
 * caching behaviour, forced refresh, and graceful error handling.
 */
class DeviceManagerTest {

    private lateinit var apiService: SpotifyApiService
    private lateinit var deviceManager: DeviceManager

    @Before
    fun setup() {
        apiService = mockk()
        deviceManager = DeviceManager(apiService)
    }

    @Test
    fun `getPhoneDeviceId returns active smartphone first`() = runTest {
        coEvery { apiService.getDevices() } returns DevicesResponse(
            devices = listOf(
                SpotifyDevice(id = "speaker_1", name = "Speaker", type = "Speaker", isActive = true, isRestricted = false, volumePercent = 50),
                SpotifyDevice(id = "phone_1", name = "My Phone", type = "Smartphone", isActive = true, isRestricted = false, volumePercent = 75),
                SpotifyDevice(id = "phone_2", name = "Old Phone", type = "Smartphone", isActive = false, isRestricted = false, volumePercent = 60)
            )
        )

        val deviceId = deviceManager.getPhoneDeviceId()

        assertEquals("phone_1", deviceId)
    }

    @Test
    fun `getPhoneDeviceId returns inactive smartphone when no active smartphone`() = runTest {
        coEvery { apiService.getDevices() } returns DevicesResponse(
            devices = listOf(
                SpotifyDevice(id = "speaker_1", name = "Speaker", type = "Speaker", isActive = true, isRestricted = false, volumePercent = 50),
                SpotifyDevice(id = "phone_2", name = "My Phone", type = "Smartphone", isActive = false, isRestricted = false, volumePercent = 60)
            )
        )

        val deviceId = deviceManager.getPhoneDeviceId()

        assertEquals("phone_2", deviceId)
    }

    @Test
    fun `getPhoneDeviceId returns null when no smartphones and no locked device`() = runTest {
        coEvery { apiService.getDevices() } returns DevicesResponse(
            devices = listOf(
                SpotifyDevice(id = "speaker_1", name = "Speaker", type = "Speaker", isActive = true, isRestricted = false, volumePercent = 50),
                SpotifyDevice(id = "speaker_2", name = "Other Speaker", type = "Speaker", isActive = false, isRestricted = false, volumePercent = 30)
            )
        )

        val deviceId = deviceManager.getPhoneDeviceId()

        assertNull(deviceId)
    }

    @Test
    fun `getPhoneDeviceId returns null when no devices`() = runTest {
        coEvery { apiService.getDevices() } returns DevicesResponse(devices = emptyList())

        val deviceId = deviceManager.getPhoneDeviceId()

        assertNull(deviceId)
    }

    @Test
    fun `getPhoneDeviceId caches result`() = runTest {
        coEvery { apiService.getDevices() } returns DevicesResponse(
            devices = listOf(
                SpotifyDevice(id = "phone_1", name = "My Phone", type = "Smartphone", isActive = true, isRestricted = false, volumePercent = 75)
            )
        )

        // Call twice
        deviceManager.getPhoneDeviceId()
        deviceManager.getPhoneDeviceId()

        // API should only be called once due to caching
        coVerify(exactly = 1) { apiService.getDevices() }
    }

    @Test
    fun `refreshDeviceId forces API call even with valid cache`() = runTest {
        coEvery { apiService.getDevices() } returns DevicesResponse(
            devices = listOf(
                SpotifyDevice(id = "phone_1", name = "My Phone", type = "Smartphone", isActive = true, isRestricted = false, volumePercent = 75)
            )
        )

        deviceManager.getPhoneDeviceId()
        deviceManager.refreshDeviceId()

        coVerify(exactly = 2) { apiService.getDevices() }
    }

    @Test
    fun `getPhoneDeviceId handles API error gracefully`() = runTest {
        coEvery { apiService.getDevices() } throws RuntimeException("Internal Server Error")

        val deviceId = deviceManager.getPhoneDeviceId()

        assertNull(deviceId)
    }
}
