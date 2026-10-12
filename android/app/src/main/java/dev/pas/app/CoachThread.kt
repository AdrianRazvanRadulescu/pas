package dev.pas.app

import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

enum class ReplyStatus { SENDING, WAITING, PC_SEEMS_OFF, ANSWERED, NO_REPLY_EXPECTED }

/** One line of the Coach tab: something the user wrote, something the coach wrote, or a grey fact about what the user did. */
sealed interface ThreadItem {
    val id: String
    val createdAt: OffsetDateTime

    data class FromHim(override val id: String, override val createdAt: OffsetDateTime, val text: String, val status: ReplyStatus) : ThreadItem
    data class FromCoach(override val id: String, override val createdAt: OffsetDateTime, val message: CoachMessage) : ThreadItem
    data class Event(override val id: String, override val createdAt: OffsetDateTime, val text: String) : ThreadItem
}

object CoachThread {

    private const val DAYS_SHOWN = 30L

    /** chat.mjs answers in about a minute; after this the phone says the PC is probably off. */
    val replyExpectedWithin: Duration = Duration.ofMinutes(3)

    /**
     * [uploadedUntilMillis] is when the check-ins last reached GitHub: anything written later is still "sending".
     * Oldest first.
     */
    fun build(checkIns: List<CheckIn>, messages: List<CoachMessage>, uploadedUntilMillis: Long, now: OffsetDateTime): List<ThreadItem> {
        val firstDay = now.toLocalDate().minusDays(DAYS_SHOWN)
        val answeredIds = messages.flatMap { it.covers }.toSet()
        val items = mutableListOf<ThreadItem>()

        for (checkIn in checkIns) {
            if (LocalDate.parse(checkIn.date).isBefore(firstDay)) continue
            val createdAt = OffsetDateTime.parse(checkIn.createdAt)
            eventText(checkIn, checkIns)?.let { text -> items += ThreadItem.Event("event-${checkIn.id}", createdAt, text) }
            userText(checkIn)?.let { text ->
                val status = statusOf(checkIn, createdAt, answeredIds, uploadedUntilMillis, now)
                items += ThreadItem.FromHim(checkIn.id, createdAt, text, status)
            }
        }
        for (message in messages) {
            val createdAt = OffsetDateTime.parse(message.createdAt)
            if (createdAt.toLocalDate().isBefore(firstDay)) continue
            items += ThreadItem.FromCoach(message.id, createdAt, message)
        }
        return items.sortedBy { it.createdAt.toInstant() }
    }

    /** Past this the phone stops checking every few seconds; the 15-minute sync still brings the answer. */
    val watchForReplyFor: Duration = Duration.ofMinutes(5)

    /** True while something the user wrote in the last few minutes is still waiting for an answer that should come soon. */
    fun isWaitingForReply(items: List<ThreadItem>, now: OffsetDateTime): Boolean =
        items.filterIsInstance<ThreadItem.FromHim>().any { item ->
            val isUnanswered = item.status == ReplyStatus.SENDING || item.status == ReplyStatus.WAITING
            isUnanswered && Duration.between(item.createdAt, now) < watchForReplyFor
        }

    private fun statusOf(
        checkIn: CheckIn,
        createdAt: OffsetDateTime,
        answeredIds: Set<String>,
        uploadedUntilMillis: Long,
        now: OffsetDateTime,
    ): ReplyStatus = when {
        !checkIn.askCoach -> ReplyStatus.NO_REPLY_EXPECTED
        checkIn.id in answeredIds -> ReplyStatus.ANSWERED
        createdAt.toInstant().toEpochMilli() > uploadedUntilMillis -> ReplyStatus.SENDING
        Duration.between(createdAt, now) > replyExpectedWithin -> ReplyStatus.PC_SEEMS_OFF
        else -> ReplyStatus.WAITING
    }

    /** What the user said, in the user's own voice. Only for what goes to the coach. */
    private fun userText(checkIn: CheckIn): String? = when {
        !checkIn.askCoach -> null
        checkIn.kind == CHAT_KIND -> checkIn.note
        checkIn.kind == SKIP_KIND -> "I am taking a break today${checkIn.note?.let { ": $it" }.orEmpty()}."
        checkIn.kind == LEARNED_KIND -> "What I understood: ${checkIn.note}"
        checkIn.kind == WORKOUT_KIND -> listOfNotNull(
            checkIn.feeling?.let { key -> "It was ${Feeling.entries.first { it.key == key }.label.lowercase()}." },
            checkIn.note,
        ).joinToString(" ").ifBlank { "I finished the workout." }
        else -> checkIn.note
    }

    /** The facts the phone knows at once, shown in grey, so every action gets an answer even before the coach replies. */
    private fun eventText(checkIn: CheckIn, checkIns: List<CheckIn>): String? = when (checkIn.kind) {
        FOCUS_KIND -> {
            val area = FocusArea.entries.firstOrNull { it.key == checkIn.area }?.label ?: checkIn.note
            val startedDays = Stats.startedDaysInWeek(checkIns.filter { it.date <= checkIn.date }, LocalDate.parse(checkIn.date))
            "${minutesLabel(checkIn.minutes ?: 0)} of focus${area?.let { " · $it" }.orEmpty()} · $startedDays of 7 days with work this week"
        }
        WORKOUT_KIND -> {
            val improvements = GymProgram.improvements(checkIn, checkIns)
            val facts = listOfNotNull(
                checkIn.programDayName,
                checkIn.minutes?.let { "$it min" },
                "${GymProgram.volumeKg(checkIn.sets)} kg lifted",
            ).joinToString(" · ")
            if (improvements.isEmpty()) facts else "$facts\n${improvements.joinToString("\n")}"
        }
        GYM_KIND -> "Gym ticked"
        PLAN_KIND -> "Plan for tonight: ${listOfNotNull(checkIn.note, checkIn.time).joinToString(", ")}"
        SKIP_KIND -> if (checkIn.askCoach) null else "Break today${checkIn.note?.let { ": $it" }.orEmpty()}"
        BLOCKER_KIND -> "Unblocked: ${checkIn.note}"
        else -> null
    }
}
