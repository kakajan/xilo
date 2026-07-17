package ir.xilo.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.xilo.app.core.util.canCreatePost
import ir.xilo.app.data.NetworkMonitor
import ir.xilo.app.data.remote.websocket.WebSocketManager
import ir.xilo.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val webSocketManager: WebSocketManager,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val isAuthenticated: StateFlow<Boolean> = authRepository.isAuthenticatedFlow
    val onboardingCompleted: StateFlow<Boolean> = authRepository.onboardingCompletedFlow

    private val _currentUsername = MutableStateFlow(authRepository.getUsername())
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()

    private val _canCreatePost = MutableStateFlow(canCreatePost(authRepository.getRole()))
    val canCreatePost: StateFlow<Boolean> = _canCreatePost.asStateFlow()

    private val _pendingTab = MutableStateFlow<Int?>(null)
    val pendingTab: StateFlow<Int?> = _pendingTab.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun requestTab(index: Int) {
        _pendingTab.value = index
    }

    fun consumePendingTab() {
        _pendingTab.value = null
    }

    fun completeOnboarding() {
        authRepository.completeOnboarding()
    }

    init {
        refreshUsername()
        refreshPermissions()
        if (authRepository.isAuthenticated()) {
            connectRealtime()
        }
    }

    fun updateAuthStatus() {
        if (!authRepository.isAuthenticated()) {
            _currentUsername.value = null
            _canCreatePost.value = false
            webSocketManager.disconnect()
            return
        }
        refreshUsername()
        refreshPermissions()
        connectRealtime()
    }

    /** Re-read persisted username (and hydrate from Room /me when prefs are empty). */
    fun refreshUsername() {
        val cached = authRepository.getUsername()?.takeIf { it.isNotBlank() }
        if (cached != null) {
            _currentUsername.value = cached
            return
        }
        if (!authRepository.isAuthenticated()) {
            _currentUsername.value = null
            return
        }
        viewModelScope.launch {
            val username = authRepository.getLocalProfile()?.username
                ?: runCatching { authRepository.getMeUsername() }.getOrNull()
            _currentUsername.value = username?.takeIf { it.isNotBlank() }
            refreshPermissions()
        }
    }

    fun refreshPermissions() {
        val role = authRepository.getRole()
        _canCreatePost.value = canCreatePost(role)
        if (role != null || !authRepository.isAuthenticated()) return
        viewModelScope.launch {
            runCatching { authRepository.refreshMe() }
            _canCreatePost.value = canCreatePost(authRepository.getRole())
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            updateAuthStatus()
        }
    }

    private fun connectRealtime() {
        viewModelScope.launch {
            webSocketManager.connect()
            authRepository.getUserId()?.let { userId ->
                webSocketManager.subscribeToUser(userId)
            }
        }
    }
}
