package ir.xilo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.core.util.CalendarPreference
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.util.ErrorMessageResolver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val displayName: String = "کاربر",
    val username: String = "user",
    val phone: String = "",
    val avatarUrl: String? = null,
    val preferredCalendar: CalendarPreference = CalendarPreference.AUTO,
    val isLoading: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val logoutComplete: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

sealed interface SettingsNavEvent {
    data object MyProfile : SettingsNavEvent
    data object SavedMessages : SettingsNavEvent
    data object Devices : SettingsNavEvent
    data object ChatFolders : SettingsNavEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<SettingsNavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<SettingsNavEvent> = _navEvents.asSharedFlow()

    init {
        loadProfile()
        viewModelScope.launch { authRepository.syncCalendarDefaults() }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    preferredCalendar = authRepository.getPreferredCalendar()
                )
            }
            val local = authRepository.getLocalProfile()
            applyProfile(
                displayName = local?.displayName ?: local?.username ?: authRepository.getUsername() ?: "کاربر",
                username = local?.username ?: authRepository.getUsername() ?: "user",
                phone = local?.phone.orEmpty(),
                avatarUrl = local?.avatarUrl,
                preferredCalendar = authRepository.getPreferredCalendar(),
                loading = true
            )
            authRepository.refreshMe()
                .onSuccess { me ->
                    applyProfile(
                        displayName = me.displayName ?: me.username,
                        username = me.username,
                        phone = me.phone.orEmpty(),
                        avatarUrl = me.avatarUrl,
                        preferredCalendar = CalendarPreference.fromApi(me.preferredCalendar),
                        loading = false
                    )
                }
                .onFailure {
                    _uiState.update { state -> state.copy(isLoading = false) }
                }
        }
    }

    private fun applyProfile(
        displayName: String,
        username: String,
        phone: String,
        avatarUrl: String?,
        preferredCalendar: CalendarPreference,
        loading: Boolean
    ) {
        _uiState.update {
            it.copy(
                isLoading = loading,
                displayName = displayName.ifBlank { "کاربر" },
                username = username.ifBlank { "user" },
                phone = formatPhone(phone),
                avatarUrl = avatarUrl,
                preferredCalendar = preferredCalendar
            )
        }
    }

    fun updatePreferredCalendar(preference: CalendarPreference) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.updatePreferredCalendar(preference)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            preferredCalendar = preference,
                            infoMessage = "تقویم به‌روزرسانی شد"
                        )
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

    private fun formatPhone(phone: String): String {
        if (phone.isBlank()) return ""
        return phone
    }

    fun onChangePhoto(imageBytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAvatar = true, errorMessage = null) }
            authRepository.uploadAndSetAvatar(imageBytes, mimeType = "image/png")
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            isUploadingAvatar = false,
                            avatarUrl = user.avatarUrl,
                            displayName = user.displayName ?: user.username,
                            username = user.username,
                            phone = formatPhone(user.phone.orEmpty()),
                            infoMessage = "عکس پروفایل به‌روزرسانی شد"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isUploadingAvatar = false,
                            errorMessage = errorMessageResolver.fromThrowable(e, R.string.error_unknown)
                        )
                    }
                }
        }
    }

    fun onMyProfile() {
        viewModelScope.launch { _navEvents.emit(SettingsNavEvent.MyProfile) }
    }

    fun onWalletComingSoon() {
        _uiState.update { it.copy(infoMessage = "به‌زودی") }
    }

    fun onSavedMessages() {
        viewModelScope.launch { _navEvents.emit(SettingsNavEvent.SavedMessages) }
    }

    fun onDevices() {
        viewModelScope.launch { _navEvents.emit(SettingsNavEvent.Devices) }
    }

    fun onChatFolders() {
        viewModelScope.launch { _navEvents.emit(SettingsNavEvent.ChatFolders) }
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

    fun clearInfo() {
        _uiState.update { it.copy(infoMessage = null) }
    }
}
