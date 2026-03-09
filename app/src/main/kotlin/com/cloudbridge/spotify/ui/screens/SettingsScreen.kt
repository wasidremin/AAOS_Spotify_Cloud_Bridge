package com.cloudbridge.spotify.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cloudbridge.spotify.auth.SetupActivity
import com.cloudbridge.spotify.network.model.SpotifyDevice
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.theme.*

/**
 * Settings screen providing:
 * - **Device lock**: select a Spotify Connect device to lock playback to,
 *   or leave on "Automatic" (default behaviour).
 * - **Re-authenticate**: launches [SetupActivity] to refresh credentials.
 *
 * Device list is loaded via [SpotifyViewModel.loadDevices] when the screen
 * enters composition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SpotifyViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val devices by viewModel.deviceList.collectAsState()
    val lockedId by viewModel.lockedDeviceId.collectAsState()
    val lockedName by viewModel.lockedDeviceName.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val rightPadding by viewModel.rightPadding.collectAsState()
    val playInstantly by viewModel.playInstantly.collectAsState()
    val profiles by viewModel.userProfiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val context = LocalContext.current

    // Refresh devices on entry
    LaunchedEffect(Unit) { viewModel.loadDevices() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        // ── Top bar ──────────────────────────────────────────────────
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
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = SpotifyWhite,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Section: Playback Device ─────────────────────────────
            item {
                SectionHeader("Profiles")
            }

            items(profiles, key = { it.id }) { profile ->
                ProfileRow(
                    name = profile.name,
                    subtitle = if (profile.id == activeProfileId) "Active profile" else "Tap to switch",
                    isSelected = profile.id == activeProfileId,
                    onClick = { viewModel.switchActiveProfile(profile.id) }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpotifyDarkGray)
                        .clickable { viewModel.navigateTo(SpotifyViewModel.Screen.AddProfile) }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Add profile with QR code",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SpotifyWhite,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.Add, contentDescription = null, tint = SpotifyGreen)
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Playback Device")
            }

            item {
                Text(
                    text = if (lockedId != null)
                        "Locked to: $lockedName"
                    else
                        "Automatic (discovers device each time)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotifyLightGray,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // "Automatic" option
            item {
                DeviceRow(
                    name = "Automatic",
                    type = "Let the app discover the best device",
                    isSelected = lockedId == null,
                    onClick = { viewModel.unlockDevice() }
                )
            }

            // Real devices
            items(devices, key = { it.id ?: it.name }) { device ->
                DeviceRow(
                    name = device.name,
                    type = buildString {
                        append(device.type)
                        if (device.isActive) append(" • Active")
                    },
                    isSelected = lockedId == device.id,
                    onClick = {
                        device.id?.let { id -> viewModel.lockDevice(id, device.name) }
                    }
                )
            }

            // Refresh button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.loadDevices() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh devices", color = SpotifyGreen)
                    }
                }
            }

            // ── Section: Layout Customization ────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Layout & Display")
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpotifyDarkGray)
                        .clickable { viewModel.navigateTo(SpotifyViewModel.Screen.HomeLayoutSettings) }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Home screen order",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SpotifyWhite
                        )
                        Text(
                            "Choose the order of sections like Jump Back In and Podcasts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyLightGray
                        )
                    }
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = SpotifyGreen)
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text("Artwork Grid Columns: $gridColumns", color = SpotifyWhite)
                    Slider(
                        value = gridColumns.toFloat(),
                        onValueChange = { viewModel.updateGridColumns(it.toInt()) },
                        valueRange = 2f..7f,
                        steps = 4, // 2, 3, 4, 5, 6, 7
                        colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen)
                    )
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text("Right Edge Keep-Out Zone (Bezel Margin): ${rightPadding}dp", color = SpotifyWhite)
                    Slider(
                        value = rightPadding.toFloat(),
                        onValueChange = { viewModel.updateRightPadding(it.toInt()) },
                        valueRange = 0f..400f,
                        colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Play Playlists Instantly", color = SpotifyWhite, style = MaterialTheme.typography.titleMedium)
                        Text("Tapping a playlist plays it immediately instead of opening the track list.", color = SpotifyLightGray, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = playInstantly,
                        onCheckedChange = { viewModel.updatePlayInstantly(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = SpotifyWhite, checkedTrackColor = SpotifyGreen)
                    )
                }
            }

            // ── Section: Account ─────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Account")
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpotifyDarkGray)
                        .clickable {
                            context.startActivity(
                                Intent(context, SetupActivity::class.java)
                            )
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Re-authenticate with Spotify",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SpotifyWhite,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = SpotifyLightGray
                    )
                }
            }

            // Bottom spacer for padding
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = SpotifyWhite,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun DeviceRow(
    name: String,
    type: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) SpotifyGreen.copy(alpha = 0.15f) else SpotifyDarkGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = null,
            tint = if (isSelected) SpotifyGreen else SpotifyLightGray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = SpotifyWhite,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = type,
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyLightGray
            )
        }
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = SpotifyGreen,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ProfileRow(
    name: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) SpotifyGreen.copy(alpha = 0.15f) else SpotifyDarkGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = if (isSelected) SpotifyGreen else SpotifyLightGray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = SpotifyWhite,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyLightGray
            )
        }
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = SpotifyGreen,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
