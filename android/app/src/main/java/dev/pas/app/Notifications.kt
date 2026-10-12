package dev.pas.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import dev.pas.app.ui.PasStyle
import dev.pas.app.ui.colorSchemeOf
import java.time.LocalTime
import kotlin.math.roundToInt

object Notifications {

    private const val CHANNEL_ID = "coach"
    private const val MESSAGE_NOTIFICATION_ID = 1
    private const val FOCUS_NOTIFICATION_ID = 2
    private const val UPDATE_NOTIFICATION_ID = 4
    private const val REPLY_NOTIFICATION_ID = 5
    private const val REST_NOTIFICATION_ID = 6
    private const val SCREEN_TIME_NOTIFICATION_ID = 7
    private const val SCREEN_TIME_NUDGE_NOTIFICATION_ID = 8
    private const val GYM_REMINDER_NOTIFICATION_ID = 9

    private const val NUDGE_CHANNEL_ID = "nudges"
    private const val SCREEN_TIME_CHANNEL_ID = "screen_time"
    private const val STATUS_BAR_ICON_DP = 24

    /** After 22:00 the day is closed: nothing nudges until morning, except a focus session the user started. */
    private val quietFrom = LocalTime.of(22, 0)
    private val quietUntil = LocalTime.of(7, 0)

    /** No channel makes a sound or vibrates. A channel's importance and sound are fixed once created, hence one id per kind. */
    fun createChannels(context: Context) {
        val coachChannel = NotificationChannel(CHANNEL_ID, "Coach messages", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Messages of the day, coach replies, focus and the rest between sets, with no sound and no vibration."
            setSound(null, null)
            enableVibration(false)
        }
        // High importance is what lets a notification slide in over the app the user is in.
        val nudgeChannel = NotificationChannel(NUDGE_CHANNEL_ID, "Reminders on screen", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Phone time every 30 minutes, and gym time. They slide in at the top of the screen, with no sound and no vibration."
            setSound(null, null)
            enableVibration(false)
        }
        val screenTimeChannel = NotificationChannel(SCREEN_TIME_CHANNEL_ID, "Phone time today", NotificationManager.IMPORTANCE_LOW).apply {
            description = "The fixed notification with today's phone time."
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(coachChannel, nudgeChannel, screenTimeChannel))
    }

    fun isQuietHours(): Boolean {
        val now = LocalTime.now()
        return !now.isBefore(quietFrom) || now.isBefore(quietUntil)
    }

    fun showMessage(context: Context, message: CoachMessage) {
        val builder = silentBuilder(context)
            .setContentTitle(titleFor(message.slot))
            .setContentText(message.focus)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setAutoCancel(true)

        addStartActions(context, builder)
        notifyUnlessQuiet(context, MESSAGE_NOTIFICATION_ID, builder)
    }

    /** The user asked something a minute ago, so the answer comes even after 22:00. */
    fun showReply(context: Context, message: CoachMessage) {
        val builder = silentBuilder(context, openCoachTab(context))
            .setContentTitle("The coach replied")
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setAutoCancel(true)
        notify(context, REPLY_NOTIFICATION_ID, builder)
    }

    /** The countdown between sets, for when the screen is off. */
    fun showRest(context: Context, nextExerciseName: String, endsAtMillis: Long) {
        val builder = silentBuilder(context)
            .setContentTitle("Rest, then $nextExerciseName")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endsAtMillis)
            .setTimeoutAfter(endsAtMillis - System.currentTimeMillis())
        notify(context, REST_NOTIFICATION_ID, builder)
    }

    /**
     * Not marked silent: a silent notification never slides in, so the user would not see it while scrolling; the channel has no sound.
     * Also after 22:00, on purpose: scrolling late is exactly when it helps.
     */
    fun showScreenTimeNudge(context: Context, title: String, text: String) {
        val builder = nudgeBuilder(context)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        addStartActions(context, builder)
        notify(context, SCREEN_TIME_NUDGE_NOTIFICATION_ID, builder)
    }

    fun showGymReminder(context: Context, title: String, text: String) {
        val builder = nudgeBuilder(context)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        notify(context, GYM_REMINDER_NOTIFICATION_ID, builder)
    }

    /** Always there, with today's time drawn as the status bar icon, so a glance at the top of the screen is enough. */
    fun showScreenTimeInShade(context: Context, today: ScreenTimeSpan) {
        val builder = NotificationCompat.Builder(context, SCREEN_TIME_CHANNEL_ID)
            .setSmallIcon(IconCompat.createWithBitmap(statusBarIcon(context, ScreenTime.shortLabel(today.totalMinutes))))
            .setColor(themeColor(context))
            .setContentTitle("${screenTimeLabel(today.totalMinutes)} on the phone today")
            .setContentText(ScreenTime.topAppsText(today.apps))
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setDeleteIntent(
                PendingIntent.getBroadcast(context, 0, Intent(context, ScreenTimeDismissedReceiver::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
        notify(context, SCREEN_TIME_NOTIFICATION_ID, builder)
    }

    fun cancelReply(context: Context) {
        NotificationManagerCompat.from(context).cancel(REPLY_NOTIFICATION_ID)
    }

    fun cancelRest(context: Context) {
        NotificationManagerCompat.from(context).cancel(REST_NOTIFICATION_ID)
    }

    fun showUpdate(context: Context, release: GitHubFiles.Release) {
        val builder = silentBuilder(context)
            .setContentTitle("A new version of Pas is available")
            .setContentText(release.notes.ifBlank { "Tap to install it." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(release.notes.ifBlank { "Tap to install it." }))
            .setAutoCancel(true)
        notifyUnlessQuiet(context, UPDATE_NOTIFICATION_ID, builder)
    }

    fun showFocusRunning(context: Context, minutes: Int, endsAtMillis: Long) {
        val builder = silentBuilder(context)
            .setContentTitle("${minutesLabel(minutes)}, just this")
            .setContentText(dailyMessages(CoachStore(context).messages()).lastOrNull()?.focus ?: "One thing, until the end.")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endsAtMillis)
            .setOngoing(true)
            .setTimeoutAfter(endsAtMillis - System.currentTimeMillis())
        notify(context, FOCUS_NOTIFICATION_ID, builder)
    }

    fun showFocusDone(context: Context, minutes: Int) {
        val builder = silentBuilder(context)
            .setContentTitle("Done: ${minutesLabel(minutes)}")
            .setContentText("You showed up. That is what counts. What did you understand? Write in Pas, the coach will reply.")
            .setAutoCancel(true)
            .addAction(0, "▶ ${FocusSession.FULL_MINUTES} more min", FocusSession.startPendingIntent(context, FocusSession.FULL_MINUTES))
        notify(context, FOCUS_NOTIFICATION_ID, builder)
    }

    fun cancelMessage(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(MESSAGE_NOTIFICATION_ID)
    }

    fun cancelFocus(context: Context) {
        NotificationManagerCompat.from(context).cancel(FOCUS_NOTIFICATION_ID)
    }

    private fun addStartActions(context: Context, builder: NotificationCompat.Builder) {
        builder.addAction(0, "▶ ${FocusSession.FULL_MINUTES} min", FocusSession.startPendingIntent(context, FocusSession.FULL_MINUTES))
        builder.addAction(0, "Just ${FocusSession.SHORT_MINUTES} min", FocusSession.startPendingIntent(context, FocusSession.SHORT_MINUTES))
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openCoachTab(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_COACH, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun silentBuilder(context: Context, onTap: PendingIntent = openApp(context)): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(themeColor(context))
            .setContentIntent(onTap)
            .setSilent(true)
    }

    private fun nudgeBuilder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, NUDGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(themeColor(context))
            .setContentIntent(openApp(context))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
    }

    private fun themeColor(context: Context): Int = colorSchemeOf(PasStyle.fromKey(CoachStore(context).styleKey)).primary.toArgb()

    /** White text on transparent: Android uses only the alpha of a status bar icon and tints it itself. */
    private fun statusBarIcon(context: Context, text: String): Bitmap {
        val sizePx = (STATUS_BAR_ICON_DP * context.resources.displayMetrics.density).roundToInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.62f
        }
        val textWidth = paint.measureText(text)
        if (textWidth > sizePx) paint.textSize = paint.textSize * sizePx / textWidth
        val baseline = sizePx / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bitmap).drawText(text, sizePx / 2f, baseline, paint)
        return bitmap
    }

    private fun notifyUnlessQuiet(context: Context, notificationId: Int, builder: NotificationCompat.Builder) {
        if (isQuietHours()) return
        notify(context, notificationId, builder)
    }

    private fun notify(context: Context, notificationId: Int, builder: NotificationCompat.Builder) {
        val canNotify = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!canNotify) return
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun titleFor(slot: String): String = when (slot) {
        "morning" -> "Good morning"
        "midday" -> "A small step"
        else -> "Pas"
    }
}
