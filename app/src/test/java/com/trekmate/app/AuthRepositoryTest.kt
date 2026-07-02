package com.trekmate.app

import com.trekmate.app.core.model.CurrentUser
import com.trekmate.app.core.storage.UserPreferencesDataStore
import com.trekmate.app.core.time.ClockProvider
import com.trekmate.app.feature.auth.AuthRepositoryImpl
import com.trekmate.app.feature.auth.UserIdGenerator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    private val store = mockk<UserPreferencesDataStore>(relaxed = true)
    private val idGenerator = mockk<UserIdGenerator>()
    private val clock = mockk<ClockProvider>()
    private val repo = AuthRepositoryImpl(store, idGenerator, clock)

    @Test
    fun `getOrCreateCurrentUser returns existing user when stored`() = runTest {
        val existing = CurrentUser("abc123", null, 1000L)
        every { store.observeUser() } returns flowOf(existing)

        val result = repo.getOrCreateCurrentUser()

        assertEquals(existing, result)
        coVerify(exactly = 0) { store.saveUser(any()) }
    }

    @Test
    fun `getOrCreateCurrentUser creates and saves new user when none stored`() = runTest {
        every { store.observeUser() } returns flowOf(null)
        every { idGenerator.generate() } returns "newid1234567890"
        every { clock.currentTimeMillis() } returns 5000L

        val result = repo.getOrCreateCurrentUser()

        assertNotNull(result)
        assertEquals("newid1234567890", result.userId)
        coVerify { store.saveUser(any()) }
    }

    @Test
    fun `generated userId is not blank and has correct length`() {
        val generator = UserIdGenerator()
        val id = generator.generate()
        assertTrue(id.isNotBlank())
        assertEquals(16, id.length)
    }
}
