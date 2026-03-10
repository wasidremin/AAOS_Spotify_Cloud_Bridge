package com.cloudbridge.spotify.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import com.cloudbridge.spotify.network.model.SpotifyChapter
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.theme.*

/**
 * Detail screen for an audiobook that shows its chapters.
 *
 * Header: back arrow + title.
 * Body: scrollable [LazyColumn] of [ChapterRow]s. Each row shows the
 * chapter number, cover art (from the audiobook), title, a snippet of the
 * description, duration, and a resume-progress bar (if partially played).
 *
 * Tapping a chapter fires `viewModel.playTrack(chapter.uri, contextUri)` so
 * Spotify plays that chapter on the phone via the Cloud-Bridge pattern.
 * Long-pressing (or tapping the row itself — same action) adds the chapter
 * to the playback queue via [SpotifyViewModel.addEpisodeToQueue].
 *
 * @param viewModel      The shared [SpotifyViewModel] instance.
 * @param screen         The [SpotifyViewModel.Screen.AudiobookDetail] navigation argument.
 * @param contentPadding Scaffold inner padding.
 */
@Composable
fun AudiobookDetailScreen(
    viewModel: SpotifyViewModel,
    screen: SpotifyViewModel.Screen.AudiobookDetail,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val chapters by viewModel.detailChapters.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val currentPlayingId = playback?.item?.id
    val isLoading by viewModel.isDetailLoading.collectAsState()
    val detailError by viewModel.detailError.collectAsState()
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
        }

        HorizontalDivider(color = SpotifyMediumGray.copy(alpha = 0.3f))

        // ── Chapter List ─────────────────────────────────────────────
        when {
            isLoading && chapters.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SpotifyGreen)
                }
            }
            detailError != null && chapters.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = detailError!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SpotifyLightGray
                    )
                }
            }
            chapters.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No chapters found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SpotifyLightGray
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        top = 4.dp,
                        end = contentPadding.calculateEndPadding(layoutDirection),
                        bottom = 4.dp + contentPadding.calculateBottomPadding()
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(chapters, key = { _, ch -> ch.id }) { _, chapter ->
                        ChapterRow(
                            chapter = chapter,
                            isCurrentlyPlaying = chapter.id == currentPlayingId,
                            onClick = {
                                viewModel.playTrack(
                                    trackUri = chapter.uri,
                                    contextUri = screen.uri
                                )
                            },
                            onLongClick = { viewModel.addEpisodeToQueue(chapter.uri) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    chapter: SpotifyChapter,
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
        // Chapter number / now-playing indicator
        Box(modifier = Modifier.width(56.dp), contentAlignment = Alignment.CenterStart) {
            if (isCurrentlyPlaying) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Now playing",
                    tint = SpotifyGreen,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Text(
                    text = "${chapter.chapterNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    color = SpotifyMediumGray
                )
            }
        }

        // Cover art (from chapter images; falls back gracefully to null)
        val artUrl = chapter.images?.firstOrNull()?.url
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

        // Chapter metadata
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrentlyPlaying) SpotifyGreen else SpotifyWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!chapter.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = chapter.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyLightGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(4.dp))

            val resumeMs = chapter.resumePoint?.resumePositionMs ?: 0L
            val isPlayed = chapter.resumePoint?.fullyPlayed == true
            val totalMin = chapter.durationMs / 60000
            val timeText = when {
                isPlayed -> "Played"
                resumeMs > 10_000L -> "${(chapter.durationMs - resumeMs) / 60000} min left"
                else -> "$totalMin min"
            }
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = SpotifyMediumGray
            )

            // Resume progress bar (only shown if partially played)
            if (resumeMs > 0 && !isPlayed && chapter.durationMs > 0) {
                Spacer(Modifier.height(8.dp))
                val progress = (resumeMs.toFloat() / chapter.durationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = SpotifyGreen,
                    trackColor = SpotifyDarkGray
                )
            }
        }
    }
}
