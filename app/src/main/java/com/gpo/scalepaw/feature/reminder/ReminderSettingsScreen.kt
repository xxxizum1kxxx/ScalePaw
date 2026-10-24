package com.gpo.scalepaw.feature.reminder

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.scalepaw.R
import com.gpo.scalepaw.container
import com.gpo.scalepaw.data.settings.ReminderSettings
import com.gpo.scalepaw.ui.component.Fmt
import com.gpo.scalepaw.ui.component.ScalePage
import com.gpo.scalepaw.ui.component.ScaleSymbols
import com.gpo.scalepaw.ui.component.Section
import com.gpo.scalepaw.ui.component.SettingRow
import com.gpo.scalepaw.ui.component.SwitchRow
import com.gpo.scalepaw.ui.component.TimeDialog
import com.gpo.scalepaw.ui.component.epochMillisOf
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ReminderSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by context.container.session.state.collectAsStateWithLifecycle()
    val r = state.settings.reminder
    var pickTime by remember { mutableStateOf(false) }

    fun save(next: ReminderSettings) {
        scope.launch {
            context.container.settings.setReminder(next)
            ReminderScheduler.apply(context, next)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save(r.copy(enabled = true))
    }
    val time = LocalTime.of(r.minuteOfDay / 60, r.minuteOfDay % 60)

    ScalePage(title = stringResource(R.string.settings_reminder)) {
        item {
            Section {
                SwitchRow(
                    title = stringResource(R.string.reminder_enable),
                    value = stringResource(R.string.reminder_enable_summary),
                    checked = r.enabled,
                    onCheckedChange = { on -> if (on) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else save(r.copy(enabled = false)) },
                )
                SettingRow(
                    title = stringResource(R.string.field_time),
                    value = Fmt.time(context, epochMillisOf(LocalDate.now(), time)),
                    icon = ScaleSymbols.Clock,
                    enabled = r.enabled,
                    onClick = { pickTime = true },
                )
            }
        }
        item {
            Section(title = stringResource(R.string.reminder_days)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    DayOfWeek.entries.forEachIndexed { i, day ->
                        ToggleButton(
                            checked = day in r.days,
                            enabled = r.enabled,
                            onCheckedChange = { on -> save(r.copy(days = if (on) r.days + day else r.days - day)) },
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                DayOfWeek.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp),
                        ) { Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault())) }
                    }
                }
            }
        }
    }

    if (pickTime) {
        TimeDialog(time, onDismiss = { pickTime = false }) { t -> save(r.copy(minuteOfDay = t.hour * 60 + t.minute)) }
    }
}
