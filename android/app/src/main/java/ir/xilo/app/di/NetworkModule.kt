package ir.xilo.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.xilo.app.BuildConfig
import ir.xilo.app.data.auth.refresh.BearerTokenInterceptor
import ir.xilo.app.data.auth.refresh.SerializedTokenAuthenticator
import ir.xilo.app.data.local.prefs.TokenManager
import ir.xilo.app.data.remote.AppEnvironment
import ir.xilo.app.data.remote.DeviceHeadersInterceptor
import ir.xilo.app.data.remote.EndpointRules
import ir.xilo.app.data.remote.api.XiloApiService
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

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
    @RefreshHttpClient
    fun provideRefreshHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(createLoggingInterceptor())
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        tokenManager: TokenManager,
        @RefreshHttpClient refreshClient: OkHttpClient,
        json: Json,
        appEnvironment: AppEnvironment,
    ): OkHttpClient {
        val apiBaseUrl = appEnvironment.apiBaseUrl.toHttpUrl()
        val refreshUrl = requireNotNull(apiBaseUrl.resolve("api/auth/refresh")) {
            "Unable to resolve refresh endpoint"
        }

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(DeviceHeadersInterceptor())
            .addInterceptor(
                BearerTokenInterceptor(
                    tokenStore = tokenManager,
                    approvedApiOrigin = apiBaseUrl,
                    refreshUrl = refreshUrl,
                )
            )
            .addInterceptor(createLoggingInterceptor())
            .authenticator(
                SerializedTokenAuthenticator(
                    tokenStore = tokenManager,
                    refreshClient = refreshClient,
                    refreshUrl = refreshUrl,
                    json = json,
                )
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
        appEnvironment: AppEnvironment,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(appEnvironment.apiBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideXiloApiService(retrofit: Retrofit): XiloApiService {
        return retrofit.create(XiloApiService::class.java)
    }

    private fun createLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                // BASIC deliberately excludes credentials and personal data carried in JSON bodies.
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            EndpointRules.redactedHeaders().forEach { redactHeader(it) }
        }
}
