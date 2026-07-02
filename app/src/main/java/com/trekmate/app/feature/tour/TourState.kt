package com.trekmate.app.feature.tour

import com.trekmate.app.core.model.CurrentTour
import com.trekmate.app.core.model.TourMember

sealed interface TourUiState {
    data object Idle : TourUiState
    data object Loading : TourUiState
    data class Active(val tour: CurrentTour, val members: List<TourMember>) : TourUiState
    data class Error(val message: String) : TourUiState
}
