package com.trekmate.app.di

import com.trekmate.app.feature.qr.QrCodeRenderer
import com.trekmate.app.feature.qr.QrCodeRendererImpl
import com.trekmate.app.feature.qr.QrPayloadParser
import com.trekmate.app.feature.qr.QrPayloadParserImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class QrModule {

    @Binds
    abstract fun bindQrPayloadParser(impl: QrPayloadParserImpl): QrPayloadParser

    @Binds
    abstract fun bindQrCodeRenderer(impl: QrCodeRendererImpl): QrCodeRenderer
}
