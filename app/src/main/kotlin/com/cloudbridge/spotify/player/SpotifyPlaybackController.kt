package com.cloudbridge.spotify.player

import android.util.Log
import com.cloudbridge.spotify.network.SpotifyApiService
import com.cloudbridge.spotify.network.model.CurrentPlaybackResponse
import com.cloudbridge.spotify.network.model.PlayOffset
import com.cloudbridge.spotify.network.model.PlayRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps Spotify Web API playback commands with error handling.
 *
 * This is the "bridge" in Cloud-Bridge: instead of feeding audio data
 * to a local decoder, we fire HTTP requests to Spotify's servers
 * to control playback on the user's phone.
 *
 * Error handling strategy:
 * - 204: Success (Spotify returns 204 No Content for control commands)
 * - 401: Handled by TokenRefreshAuthenticator (transparent retry)
 * - 403: Device is restricted or user lacks Premium
 * - 404: Device not found → refresh device list and retry once
 * - 429: Handled by RateLimitRetryInterceptor (transparent retry)
 */
class SpotifyPlaybackController(
    private val api: SpotifyApiService,
    private val deviceManager: DeviceManager
) {
    companion object {
        private const val TAG = "PlaybackController"
    }

    /**
     * Start playback of a specific track within a playlist context.
     *
     * Using context_uri + offset gives a better UX than playing a single
     * track URI because Spotify will continue playing the next tracks
     * in the playlist automatically.
     *
     * @param trackUri e.g., "spotify:track:6rqhFgbbKwnb9MLmUQDhG6"
     * @param contextUri e.g., "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M" (optional)
     * @return true if the command was accepted by Spotify
     */
    suspend fun play(trackUri: String? = null, contextUri: String? = null, offsetPosition: Int? = null, uris: List<String>? = null): Boolean =
        withContext(Dispatchers.IO) {
            val deviceId = deviceManager.getPhoneDeviceId()
            if (deviceId == null) {
                Log.e(TAG, "Cannot play: no device available")
                return@withContext false
            }

            val body = when {
                // 1. Play URI list (for Liked Songs queue)
                uris != null && uris.isNotEmpty() -> {
                    PlayRequest(uris = uris)
                }
                // 2. Liked Songs workaround (Spotify API rejects "collection" as a context_uri)
                contextUri == "spotify:user:me:collection" && trackUri != null -> {
                    PlayRequest(uris = listOf(trackUri))
                }
                // 3. Play a specific track in a playlist by its index position
                contextUri != null && offsetPosition != null -> {
                    PlayRequest(contextUri = contextUri, offset = PlayOffset(position = offsetPosition))
                }
                // 4. Play a specific track in a playlist by its URI
                contextUri != null && trackUri != null -> {
                    PlayRequest(contextUri = contextUri, offset = PlayOffset(uri = trackUri))
                }
                // 5. "Play All" a playlist or album from the beginning
                contextUri != null -> {
                    PlayRequest(contextUri = contextUri)
                }
                // 6. Play a single standalone track (Queue)
                trackUri != null -> {
                    PlayRequest(uris = listOf(trackUri))
                }
                // 7. Resume current playback
                else -> null
            }

            return@withContext executeWithRetry("play") {
                api.play(deviceId = it, body = body)
            }
        }

    /**
     * Resume playback on the phone (no track specified).
     */
    suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        return@withContext executeWithRetry("resume") {
            api.play(deviceId = it, body = null)
        }
    }

    /**
     * Aggressively resumes playback by transferring the session to the phone
     * and forcing it to play its local queue (bypassing cloud session checks).
     */
    suspend fun forceResume(): Boolean = withContext(Dispatchers.IO) {
        return@withContext executeWithRetry("forceResume") { deviceId ->
            if (deviceId != null) {
                api.transferPlayback(
                    com.cloudbridge.spotify.network.model.TransferPlaybackRequest(
                        deviceIds = listOf(deviceId),
                        play = true
                    )
                )
            } else {
                // Fallback if no device ID is somehow found
                api.play(deviceId = null, body = null)
            }
        }
    }

    suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        return@withContext executeWithRetry("pause") {
            api.pause(deviceId = it)
        }
    }

    suspend fun next(): Boolean = withContext(Dispatchers.IO) {
        return@withContext executeWithRetry("next") {
            api.next(deviceId = it)
        }
    }

    suspend fun previous(): Boolean = withContext(Dispatchers.IO) {
        return@withContext executeWithRetry("previous") {
            api.previous(deviceId = it)
        }
    }

    /**
     * Get the current playback state from Spotify.
     * Used for metadata sync (updating the car screen's now-playing card).
     *
     * Returns null if nothing is currently playing (HTTP 204).
     * Throws HttpException for 429 rate limit errors so ViewModel can handle backoff.
     */
    suspend fun getCurrentPlayback(): CurrentPlaybackResponse? = withContext(Dispatchers.IO) {
        try {
            val response = api.getCurrentPlayback()
            if (response.isSuccessful) {
                response.body()
            } else {
                if (response.code() == 429) {
                    throw retrofit2.HttpException(response)
                }
                Log.w(TAG, "getCurrentPlayback failed: ${response.code()}")
                null
            }
        } catch (e: retrofit2.HttpException) {
            throw e // Propagate the 429 to the ViewModel
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentPlayback error: ${e.message}", e)
            null
        }
    }

    /**
     * Toggle shuffle mode on/off.
     */
    suspend fun setShuffle(state: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceId = deviceManager.getPhoneDeviceId()
            val response = api.setShuffle(state = state, deviceId = deviceId)
            val success = response.code() in listOf(200, 202, 204)
            Log.d(TAG, "setShuffle($state): ${response.code()} (success=$success)")
            success
        } catch (e: Exception) {
            Log.e(TAG, "setShuffle failed: ${e.message}", e)
            false
        }
    }

    /**
     * Set repeat mode: "track", "context", or "off".
     */
    suspend fun setRepeat(state: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceId = deviceManager.getPhoneDeviceId()
            val response = api.setRepeat(state = state, deviceId = deviceId)
            val success = response.code() in listOf(200, 202, 204)
            Log.d(TAG, "setRepeat($state): ${response.code()} (success=$success)")
            success
        } catch (e: Exception) {
            Log.e(TAG, "setRepeat failed: ${e.message}", e)
            false
        }
    }

    /**
     * Seek to a position in the current track.
     */
    suspend fun seek(positionMs: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceId = deviceManager.getPhoneDeviceId()
            val response = api.seek(positionMs = positionMs, deviceId = deviceId)
            val success = response.code() in listOf(200, 202, 204)
            Log.d(TAG, "seek($positionMs): ${response.code()} (success=$success)")
            success
        } catch (e: Exception) {
            Log.e(TAG, "seek failed: ${e.message}", e)
            false
        }
    }

    // ── Private Helpers ──────────────────────────────────────────────

    /**
     * Execute a playback command with **one automatic retry on HTTP 404**.
     *
     * Retry logic:
     * 1. Sends the command with the currently cached device ID.
     * 2. If the response is 404 ("Device not found"), calls
     *    [DeviceManager.refreshDeviceId] to discover a new device.
     * 3. If a different device ID is returned, retries the command once.
     * 4. Any other response code is returned as-is.
     *
     * @param commandName Human-readable label for log messages.
     * @param command     Lambda that takes a device ID and returns a Retrofit [Response].
     * @return `true` if the command succeeded (HTTP 200/202/204), `false` otherwise.
     */
    private suspend fun executeWithRetry(
        commandName: String,
        command: suspend (deviceId: String?) -> retrofit2.Response<Unit>
    ): Boolean {
        // Prefer the user's phone; fall back to null so Spotify routes to the
        // last active device automatically (avoids silent no-op when device list
        // is temporarily empty, e.g. on the emulator or after a BT reconnect).
        val deviceId = deviceManager.getPhoneDeviceId()
        if (deviceId == null) Log.w(TAG, "$commandName: no phone device found, letting Spotify choose")

        try {
            val response = command(deviceId)

            when (response.code()) {
                200, 202, 204 -> {
                    Log.d(TAG, "$commandName: success (${response.code()})")
                    return true
                }
                404 -> {
                    // Device might have gone away. Refresh and retry once.
                    Log.w(TAG, "$commandName: 404 Device not found. Refreshing device list...")
                    val newDeviceId = deviceManager.refreshDeviceId()
                    if (newDeviceId != null && newDeviceId != deviceId) {
                        val retryResponse = command(newDeviceId)
                        val success = retryResponse.code() in listOf(200, 202, 204)
                        Log.d(TAG, "$commandName retry: ${retryResponse.code()} (success=$success)")
                        return success
                    }
                    return false
                }
                403 -> {
                    Log.e(TAG, "$commandName: 403 Forbidden. Device may be restricted or user lacks Premium.")
                    return false
                }
                else -> {
                    Log.e(TAG, "$commandName: unexpected response ${response.code()}")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "$commandName failed: ${e.message}", e)
            return false
        }
    }
}
