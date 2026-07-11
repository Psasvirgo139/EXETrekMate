package com.trekmate.app.core.model

/**
 * State for the GPS acquisition card (independent from map download).
 *
 *  Idle → Acquiring → Success(lat, lon)
 *                   → Failed(message)
 */
sealed interface GpsState {
    /** No tour active — card is hidden. */
    data object Idle : GpsState

    /** Actively acquiring GPS fix. */
    data object Acquiring : GpsState

    /** GPS fix obtained successfully. */
    data class Success(val lat: Double, val lon: Double) : GpsState

    /** All attempts failed. */
    data class Failed(val message: String) : GpsState
}
