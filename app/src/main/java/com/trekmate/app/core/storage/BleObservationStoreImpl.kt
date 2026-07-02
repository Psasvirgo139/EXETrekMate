package com.trekmate.app.core.storage

import com.trekmate.app.core.model.BleObservation
import com.trekmate.app.core.model.MemberPresence
import com.trekmate.app.core.storage.dao.BleObservationDao
import com.trekmate.app.core.storage.entity.BleObservationEntity
import com.trekmate.app.core.time.ClockProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface BleObservationStore {
    suspend fun upsertObservation(observation: BleObservation)
    fun observePresence(recentThresholdMs: Long): Flow<List<MemberPresence>>
    suspend fun clearObservations()
}

@Singleton
class BleObservationStoreImpl @Inject constructor(
    private val dao: BleObservationDao,
    private val clock: ClockProvider
) : BleObservationStore {

    override suspend fun upsertObservation(observation: BleObservation) {
        dao.upsert(
            BleObservationEntity(
                userId = observation.userId,
                groupId = observation.groupId,
                rssi = observation.rssi,
                seenAt = observation.seenAt
            )
        )
    }

    override fun observePresence(recentThresholdMs: Long): Flow<List<MemberPresence>> =
        dao.observeAll().map { entities ->
            val now = clock.currentTimeMillis()
            entities.map { entity ->
                MemberPresence(
                    userId = entity.userId,
                    lastRssi = entity.rssi,
                    lastSeenAt = entity.seenAt,
                    isRecentlySeen = (now - entity.seenAt) <= recentThresholdMs
                )
            }
        }

    override suspend fun clearObservations() = dao.clearAll()
}
