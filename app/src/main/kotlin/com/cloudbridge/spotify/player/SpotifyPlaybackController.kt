package com.cloudbridge.spotify.player

import com.cloudbridge.spotify.network.SpotifyApiService
import com.cloudbridge.spotify.network.model.CurrentPlaybackResponse
import com.cloudbridge.spotify.network.model.PlayOffset
import com.cloudbridge.spotify.network.model.PlayRequest
import com.cloudbridge.spotify.network.model.TransferPlaybackRequest
import com.cloudbridge.spotify.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Thin Spotify Web API playback command wrapper.
 *
 * Device resolution and session recovery live in [PlaybackSessionManager].
 * This class only issues HTTP commands against an already-chosen device_id
 * (or null to let Spotify pick the active session).
 *
 * On HTTP 404, retries once with `device_id=null` so a stale id from a
 * Connect blind spot does not hard-fail. It does **not** re-discover devices.
 *
 * Returns [PlaybackCommandResult] so the session manager can treat 403
 * Forbidden differently from 404 / asleep (no AVRCP on forbidden).
 */
class SpotifyPlaybackController(
    private val api: SpotifyApiService
) {
    companion object {
        private const val TAG = "PlaybackController"
    }

    suspend fun play(
        deviceId: String?,
        trackUri: String? = null,
        contextUri: String? = null,
        offsetPosition: Int? = null,
        uris: List<String>? = null
    ): PlaybackCommandResult = withContext(Dispatchers.IO) {
        val body = buildPlayRequest(trackUri, contextUri, offsetPosition, uris)
        execute("play", deviceId) { id -> api.play(deviceId = id, body = body) }
    }

    suspend fun pause(deviceId: String?): PlaybackCommandResult = withContext(Dispatchers.IO) {
        execute("pause", deviceId) { api.pause(deviceId = it) }
    }

    suspend fun next(deviceId: String?): PlaybackCommandResult = withContext(Dispatchers.IO) {
        execute("next", deviceId) { api.next(deviceId = it) }
    }

    suspend fun previous(deviceId: String?): PlaybackCommandResult = withContext(Dispatchers.IO) {
        execute("previous", deviceId) { api.previous(deviceId = it) }
    }

    suspend fun resume(deviceId: String?): PlaybackCommandResult = withContext(Dispatchers.IO) {
        // Empty body resumes current context; Retrofit rejects a literal null @Body.
        execute("resume", deviceId) { api.play(deviceId = it, body = PlayRequest()) }
    }

    /**
     * Transfer playback to [deviceId] and force play (or resume without id).
     */
    suspend fun forceResume(deviceId: String?): PlaybackCommandResult = withContext(Dispatchers.IO) {
        execute("forceResume", deviceId) { id ->
            if (id != null) {
                api.transferPlayback(
                    TransferPlaybackRequest(deviceIds = listOf(id), play = true)
                )
            } else {
                // 404 fallback path — resume active session without a device id.
                api.play(deviceId = null, body = PlayRequest())
            }
        }
    }

    suspend fun addToQueue(deviceId: String?, uri: String): PlaybackCommandResult =
        withContext(Dispatchers.IO) {
            execute("addToQueue", deviceId) {
                api.addToQueue(uri = uri, deviceId = it)
            }
        }

    suspend fun setShuffle(deviceId: String?, state: Boolean): PlaybackCommandResult =
        withContext(Dispatchers.IO) {
            execute("setShuffle", deviceId) {
                api.setShuffle(state = state, deviceId = it)
            }
        }

    suspend fun setRepeat(deviceId: String?, state: String): PlaybackCommandResult =
        withContext(Dispatchers.IO) {
            execute("setRepeat", deviceId) {
                api.setRepeat(state = state, deviceId = it)
            }
        }

    suspend fun seek(deviceId: String?, positionMs: Long): PlaybackCommandResult =
        withContext(Dispatchers.IO) {
            execute("seek", deviceId) {
                api.seek(positionMs = positionMs, deviceId = it)
            }
        }

    /**
     * Current playback state for metadata sync.
     * Returns null on HTTP 204. Throws for 429 and transport failures.
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
                AppLogger.w(TAG, "getCurrentPlayback failed: ${response.code()}")
                null
            }
        } catch (e: retrofit2.HttpException) {
            throw e
        } catch (e: UnknownHostException) {
            AppLogger.w(TAG, "getCurrentPlayback offline: ${e.message}", e)
            throw e
        } catch (e: SocketTimeoutException) {
            AppLogger.e(TAG, "getCurrentPlayback timeout: ${e.message}", e)
            throw e
        } catch (e: IOException) {
            AppLogger.e(TAG, "getCurrentPlayback I/O error: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "getCurrentPlayback error: ${e.message}", e)
            null
        }
    }

    internal fun buildPlayRequest(
        trackUri: String?,
        contextUri: String?,
        offsetPosition: Int?,
        uris: List<String>?
    ): PlayRequest = when {
        uris != null && uris.isNotEmpty() -> PlayRequest(uris = uris)
        // Liked Songs workaround — API rejects collection as context_uri
        contextUri == "spotify:user:me:collection" && trackUri != null ->
            PlayRequest(uris = listOf(trackUri))
        contextUri != null && offsetPosition != null ->
            PlayRequest(contextUri = contextUri, offset = PlayOffset(position = offsetPosition))
        contextUri != null && trackUri != null ->
            PlayRequest(contextUri = contextUri, offset = PlayOffset(uri = trackUri))
        contextUri != null -> PlayRequest(contextUri = contextUri)
        trackUri != null -> PlayRequest(uris = listOf(trackUri))
        // Empty object resumes the active session (Retrofit cannot send a null @Body).
        else -> PlayRequest()
    }

    /**
     * Execute against [deviceId]. On 404, one retry with null so Spotify can
     * target the active session (stale id recovery only — no rediscovery).
     */
    private suspend fun execute(
        commandName: String,
        deviceId: String?,
        command: suspend (deviceId: String?) -> retrofit2.Response<Unit>
    ): PlaybackCommandResult {
        if (deviceId == null) {
            AppLogger.w(TAG, "$commandName: no device id, letting Spotify choose active session")
        }

        try {
            val response = command(deviceId)
            when (response.code()) {
                200, 202, 204 -> {
                    AppLogger.d(TAG, "$commandName: success (${response.code()})")
                    return PlaybackCommandResult.Success
                }
                404 -> {
                    if (deviceId != null) {
                        AppLogger.w(
                            TAG,
                            "$commandName: 404 Device not found; retrying without device_id"
                        )
                        val fallback = command(null)
                        return if (fallback.code() in listOf(200, 202, 204)) {
                            AppLogger.d(
                                TAG,
                                "$commandName active-session retry: ${fallback.code()} (success=true)"
                            )
                            PlaybackCommandResult.Success
                        } else {
                            AppLogger.d(
                                TAG,
                                "$commandName active-session retry: ${fallback.code()} (success=false)"
                            )
                            when (fallback.code()) {
                                403 -> PlaybackCommandResult.forbidden(403)
                                404 -> PlaybackCommandResult.notFound(404)
                                else -> PlaybackCommandResult.other(fallback.code())
                            }
                        }
                    }
                    return PlaybackCommandResult.notFound(404)
                }
                403 -> {
                    AppLogger.e(
                        TAG,
                        "$commandName: 403 Forbidden. Device may be restricted or user lacks Premium."
                    )
                    return PlaybackCommandResult.forbidden(403)
                }
                else -> {
                    AppLogger.e(TAG, "$commandName: unexpected response ${response.code()}")
                    return PlaybackCommandResult.other(response.code())
                }
            }
        } catch (e: IOException) {
            AppLogger.e(TAG, "$commandName network failure: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "$commandName failed: ${e.message}", e)
            return PlaybackCommandResult.other()
        }
    }
}
