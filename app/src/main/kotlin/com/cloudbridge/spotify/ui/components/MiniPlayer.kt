package com.cloudbridge.spotify.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cloudbridge.spotify.network.model.CurrentPlaybackResponse
import com.cloudbridge.spotify.ui.theme.*

/**
 * A floating, pill-shaped mini-player anchored to the bottom-right of the screen.
 *
 * Dimensions: 340 dp × 64 dp with fully rounded corners (32 dp radius).
 * Displays the currently playing track’s:
 * - Album art thumbnail (48 × 48 dp)
 * - Track title (single line, ellipsized)
 * - Artist name (single line, ellipsized)
 * - Play/Pause toggle button
 *
 * Tapping the body (outside the button) opens the full [NowPlayingScreen].
 *
 * @param playback    The current [CurrentPlaybackResponse]; renders nothing if `null`.
 * @param onTap       Callback when the mini-player body is tapped (opens Now Playing).
 * @param onPlayPause Callback for the play/pause button.
 * @param modifier    Optional [Modifier] for the root container.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    playback: CurrentPlaybackResponse?,
    onTap: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playback?.item ?: return

    Surface(
        modifier = modifier
            .widthIn(max = 600.dp) // Increased from 480
            .fillMaxWidth(0.55f)   // Increased from 0.45
            .height(112.dp)        // Increased from 96
            .clip(RoundedCornerShape(56.dp))
            .clickable(onClick = onTap),
        color = SpotifyElevatedSurface,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 10.dp)
        ) {
            // Album art (larger for car screen)
            val artUrl = track.images?.firstOrNull()?.url
                ?: track.album?.images?.firstOrNull()?.url
                ?: track.show?.images?.firstOrNull()?.url
                ?: track.audiobook?.images?.firstOrNull()?.url
            AsyncImage(
                model = artUrl,
                contentDescription = track.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp) // Increased from 72
                    .clip(RoundedCornerShape(16.dp))
            )

            Spacer(Modifier.width(16.dp))

            // Track info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleLarge, // Bumped to titleLarge
                    color = SpotifyWhite,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee() // Add this
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (track.type == "episode") {
                        track.show?.publisher ?: "Podcast"
                    } else if (track.type == "chapter") {
                        track.audiobook?.authors?.joinToString(", ") { it.name }
                            ?: track.audiobook?.publisher
                            ?: "Audiobook"
                    } else {
                        track.artists?.joinToString(", ") { it.name } ?: ""
                    },
                    style = MaterialTheme.typography.bodyLarge, // Bumped to bodyLarge
                    color = SpotifyLightGray,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee() // Add this
                )
            }

            // Play/Pause (larger for touch)
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(80.dp) // Increased touch target
            ) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playback.isPlaying) "Pause" else "Play",
                    tint = SpotifyWhite,
                    modifier = Modifier.size(52.dp) // Increased from 40
                )
            }
        }
    }
}
