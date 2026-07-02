package com.trekmate.app.feature.tracking

import com.trekmate.app.core.model.BleObservation
import com.trekmate.app.core.model.MemberPresence
import com.trekmate.app.core.storage.BleObservationStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val RECENT_THRESHOLD_MS = 60_000L

@Singleton
class PresenceRepositoryImpl @Inject constructor(
    private val observationStore: BleObservationStore
) : PresenceRepository {

    override fun observePresence(): Flow<List<MemberPresence>> =
        observationStore.observePresence(RECENT_THRESHOLD_MS)

    override suspend fun recordObservation(observation: BleObservation) {
        observationStore.upsertObservation(observation)
    }
}
