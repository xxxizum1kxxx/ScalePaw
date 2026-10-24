package com.izum1k.scalepaw.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.data.model.EvaluationState
import com.izum1k.scalepaw.data.model.Measurement
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.domain.EvaluationResult
import com.izum1k.scalepaw.ui.theme.YoinContainerShapes
import com.izum1k.scalepaw.ui.theme.YoinMotion

@Composable
fun EvaluationState.color(): Color = when (this) {
    EvaluationState.LOW -> MaterialTheme.colorScheme.tertiary
    EvaluationState.NORMAL -> MaterialTheme.colorScheme.primary
    EvaluationState.HIGH -> MaterialTheme.colorScheme.error
    EvaluationState.UNDEFINED -> MaterialTheme.colorScheme.outline
}

@Composable
fun EvaluationState.label(): String = stringResource(
    when (this) {
        EvaluationState.LOW -> R.string.eval_low
        EvaluationState.NORMAL -> R.string.eval_normal
        EvaluationState.HIGH -> R.string.eval_high
        EvaluationState.UNDEFINED -> R.string.eval_undefined
    },
)

/**
 * The reference band as three soft segments (low, normal, high) with a marker for the value.
 * The visible scale spans the band plus half its width on either side.
 */
@Composable
fun EvaluationBar(
    result: EvaluationResult,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val colors = MaterialTheme.colorScheme
    val width = (result.high - result.low).coerceAtLeast(0.1f)
    val min = result.low - width / 2
    val max = result.high + width / 2
    val target = ((result.value - min) / (max - min)).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(target, YoinMotion.defaultSpatialSpec(), label = "eval")
    val marker = result.state.color()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        val h = 6.dp.toPx()
        val y = (size.height - h) / 2
        val gap = 3.dp.toPx()
        val third = (size.width - gap * 2) / 4
        val segments = listOf(
            colors.tertiary.copy(alpha = 0.55f) to third,
            colors.primary.copy(alpha = 0.55f) to third * 2,
            colors.error.copy(alpha = 0.55f) to third,
        )
        var x = 0f
        segments.forEach { (c, w) ->
            drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(h / 2))
            x += w + gap
        }
        val mx = fraction * size.width
        drawCircle(background, size.height / 2, Offset(mx, size.height / 2))
        drawCircle(marker, size.height / 2 - 2.dp.toPx(), Offset(mx, size.height / 2))
    }
}

/** A row in a measurement list: date and time, the headline value with its change, and chips. */
@Composable
fun MeasurementRow(
    measurement: Measurement,
    primary: MetricType?,
    delta: Float?,
    secondary: List<MetricType>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(YoinContainerShapes.ListRow)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.weekdayDate(measurement.timestamp), style = MaterialTheme.typography.titleSmall)
            Text(
                Fmt.time(context, measurement.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val chips = secondary.mapNotNull { t -> measurement.number(t)?.let { "${t.label(context)} ${Fmt.value(it, t)}${t.unit.symbol}" } }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.take(3).forEach { chip ->
                        Text(
                            chip,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        if (primary != null) {
            val value = measurement.number(primary)
            Column(horizontalAlignment = Alignment.End) {
                if (value != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Fmt.value(value, primary), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(2.dp))
                        Text(
                            primary.unit.symbol,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
                if (delta != null && delta != 0f) DeltaText(delta, primary)
            }
        }
    }
}

@Composable
fun DeltaText(delta: Float, type: MetricType) {
    Text(
        "${Fmt.signed(delta, type)} ${type.unit.symbol}".trim(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
