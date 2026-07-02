package com.trekmate.app.di

import com.trekmate.app.feature.notification.TrekMateNotificationManager
import com.trekmate.app.feature.notification.TrekMateNotificationManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    @Singleton
    abstract fun bindNotificationManager(impl: TrekMateNotificationManagerImpl): TrekMateNotificationManager
}
