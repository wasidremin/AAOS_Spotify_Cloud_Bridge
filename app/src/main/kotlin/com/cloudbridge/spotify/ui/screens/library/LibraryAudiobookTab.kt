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

// ── Audiobooks ────────────────────────────────────────────────────────

@Composable
internal fun AudiobookList(
    audiobooks: List<SpotifyAudiobook>,
    viewModel: SpotifyViewModel,
    contentPadding: PaddingValues,
    gridColumns: Int,
    filterText: String,
    onFilterTextChange: (String) -> Unit,
    sortOption: AudiobookSortOption,
    onSortOptionChange: (AudiobookSortOption) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    var isGridView by rememberSaveable { mutableStateOf(true) }
    val query = filterText.trim().lowercase()
    val filteredAudiobooks = remember(audiobooks, query, sortOption) {
        audiobooks
            .filter { book ->
                query.isBlank() ||
                    book.name.contains(query, ignoreCase = true) ||
                    book.authors?.any { it.name.contains(query, ignoreCase = true) } == true
            }
            .let { matches ->
                when (sortOption) {
                    AudiobookSortOption.RecentlyAdded -> matches
                    AudiobookSortOption.Alphabetical -> matches.sortedBy { it.name.lowercase() }
                    AudiobookSortOption.Author -> matches.sortedWith(
                        compareBy<SpotifyAudiobook>(
                            { it.authors?.firstOrNull()?.name?.lowercase() ?: "" },
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
            filterPlaceholder = "Filter audiobooks",
            sortLabel = sortOption.label,
            sortOptions = AudiobookSortOption.entries.map { it.label },
            onSortSelected = { label -> onSortOptionChange(AudiobookSortOption.entries.first { it.label == label }) },
            isGridView = isGridView,
            onToggleView = { isGridView = !isGridView }
        )

        if (filteredAudiobooks.isEmpty()) {
            EmptyState(if (audiobooks.isEmpty()) "No saved audiobooks" else "No matching audiobooks")
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
                items(filteredAudiobooks, key = { it.id }) { book ->
                    AlbumArtTile(
                        imageUrl = viewModel.bestArtwork(book.images),
                        title = book.name,
                        subtitle = book.authors?.firstOrNull()?.name ?: book.publisher ?: "Audiobook",
                        contextActions = emptyList(),
                        onClick = {
                            viewModel.navigateTo(
                                SpotifyViewModel.Screen.AudiobookDetail(book.id, book.name, book.uri)
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
                items(filteredAudiobooks, key = { it.id }) { book ->
                    LibraryRow(
                        imageUrl = viewModel.bestArtwork(book.images),
                        title = book.name,
                        subtitle = book.authors?.firstOrNull()?.name ?: book.publisher ?: "Audiobook",
                        contextActions = emptyList(),
                        onClick = {
                            viewModel.navigateTo(
                                SpotifyViewModel.Screen.AudiobookDetail(book.id, book.name, book.uri)
                            )
                        }
                    )
                }
            }
        }
    }
}

