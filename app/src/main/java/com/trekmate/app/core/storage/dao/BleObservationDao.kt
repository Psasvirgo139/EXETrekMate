package com.trekmate.app.core.storage.dao

import androidx.room.*
import com.trekmate.app.core.storage.entity.BleObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BleObservationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(observation: BleObservationEntity)

    @Query("SELECT * FROM ble_observations")
    fun observeAll(): Flow<List<BleObservationEntity>>

    @Query("SELECT * FROM ble_observations")
    suspend fun getAll(): List<BleObservationEntity>

    @Query("SELECT * FROM ble_observations WHERE userId = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): BleObservationEntity?

    @Query("DELETE FROM ble_observations")
    suspend fun clearAll()
}
