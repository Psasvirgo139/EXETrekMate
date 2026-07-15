package com.trekmate.app.core.model

/**
 * State for the offline map download card (independent from GPS).
 *
 *  Idle → Downloading(progress, stage) → Ready
 *                                       → Error(message)
 */
sealed interface MapDownloadState {
    /** No tour active — card is hidden. */
    data object Idle : MapDownloadState

    /**
     * Downloading tiles / style packs.
     * @param progress 0.0 … 1.0
     * @param stage    human-readable label for current stage
     */
    data class Downloading(
        val progress: Float,
        val stage: String
    ) : MapDownloadState

    /**
     * All tiles downloaded — "Xem Map" button is enabled.
     * @param centerLat  latitude used as map center
     * @param centerLon  longitude used as map center
     */
    data class Ready(
        val centerLat: Double,
        val centerLon: Double
    ) : MapDownloadState

    /** Download failed. */
    data class Error(val message: String) : MapDownloadState
}
