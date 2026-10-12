package dev.pas.app

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/** Every 15 minutes: download new coach messages, upload check-ins, notify, look for a new version, refresh the widget. */
class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val store = CoachStore(applicationContext)
        if (!store.isConfigured) return Result.success()

        try {
            store.sync()
        } catch (exception: IOException) {
            return Result.retry()
        }

        notifyNewestMessage(applicationContext, store)
        notifyAboutNewVersion(store)
        PasWidget().updateAll(applicationContext)
        return Result.success()
    }

    /** Kept apart from sync(): a missing or broken release must never stop the messages from arriving. */
    private fun notifyAboutNewVersion(store: CoachStore) {
        val release = try {
            AppUpdater(applicationContext).findNewerRelease(GitHubFiles(store.githubToken, store.repository))
        } catch (exception: IOException) {
            null
        }
        // During quiet hours the notification would be dropped, so the version is announced on the first sync after 07:00.
        if (release == null || release.versionCode == store.lastNotifiedVersionCode || Notifications.isQuietHours()) return
        Notifications.showUpdate(applicationContext, release)
        store.lastNotifiedVersionCode = release.versionCode
    }

    companion object {
        val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun syncSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(networkConstraint).build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync-soon", ExistingWorkPolicy.REPLACE, request)
        }

        /**
         * Only the newest message: a reply the user is waiting for, or the coach's message of the day.
         * The coach may decide a daily message needs no notification; it still shows in the app.
         * The app marks what the user already saw, so nothing seen gets notified again.
         */
        fun notifyNewestMessage(context: Context, store: CoachStore) {
            val latestMessage = store.messages().lastOrNull() ?: return
            if (latestMessage.id == store.lastNotifiedMessageId || !isRecent(latestMessage)) return
            if (latestMessage.slot == REPLY_SLOT) Notifications.showReply(context, latestMessage)
            else if (latestMessage.notify) Notifications.showMessage(context, latestMessage)
            store.lastNotifiedMessageId = latestMessage.id
        }

        private fun isRecent(message: CoachMessage): Boolean {
            val age = Duration.between(OffsetDateTime.parse(message.createdAt), OffsetDateTime.now())
            return age < Duration.ofHours(6)
        }
    }
}
