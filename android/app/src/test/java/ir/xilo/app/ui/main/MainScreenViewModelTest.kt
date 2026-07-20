package ir.xilo.app.ui.main

import ir.xilo.app.data.NetworkMonitor
import ir.xilo.app.data.remote.websocket.NotificationRealtimeReconciler
import ir.xilo.app.data.remote.websocket.WebSocketManager
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.NotificationRepository
import ir.xilo.app.data.repository.PushTokenRepository
import io.mockk.coEvery
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
    private val notificationRealtimeReconciler = mockk<NotificationRealtimeReconciler>(relaxed = true)
    private val notificationRepository = mockk<NotificationRepository>(relaxed = true)
    private val pushTokenRepository = mockk<PushTokenRepository>(relaxed = true)
    private val networkMonitor = mockk<NetworkMonitor>()

    @Test
    fun isAuthenticated_reflectsAuthRepository() = runTest {
        every { authRepository.isAuthenticated() } returns true
        every { authRepository.isAuthenticatedFlow } returns MutableStateFlow(true)
        every { authRepository.onboardingCompletedFlow } returns MutableStateFlow(true)
        every { authRepository.getUsername() } returns "testuser"
        every { authRepository.getUserId() } returns "user-1"
        every { networkMonitor.isOnline } returns flowOf(true)
        every { notificationRepository.unreadCount } returns MutableStateFlow(0)
        coEvery { notificationRepository.refreshUnreadCount() } returns Result.success(0)
        coEvery { pushTokenRepository.syncPushToken() } returns Result.success(Unit)
        every { authRepository.getRole() } returns "user"

        val viewModel = MainScreenViewModel(
            authRepository,
            notificationRepository,
            pushTokenRepository,
            webSocketManager,
            notificationRealtimeReconciler,
            networkMonitor,
        )

        assertTrue(viewModel.isAuthenticated.value)
        assertTrue(viewModel.isOnline.value)
        verify { webSocketManager.connect() }
    }

    @Test
    fun updateAuthStatus_disconnectsWhenLoggedOut() = runTest {
        every { authRepository.isAuthenticated() } returns true
        every { authRepository.isAuthenticatedFlow } returns MutableStateFlow(true)
        every { authRepository.onboardingCompletedFlow } returns MutableStateFlow(true)
        every { authRepository.getUsername() } returns "testuser"
        every { authRepository.getUserId() } returns "user-1"
        every { authRepository.getRole() } returns null
        every { authRepository.isUsernamePending() } returns false
        every { networkMonitor.isOnline } returns flowOf(true)
        every { notificationRepository.unreadCount } returns MutableStateFlow(0)
        coEvery { notificationRepository.refreshUnreadCount() } returns Result.success(0)
        coEvery { pushTokenRepository.syncPushToken() } returns Result.success(Unit)

        val viewModel = MainScreenViewModel(
            authRepository,
            notificationRepository,
            pushTokenRepository,
            webSocketManager,
            notificationRealtimeReconciler,
            networkMonitor,
        )

        every { authRepository.isAuthenticated() } returns false
        every { authRepository.getUsername() } returns null
        viewModel.updateAuthStatus()

        verify { webSocketManager.disconnect() }
    }
}
