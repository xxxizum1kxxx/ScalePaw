package com.izum1k.scalepaw.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.izum1k.scalepaw.feature.about.AboutScreen
import com.izum1k.scalepaw.feature.bluetooth.BluetoothScreen
import com.izum1k.scalepaw.feature.compare.ComparisonScreen
import com.izum1k.scalepaw.feature.data.DataSettingsScreen
import com.izum1k.scalepaw.feature.goals.GoalsScreen
import com.izum1k.scalepaw.feature.measurement.MeasurementDetailScreen
import com.izum1k.scalepaw.feature.measurement.MeasurementEditScreen
import com.izum1k.scalepaw.feature.metrics.MetricEditScreen
import com.izum1k.scalepaw.feature.metrics.MetricSettingsScreen
import com.izum1k.scalepaw.feature.reminder.ReminderSettingsScreen
import com.izum1k.scalepaw.feature.settings.ChartSettingsScreen
import com.izum1k.scalepaw.feature.settings.GeneralSettingsScreen
import com.izum1k.scalepaw.feature.settings.SettingsScreen
import com.izum1k.scalepaw.feature.table.PeriodEntriesScreen
import com.izum1k.scalepaw.feature.users.UserEditScreen
import com.izum1k.scalepaw.feature.users.UserSettingsScreen
import com.izum1k.scalepaw.feature.weigh.WeighScreen
import com.izum1k.scalepaw.ui.component.WidthClass
import com.izum1k.scalepaw.ui.component.widthClass
import com.izum1k.scalepaw.ui.shell.MainShell
import com.izum1k.scalepaw.ui.theme.YoinMotion

/**
 * One NavDisplay for the whole app. Pushes slide in like the platform's page transition; the
 * system back gesture drives NavDisplay's predictive pop (the page shrinks and follows the finger).
 * On expanded windows a measurement opens beside the shell instead of covering it.
 */
@Composable
fun AppNavHost(navigator: Navigator) {
    val backStack = navigator.backStack
    val motion = PageMotion(
        spatial = YoinMotion.defaultSpatialSpec(),
        effects = YoinMotion.defaultEffectsSpec(),
        fastEffects = YoinMotion.fastEffectsSpec(),
        scale = YoinMotion.defaultSpatialSpec(),
    )
    val wide = widthClass() == WidthClass.Expanded
    CompositionLocalProvider(LocalNavigator provides navigator) {
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { navigator.pop() },
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                    rememberViewModelStoreNavEntryDecorator<NavKey>(),
                ),
                transitionSpec = { motion.push() },
                popTransitionSpec = { motion.pop() },
                predictivePopTransitionSpec = { motion.pop() },
                sceneStrategies = listOf(ListDetailSceneStrategy(wide), SinglePaneSceneStrategy()),
                entryProvider = entryProvider {
                    entry<Route.Main>(metadata = ListPane) { MainShell() }
                    entry<Route.MeasurementEdit> { MeasurementEditScreen(it.id) }
                    entry<Route.MeasurementDetail>(metadata = DetailPane) { MeasurementDetailScreen(it.id) }
                    entry<Route.Weigh> { WeighScreen() }
                    entry<Route.PeriodEntries> { PeriodEntriesScreen(it.start, it.endExclusive) }
                    entry<Route.Comparison> { ComparisonScreen(it.start, it.end) }
                    entry<Route.Settings> { SettingsScreen() }
                    entry<Route.SettingsGeneral> { GeneralSettingsScreen() }
                    entry<Route.SettingsUsers> { UserSettingsScreen() }
                    entry<Route.UserEdit> { UserEditScreen(it.id) }
                    entry<Route.SettingsMetrics> { MetricSettingsScreen() }
                    entry<Route.MetricEdit> { MetricEditScreen(it.id) }
                    entry<Route.SettingsChart> { ChartSettingsScreen() }
                    entry<Route.SettingsData> { DataSettingsScreen() }
                    entry<Route.SettingsBluetooth> { BluetoothScreen() }
                    entry<Route.SettingsReminder> { ReminderSettingsScreen() }
                    entry<Route.SettingsGoals> { GoalsScreen() }
                    entry<Route.About> { AboutScreen() }
                },
            )
        }
    }
}

private class PageMotion(
    val spatial: FiniteAnimationSpec<IntOffset>,
    val effects: FiniteAnimationSpec<Float>,
    val fastEffects: FiniteAnimationSpec<Float>,
    val scale: FiniteAnimationSpec<Float>,
) {
    fun push(): ContentTransform =
        (slideInHorizontally(spatial) { it / 4 } + fadeIn(effects)) togetherWith
            (slideOutHorizontally(spatial) { -it / 8 } + fadeOut(fastEffects))

    fun pop(): ContentTransform =
        (slideInHorizontally(spatial) { -it / 8 } + fadeIn(effects)) togetherWith
            (scaleOut(scale, targetScale = 0.9f) + fadeOut(fastEffects))
}
