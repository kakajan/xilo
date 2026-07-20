package ir.xilo.app.data.remote.api

import ir.xilo.app.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface XiloApiService {

    // ── Auth API ──────────────────────────────────────────────────────────

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequest): AuthResponse

    @POST("api/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Map<String, String>

    @GET("api/auth/me")
    suspend fun getMe(): UserResponse

    @PATCH("api/auth/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): UserResponse

    @GET("api/languages")
    suspend fun getLanguages(): LanguagesResponse

    @GET("api/platform/settings")
    suspend fun getPlatformSettings(): PlatformSettingsResponse

    @Multipart
    @POST("api/auth/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): UploadResponse

    @Multipart
    @POST("api/media/upload")
    suspend fun uploadMedia(@Part file: MultipartBody.Part): UploadResponse

    @GET("api/auth/sessions")
    suspend fun listSessions(
        @Header("X-Refresh-Token") refreshToken: String? = null
    ): List<SessionResponse>

    @DELETE("api/auth/sessions/{id}")
    suspend fun revokeSession(@Path("id") id: String): Map<String, String>

    @POST("api/auth/otp/request")
    suspend fun requestOtp(@Body request: RequestOTPRequest): Map<String, String>

    @POST("api/auth/otp/verify-login")
    suspend fun verifyOtpLogin(@Body request: VerifyOTPLoginRequest): AuthResponse

    // ── Posts API ─────────────────────────────────────────────────────────

    @GET("api/posts")
    suspend fun listPosts(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("tag") tag: String? = null,
        @Query("author") author: String? = null
    ): Map<String, kotlinx.serialization.json.JsonElement>

    @POST("api/posts")
    suspend fun createPost(@Body request: CreatePostRequest): PostResponse

    @PATCH("api/posts/{id}")
    suspend fun updatePost(
        @Path("id") id: String,
        @Body request: UpdatePostRequest,
    ): PostResponse

    @DELETE("api/posts/{id}")
    suspend fun deletePost(@Path("id") id: String): Map<String, String>

    @GET("api/posts/{slug}")
    suspend fun getPostBySlug(@Path("slug") slug: String): PostResponse

    @POST("api/posts/{id}/view")
    suspend fun recordPostView(
        @Path("id") id: String,
        @Body request: RecordViewRequest,
    ): RecordViewResponse

    @GET("api/tags/suggest")
    suspend fun suggestTags(
        @Query("q") query: String? = null,
        @Query("limit") limit: Int = 10,
    ): TagListResponse

    @GET("api/tags/trending")
    suspend fun trendingTags(
        @Query("limit") limit: Int = 20,
    ): TagListResponse

    // ── Comments API ──────────────────────────────────────────────────────

    @GET("api/posts/{postId}/comments")
    suspend fun listComments(
        @Path("postId") postId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String = "newest"
    ): Map<String, kotlinx.serialization.json.JsonElement>

    @POST("api/posts/{postId}/comments")
    suspend fun createComment(
        @Path("postId") postId: String,
        @Body request: CreateCommentRequest
    ): CommentResponse

    @POST("api/{type}/{id}/reactions")
    suspend fun toggleReaction(
        @Path("type") type: String, // "post" or "comment"
        @Path("id") id: String,
        @Body request: ToggleReactionRequest
    ): Map<String, kotlinx.serialization.json.JsonElement>

    // ── Bookmarks ─────────────────────────────────────────────────────────
    // Backend returns {"bookmarked": true|false} — values are booleans, not strings.

    @POST("api/posts/{id}/bookmark")
    suspend fun bookmarkPost(@Path("id") id: String): Map<String, kotlinx.serialization.json.JsonElement>

    @DELETE("api/posts/{id}/bookmark")
    suspend fun unbookmarkPost(@Path("id") id: String): Map<String, kotlinx.serialization.json.JsonElement>

    @POST("api/posts/{id}/repost")
    suspend fun repostPost(@Path("id") id: String): Map<String, kotlinx.serialization.json.JsonElement>

    @DELETE("api/posts/{id}/repost")
    suspend fun unrepostPost(@Path("id") id: String): Map<String, kotlinx.serialization.json.JsonElement>

    @GET("api/bookmarks")
    suspend fun getBookmarks(): CursorPage<PostResponse>

    @GET("api/bookmarks/comments")
    suspend fun getCommentBookmarks(): CursorPage<BookmarkedCommentResponse>

    @POST("api/comments/{id}/bookmark")
    suspend fun bookmarkComment(@Path("id") id: String): Map<String, kotlinx.serialization.json.JsonElement>

    @DELETE("api/comments/{id}/bookmark")
    suspend fun unbookmarkComment(@Path("id") id: String): Map<String, kotlinx.serialization.json.JsonElement>

    // ── Users / Follow ────────────────────────────────────────────────────

    @GET("api/users/{username}")
    suspend fun getPublicProfile(@Path("username") username: String): PublicProfileResponse

    @GET("api/users/{username}/posts")
    suspend fun listUserPosts(
        @Path("username") username: String,
        @Query("tab") tab: String = "posts",
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): CursorPage<PostResponse>

    @GET("api/users/{username}/replies")
    suspend fun listUserReplies(
        @Path("username") username: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): CursorPage<CommentResponse>

    @GET("api/users/{username}/likes")
    suspend fun listUserLikes(
        @Path("username") username: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): CursorPage<PostResponse>

    @GET("api/users/{username}/followers")
    suspend fun listUserFollowers(
        @Path("username") username: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): CursorPage<FollowListUserResponse>

    @GET("api/users/{username}/following")
    suspend fun listUserFollowing(
        @Path("username") username: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): CursorPage<FollowListUserResponse>

    @POST("api/users/{username}/follow")
    suspend fun followUser(@Path("username") username: String): FollowToggleResponse

    @DELETE("api/users/{username}/follow")
    suspend fun unfollowUser(@Path("username") username: String): FollowToggleResponse

    // ── Chats API ─────────────────────────────────────────────────────────

    @GET("api/chats")
    suspend fun listChats(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20
    ): CursorPage<ChatResponse>

    @POST("api/chats")
    suspend fun createChat(
        @Header("Idempotency-Key") operationKey: String,
        @Body request: CreateChatRequest
    ): ChatResponse

    @GET("api/chats/saved")
    suspend fun getSavedMessagesChat(): ChatResponse

    @GET("api/chats/{id}")
    suspend fun getChat(@Path("id") id: String): ChatResponse

    @PATCH("api/chats/{id}")
    suspend fun updateChat(
        @Path("id") id: String,
        @Body request: UpdateChatRequest
    ): ChatResponse

    @DELETE("api/chats/{id}")
    suspend fun leaveChat(@Path("id") id: String): Map<String, String>

    @GET("api/chats/{id}/messages")
    suspend fun listMessages(
        @Path("id") id: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 50
    ): CursorPage<MessageResponse>

    @POST("api/chats/{id}/messages")
    suspend fun sendMessage(
        @Path("id") id: String,
        @Header("Idempotency-Key") operationKey: String,
        @Body request: SendMessageRequest
    ): MessageResponse

    // ── Chat folders ──────────────────────────────────────────────────────

    @GET("api/chat-folders")
    suspend fun listChatFolders(): List<ChatFolderResponse>

    @POST("api/chat-folders")
    suspend fun createChatFolder(@Body request: CreateChatFolderRequest): ChatFolderResponse

    @PATCH("api/chat-folders/{id}")
    suspend fun updateChatFolder(
        @Path("id") id: String,
        @Body request: UpdateChatFolderRequest
    ): ChatFolderResponse

    @DELETE("api/chat-folders/{id}")
    suspend fun deleteChatFolder(@Path("id") id: String): Map<String, String>

    @PUT("api/chat-folders/{id}/chats")
    suspend fun setChatFolderChats(
        @Path("id") id: String,
        @Body request: SetChatFolderChatsRequest
    ): ChatFolderResponse

    // ── Interests / Contacts / Discover ────────────────────────────────────

    @GET("api/interests")
    suspend fun listInterests(): InterestsResponse

    @GET("api/users/me/interests")
    suspend fun getMyInterests(): UserInterestsResponse

    @PUT("api/users/me/interests")
    suspend fun updateMyInterests(@Body request: UpdateInterestsRequest): UserInterestsResponse

    @GET("api/contacts")
    suspend fun listContacts(): ContactsListResponse

    @POST("api/contacts/match")
    suspend fun matchContacts(@Body request: ContactMatchRequest): ContactMatchResponse

    @GET("api/discover/comments")
    suspend fun discoverComments(
        @Query("limit") limit: Int = 50,
        @Query("interest") interest: String? = null,
    ): DiscoverCommentsResponse

    // ── Notifications ─────────────────────────────────────────────────────

    @GET("api/notifications")
    suspend fun listNotifications(
        @Query("limit") limit: Int = 20,
    ): NotificationListResponse

    @GET("api/notifications/unread-count")
    suspend fun getUnreadNotificationCount(): UnreadCountResponse

    @POST("api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): Map<String, String>

    @POST("api/notifications/read-all")
    suspend fun markAllNotificationsRead(): Map<String, String>

    @GET("api/notifications/preferences")
    suspend fun getNotificationPreferences(): NotificationPreferencesResponse

    @PATCH("api/notifications/preferences")
    suspend fun patchNotificationPreferences(
        @Body body: Map<String, Boolean>,
    ): Map<String, String>

    // ── Push tokens ───────────────────────────────────────────────────────

    @POST("api/devices/push-tokens")
    suspend fun registerPushToken(@Body request: PushTokenRegisterRequest): Map<String, String>

    @HTTP(method = "DELETE", path = "api/devices/push-tokens", hasBody = true)
    suspend fun deletePushToken(@Body request: PushTokenDeleteRequest): Map<String, String>
}
