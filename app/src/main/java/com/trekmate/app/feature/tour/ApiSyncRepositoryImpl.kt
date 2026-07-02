package com.trekmate.app.feature.tour

import com.trekmate.app.core.network.TourApiService
import com.trekmate.app.core.network.dto.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiSyncRepositoryImpl @Inject constructor(
    private val api: TourApiService
) : ApiSyncRepository {

    override suspend fun createTour(userId: String): Result<CreateTourResponse> =
        runCatching { api.createTour(CreateTourRequest(leaderId = userId)) }

    override suspend fun joinTour(userId: String, joinCode: String): Result<JoinTourResponse> =
        runCatching { api.joinTour(JoinTourRequest(userId = userId, joinCode = joinCode)) }

    override suspend fun endTour(tourId: String, userId: String): Result<Unit> =
        runCatching { api.endTour(EndTourRequest(tourId = tourId, leaderId = userId)); Unit }

    override suspend fun getMembers(tourId: String): Result<List<TourMemberDto>> =
        runCatching { api.getMembers(tourId).members }
}
