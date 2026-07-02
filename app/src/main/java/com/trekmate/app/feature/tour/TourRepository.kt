package com.trekmate.app.feature.tour

import com.trekmate.app.core.model.CurrentTour
import com.trekmate.app.core.model.TourMember
import kotlinx.coroutines.flow.Flow

interface TourRepository {
    fun observeCurrentTour(): Flow<CurrentTour?>
    fun observeMembers(): Flow<List<TourMember>>
    suspend fun createTour(): Result<CurrentTour>
    suspend fun joinTour(joinCode: String): Result<CurrentTour>
    suspend fun endTour(): Result<Unit>
    suspend fun clearLocalTour()
    suspend fun syncMembers(): Result<Unit>
}
