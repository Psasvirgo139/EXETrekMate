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
    /** No tour active — card is hidden. */
    data object Idle : MapPrepState

    /** Acquiring GPS fix. */
    data object AcquiringGps : MapPrepState

    /**
     * GPS succeeded, offline map download in progress.
     * @param lat       GPS latitude (for display)
     * @param lon       GPS longitude (for display)
     * @param progress  0.0 … 1.0
     * @param stage     human-readable stage label
     */
    data class Downloading(
        val lat: Double,
        val lon: Double,
        val progress: Float,
        val stage: String
    ) : MapPrepState

    /**
     * GPS succeeded + map downloaded — "Xem Map" button shown.
     */
    data class Ready(val lat: Double, val lon: Double) : MapPrepState

    /** GPS acquisition failed (all retries exhausted). */
    data class GpsFailed(val message: String) : MapPrepState

    /** Map download failed. */
    data class DownloadError(val message: String) : MapPrepState
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val gpsManager: GpsManager,
    private val offlineMapManager: OfflineMapManager
) : ViewModel() {

    /**
     * Combined GPS + map-download state — drives the single [MapPrepCard] UI.
     *
     * Mapping rules:
     *  - GPS Idle or (GPS Success + map Idle)  → MapPrepState.Idle
     *  - GPS Acquiring                          → MapPrepState.AcquiringGps
     *  - GPS Failed                             → MapPrepState.GpsFailed
     *  - GPS Success + map Downloading          → MapPrepState.Downloading
     *  - GPS Success + map Ready                → MapPrepState.Ready
     *  - GPS Success + map Error                → MapPrepState.DownloadError
     */
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

            // GPS succeeded but map not yet started (brief transient) → show Downloading(0%)
            gps is GpsState.Success ->
                MapPrepState.Downloading(gps.lat, gps.lon, 0f, "Đã lấy GPS, đang khởi động tải bản đồ…")

            else -> MapPrepState.Idle
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MapPrepState.Idle)

    /** The current map style selected by the user. */
    private val _currentStyle = MutableStateFlow(MapStyle.SATELLITE_STREETS)
    val currentStyle: StateFlow<MapStyle> = _currentStyle.asStateFlow()

    /** Map center: GPS coordinates when download is ready. */
    val mapCenter: StateFlow<Pair<Double, Double>?> = offlineMapManager.state
        .map { state ->
            (state as? MapDownloadState.Ready)?.let { Pair(it.centerLat, it.centerLon) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Cycle to the next map style (satellite ↔ outdoors). */
    fun toggleStyle() {
        _currentStyle.value = _currentStyle.value.next()
    }
}
