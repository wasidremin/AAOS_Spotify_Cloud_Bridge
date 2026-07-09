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

// ── Albums ────────────────────────────────────────────────────────────

@Composable
internal fun AlbumList(
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

