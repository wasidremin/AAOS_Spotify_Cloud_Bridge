package com.cloudbridge.spotify.util

/**
 * A generic sealed class for wrapping API call results.
 *
 * Eliminates raw try/catch blocks at every call site by encapsulating
 * success data or error information in a type-safe wrapper.
 *
 * Usage:
 * ```kotlin
 * val result = ApiResult.runCatching { api.getPlaylists() }
 * when (result) {
 *     is ApiResult.Success -> handleData(result.data)
 *     is ApiResult.Error   -> showError(result.message)
 * }
 * ```
 *
 * @param T The type of the success payload.
 */
sealed class ApiResult<out T> {
    /** Successful API response containing [data]. */
    data class Success<T>(val data: T) : ApiResult<T>()

    /** Failed API response with a human-readable [message] and optional HTTP [code]. */
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()

    /** `true` if this is a [Success] instance. */
    val isSuccess: Boolean get() = this is Success

    /** `true` if this is an [Error] instance. */
    val isError: Boolean get() = this is Error

    /** Returns the success data or `null` if this is an [Error]. */
    fun getOrNull(): T? = (this as? Success)?.data

    /** Returns the success data or throws a [RuntimeException] with the error details. */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw RuntimeException("API Error ($code): $message")
    }

    companion object {
        /**
         * Wraps a suspend [block] into an [ApiResult], catching all exceptions.
         *
         * @param block The suspend lambda to execute.
         * @return [Success] with the block's return value, or [Error] with the exception message.
         */
        suspend fun <T> runCatching(block: suspend () -> T): ApiResult<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Error(e.message ?: "Unknown error")
            }
        }
    }
}
