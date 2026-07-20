package ir.xilo.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PushTokenRegisterRequest(
    val token: String,
    val platform: String = "android",
)

@Serializable
data class PushTokenDeleteRequest(
    val token: String,
)
