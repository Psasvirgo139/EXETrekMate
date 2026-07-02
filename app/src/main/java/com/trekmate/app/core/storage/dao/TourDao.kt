package com.trekmate.app.core.storage.dao

import androidx.room.*
import com.trekmate.app.core.storage.entity.CurrentTourEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TourDao {

    @Query("SELECT * FROM current_tour LIMIT 1")
    fun observeCurrentTour(): Flow<CurrentTourEntity?>

    @Query("SELECT * FROM current_tour LIMIT 1")
    suspend fun getCurrentTour(): CurrentTourEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCurrentTour(tour: CurrentTourEntity)

    @Query("DELETE FROM current_tour")
    suspend fun clearCurrentTour()
}
