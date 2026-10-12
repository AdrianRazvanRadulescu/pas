package dev.pas.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pas.app.CheckIn
import dev.pas.app.Feeling
import dev.pas.app.GymProgram
import dev.pas.app.PasViewModel
import dev.pas.app.ProgramDay
import dev.pas.app.Stats
import dev.pas.app.TargetReason
import dev.pas.app.WEEKLY_GYM_TARGET
import dev.pas.app.WorkoutDraft
import dev.pas.app.kgLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val HISTORY_WORKOUTS_SHOWN = 5

/** The gym, apart from everything else: the next workout, the workout in progress, and how it went. */
@Composable
fun GymScreen(viewModel: PasViewModel, today: LocalDate, modifier: Modifier = Modifier) {
    val draft = viewModel.workoutDraft
    val finishedWorkout = viewModel.checkIns.find { it.id == viewModel.finishedWorkoutId }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        when {
            draft != null -> WorkoutInProgress(viewModel, draft, today)
            finishedWorkout != null -> WorkoutFinished(
                workout = finishedWorkout,
                checkIns = viewModel.checkIns,
                onSend = viewModel::sendWorkoutFeeling,
                onSkip = viewModel::dismissFinishedWorkout,
            )
            else -> GymOverview(viewModel, today)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun GymOverview(viewModel: PasViewModel, today: LocalDate) {
    val checkIns = viewModel.checkIns
    val nextDay = GymProgram.nextDay(checkIns)
    Text("Gym", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = "${Stats.gymDaysInWeek(checkIns, today)} of $WEEKLY_GYM_TARGET this week · ${GymProgram.SETS}×${GymProgram.REP_MIN}-${GymProgram.REP_MAX}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    GymReason(reason = viewModel.gymReason, onSave = viewModel::saveGymReason)
    Spacer(Modifier.height(20.dp))
    NextWorkoutCard(
        day = nextDay,
        checkIns = checkIns,
        today = today,
        onStart = { viewModel.startWorkout(isShortVersion = false) },
        onStartShort = { viewModel.startWorkout(isShortVersion = true) },
        modifier = Modifier.enterInOrder(0),
    )

    val workouts = GymProgram.workouts(checkIns)
    if (workouts.isNotEmpty()) {
        Spacer(Modifier.height(32.dp))
        SectionLabel("Progress")
        Spacer(Modifier.height(12.dp))
        for (exercise in GymProgram.days.flatMap { it.exercises }) {
            val heaviest = GymProgram.heaviestKgPerSession(exercise.id, checkIns)
            if (heaviest.isEmpty()) continue
            ProgressRow(name = exercise.name, heaviestKgPerSession = heaviest)
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Recent workouts")
        Spacer(Modifier.height(12.dp))
        for (workout in workouts.takeLast(HISTORY_WORKOUTS_SHOWN).reversed()) {
            Text(text = workoutSummary(workout), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** His own words, shown on the gym card on Today: a reason he wrote moves him more than one the app gives. */
@Composable
private fun GymReason(reason: String, onSave: (String) -> Unit) {
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable(reason) { mutableStateOf(reason) }
    if (reason.isNotBlank() && !isEditing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Why I go: “$reason”",
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { isEditing = true }) { Text("Change") }
        }
        return
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Why do I go to the gym?") },
        placeholder = { Text("In your own words. I'll show it to you on gym days.") },
        shape = Pas.decor.card,
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Button(
        enabled = text.isNotBlank(),
        onClick = {
            onSave(text)
            isEditing = false
        },
        shape = Pas.decor.pill,
    ) {
        Text("Save")
    }
}

@Composable
private fun NextWorkoutCard(
    day: ProgramDay,
    checkIns: List<CheckIn>,
    today: LocalDate,
    onStart: () -> Unit,
    onStartShort: () -> Unit,
    modifier: Modifier,
) {
    HeroCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
            SectionLabel("Next: ${day.name}", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
            Spacer(Modifier.height(12.dp))
            for (exercise in day.exercises) {
                val target = GymProgram.targetFor(exercise, checkIns, today)
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        if (target.reason != TargetReason.SAME) {
                            Text(
                                text = target.reason.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("${kgLabel(target.kg)} kg", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "First step: gear in the bag and out the door. Once there, ${GymProgram.WARM_UP_MINUTES} minutes of warm-up and the first set. The rest follows.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStart,
                shape = Pas.decor.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("I'm at the gym, let's start", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onStartShort, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Rough day? Just the first ${GymProgram.SHORT_VERSION_EXERCISES} exercises. That counts too.",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun WorkoutInProgress(viewModel: PasViewModel, draft: WorkoutDraft, today: LocalDate) {
    val exercises = GymProgram.exercisesFor(draft)
    val exercise = exercises[draft.exerciseIndex]
    val target = GymProgram.targetFor(exercise, viewModel.checkIns, today)
    val setsOfExercise = draft.sets.filter { it.exerciseId == exercise.id }
    val isLastExercise = draft.exerciseIndex == exercises.lastIndex
    val haptics = LocalHapticFeedback.current

    // A new set starts from the previous one: same weight, same reps; the first from the target and last time.
    var kg by rememberSaveable(exercise.id, setsOfExercise.size) {
        mutableDoubleStateOf(setsOfExercise.lastOrNull()?.kg ?: target.kg)
    }
    var reps by rememberSaveable(exercise.id, setsOfExercise.size) {
        mutableIntStateOf(setsOfExercise.lastOrNull()?.reps ?: target.lastTimeSets.getOrNull(0)?.reps ?: GymProgram.REP_MIN)
    }

    Text(
        text = "${GymProgram.day(draft.programDayId).name} · exercise ${draft.exerciseIndex + 1} of ${exercises.size}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
    Text(exercise.howTo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))

    QuietCard(label = "Target: ${kgLabel(target.kg)} kg · ${GymProgram.SETS}×${GymProgram.REP_MIN}-${GymProgram.REP_MAX}") {
        Text(target.reason.label, style = MaterialTheme.typography.bodyMedium)
        if (target.lastTimeSets.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Last time: ${target.lastTimeSets.joinToString(", ") { "${kgLabel(it.kg)}×${it.reps}" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for ((index, set) in setsOfExercise.withIndex()) {
            Spacer(Modifier.height(6.dp))
            Text("Set ${index + 1}: ${kgLabel(set.kg)} kg × ${set.reps}", style = MaterialTheme.typography.titleMedium)
        }
    }

    val restSecondsLeft = rememberSecondsUntil(viewModel.restEndsAtMillis)
    AnimatedVisibility(visible = restSecondsLeft > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
            Text(
                text = "Rest ${clockLabel(restSecondsLeft)}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::skipRest) { Text("Skip") }
        }
    }

    Spacer(Modifier.height(16.dp))
    Stepper(
        label = "Weight",
        value = "${kgLabel(kg)} kg",
        onDecrease = { kg = (kg - GymProgram.SMALLEST_STEP_KG).coerceAtLeast(0.0) },
        onIncrease = { kg += GymProgram.SMALLEST_STEP_KG },
    )
    Spacer(Modifier.height(8.dp))
    Stepper(
        label = "Reps",
        value = reps.toString(),
        onDecrease = { reps = (reps - 1).coerceAtLeast(0) },
        onIncrease = { reps += 1 },
    )
    Spacer(Modifier.height(16.dp))
    val isExerciseDone = setsOfExercise.size >= GymProgram.SETS
    val logButtonText = "Done with set ${setsOfExercise.size + 1}"
    if (isExerciseDone && !isLastExercise) {
        Button(
            onClick = { viewModel.moveToExercise(draft.exerciseIndex + 1) },
            shape = Pas.decor.pill,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Next: ${exercises[draft.exerciseIndex + 1].name}")
        }
        TextButton(onClick = { viewModel.logSet(kg, reps) }, modifier = Modifier.fillMaxWidth()) {
            Text("One more set here")
        }
    } else if (isExerciseDone) {
        Button(
            onClick = viewModel::finishWorkout,
            shape = Pas.decor.pill,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Done, finish workout")
        }
        TextButton(onClick = { viewModel.logSet(kg, reps) }, modifier = Modifier.fillMaxWidth()) {
            Text("One more set here")
        }
    } else {
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.logSet(kg, reps)
            },
            shape = Pas.decor.pill,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(logButtonText, style = MaterialTheme.typography.titleMedium)
        }
    }

    Spacer(Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        if (draft.exerciseIndex > 0) {
            TextButton(onClick = { viewModel.moveToExercise(draft.exerciseIndex - 1) }) { Text("‹ Back") }
        }
        Spacer(Modifier.weight(1f))
        if (!isLastExercise && !isExerciseDone) {
            TextButton(onClick = { viewModel.moveToExercise(draft.exerciseIndex + 1) }) { Text("Skip to next ›") }
        }
    }
    if (setsOfExercise.isNotEmpty()) {
        TextButton(onClick = viewModel::removeLastSet) { Text("Delete last set") }
    }
    if (!(isExerciseDone && isLastExercise)) {
        OutlinedButton(onClick = viewModel::finishWorkout, shape = Pas.decor.pill, modifier = Modifier.fillMaxWidth()) {
            Text(if (draft.sets.isEmpty()) "Skip today" else "Finish here")
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onDecrease, shape = Pas.decor.pill, modifier = Modifier.size(width = 56.dp, height = 48.dp)) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(96.dp),
        )
        OutlinedButton(onClick = onIncrease, shape = Pas.decor.pill, modifier = Modifier.size(width = 56.dp, height = 48.dp)) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Facts first, so the effort is seen before anything is asked; then one optional line for the coach. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutFinished(workout: CheckIn, checkIns: List<CheckIn>, onSend: (Feeling, String) -> Unit, onSkip: () -> Unit) {
    var feeling by rememberSaveable { mutableStateOf<Feeling?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    val improvements = GymProgram.improvements(workout, checkIns)

    PasCard(color = MaterialTheme.colorScheme.secondaryContainer, shape = Pas.decor.hero, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = "Done: ${workout.programDayName}${workout.minutes?.let { ", $it min" }.orEmpty()}.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${workout.sets.size} sets, ${GymProgram.volumeKg(workout.sets)} kg lifted in total.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (improvements.isEmpty()) {
                Text(
                    text = "You showed up and did it. That's the part that counts.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            for (improvement in improvements) {
                Spacer(Modifier.height(4.dp))
                Text("↑ $improvement", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    QuietCard(label = "How was it?") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in Feeling.entries) {
                ChoiceChip(text = option.label, isSelected = feeling == option, onClick = { feeling = option })
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text("Anything to add? (optional)") },
            shape = Pas.decor.field,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        val chosenFeeling = feeling
        Button(
            enabled = chosenFeeling != null,
            onClick = { onSend(chosenFeeling!!, note) },
            shape = Pas.decor.pill,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send to your coach")
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("No message") }
    }
}

@Composable
private fun ProgressRow(name: String, heaviestKgPerSession: List<Double>) {
    val lineColor = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Canvas(
            Modifier
                .width(72.dp)
                .height(24.dp),
        ) {
            if (heaviestKgPerSession.size < 2) return@Canvas
            val lightest = heaviestKgPerSession.min()
            val range = (heaviestKgPerSession.max() - lightest).coerceAtLeast(GymProgram.SMALLEST_STEP_KG)
            val stepX = size.width / (heaviestKgPerSession.size - 1)
            val points = heaviestKgPerSession.mapIndexed { index, kg ->
                Offset(index * stepX, size.height - ((kg - lightest) / range * size.height).toFloat())
            }
            for (index in 1..points.lastIndex) {
                drawLine(lineColor, points[index - 1], points[index], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${kgLabel(heaviestKgPerSession.last())} kg",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp),
        )
    }
}

/** "Wed 8 Oct · Day A · 40 min · 2350 kg · Hard" */
private fun workoutSummary(workout: CheckIn): String {
    val dateText = LocalDate.parse(workout.date).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
    val feelingLabel = Feeling.entries.firstOrNull { it.key == workout.feeling }?.label
    return listOfNotNull(
        dateText,
        workout.programDayName,
        workout.minutes?.let { "$it min" },
        "${GymProgram.volumeKg(workout.sets)} kg",
        feelingLabel,
    ).joinToString(" · ")
}
