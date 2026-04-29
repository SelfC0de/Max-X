package ru.maxx.app.ui

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import ru.maxx.app.MaxXApp
import ru.maxx.app.ui.navigation.AppNavGraph
import ru.maxx.app.ui.navigation.Route
import ru.maxx.app.ui.theme.BgPrimary
import ru.maxx.app.ui.theme.MaxXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen (API 31+ нативный, 21-30 через compat)
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Edge-to-edge для API 21+
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // Доп. флаги для очень старых Android
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.parseColor("#0A0A10")
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.parseColor("#0D0D14")
        }

        val container = (application as MaxXApp).container
        val spoofDone = container.authPrefs.isSpoofSetupDone()
        val start = when {
            container.authPrefs.getToken() != null -> Route.Chats.path
            !spoofDone -> Route.SpoofSetup.path
            else -> Route.Phone.path
        }

        setContent {
            MaxXTheme {
                val nav = rememberNavController()
                AppNavGraph(
                    nav = nav,
                    startDest = start,
                    container = container,
                    modifier = Modifier.fillMaxSize().background(BgPrimary)
                )
            }
        }
    }
}
