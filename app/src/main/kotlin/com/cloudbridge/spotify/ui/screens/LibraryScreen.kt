package com.cloudbridge.spotify.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cloudbridge.spotify.network.model.SpotifyAlbum
import com.cloudbridge.spotify.network.model.SpotifyArtist
import com.cloudbridge.spotify.network.model.SpotifyPlaylist
import com.cloudbridge.spotify.network.model.SpotifyShow
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.components.AlbumArtTile
import com.cloudbridge.spotify.ui.components.ContextMenuAction
import com.cloudbridge.spotify.ui.theme.*

private enum class PlaylistSortOption(val label: String) {
    RecentlyAdded("Recently Added"),
    Alphabetical("Alphabetical"),
    Creator("Creator")
}

private enum class AlbumSortOption(val label: String) {
    RecentlyAdded("Recently Added"),
    Alphabetical("Alphabetical"),
    Artist("Artist"),
    ReleaseDate("Release Date")
}

private enum class ArtistSortOption(val label: String) {
    RecentlyAdded("Recently Added"),
    Alphabetical("Alphabetical"),
    Genre("Genre")
}

private enum class ShowSortOption(val label: String) {
    RecentlyAdded("Recently Added"),
    Alphabetical("Alphabetical"),
    Publisher("Publisher")
}

/**
 * Library screen with tabbed navigation: **Playlists** / **Albums** / **Podcasts**.
 *
 * Each tab renders a 4-column [LazyVerticalGrid] of [AlbumArtTile]s.
 * The Playlists tab includes a synthetic "Liked Songs" tile at position 0.
 *
 * Data is loaded lazily on first visit and cached in the [SpotifyViewModel];
 * subsequent visits reuse the cached list without hitting the API.
 *
 * @param viewModel      The shared [SpotifyViewModel] instance.
 * @param contentPadding Scaffold inner padding.
 */
@OptIn(ExperimentalMaterial3Api::class) // <-- ADD THIS LINE
@Composable
fun LibraryScreen(
    viewModel: SpotifyViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val playlists by viewModel.playlists.collectAsState()
    val albums by viewModel.savedAlbums.collectAsState()
    val shows by viewModel.savedShows.collectAsState()
    val artists by viewModel.followedArtists.collectAsState()
    val pinnedItems by viewModel.pinnedItems.collectAsState()
    val isLoading by viewModel.isLibraryLoading.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val playInstantly by viewModel.playInstantly.collectAsState()
    val pinnedUris = remember(pinnedItems) { pinnedItems.mapTo(mutableSetOf()) { it.uri } }

    val selectedTab = viewModel.libraryTab
    val tabs = listOf("Playlists", "Albums", "Artists", "Podcasts")
    var playlistFilter by rememberSaveable { mutableStateOf("") }
    var albumFilter by rememberSaveable { mutableStateOf("") }
    var artistFilter by rememberSaveable { mutableStateOf("") }
    var showFilter by rememberSaveable { mutableStateOf("") }
    var playlistSortLabel by rememberSaveable { mutableStateOf(PlaylistSortOption.RecentlyAdded.label) }
    var albumSortLabel by rememberSaveable { mutableStateOf(AlbumSortOption.RecentlyAdded.label) }
    var artistSortLabel by rememberSaveable { mutableStateOf(ArtistSortOption.RecentlyAdded.label) }
    var showSortLabel by rememberSaveable { mutableStateOf(ShowSortOption.RecentlyAdded.label) }

    // Load artists when Artists tab is first selected
    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) viewModel.loadArtists()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Tab Row ──────────────────────────────────────────────────
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SpotifyDarkSurface,
            contentColor = SpotifyWhite,
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = SpotifyGreen
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { viewModel.libraryTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selectedTab == index) SpotifyWhite else SpotifyLightGray
                        )
                    }
                )
            }
        }

        // ── Loading State ────────────────────────────────────────────
        if (isLoading && playlists.isEmpty() && albums.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SpotifyGreen)
            }
            return
        }

        // ── Tab Content ──────────────────────────────────────────────
        when (selectedTab) {
            0 -> PlaylistList(
                playlists = playlists,
                viewModel = viewModel,
                contentPadding = contentPadding,
                gridColumns = gridColumns,
                playInstantly = playInstantly,
                pinnedUris = pinnedUris,
                filterText = playlistFilter,
                onFilterTextChange = { playlistFilter = it },
                sortOption = PlaylistSortOption.entries.first { it.label == playlistSortLabel },
                onSortOptionChange = { playlistSortLabel = it.label }
            )
            1 -> AlbumList(
                albums = albums,
                viewModel = viewModel,
                contentPadding = contentPadding,
                gridColumns = gridColumns,
                pinnedUris = pinnedUris,
                filterText = albumFilter,
                onFilterTextChange = { albumFilter = it },
                sortOption = AlbumSortOption.entries.first { it.label == albumSortLabel },
                onSortOptionChange = { albumSortLabel = it.label }
            )
            2 -> ArtistList(
                artists = artists,
                viewModel = viewModel,
                contentPadding = contentPadding,
                gridColumns = gridColumns,
                filterText = artistFilter,
                onFilterTextChange = { artistFilter = it },
                sortOption = ArtistSortOption.entries.first { it.label == artistSortLabel },
                onSortOptionChange = { artistSortLabel = it.label }
            )
            3 -> ShowList(
                shows = shows,
                viewModel = viewModel,
                contentPadding = contentPadding,
                gridColumns = gridColumns,
                pinnedUris = pinnedUris,
                filterText = showFilter,
                onFilterTextChange = { showFilter = it },
                sortOption = ShowSortOption.entries.first { it.label == showSortLabel },
                onSortOptionChange = { showSortLabel = it.label }
            )
        }
    }
}

// ── Playlists ────────────────────────────────────────────────────────

@Composable
private fun PlaylistList(
    playlists: List<SpotifyPlaylist>,
    viewModel: SpotifyViewModel,
    contentPadding: PaddingValues,
    gridColumns: Int,
    playInstantly: Boolean,
    pinnedUris: Set<String>,
    filterText: String,
    onFilterTextChange: (String) -> Unit,
    sortOption: PlaylistSortOption,
    onSortOptionChange: (PlaylistSortOption) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    var isGridView by rememberSaveable { mutableStateOf(true) }
    val query = filterText.trim().lowercase()
    val filteredPlaylists = remember(playlists, query, sortOption) {
        playlists
            .filter { playlist ->
                query.isBlank() ||
                    playlist.name.orEmpty().contains(query, ignoreCase = true) ||
                    (playlist.owner?.displayName?.contains(query, ignoreCase = true) == true) ||
                    (playlist.description?.contains(query, ignoreCase = true) == true)
            }
            .let { matches ->
                when (sortOption) {
                    PlaylistSortOption.RecentlyAdded -> matches
                    PlaylistSortOption.Alphabetical -> matches.sortedBy { it.name.orEmpty().lowercase() }
                    PlaylistSortOption.Creator -> matches.sortedWith(
                        compareBy<SpotifyPlaylist>(
                            { (it.owner?.displayName ?: "You").lowercase() },
                            { it.name.orEmpty().lowercase() }
                        )
                    )
                }
            }
    }
    val likedSongsVisible = query.isBlank() ||
        "liked songs".contains(query, ignoreCase = true) ||
        "saved tracks".contains(query, ignoreCase = true)

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryToolbar(
            filterValue = filterText,
            onFilterValueChange = onFilterTextChange,
            filterPlaceholder = "Filter playlists",
            sortLabel = sortOption.label,
            sortOptions = PlaylistSortOption.entries.map { it.label },
            onSortSelected = { label -> onSortOptionChange(PlaylistSortOption.entries.first { it.label == label }) },
            isGridView = isGridView,
            onToggleView = { isGridView = !isGridView }
        )

        if (filteredPlaylists.isEmpty() && !likedSongsVisible) {
            EmptyState(if (playlists.isEmpty()) "No playlists found" else "No matching playlists")
            return
        }

        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(
                    start = 16.dp + contentPadding.calculateStartPadding(layoutDirection),
                    top = 4.dp,
                    end = 16.dp + contentPadding.calculateEndPadding(layoutDirection),
                    bottom = 100.dp + contentPadding.calculateBottomPadding()
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (likedSongsVisible) {
                    item {
                        AlbumArtTile(
                            imageUrl = "https://misc.scdn.co/liked-songs/liked-songs-300.png",
                            title = "Liked Songs",
                            subtitle = "Your saved tracks",
                            contextActions = listOf(
                                ContextMenuAction("Add to queue") { viewModel.addLikedSongsToQueue() }
                            ),
                            onClick = {
                                viewModel.navigateTo(
                                    SpotifyViewModel.Screen.PlaylistDetail(
                                        id = "liked-songs",
                                        name = "Liked Songs",
                                        uri = "spotify:user:me:collection"
                                    )
                                )
                            }
                        )
                    }
                }

                items(filteredPlaylists, key = { it.id ?: it.uri ?: it.name ?: it.hashCode() }) { playlist ->
                    AlbumArtTile(
                        imageUrl = viewModel.bestArtwork(playlist.images),
                        title = playlist.name ?: "Unknown Playlist",
                        subtitle = "${playlist.tracks?.total ?: 0} tracks",
                        isPinned = (playlist.uri ?: "") in pinnedUris,
                        contextActions = listOf(
                            ContextMenuAction("Add to queue") { playlist.id?.let(viewModel::addPlaylistToQueue) },
                            ContextMenuAction(if ((playlist.uri ?: "") in pinnedUris) "Unpin" else "Pin to Home") {
                                viewModel.togglePinForPlaylist(playlist)
                            }
                        ),
                        onClick = {
                            val playlistUri = playlist.uri ?: return@AlbumArtTile
                            if (playInstantly) {
                                viewModel.playContext(playlistUri)
                            } else {
                                val playlistId = playlist.id ?: return@AlbumArtTile
                                viewModel.navigateTo(
                                    SpotifyViewModel.Screen.PlaylistDetail(
                                        id = playlistId,
                                        name = playlist.name ?: "Unknown Playlist",
                                        uri = playlistUri
                                    )
                                )
                            }
                        }
                    )
                }
            }
        } else {
            // List view
            LazyColumn(
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = 100.dp + contentPadding.calculateBottomPadding()
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                if (likedSongsVisible) {
                    item {
                        LibraryRow(
                            imageUrl = "https://misc.scdn.co/liked-songs/liked-songs-300.png",
                            title = "Liked Songs",
                            subtitle = "Your saved tracks",
                            contextActions = listOf(
                                ContextMenuAction("Add to queue") { viewModel.addLikedSongsToQueue() }
                            ),
                            onClick = {
                                viewModel.navigateTo(
                                    SpotifyViewModel.Screen.PlaylistDetail(
                                        id = "liked-songs",
                                        name = "Liked Songs",
                                        uri = "spotify:user:me:collection"
                                    )
                                )
                            }
                        )
                    }
                }

                items(filteredPlaylists, key = { it.id ?: it.uri ?: it.name ?: it.hashCode() }) { playlist ->
                    LibraryRow(
                        imageUrl = viewModel.bestArtwork(playlist.images),
                        title = playlist.name ?: "Unknown Playlist",
                        subtitle = "${playlist.tracks?.total ?: 0} tracks",
                        isPinned = (playlist.uri ?: "") in pinnedUris,
                        contextActions = listOf(
                            ContextMenuAction("Add to queue") { playlist.id?.let(viewModel::addPlaylistToQueue) },
                            ContextMenuAction(if ((playlist.uri ?: "") in pinnedUris) "Unpin" else "Pin to Home") {
                                viewModel.togglePinForPlaylist(playlist)
                            }
                        ),
                        onClick = {
                            val playlistUri = playlist.uri ?: return@LibraryRow
                            if (playInstantly) {
                                viewModel.playContext(playlistUri)
                            } else {
                                val playlistId = playlist.id ?: return@LibraryRow
                                viewModel.navigateTo(
                                    SpotifyViewModel.Screen.PlaylistDetail(
                                        id = playlistId,
                                        name = playlist.name ?: "Unknown Playlist",
                                        uri = playlistUri
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

// ── Albums ────────────────────────────────────────────────────────────

@Composable
private fun AlbumList(
    albums: List<SpotifyAlbum>,
    viewModel: SpotifyViewModel,
    contentPadding: PaddingValues,
    gridColumns: Int,
    pinnedUris: Set<String>,
    filterText: String,
    onFilterTextChange: (String) -> Unit,
    sortOption: AlbumSortOption,
    onSortOptionChange: (AlbumSortOption) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    var isGridView by rememberSaveable { mutableStateOf(true) }
    val query = filterText.trim().lowercase()
    val filteredAlbums = remember(albums, query, sortOption) {
        albums
            .filter { album ->
                query.isBlank() ||
                    album.name.contains(query, ignoreCase = true) ||
                    album.artists.orEmpty().any { it.name.contains(query, ignoreCase = true) } ||
                    (album.releaseDate?.contains(query, ignoreCase = true) == true)
            }
            .let { matches ->
                when (sortOption) {
                    AlbumSortOption.RecentlyAdded -> matches
                    AlbumSortOption.Alphabetical -> matches.sortedBy { it.name.lowercase() }
                    AlbumSortOption.Artist -> matches.sortedWith(
                        compareBy<SpotifyAlbum>(
                            { it.artists?.firstOrNull()?.name?.lowercase() ?: "" },
                            { it.name.lowercase() }
                        )
                    )
                    AlbumSortOption.ReleaseDate -> matches.sortedByDescending { it.releaseDate.orEmpty() }
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryToolbar(
            filterValue = filterText,
            onFilterValueChange = onFilterTextChange,
            filterPlaceholder = "Filter albums",
            sortLabel = sortOption.label,
            sortOptions = AlbumSortOption.entries.map { it.label },
            onSortSelected = { label -> onSortOptionChange(AlbumSortOption.entries.first { it.label == label }) },
            isGridView = isGridView,
            onToggleView = { isGridView = !isGridView }
        )

        if (filteredAlbums.isEmpty()) {
            EmptyState(if (albums.isEmpty()) "No saved albums" else "No matching albums")
            return
        }

        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(
                    start = 16.dp + contentPadding.calculateStartPadding(layoutDirection),
                    top = 4.dp,
                    end = 16.dp + contentPadding.calculateEndPadding(layoutDirection),
                    bottom = 100.dp + contentPadding.calculateBottomPadding()
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredAlbums, key = { it.id ?: it.name }) { album ->
                    AlbumArtTile(
                        imageUrl = viewModel.bestArtwork(album.images),
                        title = album.name,
                        subtitle = album.artists?.joinToString(", ") { it.name } ?: "",
                        isPinned = (album.uri ?: "") in pinnedUris,
                        contextActions = listOfNotNull(
                            album.id?.let { albumId ->
                                ContextMenuAction("Add to queue") { viewModel.addAlbumToQueue(albumId) }
                            },
                            ContextMenuAction(if ((album.uri ?: "") in pinnedUris) "Unpin" else "Pin to Home") {
                                viewModel.togglePinForAlbum(album)
                            }
                        ),
                        onClick = {
                            viewModel.navigateTo(
                                SpotifyViewModel.Screen.AlbumDetail(
                                    id = album.id ?: return@AlbumArtTile,
                                    name = album.name,
                                    uri = album.uri
                                )
                            )
                        }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = 100.dp + contentPadding.calculateBottomPadding()
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredAlbums, key = { it.id ?: it.name }) { album ->
                    LibraryRow(
                        imageUrl = viewModel.bestArtwork(album.images),
                        title = album.name,
                        subtitle = album.artists?.joinToString(", ") { it.name } ?: "",
                        isPinned = (album.uri ?: "") in pinnedUris,
                        contextActions = listOfNotNull(
                            album.id?.let { albumId ->
                                ContextMenuAction("Add to queue") { viewModel.addAlbumToQueue(albumId) }
                            },
                            ContextMenuAction(if ((album.uri ?: "") in pinnedUris) "Unpin" else "Pin to Home") {
                                viewModel.togglePinForAlbum(album)
                            }
                        ),
                        onClick = {
                            viewModel.navigateTo(
                                SpotifyViewModel.Screen.AlbumDetail(
                                    id = album.id ?: return@LibraryRow,
                                    name = album.name,
                                    uri = album.uri
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

// ── Artists ───────────────────────────────────────────────────────────

@Composable
private fun ArtistList(
    artists: List<SpotifyArtist>,
    viewModel: SpotifyViewModel,
    contentPadding: PaddingValues,
    gridColumns: Int,
    filterText: String,
    onFilterTextChange: (String) -> Unit,
    sortOption: ArtistSortOption,
    onSortOptionChange: (ArtistSortOption) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val query = filterText.trim().lowercase()
    val filteredArtists = remember(artists, query, sortOption) {
        artists
            .filter { artist ->
                query.isBlank() ||
                    artist.name.contains(query, ignoreCase = true) ||
                    artist.genres.orEmpty().any { it.contains(query, ignoreCase = true) }
            }
            .let { matches ->
                when (sortOption) {
                    ArtistSortOption.RecentlyAdded -> matches
                    ArtistSortOption.Alphabetical -> matches.sortedBy { it.name.lowercase() }
                    ArtistSortOption.Genre -> matches.sortedWith(
                        compareBy<SpotifyArtist>(
                            { it.genres?.firstOrNull()?.lowercase() ?: "" },
                            { it.name.lowercase() }
                        )
                    )
                }
            }
    }

    if (filteredArtists.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryToolbar(
                filterValue = filterText,
                onFilterValueChange = onFilterTextChange,
                filterPlaceholder = "Filter artists",
                sortLabel = sortOption.label,
                sortOptions = ArtistSortOption.entries.map { it.label },
                onSortSelected = { label -> onSortOptionChange(ArtistSortOption.entries.first { it.label == label }) }
            )
            EmptyState(if (artists.isEmpty()) "No artists found" else "No matching artists")
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryToolbar(
            filterValue = filterText,
            onFilterValueChange = onFilterTextChange,
            filterPlaceholder = "Filter artists",
            sortLabel = sortOption.label,
            sortOptions = ArtistSortOption.entries.map { it.label },
            onSortSelected = { label -> onSortOptionChange(ArtistSortOption.entries.first { it.label == label }) }
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            contentPadding = PaddingValues(
                start = 16.dp + contentPadding.calculateStartPadding(layoutDirection),
                top = 4.dp,
                end = 16.dp + contentPadding.calculateEndPadding(layoutDirection),
                bottom = 100.dp + contentPadding.calculateBottomPadding()
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredArtists, key = { it.id ?: it.name }) { artist ->
                ArtistTile(artist, viewModel)
            }
        }
    }
}

@Composable
private fun ArtistTile(artist: SpotifyArtist, viewModel: SpotifyViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                viewModel.navigateTo(
                    SpotifyViewModel.Screen.ArtistDetail(
                        id = artist.id ?: return@clickable,
                        name = artist.name,
                        imageUrl = viewModel.bestArtwork(artist.images)
                    )
                )
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular artist image
        AsyncImage(
            model = viewModel.bestArtwork(artist.images),
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            color = SpotifyWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        artist.genres?.firstOrNull()?.let { genre ->
            Text(
                text = genre,
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyLightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Shows / Podcasts ─────────────────────────────────────────────────

@Composable
private fun ShowList(
    shows: List<SpotifyShow>,
    viewModel: SpotifyViewModel,
    contentPadding: PaddingValues,
    gridColumns: Int,
    pinnedUris: Set<String>,
    filterText: String,
    onFilterTextChange: (String) -> Unit,
    sortOption: ShowSortOption,
    onSortOptionChange: (ShowSortOption) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    var isGridView by rememberSaveable { mutableStateOf(true) }
    val query = filterText.trim().lowercase()
    val filteredShows = remember(shows, query, sortOption) {
        shows
            .filter { show ->
                query.isBlank() ||
                    show.name.contains(query, ignoreCase = true) ||
                    (show.publisher?.contains(query, ignoreCase = true) == true) ||
                    (show.description?.contains(query, ignoreCase = true) == true)
            }
            .let { matches ->
                when (sortOption) {
                    ShowSortOption.RecentlyAdded -> matches
                    ShowSortOption.Alphabetical -> matches.sortedBy { it.name.lowercase() }
                    ShowSortOption.Publisher -> matches.sortedWith(
                        compareBy<SpotifyShow>(
                            { (it.publisher ?: "Podcast").lowercase() },
                            { it.name.lowercase() }
                        )
                    )
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryToolbar(
            filterValue = filterText,
            onFilterValueChange = onFilterTextChange,
            filterPlaceholder = "Filter podcasts",
            sortLabel = sortOption.label,
            sortOptions = ShowSortOption.entries.map { it.label },
            onSortSelected = { label -> onSortOptionChange(ShowSortOption.entries.first { it.label == label }) },
            isGridView = isGridView,
            onToggleView = { isGridView = !isGridView }
        )

        if (filteredShows.isEmpty()) {
            EmptyState(if (shows.isEmpty()) "No saved podcasts" else "No matching podcasts")
            return
        }

        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(
                    start = 16.dp + contentPadding.calculateStartPadding(layoutDirection),
                    top = 4.dp,
                    end = 16.dp + contentPadding.calculateEndPadding(layoutDirection),
                    bottom = 100.dp + contentPadding.calculateBottomPadding()
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredShows, key = { it.id }) { show ->
                    AlbumArtTile(
                        imageUrl = viewModel.bestArtwork(show.images),
                        title = show.name,
                        subtitle = show.publisher ?: "Podcast",
                        isPinned = show.uri in pinnedUris,
                        contextActions = listOf(
                            ContextMenuAction(if (show.uri in pinnedUris) "Unpin" else "Pin to Home") {
                                viewModel.togglePinForShow(show)
                            }
                        ),
                        onClick = { viewModel.navigateTo(SpotifyViewModel.Screen.PodcastDetail(show.id, show.name, show.uri)) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = 100.dp + contentPadding.calculateBottomPadding()
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredShows, key = { it.id }) { show ->
                    LibraryRow(
                        imageUrl = viewModel.bestArtwork(show.images),
                        title = show.name,
                        subtitle = show.publisher ?: "Podcast",
                        isPinned = show.uri in pinnedUris,
                        contextActions = listOf(
                            ContextMenuAction(if (show.uri in pinnedUris) "Unpin" else "Pin to Home") {
                                viewModel.togglePinForShow(show)
                            }
                        ),
                        onClick = { viewModel.navigateTo(SpotifyViewModel.Screen.PodcastDetail(show.id, show.name, show.uri)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryToolbar(
    filterValue: String,
    onFilterValueChange: (String) -> Unit,
    filterPlaceholder: String,
    sortLabel: String,
    sortOptions: List<String>,
    onSortSelected: (String) -> Unit,
    isGridView: Boolean? = null,
    onToggleView: (() -> Unit)? = null
) {
    var sortExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = filterValue,
            onValueChange = onFilterValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(filterPlaceholder) },
            shape = RoundedCornerShape(16.dp)
        )

        Box {
            OutlinedButton(
                onClick = { sortExpanded = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(min = 170.dp)
            ) {
                Text("Sort: $sortLabel", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false }
            ) {
                sortOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSortSelected(option)
                            sortExpanded = false
                        }
                    )
                }
            }
        }

        if (isGridView != null && onToggleView != null) {
            ViewModeToggle(isGridView = isGridView, onToggle = onToggleView)
        }
    }
}

// ── Reusable Row ─────────────────────────────────────────────────────

@Composable
private fun ViewModeToggle(isGridView: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
            contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
            tint = SpotifyWhite,
            modifier = Modifier.size(28.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    imageUrl: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    contextActions: List<ContextMenuAction> = emptyList(),
    isPinned: Boolean = false
) {
    var menuExpanded by remember(title, subtitle, contextActions.size) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    when {
                        contextActions.isNotEmpty() -> menuExpanded = true
                        onLongClick != null -> onLongClick()
                    }
                }
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = SpotifyWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyLightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isPinned) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = "Pinned",
                tint = SpotifyGreen,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(24.dp)
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            contextActions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        menuExpanded = false
                        action.onClick()
                    }
                )
            }
        }
    }
}

// ── Empty State ──────────────────────────────────────────────────────

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = SpotifyLightGray
        )
    }
}
