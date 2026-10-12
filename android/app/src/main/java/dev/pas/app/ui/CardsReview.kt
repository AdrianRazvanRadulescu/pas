package dev.pas.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.pas.app.Card
import dev.pas.app.CardGrade

/**
 * One card at a time. The answer stays hidden until you try to explain it in your own words:
 * recalling is what makes it stick, rereading does not.
 */
@Composable
fun CardsReviewCard(card: Card, remainingCount: Int, onGrade: (Card, CardGrade) -> Unit) {
    var isAnswerShown by rememberSaveable(card.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    QuietCard(label = "Flashcards · $remainingCount left today") {
        Text(text = card.question, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (!isAnswerShown) {
            Text(
                text = "First explain it to yourself, in your own words.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { isAnswerShown = true },
                shape = Pas.decor.pill,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Show answer")
            }
        } else {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(text = card.answer, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for (grade in CardGrade.entries) {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onGrade(card, grade)
                        },
                        shape = Pas.decor.pill,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(grade.label, maxLines = 1)
                    }
                }
            }
        }
    }
}
