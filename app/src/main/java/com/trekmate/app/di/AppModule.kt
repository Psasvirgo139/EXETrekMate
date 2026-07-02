package com.trekmate.app.di

import com.trekmate.app.core.time.ClockProvider
import com.trekmate.app.core.time.SystemClockProvider
import com.trekmate.app.feature.auth.AuthRepository
import com.trekmate.app.feature.auth.AuthRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindClockProvider(impl: SystemClockProvider): ClockProvider

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
