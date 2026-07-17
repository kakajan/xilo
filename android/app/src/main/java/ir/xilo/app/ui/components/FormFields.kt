package ir.xilo.app.ui.components

enum class AuthField(val key: String) {
    Username("username"),
    Email("email"),
    Password("password"),
    DisplayName("display_name");

    companion object {
        fun fromKey(key: String): AuthField? = entries.firstOrNull { it.key == key }
    }
}

object PostField {
    const val Title = "title"
    const val Content = "content"
}
