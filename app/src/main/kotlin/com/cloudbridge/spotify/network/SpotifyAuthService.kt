package com.cloudbridge.spotify.network

import com.cloudbridge.spotify.network.model.TokenResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit interface for the Spotify Accounts API (accounts.spotify.com).
 *
 * Separate from [SpotifyApiService] because:
 * 1. Different base URL (accounts.spotify.com vs api.spotify.com)
 * 2. This must NOT use the AuthInterceptor (would create a circular dependency)
 * 3. Uses form-encoded POST instead of JSON
 */
interface SpotifyAuthService {

    /**
     * Refresh an expired access token using the long-lived refresh token.
     *
     * Uses HTTP Basic authentication (Base64-encoded clientId:clientSecret in the
     * Authorization header), which is the standard OAuth 2.0 mechanism for
     * confidential clients per the Spotify API specification.
     */
    @FormUrlEncoded
    @POST("api/token")
    suspend fun refreshToken(
        @Header("Authorization") authHeader: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): TokenResponse
}
