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
import androidx.compose.material.icons.filled.Map
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
import com.trekmate.app.core.model.MapStyle
import com.trekmate.app.feature.map.MapPrepState
import com.trekmate.app.feature.map.MapViewModel
import com.trekmate.app.feature.map.SavedCamera
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

    val centerLat = mapCenter?.first ?: 0.0
    val centerLon = mapCenter?.second ?: 0.0

    // Read saved camera once at composition start (remembered for this composable lifetime).
    // null = first visit for this tour → MapboxMapView will center on GPS.
    // non-null = user navigated back → MapboxMapView restores their last pan/zoom.
    val savedCamera = remember { viewModel.getSavedCamera() }

    Box(modifier = Modifier.fillMaxSize()) {
        MapboxMapView(
            styleUri = currentStyle.styleUri,
            centerLat = centerLat,
            centerLon = centerLon,
            savedCamera = savedCamera,
            onSaveCamera = { lat, lon, zoom -> viewModel.saveCamera(lat, lon, zoom) },
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

            // Style toggle — temporarily disabled (satellite raster tiles not suitable for offline).
            // Re-enable when satellite offline support is implemented.
            //
            // ExtendedFloatingActionButton(
            //     onClick = { viewModel.toggleStyle() },
            //     icon = { Icon(Icons.Default.Layers, contentDescription = null) },
            //     containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
            //     contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            // )
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
    savedCamera: SavedCamera?,
    onSaveCamera: (lat: Double, lon: Double, zoom: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // rememberUpdatedState ensures async callbacks (loadStyle) always read the latest values.
    val latestCenterLat by rememberUpdatedState(centerLat)
    val latestCenterLon by rememberUpdatedState(centerLon)
    val latestOnSaveCamera by rememberUpdatedState(onSaveCamera)

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
            override fun onStop(owner: LifecycleOwner)  = mapView.onStop()
            override fun onDestroy(owner: LifecycleOwner) { mapView.onDestroy() }
        }
        lifecycle.addObserver(observer)
        onDispose {
            // 1. Save the user's current camera position before leaving, so the next
            //    visit to MapScreen can restore it exactly.
            try {
                val cs = mapView.mapboxMap.cameraState
                latestOnSaveCamera(cs.center.latitude(), cs.center.longitude(), cs.zoom)
            } catch (_: Exception) { /* ignore if map not yet ready */ }

            // 2. Stop rendering explicitly when the composable leaves composition.
            //    This frees the render thread immediately, making back-navigation fast.
            mapView.onStop()

            lifecycle.removeObserver(observer)
        }
    }

    // Load style + set initial camera — runs ONCE per composable instance.
    // LaunchedEffect(Unit) is correct: a new instance is created each time
    // MapScreen enters the back-stack, so it fires exactly once per visit.
    LaunchedEffect(Unit) {
        mapView.mapboxMap.loadStyle(styleUri) {
            val cameraOptions = when {
                // Subsequent visit: restore user's last pan/zoom position.
                savedCamera != null -> CameraOptions.Builder()
                    .center(Point.fromLngLat(savedCamera.lon, savedCamera.lat))
                    .zoom(savedCamera.zoom)
                    .build()

                // First visit for this tour: center on GPS coordinates.
                latestCenterLat != 0.0 || latestCenterLon != 0.0 -> CameraOptions.Builder()
                    .center(Point.fromLngLat(latestCenterLon, latestCenterLat))
                    .zoom(14.0)
                    .build()

                // GPS not yet available (shouldn't happen — button only shows when Ready).
                else -> null
            }
            cameraOptions?.let { mapView.mapboxMap.setCamera(it) }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}


// ────────────────────────────────────────────────────────────────────────────
// MapPrepCard — unified GPS + offline map download card
// ────────────────────────────────────────────────────────────────────────────

/**
 * Single card that covers the full preparation flow:
 *   Acquiring GPS → GPS obtained + downloading map → map ready
 *
 * States:
 *  - [MapPrepState.Idle]          → hidden
 *  - [MapPrepState.AcquiringGps]  → spinner + "Đang lấy vị trí GPS…"
 *  - [MapPrepState.Downloading]   → GPS coords shown + progress bar
 *  - [MapPrepState.Ready]         → "Xem Map" button
 *  - [MapPrepState.GpsFailed]     → red warning + error message
 *  - [MapPrepState.DownloadError] → red warning + error message
 */
@Composable
fun MapPrepCard(
    state: MapPrepState,
    onViewMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state !is MapPrepState.Idle,
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

                // ── Acquiring GPS ────────────────────────────────────────────
                is MapPrepState.AcquiringGps -> {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Column {
                            Text(
                                "Chuẩn bị bản đồ offline",
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

                // ── Downloading map ─────────────────────────────────────────
                is MapPrepState.Downloading -> {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    "Chuẩn bị bản đồ offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // GPS success indicator + coordinates
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        "GPS: ${String.format("%.4f", state.lat)}, ${String.format("%.4f", state.lon)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
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

                // ── Ready ───────────────────────────────────────────────────
                is MapPrepState.Ready -> {
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
                                Icons.Default.Map,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Xem Map")
                        }
                    }
                }

                // ── GPS Failed ──────────────────────────────────────────────
                is MapPrepState.GpsFailed -> {
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
                                "Không lấy được vị trí GPS",
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

                // ── Download Error ──────────────────────────────────────────
                is MapPrepState.DownloadError -> {
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
                                "Tải bản đồ offline — lỗi",
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

                is MapPrepState.Idle -> { /* hidden */ }
            }
        }
    }
}
