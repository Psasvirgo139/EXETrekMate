package com.trekmate.app.di

import android.content.Context
import androidx.room.Room
import com.trekmate.app.core.storage.*
import com.trekmate.app.core.storage.dao.BleObservationDao
import com.trekmate.app.core.storage.dao.MemberDao
import com.trekmate.app.core.storage.dao.TourDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "trekmate.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTourDao(db: AppDatabase): TourDao = db.tourDao()

    @Provides
    fun provideMemberDao(db: AppDatabase): MemberDao = db.memberDao()

    @Provides
    fun provideBleObservationDao(db: AppDatabase): BleObservationDao = db.bleObservationDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageBindingsModule {

    @Binds
    abstract fun bindTourStore(impl: TourStoreImpl): TourStore

    @Binds
    abstract fun bindMemberStore(impl: MemberStoreImpl): MemberStore

    @Binds
    abstract fun bindBleObservationStore(impl: BleObservationStoreImpl): BleObservationStore
}
