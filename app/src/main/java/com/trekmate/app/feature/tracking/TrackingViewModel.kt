package com.trekmate.app.feature.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trekmate.app.core.model.MemberPresence
import com.trekmate.app.core.model.TourRole
import com.trekmate.app.core.time.ClockProvider
import com.trekmate.app.feature.tour.TourRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackingUiState(
    val presenceList: List<MemberPresence> = emptyList(),
    val lostResult: LostDetectionResult? = null,
    val scanningState: String = "idle",
    val advertisingState: String = "idle"
)

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val tourRepository: TourRepository,
    private val presenceRepository: PresenceRepository,
    private val lostDetectionEngine: LostDetectionEngine,
    private val clock: ClockProvider
) : ViewModel() {

    val presenceList: StateFlow<List<MemberPresence>> = presenceRepository.observePresence()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lostStatus: StateFlow<LostDetectionResult?> = combine(
        tourRepository.observeCurrentTour(),
        tourRepository.observeMembers(),
        presenceRepository.observePresence()
    ) { tour, members, presence ->
        if (tour == null) return@combine null

        val currentUserId = if (tour.role == TourRole.LEADER) {
            tour.leaderId
        } else {
            members.firstOrNull { !it.isLeader }?.userId ?: return@combine null
        }
        val input = LostDetectionInput(
            currentUserId = currentUserId,
            leaderId = tour.leaderId,
            role = tour.role,
            members = members.map { it.userId },
            presenceList = presence,
            nowMs = clock.currentTimeMillis()
        )
        lostDetectionEngine.evaluate(input)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
