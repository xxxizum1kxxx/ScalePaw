package com.izum1k.scalepaw.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.data.model.AggregationLevel
import com.izum1k.scalepaw.data.model.SmoothingAlgorithm
import com.izum1k.scalepaw.data.model.TimeRange
import com.izum1k.scalepaw.ui.component.ChoiceRow
import com.izum1k.scalepaw.ui.component.Fmt
import com.izum1k.scalepaw.ui.component.ScalePage
import com.izum1k.scalepaw.ui.component.Section
import com.izum1k.scalepaw.ui.component.SettingRow
import com.izum1k.scalepaw.ui.component.SwitchRow
import com.izum1k.scalepaw.ui.component.containerViewModel

@Composable
fun ChartSettingsScreen() {
    val vm = containerViewModel { SettingsViewModel(it.session, it.settings, it.repository) }
    val s by vm.state.collectAsStateWithLifecycle()
    val c = s.chart

    ScalePage(title = stringResource(R.string.settings_chart)) {
        item {
            Section {
                ChoiceRow(stringResource(R.string.setting_default_range), TimeRange.entries, c.range, { stringResource(it.label) }, { r -> vm.chart { it.copy(range = r) } })
                ChoiceRow(stringResource(R.string.setting_aggregation), AggregationLevel.entries, c.aggregation, { stringResource(it.label) }, { a -> vm.chart { it.copy(aggregation = a) } })
                SwitchRow(stringResource(R.string.setting_show_points), c.showPoints, { v -> vm.chart { it.copy(showPoints = v) } })
                SwitchRow(stringResource(R.string.setting_show_goal), c.showGoalLine, { v -> vm.chart { it.copy(showGoalLine = v) } })
                SwitchRow(stringResource(R.string.setting_show_trend), c.showTrendLine, { v -> vm.chart { it.copy(showTrendLine = v) } })
            }
        }
        item {
            Section(title = stringResource(R.string.section_smoothing)) {
                ChoiceRow(stringResource(R.string.setting_smoothing), SmoothingAlgorithm.entries, c.smoothing, { stringResource(it.label) }, { a -> vm.chart { it.copy(smoothing = a) } })
                when (c.smoothing) {
                    SmoothingAlgorithm.SIMPLE_MOVING_AVERAGE -> {
                        SettingRow(stringResource(R.string.setting_window), value = c.smoothingWindow.toString())
                        Slider(
                            value = c.smoothingWindow.toFloat(),
                            onValueChange = { v -> vm.chart { it.copy(smoothingWindow = v.toInt()) } },
                            valueRange = 2f..30f,
                            steps = 27,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    SmoothingAlgorithm.EXPONENTIAL -> {
                        SettingRow(stringResource(R.string.setting_alpha), value = Fmt.number(c.smoothingAlpha, 2))
                        Slider(
                            value = c.smoothingAlpha,
                            onValueChange = { v -> vm.chart { it.copy(smoothingAlpha = v) } },
                            valueRange = 0.05f..1f,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    SmoothingAlgorithm.NONE -> Unit
                }
            }
        }
    }
}
