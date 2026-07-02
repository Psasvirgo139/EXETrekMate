package com.trekmate.app.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tour_members")
data class TourMemberEntity(
    @PrimaryKey val userId: String,
    val tourId: String,
    val isLeader: Boolean
)
