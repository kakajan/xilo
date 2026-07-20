package ir.xilo.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.data.repository.AuthRepository
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

data class EditProfileUiState(
    val displayName: String = "",
    val bio: String = "",
    val username: String = "",
    val usernamePending: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val fieldError: String? = null,
)

sealed interface EditProfileEvent {
    data object Saved : EditProfileEvent
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditProfileEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditProfileEvent> = _events.asSharedFlow()

    private var initialUsername: String = ""

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, fieldError = null) }
            val local = authRepository.getLocalProfile()
            initialUsername = local?.username ?: authRepository.getUsername().orEmpty()
            _uiState.update {
                it.copy(
                    displayName = local?.displayName.orEmpty(),
                    bio = local?.bio.orEmpty(),
                    username = if (authRepository.isUsernamePending() || initialUsername.startsWith("tmp_")) {
                        ""
                    } else {
                        initialUsername
                    },
                    usernamePending = authRepository.isUsernamePending() || initialUsername.startsWith("tmp_"),
                    isLoading = false,
                )
            }
            authRepository.refreshMe()
                .onSuccess { me ->
                    initialUsername = me.username
                    val pending = me.usernamePending || me.username.startsWith("tmp_")
                    _uiState.update {
                        it.copy(
                            displayName = me.displayName.orEmpty(),
                            bio = me.bio.orEmpty(),
                            username = if (pending) "" else me.username,
                            usernamePending = pending,
                            isLoading = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state -> state.copy(isLoading = false) }
                }
        }
    }

    fun onDisplayNameChange(value: String) {
        _uiState.update { it.copy(displayName = value, fieldError = null, errorMessage = null) }
    }

    fun onBioChange(value: String) {
        if (value.length > BIO_MAX) return
        _uiState.update { it.copy(bio = value, fieldError = null, errorMessage = null) }
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, fieldError = null, errorMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun save() {
        val state = _uiState.value
        val displayName = state.displayName.trim()
        if (displayName.isEmpty()) {
            _uiState.update {
                it.copy(fieldError = errorMessageResolver.string(R.string.edit_profile_name_required))
            }
            return
        }
        if (displayName.length > DISPLAY_NAME_MAX) {
            _uiState.update {
                it.copy(fieldError = errorMessageResolver.string(R.string.edit_profile_name_too_long))
            }
            return
        }

        val usernameDraft = state.username.trim()
        val usernameChanged = usernameDraft.isNotEmpty() &&
            !usernameDraft.equals(initialUsername, ignoreCase = true)
        val mustSetUsername = state.usernamePending
        if (mustSetUsername || usernameChanged) {
            InputValidator.validateUsername(usernameDraft)?.let { resId ->
                _uiState.update { it.copy(fieldError = errorMessageResolver.string(resId)) }
                return
            }
            if (usernameDraft.startsWith("tmp_")) {
                _uiState.update {
                    it.copy(
                        fieldError = errorMessageResolver.string(R.string.validation_username_reserved_prefix)
                    )
                }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, fieldError = null) }
            val usernameToSend = when {
                mustSetUsername || usernameChanged -> usernameDraft
                else -> null
            }
            authRepository.updateProfileInfo(
                displayName = displayName,
                bio = state.bio.trim(),
                username = usernameToSend,
            )
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(EditProfileEvent.Saved)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = errorMessageResolver.fromThrowable(e, R.string.error_unknown),
                        )
                    }
                }
        }
    }

    companion object {
        private const val DISPLAY_NAME_MAX = 64
        private const val BIO_MAX = 500
    }
}
