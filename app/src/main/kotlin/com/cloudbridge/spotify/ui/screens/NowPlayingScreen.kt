package com.cloudbridge.spotify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.components.ExplicitBadge
import com.cloudbridge.spotify.ui.components.ExplicitBadgeSize
import com.cloudbridge.spotify.ui.components.PlayerControls
import com.cloudbridge.spotify.ui.theme.*
import androidx.compose.foundation.layout.aspectRatio

/**
 * Full-screen Now Playing overlay.
 *
 * Slides up from the bottom with an [AnimatedVisibility] transition.
 *
 * Visual layers (back to front):
 * 1. **Blurred album art** — fills the entire screen via 25 dp Gaussian blur.
 * 2. **60 % black scrim** — ensures text readability.
 * 3. **Split-screen [Row]** layout optimal for automotive landscape displays:
 *    - **Left column**: collapse chevron, track title, artist, full
 *      [PlayerControls] (progress slider + transport buttons + radio/heart).
 *    - **Right column**: crisp album art at 1:1 aspect ratio with 16 dp
 *      rounded corners.
 *
 * @param viewModel The shared [SpotifyViewModel] instance.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(viewModel: SpotifyViewModel) {
    val playback by viewModel.playbackState.collectAsState()
    val isTrackSaved by viewModel.isTrackSaved.collectAsState()
    val isSystemNightMode = isSystemInDarkTheme()

    val track = playback?.item
    val artUrl = viewModel.bestArtwork(track?.images ?: track?.album?.images ?: track?.show?.images ?: track?.audiobook?.images)
    val isEpisode = track?.type == "episode"
    val isChapter = track?.type == "chapter"
    val subtitle = when {
        isEpisode -> track?.show?.publisher ?: "Podcast"
        isChapter -> track?.audiobook?.authors?.joinToString(", ") { it.name }
            ?: track?.audiobook?.publisher
            ?: "Audiobook"
        else -> track?.artists?.joinToString(", ") { it.name } ?: ""
    }
    val collectionName = when {
        isEpisode -> track?.show?.name
        isChapter -> track?.audiobook?.name
        else -> track?.album?.name
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        // Layer 1: Blurred background image
        if (artUrl != null) {
            AsyncImage(
                model = artUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(25.dp)
            )
        }

        // Layer 2: Dark scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isSystemNightMode) 0.72f else 0.48f))
        )

        // Layer 3: Split-screen content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 32.dp, end = 160.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Left side: text + controls ──────────────────────────
            Column(
                modifier = Modifier.weight(1.2f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                // Collapse chevron
                IconButton(onClick = { viewModel.closeNowPlaying() }, modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape)) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Close Now Playing",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Track title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track?.name ?: "Not Playing",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp, lineHeight = 56.sp),
                        color = SpotifyWhite,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).basicMarquee()
                    )
                    if (track?.explicit == true && !isEpisode && !isChapter) {
                        Spacer(Modifier.width(12.dp))
                        ExplicitBadge(size = ExplicitBadgeSize.Large)
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Artist / Publisher (Clickable)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                    color = SpotifyLightGray,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = track != null && !isChapter) {
                            if (isEpisode) {
                                track?.show?.let { show ->
                                    viewModel.navigateTo(SpotifyViewModel.Screen.PodcastDetail(show.id, show.name, show.uri))
                                    viewModel.closeNowPlaying()
                                }
                            } else if (!isChapter) {
                                track?.artists?.firstOrNull()?.id?.let { artistId ->
                                    viewModel.navigateTo(SpotifyViewModel.Screen.ArtistDetail(artistId, track.artists.first().name, null))
                                    viewModel.closeNowPlaying()
                                }
                            }
                        }
                        .padding(vertical = 8.dp) // Invisible padding expands touch target
                        .basicMarquee()
                )

                // Album / Podcast Show Name (Clickable)
                if (!collectionName.isNullOrBlank()) {
                    Text(
                        text = collectionName,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        color = SpotifyMediumGray,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = track != null && !isChapter) {
                                if (isEpisode) {
                                    track?.show?.let { show ->
                                        viewModel.navigateTo(SpotifyViewModel.Screen.PodcastDetail(show.id, show.name, show.uri))
                                        viewModel.closeNowPlaying()
                                    }
                                } else if (!isChapter) {
                                    track?.album?.let { album ->
                                        album.id?.let { albumId ->
                                            viewModel.navigateTo(SpotifyViewModel.Screen.AlbumDetail(albumId, album.name, album.uri))
                                            viewModel.closeNowPlaying()
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 8.dp) // Invisible padding expands touch target
                            .basicMarquee()
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Player controls (slider + buttons)
                PlayerControls(
                    isPlaying = playback?.isPlaying ?: false,
                    shuffleEnabled = playback?.shuffleState ?: false,
                    repeatMode = playback?.repeatState ?: "off",
                    isTrackSaved = isTrackSaved,
                    progressMs = playback?.progressMs ?: 0L,
                    durationMs = track?.durationMs ?: 0L,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.skipNext() },
                    onPrevious = { viewModel.skipPrevious() },
                    onShuffle = { viewModel.toggleShuffle() },
                    onRepeat = { viewModel.toggleRepeat() },
                    onHeart = { viewModel.toggleSaveTrack() },
                    onRadio = { viewModel.startRadioFromCurrentTrack() },
                    onSeek = { viewModel.seekTo(it) },
                    showSkipButtons = isEpisode || isChapter,
                    onSkipBack15 = { viewModel.skipBack15Seconds() },
                    onSkipForward15 = { viewModel.skipForward15Seconds() }
                )

                Spacer(Modifier.height(16.dp))

                // Massive Queue Button
                Button(
                    onClick = { viewModel.closeNowPlaying(); viewModel.navigateTopLevel(SpotifyViewModel.Screen.Queue) },
                    modifier = Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyCardSurface, contentColor = SpotifyWhite),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(36.dp), tint = SpotifyGreen)
                    Spacer(Modifier.width(16.dp))
                    Text("Up Next", style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(Modifier.width(32.dp))

            // ── Right side: album art ────────────────────────────────
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                if (artUrl != null) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = track?.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(32.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(48.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .padding(32.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(48.dp))
                            .background(SpotifyCardSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("♪", style = MaterialTheme.typography.displayLarge, color = SpotifyMediumGray)
                    }
                }
            }
        }
    }
}
