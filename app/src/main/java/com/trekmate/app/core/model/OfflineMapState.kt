package com.trekmate.app.core.model

/**
 * Represents the full lifecycle of offline map preparation.
 *
 *  Idle ──► GettingLocation ──► Downloading ──► Ready
 *                  │
 *                  └──► LocationFailed (retrying) ──► Error (max retries)
 */
sealed interface OfflineMapState {

    /** No tour active — card is hidden. */
    data object Idle : OfflineMapState

    /** Acquiring GPS fix to use as map center. */
    data object GettingLocation : OfflineMapState

    /**
     * GPS failed on [attempt]-th try.
     * Card shows "Lấy vị trí thất bại, đang thử lại…"
     */
    data class LocationFailed(
        val attempt: Int,
        val maxAttempts: Int = 3
    ) : OfflineMapState

    /**
     * Downloading tiles / style packs.
     * @param progress 0.0 … 1.0
     * @param stage    human-readable label for current stage
     */
    data class Downloading(
        val progress: Float,
        val stage: String
    ) : OfflineMapState

    /**
     * All tiles downloaded — "Xem Map" button is shown.
     * @param centerLat  latitude used as map center
     * @param centerLon  longitude used as map center
     */
    data class Ready(
        val centerLat: Double,
        val centerLon: Double
    ) : OfflineMapState

    /** Unrecoverable error after all retries exhausted. */
    data class Error(val message: String) : OfflineMapState
}
