package com.cloudbridge.spotify.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CloudSessionPayload(
    @Json(name = "clientId") val clientId: String,
    @Json(name = "clientSecret") val clientSecret: String? = null,
    @Json(name = "refreshToken") val refreshToken: String,
    @Json(name = "profileName") val profileName: String,
    @Json(name = "profileImageUrl") val profileImageUrl: String? = null
)