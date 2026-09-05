package ir.xilo.app.data.local.entity

import ir.xilo.app.core.util.EmojiReactions

fun PostEntity.toggledReaction(emoji: String): PostEntity {
    val json = EmojiReactions.toggleSelf(reactionsJson, emoji)
    if (!EmojiReactions.isLikeFamily(emoji)) {
        return copy(reactionsJson = json)
    }
    val heart = EmojiReactions.decode(json)
        .firstOrNull { EmojiReactions.isLikeFamily(it.reaction) }
    return copy(
        reactionsJson = json,
        isLiked = heart?.reacted == true,
        likeCount = heart?.count?.toInt() ?: 0,
    )
}
