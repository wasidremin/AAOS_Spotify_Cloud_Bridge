package com.cloudbridge.spotify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ExplicitBadge(modifier: Modifier = Modifier) {
    Text(
        text = "E",
        style = MaterialTheme.typography.labelSmall,
        color = Color.Black,
        modifier = modifier
            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}