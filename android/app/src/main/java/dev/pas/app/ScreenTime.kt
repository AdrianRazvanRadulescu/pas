package dev.pas.app

import java.time.LocalDate

/** One activity coming to the front or leaving it, or the screen turning off, as Android's usage log reports it. */
data class UsageEvent(val timeMillis: Long, val packageName: String, val className: String, val type: UsageEventType)

enum class UsageEventType { OPENED, CLOSED, SCREEN_OFF }

data class AppTime(val packageName: String, val millis: Long)

data class AppMinutes(val label: String, val minutes: Int)

/** The time on the phone in some stretch of the day, with the apps longest first. */
data class ScreenTimeSpan(val totalMinutes: Int, val apps: List<AppMinutes>)

/** What the card on Azi shows; the 7-day average comes from the check-ins, so it is not here. */
data class ScreenTimeOverview(val today: ScreenTimeSpan, val yesterdayUntilNowMinutes: Int)

/** "45 min", "2 h", "2 h 14 min". */
fun screenTimeLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

object ScreenTime {

    /** A heads-up every this many minutes of screen in a day. */
    const val NUDGE_EVERY_MINUTES = 30

    private const val APPS_NAMED = 3
    private const val AVERAGE_DAYS = 7L

    /**
     * Time in front per app between [fromMillis] and [toMillis], longest first.
     * [events] start earlier, at midnight: an app opened before the window and still open in it has no event inside it.
     * An app closed with no open event in [events] was open since before them; one still open counts until [toMillis].
     * An app counts as open while any of its activities is, so moving between its screens is one stretch.
     */
    fun appTimes(events: List<UsageEvent>, fromMillis: Long, toMillis: Long): List<AppTime> {
        val openClassesByPackage = mutableMapOf<String, MutableSet<String>>()
        val openedAtByPackage = mutableMapOf<String, Long>()
        val millisByPackage = mutableMapOf<String, Long>()
        val seenPackages = mutableSetOf<String>()
        var hasScreenTurnedOff = false

        fun addStretch(packageName: String, startMillis: Long, endMillis: Long) {
            val start = maxOf(startMillis, fromMillis)
            val end = minOf(endMillis, toMillis)
            if (end > start) millisByPackage.merge(packageName, end - start, Long::plus)
        }

        for (event in events.sortedBy { it.timeMillis }) {
            when (event.type) {
                UsageEventType.OPENED -> {
                    val openClasses = openClassesByPackage.getOrPut(event.packageName) { mutableSetOf() }
                    if (openClasses.isEmpty()) openedAtByPackage[event.packageName] = event.timeMillis
                    openClasses.add(event.className)
                }
                UsageEventType.CLOSED -> {
                    val openClasses = openClassesByPackage[event.packageName]
                    val wasOpenBeforeTheWindow = event.packageName !in seenPackages && !hasScreenTurnedOff
                    if (openClasses != null && event.className in openClasses) {
                        openClasses.remove(event.className)
                        if (openClasses.isEmpty()) addStretch(event.packageName, openedAtByPackage.getValue(event.packageName), event.timeMillis)
                    } else if (wasOpenBeforeTheWindow) {
                        addStretch(event.packageName, fromMillis, event.timeMillis)
                    }
                }
                UsageEventType.SCREEN_OFF -> {
                    // Some phones report the pause late, so the screen turning off closes whatever is still open.
                    for ((packageName, openClasses) in openClassesByPackage) {
                        if (openClasses.isEmpty()) continue
                        addStretch(packageName, openedAtByPackage.getValue(packageName), event.timeMillis)
                        openClasses.clear()
                    }
                    hasScreenTurnedOff = true
                }
            }
            seenPackages.add(event.packageName)
        }

        for ((packageName, openClasses) in openClassesByPackage) {
            if (openClasses.isEmpty()) continue
            addStretch(packageName, openedAtByPackage.getValue(packageName), toMillis)
        }
        return millisByPackage.map { AppTime(it.key, it.value) }.sortedByDescending { it.millis }
    }

    /** "YouTube 48 min, Chrome 20 min, WhatsApp 12 min". */
    fun topAppsText(apps: List<AppMinutes>): String =
        apps.take(APPS_NAMED).joinToString(", ") { "${it.label} ${screenTimeLabel(it.minutes)}" }

    /** For the status bar icon, where only two or three characters fit: "45m", "2h". */
    fun shortLabel(minutes: Int): String = if (minutes < 60) "${minutes}m" else "${minutes / 60}h"

    /** Over the days before [today] that have a total; null before the first one. */
    fun averageMinutes(checkIns: List<CheckIn>, today: LocalDate): Int? {
        val firstDay = today.minusDays(AVERAGE_DAYS).toString()
        val dayTotals = checkIns
            .filter { it.kind == SCREEN_TIME_KIND && it.date >= firstDay && it.date < today.toString() }
            .mapNotNull { it.minutes }
        if (dayTotals.isEmpty()) return null
        return dayTotals.sum() / dayTotals.size
    }
}
