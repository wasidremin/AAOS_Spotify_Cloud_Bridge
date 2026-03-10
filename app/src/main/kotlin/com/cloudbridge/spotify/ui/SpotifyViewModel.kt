package com.cloudbridge.spotify.ui

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cloudbridge.spotify.auth.GlobalRateLimitException
import com.cloudbridge.spotify.auth.TokenManager
import com.cloudbridge.spotify.cache.CacheDatabase
import com.cloudbridge.spotify.cache.PinnedItem
import com.cloudbridge.spotify.cache.UserProfile
import com.cloudbridge.spotify.data.SpotifyLibraryRepository
import com.cloudbridge.spotify.domain.CustomMixEngine
import com.cloudbridge.spotify.network.SpotifyApiService
import com.cloudbridge.spotify.network.model.*
import com.cloudbridge.spotify.player.DeviceManager
import com.cloudbridge.spotify.player.SpotifyPlaybackController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import coil.Coil
import coil.request.ImageRequest
import retrofit2.HttpException

/**
 * UI-friendly representation of a recently-played context (playlist or album).
 *
 * Built by hydrating the raw [PlayContext] URIs returned from
 * `GET /v1/me/player/recently-played` into full metadata via
 * per-item API calls.
 *
 * @property id       The Spotify object ID (e.g. playlist or album ID).
 * @property uri      The full Spotify URI (`spotify:playlist:...`).
 * @property title    Human-readable name of the context.
 * @property subtitle Type label — `"Playlist"` or `"Album"`.
 * @property imageUrl Best-quality cover art URL (may be `null`).
 * @property type     Object type: `"playlist"` or `"album"`.
 */
data class RecentContextItem(
    val id: String,
    val uri: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val type: String
)

/**
 * Unified search result item displayed in the [SearchScreen] grid.
 *
 * Flattens Spotify's separate track / album / playlist search results
 * into a single list for the 4-column grid layout.
 *
 * @property id       The Spotify object ID.
 * @property uri      The full Spotify URI.
 * @property title    Track, album, or playlist name.
 * @property subtitle Artist name(s) for tracks, or type label for albums/playlists.
 * @property imageUrl Best-quality artwork URL (may be `null`).
 * @property type     `"track"`, `"album"`, or `"playlist"`.
 */
data class SearchResultItem(
    val id: String,
    val uri: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val type: String
)

data class CustomMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val colorHex: Long
)

data class PodcastUpdateInfo(
    val showId: String,
    val newEpisodeCount: Int,
    val latestEpisodeName: String? = null,
    val latestEpisodeReleaseDate: String? = null
) {
    val hasUpdates: Boolean get() = newEpisodeCount > 0

    val badgeText: String
        get() = when {
            newEpisodeCount <= 0 -> ""
            newEpisodeCount == 1 -> "NEW"
            newEpisodeCount > 9 -> "9+ NEW"
            else -> "$newEpisodeCount NEW"
        }

    fun subtitleSuffix(baseSubtitle: String?): String = when {
        newEpisodeCount <= 0 -> baseSubtitle ?: "Podcast"
        newEpisodeCount == 1 && !baseSubtitle.isNullOrBlank() -> "1 new episode · $baseSubtitle"
        newEpisodeCount == 1 -> "1 new episode"
        !baseSubtitle.isNullOrBlank() -> "$newEpisodeCount new episodes · $baseSubtitle"
        else -> "$newEpisodeCount new episodes"
    }
}

/**
 * Central ViewModel — the "brain" of the Cloud Bridge Compose UI.
 *
 * Replaces the traditional MediaBrowserService / BrowseTree pattern.
 * Instead of a content tree, this ViewModel feeds raw Spotify Web API
 * data directly into [StateFlow]s that Compose screens observe.
 *
 * Key responsibilities:
 * - **Navigation**: Manual sealed-class screen routing with back-stack.
 * - **Home feed**: Loads "Jump Back In", "Suggested For You",
 *   "New Releases", and "Your Podcasts" via [loadHomeFeed].
 * - **Library**: Playlists (paginated), albums, podcasts.
 * - **Search**: Debounced (300 ms) full-text search across tracks,
 *   albums, and playlists.
 * - **Playback control**: Delegates to [SpotifyPlaybackController]
 *   for play, pause, skip, seek, shuffle, repeat.
 * - **Metadata sync**: A 3-second polling loop ([startMetadataSync])
 *   that fetches the phone's current playback state and updates the
 *   Now Playing UI. Polling is the only reliable approach because
 *   Spotify's Web API does not support push-based state updates.
 *
 * @param api                The Spotify Web API Retrofit interface.
 * @param playbackController Bridge to send playback commands to the phone.
 * @param deviceManager      Discovers the user's phone Spotify device.
 * @see SpotifyPlaybackController
 * @see DeviceManager
 */
class SpotifyViewModel(
    private val api: SpotifyApiService,
    private val playbackController: SpotifyPlaybackController,
    private val deviceManager: DeviceManager,
    private val tokenManager: TokenManager,
    private val cacheDb: CacheDatabase,
    private val libraryRepository: SpotifyLibraryRepository,
    private val customMixEngine: CustomMixEngine,
    private val context: android.content.Context
) : ViewModel() {

    private val hasProfilesOnDisk = runBlocking(Dispatchers.IO) {
        cacheDb.userProfileDao().getAllOnce().isNotEmpty()
    }

    companion object {
        private const val TAG = "SpotifyViewModel"
        private const val METADATA_POLL_MS = 3000L
        private const val PODCAST_METADATA_POLL_MS = 2000L
        private const val PODCAST_NULL_TOLERANCE_MS = 20_000L
        private const val PODCAST_RETRY_DELAY_MS = 750L
        private const val SAVED_STATUS_REFRESH_MS = 15_000L
        private const val MAX_QUEUE_BATCH = 100
        private val PINNABLE_TYPES = setOf("playlist", "album", "show")
        const val CUSTOM_MIX_DAILY_DRIVE = "daily_drive"
        private const val CUSTOM_MIX_80S = "mix_80s"
        private const val CUSTOM_MIX_90S = "mix_90s"
        private const val CUSTOM_MIX_2000S = "mix_2000s"
        private const val CUSTOM_MIX_2010S = "mix_2010s"
    }

    /** Factory for [ViewModelProvider] so the ViewModel survives config changes. */
    class Factory(
        private val api: SpotifyApiService,
        private val playbackController: SpotifyPlaybackController,
        private val deviceManager: DeviceManager,
        private val tokenManager: TokenManager,
        private val cacheDb: CacheDatabase,
        private val libraryRepository: SpotifyLibraryRepository,
        private val customMixEngine: CustomMixEngine,
        private val context: android.content.Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SpotifyViewModel(
                api,
                playbackController,
                deviceManager,
                tokenManager,
                cacheDb,
                libraryRepository,
                customMixEngine,
                context
            ) as T
        }
    }

    // ── Navigation ───────────────────────────────────────────────────

    sealed class Screen {
        data object Home : Screen()
        data object Search : Screen()
        data object Library : Screen()
        data object ManagePins : Screen()
        data class PlaylistDetail(val id: String, val name: String, val uri: String) : Screen()
        data class AlbumDetail(val id: String, val name: String, val uri: String?) : Screen()
        data class ArtistDetail(val id: String, val name: String, val imageUrl: String?) : Screen()
        data class PodcastDetail(val id: String, val name: String, val uri: String) : Screen()
        data class AudiobookDetail(val id: String, val name: String, val uri: String) : Screen()
        data object Queue : Screen()
        data object Settings : Screen()
        data object HomeLayoutSettings : Screen()
        data class AddProfile(val refreshProfileId: String? = null) : Screen()
    }

    private val _currentScreen = MutableStateFlow<Screen>(if (hasProfilesOnDisk) Screen.Home else Screen.AddProfile())
    val currentScreen: StateFlow<Screen> = _currentScreen

    private val backStack = mutableListOf<Screen>()

    private val _showNowPlaying = MutableStateFlow(false)
    val showNowPlaying: StateFlow<Boolean> = _showNowPlaying

    // ── Home Data ────────────────────────────────────────────────────

    private val _recentContexts = MutableStateFlow<List<RecentContextItem>>(emptyList())
    val recentContexts: StateFlow<List<RecentContextItem>> = _recentContexts.asStateFlow()

    private val _featuredPlaylists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val featuredPlaylists: StateFlow<List<SpotifyPlaylist>> = _featuredPlaylists

    val pinnedItems: StateFlow<List<PinnedItem>> = cacheDb.pinnedItemDao().getAllPinned()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val customMixes: List<CustomMix> = listOf(
        CustomMix(CUSTOM_MIX_DAILY_DRIVE, "Daily Drive", "Podcasts + music built from your library", 0xFF2563EB),
        CustomMix(CUSTOM_MIX_80S, "80s Mix", "1980s favorites with smart recommendations", 0xFFE11D48),
        CustomMix(CUSTOM_MIX_90S, "90s Mix", "1990s favorites with smart recommendations", 0xFF9333EA),
        CustomMix(CUSTOM_MIX_2000S, "2000s Mix", "2000s favorites with smart recommendations", 0xFF0891B2),
        CustomMix(CUSTOM_MIX_2010S, "2010s Mix", "2010s favorites with smart recommendations", 0xFF16A34A)
    )

    /** Mix-ID → up to 4 cover-art URLs for the 2×2 grid tiles on the home screen. */
    private val _customMixArt = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val customMixArt: StateFlow<Map<String, List<String>>> = _customMixArt.asStateFlow()

    private val _topTracks = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val topTracks: StateFlow<List<SpotifyTrack>> = _topTracks

    private val _newReleases = MutableStateFlow<List<SpotifyAlbum>>(emptyList())
    val newReleases: StateFlow<List<SpotifyAlbum>> = _newReleases

    private val _podcastUpdates = MutableStateFlow<Map<String, PodcastUpdateInfo>>(emptyMap())
    val podcastUpdates: StateFlow<Map<String, PodcastUpdateInfo>> = _podcastUpdates.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

    // ── Library Data ─────────────────────────────────────────────────

    private val _playlists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val playlists: StateFlow<List<SpotifyPlaylist>> = _playlists

    private val _savedAlbums = MutableStateFlow<List<SpotifyAlbum>>(emptyList())
    val savedAlbums: StateFlow<List<SpotifyAlbum>> = _savedAlbums

    private val _savedShows = MutableStateFlow<List<SpotifyShow>>(emptyList())
    val savedShows: StateFlow<List<SpotifyShow>> = _savedShows

    private val _savedAudiobooks = MutableStateFlow<List<SpotifyAudiobook>>(emptyList())
    val savedAudiobooks: StateFlow<List<SpotifyAudiobook>> = _savedAudiobooks

    /** Persists selected Library tab across back-stack navigation. */
    var libraryTab by mutableIntStateOf(0)

    /** Shows sorted by most recent episode date (for home screen). */
    val showsSortedByRecent: StateFlow<List<SpotifyShow>> = _savedShows.asStateFlow()

    // ── Detail Data ──────────────────────────────────────────────────

    private val _detailTracks = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val detailTracks: StateFlow<List<SpotifyTrack>> = _detailTracks

    private val _detailEpisodes = MutableStateFlow<List<SpotifyEpisode>>(emptyList())
    val detailEpisodes: StateFlow<List<SpotifyEpisode>> = _detailEpisodes

    private val _detailChapters = MutableStateFlow<List<SpotifyChapter>>(emptyList())
    val detailChapters: StateFlow<List<SpotifyChapter>> = _detailChapters

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError: StateFlow<String?> = _detailError.asStateFlow()

    // ── Now Playing ──────────────────────────────────────────────────

    private val _playbackState = MutableStateFlow<CurrentPlaybackResponse?>(null)
    val playbackState: StateFlow<CurrentPlaybackResponse?> = _playbackState

    private val _isTrackSaved = MutableStateFlow(false)
    val isTrackSaved: StateFlow<Boolean> = _isTrackSaved

    // ── Queue ────────────────────────────────────────────────────────

    private val _queue = MutableStateFlow<List<SpotifyPlayableItem>>(emptyList())
    val queue: StateFlow<List<SpotifyPlayableItem>> = _queue

    // ── Artists ──────────────────────────────────────────────────────

    private val _followedArtists = MutableStateFlow<List<SpotifyArtist>>(emptyList())
    val followedArtists: StateFlow<List<SpotifyArtist>> = _followedArtists.asStateFlow()

    private val _artistTopTracks = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val artistTopTracks: StateFlow<List<SpotifyTrack>> = _artistTopTracks.asStateFlow()

    private val _artistAlbums = MutableStateFlow<List<SpotifyAlbum>>(emptyList())
    val artistAlbums: StateFlow<List<SpotifyAlbum>> = _artistAlbums.asStateFlow()

    private val _artistLikedSongs = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val artistLikedSongs: StateFlow<List<SpotifyTrack>> = _artistLikedSongs.asStateFlow()

    private val _currentArtistProfile = MutableStateFlow<SpotifyArtist?>(null)
    val currentArtistProfile: StateFlow<SpotifyArtist?> = _currentArtistProfile.asStateFlow()

    // ── Device Settings ──────────────────────────────────────────────

    private val _deviceList = MutableStateFlow<List<SpotifyDevice>>(emptyList())
    val deviceList: StateFlow<List<SpotifyDevice>> = _deviceList.asStateFlow()

    private val _lockedDeviceId = MutableStateFlow<String?>(null)
    val lockedDeviceId: StateFlow<String?> = _lockedDeviceId.asStateFlow()

    private val _lockedDeviceName = MutableStateFlow<String?>(null)
    val lockedDeviceName: StateFlow<String?> = _lockedDeviceName.asStateFlow()

    // ── Layout Configuration ─────────────────────────────────────────

    private val _gridColumns = MutableStateFlow(4)
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _rightPadding = MutableStateFlow(160)
    val rightPadding: StateFlow<Int> = _rightPadding.asStateFlow()

    private val _playInstantly = MutableStateFlow(false)
    val playInstantly: StateFlow<Boolean> = _playInstantly.asStateFlow()

    val userProfiles: StateFlow<List<UserProfile>> = tokenManager.userProfilesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeProfileId: StateFlow<String?> = tokenManager.activeProfileIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val homeSectionOrder: StateFlow<List<HomeSection>> = tokenManager.homeSectionOrderFlow
        .map(HomeSection::fromStorageKeys)
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeSection.defaultOrder)

    private val _explicitFilterEnabled = MutableStateFlow(false)
    val explicitFilterEnabled: StateFlow<Boolean> = _explicitFilterEnabled.asStateFlow()

    private val _dailyDriveNewsId = MutableStateFlow("")
    val dailyDriveNewsId: StateFlow<String> = _dailyDriveNewsId.asStateFlow()

    // ── Loading State (per-section to avoid race conditions) ─────────

    private val _isHomeLoading = MutableStateFlow(false)
    val isHomeLoading: StateFlow<Boolean> = _isHomeLoading

    private val _isLibraryLoading = MutableStateFlow(false)
    val isLibraryLoading: StateFlow<Boolean> = _isLibraryLoading

    private val _isDetailLoading = MutableStateFlow(false)
    val isDetailLoading: StateFlow<Boolean> = _isDetailLoading

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _requiresReauth = MutableStateFlow(false)
    val requiresReauth: StateFlow<Boolean> = _requiresReauth.asStateFlow()

    private val _deviceNotFoundError = MutableStateFlow(false)
    val deviceNotFoundError: StateFlow<Boolean> = _deviceNotFoundError.asStateFlow()

    private val _rateLimitUntilEpochMs = MutableStateFlow(0L)
    val rateLimitUntilEpochMs: StateFlow<Long> = _rateLimitUntilEpochMs.asStateFlow()

    private var hasDismissedReauthThisSession = false
    private var lastSavedStatusTrackUri: String? = null
    private var lastSavedStatusCheckedAtMs: Long = 0L
    private var lastSuccessfulPlaybackSyncAtMs: Long = 0L

    // ── Metadata Sync ────────────────────────────────────────────────

    private var metadataSyncJob: Job? = null
    private var searchJob: Job? = null
    private var podcastUpdatesJob: Job? = null
    private var lastObservedProfileId: String? = null

    // ── Lifecycle ────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            combine(userProfiles, activeProfileId) { profiles, activeId -> profiles to activeId }
                .collectLatest { (profiles, activeId) ->
                    if (profiles.isEmpty()) {
                        if (hasProfilesOnDisk && lastObservedProfileId == null) {
                            return@collectLatest
                        }
                        lastObservedProfileId = null
                        enterProfileSetupMode()
                        return@collectLatest
                    }

                    val resolvedProfileId = activeId ?: profiles.firstOrNull()?.id
                    if (resolvedProfileId.isNullOrBlank()) {
                        lastObservedProfileId = null
                        enterProfileSetupMode()
                        return@collectLatest
                    }

                    val previousProfileId = lastObservedProfileId
                    val shouldClearProfileData = previousProfileId != null && previousProfileId != resolvedProfileId
                    if (previousProfileId == resolvedProfileId && metadataSyncJob?.isActive == true) {
                        return@collectLatest
                    }
                    lastObservedProfileId = resolvedProfileId
                    refreshForActiveProfile(shouldClearProfileData)
                }
        }

        viewModelScope.launch {
            tokenManager.gridColumnsFlow.collect { _gridColumns.value = it }
        }
        viewModelScope.launch {
            tokenManager.rightPaddingFlow.collect { _rightPadding.value = it }
        }
        viewModelScope.launch {
            tokenManager.playInstantlyFlow.collect { _playInstantly.value = it }
        }
        viewModelScope.launch {
            tokenManager.explicitFilterEnabledFlow.collect { _explicitFilterEnabled.value = it }
        }
        viewModelScope.launch {
            tokenManager.dailyDriveNewsIdFlow.collect { _dailyDriveNewsId.value = it }
        }
        viewModelScope.launch {
            tokenManager.rateLimitUntilEpochMsFlow.collect { _rateLimitUntilEpochMs.value = it }
        }
    }

    override fun onCleared() {
        super.onCleared()
        metadataSyncJob?.cancel()
    }

    private suspend fun enterProfileSetupMode() {
        metadataSyncJob?.cancel()
        metadataSyncJob = null
        homeFeedJob?.cancel()
        libraryJob?.cancel()
        searchJob?.cancel()
        podcastUpdatesJob?.cancel()
        backStack.clear()
        _showNowPlaying.value = false
        _playbackState.value = null
        _isTrackSaved.value = false
        _currentScreen.value = Screen.AddProfile()
    }

    private suspend fun refreshForActiveProfile(clearProfileData: Boolean) {
        if (clearProfileData) {
            clearProfileScopedData()
        }

        loadLockedDeviceSetting()
        loadHomeFeed()
        loadLibrary()
        loadDevices()
        startMetadataSync(forceRestart = clearProfileData, immediateSync = true)
    }

    fun refreshPlaybackStateNow() {
        startMetadataSync(immediateSync = true)
    }

    private fun prefetchImages(urls: List<String?>) {
        viewModelScope.launch(Dispatchers.IO) {
            urls.filterNotNull().distinct().forEach { url ->
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .build()
                Coil.imageLoader(context).enqueue(request)
            }
        }
    }

    // ── Navigation API ───────────────────────────────────────────────

    /**
     * Navigate to a new screen, pushing the current screen onto the back-stack.
     *
     * Avoids duplicate pushes if the user taps the same NavRail item twice.
     * After updating the current screen, triggers screen-specific data loading
     * via [handleScreenEntered].
     *
     * @param screen The target [Screen] to display.
     */
    fun navigateTo(screen: Screen) {
        if (shouldForceProfileSetup() && screen !is Screen.AddProfile) {
            backStack.clear()
            _currentScreen.value = Screen.AddProfile()
            return
        }
        if (_currentScreen.value != screen) {
            backStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
        handleScreenEntered(screen)
    }

    /**
     * Navigate to a top-level screen, clearing the back-stack to prevent infinite history.
     *
     * Used for major tab switches to avoid accumulating navigation history.
     *
     * @param screen The target [Screen] to display.
     */
    fun navigateTopLevel(screen: Screen) {
        if (shouldForceProfileSetup() && screen !is Screen.AddProfile) {
            backStack.clear()
            _showNowPlaying.value = false
            _currentScreen.value = Screen.AddProfile()
            return
        }
        _showNowPlaying.value = false // Force overlay to close
        if (_currentScreen.value != screen) {
            backStack.clear() // Prevent infinite history loop
            _currentScreen.value = screen
        }
        handleScreenEntered(screen)
    }

    private fun handleScreenEntered(screen: Screen) {
        if (shouldForceProfileSetup() && screen !is Screen.AddProfile) return
        when (screen) {
            is Screen.Home -> loadHomeFeed()
            is Screen.Search -> Unit
            is Screen.Library -> loadLibrary()
            is Screen.ManagePins -> Unit
            is Screen.PlaylistDetail -> loadPlaylistTracks(screen.id)
            is Screen.AlbumDetail -> loadAlbumTracks(screen.id)
            is Screen.ArtistDetail -> loadArtistDetail(screen.id)
            is Screen.PodcastDetail -> loadShowEpisodes(screen.id)
            is Screen.AudiobookDetail -> loadAudiobookChapters(screen.id)
            is Screen.Queue -> loadQueue()
            is Screen.Settings -> loadDevices()
            is Screen.HomeLayoutSettings -> Unit
            is Screen.AddProfile -> Unit
            else -> {}
        }
    }

    fun openNowPlaying() {
        _showNowPlaying.value = true
        lastSavedStatusTrackUri = null
        lastSavedStatusCheckedAtMs = 0L
        refreshPlaybackStateNow()
    }
    fun closeNowPlaying() { _showNowPlaying.value = false }

    /**
     * Navigate back to the previous screen.
     *
     * Priority:
     * 1. If the Now Playing overlay is open, close it (no screen change).
     * 2. Otherwise pop the back-stack; default to [Screen.Home] if empty.
     */
    fun navigateBack() {
        if (shouldForceProfileSetup()) {
            _showNowPlaying.value = false
            backStack.clear()
            _currentScreen.value = Screen.AddProfile()
            return
        }
        if (_showNowPlaying.value) {
            _showNowPlaying.value = false
            return
        }

        val previous = backStack.removeLastOrNull() ?: Screen.Home
        _currentScreen.value = previous
        handleScreenEntered(previous)
    }

    private fun shouldForceProfileSetup(): Boolean = userProfiles.value.isEmpty()

    // ── Data Loading ─────────────────────────────────────────────────

    private var homeFeedJob: Job? = null
    private var libraryJob: Job? = null

    /**
     * Loads the entire Home screen feed.
     *
     * Guard: skips the load if all three primary sections are already
     * populated (prevents unnecessary refetches on back-navigation).
     *
     * Uses [supervisorScope] so that a failure in one section (e.g.
     * "Suggested For You") does not cancel the others.
     *
     * **Shared top-artists optimisation**: A single `async` call fetches
     * `getTopArtists(limit=5)` once. The result is then passed to both
     * [loadFeatured] and [loadNewReleases], halving the number of API
     * calls and avoiding the 429 bursts that occurred when both methods
     * independently called `getTopArtists` in parallel.
     */
    fun loadHomeFeed() {
        // Only skip if *all* sections already loaded (not just recentContexts)
        if (_recentContexts.value.isNotEmpty() &&
            _featuredPlaylists.value.isNotEmpty() &&
            _newReleases.value.isNotEmpty()
        ) return
        homeFeedJob?.cancel()
        homeFeedJob = viewModelScope.launch {
            _isHomeLoading.value = true
            try {
                supervisorScope {
                    // Fetch top artists once, then share the result with both
                    // loadFeatured and loadNewReleases to avoid duplicate parallel calls
                    // that can trigger 429s.
                    val topArtistsDeferred = async(Dispatchers.IO) {
                        try {
                            api.getTopArtists(limit = 5, timeRange = "short_term")
                        } catch (e: Exception) {
                            handleApiFailure(e)
                            Log.w(TAG, "Could not load top artists: ${e.message}")
                            null
                        }
                    }
                    launch { loadRecentlyPlayed() }
                    launch { loadSavedShows() }
                    launch { loadCustomMixArt() }
                    val topArtists = topArtistsDeferred.await()
                    launch { loadFeatured(topArtists) }
                    launch { loadNewReleases(topArtists) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading home feed: ${e.message}", e)
            } finally {
                _isHomeLoading.value = false
            }
        }
    }

    /**
     * Pre-loads 4 representative album-art URLs per custom mix for the
     * 2×2 grid tiles on the home screen.
     *
     * - **Decade mixes** (80s, 90s, 2000s, 2010s): picks 4 saved tracks
     *   whose album release-date starts with the decade prefix and extracts
     *   distinct album cover-art URLs.
     * - **Daily Drive**: uses the first 2 saved podcast show images plus
     *   the first 2 saved-track album covers.
     *
     * Runs on [Dispatchers.IO] and is launched in parallel with the other
     * home-feed loaders so it doesn't block the UI.
     */
    private suspend fun loadCustomMixArt() = withContext(Dispatchers.IO) {
        try {
            if (_customMixArt.value.isNotEmpty()) return@withContext

            val savedTracks = libraryRepository.getSavedTracks(maxTracks = 200)
            val artMap = mutableMapOf<String, List<String>>()

            // Decade mixes — extract 4 unique album covers per decade
            val decadePrefixes = mapOf(
                CUSTOM_MIX_80S to "198",
                CUSTOM_MIX_90S to "199",
                CUSTOM_MIX_2000S to "200",
                CUSTOM_MIX_2010S to "201"
            )
            for ((mixId, prefix) in decadePrefixes) {
                val arts = savedTracks
                    .filter { it.album?.releaseDate?.startsWith(prefix) == true }
                    .mapNotNull { bestArtwork(it.album?.images) }
                    .distinct()
                    .shuffled()
                    .take(4)
                if (arts.isNotEmpty()) artMap[mixId] = arts
            }

            // Daily Drive — 2 podcast covers + 2 track covers
            val shows = _savedShows.value.ifEmpty { libraryRepository.getSavedShows() }
            val podcastArts = shows
                .mapNotNull { bestArtwork(it.images) }
                .distinct()
                .take(2)
            val trackArts = savedTracks
                .mapNotNull { bestArtwork(it.album?.images) }
                .distinct()
                .shuffled()
                .take(2)
            val dailyDriveArts = podcastArts + trackArts
            if (dailyDriveArts.isNotEmpty()) artMap[CUSTOM_MIX_DAILY_DRIVE] = dailyDriveArts

            _customMixArt.value = artMap
            Log.d(TAG, "Custom mix art loaded for ${artMap.size} mixes")
        } catch (e: Exception) {
            Log.w(TAG, "Could not load custom mix art: ${e.message}")
        }
    }

    /**
     * Updates the search query and triggers a debounced search.
     *
    * Debounce: 750 ms delay after the last keystroke before firing
     * the API call. Prevents spamming Spotify with partial queries
     * while the user is still typing.
     *
     * @param query The current text in the search field.
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearchLoading.value = false
            return
        }

        searchJob = viewModelScope.launch {
            delay(750)
            performSearch(query)
        }
    }

    fun retrySearch() {
        val query = _searchQuery.value
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { performSearch(query) }
        }
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
                        subtitle = track.artists.joinToString(", ") { it.name },
                        imageUrl = bestArtwork(track.album?.images),
                        type = "track"
                    )
                }
                ?: emptyList()

            _searchResults.value = (playlists + albums + tracks).distinctBy { it.uri }
            _isOffline.value = false
        } catch (e: Exception) {
            handleApiFailure(e)
            Log.e(TAG, "Search failed: ${e.message}", e)
            if (e !is GlobalRateLimitException) {
                _searchResults.value = emptyList()
            }
        } finally {
            _isSearchLoading.value = false
        }
    }

    fun loadLibrary() {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            _isLibraryLoading.value = true
            try {
                supervisorScope {
                    launch { loadPlaylists() }
                    launch { loadSavedAlbums() }
                    launch { loadSavedShows() }
                    launch { loadSavedAudiobooks() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading library: ${e.message}", e)
            } finally {
                _isLibraryLoading.value = false
            }
        }
    }

    private suspend fun loadRecentlyPlayed() = withContext(Dispatchers.IO) {
        try {
            val response = api.getRecentlyPlayed(limit = 50)

            val uniqueContexts = response.items.filterNotNull()
                .mapNotNull { it.context }
                .distinctBy { it.uri }
                .filter { it.type == "playlist" || it.type == "album" }
                .take(10)

            val localPlaylists = _playlists.value.ifEmpty { libraryRepository.getCachedPlaylists() }
            val localAlbums = _savedAlbums.value.ifEmpty { libraryRepository.getCachedAlbums() }
            val playlistById = localPlaylists.associateBy { it.id }
            val albumById = localAlbums.mapNotNull { album -> album.id?.let { it to album } }.toMap()

            val hydrationJobs = uniqueContexts.map { context ->
                async {
                    val uriParts = context.uri?.split(":") ?: return@async null
                    if (uriParts.size < 3) return@async null
                    val type = uriParts[1]
                    val id = uriParts[2]

                    when (type) {
                        "playlist" -> {
                            try {
                                val playlist = playlistById[id] ?: api.getPlaylist(id)
                                val playlistId = playlist.id ?: return@async null
                                val playlistUri = playlist.uri ?: return@async null
                                RecentContextItem(
                                    id = playlistId,
                                    uri = playlistUri,
                                    title = playlist.name ?: "Unknown Playlist",
                                    subtitle = "Playlist",
                                    imageUrl = bestArtwork(playlist.images),
                                    type = type
                                )
                            } catch (_: Exception) {
                                null
                            }
                        }

                        "album" -> {
                            try {
                                val album = albumById[id] ?: api.getAlbum(id)
                                RecentContextItem(
                                    id = album.id ?: "",
                                    uri = album.uri ?: "",
                                    title = album.name,
                                    subtitle = "Album",
                                    imageUrl = bestArtwork(album.images),
                                    type = type
                                )
                            } catch (_: Exception) {
                                null
                            }
                        }

                        else -> null
                    }
                }
            }

            _recentContexts.value = hydrationJobs.awaitAll().filterNotNull()
            prefetchImages(_recentContexts.value.map { it.imageUrl })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load recent contexts: ${e.message}")
        }
    }

    /**
     * "Suggested For You" — derived from the user's own playlist library.
     *
     * Spotify is currently returning HTTP 400 for `/v1/search` and HTTP 403 for browse-category
     * discovery on this app/token combination, so the home discovery rail is synthesised from the
     * user's library instead. This avoids noisy log spam while still surfacing useful playlists.
     *
     * Selection strategy:
     * - Exclude pinned items and "Jump Back In" contexts already shown elsewhere on Home.
     * - Prefer playlists with richer metadata (description, artwork, higher track count).
     * - Fall back to any remaining playlists if the curated subset is small.
     */
    private suspend fun loadFeatured(
        sharedTopArtists: com.cloudbridge.spotify.network.model.TopArtistsResponse? = null
    ) = withContext(Dispatchers.IO) {
        try {
            _featuredPlaylists.value = loadLibraryFeaturedPlaylists(limit = 12)
            _isOffline.value = false
            Log.d(TAG, "Loaded ${_featuredPlaylists.value.size} suggested playlists")
        } catch (e: Exception) {
            handleApiFailure(e)
            Log.e(TAG, "Failed to load featured: ${e.message}")
        }
    }

    private suspend fun loadLibraryFeaturedPlaylists(limit: Int): List<SpotifyPlaylist> {
        if (limit <= 0) return emptyList()

        val pinnedUris = cacheDb.pinnedItemDao().getAllPinnedSync().mapTo(mutableSetOf()) { it.uri }
        val recentUris = _recentContexts.value.mapTo(mutableSetOf()) { it.uri }
        val excludedUris = pinnedUris + recentUris

        val allPlaylists = when {
            _playlists.value.isNotEmpty() -> _playlists.value
            else -> {
                val cached = libraryRepository.getCachedPlaylists()
                if (cached.isNotEmpty()) cached else libraryRepository.refreshPlaylists().also { _playlists.value = it }
            }
        }

        val candidates = allPlaylists.filterNot { playlist ->
            val uri = playlist.uri ?: return@filterNot true
            uri in excludedUris
        }

        val curated = candidates
            .sortedWith(
                compareByDescending<SpotifyPlaylist> { !it.description.isNullOrBlank() }
                    .thenByDescending { it.images?.isNotEmpty() == true }
                    .thenByDescending { it.itemCount }
                    .thenBy { it.name.orEmpty().lowercase() }
            )
            .take(limit)

        return if (curated.size >= limit) {
            curated
        } else {
            (curated + allPlaylists.filterNot { playlist -> curated.any { it.uri == playlist.uri } })
                .distinctBy { it.uri ?: it.id ?: it.name ?: "playlist-${it.hashCode()}" }
                .take(limit)
        }
    }

    private suspend fun loadTopTracks() = withContext(Dispatchers.IO) {
        try {
            val response = api.getTopTracks(limit = 30)
            _topTracks.value = response.items.filterNotNull()
            _isOffline.value = false
        } catch (e: Exception) {
            handleApiFailure(e)
            Log.e(TAG, "Failed to load top tracks: ${e.message}")
        }
    }

    /**
    * "New Releases" — replaces the removed February 2026 `/v1/browse/new-releases` endpoint.
     * Fetches the user's top 5 short-term artists then collects 3–4 of their latest albums each,
     * giving a "new music from artists you listen to" feed without needing extended API access.
     *
     * [sharedTopArtists] is pre-fetched once by loadHomeFeed() and shared with loadFeatured()
     * to avoid duplicate parallel API calls that can trigger 429s.
     */
    private suspend fun loadNewReleases(
        sharedTopArtists: com.cloudbridge.spotify.network.model.TopArtistsResponse? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val topArtists = sharedTopArtists ?: api.getTopArtists(limit = 5, timeRange = "short_term")
            val albums = mutableListOf<com.cloudbridge.spotify.network.model.SpotifyAlbum>()
            for (artist in topArtists.items.filterNotNull()) {
                val artistId = artist.id ?: continue
                val artistAlbums = api.getArtistAlbums(artistId = artistId, limit = 3)
                albums += artistAlbums.items.filterNotNull()
                if (albums.size >= 12) break
            }
            _newReleases.value = albums.distinctBy { it.id }.take(12)
            _isOffline.value = false
            Log.d(TAG, "Loaded ${_newReleases.value.size} release tiles from top artists")
        } catch (e: Exception) {
            handleApiFailure(e)
            Log.e(TAG, "Failed to load new releases: ${e.message}")
        }
    }

    private suspend fun loadPlaylists() = withContext(Dispatchers.IO) {
        // Seed UI from cache so the list appears immediately, then refresh from network
        if (_playlists.value.isEmpty()) {
            val cached = libraryRepository.getCachedPlaylists()
            if (cached.isNotEmpty()) {
                _playlists.value = cached
                Log.d(TAG, "Preloaded ${cached.size} playlists from cache")
            }
        }
        try {
            val fresh = libraryRepository.refreshPlaylists()
            _playlists.value = fresh
            Log.d(TAG, "Loaded ${fresh.size} playlists")
            prefetchImages(fresh.map { bestArtwork(it.images) })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load playlists: ${e.message}")
        }
    }

    private suspend fun loadSavedAlbums() = withContext(Dispatchers.IO) {
        // Seed UI from cache so albums list appears immediately
        if (_savedAlbums.value.isEmpty()) {
            val cached = libraryRepository.getCachedAlbums()
            if (cached.isNotEmpty()) {
                _savedAlbums.value = cached
                Log.d(TAG, "Preloaded ${cached.size} albums from cache")
            }
        }
        try {
            val fresh = libraryRepository.refreshSavedAlbums()
            _savedAlbums.value = fresh
            prefetchImages(fresh.map { bestArtwork(it.images) })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load saved albums: ${e.message}")
        }
    }

    private suspend fun loadSavedShows() = withContext(Dispatchers.IO) {
        if (_savedShows.value.isNotEmpty()) return@withContext
        // Seed UI from cache
        val cached = libraryRepository.getCachedShows()
        if (cached.isNotEmpty()) {
            _savedShows.value = cached
            Log.d(TAG, "Preloaded ${cached.size} shows from cache")
        }
        try {
            val fresh = libraryRepository.refreshSavedShows()
            _savedShows.value = fresh
            _isOffline.value = false
            prefetchImages(fresh.map { bestArtwork(it.images) })
            refreshPodcastUpdates(fresh)
        } catch (e: Exception) {
            handleApiFailure(e)
            Log.e(TAG, "Failed to load saved shows: ${e.message}")
        }
    }

    private suspend fun loadSavedAudiobooks() = withContext(Dispatchers.IO) {
        if (_savedAudiobooks.value.isNotEmpty()) return@withContext
        // Seed UI from cache
        val cached = libraryRepository.getCachedAudiobooks()
        if (cached.isNotEmpty()) {
            _savedAudiobooks.value = cached
            Log.d(TAG, "Preloaded ${cached.size} audiobooks from cache")
        }
        try {
            val fresh = libraryRepository.refreshSavedAudiobooks()
            _savedAudiobooks.value = fresh
            _isOffline.value = false
            prefetchImages(fresh.map { bestArtwork(it.images) })
        } catch (e: Exception) {
            handleApiFailure(e)
            Log.e(TAG, "Failed to load saved audiobooks: ${e.message}")
        }
    }

    fun loadAudiobookChapters(audiobookId: String) {
        if (audiobookId.isBlank()) return
        viewModelScope.launch {
            _isDetailLoading.value = true
            _detailChapters.value = emptyList()
            _detailError.value = null
            try {
                val chapters = withContext(Dispatchers.IO) {
                    libraryRepository.getAudiobookChapters(audiobookId)
                }
                _detailChapters.value = chapters
            } catch (e: Exception) {
                handleApiFailure(e)
                Log.e(TAG, "Failed to load chapters for audiobook $audiobookId: ${e.message}")
                _detailError.value = "Could not load chapters.\n${e.message}"
            } finally {
                _isDetailLoading.value = false
            }
        }
    }

    private fun refreshPodcastUpdates(shows: List<SpotifyShow>) {
        if (shows.isEmpty()) {
            _podcastUpdates.value = emptyMap()
            return
        }

        podcastUpdatesJob?.cancel()
        podcastUpdatesJob = viewModelScope.launch(Dispatchers.IO) {
            val concurrency = Semaphore(4)
            val updates = shows.map { show ->
                async {
                    concurrency.withPermit {
                        computePodcastUpdateInfo(show)
                    }
                }
            }.awaitAll().filterNotNull()

            _podcastUpdates.value = updates.associateBy { it.showId }
        }
    }

    private suspend fun computePodcastUpdateInfo(show: SpotifyShow): PodcastUpdateInfo? {
        return try {
            val episodes = api.getShowEpisodes(show.id, limit = 8).items.filterNotNull()
            if (episodes.isEmpty()) return null

            val currentEpisode = _playbackState.value?.item
            val currentEpisodeIndex = if (currentEpisode?.type == "episode" && currentEpisode.show?.id == show.id) {
                episodes.indexOfFirst { it.id == currentEpisode.id }
            } else {
                -1
            }

            val listenedMarkerIndex = when {
                currentEpisodeIndex >= 0 -> currentEpisodeIndex
                else -> episodes.indexOfFirst { episode ->
                    val resume = episode.resumePoint
                    resume?.fullyPlayed == true || (resume?.resumePositionMs ?: 0L) > 10_000L
                }
            }

            val newEpisodeCount = if (listenedMarkerIndex > 0) {
                episodes.take(listenedMarkerIndex).count { episode ->
                    val resume = episode.resumePoint
                    !(resume?.fullyPlayed == true || (resume?.resumePositionMs ?: 0L) > 10_000L)
                }
            } else {
                0
            }

            PodcastUpdateInfo(
                showId = show.id,
                newEpisodeCount = newEpisodeCount,
                latestEpisodeName = episodes.firstOrNull()?.name,
                latestEpisodeReleaseDate = episodes.firstOrNull()?.releaseDate
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load podcast freshness for ${show.name}: ${e.message}")
            null
        }
    }

    fun loadPlaylistTracks(playlistId: String) {
        if (playlistId == "liked-songs") {
            loadLikedSongs()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (_rateLimitUntilEpochMs.value > System.currentTimeMillis()) {
                _detailError.value = buildRateLimitMessage(_rateLimitUntilEpochMs.value)
                _isDetailLoading.value = false
                return@launch
            }
            _isDetailLoading.value = true
            _detailTracks.value = emptyList()  // Clear stale data from previous screen
            _detailError.value = null
            try {
                var offset = 0
                do {
                    val page = api.getPlaylistItems(playlistId, limit = 50, offset = offset)
                    _detailTracks.update { current -> current + page.items.filterNotNull().mapNotNull { it.track } }
                    offset += 50
                } while (page.next != null && offset < page.total)
                _isOffline.value = false
            } catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() == 403) {
                    // 403 = Spotify denied the items request. Possible causes:
                    // 1) Token lacks playlist-read-private / playlist-read-collaborative scope.
                    //    → Use Settings → Manage Profiles → Refresh Permissions, then restart the app.
                    // 2) Spotify Developer-mode quota: the playlist owner is not registered in
                    //    your Spotify app's user list (25-user dev-mode cap).
                    //    → Add the owner to your app at developer.spotify.com → Users & Access,
                    //      or apply for Extended Quota Mode.
                    // 3) The playlist is Spotify-curated (editorial) — these are blocked for dev apps.
                    // 4) Stale APK calling a removed endpoint (e.g. /tracks instead of /items).
                    //    → Rebuild and reinstall the latest APK.
                    Log.w(TAG, "403 Forbidden loading playlist $playlistId — possible causes: missing scope, dev-mode quota, curated playlist, or removed API endpoint.")
                    _detailError.value = buildString {
                        appendLine("Spotify returned 403 — playlist access denied.")
                        appendLine()
                        appendLine("Common causes:")
                        appendLine("• Missing scope: use Settings → Manage Profiles → Refresh Permissions, then restart the app.")
                        appendLine("• Developer-mode 25-user cap: the playlist owner must be added to your Spotify App at developer.spotify.com → Users & Access.")
                        appendLine("• Spotify editorial/curated playlists are blocked for developer apps.")
                        append("• Stale build calling a removed endpoint — reinstall the latest APK.")
                    }
                } else if (e is GlobalRateLimitException) {
                    _detailError.value = buildRateLimitMessage(e.lockedUntilEpochMs)
                } else {
                    handleApiFailure(e)
                    Log.e(TAG, "Failed to load playlist tracks: ${e.message}")
                }
            } finally {
                _isDetailLoading.value = false
            }
        }
    }

    private fun loadLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            _isDetailLoading.value = true
            _detailTracks.value = emptyList()
            _detailError.value = null
            try {
                _detailTracks.value = libraryRepository.getSavedTracks(maxTracks = Int.MAX_VALUE)
                _isOffline.value = false
            } catch (e: Exception) {
                handleApiFailure(e)
                Log.e(TAG, "Failed to load liked songs: ${e.message}")
            } finally {
                _isDetailLoading.value = false
            }
        }
    }

    private fun loadAlbumTracks(albumId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_rateLimitUntilEpochMs.value > System.currentTimeMillis()) {
                _detailError.value = buildRateLimitMessage(_rateLimitUntilEpochMs.value)
                _isDetailLoading.value = false
                return@launch
            }
            _isDetailLoading.value = true
            _detailTracks.value = emptyList()
            _detailError.value = null
            try {
                var offset = 0
                do {
                    val page = api.getAlbumTracks(albumId, limit = 50, offset = offset)
                    _detailTracks.update { current -> current + page.items.filterNotNull() }
                    offset += 50
                } while (page.next != null && offset < page.total)
            } catch (e: Exception) {
                if (e is GlobalRateLimitException) {
                    _detailError.value = buildRateLimitMessage(e.lockedUntilEpochMs)
                } else {
                    handleApiFailure(e)
                }
                Log.e(TAG, "Failed to load album tracks: ${e.message}")
            } finally {
                _isDetailLoading.value = false
            }
        }
    }

    fun loadShowEpisodes(showId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_rateLimitUntilEpochMs.value > System.currentTimeMillis()) {
                _detailError.value = buildRateLimitMessage(_rateLimitUntilEpochMs.value)
                _isDetailLoading.value = false
                return@launch
            }
            _isDetailLoading.value = true
            _detailEpisodes.value = emptyList()
            try {
                _detailEpisodes.value = libraryRepository.getShowEpisodes(showId)
                _isOffline.value = false
            } catch (e: Exception) {
                handleApiFailure(e)
                if (e is GlobalRateLimitException) {
                    _detailError.value = buildRateLimitMessage(e.lockedUntilEpochMs)
                }
                Log.e(TAG, "Failed to load show episodes: ${e.message}")
            } finally {
                _isDetailLoading.value = false
            }
        }
    }

    fun loadQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            syncQueueState()
        }
    }

    private suspend fun syncQueueState(currentPlaybackUri: String? = _playbackState.value?.item?.uri) {
        try {
            val response = api.getQueue()
            val currentUri = currentPlaybackUri ?: response.currentlyPlaying?.uri
            _queue.value = normalizeQueue(response.queue, currentUri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load queue: ${e.message}")
        }
    }

    private fun normalizeQueue(queueItems: List<SpotifyPlayableItem>, currentUri: String?): List<SpotifyPlayableItem> {
        if (queueItems.isEmpty()) return emptyList()
        if (currentUri.isNullOrBlank()) return queueItems
        return if (queueItems.firstOrNull()?.uri == currentUri) queueItems.drop(1) else queueItems
    }

    // ── Playback Controls ────────────────────────────────────────────

    fun playTrack(trackUri: String, contextUri: String? = null) {
        openNowPlaying()
        viewModelScope.launch {
            val resolvedUri = resolveTrackUriForPlayback(trackUri)
            val preserveContext = resolvedUri == trackUri && !contextUri.isNullOrBlank()
            val success = if (preserveContext) {
                playbackController.play(trackUri = trackUri, contextUri = contextUri)
            } else {
                playbackController.play(trackUri = resolvedUri)
            }
            _deviceNotFoundError.value = !success // Show error if false, clear if true
            delay(500)
            syncPlaybackState()
        }
    }

    fun playContext(contextUri: String) {
        openNowPlaying()
        viewModelScope.launch {
            val success = if (_explicitFilterEnabled.value && shouldPlayLoadedTracksAsUris(contextUri)) {
                playResolvedTrackList(_detailTracks.value)
            } else {
                playbackController.play(trackUri = null, contextUri = contextUri)
            }
            _deviceNotFoundError.value = !success
            delay(500)
            syncPlaybackState()
        }
    }

    fun playCustomMix(mixId: String) {
        viewModelScope.launch {
            _isHomeLoading.value = true
            try {
                val generatedUris = when (mixId) {
                    CUSTOM_MIX_DAILY_DRIVE -> {
                        val savedShows = _savedShows.value.ifEmpty { libraryRepository.getSavedShows() }
                        if (_savedShows.value.isEmpty() && savedShows.isNotEmpty()) {
                            _savedShows.value = savedShows
                        }
                        customMixEngine.buildDailyDrive(_dailyDriveNewsId.value, savedShows)
                    }
                    CUSTOM_MIX_80S -> customMixEngine.buildDecadeMix("198")
                    CUSTOM_MIX_90S -> customMixEngine.buildDecadeMix("199")
                    CUSTOM_MIX_2000S -> customMixEngine.buildDecadeMix("200")
                    CUSTOM_MIX_2010S -> customMixEngine.buildDecadeMix("201")
                    else -> {
                        Log.w(TAG, "Unknown custom mix requested: $mixId")
                        emptyList()
                    }
                }

                val playbackUris = resolveUrisForPlayback(generatedUris)
                val success = playbackUris.isNotEmpty() && playbackController.play(uris = playbackUris)

                _deviceNotFoundError.value = !success
                if (success) {
                    _isOffline.value = false
                    openNowPlaying()
                    delay(500)
                    syncPlaybackState()
                }
            } catch (e: Exception) {
                handleApiFailure(e)
                Log.e(TAG, "playCustomMix failed for $mixId: ${e.message}", e)
            } finally {
                _isHomeLoading.value = false
            }
        }
    }

    fun setDailyDriveNewsPodcast(showId: String) {
        val normalized = showId.trim()
        if (normalized.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                tokenManager.saveDailyDriveNewsId(normalized)
                _dailyDriveNewsId.value = normalized
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save Daily Drive news podcast: ${e.message}", e)
            }
        }
    }

    fun playTrackInContext(trackUri: String, contextUri: String, index: Int) {
        openNowPlaying()
        viewModelScope.launch {
            val success = if (
                contextUri == "spotify:user:me:collection" ||
                (_explicitFilterEnabled.value && shouldPlayLoadedTracksAsUris(contextUri))
            ) {
                playResolvedTrackList(_detailTracks.value.drop(index))
            } else {
                playbackController.play(trackUri = trackUri, contextUri = contextUri, offsetPosition = index)
            }
            _deviceNotFoundError.value = !success
            delay(500)
            syncPlaybackState()
        }
    }

    fun togglePlayPause() {
        val current = _playbackState.value
        val isCurrentlyPlaying = current?.isPlaying == true
        _playbackState.update { it?.copy(isPlaying = !isCurrentlyPlaying) } // Instant UI update
        viewModelScope.launch {
            val success = if (isCurrentlyPlaying) {
                playbackController.pause()
            } else {
                playbackController.forceResume()
            }
            _deviceNotFoundError.value = !success
            delay(500)
            syncPlaybackState()
        }
    }

    fun resumePlayback() {
        openNowPlaying()
        viewModelScope.launch {
            val success = playbackController.forceResume()
            _deviceNotFoundError.value = !success
            delay(500)
            syncPlaybackState()
        }
    }

    fun skipNext() {
        viewModelScope.launch {
            playbackController.next()
            delay(500)
            syncPlaybackState()
        }
    }

    fun skipPrevious() {
        viewModelScope.launch {
            playbackController.previous()
            delay(500)
            syncPlaybackState()
        }
    }

    fun skipBack15Seconds() {
        val currentProgress = _playbackState.value?.progressMs ?: return
        seekTo((currentProgress - 15_000L).coerceAtLeast(0L))
    }

    fun skipForward15Seconds() {
        val playback = _playbackState.value ?: return
        val duration = playback.item?.durationMs ?: return
        val currentProgress = playback.progressMs ?: 0L
        seekTo((currentProgress + 15_000L).coerceAtMost(duration))
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            playbackController.seek(positionMs)
            _playbackState.update { it?.copy(progressMs = positionMs) }
        }
    }

    fun toggleShuffle() {
        viewModelScope.launch {
            val current = _playbackState.value?.shuffleState ?: false
            playbackController.setShuffle(!current)
            _playbackState.update { it?.copy(shuffleState = !current) }
        }
    }

    fun toggleRepeat() {
        viewModelScope.launch {
            val current = _playbackState.value?.repeatState ?: "off"
            val next = when (current) {
                "off" -> "context"
                "context" -> "track"
                else -> "off"
            }
            playbackController.setRepeat(next)
            _playbackState.update { it?.copy(repeatState = next) }
        }
    }

    fun toggleSaveTrack() {
        viewModelScope.launch(Dispatchers.IO) {
            val trackUri = _playbackState.value?.item?.uri ?: return@launch
            try {
                if (_isTrackSaved.value) {
                    api.removeLibraryItems(uris = trackUri)
                    _isTrackSaved.value = false
                } else {
                    api.saveLibraryItems(uris = trackUri)
                    _isTrackSaved.value = true
                }
                lastSavedStatusTrackUri = null
                lastSavedStatusCheckedAtMs = 0L
            } catch (e: Exception) {
                Log.e(TAG, "Toggle save failed: ${e.message}")
            }
        }
    }

    fun addTrackToQueue(trackUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            enqueueUris(listOf(resolveTrackUriForPlayback(trackUri)))
        }
    }

    fun addEpisodeToQueue(episodeUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            enqueueUris(listOf(episodeUri))
        }
    }

    fun addPlaylistToQueue(playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val tracks = fetchPlaylistTracks(playlistId)
            enqueueUris(resolveTrackUrisForPlayback(tracks))
        }
    }

    fun addAlbumToQueue(albumId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val tracks = fetchAlbumTracks(albumId)
            enqueueUris(resolveTrackUrisForPlayback(tracks))
        }
    }

    fun addLikedSongsToQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            val tracks = libraryRepository.getSavedTracks(maxTracks = MAX_QUEUE_BATCH)
            enqueueUris(resolveTrackUrisForPlayback(tracks))
        }
    }

    /**
     * Removes a track from the local queue display by index.
     *
     * **Important limitation**: Spotify's Web API does not support
     * removing a specific track from the playback queue by index.
     * This method only updates the local [_queue] StateFlow so the
     * UI reflects the dismissal; the track will still play on the phone.
     *
     * @param index Zero-based index of the track to remove.
     */
    fun removeFromQueue(index: Int) {
        // Spotify's Web API doesn't support removing by index from queue.
        // We update local state only; the track will still play.
        _queue.update { current ->
            current.toMutableList().apply {
                if (index in indices) removeAt(index)
            }
        }
    }

    fun startRadioFromCurrentTrack() {
        val item = _playbackState.value?.item ?: return
        if (item.type != "track") return

        viewModelScope.launch(Dispatchers.IO) {
            val seedTrack = resolveTrackByUri(item.uri)
                ?: SpotifyTrack(
                    id = item.id,
                    name = item.name,
                    uri = item.uri,
                    durationMs = item.durationMs,
                    artists = item.artists.orEmpty(),
                    album = item.album,
                    explicit = item.explicit
                )
            startRadioFromSeedTracks(listOf(seedTrack), "current track")
        }
    }

    fun startRadioFromLoadedContext() {
        val seeds = _detailTracks.value
        if (seeds.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            startRadioFromSeedTracks(seeds, "detail context")
        }
    }

    fun startRadioFromArtist() {
        val seeds = (_artistTopTracks.value + _artistLikedSongs.value)
            .distinctBy { it.id ?: it.uri }
        if (seeds.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            startRadioFromSeedTracks(seeds, "artist")
        }
    }

    fun dismissReauthBanner() {
        _requiresReauth.value = false
        hasDismissedReauthThisSession = true
    }

    fun dismissDeviceNotFoundError() {
        _deviceNotFoundError.value = false
    }

    /**
     * Classifies an API exception to update the UI state banners.
     *
     * - [UnknownHostException] / [SocketTimeoutException] → show offline banner.
     * - [HttpException] 401/403 → show re-authentication banner.
     * - All other exceptions are ignored (logged elsewhere).
     *
     * @param e The exception thrown by a Retrofit / OkHttp call.
     */
    private fun handleApiFailure(e: Exception) {
        when (e) {
            is GlobalRateLimitException -> {
                _rateLimitUntilEpochMs.value = e.lockedUntilEpochMs
            }

            is UnknownHostException, is SocketTimeoutException -> {
                _isOffline.value = true
            }

            is HttpException -> {
                val code = e.code()
                if (code == 401 || code == 403) {
                    if (!hasDismissedReauthThisSession) _requiresReauth.value = true
                }
            }
        }
    }

    // ── Metadata Sync Loop ───────────────────────────────────────────

    /**
     * Starts the 3-second metadata polling loop.
     *
     * Why polling instead of WebSocket or push:
     * Spotify's Web API has no push/streaming endpoint for playback-state
     * changes. The only way to keep the car screen's Now Playing card in
     * sync with what's actually playing on the phone is to poll
     * `GET /v1/me/player` at a regular interval.
     *
     * 3 seconds strikes a balance between responsiveness (track changes
     * are reflected quickly) and API rate-limit budget (Spotify allows
     * ~180 requests/minute before returning 429).
     *
     * The loop runs in [viewModelScope] so it is automatically cancelled
     * when the ViewModel is cleared (Activity destroyed).
     */
    private fun startMetadataSync(
        forceRestart: Boolean = false,
        immediateSync: Boolean = false
    ) {
        if (forceRestart) {
            metadataSyncJob?.cancel()
            metadataSyncJob = null
        }

        if (metadataSyncJob?.isActive == true) {
            if (immediateSync) {
                viewModelScope.launch { syncPlaybackState() }
            }
            return
        }

        metadataSyncJob = viewModelScope.launch {
            var skipNextLoopSync = false
            if (immediateSync) {
                syncPlaybackState()
                skipNextLoopSync = true
            }

            while (isActive) {
                if (skipNextLoopSync) {
                    skipNextLoopSync = false
                } else {
                    syncPlaybackState()
                }

                val currentItem = _playbackState.value?.item
                val isPlaying = _playbackState.value?.isPlaying == true
                val isPodcast = currentItem?.type == "episode" || currentItem?.type == "chapter"
                val delayTime = when {
                    isPlaying && isPodcast -> PODCAST_METADATA_POLL_MS
                    isPlaying -> METADATA_POLL_MS
                    else -> 4000L
                }
                delay(delayTime)
            }
        }
    }

    /**
     * Fetches the user's current playback state from Spotify.
     *
     * Updates [_playbackState] and [_isTrackSaved]. Also toggles
     * [_isOffline] based on network connectivity.
     *
     * Errors are logged but never thrown — this is a non-critical
     * background sync; crashing would break the entire polling loop.
     *
     * **Rate Limiting**: Handles 429 errors by respecting the Retry-After
     * header with exponential backoff to prevent API abuse.
     */
    private suspend fun syncPlaybackState() {
        try {
            val previousPlayback = _playbackState.value
            val previousItemUri = previousPlayback?.item?.uri
            val previousWasPodcast = previousPlayback?.item?.type == "episode" || previousPlayback?.item?.type == "chapter"
            var playback = playbackController.getCurrentPlayback()

            if (playback == null && previousWasPodcast && previousPlayback?.isPlaying == true) {
                delay(PODCAST_RETRY_DELAY_MS)
                playback = playbackController.getCurrentPlayback()
            }

            if (playback != null) {
                _isOffline.value = false
                _deviceNotFoundError.value = false // Auto-clear error when device is detected
                _playbackState.value = playback
                lastSuccessfulPlaybackSyncAtMs = System.currentTimeMillis()
                val currentItemUri = playback.item?.uri
                val playbackItemChanged = currentItemUri != previousItemUri

                if (_currentScreen.value is Screen.Queue || playbackItemChanged) {
                    syncQueueState(currentItemUri)
                }

                if (playback.item?.type == "track") {
                    playback.item.uri?.let { trackUri ->
                        refreshSavedTrackStatus(
                            trackUri = trackUri,
                            force = playbackItemChanged || _showNowPlaying.value
                        )
                    }
                } else {
                    _isTrackSaved.value = false
                    lastSavedStatusTrackUri = null
                    lastSavedStatusCheckedAtMs = 0L
                }
            } else {
                val withinPodcastTolerance = previousWasPodcast &&
                    previousPlayback?.isPlaying == true &&
                    (System.currentTimeMillis() - lastSuccessfulPlaybackSyncAtMs) < PODCAST_NULL_TOLERANCE_MS

                if (withinPodcastTolerance) {
                    _playbackState.update { current ->
                        val currentProgress = current?.progressMs ?: 0L
                        current?.copy(
                            progressMs = (currentProgress + PODCAST_METADATA_POLL_MS)
                                .coerceAtMost(current.item?.durationMs ?: (currentProgress + PODCAST_METADATA_POLL_MS))
                        )
                    }
                    return
                }

                _playbackState.update { it?.copy(isPlaying = false) }
                if (_currentScreen.value is Screen.Queue) {
                    syncQueueState()
                }
            }
        } catch (e: GlobalRateLimitException) {
            handleApiFailure(e)
            Log.w(TAG, "Metadata sync blocked by global rate limit until ${e.lockedUntilEpochMs}")
        } catch (e: retrofit2.HttpException) {
            handleApiFailure(e)
            Log.w(TAG, "HTTP error during metadata sync: ${e.code()} ${e.message}")
        } catch (e: UnknownHostException) {
            _isOffline.value = true
            Log.w(TAG, "Metadata sync offline: ${e.message}")
        } catch (e: SocketTimeoutException) {
            _isOffline.value = true
            Log.w(TAG, "Metadata sync timeout: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Metadata sync error: ${e.message}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /** Best artwork URL from an image list (largest first). */
    fun bestArtwork(images: List<SpotifyImage>?): String? =
        images?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url

    fun buildRateLimitMessage(untilEpochMs: Long = _rateLimitUntilEpochMs.value): String {
        val remainingMs = (untilEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
        return if (remainingMinutes >= 60) {
            val hours = remainingMinutes / 60
            val minutes = remainingMinutes % 60
            "Spotify rate limited the app. Try again in ${hours}h ${minutes}m."
        } else {
            val minutes = remainingMinutes.coerceAtLeast(1L)
            "Spotify rate limited the app. Try again in ${minutes}m."
        }
    }

    // ── Device Lock Settings ─────────────────────────────────────────

    /**
     * Load locked device setting from DataStore on startup.
     * Sets [DeviceManager.lockedDeviceId] so all playback commands
     * will target the locked device.
     */
    private fun loadLockedDeviceSetting() {
        viewModelScope.launch(Dispatchers.IO) {
            val lockedId = tokenManager.getLockedDeviceId()
            val lockedName = tokenManager.getLockedDeviceName()
            _lockedDeviceId.value = lockedId
            _lockedDeviceName.value = lockedName
            deviceManager.lockedDeviceId = lockedId
            if (lockedId != null) {
                Log.i(TAG, "Device locked to: $lockedName ($lockedId)")
            }
        }
    }

    /** Fetch available Spotify Connect devices for the settings screen. */
    fun loadDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = api.getDevices()
                _deviceList.value = response.devices
                Log.d(TAG, "Loaded ${response.devices.size} devices")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load devices: ${e.message}")
            }
        }
    }

    /** Lock playback to a specific device. */
    fun lockDevice(deviceId: String, deviceName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            tokenManager.lockDevice(deviceId, deviceName)
            _lockedDeviceId.value = deviceId
            _lockedDeviceName.value = deviceName
            deviceManager.lockedDeviceId = deviceId
            Log.i(TAG, "Locked to device: $deviceName ($deviceId)")
        }
    }

    /** Unlock device — return to automatic discovery. */
    fun unlockDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            tokenManager.unlockDevice()
            _lockedDeviceId.value = null
            _lockedDeviceName.value = null
            deviceManager.lockedDeviceId = null
            Log.i(TAG, "Device unlocked — auto-discovery enabled")
        }
    }

    fun switchActiveProfile(profileId: String) {
        if (profileId == activeProfileId.value) return

        viewModelScope.launch(Dispatchers.IO) {
            tokenManager.setActiveProfileId(profileId)
        }
    }

    fun onProfileAdded() {
        refreshPlaybackStateNow()
    }

    private suspend fun clearProfileScopedData() {
        cacheDb.libraryCacheDao().clearAll()
        cacheDb.pinnedItemDao().clearAll()

        _recentContexts.value = emptyList()
        _featuredPlaylists.value = emptyList()
        _topTracks.value = emptyList()
        _newReleases.value = emptyList()
        _podcastUpdates.value = emptyMap()
        _playlists.value = emptyList()
        _savedAlbums.value = emptyList()
        _savedShows.value = emptyList()
        _savedAudiobooks.value = emptyList()
        _detailTracks.value = emptyList()
        _detailEpisodes.value = emptyList()
        _detailChapters.value = emptyList()
        _detailError.value = null
        _queue.value = emptyList()
        _followedArtists.value = emptyList()
        _artistTopTracks.value = emptyList()
        _artistAlbums.value = emptyList()
        _artistLikedSongs.value = emptyList()
        _currentArtistProfile.value = null
        _playbackState.value = null
        _isTrackSaved.value = false
        _deviceList.value = emptyList()
        _deviceNotFoundError.value = false
        _isOffline.value = false
        _requiresReauth.value = false
        _showNowPlaying.value = false
        lastSavedStatusTrackUri = null
        libraryTab = 0
    }

    // ── Artists ───────────────────────────────────────────────────────

    /** Load followed + top artists for the Library Artists tab. */
    fun loadArtists() {
        if (_followedArtists.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val artists = mutableListOf<SpotifyArtist>()

                // Try followed artists first (requires user-follow-read scope)
                try {
                    val followed = api.getFollowedArtists(limit = 50)
                    artists += followed.artists.items.filterNotNull()
                } catch (e: Exception) {
                    Log.w(TAG, "Followed artists unavailable (missing scope?): ${e.message}")
                }

                // Supplement with top artists (always available)
                try {
                    val top = api.getTopArtists(limit = 50, timeRange = "medium_term")
                    for (artist in top.items.filterNotNull()) {
                        if (artists.none { it.id == artist.id }) {
                            artists += artist
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Top artists failed: ${e.message}")
                }

                _followedArtists.value = artists
                Log.d(TAG, "Loaded ${artists.size} artists")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load artists: ${e.message}")
            }
        }
    }

    /** Load artist detail: top tracks, albums, and liked songs. */
    private fun loadArtistDetail(artistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isDetailLoading.value = true
            _currentArtistProfile.value = null // Reset previous artist
            _artistTopTracks.value = emptyList()
            _artistAlbums.value = emptyList()
            _artistLikedSongs.value = emptyList()

            try {
                supervisorScope {
                    val artistProfileDeferred = async {
                        try {
                            api.getArtist(artistId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load artist profile: ${e.message}")
                            null
                        }
                    }
                    // Fetch full artist profile for the header image.
                    launch {
                        artistProfileDeferred.await()?.let { _currentArtistProfile.value = it }
                    }
                    launch {
                        try {
                            val artistName = artistProfileDeferred.await()?.name
                            _artistTopTracks.value = loadArtistTopTracks(artistId, artistName)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load artist top tracks: ${e.message}")
                        }
                    }
                    launch {
                        try {
                            val albums = api.getArtistAlbums(artistId, limit = 20)
                            _artistAlbums.value = albums.items.filterNotNull()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load artist albums: ${e.message}")
                        }
                    }
                    launch {
                        loadArtistLikedSongs(artistId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load artist detail: ${e.message}")
            } finally {
                _isDetailLoading.value = false
            }
        }
    }

    /**
     * Spotify removed `GET /v1/artists/{id}/top-tracks` in February 2026.
     * We now approximate the same section by searching the catalog for tracks
     * matching the artist name, then filtering the results back to the target artist.
     */
    private suspend fun loadArtistTopTracks(artistId: String, artistName: String?): List<SpotifyTrack> {
        val safeArtistName = artistName?.trim().orEmpty()
        if (safeArtistName.isBlank()) return emptyList()

        return try {
            api.search(
                query = "artist:$safeArtistName",
                type = "track",
                limit = 10
            ).tracks?.items
                .orEmpty()
                .filterNotNull()
                .filter { track ->
                    track.artists.any { artist ->
                        artist.id == artistId || artist.name.equals(safeArtistName, ignoreCase = true)
                    }
                }
                .distinctBy { it.uri }
        } catch (e: Exception) {
            handleApiFailure(e)
            emptyList()
        }
    }

    /**
     * Load the user's saved tracks and filter to only those by the given artist.
     * Loads up to 200 saved tracks (4 pages) to keep API usage reasonable.
     */
    private suspend fun loadArtistLikedSongs(artistId: String) {
        try {
            val liked = mutableListOf<SpotifyTrack>()
            var offset = 0
            val maxPages = 4
            var page = 0
            do {
                val response = api.getSavedTracks(limit = 50, offset = offset)
                val filtered = response.items.filterNotNull()
                    .map { it.track }
                    .filter { track -> track.artists.any { it.id == artistId } }
                liked += filtered
                offset += 50
                page++
            } while (response.next != null && page < maxPages)

            _artistLikedSongs.value = liked
            Log.d(TAG, "Found ${liked.size} liked songs for artist $artistId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load artist liked songs: ${e.message}")
        }
    }

    fun updateGridColumns(columns: Int) {
        viewModelScope.launch(Dispatchers.IO) { tokenManager.saveGridColumns(columns) }
    }
    fun updateRightPadding(padding: Int) {
        viewModelScope.launch(Dispatchers.IO) { tokenManager.saveRightPadding(padding) }
    }
    fun updatePlayInstantly(play: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { tokenManager.savePlayInstantly(play) }
    }
    fun updateExplicitFilterEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { tokenManager.saveExplicitFilterEnabled(enabled) }
    }
    fun moveHomeSectionUp(section: HomeSection) {
        updateHomeSectionOrder { current ->
            val index = current.indexOf(section)
            if (index <= 0) current else current.toMutableList().apply {
                removeAt(index)
                add(index - 1, section)
            }
        }
    }

    fun moveHomeSectionDown(section: HomeSection) {
        updateHomeSectionOrder { current ->
            val index = current.indexOf(section)
            if (index == -1 || index >= current.lastIndex) current else current.toMutableList().apply {
                removeAt(index)
                add(index + 1, section)
            }
        }
    }

    fun resetHomeSectionOrder() {
        viewModelScope.launch(Dispatchers.IO) {
            tokenManager.saveHomeSectionOrder(HomeSection.defaultOrder.map { it.storageKey })
        }
    }

    private fun updateHomeSectionOrder(transform: (List<HomeSection>) -> List<HomeSection>) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = HomeSection.fromStorageKeys(
                transform(homeSectionOrder.value)
                    .map { it.storageKey }
            )
            tokenManager.saveHomeSectionOrder(updated.map { it.storageKey })
        }
    }

    fun playArtistTopTracks() {
        val tracks = _artistTopTracks.value
        if (tracks.isEmpty()) return

        openNowPlaying()
        viewModelScope.launch(Dispatchers.IO) {
            val success = playResolvedTrackList(tracks)
            _deviceNotFoundError.value = !success
            if (success) {
                delay(500)
                syncPlaybackState()
            }
        }
    }

    private suspend fun startRadioFromSeedTracks(seedTracks: List<SpotifyTrack>, source: String): Boolean {
        return try {
            val seedTrackIds = seedTracks.mapNotNull { it.id }.distinct().take(5)
            if (seedTrackIds.isEmpty()) return false

            val recommendedTracks = libraryRepository.getRecommendations(seedTrackIds = seedTrackIds, limit = 50)
            if (recommendedTracks.isEmpty()) return false

            _detailTracks.value = recommendedTracks
            val uris = resolveTrackUrisForPlayback(recommendedTracks)
            val success = uris.isNotEmpty() && playbackController.play(uris = uris)
            _deviceNotFoundError.value = !success
            if (success) {
                _isOffline.value = false
                openNowPlaying()
                delay(500)
                syncPlaybackState()
            }
            success
        } catch (e: Exception) {
            handleApiFailure(e)
            Log.e(TAG, "Failed to start radio from $source: ${e.message}", e)
            false
        }
    }

    private suspend fun playResolvedTrackList(tracks: List<SpotifyTrack>): Boolean {
        val uris = resolveTrackUrisForPlayback(tracks)
        return uris.isNotEmpty() && playbackController.play(uris = uris)
    }

    private suspend fun enqueueUris(uris: List<String>) {
        val distinctUris = uris.filter { it.isNotBlank() }.distinct().take(MAX_QUEUE_BATCH)
        if (distinctUris.isEmpty()) return

        var allSucceeded = true
        distinctUris.forEach { uri ->
            val success = playbackController.addToQueue(uri)
            if (!success) allSucceeded = false
        }

        _deviceNotFoundError.value = !allSucceeded
        delay(250)
        syncQueueState()
    }

    private suspend fun fetchPlaylistTracks(playlistId: String): List<SpotifyTrack> {
        if (playlistId == "liked-songs") {
            return libraryRepository.getSavedTracks(maxTracks = MAX_QUEUE_BATCH)
        }

        val tracks = mutableListOf<SpotifyTrack>()
        var offset = 0
        try {
            do {
                val page = api.getPlaylistItems(playlistId, limit = 50, offset = offset)
                tracks += page.items.filterNotNull().mapNotNull { it.track }
                offset += 50
            } while (page.next != null && offset < page.total && tracks.size < MAX_QUEUE_BATCH)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 403) {
                Log.w(TAG, "403 Forbidden fetching tracks for playlist $playlistId — missing playlist scope. User should Refresh Permissions.")
            } else {
                throw e
            }
        }
        return tracks.take(MAX_QUEUE_BATCH)
    }

    private suspend fun fetchAlbumTracks(albumId: String): List<SpotifyTrack> {
        val tracks = mutableListOf<SpotifyTrack>()
        var offset = 0
        do {
            val page = api.getAlbumTracks(albumId, limit = 50, offset = offset)
            tracks += page.items.filterNotNull()
            offset += 50
        } while (page.next != null && offset < page.total && tracks.size < MAX_QUEUE_BATCH)
        return tracks.take(MAX_QUEUE_BATCH)
    }

    private suspend fun resolveTrackUrisForPlayback(tracks: List<SpotifyTrack>): List<String> =
        tracks.take(100).map { libraryRepository.resolvePlaybackUri(it, _explicitFilterEnabled.value) }

    private suspend fun refreshSavedTrackStatus(trackUri: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val recentlyChecked = trackUri == lastSavedStatusTrackUri &&
            (now - lastSavedStatusCheckedAtMs) < SAVED_STATUS_REFRESH_MS
        if (!force && recentlyChecked) return

        lastSavedStatusTrackUri = trackUri
        lastSavedStatusCheckedAtMs = now
        try {
            val savedList = api.checkSavedLibraryItems(uris = trackUri)
            _isTrackSaved.value = savedList.firstOrNull() ?: false
        } catch (_: Exception) {
            // Non-critical UI sync.
        }
    }

    private suspend fun resolveUrisForPlayback(uris: List<String>): List<String> =
        uris.take(100).map { resolveTrackUriForPlayback(it) }

    private suspend fun resolveTrackUriForPlayback(trackUri: String): String {
        if (!_explicitFilterEnabled.value) return trackUri
        val track = resolveTrackByUri(trackUri) ?: return trackUri
        return libraryRepository.resolvePlaybackUri(track, preferClean = true)
    }

    private suspend fun resolveTrackByUri(trackUri: String): SpotifyTrack? {
        val localTrack = sequenceOf(
            _detailTracks.value,
            _artistTopTracks.value,
            _artistLikedSongs.value,
            _topTracks.value
        ).flatten().firstOrNull { it.uri == trackUri }
        if (localTrack != null) return localTrack

        val trackId = trackUri.substringAfterLast(':', "").ifBlank { return null }
        return try {
            libraryRepository.getTrack(trackId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve track for uri $trackUri: ${e.message}")
            null
        }
    }

    private fun shouldPlayLoadedTracksAsUris(contextUri: String): Boolean = when (val screen = _currentScreen.value) {
        is Screen.PlaylistDetail -> screen.uri == contextUri
        is Screen.AlbumDetail -> screen.uri == contextUri
        else -> false
    }

    fun isPinnableType(type: String): Boolean = type in PINNABLE_TYPES

    fun openPinnedItem(item: PinnedItem) {
        when (item.type) {
            "playlist" -> navigateTo(Screen.PlaylistDetail(item.id, item.name, item.uri))
            "album" -> navigateTo(Screen.AlbumDetail(item.id, item.name, item.uri))
            "show" -> navigateTo(Screen.PodcastDetail(item.id, item.name, item.uri))
        }
    }

    fun togglePin(item: PinnedItem) {
        togglePin(
            id = item.id,
            name = item.name,
            uri = item.uri,
            subtitle = item.subtitle,
            imageUrl = item.imageUrl,
            type = item.type
        )
    }

    fun togglePinForPlaylist(playlist: SpotifyPlaylist) {
        if (playlist.id == null || playlist.uri == null) return
        togglePin(
            id = playlist.id,
            name = playlist.name ?: "Unknown Playlist",
            uri = playlist.uri,
            subtitle = "${playlist.itemCount} tracks",
            imageUrl = bestArtwork(playlist.images),
            type = "playlist"
        )
    }

    fun togglePinForAlbum(album: SpotifyAlbum) {
        togglePin(
            id = album.id ?: return,
            name = album.name,
            uri = album.uri ?: return,
            subtitle = album.artists?.joinToString(", ") { it.name },
            imageUrl = bestArtwork(album.images),
            type = "album"
        )
    }

    fun togglePinForShow(show: SpotifyShow) {
        togglePin(
            id = show.id,
            name = show.name,
            uri = show.uri,
            subtitle = show.publisher ?: "Podcast",
            imageUrl = bestArtwork(show.images),
            type = "show"
        )
    }

    fun togglePinForRecentContext(item: RecentContextItem) {
        when (item.type) {
            "playlist", "album" -> togglePin(
                id = item.id,
                name = item.title,
                uri = item.uri,
                subtitle = item.subtitle,
                imageUrl = item.imageUrl,
                type = item.type
            )
        }
    }

    fun togglePinForSearchResult(item: SearchResultItem) {
        when (item.type) {
            "playlist", "album" -> togglePin(
                id = item.id,
                name = item.title,
                uri = item.uri,
                subtitle = item.subtitle,
                imageUrl = item.imageUrl,
                type = item.type
            )
        }
    }

    fun togglePin(id: String, name: String, uri: String, subtitle: String?, imageUrl: String?, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isPinnableType(type) || id.isBlank() || uri.isBlank() || name.isBlank()) return@launch
            val dao = cacheDb.pinnedItemDao()
            val currentPins = dao.getAllPinnedSync()
            if (currentPins.any { it.uri == uri }) {
                dao.deleteByUri(uri)
                dao.updateAll(
                    currentPins.filterNot { it.uri == uri }
                        .mapIndexed { index, item -> item.copy(orderIndex = index) }
                )
            } else {
                val newPin = PinnedItem(uri, id, name, subtitle, imageUrl, type, currentPins.size)
                dao.insert(newPin)
            }
        }
    }

    fun movePinUp(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = cacheDb.pinnedItemDao()
            val current = dao.getAllPinnedSync().toMutableList()
            val index = current.indexOfFirst { it.uri == uri }
            if (index > 0) {
                val temp = current[index]
                current[index] = current[index - 1]
                current[index - 1] = temp
                current.forEachIndexed { i, item -> current[i] = item.copy(orderIndex = i) }
                dao.updateAll(current)
            }
        }
    }

    fun movePinDown(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = cacheDb.pinnedItemDao()
            val current = dao.getAllPinnedSync().toMutableList()
            val index = current.indexOfFirst { it.uri == uri }
            if (index >= 0 && index < current.size - 1) {
                val temp = current[index]
                current[index] = current[index + 1]
                current[index + 1] = temp
                current.forEachIndexed { i, item -> current[i] = item.copy(orderIndex = i) }
                dao.updateAll(current)
            }
        }
    }

    fun removePin(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = cacheDb.pinnedItemDao()
            dao.deleteByUri(uri)
            dao.updateAll(
                dao.getAllPinnedSync().filterNot { it.uri == uri }
                    .mapIndexed { index, item -> item.copy(orderIndex = index) }
            )
        }
    }


}
