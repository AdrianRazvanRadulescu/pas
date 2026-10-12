package dev.pas.app

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor

data class Exercise(
    val id: String,
    val name: String,
    val howTo: String,
    val startKg: Double,
    val isLowerBody: Boolean,
    val restSeconds: Int,
)

data class ProgramDay(val id: String, val name: String, val exercises: List<Exercise>)

enum class TargetReason(val label: String) {
    FIRST_TIME("First time: a light weight, just to see how it feels."),
    SAME("Same as last time. Aim for ${GymProgram.REP_MAX} on every set."),
    HEAVIER("Heavier: last time you got ${GymProgram.REP_MAX} on every set."),
    LIGHTER_AFTER_MISSES("A bit lighter: under ${GymProgram.REP_MIN} reps twice in a row."),
    LIGHTER_AFTER_BREAK("A bit lighter after a long break."),
}

/** What to lift today for one exercise, and what he did last time. */
data class ExerciseTarget(val exercise: Exercise, val kg: Double, val reason: TargetReason, val lastTimeSets: List<LoggedSet>)

/** A workout in progress: kept on the phone only, and saved as a WORKOUT_KIND check-in when it ends. */
@Serializable
data class WorkoutDraft(
    val programDayId: String,
    val date: String,
    val startedAtMillis: Long,
    val isShortVersion: Boolean,
    val exerciseIndex: Int = 0,
    val sets: List<LoggedSet> = emptyList(),
)

/**
 * Full body A/B, three sets of 8-12, with double progression:
 * when every set reaches 12 the weight goes up; two sessions in a row under 8, or two weeks away, and it goes down 10%.
 */
object GymProgram {

    const val SETS = 3
    const val REP_MIN = 8
    const val REP_MAX = 12
    const val WARM_UP_MINUTES = 5

    /** "Rough day? Just 10 minutes": the warm-up and the first two exercises, and the day still counts. */
    const val SHORT_VERSION_EXERCISES = 2

    const val SMALLEST_STEP_KG = 2.5
    private const val UPPER_BODY_STEP_KG = 2.5
    private const val LOWER_BODY_STEP_KG = 5.0
    private const val LONG_BREAK_DAYS = 14

    val days = listOf(
        ProgramDay(
            id = "A",
            name = "Day A",
            exercises = listOf(
                Exercise("squat", "Barbell squat", "Bar on your shoulders, lower with a straight back until your thighs are parallel.", 20.0, isLowerBody = true, restSeconds = 120),
                Exercise("bench", "Barbell bench press", "Lower the bar to your chest, then press it up.", 20.0, isLowerBody = false, restSeconds = 120),
                Exercise("row", "Dumbbell row", "One knee on the bench, pull the dumbbell toward your hip.", 12.0, isLowerBody = false, restSeconds = 90),
                Exercise("curl", "Dumbbell curl", "Elbows tight to your body, no swinging.", 6.0, isLowerBody = false, restSeconds = 60),
            ),
        ),
        ProgramDay(
            id = "B",
            name = "Day B",
            exercises = listOf(
                Exercise("romanian-deadlift", "Romanian deadlift", "Knees slightly bent, push your hips back.", 30.0, isLowerBody = true, restSeconds = 120),
                Exercise("shoulder-press", "Dumbbell shoulder press", "From shoulder height to overhead.", 8.0, isLowerBody = false, restSeconds = 90),
                Exercise("pulldown", "Lat pulldown", "Pull the bar to your chest, elbows down.", 25.0, isLowerBody = false, restSeconds = 90),
                Exercise("leg-press", "Leg press", "Lower until your knees reach 90 degrees.", 40.0, isLowerBody = true, restSeconds = 90),
            ),
        ),
    )

    fun day(id: String): ProgramDay = days.first { it.id == id }

    fun exercisesFor(draft: WorkoutDraft): List<Exercise> {
        val exercises = day(draft.programDayId).exercises
        return if (draft.isShortVersion) exercises.take(SHORT_VERSION_EXERCISES) else exercises
    }

    fun workouts(checkIns: List<CheckIn>): List<CheckIn> =
        checkIns.filter { it.kind == WORKOUT_KIND }.sortedBy { it.createdAt }

    /** A and B take turns; the very first workout is A. */
    fun nextDay(checkIns: List<CheckIn>): ProgramDay {
        val lastDayId = workouts(checkIns).lastOrNull()?.programDayId
        return if (lastDayId == "A") day("B") else day("A")
    }

    fun targetFor(exercise: Exercise, checkIns: List<CheckIn>, today: LocalDate): ExerciseTarget {
        val sessions = sessionsOf(exercise.id, checkIns)
        if (sessions.isEmpty()) return ExerciseTarget(exercise, exercise.startKg, TargetReason.FIRST_TIME, emptyList())

        val lastSession = sessions.last()
        val lastKg = lastSession.sets.maxOf { it.kg }
        val daysSinceLast = ChronoUnit.DAYS.between(LocalDate.parse(lastSession.date), today)
        val lastTwo = sessions.takeLast(2)
        val missedTwiceInARow = lastTwo.size == 2 && lastTwo.all { session -> session.sets.any { it.reps < REP_MIN } }
        val reachedTopEverywhere = lastSession.sets.size >= SETS && lastSession.sets.all { it.kg >= lastKg && it.reps >= REP_MAX }
        val step = if (exercise.isLowerBody) LOWER_BODY_STEP_KG else UPPER_BODY_STEP_KG

        val (kg, reason) = when {
            daysSinceLast >= LONG_BREAK_DAYS -> tenPercentLighter(lastKg) to TargetReason.LIGHTER_AFTER_BREAK
            missedTwiceInARow -> tenPercentLighter(lastKg) to TargetReason.LIGHTER_AFTER_MISSES
            reachedTopEverywhere -> lastKg + step to TargetReason.HEAVIER
            else -> lastKg to TargetReason.SAME
        }
        return ExerciseTarget(exercise, kg, reason, lastSession.sets)
    }

    /**
     * One concrete thing to beat today, so the workout has a reason beyond "I should":
     * a heavier weight if one is due, otherwise one more rep than last time. Null before the first workout.
     */
    fun challengeFor(day: ProgramDay, checkIns: List<CheckIn>, today: LocalDate): String? {
        val targets = day.exercises.map { targetFor(it, checkIns, today) }
        val heavier = targets.firstOrNull { it.reason == TargetReason.HEAVIER }
        if (heavier != null) return "Today: ${heavier.exercise.name} at ${kgLabel(heavier.kg)} kg, heavier than last time."
        val repeated = targets.firstOrNull { it.reason == TargetReason.SAME } ?: return null
        val lastTimeReps = repeated.lastTimeSets.sumOf { it.reps }
        return "Today: ${repeated.exercise.name}, ${kgLabel(repeated.kg)} kg. Last time you did $lastTimeReps reps in total; beat it by one."
    }

    /** One line per exercise that went better than the session before it, e.g. "Squat: 25 kg, 5 kg more". */
    fun improvements(workout: CheckIn, checkIns: List<CheckIn>): List<String> {
        val earlierCheckIns = checkIns.filter { it.createdAt < workout.createdAt }
        return workout.sets.groupBy { it.exerciseId }.mapNotNull { (exerciseId, sets) ->
            val previousSets = sessionsOf(exerciseId, earlierCheckIns).lastOrNull()?.sets ?: return@mapNotNull null
            val bestKg = sets.maxOf { it.kg }
            val previousBestKg = previousSets.maxOf { it.kg }
            val name = sets.first().exerciseName
            if (bestKg > previousBestKg) return@mapNotNull "$name: ${kgLabel(bestKg)} kg, ${kgLabel(bestKg - previousBestKg)} kg more"
            val repsAtBest = sets.filter { it.kg == bestKg }.sumOf { it.reps }
            val previousRepsAtBest = previousSets.filter { it.kg == previousBestKg }.sumOf { it.reps }
            if (bestKg == previousBestKg && repsAtBest > previousRepsAtBest) "$name: ${repsAtBest - previousRepsAtBest} more reps at ${kgLabel(bestKg)} kg"
            else null
        }
    }

    fun volumeKg(sets: List<LoggedSet>): Int = sets.sumOf { it.kg * it.reps }.toInt()

    /** The heaviest weight of each session of one exercise, oldest first, for the history sparkline. */
    fun heaviestKgPerSession(exerciseId: String, checkIns: List<CheckIn>): List<Double> =
        sessionsOf(exerciseId, checkIns).map { session -> session.sets.maxOf { it.kg } }

    private class ExerciseSession(val date: String, val sets: List<LoggedSet>)

    private fun sessionsOf(exerciseId: String, checkIns: List<CheckIn>): List<ExerciseSession> =
        workouts(checkIns)
            .map { workout -> ExerciseSession(workout.date, workout.sets.filter { it.exerciseId == exerciseId }) }
            .filter { it.sets.isNotEmpty() }

    // Rounded down to what the plates allow.
    private fun tenPercentLighter(kg: Double): Double =
        (floor(kg * 0.9 / SMALLEST_STEP_KG) * SMALLEST_STEP_KG).coerceAtLeast(SMALLEST_STEP_KG)
}

/** "22.5" or "20": no ".0". */
fun kgLabel(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else String.format(Locale.ENGLISH, "%.1f", kg)
