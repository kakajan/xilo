package ir.xilo.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ir.xilo.app.core.util.AppLocale
import ir.xilo.app.data.repository.ThemeMode
import ir.xilo.app.data.repository.ThemeRepository
import ir.xilo.app.push.PushNavigationCoordinator
import ir.xilo.app.push.extractPushNotificationData
import ir.xilo.app.theme.XiloTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeRepository: ThemeRepository

    @Inject
    lateinit var pushNavigationCoordinator: PushNavigationCoordinator

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        // Keep the system navigation bar visible; bottom chrome uses navigationBarsPadding().
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.navigationBars())

        handlePushIntent(intent)

        setContent {
            val platformTheme by themeRepository.theme.collectAsStateWithLifecycle()
            val themeMode by themeRepository.themeMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            XiloTheme(darkTheme = darkTheme, platformTheme = platformTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePushIntent(intent)
    }

    private fun handlePushIntent(intent: Intent?) {
        val data = intent?.extractPushNotificationData() ?: return
        pushNavigationCoordinator.handlePushData(data)
    }
}
