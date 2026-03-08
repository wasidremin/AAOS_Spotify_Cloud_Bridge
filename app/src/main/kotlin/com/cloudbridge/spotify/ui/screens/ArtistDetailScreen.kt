package com.cloudbridge.spotify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cloudbridge.spotify.network.model.SpotifyAlbum
import com.cloudbridge.spotify.network.model.SpotifyTrack
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.components.AlbumArtTile
import com.cloudbridge.spotify.ui.components.ExplicitBadge
import com.cloudbridge.spotify.ui.theme.*

/**
 * Detail screen for a followed artist.
 *
 * Sections:
 * 1. Header – circular artist image, name, back button
 * 2. Top Tracks – numbered list with play action
 * 3. Albums – horizontal scrollable row of album tiles
 * 4. Liked Songs – tracks from the user's library by this artist
 *
 * Data is loaded by [SpotifyViewModel.loadArtistDetail] when the screen
 * enters composition.
 */
@Composable
fun ArtistDetailScreen(
    viewModel: SpotifyViewModel,
    screen: SpotifyViewModel.Screen.ArtistDetail,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val topTracks by viewModel.artistTopTracks.collectAsState()
    val albums by viewModel.artistAlbums.collectAsState()
    val likedSongs by viewModel.artistLikedSongs.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val currentPlayingId = playback?.item?.id
    val artistProfile by viewModel.currentArtistProfile.collectAsState()
    val displayImageUrl = screen.imageUrl ?: viewModel.bestArtwork(artistProfile?.images)

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateBack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SpotifyWhite,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Circular artist thumbnail
            AsyncImage(
                model = displayImageUrl,
                contentDescription = screen.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = screen.name,
                style = MaterialTheme.typography.headlineMedium,
                color = SpotifyWhite,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(12.dp))

            OutlinedButton(onClick = { viewModel.startRadioFromArtist() }, enabled = topTracks.isNotEmpty()) {
                Text("Start Radio")
            }

            Spacer(Modifier.width(8.dp))

            Button(onClick = { viewModel.playArtistTopTracks() }, enabled = topTracks.isNotEmpty()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Play Top")
            }
        }

        // ── Scrollable body ──────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // ── Top Tracks ───────────────────────────────────────────
            if (topTracks.isNotEmpty()) {
                item {
                    SectionTitle("Top Tracks")
                }

                itemsIndexed(topTracks) { index, track ->
                    ArtistTrackRow(
                        index = index + 1,
                        track = track,
                        isPlaying = track.id == currentPlayingId,
                        onPlay = {
                            track.uri?.let { uri ->
                                viewModel.playTrack(uri, screen.let { "spotify:artist:${it.id}" })
                            }
                        }
                    )
                }
            }

            // ── Albums ───────────────────────────────────────────────
            if (albums.isNotEmpty()) {
                item {
                    SectionTitle("Albums")
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(albums, key = { it.id ?: it.name }) { album ->
                            AlbumArtTile(
                                imageUrl = viewModel.bestArtwork(album.images),
                                title = album.name,
                                subtitle = album.releaseDate?.take(4) ?: "",
                                modifier = Modifier.width(160.dp),
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
                }
            }

            // ── Liked Songs by this artist ───────────────────────────
            if (likedSongs.isNotEmpty()) {
                item {
                    SectionTitle("Your Liked Songs")
                }

                itemsIndexed(likedSongs) { index, track ->
                    ArtistTrackRow(
                        index = index + 1,
                        track = track,
                        isPlaying = track.id == currentPlayingId,
                        onPlay = {
                            track.uri?.let { uri ->
                                viewModel.playTrack(uri)
                            }
                        }
                    )
                }
            }

            // Empty state
            if (topTracks.isEmpty() && albums.isEmpty() && likedSongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SpotifyGreen)
                    }
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = SpotifyWhite,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun ArtistTrackRow(
    index: Int,
    track: SpotifyTrack,
    isPlaying: Boolean,
    onPlay: () -> Unit
) {
    val textColor = if (isPlaying) SpotifyGreen else SpotifyWhite
    val secondaryColor = if (isPlaying) SpotifyGreen.copy(alpha = 0.7f) else SpotifyLightGray

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Filled.VolumeUp,
                    contentDescription = "Now playing",
                    tint = SpotifyGreen,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor
                )
            }
        }

        // Album art
        AsyncImage(
            model = track.album?.images?.firstOrNull()?.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
        )

        Spacer(Modifier.width(12.dp))

        // Title + artist
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.explicit) {
                    Spacer(Modifier.width(8.dp))
                    ExplicitBadge()
                }
            }
            track.artists?.joinToString { it.name }?.let { artistNames ->
                Text(
                    text = artistNames,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Duration
        track.durationMs?.let { ms ->
            val min = ms / 60000
            val sec = (ms % 60000) / 1000
            Text(
                text = "%d:%02d".format(min, sec),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor
            )
        }
    }
}
