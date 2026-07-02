package com.trekmate.app.core.storage.dao

import androidx.room.*
import com.trekmate.app.core.storage.entity.TourMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {

    @Query("SELECT * FROM tour_members")
    fun observeMembers(): Flow<List<TourMemberEntity>>

    @Query("SELECT * FROM tour_members")
    suspend fun getMembers(): List<TourMemberEntity>

    @Transaction
    suspend fun replaceAll(members: List<TourMemberEntity>) {
        clearAll()
        insertAll(members)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<TourMemberEntity>)

    @Query("DELETE FROM tour_members")
    suspend fun clearAll()
}
