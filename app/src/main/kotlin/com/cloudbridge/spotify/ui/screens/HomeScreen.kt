package com.cloudbridge.spotify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.cloudbridge.spotify.network.model.SpotifyAlbum
import com.cloudbridge.spotify.network.model.SpotifyPlaylist
import com.cloudbridge.spotify.network.model.SpotifyShow
import com.cloudbridge.spotify.ui.PodcastUpdateInfo
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.components.AlbumArtTile
import com.cloudbridge.spotify.ui.theme.SpotifyGreen
import com.cloudbridge.spotify.ui.theme.SpotifyLightGray
import com.cloudbridge.spotify.ui.theme.SpotifyWhite
import java.time.LocalTime

/**
 * Home screen — the default landing page after app launch.
 *
 * Displays a full-screen scrollable grid (4 columns) of album-art tiles
 * grouped into sections:
 *
 * | Section            | Data Source                                   |
 * |--------------------|-----------------------------------------------|
 * | Jump Back In       | `GET /v1/me/player/recently-played` (hydrated) |
 * | Suggested For You  | Top-artist-seeded playlist search              |
 * | New Releases       | Latest albums from user’s top 5 artists        |
 * | Your Podcasts      | `GET /v1/me/shows` + new-episode freshness     |
 *
 * Uses [LazyVerticalGrid] with [GridCells.Fixed(4)] for the widescreen
 * automotive display (typically 3–4 columns on a landscape 17” panel).
 *
 * @param viewModel      The shared [SpotifyViewModel] instance.
 * @param contentPadding Scaffold inner padding passed from [MainActivity].
 */
@Composable
fun HomeScreen(
    viewModel: SpotifyViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val recentContexts by viewModel.recentContexts.collectAsState()
    val featuredPlaylists by viewModel.featuredPlaylists.collectAsState()
    val savedShows by viewModel.showsSortedByRecent.collectAsState()
    val pinnedItems by viewModel.pinnedItems.collectAsState()
    val podcastUpdates by viewModel.podcastUpdates.collectAsState()
    val newReleases by viewModel.newReleases.collectAsState()
    val isLoading by viewModel.isHomeLoading.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val playInstantly by viewModel.playInstantly.collectAsState()
    val explicitFilterEnabled by viewModel.explicitFilterEnabled.collectAsState()
    val customMixes = viewModel.customMixes
    val layoutDirection = LocalLayoutDirection.current
    val pinnedUris = androidx.compose.runtime.remember(pinnedItems) { pinnedItems.mapTo(mutableSetOf()) { it.uri } }

    if (isLoading && recentContexts.isEmpty() && savedShows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SpotifyGreen)
        }
        return
    }

    if (
        recentContexts.isEmpty() &&
        customMixes.isEmpty() &&
        featuredPlaylists.isEmpty() &&
        savedShows.isEmpty() &&
        newReleases.isEmpty()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No Spotify data available",
                style = MaterialTheme.typography.titleMedium,
                color = SpotifyWhite
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Check account/device setup, then retry.",
                style = MaterialTheme.typography.bodyMedium,
                color = SpotifyLightGray
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { viewModel.loadHomeFeed() }) {
                Text("Retry")
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        contentPadding = PaddingValues(
            start = 24.dp + contentPadding.calculateStartPadding(layoutDirection),
            top = 24.dp + contentPadding.calculateTopPadding(),
            end = 24.dp + contentPadding.calculateEndPadding(layoutDirection),
            bottom = 100.dp + contentPadding.calculateBottomPadding()
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            HomeGreetingCard(
                explicitFilterEnabled = explicitFilterEnabled,
                onExplicitFilterChanged = viewModel::updateExplicitFilterEnabled
            )
        }

        // ── Resume Playback Button ──────────────────────────────────
        if (playback?.isPlaying != true) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Button(
                    onClick = { viewModel.resumePlayback() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .height(64.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = SpotifyGreen,
                        contentColor = com.cloudbridge.spotify.ui.theme.SpotifyBlack
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Resume",
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Resume Playback",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }

        if (recentContexts.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("Jump Back In")
            }
            items(
                items = recentContexts.take(gridColumns),
                key = { "recent-${it.uri}" }
            ) { contextItem ->
                AlbumArtTile(
                    imageUrl = contextItem.imageUrl,
                    title = contextItem.title,
                    subtitle = contextItem.subtitle,
                    isPinned = contextItem.uri in pinnedUris,
                    onClick = {
                        if (contextItem.type == "playlist") {
                            viewModel.navigateTo(
                                SpotifyViewModel.Screen.PlaylistDetail(
                                    contextItem.id,
                                    contextItem.title,
                                    contextItem.uri
                                )
                            )
                        } else if (contextItem.type == "album") {
                            viewModel.navigateTo(
                                SpotifyViewModel.Screen.AlbumDetail(
                                    contextItem.id,
                                    contextItem.title,
                                    contextItem.uri
                                )
                            )
                        }
                    },
                    onLongClick = {
                        if (viewModel.isPinnableType(contextItem.type)) {
                            viewModel.togglePinForRecentContext(contextItem)
                        }
                    }
                )
            }
        }

        // ── Your Podcasts (2nd — sorted by most recent episode) ─────
        if (savedShows.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("Your Podcasts")
            }
            items(items = savedShows, key = { "show-${it.id}" }) { show ->
                PodcastTile(
                    show = show,
                    viewModel = viewModel,
                    updateInfo = podcastUpdates[show.id],
                    isPinned = show.uri in pinnedUris
                )
            }
        }

        if (customMixes.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("Your Custom Mixes")
            }
            items(
                items = customMixes,
                key = { "mix-${it.id}" }
            ) { mix ->
                AlbumArtTile(
                    imageUrl = null,
                    title = mix.title,
                    subtitle = mix.subtitle,
                    onClick = { viewModel.playCustomMix(mix.id) },
                    artworkContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(mix.colorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (mix.id == SpotifyViewModel.CUSTOM_MIX_DAILY_DRIVE) {
                                    Icons.Filled.DirectionsCar
                                } else {
                                    Icons.Filled.MusicNote
                                },
                                contentDescription = null,
                                tint = SpotifyWhite.copy(alpha = 0.95f),
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                )
            }
        }

        if (pinnedItems.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp)
                ) {
                    SectionHeader("Pinned Favorites")
                    TextButton(onClick = { viewModel.navigateTo(SpotifyViewModel.Screen.ManagePins) }) {
                        Text("Manage", color = SpotifyGreen, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            items(items = pinnedItems, key = { "pin-${it.uri}" }) { pin ->
                AlbumArtTile(
                    imageUrl = pin.imageUrl,
                    title = pin.name,
                    subtitle = pin.subtitle,
                    isPinned = true,
                    onClick = { viewModel.openPinnedItem(pin) },
                    onLongClick = { viewModel.togglePin(pin) }
                )
            }
        }

        // ── Suggested For You ───────────────────────────────────────
        if (featuredPlaylists.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("Suggested For You")
            }
            items(
                items = featuredPlaylists.take(12),
                key = { "feat-${it.id}" }
            ) { playlist ->
                PlaylistTile(
                    playlist = playlist,
                    viewModel = viewModel,
                    playInstantly = playInstantly,
                    isPinned = playlist.uri in pinnedUris
                )
            }
        }

        // ── New Releases ─────────────────────────────────────────────
        if (newReleases.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("New Releases")
            }
            items(
                items = newReleases.take(12),
                key = { "new-${it.id}" }
            ) { album ->
                AlbumTile(album = album, viewModel = viewModel, isPinned = (album.uri ?: "") in pinnedUris)
            }
        }

    }
}

@Composable
private fun HomeGreetingCard(
    explicitFilterEnabled: Boolean,
    onExplicitFilterChanged: (Boolean) -> Unit
) {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late-night drive"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(greeting, style = MaterialTheme.typography.headlineMedium, color = SpotifyWhite)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (explicitFilterEnabled) {
                        "Clean Swapper is on and will look for non-explicit replacements."
                    } else {
                        "Turn on Clean Swapper to prefer clean versions when possible."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = SpotifyLightGray
                )
            }

            Spacer(Modifier.width(24.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text("Clean Swapper", style = MaterialTheme.typography.titleMedium, color = SpotifyWhite)
                Spacer(Modifier.height(8.dp))
                Switch(
                    checked = explicitFilterEnabled,
                    onCheckedChange = onExplicitFilterChanged
                )
            }
        }
    }
}

// ── Section Header ───────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = SpotifyWhite,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

// ── Tile Composables ─────────────────────────────────────────────────

@Composable
private fun PlaylistTile(
    playlist: SpotifyPlaylist,
    viewModel: SpotifyViewModel,
    playInstantly: Boolean,
    isPinned: Boolean
) {
    AlbumArtTile(
        imageUrl = viewModel.bestArtwork(playlist.images),
        title = playlist.name,
        subtitle = "${playlist.tracks?.total ?: 0} tracks",
        isPinned = isPinned,
        onClick = {
            if (playInstantly) {
                viewModel.playContext(playlist.uri)
            } else {
                viewModel.navigateTo(
                    SpotifyViewModel.Screen.PlaylistDetail(
                        id = playlist.id,
                        name = playlist.name,
                        uri = playlist.uri
                    )
                )
            }
        },
        onLongClick = { viewModel.togglePinForPlaylist(playlist) }
    )
}

@Composable
private fun AlbumTile(album: SpotifyAlbum, viewModel: SpotifyViewModel, isPinned: Boolean) {
    AlbumArtTile(
        imageUrl = viewModel.bestArtwork(album.images),
        title = album.name,
        subtitle = album.artists?.joinToString(", ") { it.name },
        isPinned = isPinned,
        onClick = {
            viewModel.navigateTo(
                SpotifyViewModel.Screen.AlbumDetail(
                    id = album.id ?: return@AlbumArtTile,
                    name = album.name,
                    uri = album.uri
                )
            )
        },
        onLongClick = { viewModel.togglePinForAlbum(album) }
    )
}

@Composable
private fun PodcastTile(
    show: SpotifyShow,
    viewModel: SpotifyViewModel,
    updateInfo: PodcastUpdateInfo?,
    isPinned: Boolean
) {
    AlbumArtTile(
        imageUrl = viewModel.bestArtwork(show.images),
        title = show.name,
        subtitle = updateInfo?.subtitleSuffix(show.publisher ?: "Podcast") ?: (show.publisher ?: "Podcast"),
        badgeText = updateInfo?.takeIf { it.hasUpdates }?.badgeText,
        isPinned = isPinned,
        onClick = { viewModel.navigateTo(SpotifyViewModel.Screen.PodcastDetail(show.id, show.name, show.uri)) },
        onLongClick = { viewModel.togglePinForShow(show) }
    )
}
