package com.trekmate.app.core.storage.dao

import androidx.room.*
import com.trekmate.app.core.storage.entity.CurrentTourEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TourDao {

    @Query("SELECT * FROM current_tour ORDER BY createdAt DESC LIMIT 1")
    abstract fun observeCurrentTour(): Flow<CurrentTourEntity?>

    @Query("SELECT * FROM current_tour ORDER BY createdAt DESC LIMIT 1")
    abstract suspend fun getCurrentTour(): CurrentTourEntity?

    /**
     * Always clear the table first so that LIMIT 1 queries never return a stale previous tour.
     * Each device can only be in one tour at a time.
     */
    @Transaction
    open suspend fun saveCurrentTour(tour: CurrentTourEntity) {
        clearCurrentTour()
        insertTour(tour)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTour(tour: CurrentTourEntity)

    @Query("DELETE FROM current_tour")
    abstract suspend fun clearCurrentTour()
}
