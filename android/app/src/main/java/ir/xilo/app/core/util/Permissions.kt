package ir.xilo.app.core.util

private val CREATE_POST_ROLES = setOf("author", "editor", "admin", "superadmin")
private val MODERATE_ROLES = setOf("editor", "admin", "superadmin")

fun canCreatePost(role: String?): Boolean {
    if (role.isNullOrBlank()) return false
    return role.lowercase() in CREATE_POST_ROLES
}

/** Repost is limited to the same roles that can publish posts. */
fun canRepost(role: String?): Boolean = canCreatePost(role)

/** Group chat creation uses the same writer+ gate as posts. */
fun canCreateGroup(role: String?): Boolean = canCreatePost(role)

/** Mods can pin comments; post authors are checked separately in UI. */
fun canModerate(role: String?): Boolean {
    if (role.isNullOrBlank()) return false
    return role.lowercase() in MODERATE_ROLES
}
