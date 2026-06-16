package com.example.xilo

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data class PostDetailKey(val slug: String) : NavKey
@Serializable data class ChatConversationKey(val chatId: String) : NavKey
@Serializable object CreatePostKey : NavKey
