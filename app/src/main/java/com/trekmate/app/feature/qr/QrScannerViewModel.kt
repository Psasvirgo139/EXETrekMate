package com.trekmate.app.feature.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trekmate.app.feature.tour.TourRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QrScannerViewModel @Inject constructor(
    private val parser: QrPayloadParser,
    private val tourRepository: TourRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow<QrScanState>(QrScanState.Idle)
    val scanState: StateFlow<QrScanState> = _scanState.asStateFlow()

    fun onQrScanned(rawValue: String) {
        parser.parse(rawValue)
            .onSuccess { payload ->
                _scanState.value = QrScanState.Decoded(payload)
                joinWithPayload(payload)
            }
            .onFailure { error ->
                _scanState.value = QrScanState.Error(error.message ?: "Invalid QR code")
            }
    }

    fun onCameraPermissionDenied() {
        _scanState.value = QrScanState.PermissionDenied
    }

    fun reset() {
        _scanState.value = QrScanState.Idle
    }

    private fun joinWithPayload(payload: TourJoinPayload) {
        viewModelScope.launch {
            tourRepository.joinTour(payload.joinCode)
                .onFailure { error ->
                    _scanState.value = QrScanState.Error(error.message ?: "Failed to join tour")
                }
        }
    }
}
