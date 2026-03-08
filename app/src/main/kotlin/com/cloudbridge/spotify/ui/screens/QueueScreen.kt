package com.cloudbridge.spotify.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cloudbridge.spotify.network.model.SpotifyPlayableItem
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.components.AlbumArtTile
import com.cloudbridge.spotify.ui.theme.*

/**
 * Queue screen showing the currently playing track and upcoming queue.
 *
 * Features:
 * - **Grid / List toggle**: an icon button in the header switches between
 *   a 4-column [LazyVerticalGrid] of [AlbumArtTile]s and a vertical
 *   [LazyColumn] with swipe-to-dismiss rows.
 * - **Now Playing card**: prominent row at the top showing the current track
 *   with large (100 dp) album art and a green "NOW PLAYING" label.
 * - **Swipe-to-dismiss** (list mode only): swiping a row reveals a red
 *   delete icon and removes the track from the **local** queue display.
 *
 * **Known limitation**: Spotify’s Web API does not support removing a
 * specific track from the playback queue. The swipe action is UI-only;
 * the phone will still play the "dismissed" track when its turn comes.
 *
 * @param viewModel      The shared [SpotifyViewModel] instance.
 * @param contentPadding Scaffold inner padding.
 */
@OptIn(ExperimentalMaterial3Api::class) // <-- ADD THIS LINE
@Composable
fun QueueScreen(
    viewModel: SpotifyViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val queue by viewModel.queue.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val layoutDirection = LocalLayoutDirection.current
    var isGridView by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Queue",
                style = MaterialTheme.typography.headlineMedium,
                color = SpotifyWhite
            )

            IconButton(onClick = { isGridView = !isGridView }) {
                Icon(
                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = "Toggle View",
                    tint = SpotifyWhite,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Currently Playing — big prominent card
        playback?.item?.let { current ->
            NowPlayingCard(item = current, onClick = { viewModel.openNowPlaying() })
            HorizontalDivider(
                color = SpotifyMediumGray.copy(alpha = 0.3f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Up Next label
        if (queue.isNotEmpty()) {
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleSmall,
                color = SpotifyLightGray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Queue is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SpotifyLightGray
                )
            }
        } else {
            val contentPaddings = PaddingValues(
                start = 16.dp + contentPadding.calculateStartPadding(layoutDirection),
                top = 4.dp,
                end = 16.dp + contentPadding.calculateEndPadding(layoutDirection),
                bottom = 100.dp + contentPadding.calculateBottomPadding()
            )

            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    contentPadding = contentPaddings,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = queue,
                        key = { index, item -> "grid-queue-${item.id}-$index" }
                    ) { _, item ->
                        AlbumArtTile(
                            imageUrl = item.images?.firstOrNull()?.url
                                ?: item.album?.images?.firstOrNull()?.url
                                ?: item.show?.images?.firstOrNull()?.url
                                ?: item.audiobook?.images?.firstOrNull()?.url,
                            title = item.name,
                            subtitle = if (item.type == "episode") {
                                item.show?.publisher ?: "Podcast"
                            } else if (item.type == "chapter") {
                                item.audiobook?.authors?.joinToString(", ") { it.name }
                                    ?: item.audiobook?.publisher
                                    ?: "Audiobook"
                            } else {
                                item.artists?.joinToString(", ") { it.name } ?: ""
                            },
                            onClick = { viewModel.playTrack(item.uri) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = contentPaddings,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = queue,
                        key = { index, item -> "list-queue-${item.id}-$index" }
                    ) { index, item ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { totalDistance -> totalDistance * 0.5f },
                            confirmValueChange = { value ->
                                if (value != SwipeToDismissBoxValue.Settled) {
                                    viewModel.removeFromQueue(index)
                                    true
                                } else false
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.Settled -> SpotifyCardSurface
                                        else -> ErrorRed.copy(alpha = 0.3f)
                                    },
                                    label = "dismiss_bg"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Remove",
                                        tint = ErrorRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            content = {
                                QueueTrackRow(
                                    item = item,
                                    isCurrentlyPlaying = false,
                                    onClick = { viewModel.playTrack(item.uri) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingCard(item: SpotifyPlayableItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpotifyCardSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Large album art (with podcast fallback)
        val artUrl = item.images?.firstOrNull()?.url
            ?: item.album?.images?.firstOrNull()?.url
            ?: item.show?.images?.firstOrNull()?.url
            ?: item.audiobook?.images?.firstOrNull()?.url
        AsyncImage(
            model = artUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        Spacer(Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall,
                color = SpotifyGreen
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineSmall,
                color = SpotifyWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (item.type == "episode") {
                    item.show?.publisher ?: "Podcast"
                } else if (item.type == "chapter") {
                    item.audiobook?.authors?.joinToString(", ") { it.name }
                        ?: item.audiobook?.publisher
                        ?: "Audiobook"
                } else {
                    item.artists?.joinToString(", ") { it.name } ?: ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = SpotifyLightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QueueTrackRow(
    item: SpotifyPlayableItem,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrentlyPlaying) SpotifyCardSurface else SpotifyBlack)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album/episode art (with podcast fallback)
        val artUrl = item.images?.firstOrNull()?.url
            ?: item.album?.images?.firstOrNull()?.url
            ?: item.show?.images?.firstOrNull()?.url
            ?: item.audiobook?.images?.firstOrNull()?.url
        AsyncImage(
            model = artUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrentlyPlaying) SpotifyGreen else SpotifyWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (item.type == "episode") {
                    item.show?.publisher ?: "Podcast"
                } else if (item.type == "chapter") {
                    item.audiobook?.authors?.joinToString(", ") { it.name }
                        ?: item.audiobook?.publisher
                        ?: "Audiobook"
                } else {
                    item.artists?.joinToString(", ") { it.name } ?: ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = SpotifyLightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
