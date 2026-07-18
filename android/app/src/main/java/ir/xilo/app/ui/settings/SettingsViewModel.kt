package ir.xilo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.core.util.CalendarPreference
import ir.xilo.app.data.remote.dto.LanguageInfo
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.ThemeMode
import ir.xilo.app.data.repository.ThemeRepository
import ir.xilo.app.util.ErrorMessageResolver
import ir.xilo.app.util.InputValidator
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
    val usernamePending: Boolean = false,
    val usernameDraft: String = "",
    val phone: String = "",
    val avatarUrl: String? = null,
    val preferredCalendar: CalendarPreference = CalendarPreference.AUTO,
    val preferredLanguage: String = "fa",
    val languages: List<LanguageInfo> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
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
    private val themeRepository: ThemeRepository,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            themeMode = themeRepository.themeMode.value,
            preferredLanguage = authRepository.getPreferredLanguage(),
            usernamePending = authRepository.isUsernamePending(),
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<SettingsNavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<SettingsNavEvent> = _navEvents.asSharedFlow()

    init {
        loadProfile()
        viewModelScope.launch { authRepository.syncCalendarDefaults() }
        viewModelScope.launch {
            themeRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            authRepository.listLanguages()
                .onSuccess { langs ->
                    _uiState.update { it.copy(languages = langs) }
                }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    preferredCalendar = authRepository.getPreferredCalendar(),
                    preferredLanguage = authRepository.getPreferredLanguage(),
                    usernamePending = authRepository.isUsernamePending(),
                )
            }
            val local = authRepository.getLocalProfile()
            applyProfile(
                displayName = local?.displayName ?: local?.username ?: authRepository.getUsername() ?: "کاربر",
                username = local?.username ?: authRepository.getUsername() ?: "user",
                usernamePending = authRepository.isUsernamePending(),
                phone = local?.phone.orEmpty(),
                avatarUrl = local?.avatarUrl,
                preferredCalendar = authRepository.getPreferredCalendar(),
                preferredLanguage = authRepository.getPreferredLanguage(),
                loading = true
            )
            authRepository.refreshMe()
                .onSuccess { me ->
                    applyProfile(
                        displayName = me.displayName ?: me.username,
                        username = me.username,
                        usernamePending = me.usernamePending || me.username.startsWith("tmp_"),
                        phone = me.phone.orEmpty(),
                        avatarUrl = me.avatarUrl,
                        preferredCalendar = CalendarPreference.fromApi(me.preferredCalendar),
                        preferredLanguage = me.preferredLanguage?.takeIf { it.isNotBlank() }
                            ?: authRepository.getPreferredLanguage(),
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
        usernamePending: Boolean,
        phone: String,
        avatarUrl: String?,
        preferredCalendar: CalendarPreference,
        preferredLanguage: String,
        loading: Boolean
    ) {
        _uiState.update {
            it.copy(
                isLoading = loading,
                displayName = displayName.ifBlank { "کاربر" },
                username = username.ifBlank { "user" },
                usernamePending = usernamePending,
                usernameDraft = if (usernamePending) "" else username,
                phone = formatPhone(phone),
                avatarUrl = avatarUrl,
                preferredCalendar = preferredCalendar,
                preferredLanguage = preferredLanguage,
            )
        }
    }

    fun onUsernameDraftChange(value: String) {
        _uiState.update { it.copy(usernameDraft = value, errorMessage = null) }
    }

    fun updateUsername() {
        val draft = _uiState.value.usernameDraft.trim()
        InputValidator.validateUsername(draft)?.let { resId ->
            _uiState.update {
                it.copy(errorMessage = errorMessageResolver.string(resId))
            }
            return
        }
        if (draft.startsWith("tmp_")) {
            _uiState.update { it.copy(errorMessage = "این پیشوند رزرو شده است") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.updateUsername(draft)
                .onSuccess { user ->
                    applyProfile(
                        displayName = user.displayName ?: user.username,
                        username = user.username,
                        usernamePending = user.usernamePending || user.username.startsWith("tmp_"),
                        phone = user.phone.orEmpty(),
                        avatarUrl = user.avatarUrl,
                        preferredCalendar = CalendarPreference.fromApi(user.preferredCalendar),
                        preferredLanguage = user.preferredLanguage?.takeIf { it.isNotBlank() }
                            ?: _uiState.value.preferredLanguage,
                        loading = false
                    )
                    _uiState.update { it.copy(infoMessage = "نام کاربری ذخیره شد") }
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

    fun updatePreferredLanguage(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.updatePreferredLanguage(code)
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            preferredLanguage = user.preferredLanguage?.takeIf { lang -> lang.isNotBlank() }
                                ?: code,
                            infoMessage = "زبان رابط ذخیره شد"
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

    fun updateThemeMode(mode: ThemeMode) {
        themeRepository.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode, infoMessage = "حالت نمایش به‌روزرسانی شد") }
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
                            usernamePending = user.usernamePending || user.username.startsWith("tmp_"),
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
