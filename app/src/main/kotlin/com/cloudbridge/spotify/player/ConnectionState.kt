package com.cloudbridge.spotify.player

/**
 * Explicit Spotify Connect session state observed by the UI.
 *
 * Owned exclusively by [PlaybackSessionManager]. ViewModel maps these
 * states to banners; it never invents connectivity flags on its own.
 */
sealed class ConnectionState {
    /** No attempt yet, or session was explicitly cleared (profile switch). */
    data object Disconnected : ConnectionState()

    /** Reconnect pipeline is actively discovering / transferring / verifying. */
    data object Discovering : ConnectionState()

    /**
     * A verified (or last-known good) playback target is available for commands.
     *
     * @property deviceId   Spotify Connect device_id
     * @property deviceName Human-readable name from Spotify
     * @property deviceType Spotify type string (e.g. "Smartphone", "AVR")
     * @property verified   True when confirmed via GET /v1/me/player or devices list
     */
    data class Ready(
        val deviceId: String,
        val deviceName: String,
        val deviceType: String,
        val verified: Boolean = true
    ) : ConnectionState()

    /**
     * Network is fine but the session is not command-ready.
     * UI should show an actionable banner, not a generic offline message.
     */
    data class Degraded(
        val reason: DegradedReason,
        val deviceId: String? = null,
        val deviceName: String? = null
    ) : ConnectionState()

    /** Transport-level or auth-level unavailability. */
    data class Offline(
        val reason: OfflineReason
    ) : ConnectionState()
}

enum class DegradedReason {
    /** GET /devices returned empty (and no remembered target). */
    NoDevices,

    /** Devices listed but none match the preferred target / none active. */
    DeviceAsleep,

    /** Target device is restricted (typical for some car players). */
    RestrictedDevice,

    /** Active session is on a different device than the preferred target. */
    WrongDevice,

    /** Last command failed after reconnect attempts. */
    CommandFailed
}

enum class OfflineReason {
    NoNetwork,
    SpotifyUnreachable,
    RateLimited,
    AuthExpired
}

/**
 * User-facing (or smart-default) playback endpoint preference.
 *
 * Persisted preference is resolved to a live device_id at reconnect time —
 * never stored as a raw ID alone for Auto/Phone/CarPlayer modes.
 */
sealed class PlaybackTarget {
    /** Smart selection: active phone → any phone → any active unrestricted. */
    data object Auto : PlaybackTarget()

    /** Prefer smartphone Connect devices only. */
    data object Phone : PlaybackTarget()

    /** Prefer non-phone active devices (built-in car player, speakers, etc.). */
    data object CarPlayer : PlaybackTarget()

    /**
     * Pin to a specific device id (legacy Settings lock).
     * Re-validated on reconnect; if missing, falls back to remembered name match.
     */
    data class Specific(val deviceId: String, val deviceName: String) : PlaybackTarget()
}

/** Outcome of a gated playback command. */
sealed class CommandResult {
    data object Success : CommandResult()
    data class Failed(val state: ConnectionState) : CommandResult()
}

/**
 * Low-level result of a single Spotify control HTTP call.
 * Used so [PlaybackSessionManager] can avoid AVRCP on 403 Forbidden, etc.
 */
sealed class PlaybackCommandResult {
    data object Success : PlaybackCommandResult()

    data class Failure(
        val kind: Kind,
        val httpCode: Int? = null
    ) : PlaybackCommandResult() {
        enum class Kind {
            /** HTTP 403 — restricted device, missing Premium, or policy block. Never AVRCP. */
            Forbidden,
            /** HTTP 404 after optional null-device retry — stale id / no active session. */
            NotFound,
            /** Any other semantic failure (non-2xx, unexpected). */
            Other
        }
    }

    val isSuccess: Boolean get() = this is Success

    companion object {
        fun forbidden(httpCode: Int = 403) = Failure(Failure.Kind.Forbidden, httpCode)
        fun notFound(httpCode: Int = 404) = Failure(Failure.Kind.NotFound, httpCode)
        fun other(httpCode: Int? = null) = Failure(Failure.Kind.Other, httpCode)
    }
}

/** User-facing offline banner copy. */
fun ConnectionState.offlineBannerText(): String? = when (this) {
    is ConnectionState.Offline -> when (reason) {
        OfflineReason.NoNetwork -> "No network — reconnecting..."
        OfflineReason.SpotifyUnreachable -> "Spotify unreachable — reconnecting..."
        OfflineReason.RateLimited -> null // rate-limit banner is separate
        OfflineReason.AuthExpired -> null // reauth banner is separate
    }
    else -> null
}

/** User-facing degraded-session banner copy. */
fun ConnectionState.degradedBannerText(): String? = when (this) {
    is ConnectionState.Degraded -> when (reason) {
        DegradedReason.NoDevices ->
            "No Spotify device found. Open Spotify on your phone and retry."
        DegradedReason.DeviceAsleep ->
            "Phone Spotify appears asleep. Waking session…"
        DegradedReason.RestrictedDevice ->
            "Device is restricted and cannot be controlled from the car."
        DegradedReason.WrongDevice ->
            "Playback is on a different device than preferred."
        DegradedReason.CommandFailed ->
            "Playback command failed. Check connection and retry."
    }
    else -> null
}

fun ConnectionState.showsOfflineBanner(): Boolean =
    this is ConnectionState.Offline &&
        reason != OfflineReason.RateLimited &&
        reason != OfflineReason.AuthExpired

fun ConnectionState.showsDegradedBanner(): Boolean = this is ConnectionState.Degraded
