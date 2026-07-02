package com.trekmate.app.feature.auth

import com.trekmate.app.core.model.CurrentUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun getOrCreateCurrentUser(): CurrentUser
    fun observeCurrentUser(): Flow<CurrentUser?>
}
