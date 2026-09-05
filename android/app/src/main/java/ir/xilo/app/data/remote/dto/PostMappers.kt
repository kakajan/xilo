package ir.xilo.app.data.remote.dto

import ir.xilo.app.core.util.EmojiReactions
import ir.xilo.app.data.local.entity.PostEntity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val postMediaJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val postDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun PostResponse.toPostEntity(feedRank: Int = Int.MAX_VALUE): PostEntity = PostEntity(
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
    reactionsJson = EmojiReactions.fromPostDto(reactions, viewerReactions),
    createdAt = parseIsoToEpoch(publishedAt?.takeIf { it.isNotBlank() } ?: createdAt),
    feedRank = feedRank,
    quotedPostId = quotedPostId ?: quotedPost?.id,
    quotedTitle = quotedPost?.title,
    quotedSlug = quotedPost?.slug,
    quotedExcerpt = quotedPost?.excerpt,
    quotedAuthorName = quotedPost?.author?.displayName,
    quotedAuthorUsername = quotedPost?.author?.username,
    quotedAuthorAvatar = quotedPost?.author?.avatarUrl,
    quotedCoverImageUrl = quotedPost?.coverImageUrl,
    quotedCommentId = quotedCommentId ?: quotedComment?.id,
    quotedCommentContent = quotedComment?.content,
    quotedCommentAuthorName = quotedComment?.author?.displayName,
    quotedCommentAuthorUsername = quotedComment?.author?.username,
    quotedCommentAuthorAvatar = quotedComment?.author?.avatarUrl,
    quotedCommentPostTitle = quotedComment?.postTitle,
    quotedCommentPostSlug = quotedComment?.postSlug,
    quotedCommentPostAuthorUsername = quotedComment?.postAuthorUsername,
    postType = postType.ifBlank { "article" },
    linkUrl = linkUrl,
    mediaJson = encodePostMedia(media),
)

fun encodePostMedia(media: List<PostMediaDto>): String =
    runCatching { postMediaJson.encodeToString(media) }.getOrDefault("[]")

fun decodePostMedia(raw: String): List<PostMediaDto> =
    runCatching { postMediaJson.decodeFromString<List<PostMediaDto>>(raw) }.getOrDefault(emptyList())

private fun parseIsoToEpoch(dateStr: String?): Long {
    if (dateStr.isNullOrBlank()) return 0L
    return try {
        val cleanStr = dateStr.substringBefore("Z").substringBefore("+")
        postDateFormat.parse(cleanStr)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }
}
