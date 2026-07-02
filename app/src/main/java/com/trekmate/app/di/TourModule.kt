package com.trekmate.app.di

import com.trekmate.app.feature.tour.TourRepository
import com.trekmate.app.feature.tour.TourRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TourModule {

    @Binds
    @Singleton
    abstract fun bindTourRepository(impl: TourRepositoryImpl): TourRepository
}
