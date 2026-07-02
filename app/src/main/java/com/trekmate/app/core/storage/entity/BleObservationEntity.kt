package com.trekmate.app.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ble_observations")
data class BleObservationEntity(
    @PrimaryKey val userId: String,
    val groupId: String,
    val rssi: Int,
    val seenAt: Long
)
