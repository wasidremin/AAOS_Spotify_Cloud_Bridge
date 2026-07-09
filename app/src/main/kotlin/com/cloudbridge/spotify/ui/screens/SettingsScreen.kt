package com.cloudbridge.spotify.ui.screens

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cloudbridge.spotify.auth.SetupActivity
import com.cloudbridge.spotify.network.model.SpotifyDevice
import com.cloudbridge.spotify.player.PlaybackTarget
import com.cloudbridge.spotify.ui.SpotifyViewModel
import com.cloudbridge.spotify.ui.theme.*

/**
 * Settings screen providing:
 * - **Playback target**: Auto / Phone / Car player, or lock to a specific device.
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
    val playbackTarget by viewModel.playbackTarget.collectAsState()
    val btAutoLaunchMac by viewModel.btAutoLaunchMac.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val rightPadding by viewModel.rightPadding.collectAsState()
    val playInstantly by viewModel.playInstantly.collectAsState()
    val profiles by viewModel.userProfiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val activeProfile = remember(profiles, activeProfileId) {
        profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
    }
    val context = LocalContext.current
    var pendingExportFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { destinationUri: Uri? ->
        val sourcePath = pendingExportFilePath
        pendingExportFilePath = null

        if (destinationUri == null || sourcePath.isNullOrBlank()) {
            return@rememberLauncherForActivityResult
        }

        viewModel.writeExportedLogsToUri(java.io.File(sourcePath), destinationUri) { success ->
            Toast.makeText(
                context,
                if (success) "Logs exported" else "Failed to export logs",
                Toast.LENGTH_LONG
            ).show()
        }
    }

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
                        .clickable { viewModel.navigateTo(SpotifyViewModel.Screen.AddProfile()) }
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
                val summary = when (val target = playbackTarget) {
                    is PlaybackTarget.Auto -> "Auto — phone first, then any active device"
                    is PlaybackTarget.Phone -> "Prefer phone Spotify"
                    is PlaybackTarget.CarPlayer -> "Prefer car / non-phone player"
                    is PlaybackTarget.Specific -> "Locked to: ${target.deviceName}"
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotifyLightGray,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                DeviceRow(
                    name = "Automatic",
                    type = "Phone first, then any active unrestricted device",
                    isSelected = playbackTarget is PlaybackTarget.Auto && lockedId == null,
                    onClick = { viewModel.setPlaybackTargetPreference(PlaybackTarget.Auto) }
                )
            }
            item {
                DeviceRow(
                    name = "Phone",
                    type = "Always target a smartphone Connect device",
                    isSelected = playbackTarget is PlaybackTarget.Phone,
                    onClick = { viewModel.setPlaybackTargetPreference(PlaybackTarget.Phone) }
                )
            }
            item {
                DeviceRow(
                    name = "Car player",
                    type = "Prefer the car's built-in Spotify / non-phone device",
                    isSelected = playbackTarget is PlaybackTarget.CarPlayer,
                    onClick = { viewModel.setPlaybackTargetPreference(PlaybackTarget.CarPlayer) }
                )
            }

            // Specific devices (legacy lock / pin)
            items(devices, key = { it.id ?: it.name }) { device ->
                DeviceRow(
                    name = device.name,
                    type = buildString {
                        append(device.type)
                        if (device.isActive) append(" • Active")
                        if (device.isRestricted) append(" • Restricted")
                    },
                    isSelected = lockedId == device.id ||
                        (playbackTarget is PlaybackTarget.Specific &&
                            (playbackTarget as PlaybackTarget.Specific).deviceId == device.id),
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

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpotifyDarkGray)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Bluetooth Auto-Launch",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SpotifyWhite
                        )
                        Text(
                            btAutoLaunchMac?.let { "Enabled for $it" }
                                ?: "Manual mode keeps Cloud-Bridge from foregrounding itself during Bluetooth reconnects.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyLightGray
                        )
                    }
                    TextButton(
                        enabled = !btAutoLaunchMac.isNullOrBlank(),
                        onClick = { viewModel.disableBluetoothAutoLaunch() }
                    ) {
                        Text(
                            text = if (btAutoLaunchMac.isNullOrBlank()) "Manual" else "Disable",
                            color = if (btAutoLaunchMac.isNullOrBlank()) SpotifyLightGray else SpotifyGreen
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Advanced: optional phone wake")
            }

            item {
                val savedUrl by viewModel.companionWakeUrl.collectAsState()
                var draftUrl by remember(savedUrl) { mutableStateOf(savedUrl.orEmpty()) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpotifyDarkGray)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        "Most users leave this empty. Cloud-Bridge wakes the phone via Spotify Connect " +
                            "and Bluetooth AVRCP when the phone is paired to the car.\n\n" +
                            "Optional: POST URL for home automation (e.g. Home Assistant webhook) that " +
                            "opens Spotify on the phone. Not required and not used by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpotifyLightGray
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = draftUrl,
                        onValueChange = { draftUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Wake webhook URL (optional)") },
                        placeholder = {
                            Text("http://homeassistant.local:8123/api/webhook/…")
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            draftUrl = ""
                            viewModel.saveCompanionWakeUrl(null)
                        }) {
                            Text("Clear", color = SpotifyLightGray)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { viewModel.saveCompanionWakeUrl(draftUrl) }) {
                            Text("Save", color = SpotifyGreen)
                        }
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
                        .clickable(enabled = activeProfile != null) {
                            activeProfile?.id?.let { profileId ->
                                viewModel.navigateTo(SpotifyViewModel.Screen.AddProfile(refreshProfileId = profileId))
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Refresh Permissions",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SpotifyWhite
                        )
                        Text(
                            activeProfile?.let { "Re-consent ${it.name} and keep the same saved profile." }
                                ?: "Select or add a Spotify profile first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyLightGray
                        )
                    }
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = if (activeProfile != null) SpotifyGreen else SpotifyLightGray
                    )
                }
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

            // ── Section: Logging ──────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Logging")
            }

            item {
                val loggingEnabled by viewModel.loggingEnabled.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Logging", color = SpotifyWhite, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Writes API calls, auth events, playback commands, and errors to an internal log file.",
                            color = SpotifyLightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = loggingEnabled,
                        onCheckedChange = { viewModel.updateLoggingEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = SpotifyWhite, checkedTrackColor = SpotifyGreen)
                    )
                }
            }

            item {
                val loggingEnabled by viewModel.loggingEnabled.collectAsState()
                val logSizeText = remember(loggingEnabled) {
                    val bytes = com.cloudbridge.spotify.util.AppLogger.totalLogSizeBytes()
                    when {
                        bytes < 1024 -> "$bytes B"
                        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpotifyDarkGray)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Log Size", style = MaterialTheme.typography.bodyLarge, color = SpotifyWhite)
                        Text(logSizeText, style = MaterialTheme.typography.bodySmall, color = SpotifyLightGray)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            viewModel.exportLogs { file ->
                                if (file != null) {
                                    val createDocumentIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = "text/plain"
                                    }
                                    val hasDocumentTarget = createDocumentIntent.resolveActivity(context.packageManager) != null

                                    if (hasDocumentTarget) {
                                        pendingExportFilePath = file.absolutePath
                                        createDocumentLauncher.launch(file.name)
                                    } else {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            clipData = ClipData.newRawUri("cloudbridge_logs", uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Export Logs"))
                                    }
                                } else {
                                    Toast.makeText(context, "No logs available to export", Toast.LENGTH_LONG).show()
                                }
                            }
                        }) {
                            Text("Export", color = SpotifyGreen)
                        }
                        TextButton(onClick = { viewModel.clearLogs() }) {
                            Text("Clear", color = SpotifyLightGray)
                        }
                    }
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Manual credential editor",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SpotifyWhite
                        )
                        Text(
                            "Fallback for entering client IDs or refresh tokens by hand.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyLightGray
                        )
                    }
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
