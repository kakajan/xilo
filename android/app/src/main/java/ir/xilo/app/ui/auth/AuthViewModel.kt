package ir.xilo.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.xilo.app.R
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.ui.components.AuthField
import ir.xilo.app.util.ErrorMessageResolver
import ir.xilo.app.util.InputValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, passwordHash: String) {
        val fieldErrors = buildMap {
            InputValidator.validateEmail(email)?.let {
                put(AuthField.Email, errorMessageResolver.string(it))
            }
            if (passwordHash.isBlank()) {
                put(AuthField.Password, errorMessageResolver.string(R.string.validation_password_required))
            }
        }
        if (fieldErrors.isNotEmpty()) {
            _uiState.value = AuthUiState.Error(fieldErrors = fieldErrors)
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.login(email.trim(), passwordHash)
                .onSuccess {
                    _uiState.value = AuthUiState.Success
                }
                .onFailure { error ->
                    _uiState.value = error.toAuthErrorState(R.string.error_login_failed)
                }
        }
    }

    fun register(email: String, passwordHash: String, displayName: String?) {
        val fieldErrors = buildMap {
            InputValidator.validateEmail(email)?.let {
                put(AuthField.Email, errorMessageResolver.string(it))
            }
            InputValidator.validatePassword(passwordHash)?.let {
                put(AuthField.Password, errorMessageResolver.string(it))
            }
        }
        if (fieldErrors.isNotEmpty()) {
            _uiState.value = AuthUiState.Error(fieldErrors = fieldErrors)
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.register(email, passwordHash, displayName)
                .onSuccess {
                    _uiState.value = AuthUiState.Success
                }
                .onFailure { error ->
                    _uiState.value = error.toAuthErrorState(R.string.error_registration_failed)
                }
        }
    }

    fun requestOtp(phone: String) {
        if (phone.isBlank()) {
            _uiState.value = AuthUiState.Error(generalError = "شماره موبایل الزامی است")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.requestOtp(phone)
                .onSuccess {
                    _uiState.value = AuthUiState.OtpSent
                }
                .onFailure { error ->
                    if (isSmsUnavailable(error)) {
                        _uiState.value = AuthUiState.Error(
                            generalError = errorMessageResolver.string(R.string.error_otp_sms_unavailable),
                        )
                    } else {
                        _uiState.value = error.toAuthErrorState(R.string.error_login_failed)
                    }
                }
        }
    }

    private fun isSmsUnavailable(error: Throwable): Boolean {
        val haystack = buildString {
            var current: Throwable? = error
            while (current != null) {
                append(current.message.orEmpty())
                append(' ')
                current = current.cause
            }
        }.lowercase()
        return haystack.contains("sms") ||
            haystack.contains("not initialized") ||
            haystack.contains("unavailable") ||
            haystack.contains("failed to send otp")
    }

    fun verifyOtpLogin(phone: String, code: String) {
        if (phone.isBlank() || code.isBlank()) {
            _uiState.value = AuthUiState.Error(generalError = "شماره موبایل و کد تایید الزامی است")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.verifyOtpLogin(phone, code)
                .onSuccess {
                    _uiState.value = AuthUiState.Success
                }
                .onFailure { error ->
                    _uiState.value = error.toAuthErrorState(R.string.error_login_failed)
                }
        }
    }

    fun clearError() {
        _uiState.value = AuthUiState.Idle
    }

    fun clearFieldError(field: AuthField) {
        val current = _uiState.value as? AuthUiState.Error ?: return
        val updatedErrors = current.fieldErrors - field
        _uiState.value = when {
            updatedErrors.isEmpty() && current.generalError == null -> AuthUiState.Idle
            else -> current.copy(fieldErrors = updatedErrors)
        }
    }

    private fun Throwable.toAuthErrorState(fallbackResId: Int): AuthUiState.Error {
        val parsed = errorMessageResolver.parseFormErrors(this, fallbackResId)
        val fieldErrors = parsed.fieldErrors.mapNotNull { (key, message) ->
            AuthField.fromKey(key)?.let { it to message }
        }.toMap()
        return AuthUiState.Error(
            fieldErrors = fieldErrors,
            generalError = parsed.generalError?.takeIf { fieldErrors.isEmpty() },
        )
    }
}

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    object Success : AuthUiState
    object OtpSent : AuthUiState
    data class Error(
        val fieldErrors: Map<AuthField, String> = emptyMap(),
        val generalError: String? = null,
    ) : AuthUiState
}
