package com.cloudbridge.spotify.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cloudbridge.spotify.ui.theme.SpotifyGreen
import com.cloudbridge.spotify.ui.theme.SpotifyLightGray
import com.cloudbridge.spotify.ui.theme.SpotifyMediumGray
import com.cloudbridge.spotify.ui.theme.SpotifyWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Full transport-control widget for the [NowPlayingScreen].
 *
 * Layout (top to bottom):
 * 1. **Progress slider** — thumb + active track in white, inactive in gray.
 *    Supports drag-seeking: while the user drags, `isSeeking` is `true`
 *    and the local `seekPosition` drives the slider instead of [progressMs].
 * 2. **Time labels** — elapsed (left) and total duration (right).
 * 3. **Primary control row** — Shuffle · Previous · Play/Pause · Next · Repeat.
 *    Touch targets are 48–72 dp to meet AAOS accessibility guidelines.
 * 4. **Secondary row** — Radio (seed recommendations) and Heart (save/unsave).
 *
 * @param isPlaying     Whether playback is currently active.
 * @param shuffleEnabled Whether shuffle mode is on.
 * @param repeatMode    `"off"`, `"context"`, or `"track"`.
 * @param isTrackSaved  Whether the current track is in the user’s Liked Songs.
 * @param progressMs    Current playback position in milliseconds.
 * @param durationMs    Total track duration in milliseconds.
 * @param onPlayPause   Toggle play/pause.
 * @param onNext        Skip to next track.
 * @param onPrevious    Skip to previous track.
 * @param onShuffle     Toggle shuffle.
 * @param onRepeat      Cycle repeat mode: off → context → track → off.
 * @param onHeart       Toggle save/unsave current track.
 * @param onRadio       Start a radio station seeded from the current track.
 * @param onSeek        Seek to a specific position (ms) in the current track.
 * @param modifier      Optional [Modifier] for the root container.
 */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: String,
    isTrackSaved: Boolean,
    progressMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onHeart: () -> Unit,
    onRadio: () -> Unit,
    onSeek: (Long) -> Unit,
    showSkipButtons: Boolean = false,
    onSkipBack15: () -> Unit = {},
    onSkipForward15: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    // Local state for smooth interpolation
    var localProgress by remember { mutableLongStateOf(progressMs) }

    // Extrapolated speed tracking
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var lastNetworkProgress by remember { mutableLongStateOf(progressMs) }
    var lastNetworkTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // React to new network polls
    LaunchedEffect(progressMs) {
        val now = System.currentTimeMillis()
        val wallClockDiff = now - lastNetworkTime
        val progressDiff = progressMs - lastNetworkProgress

        // If playing and we have a valid interval (e.g., 2-10 seconds), calculate speed
        if (isPlaying && wallClockDiff in 2000..10000 && progressDiff > 0) {
            val speed = progressDiff.toFloat() / wallClockDiff.toFloat()
            playbackSpeed = speed.coerceIn(0.5f, 3.5f) // Clamp between 0.5x and 3.5x to avoid wild jumps
        }

        lastNetworkProgress = progressMs
        lastNetworkTime = now
        localProgress = progressMs // Snap to true position to correct minor drift
    }

    // Local smooth ticking timer
    LaunchedEffect(progressMs, isPlaying) {
        if (isPlaying) {
            var lastTick = System.currentTimeMillis()
            while (isActive) {
                delay(250) // Tick 4 times a second for smooth sliding
                val now = System.currentTimeMillis()
                val delta = now - lastTick
                localProgress += (delta * playbackSpeed).toLong()
                if (localProgress > durationMs) localProgress = durationMs // Cap at duration
                lastTick = now
            }
        }
    }

    val displayProgress = if (isSeeking) seekPosition else localProgress.toFloat()
    val maxDuration = durationMs.toFloat().coerceAtLeast(1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // ── Main Controls (Shuffle, Prev, Play, Next, Repeat) ───────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            IconButton(
                onClick = onShuffle,
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleEnabled) SpotifyGreen else SpotifyLightGray,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Previous
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(96.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = SpotifyWhite,
                    modifier = Modifier.size(64.dp)
                )
            }

            // Play / Pause (largest — prominent center button)
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(120.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying)
                        Icons.Filled.PauseCircleFilled
                    else
                        Icons.Filled.PlayCircleFilled,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = SpotifyWhite,
                    modifier = Modifier.size(100.dp)
                )
            }

            // Next
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(96.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = SpotifyWhite,
                    modifier = Modifier.size(64.dp)
                )
            }

            // Repeat
            IconButton(
                onClick = onRepeat,
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = when (repeatMode) {
                        "track" -> Icons.Filled.RepeatOne
                        else -> Icons.Filled.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (repeatMode != "off") SpotifyGreen else SpotifyLightGray,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // ── Secondary Controls (Radio, Heart) ───────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showSkipButtons) {
                OutlinedButton(onClick = onSkipBack15) {
                    Text("−15s", color = SpotifyWhite)
                }
                OutlinedButton(onClick = onSkipForward15) {
                    Text("+15s", color = SpotifyWhite)
                }
            }

            IconButton(
                onClick = onRadio,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Radio,
                    contentDescription = "Start Radio",
                    tint = SpotifyLightGray,
                    modifier = Modifier.size(48.dp)
                )
            }

            IconButton(
                onClick = onHeart,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (isTrackSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isTrackSaved) "Remove from Liked Songs" else "Add to Liked Songs",
                    tint = if (isTrackSaved) SpotifyGreen else SpotifyLightGray,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Progress Slider ──────────────────────────────────────────
        Slider(
            value = displayProgress,
            onValueChange = { value ->
                isSeeking = true
                seekPosition = value
            },
            onValueChangeFinished = {
                isSeeking = false
                onSeek(seekPosition.toLong())
            },
            valueRange = 0f..maxDuration,
            colors = SliderDefaults.colors(
                thumbColor = SpotifyWhite,
                activeTrackColor = SpotifyWhite,
                inactiveTrackColor = SpotifyMediumGray
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        // ── Time Labels ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(displayProgress.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyLightGray
            )
            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyLightGray
            )
        }
    }
}

/** Format milliseconds to "m:ss" */
private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
