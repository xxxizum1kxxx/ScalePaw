package com.gpo.scalepaw.feature.table

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.scalepaw.R
import com.gpo.scalepaw.container
import com.gpo.scalepaw.data.model.AggregationLevel
import com.gpo.scalepaw.data.model.MetricType
import com.gpo.scalepaw.domain.Analytics
import com.gpo.scalepaw.ui.component.ConnectedChoice
import com.gpo.scalepaw.ui.component.EmptyState
import com.gpo.scalepaw.ui.component.Fmt
import com.gpo.scalepaw.ui.component.LoadingState
import com.gpo.scalepaw.ui.component.LocalBottomBarInset
import com.gpo.scalepaw.ui.component.ScaleSymbols
import com.gpo.scalepaw.ui.component.label
import com.gpo.scalepaw.ui.navigation.LocalNavigator
import com.gpo.scalepaw.ui.navigation.Route
import com.gpo.scalepaw.ui.shell.ShellHeader
import com.gpo.scalepaw.ui.theme.YoinContainerShapes
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private val DateColumn = 132.dp
private val ValueColumn = 92.dp

/** Every entry as a row, every visible metric as a column; or one row per period when aggregated. */
@Composable
fun TableScreen() {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by context.container.session.state.collectAsStateWithLifecycle()
    val level = state.settings.tableAggregation
    val columns = state.visibleTypes
    val horizontal = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        ShellHeader(stringResource(R.string.tab_table))
        if (!state.loaded) {
            LoadingState()
            return@Column
        }
        ConnectedChoice(
            AggregationLevel.entries,
            level,
            { stringResource(it.label) },
            { scope.launch { context.container.settings.setTableAggregation(it) } },
            Modifier.padding(horizontal = 16.dp),
        )
        if (state.measurements.isEmpty()) {
            EmptyState(ScaleSymbols.Table, stringResource(R.string.overview_empty))
            return@Column
        }
        val periods = remember(state.measurements, columns, level) { Analytics.aggregate(state.measurements, columns.filter { it.isNumeric }, level) }
        Box(Modifier.fillMaxSize().padding(top = 12.dp)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = LocalBottomBarInset.current + 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                stickyHeader {
                    HeaderRow(columns, horizontal)
                }
                if (level == AggregationLevel.NONE) {
                    items(state.measurements, key = { it.id }) { m ->
                        TableRow(
                            first = Fmt.shortDate(context, m.timestamp),
                            second = Fmt.time(context, m.timestamp),
                            columns = columns,
                            horizontal = horizontal,
                            onClick = { nav.push(Route.MeasurementDetail(m.id)) },
                        ) { type ->
                            m.values[type.id]?.let { v -> v.number?.let { Fmt.value(it, type) } ?: v.text }
                        }
                    }
                } else {
                    items(periods, key = { it.start }) { p ->
                        TableRow(
                            first = periodLabel(level, p.start, context),
                            second = pluralStringResource(R.plurals.entries_count, p.count, p.count),
                            columns = columns,
                            horizontal = horizontal,
                            onClick = { nav.push(Route.PeriodEntries(p.start, p.endExclusive)) },
                        ) { type -> p.averages[type.id]?.let { Fmt.value(it, type) } }
                    }
                }
            }
        }
    }
}

fun periodLabel(level: AggregationLevel, start: Long, context: Context): String = when (level) {
    AggregationLevel.MONTH -> Fmt.monthYear(start)
    AggregationLevel.YEAR -> Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).year.toString()
    AggregationLevel.WEEK -> context.getString(R.string.period_week_of, Fmt.shortDate(context, start))
    else -> Fmt.weekdayDate(start)
}

@Composable
private fun HeaderRow(columns: List<MetricType>, horizontal: ScrollState) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(stringResource(R.string.field_date), DateColumn, header = true, align = TextAlign.Start)
        Row(Modifier.horizontalScroll(horizontal)) {
            columns.forEach { t ->
                Cell(if (t.unit.symbol.isEmpty()) t.label() else "${t.label()}\n${t.unit.symbol}", ValueColumn, header = true)
            }
        }
    }
}

@Composable
private fun TableRow(
    first: String,
    second: String,
    columns: List<MetricType>,
    horizontal: ScrollState,
    onClick: () -> Unit,
    value: (MetricType) -> String?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(YoinContainerShapes.ListRow)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(DateColumn).padding(start = 12.dp)) {
            Text(first, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Row(Modifier.horizontalScroll(horizontal)) {
            columns.forEach { t -> Cell(value(t) ?: "", ValueColumn) }
        }
    }
}

@Composable
private fun Cell(text: String, width: Dp, header: Boolean = false, align: TextAlign = TextAlign.End) {
    Text(
        text,
        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = align,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 12.dp),
    )
}
