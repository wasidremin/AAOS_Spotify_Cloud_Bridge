package com.cloudbridge.spotify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * A 2×2 grid of album-art images used as the artwork for Custom Mix tiles.
 *
 * Displays up to 4 cover-art images in a seamless grid. If fewer than 4
 * URLs are provided, the remaining quadrants fall back to [fallbackColor].
 *
 * @param imageUrls     Up to 4 image URLs to display.
 * @param fallbackColor Background colour for empty quadrants.
 * @param modifier      Optional [Modifier].
 */
@Composable
fun FourImageGrid(
    imageUrls: List<String>,
    fallbackColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            GridCell(imageUrls.getOrNull(0), fallbackColor, Modifier.weight(1f).fillMaxHeight())
            GridCell(imageUrls.getOrNull(1), fallbackColor, Modifier.weight(1f).fillMaxHeight())
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            GridCell(imageUrls.getOrNull(2), fallbackColor, Modifier.weight(1f).fillMaxHeight())
            GridCell(imageUrls.getOrNull(3), fallbackColor, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun GridCell(
    imageUrl: String?,
    fallbackColor: Color,
    modifier: Modifier = Modifier
) {
    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier.background(fallbackColor))
    }
}
