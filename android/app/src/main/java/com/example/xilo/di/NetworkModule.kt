package com.example.xilo.di

import com.example.xilo.data.local.prefs.TokenManager
import com.example.xilo.data.remote.api.XiloApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "http://10.0.2.2:8888/" // Android Emulator localhost bridge to host (Docker maps host:8888 → container:8000)

    @Provides
    @Singleton
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenManager: TokenManager): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val token = tokenManager.getAccessToken()

                val requestBuilder = originalRequest.newBuilder()
                if (token != null && !originalRequest.url.encodedPath.contains("auth/refresh")) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }

                val response = chain.proceed(requestBuilder.build())

                // If unauthorized and we have a refresh token, try to refresh token
                if (response.code == 401 && tokenManager.getRefreshToken() != null) {
                    // Synchronously call refresh endpoint (omitted for brevity in interceptor to prevent cycle,
                    // but tokenManager.clearTokens() on persistent auth failure is handled at app level)
                }

                response
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideXiloApiService(retrofit: Retrofit): XiloApiService {
        return retrofit.create(XiloApiService::class.java)
    }
}
