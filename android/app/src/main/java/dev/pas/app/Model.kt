package dev.pas.app

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

@Serializable
data class CoachMessage(
    val id: String,
    val createdAt: String,
    val slot: String,
    val body: String,
    val focus: String,
    val quote: Quote? = null,
    val news: List<NewsItem> = emptyList(),
    /** False when the coach judged that a notification would not help; the message still shows in the app. */
    val notify: Boolean = true,
    /** Only on replies (REPLY_SLOT): the ids of the user's check-ins this message answers. */
    val covers: List<String> = emptyList(),
)

/** The slot of a message written by chat.mjs in answer to something the user sent. */
const val REPLY_SLOT = "reply"

/** The coach's real messages of the day (morning, midday), without the chat replies. */
fun dailyMessages(messages: List<CoachMessage>): List<CoachMessage> = messages.filter { it.slot != REPLY_SLOT }

/** The focus of today's newest daily message; null when the coach has not written today. */
fun todayFocus(messages: List<CoachMessage>, today: LocalDate): String? =
    dailyMessages(messages).lastOrNull { localDateOf(it) == today && it.focus.isNotBlank() }?.focus

fun localDateOf(message: CoachMessage): LocalDate =
    OffsetDateTime.parse(message.createdAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()

@Serializable
data class Quote(val text: String, val author: String? = null)

@Serializable
data class NewsItem(val text: String, val source: String)

@Serializable
data class CheckIn(
    val id: String,
    val date: String,
    val createdAt: String,
    val kind: String,
    val note: String? = null,
    val minutes: Int? = null,
    /** Evening plan: "20:30", when the user picked a time. */
    val time: String? = null,
    /** Old evening plans: where the work happens. */
    val place: String? = null,
    /** Old "close the day" check-ins: energy from 1 to 5. */
    val energy: Int? = null,
    /** Card review: which card and how well it was remembered (see CardGrade). */
    val cardId: String? = null,
    val grade: String? = null,
    /** True when the coach on the PC should answer this in the Coach tab (chat.mjs). */
    val askCoach: Boolean = false,
    /** Focus session: what it was for, a FocusArea key or BLOCKER_AREA. */
    val area: String? = null,
    /** Workout: which day of GymProgram, the sets as done and how it felt (a Feeling key). */
    val programDayId: String? = null,
    val programDayName: String? = null,
    val sets: List<LoggedSet> = emptyList(),
    val feeling: String? = null,
)

@Serializable
data class LoggedSet(val exerciseId: String, val exerciseName: String, val kg: Double, val reps: Int)

/** Old one-tap gym tick, from before the Gym tab; still counted as a gym day. */
const val GYM_KIND = "gym"

/** A finished workout from the Gym tab, with every set. */
const val WORKOUT_KIND = "workout"

/** A chosen pause for today, with the reason in the note. */
const val SKIP_KIND = "skip"

/** A message the user wrote to the coach in the Coach tab. */
const val CHAT_KIND = "chat"

/** A finished focus session, with how many minutes it lasted. */
const val FOCUS_KIND = "focus"

/** Tonight's plan: what (in the note) and, if the user picked one, the time. */
const val PLAN_KIND = "plan"

/** What the user understood after a focus session; the PC turns it into cards. */
const val LEARNED_KIND = "learned"

/** A business blocker from profile.md that the user has done (title in the note). */
const val BLOCKER_KIND = "blocker"

/** The user's time on the phone on the day in [CheckIn.date]: total in minutes, top apps in the note. One per day, replaced as the day goes on. */
const val SCREEN_TIME_KIND = "screen_time"

/** One card review. */
const val CARD_KIND = "card"

/** The kinds of the old one-tap ticks, still in the user's history. */
val oldTickKinds = setOf(GYM_KIND, "learning", "project")

enum class FocusArea(val key: String, val label: String) {
    LEARNING("learning", "Learning"),
    PROJECT("project", "Project"),
}

/** A focus session spent on one of the business blockers; its title is kept with the running session. */
const val BLOCKER_AREA = "blocker"

enum class Feeling(val key: String, val label: String) {
    EASY("easy", "Easy"),
    OK("ok", "Ok"),
    HARD("hard", "Hard"),
}

enum class CardGrade(val key: String, val label: String) {
    AGAIN("again", "Didn't know"),
    HARD("hard", "Hard"),
    EASY("easy", "Easy"),
}

val skipReasons = listOf("tired", "sick", "working late", "no motivation", "chosen pause")

/** The evening plan that means the gym; it gets a reminder at its time. */
const val GYM_PLAN = "Gym"

val eveningPlans = listOf(GYM_PLAN, "Learning", "Project", "Go out", "Rest")

/** Written by the PC: countdowns and blockers from profile.md, the cards and the weekly digests. */
@Serializable
data class Board(
    val upcoming: List<UpcomingEvent> = emptyList(),
    /** Small manual tasks the business waits on, from the "Blockers" section of profile.md. */
    val blockers: List<String> = emptyList(),
    val cards: List<Card> = emptyList(),
    val digests: List<Digest> = emptyList(),
)

/** A retrieval card the PC made from one of the user's "learned" notes. */
@Serializable
data class Card(val id: String, val createdOn: String, val question: String, val answer: String)

@Serializable
data class UpcomingEvent(val date: String, val title: String)

@Serializable
data class Digest(
    val id: String,
    val title: String,
    val summary: List<DigestItem>,
    val reading: List<DigestItem>,
    val watch: List<DigestItem>,
    val stories: List<Story> = emptyList(),
)

/** One news story explained in depth: "The question", "The method", "Limits"... coreRank 1-3 marks the ones worth understanding this week. */
@Serializable
data class Story(
    val id: String,
    val section: String,
    val topic: String? = null,
    val title: String,
    val intro: String? = null,
    val url: String? = null,
    val coreRank: Int? = null,
    val readMinutes: Int = 1,
    val parts: List<StoryPart> = emptyList(),
)

@Serializable
data class StoryPart(val label: String? = null, val text: String)

@Serializable
data class DigestItem(val text: String, val url: String? = null)
