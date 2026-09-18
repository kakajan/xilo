package ir.xilo.app.ui.feed

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.xilo.app.R
import ir.xilo.app.core.util.HashtagParser
import ir.xilo.app.core.util.canCreatePost
import ir.xilo.app.data.local.entity.CommentEntity
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.local.prefs.ComposeDraftStore
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.PostSearchHit
import ir.xilo.app.data.remote.dto.TagSuggestion
import ir.xilo.app.data.remote.dto.decodePostMedia
import ir.xilo.app.data.remote.dto.tags
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.CommentRepository
import ir.xilo.app.data.repository.PostRepository
import ir.xilo.app.ui.components.PostField
import ir.xilo.app.ui.postdetail.extractPlainText
import ir.xilo.app.util.ErrorMessageResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

data class AttachedMedia(val id: String, val url: String)

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val authRepository: AuthRepository,
    private val apiService: XiloApiService,
    private val errorMessageResolver: ErrorMessageResolver,
    private val composeDraftStore: ComposeDraftStore,
    @ApplicationContext private val context: Context,
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

    private val _audioUrl = MutableStateFlow("")
    val audioUrl: StateFlow<String> = _audioUrl.asStateFlow()

    private val _coverImageUrl = MutableStateFlow("")
    val coverImageUrl: StateFlow<String> = _coverImageUrl.asStateFlow()

    private val _linkUrl = MutableStateFlow("")
    val linkUrl: StateFlow<String> = _linkUrl.asStateFlow()

    private val _photoMedia = MutableStateFlow<List<AttachedMedia>>(emptyList())
    val photoMedia: StateFlow<List<AttachedMedia>> = _photoMedia.asStateFlow()

    private val _videoMedia = MutableStateFlow<AttachedMedia?>(null)
    val videoMedia: StateFlow<AttachedMedia?> = _videoMedia.asStateFlow()

    private val _isUploadingCover = MutableStateFlow(false)
    val isUploadingCover: StateFlow<Boolean> = _isUploadingCover.asStateFlow()

    private val _isUploadingPhoto = MutableStateFlow(false)
    val isUploadingPhoto: StateFlow<Boolean> = _isUploadingPhoto.asStateFlow()

    private val _isUploadingVideo = MutableStateFlow(false)
    val isUploadingVideo: StateFlow<Boolean> = _isUploadingVideo.asStateFlow()

    private val _scheduledAtEpoch = MutableStateFlow<Long?>(null)
    val scheduledAtEpoch: StateFlow<Long?> = _scheduledAtEpoch.asStateFlow()

    private val _isUploadingAudio = MutableStateFlow(false)
    val isUploadingAudio: StateFlow<Boolean> = _isUploadingAudio.asStateFlow()

    private val _editPostId = MutableStateFlow<String?>(null)
    val editPostId: StateFlow<String?> = _editPostId.asStateFlow()

    private val _isLoadingEdit = MutableStateFlow(false)
    val isLoadingEdit: StateFlow<Boolean> = _isLoadingEdit.asStateFlow()

    private val _tagSuggestions = MutableStateFlow<List<TagSuggestion>>(emptyList())
    val tagSuggestions: StateFlow<List<TagSuggestion>> = _tagSuggestions.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _tagInput = MutableStateFlow("")
    val tagInput: StateFlow<String> = _tagInput.asStateFlow()

    private val _tagInputSuggestions = MutableStateFlow<List<TagSuggestion>>(emptyList())
    val tagInputSuggestions: StateFlow<List<TagSuggestion>> = _tagInputSuggestions.asStateFlow()

    private val _quotedPost = MutableStateFlow<PostEntity?>(null)
    val quotedPost: StateFlow<PostEntity?> = _quotedPost.asStateFlow()

    private val _quotedComment = MutableStateFlow<CommentEntity?>(null)
    val quotedComment: StateFlow<CommentEntity?> = _quotedComment.asStateFlow()

    private val _quotedCommentPostTitle = MutableStateFlow<String?>(null)
    val quotedCommentPostTitle: StateFlow<String?> = _quotedCommentPostTitle.asStateFlow()

    private val _composeKind = MutableStateFlow(ComposeKind.ARTICLE)
    val composeKind: StateFlow<String> = _composeKind.asStateFlow()

    private val _quoteQuery = MutableStateFlow("")
    val quoteQuery: StateFlow<String> = _quoteQuery.asStateFlow()

    private val _quoteResults = MutableStateFlow<List<PostSearchHit>>(emptyList())
    val quoteResults: StateFlow<List<PostSearchHit>> = _quoteResults.asStateFlow()

    private val _isSearchingQuote = MutableStateFlow(false)
    val isSearchingQuote: StateFlow<Boolean> = _isSearchingQuote.asStateFlow()

    private var quotedPostId: String? = null
    private var quotedCommentId: String? = null
    private var audioCleared: Boolean = false
    private var suggestJob: Job? = null
    private var tagInputSuggestJob: Job? = null
    private var quoteSearchJob: Job? = null
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
    fun prepare(
        editPostId: String?,
        quotedPostId: String? = null,
        quotedCommentId: String? = null,
        composeKind: String = ComposeKind.ARTICLE,
    ) {
        _success.value = false
        _error.value = null
        _fieldErrors.value = emptyMap()
        _isSubmitting.value = false
        _isLoadingEdit.value = false
        draftSaveJob?.cancel()
        this.quotedCommentId = quotedCommentId?.takeIf { it.isNotBlank() }
        this.quotedPostId = quotedPostId?.takeIf { it.isNotBlank() }
            ?.takeIf { this.quotedCommentId == null }
        _quotedPost.value = null
        _quotedComment.value = null
        _quotedCommentPostTitle.value = null
        _quoteQuery.value = ""
        _quoteResults.value = emptyList()
        audioCleared = false
        val initialKind = when {
            !this.quotedCommentId.isNullOrBlank() || !this.quotedPostId.isNullOrBlank() -> ComposeKind.QUOTE
            else -> composeKind.ifBlank { ComposeKind.ARTICLE }
        }
        _composeKind.value = initialKind
        draftKey = composeDraftStore.draftKey(
            when {
                !editPostId.isNullOrBlank() -> editPostId
                !this.quotedCommentId.isNullOrBlank() -> "quote-comment-${this.quotedCommentId}"
                !this.quotedPostId.isNullOrBlank() -> "quote-${this.quotedPostId}"
                else -> null
            }
        )
        if (editPostId.isNullOrBlank()) {
            _editPostId.value = null
            restoreLocalDraft(force = restoreDoneForKey != draftKey)
            this.quotedCommentId?.let { loadQuotedComment(it) }
            this.quotedPostId?.let { loadQuotedPost(it) }
        } else {
            loadForEdit(editPostId, force = true)
        }
    }

    private fun loadQuotedPost(postId: String) {
        viewModelScope.launch {
            val post = postRepository.getPostById(postId)
                ?: postRepository.getPostBySlug(postId).getOrNull()
            _quotedPost.value = post
        }
    }

    private fun loadQuotedComment(commentId: String) {
        viewModelScope.launch {
            commentRepository.getCommentById(commentId)
                .onSuccess { detail ->
                    _quotedComment.value = detail.comment
                    _quotedCommentPostTitle.value = detail.postTitle
                        ?: postRepository.getPostById(detail.comment.postId)?.title
                }
                .onFailure {
                    _error.value = errorMessageResolver.fromThrowable(it, R.string.error_load_post)
                }
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
            val post = postRepository.loadPostForEdit(postId)
            val local = composeDraftStore.load(draftKey)
            if (post == null && local == null) {
                _error.value = errorMessageResolver.string(R.string.error_load_post)
                _isLoadingEdit.value = false
                return@launch
            }
            applyLoadedPost(post)
            if (local != null) {
                _title.value = local.title.ifBlank { _title.value }
                _content.value = local.content.ifBlank { _content.value }
                if (local.tags.isNotEmpty()) {
                    _tags.value = local.tags
                }
                if (local.audioUrl.isNotBlank()) {
                    _audioUrl.value = local.audioUrl
                    audioCleared = false
                } else if (_audioUrl.value.isNotBlank()) {
                    // Keep server audio when a stale empty draft would wipe it.
                } else {
                    _audioUrl.value = ""
                }
                if (local.coverImageUrl.isNotBlank()) {
                    _coverImageUrl.value = local.coverImageUrl
                }
            }
            restoreDoneForKey = draftKey
            _isLoadingEdit.value = false
        }
    }

    private fun applyLoadedPost(post: PostEntity?) {
        if (post == null) return
        _title.value = post.title
        _content.value = extractPlainText(post.content).ifBlank {
            post.excerpt.orEmpty()
        }
        _tags.value = post.tags
        _audioUrl.value = post.audioUrl.orEmpty()
        audioCleared = false
        _coverImageUrl.value = post.coverImageUrl.orEmpty()
        _linkUrl.value = post.linkUrl.orEmpty()
        quotedPostId = post.quotedPostId
        quotedCommentId = post.quotedCommentId
        _composeKind.value = ComposeKind.fromPost(
            postType = post.postType,
            audioUrl = post.audioUrl,
            quotedPostId = post.quotedPostId,
            quotedCommentId = post.quotedCommentId,
        )
        val media = decodePostMedia(post.mediaJson).mapNotNull { item ->
            val id = item.id.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = item.url.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AttachedMedia(id = id, url = url)
        }
        when (_composeKind.value) {
            ComposeKind.PHOTO -> {
                _photoMedia.value = media
                _videoMedia.value = null
            }
            ComposeKind.VIDEO -> {
                _videoMedia.value = media.firstOrNull()
                _photoMedia.value = emptyList()
            }
            else -> {
                _photoMedia.value = emptyList()
                _videoMedia.value = null
            }
        }
        if (!post.quotedCommentId.isNullOrBlank()) {
            loadQuotedComment(post.quotedCommentId)
        } else if (!post.quotedPostId.isNullOrBlank()) {
            loadQuotedPost(post.quotedPostId)
        }
    }

    fun consumeSuccess() {
        _success.value = false
    }

    fun wrapMarkdown(prefix: String, suffix: String = prefix) {
        val current = _content.value
        _content.value = if (current.isBlank()) {
            "$prefix$suffix"
        } else {
            "$prefix$current$suffix"
        }
        scheduleDraftSave()
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

    fun updateLinkUrl(value: String) {
        _linkUrl.value = value
        clearFieldError(PostField.LinkUrl)
        scheduleDraftSave()
    }

    fun clearAudio() {
        _audioUrl.value = ""
        audioCleared = true
        scheduleDraftSave()
    }

    fun setComposeKind(kind: String) {
        val next = kind.ifBlank { ComposeKind.ARTICLE }
        if (_composeKind.value == next) return
        _composeKind.value = next
        _fieldErrors.value = emptyMap()
        if (next != ComposeKind.QUOTE) {
            quotedPostId = null
            quotedCommentId = null
            _quotedPost.value = null
            _quotedComment.value = null
            _quoteQuery.value = ""
            _quoteResults.value = emptyList()
        }
        scheduleDraftSave()
    }

    fun updateQuoteQuery(value: String) {
        _quoteQuery.value = value
        quoteSearchJob?.cancel()
        val q = value.trim()
        if (q.length < 2) {
            _quoteResults.value = emptyList()
            _isSearchingQuote.value = false
            return
        }
        quoteSearchJob = viewModelScope.launch {
            delay(250)
            _isSearchingQuote.value = true
            postRepository.searchPosts(q)
                .onSuccess { _quoteResults.value = it }
                .onFailure {
                    _quoteResults.value = emptyList()
                    _error.value = errorMessageResolver.fromThrowable(it, R.string.error_load_post)
                }
            _isSearchingQuote.value = false
        }
    }

    fun selectQuotedPost(hit: PostSearchHit) {
        quotedPostId = hit.id
        quotedCommentId = null
        _quotedComment.value = null
        _quoteQuery.value = ""
        _quoteResults.value = emptyList()
        viewModelScope.launch {
            val post = postRepository.getPostById(hit.id)
                ?: postRepository.getPostBySlug(hit.slug).getOrNull()
                ?: PostEntity(
                    id = hit.id,
                    authorId = "",
                    authorName = hit.authorName,
                    authorUsername = hit.authorUsername,
                    authorAvatar = null,
                    title = hit.title,
                    slug = hit.slug,
                    content = "",
                    excerpt = hit.excerpt,
                    coverImageUrl = hit.coverImageUrl,
                    createdAt = 0L,
                )
            quotedPostId = post.id
            _quotedPost.value = post
        }
        clearFieldError(PostField.Media)
    }

    private fun audioUrlForUpdate(): String? {
        val kind = _composeKind.value
        if (kind != ComposeKind.AUDIO && kind != ComposeKind.ARTICLE && kind != ComposeKind.MICRO) {
            return ""
        }
        if (audioCleared) return ""
        return _audioUrl.value.takeIf { it.isNotBlank() }
    }

    fun clearCover() {
        _coverImageUrl.value = ""
        scheduleDraftSave()
    }

    fun removePhoto(index: Int) {
        val current = _photoMedia.value.toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        _photoMedia.value = current
        if (current.isEmpty()) {
            _coverImageUrl.value = ""
        } else if (_coverImageUrl.value.isBlank()) {
            _coverImageUrl.value = current.first().url
        }
        scheduleDraftSave()
    }

    fun clearVideo() {
        _videoMedia.value = null
        scheduleDraftSave()
    }

    fun setScheduledAt(epochMs: Long?) {
        _scheduledAtEpoch.value = epochMs
    }

    fun uploadCover(uri: Uri) {
        viewModelScope.launch {
            _isUploadingCover.value = true
            _error.value = null
            try {
                val size = fileByteSize(uri)
                if (size != null && size > MAX_COVER_BYTES) {
                    _error.value = errorMessageResolver.string(R.string.error_cover_too_large)
                    return@launch
                }
                val part = uriToImageMultipart(uri)
                    ?: throw IllegalStateException("cover")
                val response = apiService.uploadMedia(part)
                _coverImageUrl.value = response.url
                scheduleDraftSave()
            } catch (e: Exception) {
                _error.value = errorMessageResolver.fromThrowable(e, R.string.error_cover_upload)
            } finally {
                _isUploadingCover.value = false
            }
        }
    }

    fun uploadPhoto(uri: Uri) {
        viewModelScope.launch {
            if (_photoMedia.value.size >= MAX_PHOTOS) return@launch
            _isUploadingPhoto.value = true
            _error.value = null
            try {
                val size = fileByteSize(uri)
                if (size != null && size > MAX_COVER_BYTES) {
                    _error.value = errorMessageResolver.string(R.string.error_cover_too_large)
                    return@launch
                }
                val part = uriToImageMultipart(uri)
                    ?: throw IllegalStateException("photo")
                val response = apiService.uploadMedia(part)
                val attached = AttachedMedia(id = response.id, url = response.url)
                _photoMedia.value = _photoMedia.value + attached
                if (_coverImageUrl.value.isBlank()) {
                    _coverImageUrl.value = response.url
                }
                clearFieldError(PostField.Media)
                scheduleDraftSave()
            } catch (e: Exception) {
                _error.value = errorMessageResolver.fromThrowable(e, R.string.error_cover_upload)
            } finally {
                _isUploadingPhoto.value = false
            }
        }
    }

    fun uploadVideo(uri: Uri) {
        viewModelScope.launch {
            _isUploadingVideo.value = true
            _error.value = null
            try {
                val size = fileByteSize(uri)
                if (size != null && size > MAX_VIDEO_BYTES) {
                    _error.value = errorMessageResolver.string(R.string.error_video_too_large)
                    return@launch
                }
                val part = uriToVideoMultipart(uri)
                    ?: throw IllegalStateException("video")
                val response = apiService.uploadMedia(part)
                _videoMedia.value = AttachedMedia(id = response.id, url = response.url)
                clearFieldError(PostField.Media)
                scheduleDraftSave()
            } catch (e: Exception) {
                _error.value = errorMessageResolver.fromThrowable(e, R.string.error_cover_upload)
            } finally {
                _isUploadingVideo.value = false
            }
        }
    }

    fun uploadAudio(uri: Uri) {
        viewModelScope.launch {
            _isUploadingAudio.value = true
            _error.value = null
            try {
                val size = fileByteSize(uri)
                if (size != null && size > MAX_AUDIO_BYTES) {
                    _error.value = errorMessageResolver.string(R.string.error_audio_too_large)
                    return@launch
                }
                val part = uriToAudioMultipart(uri)
                    ?: throw IllegalStateException("audio")
                val url = apiService.uploadMedia(part).url
                _audioUrl.value = url
                scheduleDraftSave()
            } catch (e: Exception) {
                _error.value = errorMessageResolver.fromThrowable(e, R.string.error_audio_upload)
            } finally {
                _isUploadingAudio.value = false
            }
        }
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

    fun updateTagInput(value: String) {
        _tagInput.value = value
        tagInputSuggestJob?.cancel()
        val query = value.trim().removePrefix("#")
        if (query.isEmpty()) {
            _tagInputSuggestions.value = emptyList()
            return
        }
        tagInputSuggestJob = viewModelScope.launch {
            delay(200)
            try {
                val res = apiService.suggestTags(query = query, limit = 8)
                _tagInputSuggestions.value = res.data
            } catch (_: Exception) {
                _tagInputSuggestions.value = emptyList()
            }
        }
    }

    fun addTag(rawTag: String): Boolean {
        val normalized = HashtagParser.normalize(rawTag)
        if (normalized.isBlank()) {
            _error.value = errorMessageResolver.string(R.string.post_hashtag_invalid)
            return false
        }
        val current = _tags.value
        if (current.any { it.equals(normalized, ignoreCase = true) }) {
            _error.value = errorMessageResolver.string(R.string.post_hashtag_duplicate)
            return false
        }
        if (current.size >= HashtagParser.MAX_TAGS) {
            _error.value = errorMessageResolver.string(R.string.post_hashtag_limit)
            return false
        }
        _tags.value = current + normalized
        _tagInput.value = ""
        _tagInputSuggestions.value = emptyList()
        scheduleDraftSave()
        return true
    }

    fun removeTag(tag: String) {
        _tags.value = _tags.value.filterNot { it.equals(tag, ignoreCase = true) }
        scheduleDraftSave()
    }

    fun submit() {
        val editingId = _editPostId.value
        if (editingId != null) {
            updatePost(editingId, _title.value, _content.value)
        } else {
            createPost(_title.value, _content.value, _audioUrl.value)
        }
    }

    fun submitDraft() {
        createPost(
            title = _title.value,
            content = _content.value,
            audioUrl = _audioUrl.value,
            status = "draft",
        )
    }

    fun createPost(
        title: String,
        content: String,
        audioUrl: String = _audioUrl.value,
        status: String? = null,
    ) {
        if (!canCreatePost(authRepository.getRole())) {
            _error.value = errorMessageResolver.string(R.string.error_create_post_forbidden)
            return
        }
        val quoting = _composeKind.value == ComposeKind.QUOTE ||
            !quotedPostId.isNullOrBlank() ||
            !quotedCommentId.isNullOrBlank()
        val errors = validate(title, content, _linkUrl.value, quoting = quoting)
        if (errors.isNotEmpty()) {
            _fieldErrors.value = errors
            _error.value = null
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null
            _fieldErrors.value = emptyMap()

            val untitled = errorMessageResolver.string(R.string.post_untitled_fallback)
            val kind = _composeKind.value
            val resolvedTitle = when {
                kind == ComposeKind.TEXT -> content.take(80).ifBlank { untitled }
                kind == ComposeKind.PHOTO -> content.take(80).ifBlank { untitled }
                kind == ComposeKind.VIDEO -> content.take(80).ifBlank { untitled }
                kind == ComposeKind.LINK -> content.take(80).ifBlank { untitled }
                kind == ComposeKind.QUOTE -> content.take(80).ifBlank { untitled }
                else -> title.ifBlank { content.take(80).ifBlank { untitled } }
            }
            val mediaIds = when (kind) {
                ComposeKind.PHOTO -> _photoMedia.value.map { it.id }
                ComposeKind.VIDEO -> _videoMedia.value?.let { listOf(it.id) }
                else -> null
            }
            postRepository.createPost(
                title = resolvedTitle,
                content = content,
                audioUrl = audioUrl.takeIf { it.isNotBlank() },
                coverImageUrl = _coverImageUrl.value.takeIf { it.isNotBlank() },
                scheduledAt = isoScheduledAt(),
                quotedPostId = quotedPostId,
                quotedCommentId = quotedCommentId,
                postType = ComposeKind.apiPostType(kind),
                linkUrl = _linkUrl.value.takeIf {
                    kind == ComposeKind.LINK && it.isNotBlank()
                },
                mediaIds = mediaIds,
                tags = _tags.value,
                status = status,
            )
                .onSuccess {
                    clearLocalDraft()
                    _success.value = true
                    postRepository.refreshFeed()
                }
                .onFailure { e ->
                    val parsed = errorMessageResolver.parseFormErrors(e, R.string.error_create_post)
                    val mappedErrors = parsed.fieldErrors.toMutableMap()
                    parsed.fieldErrors["text"]?.let { mappedErrors[PostField.Content] = it }
                    parsed.fieldErrors["link_url"]?.let { mappedErrors[PostField.LinkUrl] = it }
                    _fieldErrors.value = mappedErrors
                    _error.value = parsed.generalError?.takeIf { mappedErrors.isEmpty() }
                }

            _isSubmitting.value = false
        }
    }

    private fun updatePost(
        postId: String,
        title: String,
        content: String,
    ) {
        if (!canCreatePost(authRepository.getRole())) {
            _error.value = errorMessageResolver.string(R.string.error_create_post_forbidden)
            return
        }
        val errors = validate(title, content, _linkUrl.value, quoting = _composeKind.value == ComposeKind.QUOTE)
        if (errors.isNotEmpty()) {
            _fieldErrors.value = errors
            _error.value = null
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null
            _fieldErrors.value = emptyMap()

            val kind = _composeKind.value
            val untitled = errorMessageResolver.string(R.string.post_untitled_fallback)
            val resolvedTitle = when {
                kind == ComposeKind.TEXT -> content.take(80).ifBlank { untitled }
                kind == ComposeKind.PHOTO -> content.take(80).ifBlank { untitled }
                kind == ComposeKind.VIDEO -> content.take(80).ifBlank { untitled }
                kind == ComposeKind.LINK -> content.take(80).ifBlank { untitled }
                kind == ComposeKind.QUOTE -> content.take(80).ifBlank { untitled }
                else -> title.ifBlank { content.take(80).ifBlank { untitled } }
            }
            val mediaIds = when (kind) {
                ComposeKind.PHOTO -> _photoMedia.value.map { it.id }
                ComposeKind.VIDEO -> _videoMedia.value?.let { listOf(it.id) } ?: emptyList()
                else -> emptyList()
            }
            val clearQuote = kind != ComposeKind.QUOTE
            postRepository.updatePost(
                postId,
                resolvedTitle,
                content,
                audioUrl = audioUrlForUpdate(),
                coverImageUrl = _coverImageUrl.value.takeIf { it.isNotBlank() },
                postType = ComposeKind.apiPostType(kind),
                linkUrl = if (kind == ComposeKind.LINK) _linkUrl.value else "",
                mediaIds = mediaIds,
                quotedPostId = if (clearQuote) "" else quotedPostId,
                quotedCommentId = if (clearQuote) "" else quotedCommentId,
                tags = _tags.value,
            )
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

    private fun validate(
        title: String,
        content: String,
        linkUrl: String,
        quoting: Boolean,
    ): Map<String, String> = buildMap {
        val kind = if (quoting) ComposeKind.QUOTE else _composeKind.value
        when (kind) {
            ComposeKind.TEXT -> {
                if (content.isBlank()) {
                    put(PostField.Content, errorMessageResolver.string(R.string.validation_content_required))
                } else if (content.codePointCount(0, content.length) > MICRO_MAX_RUNES) {
                    put(
                        PostField.Content,
                        errorMessageResolver.string(R.string.validation_title_too_long),
                    )
                }
            }
            ComposeKind.PHOTO -> {
                if (_photoMedia.value.isEmpty()) {
                    put(PostField.Media, errorMessageResolver.string(R.string.validation_content_required))
                }
            }
            ComposeKind.VIDEO -> {
                if (_videoMedia.value == null) {
                    put(PostField.Media, errorMessageResolver.string(R.string.validation_content_required))
                }
            }
            ComposeKind.LINK -> {
                if (!isValidHttpsUrl(linkUrl)) {
                    put(PostField.LinkUrl, errorMessageResolver.string(R.string.post_link_invalid))
                }
            }
            ComposeKind.QUOTE -> {
                if (content.isBlank()) {
                    put(PostField.Content, errorMessageResolver.string(R.string.validation_content_required))
                }
                if (quotedPostId.isNullOrBlank() && quotedCommentId.isNullOrBlank()) {
                    put(PostField.Media, errorMessageResolver.string(R.string.quote_source_required))
                }
            }
            ComposeKind.AUDIO -> {
                if (title.isBlank()) {
                    put(PostField.Title, errorMessageResolver.string(R.string.validation_title_required))
                }
                if (content.isBlank()) {
                    put(PostField.Content, errorMessageResolver.string(R.string.validation_content_required))
                }
                if (_audioUrl.value.isBlank()) {
                    put(PostField.Media, errorMessageResolver.string(R.string.post_audio_required))
                }
            }
            else -> {
                if (title.isBlank()) {
                    put(PostField.Title, errorMessageResolver.string(R.string.validation_title_required))
                }
                if (content.isBlank()) {
                    put(PostField.Content, errorMessageResolver.string(R.string.validation_content_required))
                }
            }
        }
    }

    private fun isValidHttpsUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) && url.length > "https://".length

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
        if (!_isLoadingEdit.value) {
            flushDraftNow()
        }
        super.onCleared()
    }

    private fun restoreLocalDraft(force: Boolean) {
        if (!force && restoreDoneForKey == draftKey) return
        val draft = composeDraftStore.load(draftKey)
        _title.value = draft?.title.orEmpty()
        _content.value = draft?.content.orEmpty()
        _audioUrl.value = draft?.audioUrl.orEmpty()
        _coverImageUrl.value = draft?.coverImageUrl.orEmpty()
        _tags.value = draft?.tags.orEmpty()
        _linkUrl.value = ""
        _photoMedia.value = emptyList()
        _videoMedia.value = null
        restoreDoneForKey = draftKey
    }

    private fun scheduleDraftSave() {
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(DRAFT_DEBOUNCE_MS)
            composeDraftStore.save(
                title = _title.value,
                content = _content.value,
                audioUrl = _audioUrl.value,
                coverImageUrl = _coverImageUrl.value,
                tags = _tags.value,
                key = draftKey,
            )
        }
    }

    private fun flushDraftNow() {
        draftSaveJob?.cancel()
        composeDraftStore.save(
            title = _title.value,
            content = _content.value,
            audioUrl = _audioUrl.value,
            coverImageUrl = _coverImageUrl.value,
            tags = _tags.value,
            key = draftKey,
        )
    }

    private fun clearLocalDraft() {
        draftSaveJob?.cancel()
        composeDraftStore.clear(draftKey)
        restoreDoneForKey = null
        _title.value = ""
        _content.value = ""
        _audioUrl.value = ""
        _coverImageUrl.value = ""
        _tags.value = emptyList()
        _linkUrl.value = ""
        _photoMedia.value = emptyList()
        _videoMedia.value = null
        _scheduledAtEpoch.value = null
    }

    private fun fileByteSize(uri: Uri): Long? {
        val resolver = context.contentResolver
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) {
                    return cursor.getLong(idx)
                }
            }
        return resolver.openInputStream(uri)?.use { stream ->
            var total = 0L
            val buf = ByteArray(8192)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
            }
            total
        }
    }

    private fun uriToAudioMultipart(uri: Uri): MultipartBody.Part? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "audio/mpeg"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size > MAX_AUDIO_BYTES) {
            throw IllegalArgumentException("file too large")
        }
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val filename = when {
            mime.contains("mpeg") || mime.contains("mp3") -> "post.mp3"
            mime.contains("mp4") || mime.contains("m4a") -> "post.m4a"
            mime.contains("aac") -> "post.aac"
            mime.contains("ogg") -> "post.ogg"
            mime.contains("wav") -> "post.wav"
            mime.contains("webm") -> "post.webm"
            else -> "post.mp3"
        }
        return MultipartBody.Part.createFormData("file", filename, body)
    }

    private fun uriToImageMultipart(uri: Uri): MultipartBody.Part? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size > MAX_COVER_BYTES) {
            throw IllegalArgumentException("file too large")
        }
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val filename = when {
            mime.contains("png") -> "cover.png"
            mime.contains("webp") -> "cover.webp"
            mime.contains("gif") -> "cover.gif"
            else -> "cover.jpg"
        }
        return MultipartBody.Part.createFormData("file", filename, body)
    }

    private fun uriToVideoMultipart(uri: Uri): MultipartBody.Part? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "video/mp4"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size > MAX_VIDEO_BYTES) {
            throw IllegalArgumentException("file too large")
        }
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val filename = when {
            mime.contains("webm") -> "post.webm"
            mime.contains("quicktime") || mime.contains("mov") -> "post.mov"
            else -> "post.mp4"
        }
        return MultipartBody.Part.createFormData("file", filename, body)
    }

    private fun isoScheduledAt(): String? {
        val epoch = _scheduledAtEpoch.value ?: return null
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return format.format(java.util.Date(epoch))
    }

    private companion object {
        const val DRAFT_DEBOUNCE_MS = 800L
        const val MAX_AUDIO_BYTES = 50L * 1024L * 1024L
        const val MAX_COVER_BYTES = 5L * 1024L * 1024L
        const val MAX_VIDEO_BYTES = 100L * 1024L * 1024L
        const val MAX_PHOTOS = 10
        const val MICRO_MAX_RUNES = 500
    }
}
