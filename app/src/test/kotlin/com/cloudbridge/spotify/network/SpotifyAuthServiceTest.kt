package com.cloudbridge.spotify.network

import com.cloudbridge.spotify.network.model.TokenResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Integration tests for [SpotifyAuthService].
 *
 * Uses [MockWebServer] to simulate the Spotify Accounts API
 * (`accounts.spotify.com/api/token`). Verifies the form-encoded
 * refresh-token grant and error handling.
 */
class SpotifyAuthServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var authService: SpotifyAuthService

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        authService = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SpotifyAuthService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `refreshToken sends correct form-encoded body`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "access_token": "new_access_token",
                        "token_type": "Bearer",
                        "expires_in": 3600,
                        "scope": "user-library-read"
                    }
                """.trimIndent())
        )

        val response = authService.refreshToken(
            authHeader = "Basic dGVzdF9jbGllbnQ6dGVzdF9zZWNyZXQ=",
            refreshToken = "test_refresh"
        )

        assertNotNull(response)
        assertEquals("new_access_token", response.accessToken)
        assertEquals("Bearer", response.tokenType)
        assertEquals(3600, response.expiresIn)

        // Verify the form body and auth header
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("Basic dGVzdF9jbGllbnQ6dGVzdF9zZWNyZXQ=", request.getHeader("Authorization"))
        val formBody = request.body.readUtf8()
        assertTrue(formBody.contains("grant_type=refresh_token"))
        assertTrue(formBody.contains("refresh_token=test_refresh"))
    }

    @Test
    fun `refreshToken handles 401 error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid_grant"}"""))

        try {
            authService.refreshToken(
                authHeader = "Basic YmFkX2NsaWVudDpiYWRfc2VjcmV0",
                refreshToken = "bad_refresh"
            )
            fail("Expected HttpException")
        } catch (e: retrofit2.HttpException) {
            assertEquals(401, e.code())
        }
    }
}
