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
import com.trekmate.app.feature.tour.TourRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
 * Manages Mapbox offline tile downloads — INDEPENDENT from GPS acquisition.
 *
 * Map center is hardcoded to [CENTER_LAT], [CENTER_LON] (Đà Nẵng area) for testing.
 * This allows the map download to be tested without depending on live GPS.
 *
 * Lifecycle:
 *  - Tour appears  → start download immediately with fixed center
 *  - Tour ends     → delete tile region + style packs (after 5 s grace period)
 *
 * State is exposed via [state] StateFlow → drives [MapDownloadCard] UI.
 *
 * Mapbox Maps SDK v11 package note:
 *   OfflineManager, StylePackLoadOptions, GlyphsRasterizationMode, TilesetDescriptorOptions
 *   → com.mapbox.maps  (NOT com.mapbox.maps.offline / NOT com.mapbox.common)
 *   TileStore, TileRegionLoadOptions, TileRegionLoadProgress, NetworkRestriction, Cancelable
 *   → com.mapbox.common
 *   Value → com.mapbox.bindgen  (NOT com.mapbox.common)
 */
@Singleton
class OfflineMapManager @Inject constructor(
    private val tourRepository: TourRepository
) {
    companion object {
        private const val TAG = "TrekOfflineMap"
        private const val REGION_ID = "trek-offline-region"

        // Fixed center for testing — Đà Nẵng area (15.962429, 108.261144)
        const val CENTER_LAT = 18.663466
        const val CENTER_LON = 138.549436

        private const val RADIUS_KM = 1.0
        private const val MIN_ZOOM: Byte = 0
        private const val MAX_ZOOM: Byte = 16
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<MapDownloadState>(MapDownloadState.Idle)
    val state: StateFlow<MapDownloadState> = _state.asStateFlow()

    /** Lazy — created only after Mapbox token is set (via resValue string resource). */
    private val offlineManager: OfflineManager by lazy { OfflineManager() }
    private val tileStore: TileStore by lazy { TileStore.create() }

    private var activeTourId: String? = null
    private var downloadJob: Job? = null
    /** 5-second grace period before treating tour-null as truly ended (handles WebSocket blips). */
    private var tourNullJob: Job? = null

    init {
        scope.launch {
            tourRepository.observeCurrentTour().collect { tour ->
                if (tour != null) {
                    tourNullJob?.cancel()
                    tourNullJob = null

                    if (activeTourId != tour.tourId) {
                        activeTourId = tour.tourId
                        downloadJob?.cancel()
                        _state.value = MapDownloadState.Idle
                        downloadJob = scope.launch { beginDownload() }
                    }
                    // Same tour re-emit → download already running/done, ignore
                } else {
                    if (activeTourId != null && tourNullJob == null) {
                        Log.d(TAG, "Tour null — starting 5 s grace period")
                        tourNullJob = scope.launch {
                            delay(5_000L)
                            activeTourId = null
                            tourNullJob = null
                            downloadJob?.cancel()
                            downloadJob = null
                            deleteRegion()
                        }
                    }
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Download flow
    // ────────────────────────────────────────────────────────────────────────

    private suspend fun beginDownload() {
        // Guard: don't restart if already in progress or done
        when (_state.value) {
            is MapDownloadState.Downloading,
            is MapDownloadState.Ready -> {
                Log.d(TAG, "Download already in progress or ready — skipping")
                return
            }
            else -> { /* Idle or Error — proceed */ }
        }

        // *** Signal to UI immediately so the card appears, even before Mapbox API calls ***
        // Without this, state stays Idle until the first progress callback fires (which may
        // never happen if the download completes instantly from cache, or fails immediately).
        _state.value = MapDownloadState.Downloading(0f, "Đang khởi động tải bản đồ…")
        Log.d(TAG, "Starting offline download — center ($CENTER_LAT, $CENTER_LON) radius ${RADIUS_KM}km zoom $MIN_ZOOM-$MAX_ZOOM")

        try {
            downloadAll(CENTER_LAT, CENTER_LON)
        } catch (e: CancellationException) {
            // Coroutine cancelled (tour changed/ended) — reset state cleanly
            Log.d(TAG, "Download cancelled (tour changed or ended)")
            _state.value = MapDownloadState.Idle
            throw e  // Rethrow so coroutine properly cancels
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            _state.value = MapDownloadState.Error("Tải bản đồ thất bại: ${e.message}")
        }
    }

    private suspend fun downloadAll(lat: Double, lon: Double) {
        val STAGE_TIMEOUT = 60_000L  // 60 s per stage; style packs can be slow on first run

        // ── Stage 1: Satellite style pack (0 → 15%) ──────────────────────────
        _state.value = MapDownloadState.Downloading(0.01f, "Đang tải style vệ tinh…")
        Log.d(TAG, "[Stage 1] loadStylePack SATELLITE_STREETS start")
        val s1ok = withTimeoutOrNull(STAGE_TIMEOUT) {
            loadStylePackSuspend(MapStyle.SATELLITE_STREETS.styleUri) { p ->
                _state.value = MapDownloadState.Downloading(
                    progress = p * 0.15f,
                    stage = "Đang tải style vệ tinh… ${(p * 100).toInt()}%"
                )
            }
        }
        if (s1ok == null) Log.w(TAG, "[Stage 1] TIMEOUT — continuing")
        else Log.d(TAG, "[Stage 1] done")

        // ── Stage 2: Outdoors style pack (15 → 30%) ─────────────────────────
        _state.value = MapDownloadState.Downloading(0.15f, "Đang tải style địa hình…")
        Log.d(TAG, "[Stage 2] loadStylePack OUTDOORS start")
        val s2ok = withTimeoutOrNull(STAGE_TIMEOUT) {
            loadStylePackSuspend(MapStyle.OUTDOORS.styleUri) { p ->
                _state.value = MapDownloadState.Downloading(
                    progress = 0.15f + p * 0.15f,
                    stage = "Đang tải style địa hình… ${(p * 100).toInt()}%"
                )
            }
        }
        if (s2ok == null) Log.w(TAG, "[Stage 2] TIMEOUT — continuing")
        else Log.d(TAG, "[Stage 2] done")

        // ── Stage 3: Tile region (30 → 100%) ────────────────────────────────
        _state.value = MapDownloadState.Downloading(0.30f, "Đang tải dữ liệu bản đồ…")
        val geometry = buildBoundingBoxPolygon(lat, lon, RADIUS_KM)
        Log.d(TAG, "[Stage 3] loadTileRegion center=($lat,$lon) radius=${RADIUS_KM}km")

        val descriptors = withContext(Dispatchers.Main) {
            listOf(
                offlineManager.createTilesetDescriptor(
                    TilesetDescriptorOptions.Builder()
                        .styleURI(MapStyle.SATELLITE_STREETS.styleUri)
                        .minZoom(MIN_ZOOM).maxZoom(MAX_ZOOM).build()
                ),
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
            .acceptExpired(true)
            .networkRestriction(NetworkRestriction.NONE)
            .build()

        val s3ok = withTimeoutOrNull(300_000L) {  // 5 min for tiles
            downloadTileRegionSuspend(REGION_ID, tileOptions) { p ->
                _state.value = MapDownloadState.Downloading(
                    progress = 0.30f + p * 0.70f,
                    stage = "Đang tải dữ liệu bản đồ… ${(p * 100).toInt()}%"
                )
            }
        }
        if (s3ok == null) {
            Log.e(TAG, "[Stage 3] TIMEOUT after 5 min")
            throw Exception("Tải tile region timeout sau 5 phút")
        }
        Log.d(TAG, "[Stage 3] done")

        Log.d(TAG, "Offline download complete")
        _state.value = MapDownloadState.Ready(centerLat = lat, centerLon = lon)
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
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<Unit> { cont ->
                    offlineManager.removeStylePack(MapStyle.SATELLITE_STREETS.styleUri) { cont.resume(Unit) }
                }
                suspendCancellableCoroutine<Unit> { cont ->
                    offlineManager.removeStylePack(MapStyle.OUTDOORS.styleUri) { cont.resume(Unit) }
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "Error during region delete (harmless): ${e.message}")
        }
        _state.value = MapDownloadState.Idle
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
