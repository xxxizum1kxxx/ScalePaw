package com.gpo.scalepaw.feature.table

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.scalepaw.R
import com.gpo.scalepaw.container
import com.gpo.scalepaw.data.model.Metrics
import com.gpo.scalepaw.domain.Analytics
import com.gpo.scalepaw.ui.component.EmptyState
import com.gpo.scalepaw.ui.component.Fmt
import com.gpo.scalepaw.ui.component.MeasurementRow
import com.gpo.scalepaw.ui.component.ScalePage
import com.gpo.scalepaw.ui.component.ScaleSymbols
import com.gpo.scalepaw.ui.navigation.LocalNavigator
import com.gpo.scalepaw.ui.navigation.Route

/** The entries behind one aggregated table row. */
@Composable
fun PeriodEntriesScreen(start: Long, endExclusive: Long) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val state by context.container.session.state.collectAsStateWithLifecycle()
    val entries = state.measurements.filter { it.timestamp in start until endExclusive }
    val primary = state.type(Metrics.WEIGHT)?.takeIf { it.isVisible } ?: state.numericTypes.firstOrNull()
    val secondary = state.visibleTypes.filter { it.isPinned && it.id != primary?.id }

    ScalePage(title = "${Fmt.date(start)} – ${Fmt.date(endExclusive - 1)}") {
        if (entries.isEmpty()) {
            item { EmptyState(ScaleSymbols.Table, stringResource(R.string.chart_no_data)) }
        }
        items(entries, key = { it.id }) { m ->
            MeasurementRow(
                measurement = m,
                primary = primary,
                delta = primary?.let { Analytics.previousDifference(state.measurements, it, m) },
                secondary = secondary,
                onClick = { nav.push(Route.MeasurementDetail(m.id)) },
            )
        }
    }
}
