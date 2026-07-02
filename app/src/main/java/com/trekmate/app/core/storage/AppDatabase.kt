package com.trekmate.app.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.trekmate.app.core.storage.dao.BleObservationDao
import com.trekmate.app.core.storage.dao.MemberDao
import com.trekmate.app.core.storage.dao.TourDao
import com.trekmate.app.core.storage.entity.BleObservationEntity
import com.trekmate.app.core.storage.entity.CurrentTourEntity
import com.trekmate.app.core.storage.entity.TourMemberEntity

@Database(
    entities = [
        CurrentTourEntity::class,
        TourMemberEntity::class,
        BleObservationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tourDao(): TourDao
    abstract fun memberDao(): MemberDao
    abstract fun bleObservationDao(): BleObservationDao
}
