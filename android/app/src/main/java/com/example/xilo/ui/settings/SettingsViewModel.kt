package com.example.xilo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xilo.R
import com.example.xilo.data.repository.AuthRepository
import com.example.xilo.util.ErrorMessageResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val displayName: String = "کاربر",
    val username: String = "user",
    val phone: String = "+98 --- --- ----",
    val avatarUrl: String? = null,
    val isLoading: Boolean = false,
    val logoutComplete: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val profile = authRepository.getLocalProfile()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    displayName = profile?.displayName ?: profile?.username ?: authRepository.getUsername() ?: "کاربر",
                    username = profile?.username ?: authRepository.getUsername() ?: "user",
                    avatarUrl = profile?.avatarUrl
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                authRepository.logout()
                _uiState.update { it.copy(isLoading = false, logoutComplete = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = errorMessageResolver.fromThrowable(e, R.string.error_logout)
                    )
                }
            }
        }
    }

    fun resetLogoutFlag() {
        _uiState.update { it.copy(logoutComplete = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
