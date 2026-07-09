package com.cloudbridge.spotify.player

/**
 * Durable identity of the last successful Spotify Connect command target.
 * Survives process death so empty `/devices` can still try a blind transfer.
 */
data class LastKnownDevice(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val lastSeenAtEpochMs: Long
) {
    val isPhone: Boolean
        get() = deviceType.equals("Smartphone", ignoreCase = true)
}

/**
 * Persistence for [LastKnownDevice]. Implementations must be thread-safe.
 */
interface LastKnownDeviceStore {
    /** In-memory peek (may be null until [load] or [save]). */
    fun peek(): LastKnownDevice?

    suspend fun load(): LastKnownDevice?

    suspend fun save(device: LastKnownDevice)

    suspend fun clear()
}

/** In-memory only — unit tests and environments without DataStore. */
class InMemoryLastKnownDeviceStore : LastKnownDeviceStore {
    @Volatile
    private var cached: LastKnownDevice? = null

    override fun peek(): LastKnownDevice? = cached

    override suspend fun load(): LastKnownDevice? = cached

    override suspend fun save(device: LastKnownDevice) {
        cached = device
    }

    override suspend fun clear() {
        cached = null
    }
}
