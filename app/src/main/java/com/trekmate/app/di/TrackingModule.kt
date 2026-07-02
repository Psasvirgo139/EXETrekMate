package com.trekmate.app.di

import com.trekmate.app.feature.tracking.LostDetectionEngine
import com.trekmate.app.feature.tracking.LostDetectionEngineImpl
import com.trekmate.app.feature.tracking.PresenceRepository
import com.trekmate.app.feature.tracking.PresenceRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {

    @Binds
    @Singleton
    abstract fun bindPresenceRepository(impl: PresenceRepositoryImpl): PresenceRepository

    @Binds
    @Singleton
    abstract fun bindLostDetectionEngine(impl: LostDetectionEngineImpl): LostDetectionEngine
}
