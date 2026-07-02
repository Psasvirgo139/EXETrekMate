package com.trekmate.app.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trekmate.app.core.model.CurrentUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val CREATED_AT = longPreferencesKey("created_at")
    }

    fun observeUser(): Flow<CurrentUser?> = context.dataStore.data.map { prefs ->
        val userId = prefs[Keys.USER_ID] ?: return@map null
        CurrentUser(
            userId = userId,
            displayName = prefs[Keys.DISPLAY_NAME],
            createdAt = prefs[Keys.CREATED_AT] ?: 0L
        )
    }

    suspend fun saveUser(user: CurrentUser) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = user.userId
            user.displayName?.let { prefs[Keys.DISPLAY_NAME] = it }
            prefs[Keys.CREATED_AT] = user.createdAt
        }
    }
}
