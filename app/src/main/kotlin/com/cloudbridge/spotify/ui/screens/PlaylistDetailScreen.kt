package com.cloudbridge.spotify.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cloudbridge.spotify.network.model.SpotifyTrack
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.components.ExplicitBadge
import com.cloudbridge.spotify.ui.theme.*

/**
 * Detail screen for a playlist or album.
 *
 * Header: back arrow, context name, and a "Play All" button that sends
 * a `playContext(uri)` command to the phone.
 *
 * Body: scrollable [LazyColumn] of [TrackRow]s. Each row shows the track
 * number (or speaker icon when active), 64 dp album art, title, artist,
 * and duration. The currently playing track is highlighted in [SpotifyGreen].
 *
 * @param viewModel      The shared [SpotifyViewModel] instance.
 * @param screen         The [SpotifyViewModel.Screen.PlaylistDetail] navigation argument.
 * @param contentPadding Scaffold inner padding.
 */
@Composable
fun PlaylistDetailScreen(
    viewModel: SpotifyViewModel,
    screen: SpotifyViewModel.Screen.PlaylistDetail,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val tracks by viewModel.detailTracks.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val currentPlayingId = playback?.item?.id
    val isLoading by viewModel.isDetailLoading.collectAsState()
    val detailError by viewModel.detailError.collectAsState()
    val canPlayAll = screen.uri.isNotBlank() && screen.uri != "spotify:user:me:collection"
    val layoutDirection = LocalLayoutDirection.current

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateBack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SpotifyWhite,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = screen.name,
                style = MaterialTheme.typography.headlineMedium,
                color = SpotifyWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { viewModel.startRadioFromLoadedContext() },
                    enabled = tracks.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyWhite),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Start Radio", style = MaterialTheme.typography.titleSmall)
                }

                Button(
                    onClick = {
                        if (canPlayAll) {
                            viewModel.playContext(screen.uri)
                        }
                    },
                    enabled = canPlayAll,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyGreen,
                        contentColor = SpotifyBlack
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Play All", style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        HorizontalDivider(color = SpotifyMediumGray.copy(alpha = 0.3f))

        // ── Track List ───────────────────────────────────────────────
        if (isLoading && tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SpotifyGreen)
            }
        } else if (detailError != null && tracks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = SpotifyLightGray, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = detailError!!,
                    style = MaterialTheme.typography.titleMedium,
                    color = SpotifyLightGray,
                    textAlign = TextAlign.Center
                )
            }
        } else if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No tracks found", style = MaterialTheme.typography.bodyLarge, color = SpotifyLightGray)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    top = 4.dp,
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = 4.dp + contentPadding.calculateBottomPadding()
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = tracks,
                    key = { index, track -> "${track.id}-$index" }
                ) { index, track ->
                    TrackRow(
                        viewModel = viewModel,
                        track = track,
                        index = index + 1,
                        isCurrentlyPlaying = track.id == currentPlayingId,
                        onClick = {
                            viewModel.playTrackInContext(
                                trackUri = track.uri,
                                contextUri = screen.uri,
                                index = index
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    viewModel: SpotifyViewModel,
    track: SpotifyTrack,
    index: Int,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { viewModel.addTrackToQueue(track.uri) }
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number
        Box(
            modifier = Modifier.width(56.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isCurrentlyPlaying) {
                Icon(
                    imageVector = Icons.Filled.VolumeUp,
                    contentDescription = "Now playing",
                    tint = SpotifyGreen,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.titleMedium,
                    color = SpotifyMediumGray
                )
            }
        }

        // Small album art
        val artUrl = track.album?.images?.firstOrNull()?.url
        if (artUrl != null) {
            AsyncImage(
                model = artUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(16.dp))
        }

        // Track info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isCurrentlyPlaying) SpotifyGreen else SpotifyWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.explicit) {
                    Spacer(Modifier.width(8.dp))
                    ExplicitBadge()
                }
            }
            Text(
                text = track.artists.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyLightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyMediumGray
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
