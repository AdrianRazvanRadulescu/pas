package dev.pas.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Full body three times a week; the profile says 3-4, and the fourth is a bonus, not a debt. */
const val WEEKLY_GYM_TARGET = 3

/** Days between card reviews: after "Easy" a card moves one step right, after "Didn't know" it starts again. */
val cardIntervalsInDays = listOf(1, 3, 7, 21, 60)

/** At most this many cards a day, so the review stays a two-minute thing. */
const val DAILY_CARD_LIMIT = 10

/** Real work: a focus session, a workout or one of the old ticks. A pause, a plan or a chat message is not a start. */
private val startedKinds = setOf(FOCUS_KIND, WORKOUT_KIND) + oldTickKinds

private val gymKinds = setOf(GYM_KIND, WORKOUT_KIND)

/** "1 day", "3 days". */
fun daysLabel(days: Int): String = if (days == 1) "1 day" else "$days days"

/** "1 minute", "25 minutes". */
fun minutesLabel(minutes: Int): String = if (minutes == 1) "1 minute" else "$minutes minutes"

/** "2 h 15 min" for the weekly total; plain minutes under an hour. */
fun hoursAndMinutesLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

object Stats {

    fun startOfWeek(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun gymDaysInWeek(checkIns: List<CheckIn>, anyDayOfWeek: LocalDate): Int {
        val monday = startOfWeek(anyDayOfWeek)
        val sunday = monday.plusDays(6)
        return checkIns
            .filter { it.kind in gymKinds }
            .map { LocalDate.parse(it.date) }
            .filter { it in monday..sunday }
            .distinct()
            .size
    }

    fun startedDays(checkIns: List<CheckIn>): Set<LocalDate> =
        checkIns.filter { it.kind in startedKinds }.map { LocalDate.parse(it.date) }.toSet()

    /** "Days you got started: 4/7", Monday to Sunday. */
    fun startedDaysInWeek(checkIns: List<CheckIn>, anyDayOfWeek: LocalDate): Int {
        val monday = startOfWeek(anyDayOfWeek)
        return startedDays(checkIns).count { it in monday..monday.plusDays(6) }
    }

    /**
     * How many times the user started again after at least one day without starting.
     * A chosen pause is still a day without a start, so coming back after it counts as a return too.
     */
    fun returnCount(checkIns: List<CheckIn>): Int {
        val days = startedDays(checkIns)
        return days.count { day -> isReturn(day, days) }
    }

    /** Today is a start after a gap: the Today tab says so, because coming back matters more than any streak. */
    fun isReturnDay(checkIns: List<CheckIn>, today: LocalDate): Boolean {
        val days = startedDays(checkIns)
        return today in days && isReturn(today, days)
    }

    private fun isReturn(day: LocalDate, startedDays: Set<LocalDate>): Boolean =
        day.minusDays(1) !in startedDays && startedDays.any { it < day }

    fun focusMinutesBetween(checkIns: List<CheckIn>, firstDay: LocalDate, lastDay: LocalDate): Int =
        checkIns
            .filter { it.kind == FOCUS_KIND && LocalDate.parse(it.date) in firstDay..lastDay }
            .sumOf { it.minutes ?: 0 }

    /** The last day with a workout or a gym tick, or null if there was none yet. */
    fun lastGymDay(checkIns: List<CheckIn>): LocalDate? =
        checkIns.filter { it.kind in gymKinds }.maxOfOrNull { LocalDate.parse(it.date) }

    fun todayCheckIn(checkIns: List<CheckIn>, kind: String, today: LocalDate): CheckIn? =
        checkIns.lastOrNull { it.date == today.toString() && it.kind == kind }

    /** Cards whose next review is today or earlier, the most overdue first, at most the daily limit. */
    fun dueCards(cards: List<Card>, checkIns: List<CheckIn>, today: LocalDate): List<Card> =
        cards
            .map { card -> card to nextReviewDay(card, checkIns) }
            .filter { (_, nextReview) -> !nextReview.isAfter(today) }
            .sortedBy { (_, nextReview) -> nextReview }
            .map { (card, _) -> card }
            .take(DAILY_CARD_LIMIT)

    /** A new card is first asked the day after it was made; each review then moves it along the intervals. */
    fun nextReviewDay(card: Card, checkIns: List<CheckIn>): LocalDate {
        val reviews = checkIns.filter { it.kind == CARD_KIND && it.cardId == card.id }.sortedBy { it.createdAt }
        var step = 0
        var nextReview = LocalDate.parse(card.createdOn).plusDays(1)
        for (review in reviews) {
            step = when (review.grade) {
                CardGrade.AGAIN.key -> 0
                CardGrade.EASY.key -> (step + 1).coerceAtMost(cardIntervalsInDays.lastIndex)
                else -> step
            }
            nextReview = LocalDate.parse(review.date).plusDays(cardIntervalsInDays[step].toLong())
        }
        return nextReview
    }

    /** The blockers from profile.md the user has not ticked yet. */
    fun openBlockers(blockers: List<String>, checkIns: List<CheckIn>): List<String> {
        val doneTitles = checkIns.filter { it.kind == BLOCKER_KIND }.map { it.note }.toSet()
        return blockers.filter { it !in doneTitles }
    }
}
