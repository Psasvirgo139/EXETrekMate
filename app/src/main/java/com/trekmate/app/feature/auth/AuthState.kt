package com.trekmate.app.feature.auth

import com.trekmate.app.core.model.CurrentUser

sealed interface AuthState {
    data object Loading : AuthState
    data class Ready(val user: CurrentUser) : AuthState
    data class Error(val message: String) : AuthState
}
