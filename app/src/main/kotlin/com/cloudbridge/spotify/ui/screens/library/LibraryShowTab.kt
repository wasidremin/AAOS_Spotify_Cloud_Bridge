package com.cloudbridge.spotify.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.cloudbridge.spotify.network.model.*
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.components.AlbumArtTile
import com.cloudbridge.spotify.ui.components.ContextMenuAction
import com.cloudbridge.spotify.ui.theme.*

// ── Shows / Podcasts ─────────────────────────────────────────────────

@Composable
internal fun ShowList(
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
                    (show.description?.contains(query, ignoreCase = true) == true)
            }
            .let { matches ->
                when (sortOption) {
                    ShowSortOption.RecentlyAdded -> matches
                    ShowSortOption.Alphabetical -> matches.sortedBy { it.name.lowercase() }
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

