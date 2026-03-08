package com.cloudbridge.spotify.data

import android.util.Log
import com.cloudbridge.spotify.cache.CacheDatabase
import com.cloudbridge.spotify.cache.CachedAlbum
import com.cloudbridge.spotify.cache.CachedPlaylist
import com.cloudbridge.spotify.cache.CachedShow
import com.cloudbridge.spotify.cache.CleanTrackMapping
import com.cloudbridge.spotify.network.SpotifyApiService
import com.cloudbridge.spotify.network.model.PlaylistTracksRef
import com.cloudbridge.spotify.network.model.SpotifyAlbum
import com.cloudbridge.spotify.network.model.SpotifyArtist
import com.cloudbridge.spotify.network.model.SpotifyEpisode
import com.cloudbridge.spotify.network.model.SpotifyImage
import com.cloudbridge.spotify.network.model.SpotifyPlaylist
import com.cloudbridge.spotify.network.model.SpotifyShow
import com.cloudbridge.spotify.network.model.SpotifyTrack

interface CustomMixDataSource {
    suspend fun getSavedTracks(maxTracks: Int): List<SpotifyTrack>
    suspend fun getSavedShows(): List<SpotifyShow>
    suspend fun getLatestEpisodeUri(showId: String): String?
    suspend fun getRecommendations(seedTrackIds: List<String>, limit: Int): List<SpotifyTrack>
}

class SpotifyLibraryRepository(
    private val api: SpotifyApiService,
    private val cacheDb: CacheDatabase
) : CustomMixDataSource {

    private val cleanTrackMappingDao = cacheDb.cleanTrackMappingDao()

    companion object {
        private const val TAG = "SpotifyLibraryRepository"
    }

    suspend fun getCachedPlaylists(): List<SpotifyPlaylist> =
        cacheDb.libraryCacheDao().getAllPlaylists().map { it.toSpotifyPlaylist() }

    suspend fun refreshPlaylists(): List<SpotifyPlaylist> {
        var offset = 0
        val fresh = mutableListOf<SpotifyPlaylist>()
        do {
            val page = api.getPlaylists(limit = 50, offset = offset)
            fresh += page.items.filterNotNull()
            offset += 50
        } while (page.next != null && offset < page.total)

        cacheDb.libraryCacheDao().apply {
            clearPlaylists()
            insertPlaylists(fresh.map { playlist -> playlist.toCachedPlaylist() })
        }

        return fresh
    }

    suspend fun getCachedAlbums(): List<SpotifyAlbum> =
        cacheDb.libraryCacheDao().getAllAlbums().map { it.toSpotifyAlbum() }

    suspend fun refreshSavedAlbums(): List<SpotifyAlbum> {
        var offset = 0
        val fresh = mutableListOf<SpotifyAlbum>()
        do {
            val page = api.getSavedAlbums(limit = 50, offset = offset)
            fresh += page.items.filterNotNull().map { it.album }
            offset += 50
        } while (page.next != null && offset < page.total)

        cacheDb.libraryCacheDao().apply {
            clearAlbums()
            insertAlbums(
                fresh.mapNotNull { album ->
                    val id = album.id ?: return@mapNotNull null
                    val uri = album.uri ?: return@mapNotNull null
                    CachedAlbum(
                        id = id,
                        name = album.name,
                        uri = uri,
                        imageUrl = album.images?.firstOrNull()?.url,
                        artistName = album.artists?.firstOrNull()?.name,
                        albumType = album.albumType,
                        releaseDate = album.releaseDate
                    )
                }
            )
        }

        return fresh
    }

    suspend fun getCachedShows(): List<SpotifyShow> =
        cacheDb.libraryCacheDao().getAllShows().map { it.toSpotifyShow() }

    suspend fun refreshSavedShows(): List<SpotifyShow> {
        var offset = 0
        val fresh = mutableListOf<SpotifyShow>()
        do {
            val page = api.getSavedShows(limit = 50, offset = offset)
            fresh += page.items.filterNotNull().map { it.show }
            offset += 50
        } while (page.next != null && offset < page.total)

        cacheDb.libraryCacheDao().apply {
            clearShows()
            insertShows(fresh.map { show -> show.toCachedShow() })
        }

        return fresh
    }

    override suspend fun getSavedTracks(maxTracks: Int): List<SpotifyTrack> {
        val collected = mutableListOf<SpotifyTrack>()
        var offset = 0

        while (collected.size < maxTracks) {
            val page = api.getSavedTracks(limit = 50, offset = offset)
            val tracks = page.items.filterNotNull().map { it.track }
            if (tracks.isEmpty()) break

            collected += tracks
            if (page.next == null || collected.size >= maxTracks) break
            offset += page.limit
        }

        return collected.distinctBy { it.uri }.take(maxTracks)
    }

    suspend fun getShowEpisodes(showId: String, maxEpisodes: Int = Int.MAX_VALUE): List<SpotifyEpisode> {
        val episodes = mutableListOf<SpotifyEpisode>()
        var offset = 0

        while (episodes.size < maxEpisodes) {
            val page = api.getShowEpisodes(showId = showId, limit = 50, offset = offset)
            val items = page.items.filterNotNull()
            if (items.isEmpty()) break

            episodes += items
            if (page.next == null || episodes.size >= maxEpisodes) break
            offset += page.limit
        }

        return episodes.take(maxEpisodes)
    }

    override suspend fun getSavedShows(): List<SpotifyShow> = refreshSavedShows()

    override suspend fun getLatestEpisodeUri(showId: String): String? {
        if (showId.isBlank()) return null
        return try {
            api.getShowEpisodes(showId = showId, limit = 1).items.firstOrNull()?.uri
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch latest episode for show $showId: ${e.message}")
            null
        }
    }

    override suspend fun getRecommendations(seedTrackIds: List<String>, limit: Int): List<SpotifyTrack> {
        if (seedTrackIds.isEmpty()) return emptyList()
        return try {
            api.getRecommendations(
                limit = limit,
                seedTracks = seedTrackIds.take(5).joinToString(",")
            ).tracks
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch recommendations for seeds ${seedTrackIds.joinToString(",")}: ${e.message}")
            emptyList()
        }
    }

    suspend fun getTrack(trackId: String): SpotifyTrack = api.getTrack(trackId)

    suspend fun resolvePlaybackUri(track: SpotifyTrack, preferClean: Boolean): String {
        if (!preferClean || !track.explicit) return track.uri

        cleanTrackMappingDao.getReplacementUri(track.uri)?.let { return it }

        val replacementUri = findCleanReplacement(track)?.uri ?: track.uri
        cleanTrackMappingDao.insert(
            CleanTrackMapping(
                explicitUri = track.uri,
                replacementUri = replacementUri
            )
        )
        return replacementUri
    }

    private suspend fun findCleanReplacement(track: SpotifyTrack): SpotifyTrack? {
        val primaryArtist = track.artists.firstOrNull()?.name.orEmpty()
        val query = listOf(track.name, primaryArtist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        if (query.isBlank()) return null

        return try {
            api.search(
                query = query,
                type = "track",
                limit = 10
            ).tracks?.items
                ?.filterNotNull()
                ?.filter { candidate ->
                    candidate.uri != track.uri &&
                        candidate.isPlayable != false &&
                        !candidate.explicit
                }
                ?.maxByOrNull { candidate -> cleanCandidateScore(track, candidate) }
                ?.takeIf { cleanCandidateScore(track, it) >= 3 }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve clean replacement for ${track.uri}: ${e.message}")
            null
        }
    }

    private fun cleanCandidateScore(source: SpotifyTrack, candidate: SpotifyTrack): Int {
        val sourceTitle = normalizeMatchKey(source.name)
        val candidateTitle = normalizeMatchKey(candidate.name)
        val sourceArtists = source.artists.map { normalizeMatchKey(it.name) }.filter { it.isNotBlank() }
        val candidateArtists = candidate.artists.map { normalizeMatchKey(it.name) }.filter { it.isNotBlank() }
        val sourceAlbum = normalizeMatchKey(source.album?.name)
        val candidateAlbum = normalizeMatchKey(candidate.album?.name)

        var score = 0
        if (candidateTitle == sourceTitle) score += 4
        else if (candidateTitle.contains(sourceTitle) || sourceTitle.contains(candidateTitle)) score += 2
        score += candidateArtists.count { it in sourceArtists } * 2
        if (sourceAlbum.isNotBlank() && sourceAlbum == candidateAlbum) score += 1
        return score
    }

    private fun normalizeMatchKey(value: String?): String = value
        .orEmpty()
        .lowercase()
        .replace(Regex("""\\((.*?)\\)|\\[(.*?)]"""), " ")
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()

    private fun CachedPlaylist.toSpotifyPlaylist(): SpotifyPlaylist = SpotifyPlaylist(
        id = id,
        name = name,
        uri = uri,
        images = imageUrl?.let { listOf(SpotifyImage(url = it)) },
        description = description,
        tracks = PlaylistTracksRef(trackCount)
    )

    private fun SpotifyPlaylist.toCachedPlaylist(): CachedPlaylist = CachedPlaylist(
        id = id,
        name = name,
        uri = uri,
        imageUrl = images?.firstOrNull()?.url,
        description = description,
        trackCount = tracks?.total ?: 0
    )

    private fun CachedAlbum.toSpotifyAlbum(): SpotifyAlbum = SpotifyAlbum(
        id = id,
        name = name,
        uri = uri,
        images = imageUrl?.let { listOf(SpotifyImage(url = it)) },
        artists = artistName?.let { listOf(SpotifyArtist(id = null, name = it)) },
        albumType = albumType,
        releaseDate = releaseDate
    )

    private fun CachedShow.toSpotifyShow(): SpotifyShow = SpotifyShow(
        id = id,
        name = name,
        uri = uri,
        publisher = publisher,
        description = description,
        images = imageUrl?.let { listOf(SpotifyImage(url = it)) }
    )

    private fun SpotifyShow.toCachedShow(): CachedShow = CachedShow(
        id = id,
        name = name,
        uri = uri,
        imageUrl = images?.firstOrNull()?.url,
        publisher = publisher,
        description = description
    )
}
