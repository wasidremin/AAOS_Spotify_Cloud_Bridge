package com.cloudbridge.spotify.ui.coordinator

import com.cloudbridge.spotify.auth.GlobalRateLimitException
import com.cloudbridge.spotify.network.SpotifyApiService
import com.cloudbridge.spotify.network.model.SpotifyImage
import com.cloudbridge.spotify.ui.SearchResultItem
import com.cloudbridge.spotify.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debounced Spotify search across tracks, albums, and playlists.
 */
class SearchCoordinator(
    private val api: SpotifyApiService,
    private val scope: CoroutineScope,
    private val bestArtwork: (List<SpotifyImage>?) -> String?,
    private val onApiFailure: (Exception) -> Unit,
    private val rethrowIfCancellation: (Exception) -> Unit
) {
    companion object {
        private const val TAG = "SearchCoordinator"
        private const val DEBOUNCE_MS = 750L
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

    private var searchJob: Job? = null

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearchLoading.value = false
            return
        }
        searchJob = scope.launch {
            delay(DEBOUNCE_MS)
            performSearch(query)
        }
    }

    fun retrySearch() {
        val query = _searchQuery.value
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = scope.launch { performSearch(query) }
    }

    fun clear() {
        searchJob?.cancel()
        searchJob = null
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearchLoading.value = false
    }

    fun cancel() {
        searchJob?.cancel()
        searchJob = null
    }

    private suspend fun performSearch(query: String) = withContext(Dispatchers.IO) {
        _isSearchLoading.value = true
        try {
            val response = api.search(query = query, limit = 10)

            val playlists = response.playlists?.items
                ?.filterNotNull()
                ?.mapNotNull {
                    val id = it.id ?: return@mapNotNull null
                    val uri = it.uri ?: return@mapNotNull null
                    SearchResultItem(
                        id = id,
                        uri = uri,
                        title = it.name ?: "Unknown Playlist",
                        subtitle = "Playlist",
                        imageUrl = bestArtwork(it.images),
                        type = "playlist"
                    )
                }
                ?: emptyList()

            val albums = response.albums?.items
                ?.filterNotNull()
                ?.mapNotNull { album ->
                    val id = album.id ?: return@mapNotNull null
                    val uri = album.uri ?: return@mapNotNull null
                    SearchResultItem(
                        id = id,
                        uri = uri,
                        title = album.name,
                        subtitle = "Album",
                        imageUrl = bestArtwork(album.images),
                        type = "album"
                    )
                }
                ?: emptyList()

            val tracks = response.tracks?.items
                ?.filterNotNull()
                ?.mapNotNull { track ->
                    val id = track.id ?: return@mapNotNull null
                    SearchResultItem(
                        id = id,
                        uri = track.uri,
                        title = track.name,
                        subtitle = track.artists?.joinToString(", ") { it.name } ?: "Track",
                        imageUrl = bestArtwork(track.album?.images),
                        type = "track"
                    )
                }
                ?: emptyList()

            _searchResults.value = (playlists + albums + tracks).distinctBy { it.uri }
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            onApiFailure(e)
            AppLogger.e(TAG, "Search failed: ${e.message}", e)
            if (e !is GlobalRateLimitException) {
                _searchResults.value = emptyList()
            }
        } finally {
            if (currentCoroutineContext().isActive) {
                _isSearchLoading.value = false
            }
        }
    }
}
