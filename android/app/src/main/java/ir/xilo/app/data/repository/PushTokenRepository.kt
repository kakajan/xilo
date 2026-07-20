package ir.xilo.app.data.repository

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import ir.xilo.app.data.local.prefs.TokenManager
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.PushTokenDeleteRequest
import ir.xilo.app.data.remote.dto.PushTokenRegisterRequest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushTokenRepository @Inject constructor(
    private val apiService: XiloApiService,
    private val tokenManager: TokenManager,
) {
    /**
     * Fetches the current FCM token (when available) and registers it with the backend.
     * Safe to call after login, on cold start, and when FCM rotates the token.
     */
    suspend fun syncPushToken(forceRegister: Boolean = false): Result<Unit> {
        if (tokenManager.getAccessToken().isNullOrBlank()) {
            return Result.success(Unit)
        }
        return runCatching {
            val token = fetchFcmToken() ?: return@runCatching
            val cached = tokenManager.getFcmToken()
            if (!forceRegister && token == cached) {
                // Re-register occasionally in case the server dropped the mapping.
                runCatching {
                    apiService.registerPushToken(PushTokenRegisterRequest(token = token))
                }.onFailure { error ->
                    Log.w(TAG, "Push token re-register skipped", error)
                }
                return@runCatching
            }
            apiService.registerPushToken(PushTokenRegisterRequest(token = token))
            tokenManager.setFcmToken(token)
        }
    }

    /** Called from [ir.xilo.app.push.XiloFirebaseMessagingService.onNewToken]. */
    suspend fun onTokenRefreshed(token: String): Result<Unit> {
        tokenManager.setFcmToken(token)
        if (tokenManager.getAccessToken().isNullOrBlank()) {
            return Result.success(Unit)
        }
        return runCatching {
            apiService.registerPushToken(PushTokenRegisterRequest(token = token))
        }
    }

    /** Removes the device token from the backend and clears local cache. */
    suspend fun unregisterPushToken(): Result<Unit> {
        val token = tokenManager.getFcmToken()
        if (token.isNullOrBlank()) {
            return Result.success(Unit)
        }
        return runCatching {
            if (!tokenManager.getAccessToken().isNullOrBlank()) {
                apiService.deletePushToken(PushTokenDeleteRequest(token = token))
            }
        }.onFailure { error ->
            Log.w(TAG, "Push token unregister failed", error)
        }.also {
            tokenManager.clearFcmToken()
        }
    }

    private suspend fun fetchFcmToken(): String? {
        return runCatching {
            FirebaseMessaging.getInstance().token.await().trim().takeIf { it.isNotEmpty() }
        }.getOrElse { error ->
            Log.w(TAG, "FCM token fetch failed", error)
            null
        }
    }

    companion object {
        private const val TAG = "PushTokenRepository"
    }
}
