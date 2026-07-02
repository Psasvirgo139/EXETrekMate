package com.trekmate.app

import com.trekmate.app.core.model.MemberPresence
import com.trekmate.app.core.model.TourRole
import com.trekmate.app.feature.tracking.LostDetectionEngineImpl
import com.trekmate.app.feature.tracking.LostDetectionInput
import org.junit.Assert.*
import org.junit.Test

class LostDetectionEngineTest {

    private val engine = LostDetectionEngineImpl()
    private val threshold = LostDetectionInput.LOST_THRESHOLD_MS

    private fun presence(userId: String, seenAt: Long?) = MemberPresence(
        userId = userId,
        lastRssi = -70,
        lastSeenAt = seenAt,
        isRecentlySeen = false
    )

    @Test
    fun `member does not warn at 59 seconds`() {
        val now = 100_000L
        val input = LostDetectionInput(
            currentUserId = "member1",
            leaderId = "leader1",
            role = TourRole.MEMBER,
            members = listOf("leader1", "member1"),
            presenceList = listOf(presence("leader1", now - 59_000L)),
            nowMs = now
        )
        val result = engine.evaluate(input)
        assertFalse(result.isPossiblyLostFromLeader)
    }

    @Test
    fun `member warns at exactly 60 seconds`() {
        val now = 100_000L
        val input = LostDetectionInput(
            currentUserId = "member1",
            leaderId = "leader1",
            role = TourRole.MEMBER,
            members = listOf("leader1", "member1"),
            presenceList = listOf(presence("leader1", now - 60_000L)),
            nowMs = now
        )
        val result = engine.evaluate(input)
        assertTrue(result.isPossiblyLostFromLeader)
    }

    @Test
    fun `member clears warning after leader is seen again`() {
        val now = 100_000L
        val input = LostDetectionInput(
            currentUserId = "member1",
            leaderId = "leader1",
            role = TourRole.MEMBER,
            members = listOf("leader1", "member1"),
            presenceList = listOf(presence("leader1", now - 5_000L)),
            nowMs = now
        )
        val result = engine.evaluate(input)
        assertFalse(result.isPossiblyLostFromLeader)
    }

    @Test
    fun `leader marks missing member as lost at 60s`() {
        val now = 100_000L
        val input = LostDetectionInput(
            currentUserId = "leader1",
            leaderId = "leader1",
            role = TourRole.LEADER,
            members = listOf("leader1", "member1", "member2"),
            presenceList = listOf(
                presence("member1", now - 65_000L),
                presence("member2", now - 10_000L)
            ),
            nowMs = now
        )
        val result = engine.evaluate(input)
        assertEquals(1, result.lostMembers.size)
        assertEquals("member1", result.lostMembers.first().userId)
        assertEquals(1, result.presentMembers.size)
        assertEquals("member2", result.presentMembers.first().userId)
    }

    @Test
    fun `leader excludes own userId from evaluation`() {
        val now = 100_000L
        val input = LostDetectionInput(
            currentUserId = "leader1",
            leaderId = "leader1",
            role = TourRole.LEADER,
            members = listOf("leader1"),
            presenceList = emptyList(),
            nowMs = now
        )
        val result = engine.evaluate(input)
        assertTrue(result.lostMembers.isEmpty())
        assertTrue(result.presentMembers.isEmpty())
    }

    @Test
    fun `member with no lastSeenAt is not marked lost immediately`() {
        val now = 100_000L
        val input = LostDetectionInput(
            currentUserId = "leader1",
            leaderId = "leader1",
            role = TourRole.LEADER,
            members = listOf("leader1", "member1"),
            presenceList = listOf(presence("member1", null)),
            nowMs = now
        )
        val result = engine.evaluate(input)
        assertEquals(0, result.lostMembers.size)
    }

    @Test
    fun `empty tour produces no lost state`() {
        val input = LostDetectionInput(
            currentUserId = "leader1",
            leaderId = "leader1",
            role = TourRole.LEADER,
            members = emptyList(),
            presenceList = emptyList(),
            nowMs = 100_000L
        )
        val result = engine.evaluate(input)
        assertFalse(result.isPossiblyLostFromLeader)
        assertTrue(result.lostMembers.isEmpty())
    }
}
