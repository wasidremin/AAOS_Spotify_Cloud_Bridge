package com.cloudbridge.spotify.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.screens.library.AlbumList
import com.cloudbridge.spotify.ui.screens.library.AlbumSortOption
import com.cloudbridge.spotify.ui.screens.library.ArtistList
import com.cloudbridge.spotify.ui.screens.library.ArtistSortOption
import com.cloudbridge.spotify.ui.screens.library.AudiobookList
import com.cloudbridge.spotify.ui.screens.library.AudiobookSortOption
import com.cloudbridge.spotify.ui.screens.library.PlaylistList
import com.cloudbridge.spotify.ui.screens.library.PlaylistSortOption
import com.cloudbridge.spotify.ui.screens.library.ShowList
import com.cloudbridge.spotify.ui.screens.library.ShowSortOption
import com.cloudbridge.spotify.ui.theme.*

/**
 * Library screen with tabbed navigation: Playlists / Albums / Artists / Podcasts / Audiobooks.
 * Tab content lives under [com.cloudbridge.spotify.ui.screens.library].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: SpotifyViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val playlists by viewModel.playlists.collectAsState()
    val albums by viewModel.savedAlbums.collectAsState()
    val shows by viewModel.savedShows.collectAsState()
    val audiobooks by viewModel.savedAudiobooks.collectAsState()
    val artists by viewModel.followedArtists.collectAsState()
    val pinnedItems by viewModel.pinnedItems.collectAsState()
    val isLoading by viewModel.isLibraryLoading.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val playInstantly by viewModel.playInstantly.collectAsState()
    val pinnedUris = remember(pinnedItems) { pinnedItems.mapTo(mutableSetOf()) { it.uri } }

    val selectedTab = viewModel.libraryTab
    val tabs = listOf("Playlists", "Albums", "Artists", "Podcasts", "Audiobooks")
    var playlistFilter by rememberSaveable { mutableStateOf("") }
    var albumFilter by rememberSaveable { mutableStateOf("") }
    var artistFilter by rememberSaveable { mutableStateOf("") }
    var showFilter by rememberSaveable { mutableStateOf("") }
    var audiobookFilter by rememberSaveable { mutableStateOf("") }
    var playlistSortLabel by rememberSaveable { mutableStateOf(PlaylistSortOption.RecentlyAdded.label) }
    var albumSortLabel by rememberSaveable { mutableStateOf(AlbumSortOption.RecentlyAdded.label) }
    var artistSortLabel by rememberSaveable { mutableStateOf(ArtistSortOption.RecentlyAdded.label) }
    var showSortLabel by rememberSaveable { mutableStateOf(ShowSortOption.RecentlyAdded.label) }
    var audiobookSortLabel by rememberSaveable { mutableStateOf(AudiobookSortOption.RecentlyAdded.label) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) viewModel.loadArtists()
    }

    Column(modifier = Modifier.fillMaxSize()) {
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

        if (isLoading && playlists.isEmpty() && albums.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SpotifyGreen)
            }
            return
        }

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
            4 -> AudiobookList(
                audiobooks = audiobooks,
                viewModel = viewModel,
                contentPadding = contentPadding,
                gridColumns = gridColumns,
                filterText = audiobookFilter,
                onFilterTextChange = { audiobookFilter = it },
                sortOption = AudiobookSortOption.entries.first { it.label == audiobookSortLabel },
                onSortOptionChange = { audiobookSortLabel = it.label }
            )
        }
    }
}
