package com.gpo.scalepaw.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.gpo.scalepaw.data.model.ColorStyle
import com.gpo.scalepaw.data.model.ThemeMode
import com.gpo.scalepaw.data.settings.AppearanceSettings

val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun isDark(appearance: AppearanceSettings): Boolean = when (appearance.themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

/**
 * The app's scheme: wallpaper seeds through SPEC_2025 Expressive (default), the system dynamic
 * scheme (配色风格 › 系统, or a monochrome wallpaper), or the user's own two seeds.
 */
internal fun appColorScheme(context: Context, dark: Boolean, appearance: AppearanceSettings, wallpaper: SeedPair?): ColorScheme = when {
    appearance.dynamicColor && wallpaper != null && appearance.colorStyle == ColorStyle.EXPRESSIVE ->
        ExpressiveColorSchemeFactory.fromSeeds(wallpaper.primary, wallpaper.accent, dark)
    appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    else -> ExpressiveColorSchemeFactory.fromSeeds(appearance.seedColor, appearance.accentColor, dark)
}

/**
 * Yoin's design language: M3 Expressive motion scheme, Google Sans Flex, continuous-corner
 * shapes and an expressive colour scheme whose every token change washes with the effects spring.
 */
@Composable
fun ScaleTheme(appearance: AppearanceSettings = AppearanceSettings(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isDark(appearance)
    val wallpaper = rememberWallpaperSeeds(enabled = appearance.dynamicColor && appearance.colorStyle == ColorStyle.EXPRESSIVE)
    val target = remember(context, dark, appearance, wallpaper) { appColorScheme(context, dark, appearance, wallpaper) }
    val motionScheme = MotionScheme.expressive()
    val colorScheme = animateColorScheme(targetColorScheme = target, darkTheme = dark, motionScheme = motionScheme)
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = colorScheme,
            motionScheme = motionScheme,
            shapes = YoinShapes,
            typography = YoinTypography,
        ) {
            CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface, content = content)
        }
    }
}
