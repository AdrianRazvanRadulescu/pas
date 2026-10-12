package dev.pas.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * "Start 25 minutes" (or just 5, to get going): a silent countdown in the notification shade
 * and a quiet note when it ends. No sound, no alarm; the point is to get started, not to be chased.
 * Every session, even one stopped early, is saved as minutes, with the area chosen in [CoachStore.focusArea].
 */
object FocusSession {

    const val FULL_MINUTES = 25
    const val SHORT_MINUTES = 5
    private const val END_WORK_NAME = "focus-end"

    fun start(context: Context, minutes: Int) {
        val store = CoachStore(context)
        val startedAtMillis = System.currentTimeMillis()
        val endsAtMillis = startedAtMillis + TimeUnit.MINUTES.toMillis(minutes.toLong())
        store.focusStartedAtMillis = startedAtMillis
        store.focusEndsAtMillis = endsAtMillis
        store.finishedFocusMinutes = 0
        Notifications.showFocusRunning(context, minutes, endsAtMillis)

        val endRequest = OneTimeWorkRequestBuilder<FocusEndWorker>()
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(END_WORK_NAME, ExistingWorkPolicy.REPLACE, endRequest)
    }

    /** Stopped early: the minutes already worked still count. */
    fun stop(context: Context) {
        val store = CoachStore(context)
        val workedMinutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - store.focusStartedAtMillis).toInt()
        WorkManager.getInstance(context).cancelUniqueWork(END_WORK_NAME)
        Notifications.cancelFocus(context)
        finish(context, workedMinutes)
    }

    fun finish(context: Context, workedMinutes: Int) {
        val store = CoachStore(context)
        store.focusStartedAtMillis = 0
        store.focusEndsAtMillis = 0
        if (workedMinutes > 0) {
            store.addFocusMinutes(workedMinutes, store.focusArea, store.focusBlockerTitle)
            store.finishedFocusMinutes = workedMinutes
            SyncWorker.syncSoon(context)
        }
    }

    fun startPendingIntent(context: Context, minutes: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        START_REQUEST_CODE_BASE + minutes,
        startIntent(context, minutes),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun startIntent(context: Context, minutes: Int): Intent =
        Intent(context, FocusStartReceiver::class.java).putExtra(FocusStartReceiver.EXTRA_MINUTES, minutes)

    private const val START_REQUEST_CODE_BASE = 100
}

class FocusEndWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val store = CoachStore(applicationContext)
        // Stopped by hand just as the timer ran out: stop() already saved the minutes.
        if (store.focusEndsAtMillis == 0L) return Result.success()
        val plannedMinutes = TimeUnit.MILLISECONDS.toMinutes(store.focusEndsAtMillis - store.focusStartedAtMillis).toInt()
        FocusSession.finish(applicationContext, plannedMinutes)
        Notifications.showFocusDone(applicationContext, plannedMinutes)
        PasWidget().updateAll(applicationContext)
        return Result.success()
    }
}

/** The "▶ 25 min" and "5 min" buttons on the coach notification and on the widget. */
class FocusStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Notifications.cancelMessage(context)
        // A blocker is picked on purpose in the app; a button in the shade or on the widget means the usual work.
        val store = CoachStore(context)
        if (store.focusArea == BLOCKER_AREA) {
            store.focusArea = FocusArea.LEARNING.key
            store.focusBlockerTitle = ""
        }
        FocusSession.start(context, intent.getIntExtra(EXTRA_MINUTES, FocusSession.FULL_MINUTES))

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            PasWidget().updateAll(context)
            pendingResult.finish()
        }
    }

    companion object {
        const val EXTRA_MINUTES = "minutes"
    }
}
