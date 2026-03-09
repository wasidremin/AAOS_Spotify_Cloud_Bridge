package com.cloudbridge.spotify.domain

import com.cloudbridge.spotify.data.CustomMixDataSource
import com.cloudbridge.spotify.network.model.SpotifyShow

class CustomMixEngine(
    private val dataSource: CustomMixDataSource,
    private val maxGeneratedUris: Int = 100
) {

    suspend fun buildDecadeMix(decadePrefix: String): List<String> {
        val ownedTracks = dataSource.getSavedTracks(maxTracks = 200)
            .filter { track ->
                track.uri.isNotBlank() && track.album?.releaseDate?.startsWith(decadePrefix) == true
            }
            .shuffled()

        if (ownedTracks.isEmpty()) return emptyList()

        val seedTrackIds = ownedTracks.mapNotNull { it.id }.distinct().shuffled().take(5)
        val recommendedTracks = dataSource.getRecommendations(seedTrackIds, limit = 50)
            .filter { it.uri.isNotBlank() }

        return buildTwoToOneMix(
            ownedUris = ownedTracks.map { it.uri },
            recommendedUris = recommendedTracks.map { it.uri }
        )
    }

    suspend fun buildDailyDrive(newsShowId: String, savedShows: List<SpotifyShow>? = null): List<String> {
        val shows = savedShows ?: dataSource.getSavedShows()
        val podcastEpisodeUris = buildList {
            dataSource.getLatestEpisodeUri(newsShowId)?.let(::add)
            shows.filter { it.id != newsShowId }.forEach { show ->
                dataSource.getLatestEpisodeUri(show.id)?.let(::add)
            }
        }.distinct()

        val likedTracks = dataSource.getSavedTracks(maxTracks = 50)
            .filter { it.uri.isNotBlank() }
            .shuffled()
            .distinctBy { it.uri }
        val selectedLikedTracks = likedTracks.take(10)
        val seedTrackIds = selectedLikedTracks.mapNotNull { it.id }.distinct().take(5)
        val recommendedTracks = dataSource.getRecommendations(seedTrackIds, limit = 10)
            .filter { it.uri.isNotBlank() }
            .distinctBy { it.uri }

        val likedUris = selectedLikedTracks.map { it.uri }
        val recommendedUris = recommendedTracks.map { it.uri }
        val musicUris = buildAlternatingMix(
            primaryUris = likedUris.drop(2),
            secondaryUris = recommendedUris
        )

        return buildPodcastLedDrive(
            podcastUris = podcastEpisodeUris,
            leadInMusicUris = likedUris.take(2),
            musicUris = musicUris
        )
    }

    private fun buildTwoToOneMix(ownedUris: List<String>, recommendedUris: List<String>): List<String> {
        val mixed = mutableListOf<String>()
        val seenUris = mutableSetOf<String>()
        var ownedIndex = 0
        var recommendedIndex = 0

        while (ownedIndex < ownedUris.size || recommendedIndex < recommendedUris.size) {
            repeat(2) {
                if (ownedIndex < ownedUris.size) {
                    appendUnique(mixed, seenUris, ownedUris[ownedIndex])
                    ownedIndex++
                }
            }
            if (recommendedIndex < recommendedUris.size) {
                appendUnique(mixed, seenUris, recommendedUris[recommendedIndex])
                recommendedIndex++
            }

            if (mixed.size >= maxGeneratedUris) break
        }

        return mixed.take(maxGeneratedUris)
    }

    private fun buildAlternatingMix(primaryUris: List<String>, secondaryUris: List<String>): List<String> {
        val mixed = mutableListOf<String>()
        val seenUris = mutableSetOf<String>()
        val maxSize = maxOf(primaryUris.size, secondaryUris.size)

        for (index in 0 until maxSize) {
            primaryUris.getOrNull(index)?.let { appendUnique(mixed, seenUris, it) }
            secondaryUris.getOrNull(index)?.let { appendUnique(mixed, seenUris, it) }
            if (mixed.size >= maxGeneratedUris) break
        }

        return mixed.take(maxGeneratedUris)
    }

    private fun buildPodcastLedDrive(
        podcastUris: List<String>,
        leadInMusicUris: List<String>,
        musicUris: List<String>
    ): List<String> {
        val mixed = mutableListOf<String>()
        val seenUris = mutableSetOf<String>()
        val musicQueue = ArrayDeque(leadInMusicUris + musicUris)

        podcastUris.forEachIndexed { index, podcastUri ->
            if (index > 0 && musicQueue.isEmpty()) return@forEachIndexed
            appendUnique(mixed, seenUris, podcastUri)

            val songsAfterPodcast = 2
            repeat(songsAfterPodcast) {
                val nextSong = musicQueue.removeFirstOrNull() ?: return@repeat
                appendUnique(mixed, seenUris, nextSong)
            }

            if (mixed.size >= maxGeneratedUris) return mixed.take(maxGeneratedUris)
        }

        while (musicQueue.isNotEmpty() && mixed.size < maxGeneratedUris) {
            appendUnique(mixed, seenUris, musicQueue.removeFirst())
        }

        return mixed.take(maxGeneratedUris)
    }

    private fun appendUnique(target: MutableList<String>, seen: MutableSet<String>, uri: String) {
        if (uri.isBlank() || !seen.add(uri)) return
        target += uri
    }
}
