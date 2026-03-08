package com.cloudbridge.spotify.player

import android.util.Log
import com.cloudbridge.spotify.network.SpotifyApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Discovers and caches the user's phone Spotify device ID.
 *
 * Why this exists:
 * Every playback command to the Spotify Web API MUST include a `device_id`
 * parameter targeting the user's smartphone. Without this, Spotify might
 * route playback to any connected device (another phone, a smart speaker,
 * or the car's built-in Spotify if installed).
 *
 * Device selection priority:
 * 1. Active smartphone (type == "Smartphone" && isActive)
 * 2. Any smartphone (type == "Smartphone")
 * 3. Any active device (fallback)
 * 4. null (no devices available)
 *
 * Cache TTL: 2 minutes. Devices can come and go as the phone
 * connects/disconnects from Spotify.
 */
class DeviceManager(private val api: SpotifyApiService) {

    companion object {
        private const val TAG = "DeviceManager"
        private const val CACHE_TTL_MS = 2 * 60 * 1000L  // 2 minutes
    }

    /**
     * When set, [getPhoneDeviceId] always returns this value
     * instead of auto-discovering a smartphone.
     * Set by the ViewModel from DataStore preferences.
     */
    var lockedDeviceId: String? = null

    private var cachedDeviceId: String? = null
    private var cachedDeviceName: String? = null
    private var cacheTimestamp: Long = 0L
    private val mutex = Mutex()

    /**
     * Get the phone's Spotify device ID.
     *
     * Returns the cached value if still within the [CACHE_TTL_MS] window.
     * Otherwise, fetches a fresh device list from the API.
     *
     * The [mutex] ensures that only one coroutine hits the network at a time;
     * others will either reuse the cache or wait.
     *
     * @return The device ID string, or `null` if no suitable device is found.
     */
    suspend fun getPhoneDeviceId(): String? = withContext(Dispatchers.IO) {
        // If user locked a specific device, always use it
        lockedDeviceId?.let {
            Log.d(TAG, "Using locked device: $it")
            return@withContext it
        }

        mutex.withLock {
            if (cachedDeviceId != null && !isCacheExpired()) {
                Log.d(TAG, "Returning cached device: $cachedDeviceName ($cachedDeviceId)")
                return@withContext cachedDeviceId
            }
        }

        return@withContext refreshDeviceId()
    }

    /**
     * Force-refresh the device list from the Spotify API.
     *
     * Ignores the cache entirely. Called by [SpotifyPlaybackController]
     * when a playback command returns 404 (device not found),
     * indicating the previously cached device went offline.
     *
     * Device selection priority:
     * 1. Active smartphone (`type == "Smartphone" && isActive`)
     * 2. Any smartphone (`type == "Smartphone"`)
     * 3. Any active, unrestricted device (fallback)
     * 4. `null` — no devices available
     *
     * @return The newly discovered device ID, or `null`.
     */
    suspend fun refreshDeviceId(): String? = withContext(Dispatchers.IO) {
        // If the user locked a specific device, completely bypass auto-discovery
        lockedDeviceId?.let {
            Log.d(TAG, "Refresh bypassed: Using locked device ($it)")
            return@withContext it
        }

        try {
            val response = api.getDevices()
            val devices = response.devices

            Log.d(TAG, "Found ${devices.size} devices: ${devices.map { "${it.name} (${it.type}, active=${it.isActive})" }}")

            // Priority 1: Active smartphone
            val activePhone = devices.find {
                it.type.equals("Smartphone", ignoreCase = true) && it.isActive && !it.id.isNullOrBlank()
            }
            if (activePhone != null) {
                cacheDevice(activePhone.id!!, activePhone.name)
                return@withContext activePhone.id
            }

            // Priority 2: Any smartphone
            val anyPhone = devices.find {
                it.type.equals("Smartphone", ignoreCase = true) && !it.id.isNullOrBlank()
            }
            if (anyPhone != null) {
                cacheDevice(anyPhone.id!!, anyPhone.name)
                return@withContext anyPhone.id
            }

            Log.w(TAG, "No suitable Spotify device found.")
            clearCache()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch devices: ${e.message}", e)
            clearCache()
            null
        }
    }

    fun getCachedDeviceId(): String? = cachedDeviceId

    private fun cacheDevice(id: String, name: String) {
        mutex.tryLock() // Best-effort lock for setting cache
        try {
            cachedDeviceId = id
            cachedDeviceName = name
            cacheTimestamp = System.currentTimeMillis()
            Log.i(TAG, "Cached device: $name ($id)")
        } finally {
            try { mutex.unlock() } catch (_: IllegalStateException) { }
        }
    }

    private fun clearCache() {
        cachedDeviceId = null
        cachedDeviceName = null
        cacheTimestamp = 0L
    }

    private fun isCacheExpired(): Boolean =
        System.currentTimeMillis() - cacheTimestamp > CACHE_TTL_MS
}
