package com.trekmate.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trekmate.app.feature.tour.TourUiState
import com.trekmate.app.feature.tour.TourViewModel
import com.trekmate.app.feature.qr.QrCodeRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTourScreen(
    onBack: () -> Unit,
    onTourCreated: () -> Unit,
    tourViewModel: TourViewModel = hiltViewModel(),
    qrRenderer: QrCodeRenderer
) {
    val uiState by tourViewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is TourUiState.Active) onTourCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Tour") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = uiState) {
                is TourUiState.Idle -> {
                    Text(
                        "Create a new tour and share the join code with your group.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { tourViewModel.createTour() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create Tour")
                    }
                }

                is TourUiState.Loading -> {
                    Spacer(Modifier.height(40.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Creating tour...")
                }

                is TourUiState.Active -> {
                    TourShareCard(
                        tourId = state.tour.tourId,
                        joinCode = state.tour.joinCode,
                        groupId = state.tour.groupId,
                        qrPayload = state.tour.qrPayload,
                        qrRenderer = qrRenderer
                    )
                }

                is TourUiState.Error -> {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { tourViewModel.clearError() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun TourShareCard(
    tourId: String,
    joinCode: String,
    groupId: String,
    qrPayload: String,
    qrRenderer: QrCodeRenderer
) {
    val qrBitmap: Bitmap? = remember(qrPayload) { qrRenderer.render(qrPayload) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Tour Created!", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))

            InfoRow("Tour ID", tourId)
            InfoRow("Group ID", groupId)

            Spacer(Modifier.height(8.dp))
            Text("Join Code", style = MaterialTheme.typography.labelSmall)
            Text(
                joinCode,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )

            qrBitmap?.let { bitmap ->
                Spacer(Modifier.height(16.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.size(240.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
