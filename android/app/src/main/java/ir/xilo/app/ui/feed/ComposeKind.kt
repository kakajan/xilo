package ir.xilo.app.ui.feed

/** Composer modes that map 1:1 to API `post_type` (plus audio = article + audio_url). */
object ComposeKind {
    const val ARTICLE = "article"
    const val MICRO = "micro"
    const val TEXT = MICRO
    const val PHOTO = "photo"
    const val VIDEO = "video"
    const val LINK = "link"
    const val AUDIO = "audio"

    fun apiPostType(kind: String): String = when (kind) {
        "text", MICRO -> MICRO
        PHOTO -> PHOTO
        VIDEO -> VIDEO
        LINK -> LINK
        else -> ARTICLE
    }
}
