package com.cloudbridge.spotify.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for cache-only [DeviceManager].
 * Device selection lives in [PlaybackSessionManager].
 */
class DeviceManagerTest {

    private lateinit var deviceManager: DeviceManager

    @Before
    fun setup() {
        deviceManager = DeviceManager()
    }

    @Test
    fun `registerActiveDevice caches id and name`() {
        deviceManager.registerActiveDevice("phone_1", "My Phone")

        assertEquals("phone_1", deviceManager.getCachedDeviceId())
        assertEquals("My Phone", deviceManager.getCachedDeviceName())
        assertTrue(deviceManager.isCacheFresh())
    }

    @Test
    fun `registerActiveDevice ignores blank id`() {
        deviceManager.registerActiveDevice("", "Nope")
        assertNull(deviceManager.getCachedDeviceId())
    }

    @Test
    fun `clearCache wipes remembered device`() {
        deviceManager.registerActiveDevice("phone_1", "My Phone")
        deviceManager.clearCache()

        assertNull(deviceManager.getCachedDeviceId())
        assertNull(deviceManager.getCachedDeviceName())
        assertFalse(deviceManager.isCacheFresh())
    }

    @Test
    fun `lockedDeviceId is independent of cache`() {
        deviceManager.lockedDeviceId = "locked_99"
        deviceManager.registerActiveDevice("phone_1", "My Phone")

        assertEquals("locked_99", deviceManager.lockedDeviceId)
        assertEquals("phone_1", deviceManager.getCachedDeviceId())
    }
}
