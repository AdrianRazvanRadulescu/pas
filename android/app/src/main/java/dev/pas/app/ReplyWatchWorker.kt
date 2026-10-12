package dev.pas.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/**
 * After the user writes to the coach: every 30 seconds, upload what the user wrote and look for the answer,
 * until it comes or a few minutes pass. Brings the reply as a notification when the app is closed.
 */
class ReplyWatchWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val store = CoachStore(applicationContext)
        if (!store.isConfigured) return Result.success()
        try {
            store.sync()
            SyncWorker.notifyNewestMessage(applicationContext, store)
        } catch (exception: IOException) {
            // No network for a moment: the next round tries again, and the 15-minute sync after that.
        }
        val thread = CoachThread.build(store.checkIns(), store.messages(), store.uploadedUntilMillis, OffsetDateTime.now())
        if (CoachThread.isWaitingForReply(thread, OffsetDateTime.now())) {
            enqueue(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "reply-watch"
        private const val CHECK_EVERY_SECONDS = 30L

        /** Called right after the user sends something; a new question restarts the watch. */
        fun start(context: Context) = enqueue(context, ExistingWorkPolicy.REPLACE)

        private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<ReplyWatchWorker>()
                .setInitialDelay(CHECK_EVERY_SECONDS, TimeUnit.SECONDS)
                .setConstraints(SyncWorker.networkConstraint)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
