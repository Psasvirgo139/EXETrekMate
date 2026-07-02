package com.trekmate.app.core.model

data class BleObservation(
    val userId: String,
    val groupId: String,
    val rssi: Int,
    val seenAt: Long
)
