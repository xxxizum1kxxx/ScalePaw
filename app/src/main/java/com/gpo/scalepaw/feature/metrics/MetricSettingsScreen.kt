package com.gpo.scalepaw.feature.metrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.scalepaw.R
import com.gpo.scalepaw.container
import com.gpo.scalepaw.data.model.MetricType
import com.gpo.scalepaw.ui.component.MetricBadge
import com.gpo.scalepaw.ui.component.ScalePage
import com.gpo.scalepaw.ui.component.label
import com.gpo.scalepaw.ui.navigation.LocalNavigator
import com.gpo.scalepaw.ui.navigation.Route
import com.gpo.scalepaw.ui.theme.YoinContainerShapes
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.launch

@Composable
fun MetricSettingsScreen() {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val state by context.container.session.state.collectAsStateWithLifecycle()
    val repository = context.container.repository
    val types = state.types.filter { !it.isInternal }

    fun move(from: Int, to: Int) {
        if (to !in types.indices) return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        val reordered = types.toMutableList().apply { add(to, removeAt(from)) }
        scope.launch { repository.reorderTypes(reordered + state.types.filter { it.isInternal }) }
    }

    ScalePage(
        title = stringResource(R.string.settings_metrics),
        floatingAction = {
            ExtendedFloatingActionButton(
                onClick = { nav.push(Route.MetricEdit()) },
                icon = { Icon(YoinSymbols.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.metric_add)) },
            )
        },
    ) {
        itemsIndexed(types, key = { _, t -> t.id }) { index, type ->
            MetricRow(
                type = type,
                onClick = { nav.push(Route.MetricEdit(type.id)) },
                onToggle = { on -> scope.launch { repository.updateType(type.copy(isEnabled = on)) } },
                onUp = if (index > 0) ({ move(index, index - 1) }) else null,
                onDown = if (index < types.lastIndex) ({ move(index, index + 1) }) else null,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun MetricRow(
    type: MetricType,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    modifier: Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(YoinContainerShapes.ListRow)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetricBadge(type.icon, type.color, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(type.label(), style = MaterialTheme.typography.bodyLarge)
            val tags = buildList {
                if (type.unit.symbol.isNotEmpty()) add(type.unit.symbol)
                if (type.isDerived) add(stringResource(R.string.metric_tag_derived))
                if (type.isPinned) add(stringResource(R.string.metric_tag_pinned))
                if (!type.isBuiltin) add(stringResource(if (type.isUserOwned) R.string.metric_tag_custom else R.string.metric_tag_device))
            }
            if (tags.isNotEmpty()) {
                Text(tags.joinToString("  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = type.isEnabled, onCheckedChange = onToggle)
        Column {
            IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null, shapes = IconButtonDefaults.shapes()) {
                Icon(YoinSymbols.ChevronUp, contentDescription = stringResource(R.string.action_move_up))
            }
            IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null, shapes = IconButtonDefaults.shapes()) {
                Icon(YoinSymbols.ChevronDown, contentDescription = stringResource(R.string.action_move_down))
            }
        }
    }
}
