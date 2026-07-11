package com.trekmate.app.core.map

import android.util.Log
import com.mapbox.bindgen.Value
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
import com.trekmate.app.core.model.MapDownloadState
import com.trekmate.app.core.model.MapStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.cos
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Mapbox offline tile downloads.
 *
 * Map center is provided at runtime via [startDownload] — taken from GPS coordinates
 * obtained by [GpsManager]. This ensures the offline map is always centered on the
 * actual tour location.
 *
 * Lifecycle:
 *  - [startDownload] called with GPS coords → download immediately
 *  - Tour ends → delete tile region + style packs (after 5 s grace period)
 *
 * State is exposed via [state] StateFlow → drives the combined [MapPrepCard] UI.
 *
 * Mapbox Maps SDK v11 package note:
 *   OfflineManager, StylePackLoadOptions, GlyphsRasterizationMode, TilesetDescriptorOptions
 *   → com.mapbox.maps  (NOT com.mapbox.maps.offline / NOT com.mapbox.common)
 *   TileStore, TileRegionLoadOptions, TileRegionLoadProgress, NetworkRestriction, Cancelable
 *   → com.mapbox.common
 *   Value → com.mapbox.bindgen  (NOT com.mapbox.common)
 */
@Singleton
class OfflineMapManager @Inject constructor() {
    companion object {
        private const val TAG = "TrekOfflineMap"
        private const val REGION_ID_PREFIX = "trek-offline-region"

        private const val RADIUS_KM = 10.0
        // Zoom 8–16: regional overview → street detail for trekking.
        // Skipping zoom 0–7 saves ~70% tile data.
        private const val MIN_ZOOM: Byte = 8
        private const val MAX_ZOOM: Byte = 16
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<MapDownloadState>(MapDownloadState.Idle)
    val state: StateFlow<MapDownloadState> = _state.asStateFlow()

    /** Lazy — created only after Mapbox token is set (via resValue string resource). */
    private val offlineManager: OfflineManager by lazy { OfflineManager() }
    private val tileStore: TileStore by lazy { TileStore.create() }

    private var downloadJob: Job? = null

    /**
     * Unique region ID per download, generated at [startDownload] time.
     * Using a unique ID prevents Mapbox from silently reusing cached tiles from a
     * previous tour area when the region bounding-box changes.
     */
    private var currentRegionId = "$REGION_ID_PREFIX-init"

    // ── Camera state — stored here (singleton) so it survives MapViewModel recreation ──
    // MapViewModel is recreated each time MapScreen is pushed onto the nav back-stack.
    // Storing camera here ensures the saved position persists across navigations.
    private var _savedCameraLat = 0.0
    private var _savedCameraLon = 0.0
    private var _savedCameraZoom = 0.0
    private var _hasSavedCamera = false

    /** Save the user's current map pan/zoom position (called from MapScreen on dispose). */
    fun saveCamera(lat: Double, lon: Double, zoom: Double) {
        _savedCameraLat = lat
        _savedCameraLon = lon
        _savedCameraZoom = zoom
        _hasSavedCamera = true
        Log.d(TAG, "Camera saved: ($lat, $lon) zoom=$zoom")
    }

    /**
     * Returns the last saved camera position, or null if none has been saved yet
     * (first visit for this tour).
     */
    fun getSavedCamera(): Triple<Double, Double, Double>? =
        if (_hasSavedCamera) Triple(_savedCameraLat, _savedCameraLon, _savedCameraZoom) else null

    // ────────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Start offline map download centered on [lat], [lon] (GPS-obtained coordinates).
     * Safe to call multiple times — skips if already downloading or ready.
     */
    fun startDownload(lat: Double, lon: Double) {
        // Guard: don't restart if already in progress or done
        when (_state.value) {
            is MapDownloadState.Downloading,
            is MapDownloadState.Ready -> {
                Log.d(TAG, "Download already in progress or ready — skipping")
                return
            }
            else -> { /* Idle or Error — proceed */ }
        }

        // Generate a unique region ID for this download so Mapbox cannot reuse
        // tiles cached from a previous tour area.
        currentRegionId = "$REGION_ID_PREFIX-${System.currentTimeMillis()}"
        Log.d(TAG, "New download region: $currentRegionId")

        downloadJob?.cancel()
        downloadJob = scope.launch { beginDownload(lat, lon) }
    }

    /**
     * Cancel any in-progress download, immediately reset state to Idle (so the
     * guard in [startDownload] is cleared), then delete stored tiles in the background.
     * Called by [GpsManager] when the tour ends or a new tour starts.
     */
    fun cancelAndReset() {
        downloadJob?.cancel()
        downloadJob = null
        // Reset download state synchronously — clears the "Ready" guard before the new
        // tour's GPS triggers startDownload() while deleteRegion() is still running.
        _state.value = MapDownloadState.Idle
        // Reset saved camera — new tour should start fresh at its own GPS center.
        _hasSavedCamera = false
        val regionToDelete = currentRegionId
        scope.launch { deleteRegion(regionToDelete) }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Download flow
    // ────────────────────────────────────────────────────────────────────────

    private suspend fun beginDownload(lat: Double, lon: Double) {
        // *** Signal to UI immediately so the card appears, even before Mapbox API calls ***
        _state.value = MapDownloadState.Downloading(0f, "Đang khởi động tải bản đồ…")
        Log.d(TAG, "Starting offline download — center ($lat, $lon) radius ${RADIUS_KM}km zoom $MIN_ZOOM-$MAX_ZOOM")

        try {
            downloadAll(lat, lon)
        } catch (e: CancellationException) {
            Log.d(TAG, "Download cancelled (tour changed or ended)")
            _state.value = MapDownloadState.Idle
            throw e  // Rethrow so coroutine properly cancels
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            _state.value = MapDownloadState.Error("Tải bản đồ thất bại: ${e.message}")
        }
    }

    private suspend fun downloadAll(lat: Double, lon: Double) {
        val STYLE_TIMEOUT = 90_000L   // 90 s — style pack includes sprites + fonts

        // ── Stage 1: OUTDOORS style pack (0 → 50%) ───────────────────────────
        // OUTDOORS is vector-based and fully offline capable.
        // SATELLITE_STREETS is skipped — raster tiles are enormous and don't
        // work reliably offline; satellite is an online-only bonus feature.
        _state.value = MapDownloadState.Downloading(0.01f, "Đang tải style bản đồ…")
        Log.d(TAG, "[Stage 1] loadStylePack OUTDOORS start")
        val s1ok = withTimeoutOrNull(STYLE_TIMEOUT) {
            loadStylePackSuspend(MapStyle.OUTDOORS.styleUri) { p ->
                _state.value = MapDownloadState.Downloading(
                    progress = p * 0.50f,
                    stage = "Đang tải style bản đồ… ${(p * 100).toInt()}%"
                )
            }
        }
        if (s1ok == null) Log.w(TAG, "[Stage 1] TIMEOUT — continuing")
        else Log.d(TAG, "[Stage 1] done")

        // ── Stage 2: Tile region — OUTDOORS vector tiles (50 → 100%) ─────────
        _state.value = MapDownloadState.Downloading(0.50f, "Đang tải dữ liệu bản đồ…")
        val geometry = buildBoundingBoxPolygon(lat, lon, RADIUS_KM)
        Log.d(TAG, "[Stage 2] loadTileRegion center=($lat,$lon) radius=${RADIUS_KM}km zoom=$MIN_ZOOM-$MAX_ZOOM")

        val descriptors = withContext(Dispatchers.Main) {
            listOf(
                offlineManager.createTilesetDescriptor(
                    TilesetDescriptorOptions.Builder()
                        .styleURI(MapStyle.OUTDOORS.styleUri)
                        .minZoom(MIN_ZOOM).maxZoom(MAX_ZOOM).build()
                )
            )
        }

        val tileOptions = TileRegionLoadOptions.Builder()
            .geometry(geometry)
            .descriptors(descriptors)
            .metadata(Value.valueOf("TrekMate offline region"))
            // acceptExpired(false): don't serve tiles that have expired.
            // Combined with a fresh currentRegionId, this forces Mapbox to verify
            // each tile's validity rather than blindly serving the old region.
            .acceptExpired(false)
            .networkRestriction(NetworkRestriction.NONE)
            .build()

        val s2ok = withTimeoutOrNull(300_000L) {  // 5 min for tiles
            downloadTileRegionSuspend(currentRegionId, tileOptions) { p ->
                _state.value = MapDownloadState.Downloading(
                    progress = 0.50f + p * 0.50f,
                    stage = "Đang tải dữ liệu bản đồ… ${(p * 100).toInt()}%"
                )
            }
        }
        if (s2ok == null) {
            Log.e(TAG, "[Stage 2] TIMEOUT after 5 min")
            throw Exception("Tải tile region timeout sau 5 phút")
        }
        Log.d(TAG, "[Stage 2] done")

        Log.d(TAG, "Offline download complete")
        _state.value = MapDownloadState.Ready(centerLat = lat, centerLon = lon)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Delete
    // ────────────────────────────────────────────────────────────────────────

    private suspend fun deleteRegion(regionId: String) {
        Log.d(TAG, "Deleting offline region: $regionId")
        // IMPORTANT: tileStore callbacks are dispatched on the
        // main thread. Without withContext(Dispatchers.Main) the callbacks are NEVER
        // delivered → suspendCancellableCoroutine hangs forever silently.
        withContext(Dispatchers.Main) {
            runCatching {
                // Remove named tile region (frees tiles from offline anchor)
                suspendCancellableCoroutine<Unit> { cont ->
                    tileStore.removeTileRegion(regionId) { expected ->
                        if (expected?.error != null) {
                            Log.w(TAG, "removeTileRegion error: ${expected.error?.message}")
                        }
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                Log.d(TAG, "  removeTileRegion done: $regionId")
            }.onFailure { e ->
                Log.w(TAG, "Error during region delete: ${e.message}")
            }
        }
        Log.d(TAG, "Region delete complete: $regionId")
    }

    // ────────────────────────────────────────────────────────────────────────
    // Suspend helpers — Mapbox callbacks dispatched on main thread
    // withContext(Dispatchers.Main) ensures callbacks can be delivered
    // even if the calling coroutine runs on Dispatchers.Default.
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Downloads a Mapbox style pack and suspends until complete.
     * [onProgress] is called with 0f…1f as resources are fetched.
     */
    private suspend fun loadStylePackSuspend(
        styleUri: String,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            val opts = StylePackLoadOptions.Builder()
                .glyphsRasterizationMode(GlyphsRasterizationMode.ALL_GLYPHS_RASTERIZED_LOCALLY)
                .metadata(Value.valueOf("TrekMate"))
                .acceptExpired(true)
                .build()

            Log.d(TAG, "loadStylePackSuspend: calling offlineManager.loadStylePack($styleUri)")
            val cancelable = offlineManager.loadStylePack(
                styleUri, opts,
                { progress ->
                    val f = safeFraction(progress.completedResourceCount, progress.requiredResourceCount)
                    Log.v(TAG, "  stylePack progress: ${(f*100).toInt()}%")
                    onProgress(f)
                }
            ) { expected ->
                Log.d(TAG, "  stylePack completion: isValue=${expected.isValue} error=${expected.error}")
                if (expected.isValue) {
                    if (cont.isActive) cont.resume(Unit)
                } else {
                    if (cont.isActive) cont.resumeWithException(
                        Exception("StylePack($styleUri) error: ${expected.error?.message}")
                    )
                }
            }
            cont.invokeOnCancellation {
                Log.d(TAG, "  stylePack cancelled")
                cancelable.cancel()
            }
        }
    }

    /**
     * Downloads a Mapbox tile region and suspends until complete.
     * [onProgress] is called with 0f…1f as tiles are downloaded.
     */
    private suspend fun downloadTileRegionSuspend(
        regionId: String,
        options: TileRegionLoadOptions,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            Log.d(TAG, "downloadTileRegionSuspend: calling tileStore.loadTileRegion($regionId)")
            val cancelable = tileStore.loadTileRegion(
                regionId, options,
                { progress: TileRegionLoadProgress ->
                    val f = safeFraction(progress.completedResourceCount, progress.requiredResourceCount)
                    Log.v(TAG, "  tileRegion progress: ${(f*100).toInt()}% (${progress.completedResourceCount}/${progress.requiredResourceCount})")
                    onProgress(f)
                }
            ) { expected ->
                Log.d(TAG, "  tileRegion completion: isValue=${expected.isValue} error=${expected.error}")
                if (expected.isValue) {
                    if (cont.isActive) cont.resume(Unit)
                } else {
                    if (cont.isActive) cont.resumeWithException(
                        Exception("TileRegion error: ${expected.error?.message}")
                    )
                }
            }
            cont.invokeOnCancellation {
                Log.d(TAG, "  tileRegion cancelled")
                cancelable.cancel()
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Geometry helpers
    // ────────────────────────────────────────────────────────────────────────

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
