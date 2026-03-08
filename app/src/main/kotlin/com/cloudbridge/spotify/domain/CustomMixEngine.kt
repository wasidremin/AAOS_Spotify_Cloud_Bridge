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
        val newsEpisodeUri = dataSource.getLatestEpisodeUri(newsShowId)
        val generalShowId = shows.firstOrNull { it.id != newsShowId }?.id
        val generalEpisodeUri = generalShowId?.let { dataSource.getLatestEpisodeUri(it) }

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
        val masterUriList = mutableListOf<String>()
        val seenUris = mutableSetOf<String>()

        newsEpisodeUri?.let { appendUnique(masterUriList, seenUris, it) }
        likedUris.take(2).forEach { appendUnique(masterUriList, seenUris, it) }
        recommendedUris.firstOrNull()?.let { appendUnique(masterUriList, seenUris, it) }
        generalEpisodeUri?.let { appendUnique(masterUriList, seenUris, it) }

        val interleavedRemainder = buildAlternatingMix(
            primaryUris = likedUris.drop(2),
            secondaryUris = recommendedUris.drop(1)
        )
        interleavedRemainder.forEach { appendUnique(masterUriList, seenUris, it) }

        return masterUriList.take(maxGeneratedUris)
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

    private fun appendUnique(target: MutableList<String>, seen: MutableSet<String>, uri: String) {
        if (uri.isBlank() || !seen.add(uri)) return
        target += uri
    }
}
