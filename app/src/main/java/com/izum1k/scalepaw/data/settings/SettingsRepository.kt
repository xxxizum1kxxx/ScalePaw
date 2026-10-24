package com.izum1k.scalepaw.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.izum1k.scalepaw.data.model.AggregationLevel
import com.izum1k.scalepaw.data.model.BackupInterval
import com.izum1k.scalepaw.data.model.BodyFatFormula
import com.izum1k.scalepaw.data.model.BodyWaterFormula
import com.izum1k.scalepaw.data.model.ColorStyle
import com.izum1k.scalepaw.data.model.LbmFormula
import com.izum1k.scalepaw.data.model.SmoothingAlgorithm
import com.izum1k.scalepaw.data.model.ThemeMode
import com.izum1k.scalepaw.data.model.TimeRange
import com.izum1k.scalepaw.domain.FormulaSettings
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val colorStyle: ColorStyle = ColorStyle.EXPRESSIVE,
    val seedColor: Int = DEFAULT_SEED,
    val accentColor: Int = DEFAULT_ACCENT,
) {
    companion object {
        const val DEFAULT_SEED = 0xFF2E9C8F.toInt()
        const val DEFAULT_ACCENT = 0xFFFF8A65.toInt()
    }
}

data class ChartSettings(
    val range: TimeRange = TimeRange.QUARTER,
    val selectedTypeIds: Set<Long> = emptySet(),
    val smoothing: SmoothingAlgorithm = SmoothingAlgorithm.NONE,
    val smoothingWindow: Int = 5,
    val smoothingAlpha: Float = 0.3f,
    val showPoints: Boolean = true,
    val showGoalLine: Boolean = true,
    val showTrendLine: Boolean = false,
    val aggregation: AggregationLevel = AggregationLevel.NONE,
)

data class AssignmentSettings(
    val smartAssignment: Boolean = false,
    val tolerancePercent: Int = 5,
    val ignoreOutsideTolerance: Boolean = false,
)

data class BackupSettings(
    val enabled: Boolean = false,
    val interval: BackupInterval = BackupInterval.WEEKLY,
    val folderUri: String? = null,
    val keepCount: Int = 5,
    val lastRun: Long = 0L,
    val lastError: String? = null,
)

data class ReminderSettings(
    val enabled: Boolean = false,
    val minuteOfDay: Int = 7 * 60 + 30,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
)

data class ScaleSettings(
    val address: String? = null,
    val name: String? = null,
    val driverId: String? = null,
    /** The advertisement the scale was paired from, so drivers can re-identify it offline. */
    val snapshot: String? = null,
    /** Link pacing profile name; null follows the driver's default. */
    val tuning: String? = null,
    /** Routes the scale to the diagnostic driver, which logs traffic and stores nothing. */
    val developerMode: Boolean = false,
)

data class AppSettings(
    val currentUserId: Long = 0L,
    val appearance: AppearanceSettings = AppearanceSettings(),
    val formulas: FormulaSettings = FormulaSettings(),
    val chart: ChartSettings = ChartSettings(),
    val tableAggregation: AggregationLevel = AggregationLevel.NONE,
    val assignment: AssignmentSettings = AssignmentSettings(),
    val backup: BackupSettings = BackupSettings(),
    val reminder: ReminderSettings = ReminderSettings(),
    val scale: ScaleSettings = ScaleSettings(),
    val hapticsEnabled: Boolean = true,
)

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Observable app settings in Preferences DataStore. Writes go through the typed setters below. */
class SettingsRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.store)

    private object K {
        val currentUser = longPreferencesKey("current_user")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val colorStyle = stringPreferencesKey("color_style")
        val seed = intPreferencesKey("seed_color")
        val accent = intPreferencesKey("accent_color")
        val bodyFat = stringPreferencesKey("formula_body_fat")
        val bodyWater = stringPreferencesKey("formula_body_water")
        val lbm = stringPreferencesKey("formula_lbm")
        val carryOver = booleanPreferencesKey("carry_over")
        val chartRange = stringPreferencesKey("chart_range")
        val chartTypes = stringSetPreferencesKey("chart_types")
        val smoothing = stringPreferencesKey("chart_smoothing")
        val smoothingWindow = intPreferencesKey("chart_smoothing_window")
        val smoothingAlpha = floatPreferencesKey("chart_smoothing_alpha")
        val showPoints = booleanPreferencesKey("chart_points")
        val showGoal = booleanPreferencesKey("chart_goal")
        val showTrend = booleanPreferencesKey("chart_trend")
        val chartAggregation = stringPreferencesKey("chart_aggregation")
        val tableAggregation = stringPreferencesKey("table_aggregation")
        val smart = booleanPreferencesKey("smart_assignment")
        val tolerance = intPreferencesKey("smart_tolerance")
        val ignoreOutside = booleanPreferencesKey("smart_ignore_outside")
        val backupEnabled = booleanPreferencesKey("backup_enabled")
        val backupInterval = stringPreferencesKey("backup_interval")
        val backupFolder = stringPreferencesKey("backup_folder")
        val backupKeep = intPreferencesKey("backup_keep")
        val backupLast = longPreferencesKey("backup_last")
        val backupError = stringPreferencesKey("backup_error")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val reminderDays = stringSetPreferencesKey("reminder_days")
        val scaleAddress = stringPreferencesKey("scale_address")
        val scaleName = stringPreferencesKey("scale_name")
        val scaleDriver = stringPreferencesKey("scale_driver")
        val scaleSnapshot = stringPreferencesKey("scale_snapshot")
        val scaleTuning = stringPreferencesKey("scale_tuning")
        val scaleDeveloper = booleanPreferencesKey("scale_developer")
        val haptics = booleanPreferencesKey("haptics")
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, fallback: E): E =
        this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: fallback

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            currentUserId = p[K.currentUser] ?: 0L,
            appearance = AppearanceSettings(
                themeMode = p.enum(K.themeMode, ThemeMode.SYSTEM),
                dynamicColor = p[K.dynamicColor] ?: true,
                colorStyle = p.enum(K.colorStyle, ColorStyle.EXPRESSIVE),
                seedColor = p[K.seed] ?: AppearanceSettings.DEFAULT_SEED,
                accentColor = p[K.accent] ?: AppearanceSettings.DEFAULT_ACCENT,
            ),
            formulas = FormulaSettings(
                bodyFat = p.enum(K.bodyFat, BodyFatFormula.OFF),
                bodyWater = p.enum(K.bodyWater, BodyWaterFormula.OFF),
                lbm = p.enum(K.lbm, LbmFormula.OFF),
                carryOverValues = p[K.carryOver] ?: false,
            ),
            chart = ChartSettings(
                range = p.enum(K.chartRange, TimeRange.QUARTER),
                selectedTypeIds = p[K.chartTypes]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
                smoothing = p.enum(K.smoothing, SmoothingAlgorithm.NONE),
                smoothingWindow = p[K.smoothingWindow] ?: 5,
                smoothingAlpha = p[K.smoothingAlpha] ?: 0.3f,
                showPoints = p[K.showPoints] ?: true,
                showGoalLine = p[K.showGoal] ?: true,
                showTrendLine = p[K.showTrend] ?: false,
                aggregation = p.enum(K.chartAggregation, AggregationLevel.NONE),
            ),
            tableAggregation = p.enum(K.tableAggregation, AggregationLevel.NONE),
            assignment = AssignmentSettings(
                smartAssignment = p[K.smart] ?: false,
                tolerancePercent = p[K.tolerance] ?: 5,
                ignoreOutsideTolerance = p[K.ignoreOutside] ?: false,
            ),
            backup = BackupSettings(
                enabled = p[K.backupEnabled] ?: false,
                interval = p.enum(K.backupInterval, BackupInterval.WEEKLY),
                folderUri = p[K.backupFolder],
                keepCount = p[K.backupKeep] ?: 5,
                lastRun = p[K.backupLast] ?: 0L,
                lastError = p[K.backupError],
            ),
            reminder = ReminderSettings(
                enabled = p[K.reminderEnabled] ?: false,
                minuteOfDay = p[K.reminderMinute] ?: (7 * 60 + 30),
                days = p[K.reminderDays]?.mapNotNull { d -> DayOfWeek.entries.firstOrNull { it.name == d } }?.toSet()
                    ?: DayOfWeek.entries.toSet(),
            ),
            scale = ScaleSettings(
                address = p[K.scaleAddress],
                name = p[K.scaleName],
                driverId = p[K.scaleDriver],
                snapshot = p[K.scaleSnapshot],
                tuning = p[K.scaleTuning],
                developerMode = p[K.scaleDeveloper] ?: false,
            ),
            hapticsEnabled = p[K.haptics] ?: true,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else set(key, value)
    }

    suspend fun setCurrentUser(id: Long) = edit { it[K.currentUser] = id }

    suspend fun setAppearance(a: AppearanceSettings) = edit {
        it[K.themeMode] = a.themeMode.name
        it[K.dynamicColor] = a.dynamicColor
        it[K.colorStyle] = a.colorStyle.name
        it[K.seed] = a.seedColor
        it[K.accent] = a.accentColor
    }

    suspend fun setFormulas(f: FormulaSettings) = edit {
        it[K.bodyFat] = f.bodyFat.name
        it[K.bodyWater] = f.bodyWater.name
        it[K.lbm] = f.lbm.name
        it[K.carryOver] = f.carryOverValues
    }

    suspend fun setChart(c: ChartSettings) = edit {
        it[K.chartRange] = c.range.name
        it[K.chartTypes] = c.selectedTypeIds.map(Long::toString).toSet()
        it[K.smoothing] = c.smoothing.name
        it[K.smoothingWindow] = c.smoothingWindow
        it[K.smoothingAlpha] = c.smoothingAlpha
        it[K.showPoints] = c.showPoints
        it[K.showGoal] = c.showGoalLine
        it[K.showTrend] = c.showTrendLine
        it[K.chartAggregation] = c.aggregation.name
    }

    suspend fun setTableAggregation(level: AggregationLevel) = edit { it[K.tableAggregation] = level.name }

    suspend fun setAssignment(a: AssignmentSettings) = edit {
        it[K.smart] = a.smartAssignment
        it[K.tolerance] = a.tolerancePercent
        it[K.ignoreOutside] = a.ignoreOutsideTolerance
    }

    suspend fun setBackup(b: BackupSettings) = edit {
        it[K.backupEnabled] = b.enabled
        it[K.backupInterval] = b.interval.name
        it.setOrRemove(K.backupFolder, b.folderUri)
        it[K.backupKeep] = b.keepCount
        it[K.backupLast] = b.lastRun
        it.setOrRemove(K.backupError, b.lastError)
    }

    suspend fun setReminder(r: ReminderSettings) = edit {
        it[K.reminderEnabled] = r.enabled
        it[K.reminderMinute] = r.minuteOfDay
        it[K.reminderDays] = r.days.map { d -> d.name }.toSet()
    }

    suspend fun setScale(s: ScaleSettings) = edit {
        it.setOrRemove(K.scaleAddress, s.address)
        it.setOrRemove(K.scaleName, s.name)
        it.setOrRemove(K.scaleDriver, s.driverId)
        it.setOrRemove(K.scaleSnapshot, s.snapshot)
        it.setOrRemove(K.scaleTuning, s.tuning)
        it[K.scaleDeveloper] = s.developerMode
    }

    suspend fun setHaptics(enabled: Boolean) = edit { it[K.haptics] = enabled }
}
