package dev.pas.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes, without network: today's time in the shade, a heads-up for every 30 minutes of screen,
 * and the day's total saved as a check-in, so the coach sees it.
 */
class ScreenTimeWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val reader = ScreenTimeReader(applicationContext)
        if (!reader.hasAccess()) return Result.success()
        val store = CoachStore(applicationContext)
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        if (store.screenTimeDay != today.toString()) {
            saveYesterday(reader, store, today)
            store.screenTimeDay = today.toString()
            store.screenTimeNudgedSteps = 0
            store.isScreenTimeShadeDismissed = false
        }

        val todaySpan = reader.span(today.atStartOfDay(), now)
        // Once the user swipes it away it stays away until tomorrow; putting it back every 15 minutes would be nagging.
        if (!store.isScreenTimeShadeDismissed) Notifications.showScreenTimeInShade(applicationContext, todaySpan)

        val steps = todaySpan.totalMinutes / ScreenTime.NUDGE_EVERY_MINUTES
        if (steps > store.screenTimeNudgedSteps) {
            val lastHalfHour = reader.span(now.minusMinutes(ScreenTime.NUDGE_EVERY_MINUTES.toLong()), now)
            Notifications.showScreenTimeNudge(
                applicationContext,
                title = "${screenTimeLabel(todaySpan.totalMinutes)} on the phone today",
                text = nudgeText(lastHalfHour, store, now),
            )
            store.screenTimeNudgedSteps = steps
            store.saveScreenTime(today, todaySpan.totalMinutes, ScreenTime.topAppsText(todaySpan.apps))
            SyncWorker.syncSoon(applicationContext)
        }
        return Result.success()
    }

    /** The first run of a day closes the one before with its full total. */
    private fun saveYesterday(reader: ScreenTimeReader, store: CoachStore, today: LocalDate) {
        val yesterday = today.minusDays(1)
        val yesterdaySpan = reader.span(yesterday.atStartOfDay(), today.atStartOfDay())
        if (yesterdaySpan.totalMinutes == 0) return
        store.saveScreenTime(yesterday, yesterdaySpan.totalMinutes, ScreenTime.topAppsText(yesterdaySpan.apps))
        SyncWorker.syncSoon(applicationContext)
    }


    /** The facts of the last half hour, and in the evening without a plan, the question that matters most. */
    private fun nudgeText(lastHalfHour: ScreenTimeSpan, store: CoachStore, now: LocalDateTime): String {
        val lastHalfHourText = if (lastHalfHour.apps.isEmpty()) {
            "Last half hour: almost nothing."
        } else {
            "Last half hour: ${ScreenTime.topAppsText(lastHalfHour.apps)}."
        }
        val hasPlanTonight = Stats.todayCheckIn(store.checkIns(), PLAN_KIND, now.toLocalDate()) != null
        if (now.hour in EVENING_PLAN_HOURS && !hasPlanTonight) return "$lastHalfHourText\nWhat are you doing tonight? Pick a plan in Pas."
        return lastHalfHourText
    }

    companion object {
        private val EVENING_PLAN_HOURS = 18..21

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScreenTimeWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("screen-time", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Right after the user grants access, so the time shows up without waiting a quarter of an hour. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ScreenTimeWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork("screen-time-now", ExistingWorkPolicy.KEEP, request)
        }
    }
}
