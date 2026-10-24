package com.izum1k.scalepaw.feature.insights

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.domain.Anomaly
import com.izum1k.scalepaw.domain.CompositionInsight
import com.izum1k.scalepaw.domain.CompositionPattern
import com.izum1k.scalepaw.domain.Confidence
import com.izum1k.scalepaw.domain.SeasonalInsight
import com.izum1k.scalepaw.domain.SeriesAnalysis
import com.izum1k.scalepaw.domain.Trend
import com.izum1k.scalepaw.domain.Volatility
import com.izum1k.scalepaw.domain.WeekdayInsight
import com.izum1k.scalepaw.ui.component.EmptyState
import com.izum1k.scalepaw.ui.component.Fmt
import com.izum1k.scalepaw.ui.component.LoadingState
import com.izum1k.scalepaw.ui.component.LocalBottomBarInset
import com.izum1k.scalepaw.ui.component.MetricChip
import com.izum1k.scalepaw.ui.component.PillShape
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.component.containerViewModel
import com.izum1k.scalepaw.ui.component.label
import com.izum1k.scalepaw.ui.component.readingWidth
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.izum1k.scalepaw.ui.navigation.Route
import com.izum1k.scalepaw.ui.shell.ShellHeader
import com.izum1k.scalepaw.ui.theme.YoinContainerShapes
import com.izum1k.scalepaw.ui.theme.YoinMotion
import com.gpo.yoin.symbols.PiliSymbols
import com.gpo.yoin.symbols.YoinSymbols
import java.time.DayOfWeek
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@Composable
fun InsightsScreen() {
    val vm = containerViewModel { InsightsViewModel(it.session) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ShellHeader(stringResource(R.string.tab_insights))
        val primary = state.primary
        val report = state.report
        when {
            !state.loaded -> LoadingState()
            primary == null || report == null -> EmptyState(YoinSymbols.Insights, stringResource(R.string.chart_no_data))
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(320.dp),
                    modifier = Modifier.readingWidth(1200.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarInset.current + 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.types.forEach { t -> MetricChip(t, t.id == primary.id) { vm.select(t.id) } }
                        }
                    }
                    val nothing = report.analysis == null && report.composition == null && report.weekday == null &&
                        report.seasonal == null && report.anomalies.isEmpty()
                    if (nothing) {
                        item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(YoinSymbols.Insights, stringResource(R.string.insights_not_enough)) }
                    }
                    report.analysis?.let { a -> item { TrendCard(a) } }
                    report.composition?.let { c -> item { CompositionCard(c, state.fat, state.muscle) } }
                    report.weekday?.let { w -> item { WeekdayCard(w, primary) } }
                    report.seasonal?.let { s -> item { SeasonalCard(s, primary) } }
                    if (report.anomalies.isNotEmpty()) item { AnomalyCard(report.anomalies, primary) }
                }
            }
        }
    }
}

@Composable
private fun InsightCard(icon: ImageVector, title: String, confidence: Confidence? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(YoinContainerShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (confidence != null && confidence != Confidence.HIGH) {
                Text(
                    stringResource(if (confidence == Confidence.LOW) R.string.confidence_low else R.string.confidence_insufficient),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        content()
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Trend.label(): String = stringResource(
    when (this) {
        Trend.UP -> R.string.trend_up
        Trend.DOWN -> R.string.trend_down
        Trend.STABLE -> R.string.trend_stable
    },
)

@Composable
private fun TrendCard(a: SeriesAnalysis) {
    val type = a.type
    InsightCard(ScaleSymbols.Trend, stringResource(R.string.insight_trend), a.confidence) {
        Fact(stringResource(R.string.insight_short_term), a.shortTerm.label())
        Fact(stringResource(R.string.insight_long_term), a.longTerm.label())
        Fact(stringResource(R.string.insight_rate), stringResource(R.string.per_month, "${Fmt.signed(a.ratePerMonth, type)} ${type.unit.symbol}".trim()))
        Fact(
            stringResource(R.string.insight_volatility),
            stringResource(
                when (a.volatility) {
                    Volatility.STABLE -> R.string.volatility_stable
                    Volatility.MODERATE -> R.string.volatility_moderate
                    Volatility.HIGH -> R.string.volatility_high
                },
            ),
        )
        a.plateauDays?.let { Fact(stringResource(R.string.insight_plateau), pluralStringResource(R.plurals.days_count, it, it)) }
        Fact(stringResource(R.string.insight_range), "${Fmt.value(a.stats.min.value, type)} – ${Fmt.value(a.stats.max.value, type)} ${type.unit.symbol}")
    }
}

@Composable
private fun CompositionCard(c: CompositionInsight, fat: MetricType?, muscle: MetricType?) {
    InsightCard(ScaleSymbols.Muscle, stringResource(R.string.insight_composition), c.confidence) {
        Text(
            stringResource(
                when (c.pattern) {
                    CompositionPattern.FAT_GAIN -> R.string.pattern_fat_gain
                    CompositionPattern.MUSCLE_GAIN -> R.string.pattern_muscle_gain
                    CompositionPattern.MUSCLE_AND_FAT_GAIN -> R.string.pattern_muscle_and_fat_gain
                    CompositionPattern.WEIGHT_LOSS_MIXED -> R.string.pattern_weight_loss_mixed
                    CompositionPattern.FAT_LOSS -> R.string.pattern_fat_loss
                    CompositionPattern.RECOMPOSITION -> R.string.pattern_recomposition
                    CompositionPattern.STABLE -> R.string.pattern_stable
                },
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (c.confidence != Confidence.INSUFFICIENT) {
            fat?.let { Fact(it.label(), "${Fmt.signed(c.fatDelta, it)} ${it.unit.symbol}") }
            muscle?.let { Fact(it.label(), "${Fmt.signed(c.muscleDelta, it)} ${it.unit.symbol}") }
        }
        Text(
            pluralStringResource(R.plurals.insight_based_on, c.basedOn, c.basedOn),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Bars around a zero line: how far each weekday's mean sits from the overall mean. */
@Composable
private fun WeekdayCard(w: WeekdayInsight, type: MetricType) {
    val max = w.deviationByDay.values.maxOfOrNull { abs(it) }?.takeIf { it > 0f } ?: 1f
    InsightCard(ScaleSymbols.Calendar, stringResource(R.string.insight_weekday), w.confidence) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DayOfWeek.entries.forEach { day ->
                val dev = w.deviationByDay[day] ?: 0f
                Bar(
                    fraction = dev / max,
                    label = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    highlight = day == w.heaviest || day == w.lightest,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        w.heaviest?.let { Fact(stringResource(R.string.insight_highest_day), it.getDisplayName(TextStyle.FULL, Locale.getDefault())) }
        w.lightest?.let { Fact(stringResource(R.string.insight_lowest_day), it.getDisplayName(TextStyle.FULL, Locale.getDefault())) }
        w.heaviest?.let { h ->
            w.lightest?.let { l ->
                val spread = (w.deviationByDay[h] ?: 0f) - (w.deviationByDay[l] ?: 0f)
                Fact(stringResource(R.string.insight_spread), "${Fmt.value(spread, type)} ${type.unit.symbol}")
            }
        }
    }
}

@Composable
private fun SeasonalCard(s: SeasonalInsight, type: MetricType) {
    val values = s.averageByMonth.values
    val lo = values.minOrNull() ?: 0f
    val hi = values.maxOrNull() ?: 1f
    val span = (hi - lo).takeIf { it > 0f } ?: 1f
    InsightCard(ScaleSymbols.Chart, stringResource(R.string.insight_seasonal)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Month.entries.forEach { month ->
                val v = s.averageByMonth[month]
                Bar(
                    fraction = if (v == null) 0f else 0.15f + 0.85f * (v - lo) / span,
                    label = month.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    highlight = month == s.highest || month == s.lowest,
                    modifier = Modifier.weight(1f),
                    centered = false,
                )
            }
        }
        s.highest?.let { Fact(stringResource(R.string.insight_highest_month), it.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())) }
        s.lowest?.let { Fact(stringResource(R.string.insight_lowest_month), it.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())) }
        Fact(stringResource(R.string.insight_range), "${Fmt.value(lo, type)} – ${Fmt.value(hi, type)} ${type.unit.symbol}")
        Text(
            pluralStringResource(R.plurals.years_count, s.years, s.years),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A vertical bar; centred bars grow up or down from the middle line. */
@Composable
private fun Bar(fraction: Float, label: String, highlight: Boolean, modifier: Modifier, centered: Boolean = true) {
    val f by animateFloatAsState(fraction, YoinMotion.defaultSpatialSpec(), label = "bar")
    val color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(if (centered) 0.5f else 1f)) {
            if (centered) {
                val h = abs(f).coerceIn(0.04f, 1f) / 2
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(0.5f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        if (f >= 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(h * 2).clip(CircleShape).background(color))
                    }
                    Box(Modifier.weight(0.5f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        if (f < 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(h * 2).clip(CircleShape).background(color))
                    }
                }
            } else {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(f.coerceIn(0f, 1f))
                        .clip(YoinContainerShapes.ListRow)
                        .background(color),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnomalyCard(anomalies: List<Anomaly>, type: MetricType) {
    val nav = LocalNavigator.current
    InsightCard(PiliSymbols.Bell, stringResource(R.string.insight_anomalies)) {
        anomalies.take(8).forEach { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(YoinContainerShapes.ListRow)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { nav.push(Route.MeasurementDetail(a.measurementId)) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.weekdayDate(a.time), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.insight_expected, "${Fmt.value(a.expected, type)} ${type.unit.symbol}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${Fmt.value(a.value, type)} ${type.unit.symbol}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        if (anomalies.size > 8) {
            Text(
                pluralStringResource(R.plurals.more_count, anomalies.size - 8, anomalies.size - 8),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
