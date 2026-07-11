package com.trekmate.app.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trekmate.app.core.map.OfflineMapManager
import com.trekmate.app.core.model.MapStyle
import com.trekmate.app.core.model.OfflineMapState
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
    private val offlineMapManager: OfflineMapManager
) : ViewModel() {

    /** Live offline download state — drives the MapDownloadCard UI. */
    val offlineMapState: StateFlow<OfflineMapState> = offlineMapManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OfflineMapState.Idle)

    /** The current map style selected by the user. Persists as long as the ViewModel lives. */
    private val _currentStyle = MutableStateFlow(MapStyle.SATELLITE_STREETS)
    val currentStyle: StateFlow<MapStyle> = _currentStyle.asStateFlow()

    /** Map center derived from the Ready state. Null until download completes. */
    val mapCenter: StateFlow<Pair<Double, Double>?> = offlineMapManager.state
        .map { state ->
            (state as? OfflineMapState.Ready)?.let { Pair(it.centerLat, it.centerLon) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Cycle to the next map style (satellite ↔ outdoors). */
    fun toggleStyle() {
        _currentStyle.value = _currentStyle.value.next()
    }
}
