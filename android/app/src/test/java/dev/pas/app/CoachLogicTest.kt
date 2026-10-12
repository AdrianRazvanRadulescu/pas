package dev.pas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class CoachLogicTest {

    private val thursday = LocalDate.parse("2026-10-08")

    private fun checkIn(date: String, kind: String) =
        CheckIn(id = "$date-$kind", date = date, createdAt = "${date}T10:00:00Z", kind = kind)

    private fun workout(date: String, dayId: String, exerciseId: String, kg: Double, reps: List<Int>) = CheckIn(
        id = "$date-workout",
        date = date,
        createdAt = "${date}T18:00:00Z",
        kind = WORKOUT_KIND,
        programDayId = dayId,
        sets = reps.map { LoggedSet(exerciseId, exerciseId, kg, it) },
    )

    private val squat = GymProgram.day("A").exercises.first { it.id == "squat" }

    @Test
    fun programDaysTakeTurnsStartingWithA() {
        assertEquals("A", GymProgram.nextDay(emptyList()).id)
        assertEquals("B", GymProgram.nextDay(listOf(workout("2026-10-06", "A", "squat", 20.0, listOf(10)))).id)
    }

    @Test
    fun firstTimeStartsFromTheProgramWeight() {
        val target = GymProgram.targetFor(squat, emptyList(), thursday)
        assertEquals(squat.startKg, target.kg, 0.0)
        assertEquals(TargetReason.FIRST_TIME, target.reason)
    }

    @Test
    fun twelveRepsOnEverySetMakesItHeavier() {
        val checkIns = listOf(workout("2026-10-06", "A", "squat", 20.0, listOf(12, 12, 12)))
        val target = GymProgram.targetFor(squat, checkIns, thursday)
        assertEquals(25.0, target.kg, 0.0)
        assertEquals(TargetReason.HEAVIER, target.reason)
    }

    @Test
    fun twoSessionsUnderEightMakeItLighter() {
        val checkIns = listOf(
            workout("2026-10-01", "A", "squat", 40.0, listOf(7, 6, 6)),
            workout("2026-10-06", "A", "squat", 40.0, listOf(8, 7, 6)),
        )
        val target = GymProgram.targetFor(squat, checkIns, thursday)
        assertEquals(35.0, target.kg, 0.0)
        assertEquals(TargetReason.LIGHTER_AFTER_MISSES, target.reason)
    }

    @Test
    fun aLongBreakMakesItLighter() {
        val checkIns = listOf(workout("2026-09-20", "A", "squat", 40.0, listOf(10, 10, 10)))
        assertEquals(TargetReason.LIGHTER_AFTER_BREAK, GymProgram.targetFor(squat, checkIns, thursday).reason)
    }

    @Test
    fun aChosenPauseWinsOverEverything() {
        val checkIns = listOf(checkIn("2026-10-08", SKIP_KIND))
        assertTrue(NextActions.choose(checkIns, thursday.atTime(10, 0)) is NextAction.Resting)
    }

    @Test
    fun theGymComesFirstWhenRestedAndUnderTheWeeklyTarget() {
        val checkIns = listOf(workout("2026-10-05", "A", "squat", 20.0, listOf(10)))
        val action = NextActions.choose(checkIns, thursday.atTime(10, 0))
        assertTrue(action is NextAction.Gym)
        assertEquals("B", (action as NextAction.Gym).day.id)
    }

    @Test
    fun theDayAfterTheGymTheAfternoonAsksForAPlan() {
        val checkIns = listOf(workout("2026-10-07", "A", "squat", 20.0, listOf(10)))
        assertTrue(NextActions.choose(checkIns, thursday.atTime(10, 0)) is NextAction.Focus)
        assertTrue(NextActions.choose(checkIns, thursday.atTime(16, 0)) is NextAction.PlanEvening)
        val withPlan = checkIns + checkIn("2026-10-08", PLAN_KIND)
        assertTrue(NextActions.choose(withPlan, thursday.atTime(16, 0)) is NextAction.Focus)
    }

    @Test
    fun aChatMessageWaitsThenIsAnsweredOrThePcSeemsOff() {
        val sentAt = OffsetDateTime.parse("2026-10-08T10:00:00Z")
        val chat = CheckIn(id = "c1", date = "2026-10-08", createdAt = sentAt.toString(), kind = CHAT_KIND, note = "Hello", askCoach = true)
        val uploaded = sentAt.toInstant().toEpochMilli() + 1000

        val notUploaded = CoachThread.build(listOf(chat), emptyList(), 0, sentAt.plusSeconds(5))
        assertEquals(ReplyStatus.SENDING, (notUploaded.single() as ThreadItem.FromHim).status)

        val waiting = CoachThread.build(listOf(chat), emptyList(), uploaded, sentAt.plusSeconds(30))
        assertEquals(ReplyStatus.WAITING, (waiting.single() as ThreadItem.FromHim).status)
        assertTrue(CoachThread.isWaitingForReply(waiting, sentAt.plusSeconds(30)))

        val late = sentAt.plusMinutes(4)
        val pcOff = CoachThread.build(listOf(chat), emptyList(), uploaded, late)
        assertEquals(ReplyStatus.PC_SEEMS_OFF, (pcOff.single() as ThreadItem.FromHim).status)

        val reply = CoachMessage(
            id = "r1",
            createdAt = sentAt.plusSeconds(40).toString(),
            slot = REPLY_SLOT,
            body = "Hello!",
            focus = "",
            covers = listOf("c1"),
        )
        val answered = CoachThread.build(listOf(chat), listOf(reply), uploaded, sentAt.plusSeconds(50))
        assertEquals(ReplyStatus.ANSWERED, (answered.first() as ThreadItem.FromHim).status)
        assertTrue(answered.last() is ThreadItem.FromCoach)
        assertFalse(CoachThread.isWaitingForReply(answered, sentAt.plusSeconds(50)))
    }

    @Test
    fun aFeelingSentLongAfterTheWorkoutStillWaitsForTheCoach() {
        val sentAt = OffsetDateTime.parse("2026-10-08T19:00:00Z")
        val workoutEndedEarlier = workout("2026-10-08", "A", "squat", 20.0, listOf(10))
        val feeling = workoutEndedEarlier.copy(feeling = Feeling.HARD.key, askCoach = true, createdAt = sentAt.toString())
        val uploaded = sentAt.toInstant().toEpochMilli() + 1000

        val thread = CoachThread.build(listOf(feeling), emptyList(), uploaded, sentAt.plusSeconds(20))
        assertEquals(ReplyStatus.WAITING, thread.filterIsInstance<ThreadItem.FromHim>().single().status)
        assertTrue(CoachThread.isWaitingForReply(thread, sentAt.plusSeconds(20)))
    }

    @Test
    fun yesterdaysFocusIsNotTodays() {
        val yesterday = CoachMessage(id = "e", createdAt = "2026-10-07T15:00:00Z", slot = "evening", body = "B", focus = "Yesterday")
        assertEquals(null, todayFocus(listOf(yesterday), thursday))
        val morning = yesterday.copy(id = "m", createdAt = "2026-10-08T06:30:00Z", slot = "morning", focus = "Today")
        assertEquals("Today", todayFocus(listOf(yesterday, morning), thursday))
    }

    @Test
    fun theGymChallengeIsOneMoreRepOrAHeavierWeight() {
        val dayA = GymProgram.day("A")
        assertEquals(null, GymProgram.challengeFor(dayA, emptyList(), thursday))
        val heavier = GymProgram.challengeFor(dayA, listOf(workout("2026-10-06", "A", "squat", 20.0, listOf(12, 12, 12))), thursday)!!
        assertTrue(heavier.contains("25 kg"))
        val same = GymProgram.challengeFor(dayA, listOf(workout("2026-10-06", "A", "squat", 20.0, listOf(10, 9, 8))), thursday)!!
        assertTrue(same.contains("27 reps"))
    }

    @Test
    fun dailyMessagesLeaveOutReplies() {
        val morning = CoachMessage(id = "m", createdAt = "2026-10-08T08:30:00Z", slot = "morning", body = "B", focus = "F")
        val reply = morning.copy(id = "r", slot = REPLY_SLOT)
        assertEquals(listOf(morning), dailyMessages(listOf(morning, reply)))
    }

    private fun opened(minute: Long, packageName: String, className: String = "Main") =
        UsageEvent(minute * 60_000, packageName, className, UsageEventType.OPENED)

    private fun closed(minute: Long, packageName: String, className: String = "Main") =
        UsageEvent(minute * 60_000, packageName, className, UsageEventType.CLOSED)

    private fun minutesByPackage(events: List<UsageEvent>, fromMinute: Long, toMinute: Long): Map<String, Long> =
        ScreenTime.appTimes(events, fromMinute * 60_000, toMinute * 60_000).associate { it.packageName to it.millis / 60_000 }

    @Test
    fun appTimeCountsFromOpenToClose() {
        val events = listOf(opened(10, "youtube"), closed(25, "youtube"), opened(30, "chrome"), closed(35, "chrome"))
        assertEquals(mapOf("youtube" to 15L, "chrome" to 5L), minutesByPackage(events, 0, 60))
    }

    @Test
    fun appOpenBeforeTheWindowCountsFromItsStart() {
        assertEquals(mapOf("youtube" to 5L), minutesByPackage(listOf(closed(5, "youtube")), 0, 60))
    }

    @Test
    fun appStillOpenCountsUntilNow() {
        assertEquals(mapOf("instagram" to 20L), minutesByPackage(listOf(opened(40, "instagram")), 0, 60))
    }

    @Test
    fun movingBetweenScreensOfOneAppIsOneStretch() {
        val events = listOf(
            opened(0, "chrome", "Tabs"),
            opened(10, "chrome", "Settings"),
            closed(10, "chrome", "Tabs"),
            closed(20, "chrome", "Settings"),
        )
        assertEquals(mapOf("chrome" to 20L), minutesByPackage(events, 0, 60))
    }

    @Test
    fun screenOffClosesWhatIsOpenAndALatePauseAddsNothing() {
        val events = listOf(
            opened(0, "youtube"),
            UsageEvent(10 * 60_000, "android", "", UsageEventType.SCREEN_OFF),
            closed(30, "youtube"),
        )
        assertEquals(mapOf("youtube" to 10L), minutesByPackage(events, 0, 60))
    }

    @Test
    fun appOpenSinceBeforeTheWindowCountsOnlyInsideIt() {
        assertEquals(mapOf("youtube" to 30L), minutesByPackage(listOf(opened(10, "youtube")), 30, 60))
        assertEquals(mapOf("youtube" to 10L), minutesByPackage(listOf(opened(10, "youtube"), closed(40, "youtube")), 30, 60))
        assertEquals(emptyMap<String, Long>(), minutesByPackage(listOf(opened(10, "youtube"), closed(20, "youtube")), 30, 60))
    }

    @Test
    fun screenTimeLabels() {
        assertEquals("45 min", screenTimeLabel(45))
        assertEquals("2 h", screenTimeLabel(120))
        assertEquals("2 h 14 min", screenTimeLabel(134))
        assertEquals("45m", ScreenTime.shortLabel(45))
        assertEquals("2h", ScreenTime.shortLabel(134))
    }

    @Test
    fun weekAverageLeavesOutTodayAndOlderDays() {
        val checkIns = listOf("2026-10-08" to 100, "2026-10-07" to 200, "2026-10-01" to 999, "2026-10-09" to 50).map { (date, minutes) ->
            checkIn(date, SCREEN_TIME_KIND).copy(minutes = minutes)
        }
        assertEquals(150, ScreenTime.averageMinutes(checkIns, LocalDate.parse("2026-10-09")))
        assertEquals(null, ScreenTime.averageMinutes(emptyList(), LocalDate.parse("2026-10-09")))
    }

    @Test
    fun screenTimeDoesNotCountAsAStartedDay() {
        val checkIns = listOf(checkIn(thursday.toString(), SCREEN_TIME_KIND))
        assertEquals(0, Stats.startedDaysInWeek(checkIns, thursday))
    }
}
