package ir.xilo.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.core.util.canCreatePost
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.local.entity.UserEntity
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.CommentResponse
import ir.xilo.app.data.remote.dto.PostResponse
import ir.xilo.app.data.remote.dto.PublicProfileResponse
import ir.xilo.app.data.remote.dto.UserResponse
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.ChatRepository
import ir.xilo.app.data.repository.PostRepository
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

data class ProfileReplyItem(
    val id: String,
    val content: String,
    val postSlug: String,
    val postTitle: String,
    val createdAt: String,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val apiService: XiloApiService,
    private val postRepository: PostRepository,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _userProfile = MutableStateFlow<UserResponse?>(null)
    val userProfile: StateFlow<UserResponse?> = _userProfile.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    private val _userPosts = MutableStateFlow<List<PostEntity>>(emptyList())
    val userPosts: StateFlow<List<PostEntity>> = _userPosts.asStateFlow()

    private val _userReplies = MutableStateFlow<List<ProfileReplyItem>>(emptyList())
    val userReplies: StateFlow<List<ProfileReplyItem>> = _userReplies.asStateFlow()

    private val _userLikes = MutableStateFlow<List<PostEntity>>(emptyList())
    val userLikes: StateFlow<List<PostEntity>> = _userLikes.asStateFlow()

    private val _isOwnProfile = MutableStateFlow(false)
    val isOwnProfile: StateFlow<Boolean> = _isOwnProfile.asStateFlow()

    private val _canCreatePost = MutableStateFlow(canCreatePost(authRepository.getRole()))
    val canCreatePost: StateFlow<Boolean> = _canCreatePost.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isTabLoading = MutableStateFlow(false)
    val isTabLoading: StateFlow<Boolean> = _isTabLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _openChatId = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openChatId: SharedFlow<String> = _openChatId.asSharedFlow()

    private var loadedUsername: String? = null

    init {
        // Own-profile avatar/name edits land in Room via Settings; keep header in sync
        // without requiring a full public-profile reload (pager tab stays alive).
        viewModelScope.launch {
            authRepository.observeLocalProfile().collect { local ->
                mergeLocalProfile(local)
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    /** Soft refresh when returning to the profile tab / after Settings. */
    fun refreshProfile() {
        val username = loadedUsername ?: return
        if (_isLoading.value) return
        viewModelScope.launch {
            try {
                val remote = apiService.getPublicProfile(username)
                applyPublicProfile(remote)
            } catch (_: Exception) {
                // Keep the last successful snapshot; local merge still covers avatar.
            }
        }
    }

    fun toggleLike(postId: String, currentState: Boolean) {
        viewModelScope.launch {
            _userPosts.update { posts ->
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
            _userPosts.update { posts ->
                posts.map { post ->
                    if (post.id != postId) post
                    else post.copy(isBookmarked = !currentState)
                }
            }
            postRepository.toggleBookmark(postId, currentState)
        }
    }

    fun toggleRepost(postId: String, currentState: Boolean) {
        viewModelScope.launch {
            _userPosts.update { posts ->
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

    fun loadProfile(username: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            loadedUsername = username
            _isOwnProfile.value = isCurrentUser(username = username, userId = null)
            _canCreatePost.value = canCreatePost(authRepository.getRole())

            // Seed from Room first so a stale/down public-profile API cannot hide the avatar.
            if (_isOwnProfile.value) {
                seedFromLocal(authRepository.getLocalProfile())
            }

            try {
                val remote = apiService.getPublicProfile(username)
                applyPublicProfile(remote)
                loadTabContent(tabIndex = 0, force = true)
            } catch (e: Exception) {
                if (_userProfile.value == null) {
                    _error.value = errorMessageResolver.fromThrowable(e, R.string.error_load_profile)
                }
            }
            _isLoading.value = false
        }
    }

    fun onTabSelected(tabIndex: Int) {
        viewModelScope.launch {
            loadTabContent(tabIndex = tabIndex, force = false)
        }
    }

    fun toggleFollow() {
        val username = loadedUsername ?: return
        if (_isOwnProfile.value || username.isBlank()) return
        viewModelScope.launch {
            val becomingFollow = !_isFollowing.value
            _isFollowing.value = becomingFollow
            _userProfile.update { profile ->
                profile?.copy(
                    followerCount = (profile.followerCount + if (becomingFollow) 1 else -1)
                        .coerceAtLeast(0)
                )
            }
            try {
                val response = if (becomingFollow) {
                    apiService.followUser(username)
                } else {
                    apiService.unfollowUser(username)
                }
                _isFollowing.value = response.following
            } catch (e: Exception) {
                _isFollowing.value = !becomingFollow
                _userProfile.update { profile ->
                    profile?.copy(
                        followerCount = (profile.followerCount + if (becomingFollow) -1 else 1)
                            .coerceAtLeast(0)
                    )
                }
                _error.value = errorMessageResolver.fromThrowable(e, R.string.error_follow_failed)
            }
        }
    }

    fun startDirectMessage() {
        val userId = _userProfile.value?.id ?: return
        viewModelScope.launch {
            chatRepository.createDirectChat(userId)
                .onSuccess { chat -> _openChatId.emit(chat.id) }
                .onFailure { e ->
                    _error.value = errorMessageResolver.fromThrowable(e, R.string.error_start_chat)
                }
        }
    }

    private suspend fun applyPublicProfile(remote: PublicProfileResponse) {
        _isFollowing.value = remote.isFollowing
        _isOwnProfile.value = isCurrentUser(
            username = remote.username,
            userId = remote.id,
        )
        _userProfile.value = remote.toUserResponse()
        // Prefer freshly-saved local avatar when Redis/public API is stale.
        mergeLocalProfile(authRepository.getLocalProfile())
    }

    private fun seedFromLocal(local: UserEntity?) {
        if (local == null) return
        _userProfile.value = UserResponse(
            id = local.id,
            username = local.username,
            email = local.email,
            phone = local.phone,
            displayName = local.displayName,
            avatarUrl = local.avatarUrl?.takeIf { it.isNotBlank() },
            bio = local.bio,
            followerCount = local.followerCount,
            followingCount = local.followingCount,
            postCount = local.postCount,
        )
    }

    private fun mergeLocalProfile(local: UserEntity?) {
        if (local == null || !_isOwnProfile.value) return
        _userProfile.update { current ->
            val base = current ?: return@update UserResponse(
                id = local.id,
                username = local.username,
                email = local.email,
                phone = local.phone,
                displayName = local.displayName,
                avatarUrl = local.avatarUrl?.takeIf { it.isNotBlank() },
                bio = local.bio,
                followerCount = local.followerCount,
                followingCount = local.followingCount,
                postCount = local.postCount,
            )
            if (base.id != local.id &&
                !base.username.equals(local.username, ignoreCase = true)
            ) {
                return@update current
            }
            base.copy(
                avatarUrl = local.avatarUrl?.takeIf { it.isNotBlank() } ?: base.avatarUrl,
                displayName = local.displayName?.takeIf { it.isNotBlank() } ?: base.displayName,
                bio = local.bio ?: base.bio,
                username = local.username.takeIf { it.isNotBlank() } ?: base.username,
                phone = local.phone ?: base.phone,
                email = local.email.takeIf { it.isNotBlank() } ?: base.email,
            )
        }
    }

    private suspend fun loadTabContent(tabIndex: Int, force: Boolean) {
        val username = loadedUsername ?: return
        val own = _isOwnProfile.value
        when {
            tabIndex == 0 -> {
                if (!force && _userPosts.value.isNotEmpty()) return
                _isTabLoading.value = true
                runCatching { apiService.listUserPosts(username = username, limit = 20) }
                    .onSuccess { page -> _userPosts.value = page.data.map { it.toProfileEntity() } }
                    .onFailure { e ->
                        // Don't toast over an already-visible header (e.g. offline after local seed).
                        if (_userPosts.value.isEmpty() && _userProfile.value == null) {
                            _error.value =
                                errorMessageResolver.fromThrowable(e, R.string.error_load_profile)
                        }
                    }
                _isTabLoading.value = false
            }
            !own && tabIndex == 1 -> {
                if (!force && _userReplies.value.isNotEmpty()) return
                _isTabLoading.value = true
                runCatching { apiService.listUserReplies(username = username, limit = 20) }
                    .onSuccess { page -> _userReplies.value = page.data.map { it.toReplyItem() } }
                    .onFailure { e ->
                        if (_userReplies.value.isEmpty() && _userProfile.value == null) {
                            _error.value =
                                errorMessageResolver.fromThrowable(e, R.string.error_load_profile)
                        }
                    }
                _isTabLoading.value = false
            }
            !own && tabIndex == 2 -> {
                if (!force && _userLikes.value.isNotEmpty()) return
                _isTabLoading.value = true
                runCatching { apiService.listUserLikes(username = username, limit = 20) }
                    .onSuccess { page -> _userLikes.value = page.data.map { it.toProfileEntity() } }
                    .onFailure { e ->
                        if (_userLikes.value.isEmpty() && _userProfile.value == null) {
                            _error.value =
                                errorMessageResolver.fromThrowable(e, R.string.error_load_profile)
                        }
                    }
                _isTabLoading.value = false
            }
        }
    }

    private fun PostResponse.toProfileEntity(): PostEntity = PostEntity(
        id = id,
        authorId = authorId,
        authorName = author?.displayName ?: "",
        authorUsername = author?.username ?: "",
        authorAvatar = author?.avatarUrl ?: "",
        title = title,
        slug = slug,
        content = content,
        excerpt = excerpt,
        coverImageUrl = coverImageUrl,
        audioUrl = audioUrl,
        likeCount = resolvedLikeCount(),
        commentCount = commentCount,
        repostCount = repostCount,
        viewCount = viewCount,
        isLiked = resolvedIsLiked(),
        isBookmarked = isBookmarked,
        isReposted = isReposted,
        createdAt = 0L,
    )

    private fun CommentResponse.toReplyItem(): ProfileReplyItem = ProfileReplyItem(
        id = id,
        content = content,
        postSlug = post?.slug.orEmpty(),
        postTitle = post?.title.orEmpty(),
        createdAt = createdAt,
    )

    private fun isCurrentUser(username: String?, userId: String?): Boolean {
        val myUsername = authRepository.getUsername()
        val myId = authRepository.getUserId()
        val usernameMatch = !myUsername.isNullOrBlank() &&
            !username.isNullOrBlank() &&
            myUsername.equals(username, ignoreCase = true)
        val idMatch = !myId.isNullOrBlank() &&
            !userId.isNullOrBlank() &&
            myId == userId
        return usernameMatch || idMatch
    }
}
