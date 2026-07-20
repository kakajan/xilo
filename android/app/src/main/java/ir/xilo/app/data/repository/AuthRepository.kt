package ir.xilo.app.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.xilo.app.R
import ir.xilo.app.data.local.dao.UserDao
import ir.xilo.app.data.local.entity.UserEntity
import ir.xilo.app.data.local.prefs.TokenManager
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.LoginRequest
import ir.xilo.app.data.remote.dto.LogoutRequest
import ir.xilo.app.data.remote.dto.RegisterRequest
import ir.xilo.app.data.remote.dto.RequestOTPRequest
import ir.xilo.app.data.remote.dto.SessionResponse
import ir.xilo.app.core.util.CalendarPreference
import ir.xilo.app.core.util.DateFormatter
import ir.xilo.app.data.remote.dto.UpdateProfileRequest
import ir.xilo.app.data.remote.dto.UserResponse
import ir.xilo.app.data.remote.dto.VerifyOTPLoginRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: XiloApiService,
    private val userDao: UserDao,
    private val tokenManager: TokenManager,
    private val pushTokenRepository: PushTokenRepository,
) {
    val isAuthenticatedFlow: StateFlow<Boolean> = tokenManager.isAuthenticatedFlow

    init {
        DateFormatter.setUserPreferenceFromApi(tokenManager.getPreferredCalendar())
    }

    suspend fun register(
        email: String,
        password: String,
        displayName: String? = null,
        username: String = "",
    ): Result<UserResponse> {
        return try {
            val authResp = apiService.register(RegisterRequest(email, password, username, displayName))
            tokenManager.saveTokens(authResp.accessToken, authResp.refreshToken)
            val userProfile = try {
                apiService.getMe()
            } catch (_: Exception) {
                authResp.user ?: throw Exception("No user data in auth response")
            }
            saveUserLocal(userProfile)
            syncPreferredLanguageAfterAuth()
            Result.success(userProfile)
        } catch (e: Exception) {
            Result.failure(mapNetworkFailure(e))
        }
    }

    suspend fun login(email: String, password: String): Result<UserResponse> {
        return try {
            val authResp = apiService.login(LoginRequest(email, password))
            tokenManager.saveTokens(authResp.accessToken, authResp.refreshToken)
            val userProfile = try {
                apiService.getMe()
            } catch (_: Exception) {
                authResp.user ?: throw Exception("No user data in auth response")
            }
            saveUserLocal(userProfile)
            syncPreferredLanguageAfterAuth()
            Result.success(userProfile)
        } catch (e: Exception) {
            Result.failure(mapNetworkFailure(e))
        }
    }

    suspend fun requestOtp(phone: String): Result<Unit> {
        return try {
            apiService.requestOtp(RequestOTPRequest(phone = phone))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapNetworkFailure(e))
        }
    }

    suspend fun verifyOtpLogin(phone: String, code: String): Result<UserResponse> {
        return try {
            val authResp = apiService.verifyOtpLogin(VerifyOTPLoginRequest(phone = phone, code = code))
            tokenManager.saveTokens(authResp.accessToken, authResp.refreshToken)
            val userProfile = try {
                apiService.getMe()
            } catch (_: Exception) {
                authResp.user ?: throw Exception("No user data in auth response")
            }
            saveUserLocal(userProfile)
            syncPreferredLanguageAfterAuth()
            Result.success(userProfile)
        } catch (e: Exception) {
            Result.failure(mapNetworkFailure(e))
        }
    }

    /** Surfaces host/connect failures with the shared Persian network message. */
    private fun mapNetworkFailure(error: Exception): Exception {
        var current: Throwable = error
        while (true) {
            when (current) {
                is UnknownHostException, is ConnectException -> {
                    return IOException(context.getString(R.string.error_network), error)
                }
            }
            current = current.cause ?: break
        }
        return error
    }

    private val _onboardingCompleted = MutableStateFlow(isOnboardingCompleted())
    val onboardingCompletedFlow: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    suspend fun logout() {
        val refresh = tokenManager.getRefreshToken().orEmpty()
        pushTokenRepository.unregisterPushToken()
        if (refresh.isNotBlank()) {
            runCatching { apiService.logout(LogoutRequest(refreshToken = refresh)) }
        }
        val userId = tokenManager.getUserId()
        tokenManager.clearTokens()
        tokenManager.clearUser()
        tokenManager.setPreferredCalendar("auto")
        DateFormatter.setUserPreference(CalendarPreference.AUTO)
        tokenManager.setOnboardingCompleted(false)
        _onboardingCompleted.value = false
        if (userId != null) {
            runCatching { userDao.deleteUserById(userId) }
        }
    }

    suspend fun refreshMe(): Result<UserResponse> {
        return try {
            val me = apiService.getMe()
            saveUserLocal(me)
            Result.success(me)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncCalendarDefaults(): Result<Unit> {
        return try {
            val languages = apiService.getLanguages()
            DateFormatter.setPlatformDefaults(languages.calendarDefaults)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getPreferredCalendar(): CalendarPreference =
        CalendarPreference.fromApi(tokenManager.getPreferredCalendar())

    suspend fun updatePreferredCalendar(preference: CalendarPreference): Result<UserResponse> {
        return try {
            val updated = apiService.updateProfile(
                UpdateProfileRequest(preferredCalendar = preference.apiValue())
            )
            saveUserLocal(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUsername(username: String): Result<UserResponse> {
        return try {
            val updated = apiService.updateProfile(
                UpdateProfileRequest(username = username.trim())
            )
            saveUserLocal(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Updates public profile fields (display name, bio, optional username). */
    suspend fun updateProfileInfo(
        displayName: String,
        bio: String,
        username: String? = null,
    ): Result<UserResponse> {
        return try {
            val updated = apiService.updateProfile(
                UpdateProfileRequest(
                    displayName = displayName.trim(),
                    bio = bio.trim(),
                    username = username?.trim().orEmpty(),
                )
            )
            saveUserLocal(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePreferredLanguage(language: String): Result<UserResponse> {
        val normalized = language.trim().ifBlank { "fa" }
        tokenManager.setPreferredLanguage(normalized, chosen = true)
        return try {
            val updated = apiService.updateProfile(
                UpdateProfileRequest(preferredLanguage = normalized)
            )
            saveUserLocal(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listLanguages(): Result<List<ir.xilo.app.data.remote.dto.LanguageInfo>> {
        return try {
            Result.success(apiService.getLanguages().languages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isUsernamePending(): Boolean = tokenManager.isUsernamePending()

    fun getPreferredLanguage(): String = tokenManager.getPreferredLanguage()

    /** Persists UI language locally (login screen / offline). Server sync is separate. */
    fun setPreferredLanguageLocal(language: String) {
        tokenManager.setPreferredLanguage(language.trim().ifBlank { "fa" }, chosen = true)
    }

    /**
     * After auth: keep an explicit pre-auth language choice and push it to the account.
     * Otherwise adopt the server preference (e.g. returning user on a fresh install).
     */
    private suspend fun syncPreferredLanguageAfterAuth() {
        if (!tokenManager.isPreferredLanguageChosen()) return
        val local = tokenManager.getPreferredLanguage()
        runCatching {
            apiService.updateProfile(UpdateProfileRequest(preferredLanguage = local))
        }
    }

    suspend fun uploadAndSetAvatar(uri: Uri): Result<UserResponse> {
        return try {
            val part = uriToMultipart(uri) ?: return Result.failure(IllegalArgumentException("Unable to read image"))
            uploadAvatarPart(part)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Uploads a client-cropped PNG/JPEG avatar and updates the profile URL. */
    suspend fun uploadAndSetAvatar(imageBytes: ByteArray, mimeType: String = "image/png"): Result<UserResponse> {
        return try {
            if (imageBytes.isEmpty()) {
                return Result.failure(IllegalArgumentException("Unable to read image"))
            }
            val filename = if (mimeType.contains("jpeg") || mimeType.contains("jpg")) {
                "avatar.jpg"
            } else {
                "avatar.png"
            }
            val body = imageBytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", filename, body)
            uploadAvatarPart(part)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun uploadAvatarPart(part: MultipartBody.Part): Result<UserResponse> {
        return try {
            val upload = apiService.uploadAvatar(part)
            val avatarUrl = upload.variants["avatar"]?.takeIf { it.isNotBlank() } ?: upload.url
            val updated = apiService.updateProfile(UpdateProfileRequest(avatarUrl = avatarUrl))
            saveUserLocal(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listSessions(): Result<List<SessionResponse>> {
        return try {
            Result.success(apiService.listSessions(refreshToken = tokenManager.getRefreshToken()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun revokeSession(sessionId: String): Result<Unit> {
        return try {
            apiService.revokeSession(sessionId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAccessToken(): String? = tokenManager.getAccessToken()
    fun getUserId(): String? = tokenManager.getUserId()
    fun getUsername(): String? = tokenManager.getUsername()
    fun isAuthenticated(): Boolean = getAccessToken() != null
    fun isOnboardingCompleted(): Boolean = tokenManager.isOnboardingCompleted()

    fun completeOnboarding() {
        tokenManager.setOnboardingCompleted(true)
        _onboardingCompleted.value = true
    }

    fun getRole(): String? = tokenManager.getRole()

    private suspend fun saveUserLocal(user: UserResponse) {
        tokenManager.saveUser(user.id, user.username, user.role.ifBlank { "reader" })
        val calendar = user.preferredCalendar?.takeIf { it.isNotBlank() } ?: "auto"
        tokenManager.setPreferredCalendar(calendar)
        DateFormatter.setUserPreferenceFromApi(calendar)
        val pending = user.usernamePending || user.username.startsWith("tmp_")
        tokenManager.setUsernamePending(pending)
        // Do not clobber an explicit UI language (e.g. English chosen on the login screen)
        // with the account default (usually fa) during login /me refresh.
        if (!tokenManager.isPreferredLanguageChosen()) {
            user.preferredLanguage?.takeIf { it.isNotBlank() }?.let {
                tokenManager.setPreferredLanguage(it, chosen = false)
            }
        }
        userDao.insertUser(
            UserEntity(
                id = user.id,
                username = user.username,
                email = user.email,
                phone = user.phone,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
                bio = user.bio
            )
        )
    }

    suspend fun getLocalProfile(): UserEntity? {
        val uid = getUserId() ?: return null
        return userDao.getUserById(uid)
    }

    fun observeLocalProfile(): Flow<UserEntity?> {
        val uid = getUserId() ?: return flowOf(null)
        return userDao.observeUserById(uid)
    }

    /** Hydrates local identity from `/api/auth/me` when prefs/Room lack a username. */
    suspend fun getMeUsername(): String? {
        val me = apiService.getMe()
        saveUserLocal(me)
        return me.username.takeIf { it.isNotBlank() }
    }

    private fun uriToMultipart(uri: Uri): MultipartBody.Part? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val filename = when {
            mime.contains("png") -> "avatar.png"
            mime.contains("webp") -> "avatar.webp"
            else -> "avatar.jpg"
        }
        return MultipartBody.Part.createFormData("file", filename, body)
    }
}
