package com.trekmate.app.di

import com.trekmate.app.core.ble.BlePacketDecoder
import com.trekmate.app.core.ble.BlePacketDecoderImpl
import com.trekmate.app.core.ble.BlePacketEncoder
import com.trekmate.app.core.ble.BlePacketEncoderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {

    @Binds
    abstract fun bindBlePacketEncoder(impl: BlePacketEncoderImpl): BlePacketEncoder

    @Binds
    abstract fun bindBlePacketDecoder(impl: BlePacketDecoderImpl): BlePacketDecoder
}
