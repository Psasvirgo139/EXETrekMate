package com.trekmate.app.core.model

data class CurrentTour(
    val tourId: String,
    val groupId: String,
    val leaderId: String,
    val joinCode: String,
    val qrPayload: String,
    val role: TourRole,
    val createdAt: Long
)
