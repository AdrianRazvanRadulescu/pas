package dev.pas.app

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** The one thing the Today tab puts first, with one line that says why. */
sealed interface NextAction {
    val reason: String

    data class Gym(val day: ProgramDay, override val reason: String) : NextAction
    data class PlanEvening(override val reason: String) : NextAction
    data class Focus(override val reason: String) : NextAction
    data class Resting(override val reason: String) : NextAction
}

object NextActions {

    /** After this hour the card stops proposing the gym: the day closes at 22:00. */
    private const val LAST_GYM_HOUR = 20
    private const val PLAN_FROM_HOUR = 15
    private const val PLAN_UNTIL_HOUR = 21

    /**
     * In order: a chosen pause wins; then the gym on a gym day; after 15:00 without a plan, the plan for tonight;
     * otherwise 25 minutes of focus. Deterministic, so the same moment always gives the same card.
     */
    fun choose(checkIns: List<CheckIn>, now: LocalDateTime): NextAction {
        val today = now.toLocalDate()
        val skipToday = Stats.todayCheckIn(checkIns, SKIP_KIND, today)
        if (skipToday != null) {
            return NextAction.Resting("You chose a break today${skipToday.note?.let { " ($it)" }.orEmpty()}. That is fine, tomorrow is another day.")
        }

        val gymDaysThisWeek = Stats.gymDaysInWeek(checkIns, today)
        val lastGymDay = Stats.lastGymDay(checkIns)
        val isRestedEnough = lastGymDay == null || ChronoUnit.DAYS.between(lastGymDay, today) >= 2
        if (isRestedEnough && gymDaysThisWeek < WEEKLY_GYM_TARGET && now.hour <= LAST_GYM_HOUR) {
            val reason = if (lastGymDay == null) {
                "First workout of the program: light weights, just to see how it goes."
            } else {
                val daysAgo = ChronoUnit.DAYS.between(lastGymDay, today).toInt()
                "Last time was ${daysLabel(daysAgo)} ago. $gymDaysThisWeek of $WEEKLY_GYM_TARGET this week."
            }
            return NextAction.Gym(GymProgram.nextDay(checkIns), reason)
        }

        val hasPlanToday = Stats.todayCheckIn(checkIns, PLAN_KIND, today) != null
        if (!hasPlanToday && now.hour in PLAN_FROM_HOUR until PLAN_UNTIL_HOUR) {
            return NextAction.PlanEvening("The evening goes easier with a plan made now, not at 21:00.")
        }

        return NextAction.Focus("One thing, ${minutesLabel(FocusSession.FULL_MINUTES)}. The hard part is only the start.")
    }
}
