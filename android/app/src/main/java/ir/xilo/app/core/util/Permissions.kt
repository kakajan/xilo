package ir.xilo.app.core.util

private val CREATE_POST_ROLES = setOf("author", "editor", "admin", "superadmin")

fun canCreatePost(role: String?): Boolean {
    if (role.isNullOrBlank()) return false
    return role.lowercase() in CREATE_POST_ROLES
}
