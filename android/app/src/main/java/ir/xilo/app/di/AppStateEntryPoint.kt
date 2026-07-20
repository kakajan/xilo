package ir.xilo.app.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.xilo.app.data.local.prefs.TokenManager
import ir.xilo.app.data.repository.AuthRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppStateEntryPoint {
    fun tokenManager(): TokenManager
    fun authRepository(): AuthRepository
}
