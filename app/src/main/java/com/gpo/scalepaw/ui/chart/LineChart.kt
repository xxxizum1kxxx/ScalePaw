package com.gpo.scalepaw.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.gpo.scalepaw.domain.Point
import com.gpo.scalepaw.ui.component.Fmt
import com.gpo.scalepaw.ui.theme.YoinMotion
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class ChartSeries(
    val id: Long,
    val label: String,
    val color: Color,
    val points: List<Point>,
    val decimals: Int = 1,
    val rightAxis: Boolean = false,
    val goal: Float? = null,
    /** Smoothed values drawn instead of the raw line; raw points still show as dots. */
    val smoothed: List<Float>? = null,
    val trend: Pair<Double, Double>? = null,
)

/**
 * A spring-revealed line chart drawn on Canvas. Up to two value axes, optional dots, dashed goal
 * line and least-squares trend; drag or tap to scrub, which reports the nearest timestamp.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    showPoints: Boolean = true,
    showGoal: Boolean = true,
    showTrend: Boolean = false,
    showAxes: Boolean = true,
    fill: Boolean = false,
    selected: Long? = null,
    onSelect: ((Long?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val reveal = remember { Animatable(0f) }
    val revealSpec = YoinMotion.defaultEffectsSpec<Float>()
    LaunchedEffect(series.map { it.id to it.points.size }) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, revealSpec)
    }
    val times = remember(series) { series.flatMap { s -> s.points.map { it.time } } }
    val tMin = times.minOrNull() ?: 0L
    val tMax = times.maxOrNull() ?: 1L
    var scrub by remember { mutableStateOf<Long?>(null) }
    val active = selected ?: scrub

    val leftPad = if (showAxes) 44.dp else 0.dp
    val rightPad = if (showAxes && series.any { it.rightAxis }) 44.dp else 0.dp
    val bottomPad = if (showAxes) 22.dp else 0.dp

    fun nearest(x: Float, width: Float, left: Float, right: Float): Long? {
        if (times.isEmpty()) return null
        val plotW = width - left - right
        val t = tMin + ((x - left) / plotW).coerceIn(0f, 1f) * (tMax - tMin)
        return times.minByOrNull { abs(it - t) }
    }

    Box(
        modifier.pointerInput(series, onSelect) {
            if (onSelect == null) return@pointerInput
            val l = leftPad.toPx()
            val r = rightPad.toPx()
            detectTapGestures { o ->
                val t = nearest(o.x, size.width.toFloat(), l, r)
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelect(if (t == active) null else t)
            }
        }.pointerInput(series, onSelect) {
            if (onSelect == null) return@pointerInput
            val l = leftPad.toPx()
            val r = rightPad.toPx()
            detectDragGestures(
                onDragEnd = { },
                onDrag = { change, _ ->
                    val t = nearest(change.position.x, size.width.toFloat(), l, r)
                    if (t != scrub) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    scrub = t
                    onSelect(t)
                },
            )
        },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val left = leftPad.toPx()
            val right = rightPad.toPx()
            val bottom = bottomPad.toPx()
            val top = 8.dp.toPx()
            val plot = Size(size.width - left - right, size.height - top - bottom)
            if (plot.width <= 0 || plot.height <= 0 || times.isEmpty()) return@Canvas

            val leftAxis = axisRange(series.filterNot { it.rightAxis }, showGoal)
            val rightAxis = axisRange(series.filter { it.rightAxis }, showGoal)
            val span = (tMax - tMin).coerceAtLeast(1L).toFloat()
            fun x(t: Long) = left + (t - tMin) / span * plot.width
            fun y(v: Float, axis: Axis) = top + plot.height * (1f - (v - axis.min) / (axis.max - axis.min))

            if (showAxes) {
                leftAxis?.let { axis ->
                    axis.ticks.forEach { v ->
                        val yy = y(v, axis)
                        drawLine(colors.surfaceContainerHighest, Offset(left, yy), Offset(left + plot.width, yy), 1.dp.toPx())
                        drawLabel(measurer, Fmt.number(v, axis.decimals), labelStyle, Offset(0f, yy), alignRight = left - 6.dp.toPx())
                    }
                }
                rightAxis?.let { axis ->
                    axis.ticks.forEach { v ->
                        drawLabel(measurer, Fmt.number(v, axis.decimals), labelStyle, Offset(left + plot.width + 6.dp.toPx(), y(v, axis)))
                    }
                }
                xTicks(tMin, tMax).forEach { t ->
                    val text = Fmt.shortDate(context, t)
                    val layout = measurer.measure(text, labelStyle)
                    val xx = (x(t) - layout.size.width / 2f).coerceIn(left, left + plot.width - layout.size.width)
                    drawText(layout, topLeft = Offset(xx, size.height - layout.size.height))
                }
            }

            clipRect(left = left - 6.dp.toPx(), top = 0f, right = left + plot.width * reveal.value + 6.dp.toPx(), bottom = size.height) {
                series.forEach { s ->
                    val axis = (if (s.rightAxis) rightAxis else leftAxis) ?: return@forEach
                    if (s.points.isEmpty()) return@forEach
                    val values = s.smoothed?.takeIf { it.size == s.points.size } ?: s.points.map { it.value }
                    val path = Path()
                    s.points.forEachIndexed { i, p ->
                        val o = Offset(x(p.time), y(values[i], axis))
                        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                    }
                    if (fill && s.points.size > 1) {
                        val area = Path().apply {
                            addPath(path)
                            lineTo(x(s.points.last().time), top + plot.height)
                            lineTo(x(s.points.first().time), top + plot.height)
                            close()
                        }
                        drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.28f), s.color.copy(alpha = 0f)), top, top + plot.height))
                    }
                    drawPath(path, s.color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    if (showPoints || s.points.size == 1) {
                        val faded = s.smoothed != null
                        s.points.forEach { p ->
                            drawCircle(
                                if (faded) s.color.copy(alpha = 0.35f) else s.color,
                                radius = 3.dp.toPx(),
                                center = Offset(x(p.time), y(p.value, axis)),
                            )
                        }
                    }
                    if (showTrend && s.trend != null && s.points.size > 1) {
                        val (slope, intercept) = s.trend
                        val a = s.points.first().time
                        val b = s.points.last().time
                        drawLine(
                            s.color.copy(alpha = 0.6f),
                            Offset(x(a), y((slope * a + intercept).toFloat(), axis)),
                            Offset(x(b), y((slope * b + intercept).toFloat(), axis)),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
                            cap = StrokeCap.Round,
                        )
                    }
                    if (showGoal && s.goal != null) {
                        val gy = y(s.goal, axis)
                        drawLine(
                            s.color.copy(alpha = 0.8f),
                            Offset(left, gy),
                            Offset(left + plot.width, gy),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                        )
                    }
                }
            }

            active?.let { t ->
                val xx = x(t)
                drawLine(colors.onSurfaceVariant.copy(alpha = 0.5f), Offset(xx, top), Offset(xx, top + plot.height), 1.dp.toPx())
                series.forEach { s ->
                    val axis = (if (s.rightAxis) rightAxis else leftAxis) ?: return@forEach
                    s.points.firstOrNull { it.time == t }?.let { p ->
                        drawCircle(colors.surface, 6.dp.toPx(), Offset(xx, y(p.value, axis)))
                        drawCircle(s.color, 4.5.dp.toPx(), Offset(xx, y(p.value, axis)))
                    }
                }
                drawTooltip(measurer, series, t, xx, top, left, left + plot.width, labelStyle, colors.inverseSurface, colors.inverseOnSurface)
            }
        }
    }
}

private class Axis(val min: Float, val max: Float, val ticks: List<Float>, val decimals: Int)

private fun axisRange(series: List<ChartSeries>, withGoal: Boolean): Axis? {
    val values = series.flatMap { s -> s.points.map { it.value } + listOfNotNull(s.goal.takeIf { withGoal }) }
    if (values.isEmpty()) return null
    var lo = values.min()
    var hi = values.max()
    if (hi - lo < 1e-3f) {
        lo -= 1f
        hi += 1f
    }
    val step = niceStep((hi - lo) / 4f)
    lo = floor(lo / step) * step
    hi = ceil(hi / step) * step
    val ticks = generateSequence(lo) { it + step }.takeWhile { it <= hi + step / 2 }.toList()
    val decimals = if (step >= 1f) 0 else ceil(-log10(step)).toInt().coerceAtMost(2)
    return Axis(lo, hi, ticks, decimals)
}

private fun niceStep(raw: Float): Float {
    if (raw <= 0f) return 1f
    val magnitude = 10f.pow(floor(log10(raw)))
    val n = raw / magnitude
    return magnitude * when {
        n <= 1f -> 1f
        n <= 2f -> 2f
        n <= 2.5f -> 2.5f
        n <= 5f -> 5f
        else -> 10f
    }
}

private fun xTicks(min: Long, max: Long): List<Long> {
    if (max <= min) return listOf(min)
    return (0..3).map { min + (max - min) * it / 3 }
}

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, style: TextStyle, at: Offset, alignRight: Float? = null) {
    val layout = measurer.measure(text, style)
    val x = alignRight?.let { it - layout.size.width } ?: at.x
    drawText(layout, topLeft = Offset(x, at.y - layout.size.height / 2f))
}

private fun DrawScope.drawTooltip(
    measurer: TextMeasurer,
    series: List<ChartSeries>,
    time: Long,
    x: Float,
    top: Float,
    minX: Float,
    maxX: Float,
    style: TextStyle,
    container: Color,
    content: Color,
) {
    val lines = series.mapNotNull { s -> s.points.firstOrNull { it.time == time }?.let { "${s.label}  ${Fmt.number(it.value, s.decimals)}" } }
    if (lines.isEmpty()) return
    val layout = measurer.measure(lines.joinToString("\n"), style.copy(color = content))
    val pad = 8.dp.toPx()
    val w = layout.size.width + pad * 2
    val h = layout.size.height + pad * 2
    val left = (x - w / 2).coerceIn(minX, (maxX - w).coerceAtLeast(minX))
    drawRoundRect(container, Offset(left, top), Size(w, h), CornerRadius(12.dp.toPx()))
    drawText(layout, topLeft = Offset(left + pad, top + pad))
}
