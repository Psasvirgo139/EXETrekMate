package com.trekmate.app.core.time

interface ClockProvider {
    fun currentTimeMillis(): Long
}

class SystemClockProvider : ClockProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
