package com.example.xilo.data.repository

import com.example.xilo.data.local.dao.UserDao
import com.example.xilo.data.local.entity.UserEntity
import com.example.xilo.data.local.prefs.TokenManager
import com.example.xilo.data.remote.api.XiloApiService
import com.example.xilo.data.remote.dto.LoginRequest
import com.example.xilo.data.remote.dto.RegisterRequest
import com.example.xilo.data.remote.dto.UserResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: XiloApiService,
    private val userDao: UserDao,
    private val tokenManager: TokenManager
) {
    suspend fun register(
        username: String,
        email: String,
        password: String,
        displayName: String?
    ): Result<UserResponse> {
        return try {
            val authResp = apiService.register(RegisterRequest(email, username, password, displayName))
            tokenManager.saveTokens(authResp.accessToken, authResp.refreshToken)
            // Try to load full profile; fall back to the user embedded in AuthResponse
            val userProfile = try {
                apiService.getMe()
            } catch (e: Exception) {
                authResp.user ?: throw Exception("No user data in auth response")
            }
            saveUserLocal(userProfile)
            Result.success(userProfile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<UserResponse> {
        return try {
            val authResp = apiService.login(LoginRequest(email, password))
            tokenManager.saveTokens(authResp.accessToken, authResp.refreshToken)
            // Try to load full profile; fall back to the user embedded in AuthResponse
            val userProfile = try {
                apiService.getMe()
            } catch (e: Exception) {
                authResp.user ?: throw Exception("No user data in auth response")
            }
            saveUserLocal(userProfile)
            Result.success(userProfile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        tokenManager.clearTokens()
    }

    fun getAccessToken(): String? = tokenManager.getAccessToken()
    fun getUserId(): String? = tokenManager.getUserId()
    fun getUsername(): String? = tokenManager.getUsername()
    fun isAuthenticated(): Boolean = getAccessToken() != null

    private suspend fun saveUserLocal(user: UserResponse) {
        tokenManager.saveUser(user.id, user.username)
        userDao.insertUser(
            UserEntity(
                id = user.id,
                username = user.username,
                email = user.email,
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
}
