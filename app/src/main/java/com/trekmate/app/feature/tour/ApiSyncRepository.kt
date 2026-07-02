package com.trekmate.app.feature.tour

import com.trekmate.app.core.network.dto.*

interface ApiSyncRepository {
    suspend fun createTour(userId: String): Result<CreateTourResponse>
    suspend fun joinTour(userId: String, joinCode: String): Result<JoinTourResponse>
    suspend fun endTour(tourId: String, userId: String): Result<Unit>
    suspend fun getMembers(tourId: String): Result<List<TourMemberDto>>
}
