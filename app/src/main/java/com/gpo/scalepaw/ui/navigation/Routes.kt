package com.gpo.scalepaw.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Every destination. [Main] is the shell with the floating tab bar; everything else is pushed. */
@Serializable
sealed interface Route : NavKey {
    @Serializable data object Main : Route

    /** New entry when [id] is 0. */
    @Serializable data class MeasurementEdit(val id: Long = 0L) : Route

    @Serializable data class MeasurementDetail(val id: Long) : Route

    /** Raw entries inside an aggregated period. */
    @Serializable data class PeriodEntries(val start: Long, val endExclusive: Long) : Route

    /** Side-by-side comparison of the first and last entry of a window. */
    @Serializable data class Comparison(val start: Long, val end: Long) : Route

    @Serializable data object Settings : Route
    @Serializable data object SettingsGeneral : Route
    @Serializable data object SettingsUsers : Route
    @Serializable data class UserEdit(val id: Long = 0L) : Route
    @Serializable data object SettingsMetrics : Route
    @Serializable data class MetricEdit(val id: Long = 0L) : Route
    @Serializable data object SettingsChart : Route
    @Serializable data object SettingsData : Route
    @Serializable data object SettingsBluetooth : Route
    @Serializable data object SettingsReminder : Route
    @Serializable data object SettingsGoals : Route
    @Serializable data object About : Route
}

/** App-wide navigation handle: `LocalNavigator.current.push(Route.MeasurementDetail(id))`. */
class Navigator(internal val backStack: NavBackStack<NavKey>) {
    fun push(route: Route) {
        backStack.add(route)
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun replace(route: Route) {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack.add(route)
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("LocalNavigator not provided") }
