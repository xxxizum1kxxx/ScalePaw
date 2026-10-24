package com.izum1k.scalepaw

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation3.runtime.rememberNavBackStack
import com.izum1k.scalepaw.ui.navigation.AppNavHost
import com.izum1k.scalepaw.ui.navigation.Navigator
import com.izum1k.scalepaw.ui.navigation.Route
import com.izum1k.scalepaw.ui.theme.ApplySystemBars
import com.izum1k.scalepaw.ui.theme.ScaleTheme
import com.izum1k.scalepaw.ui.theme.isDark
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
                val haptics = LocalHapticFeedback.current
                val feedback = remember(haptics, state.settings.hapticsEnabled) {
                    if (state.settings.hapticsEnabled) haptics else SilentHaptics
                }
                CompositionLocalProvider(LocalHapticFeedback provides feedback) {
                    AppNavHost(navigator)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val ACTION_ADD = "com.izum1k.scalepaw.action.ADD_MEASUREMENT"
    }
}

private object SilentHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
