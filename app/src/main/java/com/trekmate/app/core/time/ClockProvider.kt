package com.trekmate.app.core.time

import javax.inject.Inject

interface ClockProvider {
    fun currentTimeMillis(): Long
}

class SystemClockProvider @Inject constructor() : ClockProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
