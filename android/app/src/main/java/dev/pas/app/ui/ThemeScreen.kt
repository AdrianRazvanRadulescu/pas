package dev.pas.app.ui

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.remember
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.pas.app.Stats
import java.time.LocalDate

/** A tab of its own: tapping a preview applies it at once, and the connection settings sit at the bottom. */
@Composable
fun ThemeScreen(
    selected: PasStyle,
    onSelect: (PasStyle) -> Unit,
    repository: String,
    onEditConnection: () -> Unit,
    scrollState: ScrollState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val monday = Stats.startOfWeek(today)
    val sampleStartedDays = setOf(monday, monday.plusDays(1), monday.plusDays(3))

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column {
            Text(
                text = "Themes",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Tap one and the app changes instantly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (style in PasStyle.entries) {
            ThemePreview(
                style = style,
                isSelected = style == selected,
                selectedBorderColor = MaterialTheme.colorScheme.primary,
                sampleStartedDays = sampleStartedDays,
                monday = monday,
                today = today,
                onClick = { onSelect(style) },
            )
        }
        QuietCard(label = "Connection") {
            Text(
                text = "Your data comes from $repository.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onEditConnection, shape = Pas.decor.pill) {
                Text("Change connection")
            }
        }
    }
}

/** A miniature Today screen drawn in its own theme, so each card looks exactly like the app would. */
@Composable
private fun ThemePreview(
    style: PasStyle,
    isSelected: Boolean,
    selectedBorderColor: Color,
    sampleStartedDays: Set<LocalDate>,
    monday: LocalDate,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    PasTheme(style = style) {
        val decor = Pas.decor
        val selectionBorder = if (isSelected) Modifier.border(BorderStroke(3.dp, selectedBorderColor), decor.card) else Modifier
        Box(
            modifier = modifier
                .pressScale(interactionSource)
                .fillMaxWidth()
                .then(selectionBorder)
                .clip(decor.card)
                .pasBackdrop(
                    backdrop = decor.backdrop,
                    background = MaterialTheme.colorScheme.background,
                    pattern = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(decor.logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(decor.field),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = style.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = style.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Selected theme",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                HeroCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "First step, today",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = onClick,
                                shape = decor.pill,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary,
                                ),
                            ) {
                                Text("▶ 25")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        WeekMarks(
                            startedDays = sampleStartedDays,
                            monday = monday,
                            today = today,
                            litColor = MaterialTheme.colorScheme.secondary,
                            dimColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.45f),
                        )
                    }
                }
            }
        }
    }
}
