package ir.xilo.app.ui.main

import ir.xilo.app.data.NetworkMonitor
import ir.xilo.app.data.remote.websocket.WebSocketManager
import ir.xilo.app.data.repository.AuthRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenViewModelTest {

    private val authRepository = mockk<AuthRepository>()
    private val webSocketManager = mockk<WebSocketManager>(relaxed = true)
    private val networkMonitor = mockk<NetworkMonitor>()

    @Test
    fun isAuthenticated_reflectsAuthRepository() = runTest {
        every { authRepository.isAuthenticated() } returns true
        every { authRepository.isAuthenticatedFlow } returns MutableStateFlow(true)
        every { authRepository.onboardingCompletedFlow } returns MutableStateFlow(true)
        every { authRepository.getUsername() } returns "testuser"
        every { authRepository.getUserId() } returns "user-1"
        every { networkMonitor.isOnline } returns flowOf(true)

        val viewModel = MainScreenViewModel(authRepository, webSocketManager, networkMonitor)

        assertTrue(viewModel.isAuthenticated.value)
        assertTrue(viewModel.isOnline.value)
        verify { webSocketManager.connect() }
    }

    @Test
    fun updateAuthStatus_disconnectsWhenLoggedOut() = runTest {
        every { authRepository.isAuthenticated() } returnsMany listOf(true, false)
        every { authRepository.isAuthenticatedFlow } returns MutableStateFlow(true)
        every { authRepository.onboardingCompletedFlow } returns MutableStateFlow(true)
        every { authRepository.getUsername() } returnsMany listOf("testuser", null)
        every { authRepository.getUserId() } returns "user-1"
        every { networkMonitor.isOnline } returns flowOf(true)

        val viewModel = MainScreenViewModel(authRepository, webSocketManager, networkMonitor)
        viewModel.updateAuthStatus()

        verify { webSocketManager.disconnect() }
    }
}
