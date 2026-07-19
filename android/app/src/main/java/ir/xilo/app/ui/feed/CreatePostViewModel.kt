package ir.xilo.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.xilo.app.R
import ir.xilo.app.core.util.HashtagParser
import ir.xilo.app.core.util.canCreatePost
import ir.xilo.app.data.local.prefs.ComposeDraftStore
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.TagSuggestion
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.PostRepository
import ir.xilo.app.ui.components.PostField
import ir.xilo.app.ui.postdetail.extractPlainText
import ir.xilo.app.util.ErrorMessageResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val apiService: XiloApiService,
    private val errorMessageResolver: ErrorMessageResolver,
    private val composeDraftStore: ComposeDraftStore,
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

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _editPostId = MutableStateFlow<String?>(null)
    val editPostId: StateFlow<String?> = _editPostId.asStateFlow()

    private val _isLoadingEdit = MutableStateFlow(false)
    val isLoadingEdit: StateFlow<Boolean> = _isLoadingEdit.asStateFlow()

    private val _tagSuggestions = MutableStateFlow<List<TagSuggestion>>(emptyList())
    val tagSuggestions: StateFlow<List<TagSuggestion>> = _tagSuggestions.asStateFlow()

    private var suggestJob: Job? = null
    private var draftSaveJob: Job? = null
    private var draftKey: String = ComposeDraftStore.KEY_NEW
    private var restoreDoneForKey: String? = null

    /** Exclusive end index of the active `#query` in [content]. */
    private var activeHashtagFrom: Int = -1
    private var activeHashtagTo: Int = -1

    init {
        viewModelScope.launch {
            if (!canCreatePost(authRepository.getRole())) {
                runCatching { authRepository.refreshMe() }
            }
            _allowed.value = canCreatePost(authRepository.getRole())
        }
    }

    /** Resets compose/edit state whenever the screen is (re)opened. */
    fun prepare(editPostId: String?) {
        _success.value = false
        _error.value = null
        _fieldErrors.value = emptyMap()
        _isSubmitting.value = false
        _isLoadingEdit.value = false
        draftSaveJob?.cancel()
        draftKey = composeDraftStore.draftKey(editPostId)
        if (editPostId.isNullOrBlank()) {
            _editPostId.value = null
            restoreLocalDraft(force = restoreDoneForKey != draftKey)
        } else {
            loadForEdit(editPostId, force = true)
        }
    }

    fun loadForEdit(postId: String, force: Boolean = false) {
        if (!force &&
            _editPostId.value == postId &&
            (_title.value.isNotBlank() || _content.value.isNotBlank())
        ) {
            return
        }
        _editPostId.value = postId
        draftKey = composeDraftStore.draftKey(postId)
        _success.value = false
        viewModelScope.launch {
            _isLoadingEdit.value = true
            // Local cache first; API GetBySlug also accepts post id as a fallback.
            val post = postRepository.getPostById(postId)
                ?: postRepository.getPostBySlug(postId).getOrNull()
            val local = composeDraftStore.load(draftKey)
            if (local != null) {
                _title.value = local.title
                _content.value = local.content
                restoreDoneForKey = draftKey
            } else if (post != null) {
                _title.value = post.title
                _content.value = extractPlainText(post.content).ifBlank {
                    post.excerpt.orEmpty()
                }
                restoreDoneForKey = draftKey
            } else {
                _error.value = errorMessageResolver.string(R.string.error_load_post)
            }
            _isLoadingEdit.value = false
        }
    }

    fun consumeSuccess() {
        _success.value = false
    }

    fun updateTitle(value: String) {
        _title.value = value
        clearFieldError(PostField.Title)
        scheduleDraftSave()
    }

    fun updateContent(value: String) {
        _content.value = value
        clearFieldError(PostField.Content)
        refreshHashtagSuggestions(value, value.length)
        scheduleDraftSave()
    }

    fun refreshHashtagSuggestions(text: String, cursor: Int) {
        val active = HashtagParser.activeQuery(text, cursor)
        if (active == null) {
            activeHashtagFrom = -1
            activeHashtagTo = -1
            _tagSuggestions.value = emptyList()
            suggestJob?.cancel()
            return
        }
        val (query, from, to) = active
        activeHashtagFrom = from
        activeHashtagTo = to
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(200)
            try {
                val res = apiService.suggestTags(query = query, limit = 8)
                _tagSuggestions.value = res.data
            } catch (_: Exception) {
                _tagSuggestions.value = emptyList()
            }
        }
    }

    fun applyTagSuggestion(tag: String) {
        if (activeHashtagFrom < 0 || activeHashtagTo < activeHashtagFrom) return
        val current = _content.value
        if (activeHashtagTo > current.length) return
        _content.value = current.replaceRange(activeHashtagFrom, activeHashtagTo, "#$tag ")
        activeHashtagFrom = -1
        activeHashtagTo = -1
        _tagSuggestions.value = emptyList()
        scheduleDraftSave()
    }

    fun submit() {
        val editingId = _editPostId.value
        if (editingId != null) {
            updatePost(editingId, _title.value, _content.value)
        } else {
            createPost(_title.value, _content.value)
        }
    }

    fun createPost(title: String, content: String) {
        if (!canCreatePost(authRepository.getRole())) {
            _error.value = errorMessageResolver.string(R.string.error_create_post_forbidden)
            return
        }
        val errors = validate(title, content)
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
                    clearLocalDraft()
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

    private fun updatePost(postId: String, title: String, content: String) {
        if (!canCreatePost(authRepository.getRole())) {
            _error.value = errorMessageResolver.string(R.string.error_create_post_forbidden)
            return
        }
        val errors = validate(title, content)
        if (errors.isNotEmpty()) {
            _fieldErrors.value = errors
            _error.value = null
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null
            _fieldErrors.value = emptyMap()

            postRepository.updatePost(postId, title, content)
                .onSuccess {
                    clearLocalDraft()
                    _success.value = true
                    postRepository.refreshFeed()
                }
                .onFailure { e ->
                    val parsed = errorMessageResolver.parseFormErrors(e, R.string.error_update_post)
                    val mappedErrors = parsed.fieldErrors.toMutableMap()
                    parsed.fieldErrors["text"]?.let { mappedErrors[PostField.Content] = it }
                    _fieldErrors.value = mappedErrors
                    _error.value = parsed.generalError?.takeIf { mappedErrors.isEmpty() }
                }

            _isSubmitting.value = false
        }
    }

    private fun validate(title: String, content: String): Map<String, String> = buildMap {
        if (title.isBlank()) {
            put(PostField.Title, errorMessageResolver.string(R.string.validation_title_required))
        }
        if (content.isBlank()) {
            put(PostField.Content, errorMessageResolver.string(R.string.validation_content_required))
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

    override fun onCleared() {
        flushDraftNow()
        super.onCleared()
    }

    private fun restoreLocalDraft(force: Boolean) {
        if (!force && restoreDoneForKey == draftKey) return
        val draft = composeDraftStore.load(draftKey)
        _title.value = draft?.title.orEmpty()
        _content.value = draft?.content.orEmpty()
        restoreDoneForKey = draftKey
    }

    private fun scheduleDraftSave() {
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(DRAFT_DEBOUNCE_MS)
            composeDraftStore.save(
                title = _title.value,
                content = _content.value,
                key = draftKey,
            )
        }
    }

    private fun flushDraftNow() {
        draftSaveJob?.cancel()
        composeDraftStore.save(
            title = _title.value,
            content = _content.value,
            key = draftKey,
        )
    }

    private fun clearLocalDraft() {
        draftSaveJob?.cancel()
        composeDraftStore.clear(draftKey)
        restoreDoneForKey = null
        _title.value = ""
        _content.value = ""
    }

    private companion object {
        const val DRAFT_DEBOUNCE_MS = 800L
    }
}
