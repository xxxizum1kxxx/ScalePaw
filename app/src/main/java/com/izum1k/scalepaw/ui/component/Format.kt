package com.izum1k.scalepaw.ui.component

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.data.model.Metrics
import com.izum1k.scalepaw.data.model.UnitConversion
import com.izum1k.scalepaw.data.model.UnitType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

object Fmt {
    fun decimals(type: MetricType): Int = when {
        type.unit == UnitType.KCAL || type.unit == UnitType.BPM || type.unit == UnitType.OHM -> 0
        type.identity == Metrics.WHR.identity || type.identity == Metrics.WHTR.identity -> 2
        else -> 1
    }

    fun number(value: Float, decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", value)

    /** The number alone, no unit; stone shows as whole stone and pounds. */
    fun value(value: Float, type: MetricType): String = if (type.unit == UnitType.ST) {
        val (st, lb) = UnitConversion.stToStLb(value)
        "$st:${lb.toString().padStart(2, '0')}"
    } else {
        number(value, decimals(type))
    }

    fun unit(type: MetricType): String = type.unit.symbol

    fun signed(value: Float, type: MetricType): String {
        val body = number(abs(value), decimals(type))
        if (body.none { it in '1'..'9' }) return body
        return when {
            value > 0f -> "+$body"
            value < 0f -> "−$body"
            else -> body
        }
    }

    private fun zoned(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())

    fun date(epochMillis: Long): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(zoned(epochMillis))

    fun shortDate(context: Context, epochMillis: Long): String {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd")
        return DateTimeFormatter.ofPattern(pattern).format(zoned(epochMillis))
    }

    fun time(context: Context, epochMillis: Long): String {
        val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
        return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)).format(zoned(epochMillis))
    }

    fun weekdayDate(epochMillis: Long): String {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd")
        return DateTimeFormatter.ofPattern(pattern).format(zoned(epochMillis))
    }

    fun monthYear(epochMillis: Long): String {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMM")
        return DateTimeFormatter.ofPattern(pattern).format(zoned(epochMillis))
    }
}

/** Display name: builtins resolve their string, user and device types show the stored name. */
@Composable
fun MetricType.label(): String = label(LocalContext.current)

fun MetricType.label(context: Context): String = spec?.let { context.getString(it.nameRes) } ?: customName.orEmpty().ifBlank { columnKey }
