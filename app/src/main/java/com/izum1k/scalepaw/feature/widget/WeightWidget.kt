package com.izum1k.scalepaw.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.izum1k.scalepaw.MainActivity
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.container
import com.izum1k.scalepaw.data.model.Metrics
import com.izum1k.scalepaw.ui.component.Fmt
import kotlinx.coroutines.flow.first

/** Home screen widget: latest weight, its change and when it was taken; tap to add an entry. */
class WeightWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val session = context.container.session
        val s = session.state.first { it.loaded }
        val type = s.type(Metrics.WEIGHT)
        val latest = s.measurements.firstOrNull()
        val value = latest?.number(type)
        val previous = s.measurements.drop(1).firstNotNullOfOrNull { it.number(type) }
        val headline = if (type != null && value != null) "${Fmt.value(value, type)} ${type.unit.symbol}" else "—"
        val delta = if (type != null && value != null && previous != null && value != previous) Fmt.signed(value - previous, type) else null
        val subtitle = latest?.let { Fmt.weekdayDate(it.timestamp) } ?: context.getString(R.string.overview_empty)
        val add = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_ADD)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(24.dp)
                        .padding(16.dp)
                        .clickable(actionStartActivity(add)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        s.user?.name ?: context.getString(R.string.app_name),
                        style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 13.sp),
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            headline,
                            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 30.sp, fontWeight = FontWeight.Bold),
                        )
                        if (delta != null) {
                            Spacer(GlanceModifier.width(8.dp))
                            Text(delta, style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 14.sp))
                        }
                    }
                    Text(subtitle, style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 12.sp))
                }
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = WeightWidget().updateAll(context)
    }
}

class WeightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeightWidget()
}
