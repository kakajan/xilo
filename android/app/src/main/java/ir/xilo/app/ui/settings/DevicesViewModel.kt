package ir.xilo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.data.remote.dto.SessionResponse
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.util.ErrorMessageResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DevicesUiState(
    val sessions: List<SessionResponse> = emptyList(),
    val isLoading: Boolean = false,
    val currentSessionRevoked: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.listSessions()
                .onSuccess { sessions ->
                    _uiState.update { it.copy(isLoading = false, sessions = sessions) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = errorMessageResolver.fromThrowable(e, R.string.error_unknown)
                        )
                    }
                }
        }
    }

    fun revokeSession(session: SessionResponse) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.revokeSession(session.id)
                .onSuccess {
                    if (session.isCurrent) {
                        authRepository.logout()
                        _uiState.update {
                            it.copy(isLoading = false, currentSessionRevoked = true)
                        }
                    } else {
                        refresh()
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = errorMessageResolver.fromThrowable(e, R.string.error_unknown)
                        )
                    }
                }
        }
    }

    fun resetCurrentSessionRevoked() {
        _uiState.update { it.copy(currentSessionRevoked = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
