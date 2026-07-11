package com.trekmate.app.core.map

import android.util.Log
import com.mapbox.common.Cancelable
import com.mapbox.common.NetworkRestriction
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileRegionLoadProgress
import com.mapbox.common.TileStore
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.GlyphsRasterizationMode           // com.mapbox.maps (NOT .offline)
import com.mapbox.maps.OfflineManager                    // com.mapbox.maps (NOT .offline)
import com.mapbox.maps.StylePackLoadOptions              // com.mapbox.maps (NOT .offline)
import com.mapbox.maps.TilesetDescriptorOptions          // com.mapbox.maps (NOT com.mapbox.common)
import com.mapbox.bindgen.Value                          // NOT com.mapbox.common.Value
import com.trekmate.app.core.model.MapStyle
import com.trekmate.app.core.model.OfflineMapState
import com.trekmate.app.feature.tour.TourRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.cos
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton that manages Mapbox offline tile downloads.
 *
 * Lifecycle is automatically tied to the tour lifecycle:
 *  - tour appears  → get GPS → download [SATELLITE_STREETS + OUTDOORS] for 30km radius, zoom 0–16
 *  - tour disappears → delete tile region + style packs
 *
 * Progress is exposed via [state] StateFlow for the UI.
 *
 * Package note (Mapbox Maps SDK v11.x):
 *   OfflineManager, StylePackLoadOptions, GlyphsRasterizationMode, TilesetDescriptorOptions
 *   → all in com.mapbox.maps (NOT com.mapbox.maps.offline / NOT com.mapbox.common)
 *   TileStore, TileRegionLoadOptions, TileRegionLoadProgress, NetworkRestriction, Cancelable
 *   → com.mapbox.common
 */
@Singleton
class OfflineMapManager @Inject constructor(
    private val locationProvider: LocationProvider,
    private val tourRepository: TourRepository
) {
    companion object {
        private const val TAG = "TrekOfflineMap"
        private const val REGION_ID = "trek-offline-region"
        private const val RADIUS_KM = 30.0
        private const val MIN_ZOOM: Byte = 0
        private const val MAX_ZOOM: Byte = 16
        private const val MAX_LOCATION_ATTEMPTS = 3
        private const val LOCATION_RETRY_DELAY_MS = 10_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<OfflineMapState>(OfflineMapState.Idle)
    val state: StateFlow<OfflineMapState> = _state.asStateFlow()

    /** Lazy — created only after Mapbox token is set (via resValue string resource). */
    private val offlineManager: OfflineManager by lazy { OfflineManager() }
    private val tileStore: TileStore by lazy { TileStore.create() }

    private var activeTourId: String? = null

    init {
        scope.launch {
            tourRepository.observeCurrentTour().collectLatest { tour ->
                if (tour != null) {
                    if (activeTourId != tour.tourId) {
                        activeTourId = tour.tourId
                        beginDownload()
                    }
                } else {
                    if (activeTourId != null) {
                        activeTourId = null
                        deleteRegion()
                    }
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Download flow
    // ────────────────────────────────────────────────────────────────────────

    private suspend fun beginDownload() {
        if (_state.value is OfflineMapState.Ready || _state.value is OfflineMapState.Downloading) {
            Log.d(TAG, "Already downloading or ready — skipping")
            return
        }
        val (lat, lon) = acquireLocation() ?: return
        try {
            downloadAll(lat, lon)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            _state.value = OfflineMapState.Error("Tải bản đồ thất bại: ${e.message}")
        }
    }

    /**
     * Tries to get GPS up to [MAX_LOCATION_ATTEMPTS] times.
     * Emits [OfflineMapState.GettingLocation] / [OfflineMapState.LocationFailed] while retrying.
     */
    private suspend fun acquireLocation(): Pair<Double, Double>? {
        repeat(MAX_LOCATION_ATTEMPTS) { attempt ->
            _state.value = if (attempt == 0) {
                OfflineMapState.GettingLocation
            } else {
                OfflineMapState.LocationFailed(attempt, MAX_LOCATION_ATTEMPTS)
            }
            val result = locationProvider.getCurrentLocation()
            if (result.isSuccess) return result.getOrThrow()
            Log.w(TAG, "Location attempt ${attempt + 1}/$MAX_LOCATION_ATTEMPTS failed")
            if (attempt < MAX_LOCATION_ATTEMPTS - 1) delay(LOCATION_RETRY_DELAY_MS)
        }
        _state.value = OfflineMapState.Error("Không lấy được vị trí sau $MAX_LOCATION_ATTEMPTS lần thử")
        return null
    }

    private suspend fun downloadAll(lat: Double, lon: Double) {
        Log.d(TAG, "Starting offline download — center ($lat, $lon) radius ${RADIUS_KM}km zoom $MIN_ZOOM-$MAX_ZOOM")

        // ── Stage 1: Satellite style pack (0 → 15%) ──────────────────────────
        loadStylePackFlow(MapStyle.SATELLITE_STREETS.styleUri).collect { p ->
            _state.value = OfflineMapState.Downloading(
                progress = p * 0.15f,
                stage = "Đang tải style vệ tinh… ${(p * 100).toInt()}%"
            )
        }

        // ── Stage 2: Outdoors style pack (15 → 30%) ─────────────────────────
        loadStylePackFlow(MapStyle.OUTDOORS.styleUri).collect { p ->
            _state.value = OfflineMapState.Downloading(
                progress = 0.15f + p * 0.15f,
                stage = "Đang tải style địa hình… ${(p * 100).toInt()}%"
            )
        }

        // ── Stage 3: Tile region (30 → 100%) ────────────────────────────────
        val geometry = buildBoundingBoxPolygon(lat, lon, RADIUS_KM)
        val descriptors = listOf(
            offlineManager.createTilesetDescriptor(
                TilesetDescriptorOptions.Builder()
                    .styleURI(MapStyle.SATELLITE_STREETS.styleUri)
                    .minZoom(MIN_ZOOM)
                    .maxZoom(MAX_ZOOM)
                    .build()
            ),
            offlineManager.createTilesetDescriptor(
                TilesetDescriptorOptions.Builder()
                    .styleURI(MapStyle.OUTDOORS.styleUri)
                    .minZoom(MIN_ZOOM)
                    .maxZoom(MAX_ZOOM)
                    .build()
            )
        )
        val options = TileRegionLoadOptions.Builder()
            .geometry(geometry)
            .descriptors(descriptors)
            .metadata(Value.valueOf("TrekMate offline region"))
            .acceptExpired(true)
            .networkRestriction(NetworkRestriction.NONE)
            .build()

        downloadTileRegionFlow(REGION_ID, options).collect { p ->
            _state.value = OfflineMapState.Downloading(
                progress = 0.30f + p * 0.70f,
                stage = "Đang tải dữ liệu bản đồ… ${(p * 100).toInt()}%"
            )
        }

        Log.d(TAG, "Offline download complete")
        _state.value = OfflineMapState.Ready(centerLat = lat, centerLon = lon)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Delete
    // ────────────────────────────────────────────────────────────────────────

    private suspend fun deleteRegion() {
        Log.d(TAG, "Deleting offline region")
        runCatching {
            suspendCancellableCoroutine<Unit> { cont ->
                tileStore.removeTileRegion(REGION_ID) { cont.resume(Unit) }
            }
            suspendCancellableCoroutine<Unit> { cont ->
                offlineManager.removeStylePack(MapStyle.SATELLITE_STREETS.styleUri) { cont.resume(Unit) }
            }
            suspendCancellableCoroutine<Unit> { cont ->
                offlineManager.removeStylePack(MapStyle.OUTDOORS.styleUri) { cont.resume(Unit) }
            }
        }.onFailure { e ->
            Log.w(TAG, "Error during region delete (harmless): ${e.message}")
        }
        _state.value = OfflineMapState.Idle
    }

    // ────────────────────────────────────────────────────────────────────────
    // Flow helpers — convert callbacks to Flow<Float> (0f..1f)
    // ────────────────────────────────────────────────────────────────────────

    private fun loadStylePackFlow(styleUri: String): Flow<Float> = callbackFlow {
        var cancelable: Cancelable? = null
        // metadata() accepts HashMap<String, Value> in Mapbox Maps SDK v11
        val opts = StylePackLoadOptions.Builder()
            .glyphsRasterizationMode(GlyphsRasterizationMode.ALL_GLYPHS_RASTERIZED_LOCALLY)
            .metadata(Value.valueOf("TrekMate"))  // metadata() expects Value? — NOT HashMap
            .acceptExpired(true)
            .build()

        cancelable = offlineManager.loadStylePack(
            styleUri,
            opts,
            { progress ->
                val f = safeFraction(progress.completedResourceCount, progress.requiredResourceCount)
                trySend(f)
            }
        ) { expected ->
            if (expected.isValue) {
                trySend(1f); close()
            } else {
                close(Exception("StylePack error: ${expected.error?.message}"))
            }
        }
        awaitClose { cancelable?.cancel() }
    }

    private fun downloadTileRegionFlow(
        regionId: String,
        options: TileRegionLoadOptions
    ): Flow<Float> = callbackFlow {
        var cancelable: Cancelable? = null
        cancelable = tileStore.loadTileRegion(
            regionId,
            options,
            { progress: TileRegionLoadProgress ->
                val f = safeFraction(progress.completedResourceCount, progress.requiredResourceCount)
                trySend(f)
            }
        ) { expected ->
            if (expected.isValue) {
                trySend(1f); close()
            } else {
                close(Exception("TileRegion error: ${expected.error?.message}"))
            }
        }
        awaitClose { cancelable?.cancel() }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Geometry helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Creates a rectangular bounding-box polygon approximating a circle
     * of [radiusKm] km around ([lat], [lon]).
     */
    private fun buildBoundingBoxPolygon(lat: Double, lon: Double, radiusKm: Double): Polygon {
        val deltaLat = radiusKm / 111.32
        val deltaLon = radiusKm / (111.32 * cos(lat * PI / 180.0))
        val sw = Point.fromLngLat(lon - deltaLon, lat - deltaLat)
        val se = Point.fromLngLat(lon + deltaLon, lat - deltaLat)
        val ne = Point.fromLngLat(lon + deltaLon, lat + deltaLat)
        val nw = Point.fromLngLat(lon - deltaLon, lat + deltaLat)
        return Polygon.fromLngLats(listOf(listOf(sw, se, ne, nw, sw)))
    }

    private fun safeFraction(completed: Long, required: Long): Float =
        if (required > 0L) (completed.toFloat() / required).coerceIn(0f, 1f) else 0f
}
