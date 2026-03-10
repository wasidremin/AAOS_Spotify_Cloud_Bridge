package com.cloudbridge.spotify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cloudbridge.spotify.ui.theme.SpotifyBlack
import com.cloudbridge.spotify.ui.theme.SpotifyCardSurface
import com.cloudbridge.spotify.ui.theme.SpotifyGreen
import com.cloudbridge.spotify.ui.theme.SpotifyLightGray
import com.cloudbridge.spotify.ui.theme.SpotifyWhite

data class ContextMenuAction(
    val label: String,
    val onClick: () -> Unit
)

/**
 * A square album-art tile used in grid layouts throughout the app.
 *
 * Visual design (CarPlay-inspired):
 * - Fills the available width with a 1:1 aspect ratio.
 * - Renders the cover image full-bleed.
 * - Overlays a bottom-to-top dark gradient covering ~60 % of the height
 *   so that white title/subtitle text is always readable.
 * - 12 dp rounded corners for a polished card appearance.
 *
 * Used in: [HomeScreen], [LibraryScreen], [QueueScreen], [SearchScreen].
 *
 * @param imageUrl   URL of the album/playlist/show cover art (loaded via Coil).
 * @param title      Primary label (track, playlist, or album name).
 * @param subtitle   Secondary label (artist, track count, or "Podcast").
 * @param onClick    Callback invoked when the tile is tapped.
 * @param modifier   Optional [Modifier] for the root container.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumArtTile(
    imageUrl: String?,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    contextActions: List<ContextMenuAction> = emptyList(),
    badgeText: String? = null,
    isPinned: Boolean = false,
    artworkContent: (@Composable BoxScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember(title, subtitle, contextActions.size) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(min = 100.dp, max = 320.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(SpotifyCardSurface)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    when {
                        contextActions.isNotEmpty() -> menuExpanded = true
                        onLongClick != null -> onLongClick()
                    }
                }
            )
    ) {
        if (artworkContent != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                content = artworkContent
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isPinned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    tint = SpotifyGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (!badgeText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(SpotifyGreen)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelLarge,
                    color = SpotifyBlack,
                    maxLines = 1
                )
            }
        }

        // Dark gradient overlay at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.60f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        // Title + subtitle text
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = SpotifyWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyLightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            contextActions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        menuExpanded = false
                        action.onClick()
                    }
                )
            }
        }
    }
}
