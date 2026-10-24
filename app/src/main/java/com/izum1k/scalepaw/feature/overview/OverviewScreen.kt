package com.izum1k.scalepaw.feature.overview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.domain.Analytics
import com.izum1k.scalepaw.ui.chart.ChartSeries
import com.izum1k.scalepaw.ui.chart.LineChart
import com.izum1k.scalepaw.ui.component.DeltaText
import com.izum1k.scalepaw.ui.component.EmptyState
import com.izum1k.scalepaw.ui.component.EvaluationBar
import com.izum1k.scalepaw.ui.component.Fmt
import com.izum1k.scalepaw.ui.component.LoadingState
import com.izum1k.scalepaw.ui.component.LocalBottomBarInset
import com.izum1k.scalepaw.ui.component.MeasurementRow
import com.izum1k.scalepaw.ui.component.MetricBadge
import com.izum1k.scalepaw.ui.component.PillShape
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.component.color
import com.izum1k.scalepaw.ui.component.containerViewModel
import com.izum1k.scalepaw.ui.component.label
import com.izum1k.scalepaw.ui.component.readingWidth
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.izum1k.scalepaw.ui.navigation.Route
import com.izum1k.scalepaw.ui.shell.ShellHeader
import com.izum1k.scalepaw.ui.theme.YoinContainerShapes
import com.izum1k.scalepaw.ui.theme.YoinMotion
import kotlin.math.abs

@Composable
fun OverviewScreen() {
    val vm = containerViewModel { OverviewViewModel(it.session) }
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    Column(Modifier.fillMaxSize()) {
        ShellHeader(stringResource(R.string.tab_overview), showSync = true)
        when {
            !state.loaded -> LoadingState()
            state.latest == null -> EmptyState(ScaleSymbols.Scale, stringResource(R.string.overview_empty))
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(168.dp),
                    modifier = Modifier.readingWidth(1200.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = LocalBottomBarInset.current + 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Hero(state) }
                    state.goal?.let { goal ->
                        item(span = { GridItemSpan(maxLineSpan) }) { GoalCard(goal, state) }
                    }
                    items(state.tiles, key = { it.type.id }) { tile ->
                        MetricTile(tile, stale = tile.source.id != state.latest?.id, onClick = { nav.push(Route.MeasurementDetail(tile.source.id)) })
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.overview_recent),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        )
                    }
                    items(state.recent, key = { "m${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { m ->
                        MeasurementRow(
                            measurement = m,
                            primary = state.headline,
                            delta = state.headline?.let { Analytics.previousDifference(state.all, it, m) },
                            secondary = state.pinned,
                            onClick = { nav.push(Route.MeasurementDetail(m.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(state: OverviewUiState) {
    val type = state.headline ?: return
    val value = state.headlineValue
    val latest = state.latest ?: return
    val context = LocalContext.current
    val color = Color(type.color)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(YoinContainerShapes.Panel)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
    ) {
        Text(type.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value?.let { Fmt.value(it, type) } ?: "—",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                type.unit.symbol,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${Fmt.weekdayDate(latest.timestamp)}  ${Fmt.time(context, latest.timestamp)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f),
            )
            state.headlineDelta?.takeIf { it != 0f }?.let { DeltaText(it, type) }
        }
        state.headlineEvaluation?.let { eval ->
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.overview_target_range, Fmt.value(eval.low, type), Fmt.value(eval.high, type), type.unit.symbol),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            EvaluationBar(eval, background = MaterialTheme.colorScheme.primaryContainer)
        }
        if (state.recentSeries.size > 1) {
            Spacer(Modifier.height(16.dp))
            LineChart(
                series = listOf(ChartSeries(type.id, type.label(), color, state.recentSeries, decimals = Fmt.decimals(type))),
                showPoints = false,
                showGoal = false,
                showAxes = false,
                fill = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
        }
    }
}

@Composable
private fun GoalCard(goal: GoalProgress, state: OverviewUiState) {
    val type = state.headline ?: return
    val fraction by animateFloatAsState(goal.fraction, YoinMotion.defaultSpatialSpec(), label = "goal")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(YoinContainerShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.goal), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${Fmt.value(goal.goal.goalValue, type)} ${type.unit.symbol}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Text(
            if (abs(goal.remaining) < 0.05f) {
                stringResource(R.string.goal_reached)
            } else {
                stringResource(R.string.goal_remaining, "${Fmt.value(abs(goal.remaining), type)} ${type.unit.symbol}")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        goal.goal.targetDate?.let {
            Text(
                stringResource(R.string.goal_target_date, Fmt.date(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricTile(tile: Tile, stale: Boolean, onClick: () -> Unit) {
    val type = tile.type
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(YoinContainerShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetricBadge(type.icon, type.color, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                type.label(),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                tile.value.number?.let { Fmt.value(it, type) } ?: tile.value.text.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                type.unit.symbol,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            tile.evaluation?.let { eval ->
                Box(
                    Modifier
                        .padding(bottom = 8.dp)
                        .clip(PillShape)
                        .background(eval.state.color())
                        .width(10.dp)
                        .height(10.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            tile.delta?.takeIf { it != 0f }?.let { DeltaText(it, type) }
            if (stale) {
                Text(
                    Fmt.shortDate(context, tile.source.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
