package com.cloudbridge.spotify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ExplicitBadgeSize(
    val horizontalPadding: Int,
    val verticalPadding: Int,
    val cornerRadius: Int,
    val fontSize: TextUnit
) {
    Small(horizontalPadding = 6, verticalPadding = 2, cornerRadius = 6, fontSize = 11.sp),
    Large(horizontalPadding = 10, verticalPadding = 4, cornerRadius = 8, fontSize = 18.sp)
}

@Composable
fun ExplicitBadge(
    modifier: Modifier = Modifier,
    size: ExplicitBadgeSize = ExplicitBadgeSize.Small
) {
    Text(
        text = "E",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = size.fontSize),
        color = Color.Black,
        modifier = modifier
            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(size.cornerRadius.dp))
            .padding(horizontal = size.horizontalPadding.dp, vertical = size.verticalPadding.dp)
    )
}