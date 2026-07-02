package com.trekmate.app.feature.qr

sealed interface QrScanState {
    data object Idle : QrScanState
    data object Scanning : QrScanState
    data object PermissionDenied : QrScanState
    data class Decoded(val payload: TourJoinPayload) : QrScanState
    data class Error(val message: String) : QrScanState
}
