package com.cloudbridge.spotify.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cloudbridge.spotify.network.model.*
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.components.AlbumArtTile
import com.cloudbridge.spotify.ui.components.ContextMenuAction
import com.cloudbridge.spotify.ui.theme.*

// ── Artists ───────────────────────────────────────────────────────────

@Composable
internal fun ArtistList(
    artists: List<SpotifyArtist>,
    viewModel: SpotifyViewModel,
    contentPadding: PaddingValues,
    gridColumns: Int,
    filterText: String,
    onFilterTextChange: (String) -> Unit,
    sortOption: ArtistSortOption,
    onSortOptionChange: (ArtistSortOption) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val query = filterText.trim().lowercase()
    val filteredArtists = remember(artists, query, sortOption) {
        artists
            .filter { artist ->
                query.isBlank() ||
                    artist.name.contains(query, ignoreCase = true) ||
                    artist.genres.orEmpty().any { it.contains(query, ignoreCase = true) }
            }
            .let { matches ->
                when (sortOption) {
                    ArtistSortOption.RecentlyAdded -> matches
                    ArtistSortOption.Alphabetical -> matches.sortedBy { it.name.lowercase() }
                    ArtistSortOption.Genre -> matches.sortedWith(
                        compareBy<SpotifyArtist>(
                            { it.genres?.firstOrNull()?.lowercase() ?: "" },
                            { it.name.lowercase() }
                        )
                    )
                }
            }
    }

    if (filteredArtists.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryToolbar(
                filterValue = filterText,
                onFilterValueChange = onFilterTextChange,
                filterPlaceholder = "Filter artists",
                sortLabel = sortOption.label,
                sortOptions = ArtistSortOption.entries.map { it.label },
                onSortSelected = { label -> onSortOptionChange(ArtistSortOption.entries.first { it.label == label }) }
            )
            EmptyState(if (artists.isEmpty()) "No artists found" else "No matching artists")
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryToolbar(
            filterValue = filterText,
            onFilterValueChange = onFilterTextChange,
            filterPlaceholder = "Filter artists",
            sortLabel = sortOption.label,
            sortOptions = ArtistSortOption.entries.map { it.label },
            onSortSelected = { label -> onSortOptionChange(ArtistSortOption.entries.first { it.label == label }) }
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            contentPadding = PaddingValues(
                start = 16.dp + contentPadding.calculateStartPadding(layoutDirection),
                top = 4.dp,
                end = 16.dp + contentPadding.calculateEndPadding(layoutDirection),
                bottom = 100.dp + contentPadding.calculateBottomPadding()
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredArtists, key = { it.id ?: it.name }) { artist ->
                ArtistTile(artist, viewModel)
            }
        }
    }
}

@Composable
internal fun ArtistTile(artist: SpotifyArtist, viewModel: SpotifyViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                viewModel.navigateTo(
                    SpotifyViewModel.Screen.ArtistDetail(
                        id = artist.id ?: return@clickable,
                        name = artist.name,
                        imageUrl = viewModel.bestArtwork(artist.images)
                    )
                )
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular artist image
        AsyncImage(
            model = viewModel.bestArtwork(artist.images),
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            color = SpotifyWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        artist.genres?.firstOrNull()?.let { genre ->
            Text(
                text = genre,
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyLightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

