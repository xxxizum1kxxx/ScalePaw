package com.izum1k.scalepaw.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.ui.component.ScalePage
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.component.Section
import com.izum1k.scalepaw.ui.component.SettingRow
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.izum1k.scalepaw.ui.navigation.Route
import com.gpo.yoin.symbols.PiliSymbols
import com.gpo.yoin.symbols.YoinSymbols

@Composable
fun SettingsScreen() {
    val nav = LocalNavigator.current
    ScalePage(title = stringResource(R.string.settings)) {
        item {
            Section {
                SettingRow(stringResource(R.string.settings_general), value = stringResource(R.string.settings_general_summary), icon = YoinSymbols.Settings, onClick = { nav.push(Route.SettingsGeneral) })
                SettingRow(stringResource(R.string.settings_users), value = stringResource(R.string.settings_users_summary), icon = YoinSymbols.Person, onClick = { nav.push(Route.SettingsUsers) })
                SettingRow(stringResource(R.string.settings_goals), value = stringResource(R.string.settings_goals_summary), icon = ScaleSymbols.Target, onClick = { nav.push(Route.SettingsGoals) })
            }
        }
        item {
            Section {
                SettingRow(stringResource(R.string.settings_metrics), value = stringResource(R.string.settings_metrics_summary), icon = ScaleSymbols.Ruler, onClick = { nav.push(Route.SettingsMetrics) })
                SettingRow(stringResource(R.string.settings_chart), value = stringResource(R.string.settings_chart_summary), icon = ScaleSymbols.Chart, onClick = { nav.push(Route.SettingsChart) })
            }
        }
        item {
            Section {
                SettingRow(stringResource(R.string.bluetooth), value = stringResource(R.string.settings_bluetooth_summary), icon = ScaleSymbols.Bluetooth, onClick = { nav.push(Route.SettingsBluetooth) })
                SettingRow(stringResource(R.string.settings_reminder), value = stringResource(R.string.settings_reminder_summary), icon = PiliSymbols.Bell, onClick = { nav.push(Route.SettingsReminder) })
                SettingRow(stringResource(R.string.settings_data), value = stringResource(R.string.settings_data_summary), icon = YoinSymbols.Storage, onClick = { nav.push(Route.SettingsData) })
                SettingRow(stringResource(R.string.google_health), value = stringResource(R.string.google_health_summary), icon = YoinSymbols.Cloud, onClick = { nav.push(Route.SettingsGoogleHealth) })
            }
        }
        item {
            Section {
                SettingRow(stringResource(R.string.about), icon = YoinSymbols.Info, onClick = { nav.push(Route.About) })
            }
        }
    }
}
