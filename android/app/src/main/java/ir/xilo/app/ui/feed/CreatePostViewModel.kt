package ir.xilo.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.xilo.app.R
import ir.xilo.app.core.util.canCreatePost
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.PostRepository
import ir.xilo.app.ui.components.PostField
import ir.xilo.app.util.ErrorMessageResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _fieldErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val fieldErrors: StateFlow<Map<String, String>> = _fieldErrors.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    private val _allowed = MutableStateFlow<Boolean?>(null)
    val allowed: StateFlow<Boolean?> = _allowed.asStateFlow()

    init {
        viewModelScope.launch {
            if (!canCreatePost(authRepository.getRole())) {
                runCatching { authRepository.refreshMe() }
            }
            _allowed.value = canCreatePost(authRepository.getRole())
        }
    }

    fun createPost(title: String, content: String) {
        if (!canCreatePost(authRepository.getRole())) {
            _error.value = errorMessageResolver.string(R.string.error_create_post_forbidden)
            return
        }
        val errors = buildMap {
            if (title.isBlank()) {
                put(PostField.Title, errorMessageResolver.string(R.string.validation_title_required))
            }
            if (content.isBlank()) {
                put(PostField.Content, errorMessageResolver.string(R.string.validation_content_required))
            }
        }
        if (errors.isNotEmpty()) {
            _fieldErrors.value = errors
            _error.value = null
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null
            _fieldErrors.value = emptyMap()

            postRepository.createPost(title, content)
                .onSuccess {
                    _success.value = true
                    postRepository.refreshFeed()
                }
                .onFailure { e ->
                    val parsed = errorMessageResolver.parseFormErrors(e, R.string.error_create_post)
                    val mappedErrors = parsed.fieldErrors.toMutableMap()
                    parsed.fieldErrors["text"]?.let { mappedErrors[PostField.Content] = it }
                    _fieldErrors.value = mappedErrors
                    _error.value = parsed.generalError?.takeIf { mappedErrors.isEmpty() }
                }

            _isSubmitting.value = false
        }
    }

    fun clearFieldError(field: String) {
        val updated = _fieldErrors.value - field
        _fieldErrors.value = updated
        if (updated.isEmpty()) {
            _error.value = null
        }
    }

    fun clearErrors() {
        _error.value = null
        _fieldErrors.value = emptyMap()
    }
}
