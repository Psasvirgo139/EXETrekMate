package com.trekmate.app.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "current_tour")
data class CurrentTourEntity(
    @PrimaryKey val tourId: String,
    val groupId: String,
    val leaderId: String,
    val joinCode: String,
    val qrPayload: String,
    val role: String,
    val createdAt: Long
)
