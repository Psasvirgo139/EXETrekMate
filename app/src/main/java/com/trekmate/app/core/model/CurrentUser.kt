package com.trekmate.app.core.model

data class CurrentUser(
    val userId: String,
    val displayName: String?,
    val createdAt: Long
)
