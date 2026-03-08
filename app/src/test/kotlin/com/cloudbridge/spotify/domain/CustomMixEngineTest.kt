package com.cloudbridge.spotify.domain

import com.cloudbridge.spotify.data.CustomMixDataSource
import com.cloudbridge.spotify.network.model.SpotifyAlbum
import com.cloudbridge.spotify.network.model.SpotifyShow
import com.cloudbridge.spotify.network.model.SpotifyTrack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomMixEngineTest {

    @Test
    fun `decade mix filters liked songs and interleaves recommendations`() = runTest {
        val engine = CustomMixEngine(
            dataSource = FakeCustomMixDataSource(
                likedTracks = listOf(
                    track(id = "1", uri = "spotify:track:80a", releaseDate = "1984-01-01"),
                    track(id = "2", uri = "spotify:track:80b", releaseDate = "1989-01-01"),
                    track(id = "3", uri = "spotify:track:90a", releaseDate = "1994-01-01")
                ),
                recommendations = listOf(
                    track(id = "4", uri = "spotify:track:rec1"),
                    track(id = "5", uri = "spotify:track:rec2")
                )
            )
        )

        val result = engine.buildDecadeMix("198")

        assertEquals(4, result.size)
        assertTrue(result.contains("spotify:track:80a"))
        assertTrue(result.contains("spotify:track:80b"))
        assertTrue(result.contains("spotify:track:rec1"))
        assertTrue(result.contains("spotify:track:rec2"))
        assertTrue(result.none { it == "spotify:track:90a" })
        assertTrue(result[2].startsWith("spotify:track:rec"))
    }

    @Test
    fun `daily drive starts with podcasts then alternates music without duplicates`() = runTest {
        val engine = CustomMixEngine(
            dataSource = FakeCustomMixDataSource(
                likedTracks = listOf(
                    track(id = "1", uri = "spotify:track:l1"),
                    track(id = "2", uri = "spotify:track:l2"),
                    track(id = "3", uri = "spotify:track:l3"),
                    track(id = "4", uri = "spotify:track:l4")
                ),
                savedShows = listOf(
                    SpotifyShow(id = "news", name = "News", uri = "spotify:show:news"),
                    SpotifyShow(id = "general", name = "General", uri = "spotify:show:general")
                ),
                latestEpisodes = mapOf(
                    "news" to "spotify:episode:news1",
                    "general" to "spotify:episode:general1"
                ),
                recommendations = listOf(
                    track(id = "5", uri = "spotify:track:r1"),
                    track(id = "6", uri = "spotify:track:r2"),
                    track(id = "7", uri = "spotify:track:l3")
                )
            )
        )

        val result = engine.buildDailyDrive(newsShowId = "news")

        assertEquals("spotify:episode:news1", result[0])
        assertTrue(result.take(5).contains("spotify:episode:general1"))
        assertTrue(result.take(5).any { it.startsWith("spotify:track:l") })
        assertTrue(result.take(5).any { it.startsWith("spotify:track:r") })
        assertTrue(result.any { it == "spotify:track:l3" || it == "spotify:track:l4" })
        assertTrue(result.contains("spotify:track:r2"))
        assertEquals(result.distinct().size, result.size)
    }

    private fun track(id: String, uri: String, releaseDate: String? = null): SpotifyTrack = SpotifyTrack(
        id = id,
        name = id,
        uri = uri,
        durationMs = 180_000,
        album = SpotifyAlbum(id = "album-$id", name = "Album $id", releaseDate = releaseDate)
    )

    private class FakeCustomMixDataSource(
        private val likedTracks: List<SpotifyTrack> = emptyList(),
        private val savedShows: List<SpotifyShow> = emptyList(),
        private val latestEpisodes: Map<String, String> = emptyMap(),
        private val recommendations: List<SpotifyTrack> = emptyList()
    ) : CustomMixDataSource {
        override suspend fun getSavedTracks(maxTracks: Int): List<SpotifyTrack> = likedTracks.take(maxTracks)

        override suspend fun getSavedShows(): List<SpotifyShow> = savedShows

        override suspend fun getLatestEpisodeUri(showId: String): String? = latestEpisodes[showId]

        override suspend fun getRecommendations(seedTrackIds: List<String>, limit: Int): List<SpotifyTrack> =
            recommendations.take(limit)
    }
}
