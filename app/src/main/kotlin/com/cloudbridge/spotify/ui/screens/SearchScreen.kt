package com.cloudbridge.spotify.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.components.AlbumArtTile
import com.cloudbridge.spotify.ui.components.ContextMenuAction
import com.cloudbridge.spotify.ui.theme.SpotifyGreen
import com.cloudbridge.spotify.ui.theme.SpotifyLightGray
import com.cloudbridge.spotify.ui.theme.SpotifyWhite

/**
 * Full-text search screen with debounced query input.
 *
 * Layout:
 * - **Top**: [OutlinedTextField] for the search query.
 * - **Body**: 4-column [LazyVerticalGrid] of [AlbumArtTile] results
 *   (playlists, albums, and tracks merged into a single grid).
 *
 * Search is debounced by 750 ms in [SpotifyViewModel.updateSearchQuery]
 * to prevent excessive API calls while the user types.
 *
 * @param viewModel      The shared [SpotifyViewModel] instance.
 * @param contentPadding Scaffold inner padding.
 * @param onVoiceSearch  Callback invoked when the mic button is tapped; launches
 *                       [android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH].
 */
@Composable
fun SearchScreen(
    viewModel: SpotifyViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onVoiceSearch: () -> Unit = {}
) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val pinnedItems by viewModel.pinnedItems.collectAsState()
    val isLoading by viewModel.isSearchLoading.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val layoutDirection = LocalLayoutDirection.current
    val pinnedUris = androidx.compose.runtime.remember(pinnedItems) { pinnedItems.mapTo(mutableSetOf()) { it.uri } }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.updateSearchQuery(it) },
            label = { Text("Search songs, albums, playlists") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = SpotifyWhite),
            trailingIcon = {
                IconButton(onClick = onVoiceSearch) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Voice search",
                        tint = SpotifyGreen
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 24.dp + contentPadding.calculateStartPadding(layoutDirection),
                    top = 16.dp + contentPadding.calculateTopPadding(),
                    end = 24.dp + contentPadding.calculateEndPadding(layoutDirection),
                    bottom = 12.dp
                )
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SpotifyGreen)
            }
            return
        }

        if (query.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Type to search",
                    style = MaterialTheme.typography.titleMedium,
                    color = SpotifyLightGray
                )
            }
            return
        }

        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No results",
                    style = MaterialTheme.typography.titleMedium,
                    color = SpotifyLightGray
                )
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            contentPadding = PaddingValues(
                start = 24.dp + contentPadding.calculateStartPadding(layoutDirection),
                top = 8.dp,
                end = 24.dp + contentPadding.calculateEndPadding(layoutDirection),
                bottom = 100.dp + contentPadding.calculateBottomPadding()
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Search Results",
                    style = MaterialTheme.typography.headlineMedium,
                    color = SpotifyWhite
                )
            }

            items(results, key = { "search-${it.type}-${it.uri}" }) { item ->
                AlbumArtTile(
                    imageUrl = item.imageUrl,
                    title = item.title,
                    subtitle = item.subtitle,
                    isPinned = item.uri in pinnedUris,
                    contextActions = when (item.type) {
                        "playlist" -> listOf(
                            ContextMenuAction("Add to queue") { viewModel.addPlaylistToQueue(item.id) },
                            ContextMenuAction(if (item.uri in pinnedUris) "Unpin" else "Pin to Home") {
                                viewModel.togglePinForSearchResult(item)
                            }
                        )
                        "album" -> listOf(
                            ContextMenuAction("Add to queue") { viewModel.addAlbumToQueue(item.id) },
                            ContextMenuAction(if (item.uri in pinnedUris) "Unpin" else "Pin to Home") {
                                viewModel.togglePinForSearchResult(item)
                            }
                        )
                        "track" -> listOf(
                            ContextMenuAction("Add to queue") { viewModel.addTrackToQueue(item.uri) }
                        )
                        else -> emptyList()
                    },
                    onClick = {
                        when (item.type) {
                            "playlist" -> {
                                viewModel.navigateTo(
                                    SpotifyViewModel.Screen.PlaylistDetail(
                                        id = item.id,
                                        name = item.title,
                                        uri = item.uri
                                    )
                                )
                            }

                            "album" -> {
                                viewModel.navigateTo(
                                    SpotifyViewModel.Screen.AlbumDetail(
                                        id = item.id,
                                        name = item.title,
                                        uri = item.uri
                                    )
                                )
                            }

                            "track" -> {
                                viewModel.playTrack(trackUri = item.uri)
                            }
                        }
                    }
                )
            }
        }
    }
}
