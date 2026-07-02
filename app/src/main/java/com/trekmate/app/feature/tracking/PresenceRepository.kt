package com.trekmate.app.feature.tracking

import com.trekmate.app.core.model.BleObservation
import com.trekmate.app.core.model.MemberPresence
import kotlinx.coroutines.flow.Flow

interface PresenceRepository {
    fun observePresence(): Flow<List<MemberPresence>>
    suspend fun recordObservation(observation: BleObservation)
}
