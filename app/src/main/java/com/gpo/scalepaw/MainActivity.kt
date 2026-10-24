package com.gpo.scalepaw

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation3.runtime.rememberNavBackStack
import com.gpo.scalepaw.ui.navigation.AppNavHost
import com.gpo.scalepaw.ui.navigation.Navigator
import com.gpo.scalepaw.ui.navigation.Route
import com.gpo.scalepaw.ui.theme.ApplySystemBars
import com.gpo.scalepaw.ui.theme.ScaleTheme
import com.gpo.scalepaw.ui.theme.isDark
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val session = container.session
        splash.setKeepOnScreenCondition { !session.state.value.loaded }
        val startRoute = if (intent?.action == ACTION_ADD) Route.MeasurementEdit() else null
        setContent {
            val state by session.state.collectAsState()
            ScaleTheme(state.settings.appearance) {
                ApplySystemBars(window, isDark(state.settings.appearance))
                val backStack = rememberNavBackStack(*listOfNotNull(Route.Main, startRoute).toTypedArray())
                val navigator = remember(backStack) { Navigator(backStack) }
                AppNavHost(navigator)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val ACTION_ADD = "com.gpo.scalepaw.action.ADD_MEASUREMENT"
    }
}
