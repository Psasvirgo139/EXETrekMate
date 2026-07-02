package com.trekmate.app.core.model

data class TourMember(
    val userId: String,
    val tourId: String,
    val isLeader: Boolean
)
