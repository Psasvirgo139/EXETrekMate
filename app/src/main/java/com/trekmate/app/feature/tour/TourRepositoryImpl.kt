package com.trekmate.app.feature.tour

import com.trekmate.app.core.model.CurrentTour
import com.trekmate.app.core.model.TourMember
import com.trekmate.app.core.model.TourRole
import com.trekmate.app.core.network.dto.CreateTourResponse
import com.trekmate.app.core.network.dto.JoinTourResponse
import com.trekmate.app.core.network.dto.TourMemberDto
import com.trekmate.app.core.storage.BleObservationStore
import com.trekmate.app.core.storage.MemberStore
import com.trekmate.app.core.storage.TourStore
import com.trekmate.app.core.time.ClockProvider
import com.trekmate.app.feature.auth.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TourRepositoryImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val apiSync: ApiSyncRepository,
    private val tourStore: TourStore,
    private val memberStore: MemberStore,
    private val bleObservationStore: BleObservationStore,
    private val clock: ClockProvider
) : TourRepository {

    override fun observeCurrentTour(): Flow<CurrentTour?> = tourStore.observeCurrentTour()

    override fun observeMembers(): Flow<List<TourMember>> = memberStore.observeMembers()

    override suspend fun createTour(): Result<CurrentTour> {
        val user = authRepository.getOrCreateCurrentUser()
        return apiSync.createTour(user.userId).mapCatching { dto ->
            val tour = dto.toDomain(TourRole.LEADER, clock.currentTimeMillis())
            tourStore.saveCurrentTour(tour)
            val leaderMember = TourMember(userId = user.userId, tourId = tour.tourId, isLeader = true)
            memberStore.replaceMembers(listOf(leaderMember))
            tour
        }
    }

    override suspend fun joinTour(joinCode: String): Result<CurrentTour> {
        val user = authRepository.getOrCreateCurrentUser()
        return apiSync.joinTour(user.userId, joinCode).mapCatching { dto ->
            val tour = dto.toDomain(TourRole.MEMBER, clock.currentTimeMillis())
            tourStore.saveCurrentTour(tour)
            memberStore.replaceMembers(dto.members.toDomainList(tour.tourId))
            tour
        }
    }

    override suspend fun endTour(): Result<Unit> {
        val tour = tourStore.getCurrentTour() ?: return Result.failure(IllegalStateException("No active tour"))
        val user = authRepository.getOrCreateCurrentUser()
        return apiSync.endTour(tour.tourId, user.userId).onSuccess {
            clearLocalTour()
        }
    }

    override suspend fun clearLocalTour() {
        tourStore.clearCurrentTour()
        memberStore.clearMembers()
        bleObservationStore.clearObservations()
    }

    override suspend fun syncMembers(): Result<Unit> {
        val tour = tourStore.getCurrentTour() ?: return Result.failure(IllegalStateException("No active tour"))
        return apiSync.getMembers(tour.tourId).mapCatching { dtos ->
            memberStore.replaceMembers(dtos.toDomainList(tour.tourId))
        }
    }

    private fun CreateTourResponse.toDomain(role: TourRole, createdAt: Long) = CurrentTour(
        tourId = tourId,
        groupId = groupId,
        leaderId = leaderId,
        joinCode = joinCode,
        qrPayload = qrPayload,
        role = role,
        createdAt = createdAt
    )

    private fun JoinTourResponse.toDomain(role: TourRole, createdAt: Long) = CurrentTour(
        tourId = tourId,
        groupId = groupId,
        leaderId = leaderId,
        joinCode = joinCode,
        qrPayload = qrPayload,
        role = role,
        createdAt = createdAt
    )

    private fun List<TourMemberDto>.toDomainList(tourId: String): List<TourMember> =
        map { TourMember(userId = it.userId, tourId = tourId, isLeader = it.isLeader) }
}
