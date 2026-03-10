package com.cloudbridge.spotify.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.cloudbridge.spotify.ui.AddProfileViewModel
import com.cloudbridge.spotify.ui.theme.SpotifyCardSurface
import com.cloudbridge.spotify.ui.theme.SpotifyGreen
import com.cloudbridge.spotify.ui.theme.SpotifyLightGray
import com.cloudbridge.spotify.ui.theme.SpotifyWhite
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

@Composable
fun AddProfileScreen(
    viewModel: AddProfileViewModel,
    refreshProfileId: String? = null,
    onBack: () -> Unit,
    onCompleted: () -> Unit
) {
    val sessionCode by viewModel.sessionCode.collectAsState()
    val qrCodeUrl by viewModel.qrCodeUrl.collectAsState()
    val isWaitingForProfile by viewModel.isWaitingForProfile.collectAsState()
    val isCompleting by viewModel.isCompleting.collectAsState()
    val isCompleted by viewModel.isCompleted.collectAsState()
    val isRefreshSession by viewModel.isRefreshSession.collectAsState()
    val refreshTargetName by viewModel.refreshTargetName.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isRefreshRequest = refreshProfileId != null || isRefreshSession

    LaunchedEffect(refreshProfileId) {
        viewModel.startNewSession(refreshProfileId)
    }

    LaunchedEffect(isCompleted) {
        if (isCompleted) {
            onCompleted()
        }
    }

    val qrBitmap = remember(qrCodeUrl) {
        if (qrCodeUrl.isBlank()) null else generateQrBitmap(qrCodeUrl)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SpotifyWhite,
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(
                text = if (isRefreshRequest) "Refresh Permissions" else "Add Profile",
                style = MaterialTheme.typography.headlineMedium,
                color = SpotifyWhite
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = SpotifyCardSurface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (isRefreshRequest) {
                            "1. Scan this QR code with your phone to refresh ${refreshTargetName ?: "this profile"}."
                        } else {
                            "1. Scan this QR code with your phone."
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = SpotifyWhite
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        if (isRefreshRequest) {
                            "2. Approve Spotify again so the updated permissions are granted."
                        } else {
                            "2. Log in to Spotify."
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = SpotifyWhite
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        if (isRefreshRequest) {
                            "3. The existing profile will be updated in place without creating a duplicate."
                        } else {
                            "3. Your profile will be added automatically."
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = SpotifyWhite
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = if (isCompleting) {
                            if (isRefreshRequest) "Finalizing refreshed permissions..." else "Finalizing profile..."
                        } else {
                            if (isRefreshRequest) "Waiting for Spotify to return the refreshed consent." else "Waiting for your phone to finish sign-in."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = SpotifyLightGray
                    )
                    errorMessage?.let {
                        Spacer(Modifier.height(24.dp))
                        Text(text = it, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFFFB4AB))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.startNewSession(refreshProfileId) }) {
                            Text("Generate New Code")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = SpotifyCardSurface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .aspectRatio(1f)
                            .background(Color.White, RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR code for Spotify profile onboarding",
                                modifier = Modifier.fillMaxSize().padding(24.dp)
                            )
                        } else {
                            CircularProgressIndicator(color = SpotifyGreen)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = sessionCode,
                        style = MaterialTheme.typography.displayMedium,
                        color = SpotifyWhite
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (isWaitingForProfile) {
                            if (isRefreshRequest) "This refresh session stays active until Spotify re-consent completes." else "Code stays active until sign-in completes."
                        } else {
                            "Preparing secure session..."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = SpotifyLightGray
                    )
                    if (isCompleting) {
                        Spacer(Modifier.height(20.dp))
                        CircularProgressIndicator(color = SpotifyGreen)
                    }
                }
            }
        }
    }
}

private fun generateQrBitmap(content: String, sizePx: Int = 768): Bitmap {
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}