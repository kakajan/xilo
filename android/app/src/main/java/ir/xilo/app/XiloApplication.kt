package ir.xilo.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.BrandRepository
import ir.xilo.app.data.repository.ThemeRepository
import ir.xilo.app.data.sync.OutboxWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class XiloApplication : Application() {
    @Inject
    lateinit var outboxWorkScheduler: OutboxWorkScheduler

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var themeRepository: ThemeRepository

    @Inject
    lateinit var brandRepository: BrandRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // A true process start safely requeues every ambiguous in-flight request.
        outboxWorkScheduler.enqueueColdStartRecovery()
        appScope.launch {
            authRepository.syncCalendarDefaults()
            themeRepository.syncTheme()
            brandRepository.syncBrand()
        }
    }
}
