package dev.pas.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import java.time.LocalDateTime
import java.time.ZoneId

/** Reads Android's usage log. Works only after the user turns on Settings → Usage access → Pas. */
class ScreenTimeReader(private val context: Context) {

    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Without the home screen, where the user only looks for an app, and without Pas itself, so logging sets at the gym is not phone time.
     * Events are read from midnight of [from]'s day, so an app open since before [from] is still seen.
     */
    fun span(from: LocalDateTime, to: LocalDateTime): ScreenTimeSpan {
        val fromMillis = millisOf(from)
        val toMillis = millisOf(to)
        val leftOut = setOf(homePackage(), context.packageName)
        val events = readEvents(millisOf(from.toLocalDate().atStartOfDay()), toMillis)
        val appTimes = ScreenTime.appTimes(events, fromMillis, toMillis).filter { it.packageName !in leftOut }
        val apps = appTimes
            .map { AppMinutes(labelOf(it.packageName), (it.millis / 60_000).toInt()) }
            .filter { it.minutes > 0 }
        return ScreenTimeSpan(totalMinutes = (appTimes.sumOf { it.millis } / 60_000).toInt(), apps = apps)
    }

    private fun readEvents(fromMillis: Long, toMillis: Long): List<UsageEvent> {
        val usageEvents = usageStats.queryEvents(fromMillis, toMillis)
        val event = UsageEvents.Event()
        val events = mutableListOf<UsageEvent>()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.OPENED
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventType.CLOSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventType.SCREEN_OFF
                else -> continue
            }
            events += UsageEvent(event.timeStamp, event.packageName, event.className.orEmpty(), type)
        }
        return events
    }

    private fun homePackage(): String? {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    private fun labelOf(packageName: String): String {
        val packageManager = context.packageManager
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (exception: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun millisOf(time: LocalDateTime): Long = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
