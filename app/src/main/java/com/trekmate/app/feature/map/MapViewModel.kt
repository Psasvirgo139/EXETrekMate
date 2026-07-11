package com.trekmate.app.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trekmate.app.core.map.GpsManager
import com.trekmate.app.core.map.OfflineMapManager
import com.trekmate.app.core.model.GpsState
import com.trekmate.app.core.model.MapDownloadState
import com.trekmate.app.core.model.MapStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// ── MapPrepState ──────────────────────────────────────────────────────────────

/**
 * Combined state for the merged GPS + offline-map preparation card.
 *
 * Lifecycle (in order):
 *  Idle → AcquiringGps → GpsFailed(msg) [terminal error]
 *                      → GpsReady(lat, lon) → Downloading(progress, stage)
 *                                           → Ready(lat, lon)
 *                                           → DownloadError(msg)
 */
sealed interface MapPrepState {
    data object Idle         : MapPrepState
    data object AcquiringGps : MapPrepState
    data class Downloading(val lat: Double, val lon: Double, val progress: Float, val stage: String) : MapPrepState
    data class Ready(val lat: Double, val lon: Double) : MapPrepState
    data class GpsFailed(val message: String)     : MapPrepState
    data class DownloadError(val message: String) : MapPrepState
}

// ── SavedCamera ───────────────────────────────────────────────────────────────

/**
 * Camera position saved when the user leaves [MapScreen].
 *
 * - null     → first visit for this tour → center on GPS coordinates
 * - non-null → subsequent visit         → restore user's last pan/zoom position
 *
 * The underlying state lives in [OfflineMapManager] (a @Singleton), not in this
 * ViewModel.  This is intentional: Compose Navigation creates a new ViewModel
 * instance each time MapScreen is pushed onto the back-stack, but the singleton
 * persists across the whole app lifetime.
 */
data class SavedCamera(val lat: Double, val lon: Double, val zoom: Double)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class MapViewModel @Inject constructor(
    private val gpsManager: GpsManager,
    private val offlineMapManager: OfflineMapManager
) : ViewModel() {

    // ── Combined GPS + map-download state ─────────────────────────────────────

    val mapPrepState: StateFlow<MapPrepState> = combine(
        gpsManager.state,
        offlineMapManager.state
    ) { gps, map ->
        when {
            gps is GpsState.Idle -> MapPrepState.Idle

            gps is GpsState.Acquiring -> MapPrepState.AcquiringGps

            gps is GpsState.Failed -> MapPrepState.GpsFailed(gps.message)

            gps is GpsState.Success && map is MapDownloadState.Downloading ->
                MapPrepState.Downloading(gps.lat, gps.lon, map.progress, map.stage)

            gps is GpsState.Success && map is MapDownloadState.Ready ->
                MapPrepState.Ready(map.centerLat, map.centerLon)

            gps is GpsState.Success && map is MapDownloadState.Error ->
                MapPrepState.DownloadError(map.message)

            // GPS succeeded but map not yet started (brief transient)
            gps is GpsState.Success ->
                MapPrepState.Downloading(gps.lat, gps.lon, 0f, "Đã lấy GPS, đang khởi động tải bản đồ…")

            else -> MapPrepState.Idle
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MapPrepState.Idle)

    // ── Style (only OUTDOORS active — satellite commented out) ────────────────

    private val _currentStyle = MutableStateFlow(MapStyle.OUTDOORS)
    val currentStyle: StateFlow<MapStyle> = _currentStyle.asStateFlow()

    // /** Toggle satellite ↔ outdoors. Re-enable when satellite offline is implemented. */
    // fun toggleStyle() { _currentStyle.value = _currentStyle.value.next() }

    // ── Map center (GPS coordinates once download is ready) ───────────────────

    /**
     * Eagerly shared so [MapScreen] gets the correct value immediately on first
     * subscription — avoids the brief null→value race that would set camera to (0,0).
     */
    val mapCenter: StateFlow<Pair<Double, Double>?> = offlineMapManager.state
        .map { state ->
            (state as? MapDownloadState.Ready)?.let { Pair(it.centerLat, it.centerLon) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ── Camera save/restore (delegated to OfflineMapManager singleton) ────────
    //
    // WHY singleton and not ViewModel field?
    //   Compose Navigation creates a NEW MapViewModel instance each time the user
    //   navigates to MapScreen (new back-stack entry → new ViewModelStore).
    //   A field on the ViewModel would be lost on each navigation.
    //   OfflineMapManager is @Singleton and lives for the app lifetime, so camera
    //   state persists reliably across navigations within the same tour.

    /**
     * Returns the last saved camera position, or null on the first visit for this tour.
     * Called by [MapScreen] once at composition start (wrapped in remember {}).
     */
    fun getSavedCamera(): SavedCamera? {
        val t = offlineMapManager.getSavedCamera() ?: return null
        return SavedCamera(t.first, t.second, t.third)
    }

    /**
     * Called by [MapScreen]'s `DisposableEffect` when leaving the map view.
     * Persists the user's current pan/zoom so it can be restored on the next visit.
     */
    fun saveCamera(lat: Double, lon: Double, zoom: Double) {
        offlineMapManager.saveCamera(lat, lon, zoom)
    }
}
