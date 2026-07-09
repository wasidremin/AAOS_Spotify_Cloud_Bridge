package com.cloudbridge.spotify.network

import com.cloudbridge.spotify.network.model.CloudSessionPayload
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

interface CloudRelayService {

    /**
     * Poll Firebase RTDB for a completed QR session.
     *
     * Missing sessions return HTTP 200 with body `null` (literal JSON null).
     * Use [Response.body] so that null is treated as "still waiting", not a
     * converter failure (Retrofit throws if the suspend return type is a
     * non-Response Kotlin type and the body is null).
     */
    @GET("sessions/{sessionId}.json")
    suspend fun getSession(
        @Path("sessionId") sessionId: String
    ): Response<CloudSessionPayload>

    @DELETE("sessions/{sessionId}.json")
    suspend fun deleteSession(
        @Path("sessionId") sessionId: String
    ): Response<Unit>
}