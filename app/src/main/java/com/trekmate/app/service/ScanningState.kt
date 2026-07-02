package com.trekmate.app.service

sealed interface ScanningState {
    data object Idle : ScanningState
    data object Starting : ScanningState
    data object Running : ScanningState
    data class Failed(val reason: String) : ScanningState
    data object Stopped : ScanningState
}

sealed interface AdvertisingState {
    data object Idle : AdvertisingState
    data object Starting : AdvertisingState
    data object Running : AdvertisingState
    data class Failed(val reason: String) : AdvertisingState
    data object Stopped : AdvertisingState
}
