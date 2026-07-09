package com.cloudbridge.spotify.ui.coordinator

import com.cloudbridge.spotify.data.SpotifyLibraryRepository
import com.cloudbridge.spotify.network.model.SpotifyAlbum
import com.cloudbridge.spotify.network.model.SpotifyAudiobook
import com.cloudbridge.spotify.network.model.SpotifyImage
import com.cloudbridge.spotify.network.model.SpotifyPlaylist
import com.cloudbridge.spotify.network.model.SpotifyShow
import com.cloudbridge.spotify.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns library catalog StateFlows (playlists / albums / shows / audiobooks)
 * and cache→network loaders. ViewModel orchestrates when to load.
 */
class LibraryCatalogCoordinator(
    private val libraryRepository: SpotifyLibraryRepository,
    private val bestArtwork: (List<SpotifyImage>?) -> String?,
    private val prefetchImages: (List<String?>) -> Unit,
    private val onApiFailure: (Exception) -> Unit,
    private val rethrowIfCancellation: (Exception) -> Unit,
    private val onShowsLoaded: suspend (List<SpotifyShow>) -> Unit = {}
) {
    companion object {
        private const val TAG = "LibraryCatalog"
    }

    private val _playlists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val playlists: StateFlow<List<SpotifyPlaylist>> = _playlists

    private val _savedAlbums = MutableStateFlow<List<SpotifyAlbum>>(emptyList())
    val savedAlbums: StateFlow<List<SpotifyAlbum>> = _savedAlbums

    private val _savedShows = MutableStateFlow<List<SpotifyShow>>(emptyList())
    val savedShows: StateFlow<List<SpotifyShow>> = _savedShows

    private val _savedAudiobooks = MutableStateFlow<List<SpotifyAudiobook>>(emptyList())
    val savedAudiobooks: StateFlow<List<SpotifyAudiobook>> = _savedAudiobooks

    @Volatile var playlistsSettled = false
        private set
    @Volatile var savedAlbumsSettled = false
        private set
    @Volatile var savedShowsSettled = false
        private set
    @Volatile var savedAudiobooksSettled = false
        private set

    private val playlistsLoadMutex = Mutex()
    private val savedAlbumsLoadMutex = Mutex()
    private val savedShowsLoadMutex = Mutex()
    private val savedAudiobooksLoadMutex = Mutex()

    fun isLibraryDataSettled(): Boolean =
        playlistsSettled && savedAlbumsSettled && savedShowsSettled && savedAudiobooksSettled

    fun clear() {
        _playlists.value = emptyList()
        _savedAlbums.value = emptyList()
        _savedShows.value = emptyList()
        _savedAudiobooks.value = emptyList()
        playlistsSettled = false
        savedAlbumsSettled = false
        savedShowsSettled = false
        savedAudiobooksSettled = false
    }

    /** Allow home/custom-mix paths to seed shows without a full library load. */
    fun setSavedShows(shows: List<SpotifyShow>) {
        if (shows.isNotEmpty()) {
            _savedShows.value = shows
        }
    }

    fun setPlaylists(playlists: List<SpotifyPlaylist>) {
        if (playlists.isNotEmpty()) {
            _playlists.value = playlists
        }
    }

    suspend fun loadPlaylists() = withContext(Dispatchers.IO) {
        playlistsLoadMutex.withLock {
            var usedCachedData = false
            if (_playlists.value.isEmpty()) {
                val cached = libraryRepository.getCachedPlaylists()
                if (cached.isNotEmpty()) {
                    _playlists.value = cached
                    usedCachedData = true
                    AppLogger.d(TAG, "Preloaded ${cached.size} playlists from cache")
                }
            }

            try {
                val fresh = libraryRepository.refreshPlaylists()
                _playlists.value = fresh
                playlistsSettled = true
                AppLogger.d(TAG, "Loaded ${fresh.size} playlists")
                prefetchImages(fresh.map { bestArtwork(it.images) })
            } catch (e: Exception) {
                rethrowIfCancellation(e)
                if (usedCachedData || _playlists.value.isNotEmpty()) {
                    playlistsSettled = true
                    AppLogger.w(TAG, "Playlist refresh failed; keeping cached library data warm")
                }
                AppLogger.e(TAG, "Failed to load playlists: ${e.message}")
            }
        }
    }

    suspend fun loadSavedAlbums() = withContext(Dispatchers.IO) {
        savedAlbumsLoadMutex.withLock {
            var usedCachedData = false
            if (_savedAlbums.value.isEmpty()) {
                val cached = libraryRepository.getCachedAlbums()
                if (cached.isNotEmpty()) {
                    _savedAlbums.value = cached
                    usedCachedData = true
                    AppLogger.d(TAG, "Preloaded ${cached.size} albums from cache")
                }
            }

            try {
                val fresh = libraryRepository.refreshSavedAlbums()
                _savedAlbums.value = fresh
                savedAlbumsSettled = true
                prefetchImages(fresh.map { bestArtwork(it.images) })
            } catch (e: Exception) {
                rethrowIfCancellation(e)
                if (usedCachedData || _savedAlbums.value.isNotEmpty()) {
                    savedAlbumsSettled = true
                    AppLogger.w(TAG, "Saved albums refresh failed; keeping cached library data warm")
                }
                AppLogger.e(TAG, "Failed to load saved albums: ${e.message}")
            }
        }
    }

    suspend fun loadSavedShows() = withContext(Dispatchers.IO) {
        savedShowsLoadMutex.withLock {
            if (_savedShows.value.isNotEmpty()) return@withLock
            var usedCachedData = false
            val cached = libraryRepository.getCachedShows()
            if (cached.isNotEmpty()) {
                _savedShows.value = cached
                usedCachedData = true
                AppLogger.d(TAG, "Preloaded ${cached.size} shows from cache")
            }
            try {
                val fresh = libraryRepository.refreshSavedShows()
                _savedShows.value = fresh
                savedShowsSettled = true
                prefetchImages(fresh.map { bestArtwork(it.images) })
                onShowsLoaded(fresh)
            } catch (e: Exception) {
                rethrowIfCancellation(e)
                onApiFailure(e)
                if (usedCachedData || _savedShows.value.isNotEmpty()) {
                    savedShowsSettled = true
                    AppLogger.w(TAG, "Saved shows refresh failed; keeping cached library data warm")
                }
                AppLogger.e(TAG, "Failed to load saved shows: ${e.message}")
            }
        }
    }

    suspend fun loadSavedAudiobooks() = withContext(Dispatchers.IO) {
        savedAudiobooksLoadMutex.withLock {
            if (_savedAudiobooks.value.isNotEmpty()) return@withLock
            var usedCachedData = false
            val cached = libraryRepository.getCachedAudiobooks()
            if (cached.isNotEmpty()) {
                _savedAudiobooks.value = cached
                usedCachedData = true
                AppLogger.d(TAG, "Preloaded ${cached.size} audiobooks from cache")
            }
            try {
                val fresh = libraryRepository.refreshSavedAudiobooks()
                _savedAudiobooks.value = fresh
                savedAudiobooksSettled = true
                prefetchImages(fresh.map { bestArtwork(it.images) })
            } catch (e: Exception) {
                rethrowIfCancellation(e)
                onApiFailure(e)
                if (usedCachedData || _savedAudiobooks.value.isNotEmpty()) {
                    savedAudiobooksSettled = true
                    AppLogger.w(TAG, "Saved audiobooks refresh failed; keeping cached library data warm")
                }
                AppLogger.e(TAG, "Failed to load saved audiobooks: ${e.message}")
            }
        }
    }
}
