package com.cloudbridge.spotify.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cloudbridge.spotify.network.model.SpotifyEpisode
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.theme.*

@Composable
fun PodcastDetailScreen(
    viewModel: SpotifyViewModel,
    screen: SpotifyViewModel.Screen.PodcastDetail,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val episodes by viewModel.detailEpisodes.collectAsState()
    val dailyDriveNewsId by viewModel.dailyDriveNewsId.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val currentPlayingId = playback?.item?.id
    val isLoading by viewModel.isDetailLoading.collectAsState()
    val layoutDirection = LocalLayoutDirection.current
    val isNewsPodcast = screen.id == dailyDriveNewsId

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SpotifyWhite, modifier = Modifier.size(40.dp))
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
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { viewModel.setDailyDriveNewsPodcast(screen.id) },
                enabled = !isNewsPodcast,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isNewsPodcast) SpotifyGreen.copy(alpha = 0.24f) else SpotifyGreen,
                    contentColor = if (isNewsPodcast) SpotifyGreen else SpotifyBlack,
                    disabledContainerColor = SpotifyGreen.copy(alpha = 0.24f),
                    disabledContentColor = SpotifyGreen
                )
            ) {
                if (isNewsPodcast) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isNewsPodcast) "News Podcast" else "Set as News")
            }
        }

        HorizontalDivider(color = SpotifyMediumGray.copy(alpha = 0.3f))

        if (isLoading && episodes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SpotifyGreen)
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
                itemsIndexed(episodes, key = { _, ep -> ep.id }) { _, episode ->
                    EpisodeRow(
                        episode = episode,
                        isCurrentlyPlaying = episode.id == currentPlayingId,
                        onClick = { viewModel.playTrack(trackUri = episode.uri, contextUri = screen.uri) },
                        onLongClick = { viewModel.addEpisodeToQueue(episode.uri) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: SpotifyEpisode,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(56.dp), contentAlignment = Alignment.CenterStart) {
            if (isCurrentlyPlaying) {
                Icon(Icons.Filled.VolumeUp, contentDescription = "Now playing", tint = SpotifyGreen, modifier = Modifier.size(32.dp))
            }
        }

        val artUrl = episode.images?.firstOrNull()?.url
        if (artUrl != null) {
            AsyncImage(
                model = artUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrentlyPlaying) SpotifyGreen else SpotifyWhite,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = episode.description ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = SpotifyLightGray,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))

            val resumeMs = episode.resumePoint?.resumePositionMs ?: 0L
            val isPlayed = episode.resumePoint?.fullyPlayed == true

            // Calculate time display
            val totalMin = episode.durationMs / 60000
            val timeText = when {
                isPlayed -> "Played"
                resumeMs > 10000L -> { // Only show 'left' if they are more than 10 seconds in
                    val leftMin = (episode.durationMs - resumeMs) / 60000
                    "$leftMin min left"
                }
                else -> "$totalMin min"
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = episode.releaseDate ?: "", style = MaterialTheme.typography.labelSmall, color = SpotifyMediumGray)
                Spacer(Modifier.width(8.dp))
                Text(text = timeText, style = MaterialTheme.typography.labelSmall, color = SpotifyMediumGray)
            }

            // Show a subtle progress bar if they are partially through the episode
            if (resumeMs > 0 && !isPlayed) {
                Spacer(Modifier.height(8.dp))
                val progress = (resumeMs.toFloat() / episode.durationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = SpotifyGreen,
                    trackColor = SpotifyDarkGray,
                )
            }
        }
    }
}