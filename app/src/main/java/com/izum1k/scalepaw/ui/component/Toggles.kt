package com.izum1k.scalepaw.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.ui.theme.YoinMotion

/** Expressive connected button group for a single choice (ranges, aggregation levels). */
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = {
                    if (option != selected) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(option)
                    }
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
            ) {
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A metric toggle: a pill filled with the metric's colour when on, a quiet block when off. */
@Composable
fun MetricChip(type: MetricType, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val tint = Color(type.color)
    val container by animateColorAsState(
        if (selected) tint.copy(alpha = 0.28f).compositeOver(MaterialTheme.colorScheme.surface) else MaterialTheme.colorScheme.surfaceContainerHigh,
        YoinMotion.defaultEffectsSpec(),
        label = "chip",
    )
    Row(
        Modifier
            .clip(PillShape)
            .background(container)
            .clickable {
                haptics.performHapticFeedback(if (selected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(type.icon.vector(), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(6.dp))
        Text(type.label(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
