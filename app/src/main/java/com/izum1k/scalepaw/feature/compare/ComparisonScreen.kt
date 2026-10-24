package com.izum1k.scalepaw.feature.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.container
import com.izum1k.scalepaw.data.model.Measurement
import com.izum1k.scalepaw.ui.component.EmptyState
import com.izum1k.scalepaw.ui.component.Fmt
import com.izum1k.scalepaw.ui.component.MetricBadge
import com.izum1k.scalepaw.ui.component.ScalePage
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.component.label
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.izum1k.scalepaw.ui.navigation.Route
import com.izum1k.scalepaw.ui.theme.YoinContainerShapes
import com.gpo.yoin.symbols.YoinSymbols
import kotlin.math.abs

/** The first and last entry inside [start]..[end], metric by metric. */
@Composable
fun ComparisonScreen(start: Long, end: Long) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val state by context.container.session.state.collectAsStateWithLifecycle()
    val inside = state.measurements.filter { it.timestamp in start..end }
    val from: Measurement? = inside.lastOrNull()
    val to: Measurement? = inside.firstOrNull()

    ScalePage(title = stringResource(R.string.compare)) {
        if (from == null || to == null || from.id == to.id) {
            item { EmptyState(ScaleSymbols.Swap, stringResource(R.string.compare_need_two)) }
            return@ScalePage
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateBlock(Fmt.date(from.timestamp), Modifier.weight(1f)) { nav.push(Route.MeasurementDetail(from.id)) }
                DateBlock(Fmt.date(to.timestamp), Modifier.weight(1f)) { nav.push(Route.MeasurementDetail(to.id)) }
            }
        }
        val rows = state.numericTypes.mapNotNull { t ->
            val first = inside.lastOrNull { it.number(t) != null } ?: return@mapNotNull null
            val last = inside.firstOrNull { it.number(t) != null } ?: return@mapNotNull null
            if (first.id == last.id) null else Triple(t, first.number(t)!!, last.number(t)!!)
        }
        items(rows, key = { it.first.id }) { (type, a, b) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(YoinContainerShapes.ListRow)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricBadge(type.icon, type.color, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(type.label(), style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Fmt.value(a, type), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(YoinSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Fmt.value(b, type), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${Fmt.signed(b - a, type)} ${type.unit.symbol}".trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (abs(a) > 1e-3f) {
                        Text(
                            "${Fmt.number((b - a) / a * 100f, 1)} %",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateBlock(text: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        Icon(ScaleSymbols.Calendar, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, maxLines = 1)
    }
}
