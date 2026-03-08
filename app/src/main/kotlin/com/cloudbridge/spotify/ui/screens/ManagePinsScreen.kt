package com.cloudbridge.spotify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.theme.ErrorRed
import com.cloudbridge.spotify.ui.theme.SpotifyCardSurface
import com.cloudbridge.spotify.ui.theme.SpotifyLightGray
import com.cloudbridge.spotify.ui.theme.SpotifyMediumGray
import com.cloudbridge.spotify.ui.theme.SpotifyWhite

@Composable
fun ManagePinsScreen(
    viewModel: SpotifyViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val pinnedItems by viewModel.pinnedItems.collectAsState()
    val layoutDirection = LocalLayoutDirection.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp + contentPadding.calculateStartPadding(layoutDirection),
                    top = 16.dp + contentPadding.calculateTopPadding(),
                    end = 16.dp + contentPadding.calculateEndPadding(layoutDirection),
                    bottom = 16.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateBack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SpotifyWhite,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Manage Pinned Favorites",
                style = MaterialTheme.typography.headlineMedium,
                color = SpotifyWhite,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(color = SpotifyMediumGray.copy(alpha = 0.3f))

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp + contentPadding.calculateStartPadding(layoutDirection),
                top = 16.dp,
                end = 16.dp + contentPadding.calculateEndPadding(layoutDirection),
                bottom = 100.dp + contentPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(pinnedItems, key = { _, item -> item.uri }) { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpotifyCardSurface)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = SpotifyWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!item.subtitle.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = SpotifyLightGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { viewModel.movePinUp(item.uri) },
                            enabled = index > 0,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.Filled.ArrowUpward,
                                contentDescription = "Up",
                                tint = if (index > 0) SpotifyWhite else SpotifyMediumGray,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.movePinDown(item.uri) },
                            enabled = index < pinnedItems.size - 1,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.Filled.ArrowDownward,
                                contentDescription = "Down",
                                tint = if (index < pinnedItems.size - 1) SpotifyWhite else SpotifyMediumGray,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.removePin(item.uri) },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove",
                                tint = ErrorRed,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}