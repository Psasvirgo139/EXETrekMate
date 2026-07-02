package com.trekmate.app

import com.trekmate.app.core.model.CurrentTour
import com.trekmate.app.core.model.CurrentUser
import com.trekmate.app.core.model.TourRole
import com.trekmate.app.core.network.dto.CreateTourResponse
import com.trekmate.app.core.network.dto.JoinTourResponse
import com.trekmate.app.core.network.sse.TourSseClient
import com.trekmate.app.core.network.sse.TourSseEvent
import com.trekmate.app.core.storage.BleObservationStore
import com.trekmate.app.core.storage.MemberStore
import com.trekmate.app.core.storage.TourStore
import com.trekmate.app.core.time.ClockProvider
import com.trekmate.app.feature.auth.AuthRepository
import com.trekmate.app.feature.tour.ApiSyncRepository
import com.trekmate.app.feature.tour.TourRepositoryImpl
import io.mockk.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TourRepositoryTest {

    private val authRepo = mockk<AuthRepository>()
    private val apiSync = mockk<ApiSyncRepository>()
    private val tourStore = mockk<TourStore>(relaxed = true)
    private val memberStore = mockk<MemberStore>(relaxed = true)
    private val bleStore = mockk<BleObservationStore>(relaxed = true)
    private val clock = mockk<ClockProvider>()
    private val sseClient = mockk<TourSseClient> {
        every { eventFlow(any()) } returns emptyFlow()
    }
    private val testScope = TestScope()

    private val repo = TourRepositoryImpl(
        authRepo, apiSync, tourStore, memberStore, bleStore, clock, sseClient, testScope
    )

    private val testUser = CurrentUser("user123", null, 1000L)

    @Test
    fun `createTour success saves tour and leader member`() = runTest {
        coEvery { authRepo.getOrCreateCurrentUser() } returns testUser
        every { clock.currentTimeMillis() } returns 2000L
        coEvery { apiSync.createTour("user123") } returns Result.success(
            CreateTourResponse("tour1", "group1", "JOINABC", "trekmate://join?code=JOINABC", "user123")
        )

        val result = repo.createTour()

        assertTrue(result.isSuccess)
        assertEquals("tour1", result.getOrNull()?.tourId)
        coVerify { tourStore.saveCurrentTour(any()) }
        coVerify { memberStore.replaceMembers(any()) }
    }

    @Test
    fun `createTour API failure does not save`() = runTest {
        coEvery { authRepo.getOrCreateCurrentUser() } returns testUser
        coEvery { apiSync.createTour(any()) } returns Result.failure(RuntimeException("Network error"))

        val result = repo.createTour()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { tourStore.saveCurrentTour(any()) }
    }

    @Test
    fun `joinTour success saves tour and member list`() = runTest {
        coEvery { authRepo.getOrCreateCurrentUser() } returns testUser
        every { clock.currentTimeMillis() } returns 3000L
        coEvery { apiSync.joinTour("user123", "CODE1") } returns Result.success(
            JoinTourResponse("tour2", "group2", "leader1", "CODE1", "trekmate://join?code=CODE1", emptyList())
        )

        val result = repo.joinTour("CODE1")

        assertTrue(result.isSuccess)
        assertEquals("group2", result.getOrNull()?.groupId)
        coVerify { tourStore.saveCurrentTour(any()) }
    }

    @Test
    fun `endTour clears local data on success`() = runTest {
        val activeTour = CurrentTour("tour1", "group1", "user123", "CODE", "qr", TourRole.LEADER, 1000L)
        coEvery { tourStore.getCurrentTour() } returns activeTour
        coEvery { authRepo.getOrCreateCurrentUser() } returns testUser
        coEvery { apiSync.endTour("tour1", "user123") } returns Result.success(Unit)

        val result = repo.endTour()

        assertTrue(result.isSuccess)
        coVerify { tourStore.clearCurrentTour() }
        coVerify { memberStore.clearMembers() }
        coVerify { bleStore.clearObservations() }
    }
}
