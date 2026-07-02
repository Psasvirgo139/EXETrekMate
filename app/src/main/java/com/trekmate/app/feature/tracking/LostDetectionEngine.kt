package com.trekmate.app.feature.tracking

import com.trekmate.app.core.model.TourRole
import javax.inject.Inject
import javax.inject.Singleton

interface LostDetectionEngine {
    fun evaluate(input: LostDetectionInput): LostDetectionResult
}

@Singleton
class LostDetectionEngineImpl @Inject constructor() : LostDetectionEngine {

    override fun evaluate(input: LostDetectionInput): LostDetectionResult {
        if (input.members.isEmpty()) {
            return LostDetectionResult(
                isPossiblyLostFromLeader = false,
                lostMembers = emptyList(),
                presentMembers = emptyList()
            )
        }

        val presenceMap = input.presenceList.associateBy { it.userId }

        return when (input.role) {
            TourRole.MEMBER -> evaluateAsMember(input, presenceMap)
            TourRole.LEADER -> evaluateAsLeader(input, presenceMap)
        }
    }

    private fun evaluateAsMember(
        input: LostDetectionInput,
        presenceMap: Map<String, com.trekmate.app.core.model.MemberPresence>
    ): LostDetectionResult {
        val leaderPresence = presenceMap[input.leaderId]
        val isPossiblyLost = when {
            leaderPresence == null -> false  // haven't started tracking yet
            leaderPresence.lastSeenAt == null -> false
            else -> (input.nowMs - leaderPresence.lastSeenAt) >= input.thresholdMs
        }
        return LostDetectionResult(
            isPossiblyLostFromLeader = isPossiblyLost,
            lostMembers = emptyList(),
            presentMembers = emptyList()
        )
    }

    private fun evaluateAsLeader(
        input: LostDetectionInput,
        presenceMap: Map<String, com.trekmate.app.core.model.MemberPresence>
    ): LostDetectionResult {
        val memberStates = input.members
            .filter { it != input.currentUserId }
            .map { userId ->
                val presence = presenceMap[userId]
                val lastSeen = presence?.lastSeenAt
                val isLost = when {
                    lastSeen == null -> false  // never seen yet — not lost until tracking began
                    else -> (input.nowMs - lastSeen) >= input.thresholdMs
                }
                MemberLostState(userId = userId, isLost = isLost, lastSeenAt = lastSeen)
            }

        return LostDetectionResult(
            isPossiblyLostFromLeader = false,
            lostMembers = memberStates.filter { it.isLost },
            presentMembers = memberStates.filter { !it.isLost }
        )
    }
}
