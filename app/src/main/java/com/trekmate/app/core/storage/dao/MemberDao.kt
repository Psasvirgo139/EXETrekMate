package com.trekmate.app.core.storage.dao

import androidx.room.*
import com.trekmate.app.core.storage.entity.TourMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MemberDao {

    @Query("SELECT * FROM tour_members")
    abstract fun observeMembers(): Flow<List<TourMemberEntity>>

    @Query("SELECT * FROM tour_members")
    abstract suspend fun getMembers(): List<TourMemberEntity>

    @Transaction
    open suspend fun replaceAll(members: List<TourMemberEntity>) {
        clearAll()
        insertAll(members)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(members: List<TourMemberEntity>)

    @Query("DELETE FROM tour_members")
    abstract suspend fun clearAll()
}
