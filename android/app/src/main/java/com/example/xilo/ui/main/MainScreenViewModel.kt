package com.example.xilo.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xilo.data.NetworkMonitor
import com.example.xilo.data.remote.websocket.WebSocketManager
import com.example.xilo.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val webSocketManager: WebSocketManager,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _isAuthenticated = MutableStateFlow(authRepository.isAuthenticated())
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _currentUsername = MutableStateFlow(authRepository.getUsername())
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        if (authRepository.isAuthenticated()) {
            connectRealtime()
        }
    }

    fun updateAuthStatus() {
        _isAuthenticated.value = authRepository.isAuthenticated()
        _currentUsername.value = authRepository.getUsername()
        if (_isAuthenticated.value) {
            connectRealtime()
        } else {
            webSocketManager.disconnect()
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
