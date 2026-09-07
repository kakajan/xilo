package ir.xilo.app.ui.feed

/** Composer modes that map 1:1 to API `post_type` (plus audio/quote as article + extras). */
object ComposeKind {
    const val ARTICLE = "article"
    const val MICRO = "micro"
    const val TEXT = MICRO
    const val PHOTO = "photo"
    const val VIDEO = "video"
    const val LINK = "link"
    const val AUDIO = "audio"
    const val QUOTE = "quote"

    val all = listOf(TEXT, ARTICLE, PHOTO, VIDEO, LINK, QUOTE, AUDIO)

    fun apiPostType(kind: String): String = when (kind) {
        "text", MICRO -> MICRO
        PHOTO -> PHOTO
        VIDEO -> VIDEO
        LINK -> LINK
        else -> ARTICLE
    }

    fun fromPost(
        postType: String?,
        audioUrl: String?,
        quotedPostId: String?,
        quotedCommentId: String?,
    ): String {
        if (!quotedPostId.isNullOrBlank() || !quotedCommentId.isNullOrBlank()) return QUOTE
        val type = postType?.takeIf { it.isNotBlank() } ?: ARTICLE
        if (type == ARTICLE && !audioUrl.isNullOrBlank()) return AUDIO
        return when (type) {
            MICRO, "text" -> TEXT
            PHOTO -> PHOTO
            VIDEO -> VIDEO
            LINK -> LINK
            AUDIO -> AUDIO
            QUOTE -> QUOTE
            else -> ARTICLE
        }
    }
}
