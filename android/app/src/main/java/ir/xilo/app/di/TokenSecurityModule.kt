package ir.xilo.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.xilo.app.data.auth.security.AndroidKeystoreTokenCipher
import ir.xilo.app.data.auth.security.TokenCipher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TokenSecurityModule {
    @Binds
    @Singleton
    abstract fun bindTokenCipher(implementation: AndroidKeystoreTokenCipher): TokenCipher
}
