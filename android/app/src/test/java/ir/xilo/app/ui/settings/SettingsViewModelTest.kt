package ir.xilo.app.ui.settings

import ir.xilo.app.core.util.CalendarPreference
import ir.xilo.app.data.local.entity.UserEntity
import ir.xilo.app.data.remote.dto.UserResponse
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.ThemeMode
import ir.xilo.app.data.repository.ThemeRepository
import ir.xilo.app.util.ErrorMessageResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val authRepository = mockk<AuthRepository>()
    private val themeRepository = mockk<ThemeRepository>()
    private val errorMessageResolver = mockk<ErrorMessageResolver>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authRepository.getUsername() } returns "alice"
        every { authRepository.getPreferredLanguage() } returns "fa"
        every { authRepository.isUsernamePending() } returns false
        every { authRepository.getPreferredCalendar() } returns CalendarPreference.AUTO
        every { themeRepository.themeMode } returns MutableStateFlow(ThemeMode.SYSTEM)
        coEvery { authRepository.syncCalendarDefaults() } returns Result.success(Unit)
        coEvery { authRepository.getLocalProfile() } returns UserEntity(
            id = "u1",
            username = "alice",
            email = "a@xilo.ir",
            phone = "+98912",
            displayName = "Alice",
            avatarUrl = null,
            bio = null
        )
        coEvery { authRepository.refreshMe() } returns Result.success(
            UserResponse(id = "u1", username = "alice", phone = "+98912", displayName = "Alice")
        )
        every { errorMessageResolver.fromThrowable(any(), any()) } returns "error"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun wallet_showsComingSoonInfo() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(authRepository, themeRepository, errorMessageResolver)
        advanceUntilIdle()

        viewModel.onWalletComingSoon()

        assertEquals("به‌زودی", viewModel.uiState.value.infoMessage)
    }

    @Test
    fun savedMessages_emitsNavEvent() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(authRepository, themeRepository, errorMessageResolver)
        advanceUntilIdle()

        val deferred = async { viewModel.navEvents.first() }
        viewModel.onSavedMessages()
        advanceUntilIdle()

        val event = deferred.await()
        assertTrue(event is SettingsNavEvent.SavedMessages)
    }

    @Test
    fun logout_callsRepository() = runTest(dispatcher) {
        coEvery { authRepository.logout() } returns Unit
        val viewModel = SettingsViewModel(authRepository, themeRepository, errorMessageResolver)
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.logout() }
        assertTrue(viewModel.uiState.value.logoutComplete)
    }

    @Test
    fun myProfile_emitsNavEvent() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(authRepository, themeRepository, errorMessageResolver)
        advanceUntilIdle()

        var event: SettingsNavEvent? = null
        val job = launch { viewModel.navEvents.collect { event = it } }
        viewModel.onMyProfile()
        advanceUntilIdle()
        job.cancel()

        assertEquals(SettingsNavEvent.MyProfile, event)
    }
}
