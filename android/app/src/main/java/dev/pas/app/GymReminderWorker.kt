package dev.pas.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** At the hour he picked for the gym: a heads-up with his own reason and today's challenge. */
class GymReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val store = CoachStore(applicationContext)
        val checkIns = store.checkIns()
        val today = LocalDate.now()
        val plan = Stats.todayCheckIn(checkIns, PLAN_KIND, today)
        val plannedTime = inputData.getString(KEY_TIME)
        val isStillPlanned = plan?.note == GYM_PLAN && plan.time == plannedTime
        val hasWorkedOutToday = Stats.todayCheckIn(checkIns, WORKOUT_KIND, today) != null || store.workoutDraft() != null
        if (!isStillPlanned || hasWorkedOutToday) return Result.success()

        val gymDay = GymProgram.nextDay(checkIns)
        val lines = listOfNotNull(
            store.gymReason.takeIf { it.isNotBlank() }?.let { "“$it”" },
            GymProgram.challengeFor(gymDay, checkIns, today) ?: gymDay.name,
        )
        Notifications.showGymReminder(applicationContext, title = "It's $plannedTime, gym time", text = lines.joinToString("\n"))
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "gym-reminder"
        private const val KEY_TIME = "time"

        /** [time] as saved in the plan, "18:00". A new plan replaces the reminder of the old one. */
        fun schedule(context: Context, time: String) {
            val delay = Duration.between(LocalDateTime.now(), LocalDate.now().atTime(LocalTime.parse(time)))
            if (delay.isNegative) return
            val request = OneTimeWorkRequestBuilder<GymReminderWorker>()
                .setInitialDelay(delay)
                .setInputData(workDataOf(KEY_TIME to time))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
