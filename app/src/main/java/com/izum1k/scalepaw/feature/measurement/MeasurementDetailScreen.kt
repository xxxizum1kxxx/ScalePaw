package com.izum1k.scalepaw.feature.measurement

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.domain.Analytics
import com.izum1k.scalepaw.domain.Evaluation
import com.izum1k.scalepaw.domain.evaluate
import com.izum1k.scalepaw.ui.component.ConfirmDialog
import com.izum1k.scalepaw.ui.component.DeltaText
import com.izum1k.scalepaw.ui.component.EmptyState
import com.izum1k.scalepaw.ui.component.EvaluationBar
import com.izum1k.scalepaw.ui.component.Fmt
import com.izum1k.scalepaw.ui.component.MetricBadge
import com.izum1k.scalepaw.ui.component.ScalePage
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.component.color
import com.izum1k.scalepaw.ui.component.label
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.izum1k.scalepaw.ui.navigation.Route
import com.izum1k.scalepaw.ui.theme.YoinContainerShapes
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.launch

@Composable
fun MeasurementDetailScreen(id: Long) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by context.container.session.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val index = state.measurements.indexOfFirst { it.id == id }
    val measurement = state.measurements.getOrNull(index)
    val user = state.user

    ScalePage(
        title = measurement?.let { Fmt.weekdayDate(it.timestamp) } ?: stringResource(R.string.measurement),
        actions = {
            if (measurement != null) {
                IconButton(onClick = { nav.push(Route.MeasurementEdit(id)) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(YoinSymbols.Edit, contentDescription = stringResource(R.string.action_edit))
                }
                IconButton(onClick = { confirmDelete = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(YoinSymbols.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        },
    ) {
        if (measurement == null || user == null) {
            item { EmptyState(ScaleSymbols.Scale, stringResource(R.string.measurement_missing)) }
            return@ScalePage
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Fmt.time(context, measurement.timestamp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                // Newest first: the older entry sits after this one in the list.
                state.measurements.getOrNull(index + 1)?.let { older ->
                    FilledTonalButton(onClick = { nav.replace(Route.MeasurementDetail(older.id)) }) {
                        Icon(YoinSymbols.ChevronLeft, contentDescription = null)
                        Text(stringResource(R.string.action_older))
                    }
                }
                state.measurements.getOrNull(index - 1)?.let { newer ->
                    FilledTonalButton(onClick = { nav.replace(Route.MeasurementDetail(newer.id)) }) {
                        Text(stringResource(R.string.action_newer))
                        Icon(YoinSymbols.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
        val shown = state.visibleTypes.filter { measurement.values[it.id]?.isEmpty == false }
        items(shown, key = { it.id }) { type ->
            ValueRow(type, measurement.values.getValue(type.id).let { v -> v.number?.let { Fmt.value(it, type) } ?: v.text.orEmpty() }) {
                val number = measurement.number(type) ?: return@ValueRow
                Analytics.previousDifference(state.measurements, type, measurement)?.takeIf { it != 0f }?.let { DeltaText(it, type) }
                Evaluation.evaluate(type, number, user, measurement.timestamp)?.let { eval ->
                    Spacer(Modifier.padding(top = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            eval.state.label(),
                            style = MaterialTheme.typography.labelMedium,
                            color = eval.state.color(),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${Fmt.value(eval.low, type)} – ${Fmt.value(eval.high, type)} ${type.unit.symbol}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    EvaluationBar(eval)
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.measurement_delete_title),
            text = stringResource(R.string.measurement_delete_body),
            confirm = stringResource(R.string.action_delete),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                scope.launch {
                    context.container.repository.deleteMeasurement(id)
                    nav.pop()
                }
            },
        )
    }
}

@Composable
private fun ValueRow(type: MetricType, value: String, extra: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(YoinContainerShapes.ListRow)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MetricBadge(type.icon, type.color)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(type.label(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (type.unit.symbol.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        type.unit.symbol,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            extra()
        }
    }
}
