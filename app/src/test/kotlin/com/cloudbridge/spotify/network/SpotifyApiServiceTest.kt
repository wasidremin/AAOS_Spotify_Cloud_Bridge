package com.cloudbridge.spotify.network

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
 * Integration tests for [SpotifyApiService].
 *
 * Uses [MockWebServer] to simulate the Spotify Web API.
 * Each test enqueues a canned JSON response, executes the Retrofit
 * call, and asserts both the parsed model and the outgoing HTTP
 * request (method, path, body).
 *
 * Covers: getPlaylists, getPlaylistItems, generic library save/remove/contains,
 * getDevices, play, pause, next, previous, and getCurrentPlayback.
 */
class SpotifyApiServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: SpotifyApiService

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        apiService = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SpotifyApiService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPlaylists returns paginated results`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""
                {
                    "items": [
                        {
                            "id": "playlist_1",
                            "name": "My Playlist",
                            "description": "A test playlist",
                            "uri": "spotify:playlist:playlist_1",
                            "images": [{"url": "https://img.spotify.com/1.jpg", "width": 300, "height": 300}],
                            "items": {"total": 50}
                        }
                    ],
                    "total": 1,
                    "limit": 50,
                    "offset": 0,
                    "next": null
                }
            """.trimIndent())
        )

        val body = apiService.getPlaylists(limit = 50, offset = 0)

        assertEquals(1, body.items.size)
        assertEquals("playlist_1", body.items[0]!!.id)
        assertEquals("My Playlist", body.items[0]!!.name)
        assertEquals("spotify:playlist:playlist_1", body.items[0]!!.uri)
        assertEquals(50, body.items[0]!!.itemCount)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.contains("limit=50"))
        assertTrue(request.path!!.contains("offset=0"))
    }

    @Test
    fun `getPlaylistItems returns track items`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""
                {
                    "items": [
                        {
                            "item": {
                                "id": "track_1",
                                "name": "Test Song",
                                "uri": "spotify:track:track_1",
                                "type": "track",
                                "duration_ms": 210000,
                                "artists": [{"id": "artist_1", "name": "Test Artist"}],
                                "album": {
                                    "id": "album_1",
                                    "name": "Test Album",
                                    "images": [{"url": "https://img.spotify.com/album.jpg", "width": 300, "height": 300}]
                                }
                            }
                        }
                    ],
                    "total": 1,
                    "limit": 100,
                    "offset": 0,
                    "next": null
                }
            """.trimIndent())
        )

        val body = apiService.getPlaylistItems(
            playlistId = "playlist_1",
            limit = 50,
            offset = 0
        )

        assertEquals(1, body.items.size)
        val track = body.items[0]!!.track
        assertNotNull(track)
        assertEquals("track_1", track!!.id)
        assertEquals("Test Song", track.name)
        assertEquals(210000L, track.durationMs)
        assertEquals("Test Artist", track.artists.first().name)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.contains("/v1/playlists/playlist_1/items"))
    }

    @Test
    fun `getTrack parses explicit flag`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""
                {
                    "id": "track_explicit",
                    "name": "Explicit Song",
                    "uri": "spotify:track:track_explicit",
                    "duration_ms": 180000,
                    "explicit": true,
                    "artists": [{"id": "artist_1", "name": "Artist"}],
                    "album": {
                        "id": "album_1",
                        "name": "Album"
                    }
                }
            """.trimIndent())
        )

        val track = apiService.getTrack("track_explicit")

        assertEquals("track_explicit", track.id)
        assertTrue(track.explicit)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.contains("/v1/tracks/track_explicit"))
    }

    @Test
    fun `getDevices returns device list`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""
                {
                    "devices": [
                        {
                            "id": "device_123",
                            "is_active": true,
                            "is_restricted": false,
                            "name": "My Phone",
                            "type": "Smartphone",
                            "volume_percent": 75
                        },
                        {
                            "id": "device_456",
                            "is_active": false,
                            "is_restricted": false,
                            "name": "Living Room Speaker",
                            "type": "Speaker",
                            "volume_percent": 50
                        }
                    ]
                }
            """.trimIndent())
        )

        val body = apiService.getDevices()

        val devices = body.devices
        assertEquals(2, devices.size)
        assertEquals("device_123", devices[0].id)
        assertTrue(devices[0].isActive)
        assertEquals("Smartphone", devices[0].type)
    }

    @Test
    fun `play sends correct body with context URI`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val response = apiService.play(
            deviceId = "device_123",
            body = com.cloudbridge.spotify.network.model.PlayRequest(
                contextUri = "spotify:playlist:abc",
                offset = com.cloudbridge.spotify.network.model.PlayOffset(uri = "spotify:track:xyz"),
                uris = null
            )
        )

        assertTrue(response.isSuccessful)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertTrue(request.path!!.contains("device_id=device_123"))

        val bodyStr = request.body.readUtf8()
        assertTrue(bodyStr.contains("spotify:playlist:abc"))
        assertTrue(bodyStr.contains("spotify:track:xyz"))
    }

    @Test
    fun `pause sends PUT request`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val response = apiService.pause(deviceId = "device_123")

        assertTrue(response.isSuccessful)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertTrue(request.path!!.contains("/v1/me/player/pause"))
    }

    @Test
    fun `next sends POST request`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val response = apiService.next(deviceId = "device_123")

        assertTrue(response.isSuccessful)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("/v1/me/player/next"))
    }

    @Test
    fun `previous sends POST request`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val response = apiService.previous(deviceId = "device_123")

        assertTrue(response.isSuccessful)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("/v1/me/player/previous"))
    }

    @Test
    fun `getCurrentPlayback parses response`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""
                {
                    "is_playing": true,
                    "progress_ms": 45000,
                    "item": {
                        "id": "track_current",
                        "name": "Now Playing Song",
                        "uri": "spotify:track:track_current",
                        "duration_ms": 180000,
                        "artists": [{"id": "a1", "name": "Active Artist"}],
                        "album": {
                            "id": "alb1",
                            "name": "Current Album",
                            "images": [{"url": "https://img.spotify.com/now.jpg", "width": 640, "height": 640}]
                        }
                    },
                    "device": {
                        "id": "device_123",
                        "is_active": true,
                        "is_restricted": false,
                        "name": "My Phone",
                        "type": "Smartphone",
                        "volume_percent": 75
                    }
                }
            """.trimIndent())
        )

        val response = apiService.getCurrentPlayback()

        assertTrue(response.isSuccessful)
        val playback = response.body()!!
        assertTrue(playback.isPlaying)
        assertEquals(45000L, playback.progressMs)
        assertEquals("Now Playing Song", playback.item?.name)
        assertEquals("Active Artist", playback.item?.artists?.first()?.name)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.contains("additional_types=episode") && !request.path!!.contains("chapter"))
    }

    @Test
    fun `getCurrentPlayback parses audiobook chapters`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""
                {
                    "is_playing": true,
                    "progress_ms": 120000,
                    "item": {
                        "id": "chapter_1",
                        "name": "Chapter 1",
                        "uri": "spotify:chapter:chapter_1",
                        "type": "chapter",
                        "duration_ms": 600000,
                        "images": [{"url": "https://img.spotify.com/chapter.jpg", "width": 640, "height": 640}],
                        "audiobook": {
                            "id": "book_1",
                            "name": "Road Trip Stories",
                            "uri": "spotify:audiobook:book_1",
                            "publisher": "Spotify Books",
                            "authors": [{"name": "A. Writer"}]
                        }
                    },
                    "device": {
                        "id": "device_123",
                        "is_active": true,
                        "is_restricted": false,
                        "name": "My Phone",
                        "type": "Smartphone",
                        "volume_percent": 75
                    }
                }
            """.trimIndent())
        )

        val response = apiService.getCurrentPlayback()

        assertTrue(response.isSuccessful)
        val playback = response.body()!!
        assertEquals("chapter", playback.item?.type)
        assertEquals("Road Trip Stories", playback.item?.audiobook?.name)
        assertEquals("A. Writer", playback.item?.audiobook?.authors?.first()?.name)
    }

    @Test
    fun `library save remove and contains use generic me library endpoint`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(200).setBody("[true]"))

        val saveResponse = apiService.saveLibraryItems("spotify:track:track_1")
        val removeResponse = apiService.removeLibraryItems("spotify:track:track_1")
        val containsResponse = apiService.checkSavedLibraryItems("spotify:track:track_1")

        assertTrue(saveResponse.isSuccessful)
        assertTrue(removeResponse.isSuccessful)
        assertEquals(listOf(true), containsResponse)

        val saveRequest = server.takeRequest()
        assertEquals("PUT", saveRequest.method)
        assertTrue(saveRequest.path!!.contains("/v1/me/library"))
        assertTrue(saveRequest.path!!.contains("uris=spotify%3Atrack%3Atrack_1") || saveRequest.path!!.contains("uris=spotify:track:track_1"))

        val removeRequest = server.takeRequest()
        assertEquals("DELETE", removeRequest.method)
        assertTrue(removeRequest.path!!.contains("/v1/me/library"))

        val containsRequest = server.takeRequest()
        assertEquals("GET", containsRequest.method)
        assertTrue(containsRequest.path!!.contains("/v1/me/library/contains"))
    }
}
