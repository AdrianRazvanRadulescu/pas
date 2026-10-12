package dev.pas.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import dev.pas.app.CoachThread
import dev.pas.app.PasViewModel
import dev.pas.app.ReplyStatus
import dev.pas.app.Stats
import dev.pas.app.ThreadItem
import dev.pas.app.WEEKLY_GYM_TARGET
import dev.pas.app.hoursAndMinutesLabel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val NOW_TICK_MILLIS = 15_000L

private val quickMessages = listOf("I don't feel like doing anything", "I only have 10 minutes", "What now?")

/**
 * One conversation with the coach: its messages of the day, the replies, what you wrote,
 * and in grey what you did. Every message gets visible feedback: sending, waiting, answered, or "the PC seems off".
 */
@Composable
fun CoachScreen(viewModel: PasViewModel, modifier: Modifier = Modifier) {
    // The statuses change with time alone (waiting → PC seems off), so the screen keeps its own clock.
    val now by produceState(OffsetDateTime.now()) {
        while (true) {
            delay(NOW_TICK_MILLIS)
            value = OffsetDateTime.now()
        }
    }
    val thread = viewModel.thread(now)
    val isWaiting = CoachThread.isWaitingForReply(thread, now)
    LaunchedEffect(isWaiting) {
        if (isWaiting) viewModel.pollForReply()
    }
    LaunchedEffect(viewModel.messages) { viewModel.markCoachSeen() }

    val today = now.toLocalDate()

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        WeekHeader(viewModel = viewModel, today = today)
        LazyColumn(
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            // The layout is reversed, so each day's label comes after its items: it shows above them on screen.
            val days = thread.groupBy { localDayOf(it.createdAt) }.toList().reversed()
            for ((day, dayItems) in days) {
                items(dayItems.reversed(), key = { it.id }) { item ->
                    when (item) {
                        is ThreadItem.FromHim -> HisBubble(item)
                        is ThreadItem.FromCoach -> CoachBubble(item)
                        is ThreadItem.Event -> EventLine(item.text)
                    }
                }
                item(key = "day-$day") { DayLabel(day = day, today = today) }
            }
            if (thread.isEmpty()) {
                item {
                    Text(
                        text = "Write the coach anything: how you feel, what is stopping you, what you did. It replies within a minute or two while the PC is on.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
        QuickReplies(onQuickMessage = viewModel::sendChat)
        MessageField(onSend = viewModel::sendChat)
    }
}

/** What used to be the Week tab, in one glance: the days you started, the gym, the hours, the comebacks. */
@Composable
private fun WeekHeader(viewModel: PasViewModel, today: LocalDate) {
    val checkIns = viewModel.checkIns
    val monday = Stats.startOfWeek(today)
    val focusMinutes = Stats.focusMinutesBetween(checkIns, monday, today)
    val returns = Stats.returnCount(checkIns)
    val facts = listOfNotNull(
        "${Stats.startedDaysInWeek(checkIns, today)} of 7 days started",
        "gym ${Stats.gymDaysInWeek(checkIns, today)}/$WEEKLY_GYM_TARGET",
        if (focusMinutes > 0) hoursAndMinutesLabel(focusMinutes) + " of focus" else null,
        if (returns > 0) "comebacks: $returns" else null,
    )
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
        Text("Coach", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        WeekMarks(
            startedDays = Stats.startedDays(checkIns),
            monday = monday,
            today = today,
            litColor = MaterialTheme.colorScheme.primary,
            dimColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = facts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HisBubble(item: ThreadItem.FromHim) {
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
        PasCard(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(text = item.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        val statusText = when (item.status) {
            ReplyStatus.SENDING -> "sending…"
            ReplyStatus.WAITING -> "the coach is reading…"
            ReplyStatus.PC_SEEMS_OFF -> "The PC seems off. It will reply when it starts."
            ReplyStatus.ANSWERED, ReplyStatus.NO_REPLY_EXPECTED -> null
        }
        Text(
            text = listOfNotNull(timeOf(item.createdAt), statusText).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun CoachBubble(item: ThreadItem.FromCoach) {
    val message = item.message
    val label = when (message.slot) {
        "morning" -> "Morning"
        "midday" -> "Midday"
        "evening" -> "Evening"
        else -> null
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        PasCard(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                label?.let {
                    SectionLabel(it, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                }
                Text(text = message.body, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text(
            text = timeOf(item.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        )
    }
}

@Composable
private fun DayLabel(day: LocalDate, today: LocalDate) {
    val text = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EventLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp),
    )
}

@Composable
private fun QuickReplies(onQuickMessage: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        for (text in quickMessages) {
            QuickChip(text) { onQuickMessage(text) }
        }
    }
}

@Composable
private fun QuickChip(text: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(text) },
        shape = Pas.decor.pill,
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun MessageField(onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Write to the coach…") },
            shape = Pas.decor.card,
            maxLines = 4,
            trailingIcon = {
                IconButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        onSend(text)
                        text = ""
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "14:05": the day is in the label above. */
private fun timeOf(createdAt: OffsetDateTime): String =
    createdAt.atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun localDayOf(createdAt: OffsetDateTime): LocalDate =
    createdAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
