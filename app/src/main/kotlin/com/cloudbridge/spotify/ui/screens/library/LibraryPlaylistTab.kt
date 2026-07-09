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

// ── Playlists ────────────────────────────────────────────────────────

@Composable
internal fun PlaylistList(
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
                        subtitle = "${playlist.itemCount} tracks",
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
                        subtitle = "${playlist.itemCount} tracks",
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

