package com.trekmate.app.di

import javax.inject.Qualifier

/** Qualifier for an application-lifetime CoroutineScope (lives until process dies). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
