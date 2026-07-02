package com.trekmate.app.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trekmate.app.core.model.CurrentTour
import com.trekmate.app.core.model.CurrentUser
import com.trekmate.app.feature.auth.AuthRepository
import com.trekmate.app.feature.auth.AuthState
import com.trekmate.app.feature.tour.TourRepository
import com.trekmate.app.service.TrekMateBleService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tourRepository: TourRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val authState: StateFlow<AuthState> = flow {
        try {
            val user = authRepository.getOrCreateCurrentUser()
            emit(AuthState.Ready(user))
        } catch (e: Exception) {
            emit(AuthState.Error(e.message ?: "Authentication failed"))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Loading)

    val currentUser: StateFlow<CurrentUser?> = authRepository.observeCurrentUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentTour: StateFlow<CurrentTour?> = tourRepository.observeCurrentTour()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            tourRepository.observeCurrentTour().collectLatest { tour ->
                if (tour != null) {
                    startBleService()
                }
            }
        }
    }

    private fun startBleService() {
        val intent = Intent(context, TrekMateBleService::class.java)
        context.startForegroundService(intent)
    }

    fun stopBleService() {
        val intent = Intent(context, TrekMateBleService::class.java).apply {
            action = TrekMateBleService.ACTION_STOP
        }
        context.startService(intent)
    }
}
