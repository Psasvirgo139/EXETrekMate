package com.trekmate.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TrekMateApplication : Application()
// Mapbox access token is provided via resValue("string", "mapbox_access_token") in build.gradle.kts.
// The Mapbox SDK v11 reads it automatically from R.string.mapbox_access_token — no programmatic init required.
