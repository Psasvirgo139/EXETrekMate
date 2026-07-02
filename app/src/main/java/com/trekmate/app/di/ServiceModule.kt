package com.trekmate.app.di

import com.trekmate.app.service.BleAdvertiserController
import com.trekmate.app.service.BleAdvertiserControllerImpl
import com.trekmate.app.service.BleScannerController
import com.trekmate.app.service.BleScannerControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindBleAdvertiserController(impl: BleAdvertiserControllerImpl): BleAdvertiserController

    @Binds
    @Singleton
    abstract fun bindBleScannerController(impl: BleScannerControllerImpl): BleScannerController
}
