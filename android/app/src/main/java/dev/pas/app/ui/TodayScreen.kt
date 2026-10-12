package dev.pas.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import dev.pas.app.BLOCKER_AREA
import dev.pas.app.GymProgram
import dev.pas.app.CARD_KIND
import dev.pas.app.DAILY_CARD_LIMIT
import dev.pas.app.FocusArea
import dev.pas.app.FocusSession
import dev.pas.app.NextAction
import dev.pas.app.NextActions
import dev.pas.app.GYM_PLAN
import dev.pas.app.PLAN_KIND
import dev.pas.app.ScreenTime
import dev.pas.app.PasViewModel
import dev.pas.app.R
import dev.pas.app.Stats
import dev.pas.app.Story
import dev.pas.app.todayFocus
import dev.pas.app.minutesLabel
import dev.pas.app.skipReasons
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val english = Locale.ENGLISH
private const val FOCUS_END_RELOAD_DELAY_MILLIS = 1_500L

/** NextActions stops proposing the gym after 20:59, so a later gym plan would hide its own card. */
private const val LAST_GYM_PLAN_HOUR = 20

/** What the main card shows when the user taps "Something else" instead of what the app proposed. */
private enum class ChosenAction { GYM, FOCUS, PLAN }

/**
 * One thing to do now, chosen by NextActions, with "Something else" for the days it is the wrong one.
 * Below it at most one quieter item, and a way to say "today is a pause" without guilt.
 * The conversation lives in the Coach tab, the gym in the Gym tab.
 */
@Composable
fun TodayScreen(
    viewModel: PasViewModel,
    now: LocalDateTime,
    onOpenStory: (String) -> Unit,
    onOpenGym: () -> Unit,
    onOpenThemes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = now.toLocalDate()
    val checkIns = viewModel.checkIns
    val plan = Stats.todayCheckIn(checkIns, PLAN_KIND, today)
    val isFocusRunning = viewModel.focusEndsAtMillis > System.currentTimeMillis()
    val nextAction = NextActions.choose(checkIns, now)
    val todayFocusText = todayFocus(viewModel.messages, today)
    var chosenInstead by rememberSaveable { mutableStateOf<ChosenAction?>(null) }
    val proposedAction = when (nextAction) {
        is NextAction.Gym -> ChosenAction.GYM
        is NextAction.PlanEvening -> ChosenAction.PLAN
        is NextAction.Focus -> ChosenAction.FOCUS
        is NextAction.Resting -> null
    }
    val shownAction = chosenInstead ?: proposedAction
    val canPlanTonight = plan == null && planTimesAfter(now.toLocalTime()).isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Header(
            today = today,
            hour = now.hour,
            planText = plan?.let { listOfNotNull(it.note, it.time).joinToString(", ") },
            isSyncing = viewModel.isSyncing,
            onRefresh = viewModel::refresh,
            onOpenThemes = onOpenThemes,
        )

        viewModel.syncError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        viewModel.availableUpdate?.let { release ->
            UpdateCard(
                releaseNotes = release.notes,
                isDownloading = viewModel.isDownloadingUpdate,
                needsInstallPermission = viewModel.needsInstallPermission,
                onInstall = viewModel::installUpdate,
            )
            Spacer(Modifier.height(16.dp))
        }

        if (Stats.isReturnDay(checkIns, today)) {
            ReturnBanner()
            Spacer(Modifier.height(16.dp))
        }

        Column(Modifier.enterInOrder(0)) {
            when {
                isFocusRunning -> FocusRunningCard(
                    focusEndsAtMillis = viewModel.focusEndsAtMillis,
                    onStop = viewModel::stopFocus,
                    onTimeUp = viewModel::onResume,
                )
                viewModel.finishedFocusMinutes > 0 -> FinishedFocusCard(
                    minutes = viewModel.finishedFocusMinutes,
                    blockerTitle = viewModel.finishedFocusBlockerTitle,
                    onBlockerSolved = { title ->
                        viewModel.markBlockerDone(title)
                        viewModel.dismissFinishedFocus()
                    },
                    onSaveLearned = viewModel::addLearned,
                    onDone = viewModel::dismissFinishedFocus,
                )
                viewModel.workoutDraft != null -> WorkoutOpenCard(onOpenGym = onOpenGym)
                nextAction is NextAction.Resting && chosenInstead == null -> RestingCard(
                    reason = nextAction.reason,
                    onChangedMind = viewModel::removeSkip,
                )
                else -> AnimatedContent(targetState = shownAction, label = "main action") { action ->
                    when (action) {
                        ChosenAction.GYM -> {
                            val gymDay = GymProgram.nextDay(checkIns)
                            GymActionCard(
                                reason = (nextAction as? NextAction.Gym)?.reason ?: "Today's workout is ready in the Gym tab.",
                                dayName = gymDay.name,
                                gymReason = viewModel.gymReason,
                                challenge = GymProgram.challengeFor(gymDay, checkIns, today),
                                plannedTime = plan?.takeIf { it.note == GYM_PLAN }?.time,
                                gymTimes = planTimesAfter(now.toLocalTime()).filter { it.minute == 0 && it.hour <= LAST_GYM_PLAN_HOUR },
                                onPlanTime = { time -> viewModel.savePlan(GYM_PLAN, time) },
                                onOpenGym = onOpenGym,
                            )
                        }
                        ChosenAction.PLAN -> PlanCard(
                            availableTimes = planTimesAfter(now.toLocalTime()),
                            onSave = { what, time ->
                                viewModel.savePlan(what, time)
                                chosenInstead = null
                            },
                        )
                        ChosenAction.FOCUS, null -> FocusStartCard(
                            focusText = todayFocusText ?: "One thing, ${minutesLabel(FocusSession.FULL_MINUTES)}. The hard part is just starting.",
                            openBlockers = Stats.openBlockers(viewModel.board.blockers, checkIns),
                            onStart = viewModel::startFocus,
                        )
                    }
                }
            }
        }

        if (!isFocusRunning && viewModel.finishedFocusMinutes == 0 && viewModel.workoutDraft == null) {
            val alternatives = ChosenAction.entries.filter { action ->
                action != shownAction && (action != ChosenAction.PLAN || canPlanTonight)
            }
            Alternatives(
                alternatives = alternatives,
                onChoose = { action -> if (action == ChosenAction.GYM) onOpenGym() else chosenInstead = action },
            )
        }

        val screenTime = viewModel.screenTime
        if (screenTime != null) {
            Spacer(Modifier.height(16.dp))
            ScreenTimeCard(
                overview = screenTime,
                weekAverageMinutes = ScreenTime.averageMinutes(checkIns, today),
                modifier = Modifier.enterInOrder(1),
            )
        } else if (!viewModel.hasUsageAccess) {
            Spacer(Modifier.height(16.dp))
            ScreenTimeAccessCard(modifier = Modifier.enterInOrder(1))
        }

        val reviewedCardsToday = checkIns.count { it.kind == CARD_KIND && it.date == today.toString() }
        val dueCards = Stats.dueCards(viewModel.board.cards, checkIns, today)
        val cardsLeftToday = minOf(dueCards.size, DAILY_CARD_LIMIT - reviewedCardsToday)
        val nextStoryToUnderstand = viewModel.board.digests.firstOrNull()?.stories
            ?.filter { it.coreRank != null && it.id !in viewModel.readStoryIds }
            ?.minByOrNull { it.coreRank!! }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.enterInOrder(2)) {
            if (cardsLeftToday > 0) {
                CardsReviewCard(card = dueCards.first(), remainingCount = cardsLeftToday, onGrade = viewModel::reviewCard)
            } else if (nextStoryToUnderstand != null) {
                StoryToUnderstandCard(story = nextStoryToUnderstand, onClick = { onOpenStory(nextStoryToUnderstand.id) })
            }
        }

        if (nextAction !is NextAction.Resting) {
            Spacer(Modifier.height(24.dp))
            PauseToday(onSave = viewModel::saveSkip)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Header(
    today: LocalDate,
    hour: Int,
    planText: String?,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    onOpenThemes: () -> Unit,
) {
    val dateText = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", english))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 20.dp),
    ) {
        Image(
            painter = painterResource(Pas.decor.logo),
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(Pas.decor.field),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = greetingFor(hour),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            planText?.let {
                Text(
                    text = "Tonight: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onOpenThemes) {
            Icon(painterResource(R.drawable.ic_tab_themes), contentDescription = "Themes", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isSyncing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(14.dp))
        } else {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Sync", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UpdateCard(releaseNotes: String, isDownloading: Boolean, needsInstallPermission: Boolean, onInstall: () -> Unit) {
    PasCard(
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
        shape = Pas.decor.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "A new version of Pas is available",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Button(
                        onClick = onInstall,
                        shape = Pas.decor.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    ) {
                        Text("Update")
                    }
                }
            }
            if (releaseNotes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "What's new: $releaseNotes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (needsInstallPermission) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Android asks for permission once: turn on \"Allow from this source\", then come back here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Starting is the hard part, so the card asks only "on what" and offers a 5-minute way in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusStartCard(focusText: String, openBlockers: List<String>, onStart: (Int, String, String) -> Unit) {
    var selectedArea by rememberSaveable { mutableStateOf(FocusArea.LEARNING.key) }
    var selectedBlocker by rememberSaveable { mutableStateOf("") }
    HeroCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)) {
            SectionLabel("Now", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
            Spacer(Modifier.height(10.dp))
            Text(
                text = focusText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (area in FocusArea.entries) {
                    HeroChip(
                        text = area.label,
                        isSelected = selectedArea == area.key,
                        onClick = {
                            selectedArea = area.key
                            selectedBlocker = ""
                        },
                    )
                }
                for (blocker in openBlockers) {
                    HeroChip(
                        text = blocker,
                        isSelected = selectedArea == BLOCKER_AREA && selectedBlocker == blocker,
                        onClick = {
                            selectedArea = BLOCKER_AREA
                            selectedBlocker = blocker
                        },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onStart(FocusSession.FULL_MINUTES, selectedArea, selectedBlocker) },
                shape = Pas.decor.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start ${minutesLabel(FocusSession.FULL_MINUTES)}", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { onStart(FocusSession.SHORT_MINUTES, selectedArea, selectedBlocker) },
                shape = Pas.decor.pill,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("Just ${minutesLabel(FocusSession.SHORT_MINUTES)}, to get going")
            }
        }
    }
}

/** ChoiceChip in the colours of the hero card, so the unselected ones stay readable on its dark fill. */
@Composable
private fun HeroChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(text) },
        shape = Pas.decor.pill,
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = onHero.copy(alpha = 0.5f),
        ),
        colors = FilterChipDefaults.filterChipColors(
            labelColor = onHero,
            selectedContainerColor = onHero,
            selectedLabelColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}

@Composable
private fun FocusRunningCard(focusEndsAtMillis: Long, onStop: () -> Unit, onTimeUp: () -> Unit) {
    val remainingSeconds = rememberSecondsUntil(focusEndsAtMillis)
    // The session is closed by FocusSession in the background; a moment later the screen reloads to show "You worked".
    LaunchedEffect(remainingSeconds == 0L) {
        if (remainingSeconds == 0L) {
            delay(FOCUS_END_RELOAD_DELAY_MILLIS)
            onTimeUp()
        }
    }
    HeroCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)) {
            Text(
                text = clockLabel(remainingSeconds),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Just this, nothing else.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onStop,
                shape = Pas.decor.pill,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("I'll stop here")
            }
        }
    }
}

/** [blockerTitle] is empty for ordinary work; for a blocker the card asks whether it is solved. */
@Composable
private fun FinishedFocusCard(
    minutes: Int,
    blockerTitle: String,
    onBlockerSolved: (String) -> Unit,
    onSaveLearned: (String) -> Unit,
    onDone: () -> Unit,
) {
    var learned by rememberSaveable { mutableStateOf("") }
    PasCard(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = Pas.decor.hero,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)) {
            Text(
                text = "You worked ${minutesLabel(minutes)}.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "You showed up. That is what counts. What did you understand? One sentence goes to the coach and becomes a flashcard.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = learned,
                onValueChange = { learned = it },
                placeholder = { Text("I understood that…") },
                shape = Pas.decor.field,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            if (blockerTitle.isNotEmpty()) {
                Button(
                    onClick = {
                        if (learned.isNotBlank()) onSaveLearned(learned)
                        onBlockerSolved(blockerTitle)
                    },
                    shape = Pas.decor.pill,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("Solved: $blockerTitle", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TextButton(
                onClick = {
                    if (learned.isNotBlank()) onSaveLearned(learned)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done for now")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GymActionCard(
    reason: String,
    dayName: String,
    gymReason: String,
    challenge: String?,
    plannedTime: String?,
    gymTimes: List<LocalTime>,
    onPlanTime: (String) -> Unit,
    onOpenGym: () -> Unit,
) {
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer
    HeroCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            SectionLabel("Today is a gym day", color = onHero.copy(alpha = 0.75f))
            Spacer(Modifier.height(10.dp))
            Text(text = dayName, style = MaterialTheme.typography.headlineSmall, color = onHero)
            if (gymReason.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = "\"$gymReason\"", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = onHero)
            }
            Spacer(Modifier.height(6.dp))
            Text(text = challenge ?: reason, style = MaterialTheme.typography.bodyLarge, color = onHero)
            Spacer(Modifier.height(16.dp))
            // Deciding the hour now (an implementation intention) is what turns "I should" into going.
            if (plannedTime != null) {
                Text(
                    text = "You're going at $plannedTime. Pack your bag now, so tonight there is nothing left to decide.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHero,
                )
            } else if (gymTimes.isNotEmpty()) {
                Text("When are you going?", style = MaterialTheme.typography.bodyMedium, color = onHero.copy(alpha = 0.75f))
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (time in gymTimes) {
                        val timeText = time.toString()
                        HeroChip(text = timeText, isSelected = false, onClick = { onPlanTime(timeText) })
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onOpenGym,
                shape = Pas.decor.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("See the workout", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Rough day? Just do the first ${GymProgram.SHORT_VERSION_EXERCISES} exercises. It counts.",
                style = MaterialTheme.typography.bodySmall,
                color = onHero.copy(alpha = 0.75f),
                modifier = Modifier.padding(end = 32.dp),
            )
        }
    }
}

@Composable
private fun WorkoutOpenCard(onOpenGym: () -> Unit) {
    HeroCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = "You are in the middle of a workout.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenGym, shape = Pas.decor.pill, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun RestingCard(reason: String, onChangedMind: () -> Unit) {
    PasCard(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = Pas.decor.hero,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(text = reason, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onChangedMind) {
                Text("I changed my mind, I'll do something")
            }
        }
    }
}

@Composable
private fun Alternatives(alternatives: List<ChosenAction>, onChoose: (ChosenAction) -> Unit) {
    if (alternatives.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text("Something else:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (action in alternatives) {
            val label = when (action) {
                ChosenAction.GYM -> "Gym"
                ChosenAction.FOCUS -> "Focus"
                ChosenAction.PLAN -> "Tonight's plan"
            }
            TextButton(onClick = { onChoose(action) }) {
                Text(label)
            }
        }
    }
}

/** A pause is a choice, not a failure: one tap, a reason, and the coach answers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PauseToday(onSave: (String) -> Unit) {
    var isOpen by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { isOpen = !isOpen }, modifier = Modifier.fillMaxWidth()) {
            Text(if (isOpen) "No, never mind" else "I'm taking today off")
        }
        AnimatedVisibility(visible = isOpen) {
            Column(Modifier.padding(top = 4.dp)) {
                Text(
                    text = "What is stopping you? No judgment, just to see the pattern.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (reason in skipReasons) {
                        ChoiceChip(
                            text = reason,
                            isSelected = false,
                            onClick = {
                                onSave(reason)
                                isOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The next of the week's three core stories, so understanding something deep has a place in the day. */
@Composable
private fun StoryToUnderstandCard(story: Story, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    PasCard(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .pressScale(interactionSource)
            .fillMaxWidth()
            .clip(Pas.decor.card)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionLabel("To understand this week", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(text = story.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${story.readMinutes} min · open ›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun ReturnBanner() {
    PasCard(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = Pas.decor.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "You're back. That is the hard part, and you already did it.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
fun SectionLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    if (Pas.decor.labelsInCaps) {
        Text(text = text.uppercase(english), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp), color = color)
    } else {
        Text(text = text, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

private fun greetingFor(hour: Int): String = when {
    hour < 12 -> "Good morning"
    hour < 18 -> "Good afternoon"
    else -> "Good evening"
}


