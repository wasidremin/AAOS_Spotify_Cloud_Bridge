package com.cloudbridge.spotify.network

import com.cloudbridge.spotify.network.model.CloudSessionPayload
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

interface CloudRelayService {

    @GET("sessions/{sessionId}.json")
    suspend fun getSession(
        @Path("sessionId") sessionId: String
    ): CloudSessionPayload?

    @DELETE("sessions/{sessionId}.json")
    suspend fun deleteSession(
        @Path("sessionId") sessionId: String
    ): Response<Unit>
}