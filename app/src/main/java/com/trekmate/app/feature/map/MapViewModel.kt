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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val gpsManager: GpsManager,
    private val offlineMapManager: OfflineMapManager
) : ViewModel() {

    /** GPS acquisition state — drives GpsStatusCard UI. */
    val gpsState: StateFlow<GpsState> = gpsManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GpsState.Idle)

    /** Map download state — drives MapDownloadCard UI. */
    val mapDownloadState: StateFlow<MapDownloadState> = offlineMapManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MapDownloadState.Idle)

    /** The current map style selected by the user. */
    private val _currentStyle = MutableStateFlow(MapStyle.SATELLITE_STREETS)
    val currentStyle: StateFlow<MapStyle> = _currentStyle.asStateFlow()

    /** Map center: fixed coordinates from OfflineMapManager when download is ready. */
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
