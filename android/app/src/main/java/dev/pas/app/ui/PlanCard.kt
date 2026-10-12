package dev.pas.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pas.app.eveningPlans
import java.time.LocalTime

/** The plan can only be for tonight and never after 21:30: the day closes at 22:00. */
private val planTimes = (0..7).map { halfHours -> LocalTime.of(18, 0).plusMinutes(30L * halfHours) }

fun planTimesAfter(now: LocalTime): List<LocalTime> = planTimes.filter { it.isAfter(now) }

/** Plain card for the quieter blocks, so they read as one family. */
@Composable
fun QuietCard(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    PasCard(
        color = MaterialTheme.colorScheme.surface,
        shape = Pas.decor.card,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .animateContentSize()
                .padding(20.dp),
        ) {
            SectionLabel(label, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** "Tonight: Gym, at 19:00." Two taps; the time is optional. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanCard(availableTimes: List<LocalTime>, onSave: (String, String?) -> Unit, modifier: Modifier = Modifier) {
    var selectedPlan by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTime by rememberSaveable { mutableStateOf<String?>(null) }
    QuietCard(label = "What are you doing tonight?", modifier = modifier) {
        Text(
            text = "The evening goes easier with a plan made now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (plan in eveningPlans) {
                ChoiceChip(text = plan, isSelected = selectedPlan == plan, onClick = { selectedPlan = plan })
            }
        }
        if (availableTimes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("At what time? (optional)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (time in availableTimes) {
                    val timeText = time.toString()
                    ChoiceChip(
                        text = timeText,
                        isSelected = selectedTime == timeText,
                        onClick = { selectedTime = if (selectedTime == timeText) null else timeText },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val plan = selectedPlan
        Button(
            enabled = plan != null,
            onClick = { onSave(plan!!, selectedTime) },
            shape = Pas.decor.pill,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Done, that's the plan")
        }
    }
}

@Composable
fun ChoiceChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(text) },
        shape = Pas.decor.pill,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
