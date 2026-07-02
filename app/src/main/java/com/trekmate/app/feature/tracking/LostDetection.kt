package com.trekmate.app.feature.tracking

import com.trekmate.app.core.model.MemberPresence
import com.trekmate.app.core.model.TourRole

data class LostDetectionInput(
    val currentUserId: String,
    val leaderId: String,
    val role: TourRole,
    val members: List<String>,
    val presenceList: List<MemberPresence>,
    val nowMs: Long,
    val thresholdMs: Long = LOST_THRESHOLD_MS
) {
    companion object {
        const val LOST_THRESHOLD_MS = 60_000L
    }
}

data class MemberLostState(
    val userId: String,
    val isLost: Boolean,
    val lastSeenAt: Long?
)

data class LostDetectionResult(
    val isPossiblyLostFromLeader: Boolean,
    val lostMembers: List<MemberLostState>,
    val presentMembers: List<MemberLostState>
)
