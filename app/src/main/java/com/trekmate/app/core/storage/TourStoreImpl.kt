package com.trekmate.app.core.storage

import com.trekmate.app.core.model.CurrentTour
import com.trekmate.app.core.model.TourRole
import com.trekmate.app.core.storage.dao.TourDao
import com.trekmate.app.core.storage.entity.CurrentTourEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface TourStore {
    fun observeCurrentTour(): Flow<CurrentTour?>
    suspend fun getCurrentTour(): CurrentTour?
    suspend fun saveCurrentTour(tour: CurrentTour)
    suspend fun clearCurrentTour()
}

@Singleton
class TourStoreImpl @Inject constructor(
    private val dao: TourDao
) : TourStore {

    override fun observeCurrentTour(): Flow<CurrentTour?> =
        dao.observeCurrentTour().map { it?.toDomain() }

    override suspend fun getCurrentTour(): CurrentTour? =
        dao.getCurrentTour()?.toDomain()

    override suspend fun saveCurrentTour(tour: CurrentTour) =
        dao.saveCurrentTour(tour.toEntity())

    override suspend fun clearCurrentTour() = dao.clearCurrentTour()

    private fun CurrentTourEntity.toDomain() = CurrentTour(
        tourId = tourId,
        groupId = groupId,
        leaderId = leaderId,
        joinCode = joinCode,
        qrPayload = qrPayload,
        role = TourRole.valueOf(role),
        createdAt = createdAt
    )

    private fun CurrentTour.toEntity() = CurrentTourEntity(
        tourId = tourId,
        groupId = groupId,
        leaderId = leaderId,
        joinCode = joinCode,
        qrPayload = qrPayload,
        role = role.name,
        createdAt = createdAt
    )
}
