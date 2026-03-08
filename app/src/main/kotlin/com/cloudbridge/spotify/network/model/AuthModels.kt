package com.cloudbridge.spotify.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─── Authentication ──────────────────────────────────────────────────
/**
 * Response body from `POST /api/token` on `accounts.spotify.com`.
 *
 * Returned by the Spotify Accounts Service when a refresh-token grant
 * is exchanged for a new short-lived access token.
 *
 * @property accessToken The new Bearer token (typically valid for 3600 s).
 * @property tokenType   Always `"Bearer"`.
 * @property expiresIn   Time-to-live in seconds.
 * @property scope       Space-separated list of granted scopes (may be `null`).
 */@JsonClass(generateAdapter = true)
data class TokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "expires_in") val expiresIn: Int,
    @Json(name = "scope") val scope: String? = null
)
