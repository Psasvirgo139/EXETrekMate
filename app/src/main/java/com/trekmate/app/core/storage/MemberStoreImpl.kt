package com.trekmate.app.core.storage

import com.trekmate.app.core.model.TourMember
import com.trekmate.app.core.storage.dao.MemberDao
import com.trekmate.app.core.storage.entity.TourMemberEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface MemberStore {
    fun observeMembers(): Flow<List<TourMember>>
    suspend fun replaceMembers(members: List<TourMember>)
    suspend fun clearMembers()
}

@Singleton
class MemberStoreImpl @Inject constructor(
    private val dao: MemberDao
) : MemberStore {

    override fun observeMembers(): Flow<List<TourMember>> =
        dao.observeMembers().map { list -> list.map { it.toDomain() } }

    override suspend fun replaceMembers(members: List<TourMember>) =
        dao.replaceAll(members.map { it.toEntity() })

    override suspend fun clearMembers() = dao.clearAll()

    private fun TourMemberEntity.toDomain() = TourMember(
        userId = userId,
        tourId = tourId,
        isLeader = isLeader
    )

    private fun TourMember.toEntity() = TourMemberEntity(
        userId = userId,
        tourId = tourId,
        isLeader = isLeader
    )
}
