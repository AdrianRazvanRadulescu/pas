package dev.pas.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceTheme
import androidx.glance.material3.ColorProviders
import dev.pas.app.ui.PasStyle
import dev.pas.app.ui.colorSchemeOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PasWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = CoachStore(context)
        val today = LocalDate.now()
        // Only today's message: yesterday's focus would read as today's.
        val focus = todayFocus(store.messages(), LocalDate.now()) ?: "One thing, ${minutesLabel(FocusSession.FULL_MINUTES)}."
        val gymDays = Stats.gymDaysInWeek(store.checkIns(), today)
        val startedDays = Stats.startedDaysInWeek(store.checkIns(), today)
        val focusEndsAtMillis = store.focusEndsAtMillis
        val runningUntil = if (focusEndsAtMillis > System.currentTimeMillis()) {
            Instant.ofEpochMilli(focusEndsAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        } else {
            null
        }
        val startFocusIntent = FocusSession.startIntent(context, FocusSession.FULL_MINUTES)
        val colors = ColorProviders(scheme = colorSchemeOf(PasStyle.fromKey(store.styleKey)))

        provideContent {
            GlanceTheme(colors = colors) {
                WidgetContent(focus, gymDays, startedDays, runningUntil, startFocusIntent)
            }
        }
    }
}

@Composable
private fun WidgetContent(focus: String, gymDays: Int, startedDays: Int, runningUntil: String?, startFocusIntent: android.content.Intent) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.primaryContainer)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            text = "Today's focus",
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onPrimaryContainer),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = focus,
            maxLines = 3,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onPrimaryContainer),
        )
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Gym $gymDays/$WEEKLY_GYM_TARGET",
                    style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onPrimaryContainer),
                )
                Text(
                    text = "This week: $startedDays/7 days",
                    style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onPrimaryContainer),
                )
            }
            if (runningUntil == null) {
                Button(
                    text = "▶ ${FocusSession.FULL_MINUTES} min",
                    onClick = actionSendBroadcast(startFocusIntent),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = GlanceTheme.colors.secondary,
                        contentColor = GlanceTheme.colors.onSecondary,
                    ),
                )
            } else {
                Text(
                    text = "Working until $runningUntil",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onPrimaryContainer),
                )
            }
        }
    }
}

class PasWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = PasWidget()
}
