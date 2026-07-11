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
import androidx.compose.material.icons.filled.Layers
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
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.geojson.Point
import com.trekmate.app.core.model.MapStyle
import com.trekmate.app.core.model.OfflineMapState
import com.trekmate.app.feature.map.MapViewModel
import androidx.compose.ui.viewinterop.AndroidView

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

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Mapbox Map ──────────────────────────────────────────────────────
        MapboxMapView(
            styleUri = currentStyle.styleUri,
            centerLat = mapCenter?.first ?: 21.0278,   // fallback: Hanoi
            centerLon = mapCenter?.second ?: 105.8342,
            modifier = Modifier.fillMaxSize()
        )

        // ── Style label chip (top center) ────────────────────────────────
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

        // ── Bottom FABs ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Back button — bottom-left
            FloatingActionButton(
                onClick = onBack,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
            }

            // Style toggle button — bottom-right
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

    // Forward lifecycle events to MapView
    DisposableEffect(lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = mapView.onStart()
            override fun onStop(owner: LifecycleOwner) = mapView.onStop()
            override fun onDestroy(owner: LifecycleOwner) { mapView.onDestroy() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Load or switch style whenever styleUri changes
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
// MapDownloadCard — overlaid at the bottom of tracking screens
// ────────────────────────────────────────────────────────────────────────────

/**
 * Animated overlay card showing map download progress.
 *
 * States:
 *  - [OfflineMapState.GettingLocation]  → spinner + "Đang lấy vị trí GPS..."
 *  - [OfflineMapState.LocationFailed]   → warning + "Lấy vị trí thất bại, đang thử lại..."
 *  - [OfflineMapState.Downloading]      → LinearProgressIndicator + "Đang tải bản đồ X%"
 *  - [OfflineMapState.Ready]            → Button "Xem Map"
 *  - [OfflineMapState.Error]            → error text
 *  - [OfflineMapState.Idle]             → hidden
 */
@Composable
fun MapDownloadCard(
    state: OfflineMapState,
    onViewMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible = state !is OfflineMapState.Idle

    AnimatedVisibility(
        visible = isVisible,
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
                // ── Getting location ──────────────────────────────────────
                is OfflineMapState.GettingLocation -> {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Column {
                            Text("Bản đồ offline", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Đang lấy vị trí GPS…", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // ── Location failed — retrying ────────────────────────────
                is OfflineMapState.LocationFailed -> {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Column {
                            Text("Bản đồ offline", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Lấy vị trí thất bại, đang thử lại… (${state.attempt}/${state.maxAttempts})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // ── Downloading ───────────────────────────────────────────
                is OfflineMapState.Downloading -> {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Bản đồ offline", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                // ── Ready ─────────────────────────────────────────────────
                is OfflineMapState.Ready -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Bản đồ offline", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Đã tải xong ✓", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2E7D32))
                        }
                        Button(onClick = onViewMap) {
                            Text("Xem Map")
                        }
                    }
                }

                // ── Error ─────────────────────────────────────────────────
                is OfflineMapState.Error -> {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Text("Bản đồ offline — lỗi", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                            Text(state.message, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                is OfflineMapState.Idle -> { /* animated away — not rendered */ }
            }
        }
    }
}
