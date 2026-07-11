package com.trekmate.app.core.model

/**
 * The two map styles available in TrekMate.
 * Toggled by the button on [MapScreen].
 */
enum class MapStyle(
    val styleUri: String,
    val displayName: String,
    val iconLabel: String
) {
    SATELLITE_STREETS(
        styleUri   = "mapbox://styles/mapbox/satellite-streets-v12",
        displayName = "Vệ tinh",
        iconLabel   = "🛰"
    ),
    OUTDOORS(
        styleUri   = "mapbox://styles/mapbox/outdoors-v12",
        displayName = "Địa hình",
        iconLabel   = "🗺"
    );

    /** Returns the next style in the cycle. */
    fun next(): MapStyle = entries[(ordinal + 1) % entries.size]
}
