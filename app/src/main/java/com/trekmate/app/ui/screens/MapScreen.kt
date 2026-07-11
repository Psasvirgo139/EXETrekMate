package com.trekmate.app.ui.screens

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.geojson.Point
import com.trekmate.app.core.map.OfflineMapManager
import com.trekmate.app.core.model.GpsState
import com.trekmate.app.core.model.MapDownloadState
import com.trekmate.app.core.model.MapStyle
import com.trekmate.app.feature.map.MapViewModel
import androidx.compose.ui.viewinterop.AndroidView

// ────────────────────────────────────────────────────────────────────────────
// MapScreen — full-screen Mapbox viewer
// ────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen Mapbox map composable.
 *
 * Layout:
 *   - MapboxMap fills the screen
 *   - Bottom-left FAB: back to tracking screen
 *   - Bottom-right FAB: toggle map style (satellite ↔ outdoors)
 */
@Composable
fun MapScreen(
    onBack: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val currentStyle by viewModel.currentStyle.collectAsState()
    val mapCenter by viewModel.mapCenter.collectAsState()

    // Use fixed center from OfflineMapManager, fallback to hardcoded center
    val centerLat = mapCenter?.first ?: OfflineMapManager.CENTER_LAT
    val centerLon = mapCenter?.second ?: OfflineMapManager.CENTER_LON

    Box(modifier = Modifier.fillMaxSize()) {
        MapboxMapView(
            styleUri = currentStyle.styleUri,
            centerLat = centerLat,
            centerLon = centerLon,
            modifier = Modifier.fillMaxSize()
        )

        // Style label chip (top center)
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            tonalElevation = 4.dp
        ) {
            Text(
                text = "${currentStyle.iconLabel}  ${currentStyle.displayName}",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        // Bottom FABs
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FloatingActionButton(
                onClick = onBack,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
            }

            ExtendedFloatingActionButton(
                onClick = { viewModel.toggleStyle() },
                icon = { Icon(Icons.Default.Layers, contentDescription = null) },
                text = {
                    Text(
                        when (currentStyle) {
                            MapStyle.SATELLITE_STREETS -> "Địa hình"
                            MapStyle.OUTDOORS          -> "Vệ tinh"
                        }
                    )
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// MapboxMapView — lifecycle-aware AndroidView wrapper
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun MapboxMapView(
    styleUri: String,
    centerLat: Double,
    centerLon: Double,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember {
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = mapView.onStart()
            override fun onStop(owner: LifecycleOwner) = mapView.onStop()
            override fun onDestroy(owner: LifecycleOwner) { mapView.onDestroy() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(styleUri) {
        mapView.mapboxMap.loadStyle(styleUri) {
            mapView.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(centerLon, centerLat))
                    .zoom(14.0)
                    .build()
            )
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}

// ────────────────────────────────────────────────────────────────────────────
// GpsStatusCard — independent GPS acquisition status card
// ────────────────────────────────────────────────────────────────────────────

/**
 * Shows GPS acquisition status as a standalone card.
 *
 * States:
 *  - [GpsState.Idle]       → hidden
 *  - [GpsState.Acquiring]  → spinner + "Đang lấy vị trí GPS..."
 *  - [GpsState.Success]    → green check + coordinates
 *  - [GpsState.Failed]     → red warning + error message
 */
@Composable
fun GpsStatusCard(
    state: GpsState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state !is GpsState.Idle,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            when (state) {
                // ── Acquiring ────────────────────────────────────────────────
                is GpsState.Acquiring -> {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Column {
                            Text(
                                "Vị trí GPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Đang lấy vị trí GPS…",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // ── Success ──────────────────────────────────────────────────
                is GpsState.Success -> {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                "Vị trí GPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Lấy vị trí thành công ✓",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                "${String.format("%.5f", state.lat)}, ${String.format("%.5f", state.lon)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ── Failed ───────────────────────────────────────────────────
                is GpsState.Failed -> {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                "Vị trí GPS — thất bại",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is GpsState.Idle -> { /* hidden */ }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// MapDownloadCard — independent map download progress card
// ────────────────────────────────────────────────────────────────────────────

/**
 * Shows map download progress and "Xem Map" button as a standalone card.
 * Uses hardcoded center coordinates — independent from GPS.
 *
 * States:
 *  - [MapDownloadState.Idle]        → hidden
 *  - [MapDownloadState.Downloading] → LinearProgressIndicator + stage label
 *  - [MapDownloadState.Ready]       → "Xem Map" button enabled
 *  - [MapDownloadState.Error]       → error message
 */
@Composable
fun MapDownloadCard(
    state: MapDownloadState,
    onViewMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state !is MapDownloadState.Idle,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            when (state) {
                // ── Downloading ───────────────────────────────────────────────
                is MapDownloadState.Downloading -> {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Bản đồ offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            state.stage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── Ready ─────────────────────────────────────────────────────
                is MapDownloadState.Ready -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                "Bản đồ offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Đã tải xong ✓",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2E7D32)
                            )
                        }
                        Button(onClick = onViewMap) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Xem Map")
                        }
                    }
                }

                // ── Error ─────────────────────────────────────────────────────
                is MapDownloadState.Error -> {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                "Bản đồ offline — lỗi",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is MapDownloadState.Idle -> { /* hidden */ }
            }
        }
    }
}
