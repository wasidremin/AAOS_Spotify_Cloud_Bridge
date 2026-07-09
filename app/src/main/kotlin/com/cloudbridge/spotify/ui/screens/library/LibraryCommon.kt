package com.cloudbridge.spotify.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cloudbridge.spotify.ui.components.ContextMenuAction
import com.cloudbridge.spotify.ui.theme.*

@Composable
internal fun LibraryToolbar(
    filterValue: String,
    onFilterValueChange: (String) -> Unit,
    filterPlaceholder: String,
    sortLabel: String,
    sortOptions: List<String>,
    onSortSelected: (String) -> Unit,
    isGridView: Boolean? = null,
    onToggleView: (() -> Unit)? = null
) {
    var sortExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = filterValue,
            onValueChange = onFilterValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(filterPlaceholder) },
            shape = RoundedCornerShape(16.dp)
        )

        Box {
            OutlinedButton(
                onClick = { sortExpanded = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(min = 170.dp)
            ) {
                Text("Sort: $sortLabel", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false }
            ) {
                sortOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSortSelected(option)
                            sortExpanded = false
                        }
                    )
                }
            }
        }

        if (isGridView != null && onToggleView != null) {
            ViewModeToggle(isGridView = isGridView, onToggle = onToggleView)
        }
    }
}

// ── Reusable Row ─────────────────────────────────────────────────────

@Composable
internal fun ViewModeToggle(isGridView: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
            contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
            tint = SpotifyWhite,
            modifier = Modifier.size(28.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryRow(
    imageUrl: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    contextActions: List<ContextMenuAction> = emptyList(),
    isPinned: Boolean = false
) {
    var menuExpanded by remember(title, subtitle, contextActions.size) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    when {
                        contextActions.isNotEmpty() -> menuExpanded = true
                        onLongClick != null -> onLongClick()
                    }
                }
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = SpotifyWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyLightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isPinned) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = "Pinned",
                tint = SpotifyGreen,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(24.dp)
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
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

// ── Empty State ──────────────────────────────────────────────────────

@Composable
internal fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = SpotifyLightGray
        )
    }
}
