package com.trekmate.app.core.model

data class MemberPresence(
    val userId: String,
    val lastRssi: Int?,
    val lastSeenAt: Long?,
    val isRecentlySeen: Boolean
)
