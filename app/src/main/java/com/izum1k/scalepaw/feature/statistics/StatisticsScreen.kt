package com.izum1k.scalepaw.feature.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.container
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.data.model.TimeRange
import com.izum1k.scalepaw.domain.Analytics
import com.izum1k.scalepaw.domain.Point
import com.izum1k.scalepaw.domain.SeriesStats
import com.izum1k.scalepaw.feature.chart.ChartViewModel
import com.izum1k.scalepaw.ui.chart.ChartSeries
import com.izum1k.scalepaw.ui.chart.LineChart
import com.izum1k.scalepaw.ui.component.ConnectedChoice
import com.izum1k.scalepaw.ui.component.DateDialog
import com.izum1k.scalepaw.ui.component.EmptyState
import com.izum1k.scalepaw.ui.component.Fmt
import com.izum1k.scalepaw.ui.component.LoadingState
import com.izum1k.scalepaw.ui.component.LocalBottomBarInset
import com.izum1k.scalepaw.ui.component.MetricBadge
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.component.label
import com.izum1k.scalepaw.ui.component.readingWidth
import com.izum1k.scalepaw.ui.component.toLocalDate
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.izum1k.scalepaw.ui.navigation.Route
import com.izum1k.scalepaw.ui.shell.ShellHeader
import com.izum1k.scalepaw.ui.theme.YoinContainerShapes
import java.time.ZoneId

private data class TypeStats(val type: MetricType, val stats: SeriesStats, val points: List<Point>)

@Composable
fun StatisticsScreen() {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val state by context.container.session.state.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(TimeRange.MONTH) }
    var pickFrom by remember { mutableStateOf(false) }
    var fromDate by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize()) {
        ShellHeader(stringResource(R.string.tab_statistics))
        if (!state.loaded) {
            LoadingState()
            return@Column
        }
        val inRange = remember(state.measurements, range) { ChartViewModel.filterRange(state.measurements, range) }
        val stats = remember(inRange, state.numericTypes) {
            state.numericTypes.mapNotNull { t ->
                val points = Analytics.series(inRange, t)
                Analytics.stats(points)?.let { TypeStats(t, it, points) }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                modifier = Modifier.readingWidth(1200.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarInset.current + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ConnectedChoice(TimeRange.entries, range, { stringResource(it.label) }, { range = it })
                }
                if (stats.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(ScaleSymbols.Statistics, stringResource(R.string.chart_no_data)) }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                pluralStringResource(R.plurals.entries_count, inRange.size, inRange.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(start = 4.dp),
                            )
                            FilledTonalButton(onClick = { pickFrom = true }) {
                                Icon(ScaleSymbols.Swap, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.compare))
                            }
                        }
                    }
                    items(stats, key = { it.type.id }) { StatCard(it) }
                }
            }
        }
    }

    if (pickFrom) {
        val zone = ZoneId.systemDefault()
        val oldest = state.measurements.lastOrNull()?.timestamp ?: System.currentTimeMillis()
        DateDialog(oldest.toLocalDate(), onDismiss = { pickFrom = false }) { d ->
            fromDate = d.atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }
    fromDate?.let { from ->
        val newest = state.measurements.firstOrNull()?.timestamp ?: System.currentTimeMillis()
        DateDialog(newest.toLocalDate(), onDismiss = { fromDate = null }) { d ->
            val to = d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            nav.push(Route.Comparison(minOf(from, to), maxOf(from, to)))
        }
    }
}

@Composable
private fun StatCard(s: TypeStats) {
    val type = s.type
    Column(
        Modifier
            .fillMaxWidth()
            .clip(YoinContainerShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetricBadge(type.icon, type.color, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(type.label(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${Fmt.signed(s.stats.change, type)} ${type.unit.symbol}".trim(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (s.points.size > 1) {
            LineChart(
                series = listOf(ChartSeries(type.id, type.label(), Color(type.color), s.points, decimals = Fmt.decimals(type))),
                showPoints = false,
                showAxes = false,
                fill = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Figure(stringResource(R.string.stat_min), s.stats.min.value, type, Modifier.weight(1f))
            Figure(stringResource(R.string.stat_avg), s.stats.average, type, Modifier.weight(1f))
            Figure(stringResource(R.string.stat_max), s.stats.max.value, type, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Figure(label: String, value: Float, type: MetricType, modifier: Modifier) {
    Column(
        modifier
            .clip(YoinContainerShapes.ListRow)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Fmt.value(value, type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
