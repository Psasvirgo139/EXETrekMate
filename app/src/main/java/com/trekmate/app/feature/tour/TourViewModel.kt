package com.trekmate.app.feature.tour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trekmate.app.core.model.CurrentTour
import com.trekmate.app.core.model.TourMember
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TourViewModel @Inject constructor(
    private val tourRepository: TourRepository
) : ViewModel() {

    val currentTour: StateFlow<CurrentTour?> = tourRepository.observeCurrentTour()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val members: StateFlow<List<TourMember>> = tourRepository.observeMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow<TourUiState>(TourUiState.Idle)
    val uiState: StateFlow<TourUiState> = _uiState.asStateFlow()

    fun createTour() {
        viewModelScope.launch {
            _uiState.value = TourUiState.Loading
            tourRepository.createTour()
                .onSuccess { tour -> _uiState.value = TourUiState.Active(tour, emptyList()) }
                .onFailure { _uiState.value = TourUiState.Error(it.message ?: "Failed to create tour") }
        }
    }

    fun joinTour(joinCode: String) {
        if (joinCode.isBlank()) {
            _uiState.value = TourUiState.Error("Join code cannot be empty")
            return
        }
        viewModelScope.launch {
            _uiState.value = TourUiState.Loading
            tourRepository.joinTour(joinCode.trim())
                .onSuccess { tour -> _uiState.value = TourUiState.Active(tour, emptyList()) }
                .onFailure { _uiState.value = TourUiState.Error(it.message ?: "Failed to join tour") }
        }
    }

    fun endTour() {
        viewModelScope.launch {
            _uiState.value = TourUiState.Loading
            tourRepository.endTour()
                .onSuccess { _uiState.value = TourUiState.Idle }
                .onFailure { _uiState.value = TourUiState.Error(it.message ?: "Failed to end tour") }
        }
    }

    fun clearError() {
        if (_uiState.value is TourUiState.Error) {
            _uiState.value = TourUiState.Idle
        }
    }
}
