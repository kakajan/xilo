package ir.xilo.app.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.core.util.canRepost
import ir.xilo.app.data.NetworkMonitor
import ir.xilo.app.data.local.entity.CommentEntity
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.DiscoverCommentDto
import ir.xilo.app.data.remote.dto.InterestDto
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.CommentRepository
import ir.xilo.app.data.repository.PostRepository
import ir.xilo.app.util.ErrorMessageResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val apiService: XiloApiService,
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val json: Json,
    private val errorMessageResolver: ErrorMessageResolver,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val recentComments: StateFlow<List<CommentEntity>> = commentRepository.getRecentComments(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _apiComments = MutableStateFlow<List<CommentEntity>?>(null)
    private val postSlugById = mutableMapOf<String, String>()
    private val _selectedInterestSlug = MutableStateFlow<String?>(null)
    val selectedInterestSlug: StateFlow<String?> = _selectedInterestSlug.asStateFlow()

    private val _topicInterests = MutableStateFlow<List<InterestDto>>(emptyList())
    val topicInterests: StateFlow<List<InterestDto>> = _topicInterests.asStateFlow()

    val discoverComments: StateFlow<List<CommentEntity>> = combine(
        _apiComments,
        recentComments,
    ) { api, cached ->
        api ?: cached
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<PostEntity>>(emptyList())
    val searchResults: StateFlow<List<PostEntity>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _infoMessage = MutableStateFlow<Int?>(null)
    val infoMessage: StateFlow<Int?> = _infoMessage.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _currentUserId = MutableStateFlow(authRepository.getUserId())
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _currentUsername = MutableStateFlow(authRepository.getUsername())
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()

    private val _canRepost = MutableStateFlow(canRepost(authRepository.getRole()))
    val canRepost: StateFlow<Boolean> = _canRepost.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        loadTopicChips()
        refreshDiscoverComments()
    }

    fun selectInterest(slug: String?) {
        if (_selectedInterestSlug.value == slug) return
        _selectedInterestSlug.value = slug
        refreshDiscoverComments()
    }

    fun refreshDiscoverComments() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val response = apiService.discoverComments(
                    limit = 50,
                    interest = _selectedInterestSlug.value,
                )
                response.data.forEach { dto ->
                    val slug = dto.post?.slug?.takeIf { it.isNotBlank() }
                    if (slug != null) postSlugById[dto.postId] = slug
                }
                _apiComments.value = response.data.map { it.toEntity() }
            } catch (_: Exception) {
                // Offline / API unavailable — fall back to cached recent comments path.
                if (_apiComments.value == null) {
                    try {
                        val postsMap = apiService.listPosts(limit = 10)
                        val dataElement = postsMap["data"]
                        if (dataElement != null) {
                            val posts = json.decodeFromJsonElement<List<ir.xilo.app.data.remote.dto.PostResponse>>(dataElement)
                            posts.forEach { post ->
                                commentRepository.refreshComments(post.id)
                            }
                        }
                    } catch (_: Exception) {
                        // Keep Room cache via discoverComments combine
                    }
                }
            }
            _isRefreshing.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearInfoMessage() {
        _infoMessage.value = null
    }

    fun toggleCommentLike(comment: CommentEntity) {
        viewModelScope.launch {
            commentRepository.toggleCommentReaction(
                commentId = comment.id,
                reaction = "like",
                currentlyActive = comment.isLiked,
                oppositeActive = comment.isDisliked
            ).onFailure {
                _errorMessage.value =
                    errorMessageResolver.fromThrowable(it, R.string.error_unknown)
            }
            // Keep in-memory discover list in sync for API-sourced rows.
            _apiComments.update { list ->
                list?.map { c ->
                    if (c.id != comment.id) c
                    else c.copy(
                        isLiked = !comment.isLiked,
                        likeCount = (c.likeCount + if (!comment.isLiked) 1 else -1).coerceAtLeast(0),
                        isDisliked = if (!comment.isLiked) false else c.isDisliked,
                        dislikeCount = if (!comment.isLiked && comment.isDisliked) {
                            (c.dislikeCount - 1).coerceAtLeast(0)
                        } else {
                            c.dislikeCount
                        },
                    )
                }
            }
        }
    }

    fun toggleCommentDislike(comment: CommentEntity) {
        viewModelScope.launch {
            commentRepository.toggleCommentReaction(
                commentId = comment.id,
                reaction = "dislike",
                currentlyActive = comment.isDisliked,
                oppositeActive = comment.isLiked
            ).onFailure {
                _errorMessage.value =
                    errorMessageResolver.fromThrowable(it, R.string.error_unknown)
            }
            _apiComments.update { list ->
                list?.map { c ->
                    if (c.id != comment.id) c
                    else c.copy(
                        isDisliked = !comment.isDisliked,
                        dislikeCount = (c.dislikeCount + if (!comment.isDisliked) 1 else -1).coerceAtLeast(0),
                        isLiked = if (!comment.isDisliked) false else c.isLiked,
                        likeCount = if (!comment.isDisliked && comment.isLiked) {
                            (c.likeCount - 1).coerceAtLeast(0)
                        } else {
                            c.likeCount
                        },
                    )
                }
            }
        }
    }

    fun toggleCommentBookmark(comment: CommentEntity) {
        viewModelScope.launch {
            commentRepository.toggleBookmark(comment.id, comment.isBookmarked)
                .onFailure {
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(it, R.string.error_unknown)
                }
            _apiComments.update { list ->
                list?.map { c ->
                    if (c.id != comment.id) c
                    else c.copy(isBookmarked = !comment.isBookmarked)
                }
            }
        }
    }

    fun reportComment() {
        _infoMessage.value = R.string.discover_report_submitted
    }

    fun toggleLike(postId: String, currentState: Boolean) {
        viewModelScope.launch {
            _searchResults.update { posts ->
                posts.map { post ->
                    if (post.id != postId) post
                    else {
                        val liked = !currentState
                        post.copy(
                            isLiked = liked,
                            likeCount = (post.likeCount + if (liked) 1 else -1).coerceAtLeast(0)
                        )
                    }
                }
            }
            postRepository.toggleLike(postId, currentState)
        }
    }

    fun toggleBookmark(postId: String, currentState: Boolean) {
        viewModelScope.launch {
            _searchResults.update { posts ->
                posts.map { post ->
                    if (post.id != postId) post
                    else post.copy(isBookmarked = !currentState)
                }
            }
            postRepository.toggleBookmark(postId, currentState)
        }
    }

    fun toggleRepost(postId: String, currentState: Boolean) {
        if (!_canRepost.value) return
        viewModelScope.launch {
            _searchResults.update { posts ->
                posts.map { post ->
                    if (post.id != postId) post
                    else {
                        val reposted = !currentState
                        post.copy(
                            isReposted = reposted,
                            repostCount = (post.repostCount + if (reposted) 1 else -1).coerceAtLeast(0)
                        )
                    }
                }
            }
            postRepository.toggleRepost(postId, currentState)
        }
    }

    fun archivePost(postId: String) {
        viewModelScope.launch {
            postRepository.archivePost(postId)
                .onSuccess {
                    _searchResults.update { posts -> posts.filterNot { it.id == postId } }
                }
                .onFailure {
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(it, R.string.error_archive_post)
                }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            postRepository.deletePost(postId)
                .onSuccess {
                    _searchResults.update { posts -> posts.filterNot { it.id == postId } }
                }
                .onFailure {
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(it, R.string.error_delete_post)
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _errorMessage.value = null
        if (query.isNotBlank()) {
            performSearch(query)
        } else {
            _searchResults.value = emptyList()
        }
    }

    fun openCommentPost(postId: String, onNavigate: (String) -> Unit) {
        viewModelScope.launch {
            val slug = resolvePostSlug(postId) ?: return@launch
            onNavigate(slug)
        }
    }

    fun openCommentReply(
        postId: String,
        commentId: String,
        authorUsername: String,
        onNavigate: (slug: String, commentId: String, authorUsername: String) -> Unit,
    ) {
        viewModelScope.launch {
            val slug = resolvePostSlug(postId) ?: return@launch
            onNavigate(slug, commentId, authorUsername)
        }
    }

    private suspend fun resolvePostSlug(postId: String): String? =
        postSlugById[postId] ?: postRepository.getPostSlugById(postId)

    private fun loadTopicChips() {
        viewModelScope.launch {
            try {
                val mine = runCatching { apiService.getMyInterests() }.getOrNull()
                val chips = when {
                    !mine?.interests.isNullOrEmpty() -> mine!!.interests.sortedBy { it.sortOrder }
                    else -> apiService.listInterests().interests.sortedBy { it.sortOrder }
                }
                _topicInterests.value = chips
            } catch (_: Exception) {
                _topicInterests.value = emptyList()
            }
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val postsMap = apiService.listPosts(limit = 20, tag = query)
                val dataElement = postsMap["data"]
                if (dataElement != null) {
                    val postsDto = json.decodeFromJsonElement<List<ir.xilo.app.data.remote.dto.PostResponse>>(dataElement)
                    _searchResults.value = postsDto.map { remote ->
                        PostEntity(
                            id = remote.id,
                            authorId = remote.authorId,
                            authorName = remote.author?.displayName ?: "",
                            authorUsername = remote.author?.username ?: "",
                            authorAvatar = remote.author?.avatarUrl ?: "",
                            title = remote.title,
                            slug = remote.slug,
                            content = remote.content,
                            excerpt = remote.excerpt,
                            coverImageUrl = remote.coverImageUrl,
                            likeCount = remote.resolvedLikeCount(),
                            commentCount = remote.commentCount,
                            repostCount = remote.repostCount,
                            viewCount = remote.viewCount,
                            isLiked = remote.resolvedIsLiked(),
                            isBookmarked = remote.isBookmarked,
                            isReposted = remote.isReposted,
                            createdAt = System.currentTimeMillis()
                        )
                    }
                } else {
                    _searchResults.value = emptyList()
                }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                _errorMessage.value = errorMessageResolver.fromThrowable(e, R.string.error_search)
            }
            _isSearching.value = false
        }
    }

    private fun DiscoverCommentDto.toEntity(): CommentEntity = CommentEntity(
        id = id,
        postId = postId,
        authorId = authorId,
        authorName = author?.displayName ?: "",
        authorUsername = author?.username ?: "",
        authorAvatar = author?.avatarUrl ?: "",
        parentId = parentId,
        rootId = rootId,
        depth = depth,
        content = content,
        likeCount = resolvedLikeCount(),
        dislikeCount = resolvedDislikeCount(),
        replyCount = replyCount,
        isLiked = resolvedIsLiked(),
        isDisliked = resolvedIsDisliked(),
        isBookmarked = isBookmarked,
        isPinned = isPinned,
        createdAt = parseDateToEpoch(createdAt)
    )

    private fun parseDateToEpoch(dateStr: String): Long {
        return try {
            val cleanStr = dateStr.substringBefore("Z").substringBefore("+")
            dateFormat.parse(cleanStr)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
