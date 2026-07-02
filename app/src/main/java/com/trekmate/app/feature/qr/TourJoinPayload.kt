package com.trekmate.app.feature.qr

/**
 * Parsed representation of a TrekMate QR code.
 * QR payload format: trekmate://join?code=JOINCODE
 */
data class TourJoinPayload(
    val joinCode: String
)
