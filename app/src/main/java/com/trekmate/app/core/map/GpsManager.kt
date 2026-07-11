package com.trekmate.app.core.map

import android.util.Log
import com.trekmate.app.core.model.GpsState
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages GPS acquisition independently from map download.
 *
 * Lifecycle:
 *  - Tour appears → [GpsState.Acquiring] → try to get GPS fix (up to [MAX_ATTEMPTS] times)
 *  - On success   → [GpsState.Success] (terminal — stays until tour ends)
 *  - On failure   → [GpsState.Failed]  (terminal — stays until tour ends)
 *  - Tour ends    → [GpsState.Idle]
 *
 * This is completely independent from [OfflineMapManager] — the two features
 * can be tested separately.
 */
@Singleton
class GpsManager @Inject constructor(
    private val locationProvider: LocationProvider,
    private val tourRepository: TourRepository
) {
    companion object {
        private const val TAG = "TrekGpsManager"
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 10_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<GpsState>(GpsState.Idle)
    val state: StateFlow<GpsState> = _state.asStateFlow()

    private var activeTourId: String? = null
    private var gpsJob: Job? = null
    private var tourNullJob: Job? = null

    init {
        scope.launch {
            tourRepository.observeCurrentTour().collect { tour ->
                if (tour != null) {
                    tourNullJob?.cancel()
                    tourNullJob = null

                    if (activeTourId != tour.tourId) {
                        activeTourId = tour.tourId
                        gpsJob?.cancel()
                        gpsJob = scope.launch { acquireGps() }
                    }
                    // Same tour re-emit → GPS already running/done, ignore
                } else {
                    if (activeTourId != null && tourNullJob == null) {
                        tourNullJob = scope.launch {
                            delay(5_000L)
                            activeTourId = null
                            tourNullJob = null
                            gpsJob?.cancel()
                            gpsJob = null
                            _state.value = GpsState.Idle
                        }
                    }
                }
            }
        }
    }

    private suspend fun acquireGps() {
        // Skip if GPS already obtained or currently acquiring for this tour
        when (_state.value) {
            is GpsState.Acquiring,
            is GpsState.Success -> {
                Log.d(TAG, "GPS already in progress or done — skipping")
                return
            }
            else -> { /* Idle or Failed — proceed */ }
        }

        repeat(MAX_ATTEMPTS) { attempt ->
            _state.value = GpsState.Acquiring
            Log.d(TAG, "GPS attempt ${attempt + 1}/$MAX_ATTEMPTS")

            try {
                val (lat, lon) = locationProvider.getCurrentLocation()
                Log.d(TAG, "GPS success: ($lat, $lon)")
                _state.value = GpsState.Success(lat, lon)
                return  // Terminal success — stop retrying
            } catch (e: CancellationException) {
                throw e  // Don't swallow coroutine cancellation
            } catch (e: SecurityException) {
                Log.e(TAG, "GPS permission denied: ${e.message}")
                _state.value = GpsState.Failed("Chưa được cấp quyền vị trí")
                return  // Fail fast — no retry for permission error
            } catch (e: Exception) {
                Log.w(TAG, "GPS attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < MAX_ATTEMPTS - 1) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        // All attempts failed
        _state.value = GpsState.Failed("Không lấy được vị trí GPS sau $MAX_ATTEMPTS lần thử")
    }
}
