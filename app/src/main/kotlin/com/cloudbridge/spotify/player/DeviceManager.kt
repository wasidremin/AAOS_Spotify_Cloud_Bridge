package com.cloudbridge.spotify.player

import com.cloudbridge.spotify.util.AppLogger

/**
 * Low-level cache of the last known Spotify Connect device.
 *
 * **Does not select or discover devices.** [PlaybackSessionManager] owns
 * discovery, [PlaybackTarget] resolution, and reconnect. This class only
 * remembers the last good id/name so blind spots and UI labels stay warm.
 */
class DeviceManager {

    companion object {
        private const val TAG = "DeviceManager"
        private const val CACHE_TTL_MS = 2 * 60 * 1000L
    }

    /**
     * When set by [PlaybackSessionManager] for [PlaybackTarget.Specific],
     * callers may read it for diagnostics. Session manager re-resolves the
     * live id on reconnect; this is not used for command routing.
     */
    @Volatile
    var lockedDeviceId: String? = null

    private var cachedDeviceId: String? = null
    private var cachedDeviceName: String? = null
    private var cacheTimestamp: Long = 0L
    private val cacheLock = Any()

    fun getCachedDeviceId(): String? = synchronized(cacheLock) { cachedDeviceId }

    fun getCachedDeviceName(): String? = synchronized(cacheLock) { cachedDeviceName }

    fun isCacheFresh(): Boolean = synchronized(cacheLock) {
        cachedDeviceId != null && System.currentTimeMillis() - cacheTimestamp <= CACHE_TTL_MS
    }

    /**
     * Passively remembers a known active playback device observed elsewhere
     * (metadata poll, successful command, reconnect).
     */
    fun registerActiveDevice(id: String, name: String) {
        if (id.isBlank()) return
        synchronized(cacheLock) {
            cachedDeviceId = id
            cachedDeviceName = name.ifBlank { "Active device" }
            cacheTimestamp = System.currentTimeMillis()
        }
        AppLogger.i(TAG, "Cached device: $name ($id)")
    }

    fun clearCache() {
        synchronized(cacheLock) {
            cachedDeviceId = null
            cachedDeviceName = null
            cacheTimestamp = 0L
        }
    }
}
