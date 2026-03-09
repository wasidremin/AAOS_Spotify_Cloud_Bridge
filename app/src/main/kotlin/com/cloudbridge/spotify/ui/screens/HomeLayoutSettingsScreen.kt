package com.cloudbridge.spotify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cloudbridge.spotify.ui.HomeSection
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.theme.SpotifyDarkGray
import com.cloudbridge.spotify.ui.theme.SpotifyGreen
import com.cloudbridge.spotify.ui.theme.SpotifyLightGray
import com.cloudbridge.spotify.ui.theme.SpotifyWhite

@Composable
fun HomeLayoutSettingsScreen(
    viewModel: SpotifyViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val homeSectionOrder by viewModel.homeSectionOrder.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
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
                    tint = SpotifyWhite
                )
            }
            Text(
                text = "Home Screen Order",
                style = MaterialTheme.typography.headlineSmall,
                color = SpotifyWhite,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Move sections up or down to choose the order they appear on Home. The greeting card and resume button stay pinned to the top.",
            style = MaterialTheme.typography.bodyMedium,
            color = SpotifyLightGray,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(homeSectionOrder, key = { _, section -> section.storageKey }) { index, section ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SpotifyDarkGray)
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = SpotifyWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Shown when this section has content.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SpotifyLightGray
                            )
                        }

                        IconButton(
                            onClick = { viewModel.moveHomeSectionUp(section) },
                            enabled = index > 0
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move up",
                                tint = if (index > 0) SpotifyGreen else SpotifyLightGray
                            )
                        }
                        IconButton(
                            onClick = { viewModel.moveHomeSectionDown(section) },
                            enabled = index < homeSectionOrder.lastIndex
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move down",
                                tint = if (index < homeSectionOrder.lastIndex) SpotifyGreen else SpotifyLightGray
                            )
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = viewModel::resetHomeSectionOrder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyGreen)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset to Default")
                }
            }

            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}
