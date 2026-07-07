package com.example.xilo.data.remote.api

import com.example.xilo.data.remote.dto.*
import retrofit2.http.*

interface XiloApiService {

    // ── Auth API ──────────────────────────────────────────────────────────

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequest): AuthResponse

    @GET("api/auth/me")
    suspend fun getMe(): UserResponse

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

    @GET("api/posts/{slug}")
    suspend fun getPostBySlug(@Path("slug") slug: String): PostResponse

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
        @Path("type") type: String, // "posts" or "comments"
        @Path("id") id: String,
        @Query("reaction") reaction: String
    ): Map<String, kotlinx.serialization.json.JsonElement>

    // ── Bookmarks ─────────────────────────────────────────────────────────

    @POST("api/posts/{id}/bookmark")
    suspend fun bookmarkPost(@Path("id") id: String): Map<String, String>

    @DELETE("api/posts/{id}/bookmark")
    suspend fun unbookmarkPost(@Path("id") id: String): Map<String, String>

    @GET("api/bookmarks")
    suspend fun getBookmarks(): List<PostResponse>

    // ── Users / Follow ────────────────────────────────────────────────────

    @GET("api/users/{username}")
    suspend fun getPublicProfile(@Path("username") username: String): UserResponse

    @POST("api/users/{username}/follow")
    suspend fun followUser(@Path("username") username: String): Map<String, String>

    @DELETE("api/users/{username}/follow")
    suspend fun unfollowUser(@Path("username") username: String): Map<String, String>

    // ── Chats API ─────────────────────────────────────────────────────────

    @GET("api/chats")
    suspend fun listChats(): List<ChatResponse>

    @GET("api/chats/{id}/messages")
    suspend fun listMessages(
        @Path("id") id: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 50
    ): List<MessageResponse>

    @POST("api/chats/{id}/messages")
    suspend fun sendMessage(
        @Path("id") id: String,
        @Body request: SendMessageRequest
    ): MessageResponse
}
