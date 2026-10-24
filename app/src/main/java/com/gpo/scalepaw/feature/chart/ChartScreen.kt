package com.gpo.scalepaw.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.scalepaw.R
import com.gpo.scalepaw.data.model.AggregationLevel
import com.gpo.scalepaw.data.model.TimeRange
import com.gpo.scalepaw.ui.chart.ChartSeries
import com.gpo.scalepaw.ui.chart.LineChart
import com.gpo.scalepaw.ui.component.ConnectedChoice
import com.gpo.scalepaw.ui.component.EmptyState
import com.gpo.scalepaw.ui.component.Fmt
import com.gpo.scalepaw.ui.component.LoadingState
import com.gpo.scalepaw.ui.component.LocalBottomBarInset
import com.gpo.scalepaw.ui.component.MetricChip
import com.gpo.scalepaw.ui.component.PillShape
import com.gpo.scalepaw.ui.component.ScaleSymbols
import com.gpo.scalepaw.ui.component.WidthClass
import com.gpo.scalepaw.ui.component.containerViewModel
import com.gpo.scalepaw.ui.component.label
import com.gpo.scalepaw.ui.component.widthClass
import com.gpo.scalepaw.ui.navigation.LocalNavigator
import com.gpo.scalepaw.ui.navigation.Route
import com.gpo.scalepaw.ui.shell.ShellHeader
import com.gpo.scalepaw.ui.theme.YoinContainerShapes
import com.gpo.yoin.symbols.YoinSymbols

@Composable
fun ChartScreen() {
    val vm = containerViewModel { ChartViewModel(it.session, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val expanded = widthClass() == WidthClass.Expanded

    Column(Modifier.fillMaxSize()) {
        ShellHeader(stringResource(R.string.tab_chart))
        when {
            !state.loaded -> LoadingState()
            state.types.isEmpty() -> EmptyState(ScaleSymbols.Chart, stringResource(R.string.chart_empty))
            expanded -> Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = LocalBottomBarInset.current),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxSize()) {
                    RangeBar(state, vm)
                    ChartPanel(state, selected, { selected = it }, Modifier.weight(1f))
                    SelectionPanel(state, selected)
                }
                Column(
                    Modifier
                        .width(320.dp)
                        .verticalScroll(rememberScrollState()),
                ) { MetricPicker(state, vm) }
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = LocalBottomBarInset.current),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RangeBar(state, vm)
                ChartPanel(state, selected, { selected = it }, Modifier.height(320.dp))
                SelectionPanel(state, selected)
                MetricPicker(state, vm)
            }
        }
    }
}

@Composable
private fun RangeBar(state: ChartUiState, vm: ChartViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConnectedChoice(TimeRange.entries, state.settings.range, { stringResource(it.label) }, vm::setRange)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AggregationLevel.entries.forEach { level ->
                val on = level == state.settings.aggregation
                Text(
                    stringResource(level.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(PillShape)
                        .background(if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { vm.setAggregation(level) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ChartPanel(state: ChartUiState, selected: Long?, onSelect: (Long?) -> Unit, modifier: Modifier) {
    val series = state.series.map { s ->
        ChartSeries(
            id = s.type.id,
            label = s.type.label(),
            color = Color(s.type.color),
            points = s.points,
            decimals = Fmt.decimals(s.type),
            rightAxis = s.type.isOnRightAxis,
            goal = s.goal,
            smoothed = s.smoothed,
            trend = s.trend,
        )
    }
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(YoinContainerShapes.Panel)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp),
    ) {
        if (series.all { it.points.isEmpty() }) {
            EmptyState(ScaleSymbols.Chart, stringResource(R.string.chart_no_data), Modifier.align(Alignment.Center))
        } else {
            LineChart(
                series = series,
                showPoints = state.settings.showPoints,
                showGoal = state.settings.showGoalLine,
                showTrend = state.settings.showTrendLine,
                selected = selected,
                onSelect = onSelect,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Values at the scrubbed point; opens the entry when the chart shows single measurements. */
@Composable
private fun SelectionPanel(state: ChartUiState, selected: Long?) {
    val time = selected ?: return
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val entry = state.measurements.firstOrNull { it.timestamp == time }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(YoinContainerShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (state.settings.aggregation == AggregationLevel.NONE) "${Fmt.weekdayDate(time)}  ${Fmt.time(context, time)}" else Fmt.date(time),
                style = MaterialTheme.typography.titleSmall,
            )
            state.series.forEach { s ->
                s.points.firstOrNull { it.time == time }?.let { p ->
                    Text(
                        "${s.type.label()}  ${Fmt.value(p.value, s.type)} ${s.type.unit.symbol}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (entry != null && state.settings.aggregation == AggregationLevel.NONE) {
            FilledTonalButton(onClick = { nav.push(Route.MeasurementDetail(entry.id)) }) {
                Text(stringResource(R.string.action_open))
                Spacer(Modifier.width(4.dp))
                Icon(YoinSymbols.ChevronRight, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricPicker(state: ChartUiState, vm: ChartViewModel) {
    Text(
        stringResource(R.string.chart_metrics),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.types.forEach { type -> MetricChip(type, type.id in state.selected) { vm.toggle(type.id) } }
    }
}
