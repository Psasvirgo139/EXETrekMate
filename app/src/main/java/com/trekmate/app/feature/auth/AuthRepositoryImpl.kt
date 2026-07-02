package com.trekmate.app.feature.auth

import com.trekmate.app.core.model.CurrentUser
import com.trekmate.app.core.storage.UserPreferencesDataStore
import com.trekmate.app.core.time.ClockProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val store: UserPreferencesDataStore,
    private val idGenerator: UserIdGenerator,
    private val clock: ClockProvider
) : AuthRepository {

    override suspend fun getOrCreateCurrentUser(): CurrentUser {
        val existing = store.observeUser().first()
        if (existing != null) return existing

        val newUser = CurrentUser(
            userId = idGenerator.generate(),
            displayName = null,
            createdAt = clock.currentTimeMillis()
        )
        store.saveUser(newUser)
        return newUser
    }

    override fun observeCurrentUser(): Flow<CurrentUser?> = store.observeUser()
}
