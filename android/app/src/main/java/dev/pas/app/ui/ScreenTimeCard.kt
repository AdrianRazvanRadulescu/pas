package dev.pas.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pas.app.ScreenTimeOverview
import dev.pas.app.screenTimeLabel

private const val APPS_SHOWN = 3

/** Today's time on the phone, next to yesterday at the same hour, and where it went. */
@Composable
fun ScreenTimeCard(overview: ScreenTimeOverview, weekAverageMinutes: Int?, modifier: Modifier = Modifier) {
    val comparisons = listOfNotNull(
        "yesterday so far: ${screenTimeLabel(overview.yesterdayUntilNowMinutes)}",
        weekAverageMinutes?.let { "7-day average: ${screenTimeLabel(it)}" },
    )
    QuietCard(label = "On your phone today", modifier = modifier) {
        Text(screenTimeLabel(overview.today.totalMinutes), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = comparisons.joinToString(" · ").replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val topApps = overview.today.apps.take(APPS_SHOWN)
        val longestMinutes = topApps.firstOrNull()?.minutes ?: return@QuietCard
        Spacer(Modifier.height(12.dp))
        for (app in topApps) {
            Row(Modifier.fillMaxWidth()) {
                Text(app.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(screenTimeLabel(app.minutes), style = MaterialTheme.typography.bodyMedium)
            }
            Box(
                Modifier
                    .fillMaxWidth(app.minutes.toFloat() / longestMinutes)
                    .height(4.dp)
                    .clip(Pas.decor.pill)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Android lets an app read the time on the phone only after a switch in Settings, so the card says why and opens it. */
@Composable
fun ScreenTimeAccessCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    QuietCard(label = "Phone time", modifier = modifier) {
        Text(
            text = "Pas can show how much time you spend on your phone and notify you every 30 minutes, silently. " +
                "In Settings, under Usage access, turn on Pas.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, shape = Pas.decor.pill) {
            Text("Open settings")
        }
    }
}
