package dev.pas.app

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.pas.app.ui.PasStyle
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Local copy of the coach data plus the sync with GitHub.
 * The PC owns messages.json and board.json; this phone owns checkins.json, so after the first download
 * the local check-ins are the truth and simply overwrite the remote file when they differ.
 */
class CoachStore(context: Context) {

    private val preferences = context.getSharedPreferences("pas", Context.MODE_PRIVATE)
    private val messagesFile = File(context.filesDir, "messages.json")
    private val checkInsFile = File(context.filesDir, "checkins.json")
    private val boardFile = File(context.filesDir, "board.json")
    private val workoutDraftFile = File(context.filesDir, "workout-draft.json")

    var githubToken: String
        get() = preferences.getString("githubToken", "")!!
        set(value) = preferences.edit().putString("githubToken", value).apply()

    var repository: String
        get() = preferences.getString("repository", DEFAULT_REPOSITORY)!!
        set(value) = preferences.edit().putString("repository", value).apply()

    var lastNotifiedMessageId: String
        get() = preferences.getString("lastNotifiedMessageId", "")!!
        set(value) = preferences.edit().putString("lastNotifiedMessageId", value).apply()

    /** The release the "new version" notification was already shown for, so it comes once per version. */
    var lastNotifiedVersionCode: Long
        get() = preferences.getLong("lastNotifiedVersionCode", 0)
        set(value) = preferences.edit().putLong("lastNotifiedVersionCode", value).apply()

    var focusStartedAtMillis: Long
        get() = preferences.getLong("focusStartedAtMillis", 0)
        set(value) = preferences.edit().putLong("focusStartedAtMillis", value).apply()

    /** Epoch millis when the running focus session ends; 0 when none is running. */
    var focusEndsAtMillis: Long
        get() = preferences.getLong("focusEndsAtMillis", 0)
        set(value) = preferences.edit().putLong("focusEndsAtMillis", value).apply()

    /** What the running focus session is for: a FocusArea key or BLOCKER_AREA. */
    var focusArea: String
        get() = preferences.getString("focusArea", FocusArea.LEARNING.key)!!
        set(value) = preferences.edit().putString("focusArea", value).apply()

    /** The blocker title when [focusArea] is BLOCKER_AREA; empty otherwise. */
    var focusBlockerTitle: String
        get() = preferences.getString("focusBlockerTitle", "")!!
        set(value) = preferences.edit().putString("focusBlockerTitle", value).apply()

    /** Minutes of the session that just ended, until the user sees the "you showed up" card; 0 when there is none. */
    var finishedFocusMinutes: Int
        get() = preferences.getInt("finishedFocusMinutes", 0)
        set(value) = preferences.edit().putInt("finishedFocusMinutes", value).apply()

    var styleKey: String
        get() = preferences.getString("styleKey", PasStyle.DEFAULT.key)!!
        set(value) = preferences.edit().putString("styleKey", value).apply()

    /** Stays on the phone: which stories were opened, so News can say "1 of 3 read". */
    var readStoryIds: Set<String>
        get() = preferences.getStringSet("readStoryIds", emptySet())!!
        set(value) = preferences.edit().putStringSet("readStoryIds", value).apply()

    /** The workout whose "How was it?" screen is still open; empty when there is none. */
    var finishedWorkoutId: String
        get() = preferences.getString("finishedWorkoutId", "")!!
        set(value) = preferences.edit().putString("finishedWorkoutId", value).apply()

    /** The user's own reason for the gym, in their words; shown on the gym card. Stays on the phone. */
    var gymReason: String
        get() = preferences.getString("gymReason", "")!!
        set(value) = preferences.edit().putString("gymReason", value).apply()

    /** The day the screen-time values below belong to; the first run of a new day resets them. */
    var screenTimeDay: String
        get() = preferences.getString("screenTimeDay", "")!!
        set(value) = preferences.edit().putString("screenTimeDay", value).apply()

    /** How many 30-minute steps of screen time today already got a heads-up. */
    var screenTimeNudgedSteps: Int
        get() = preferences.getInt("screenTimeNudgedSteps", 0)
        set(value) = preferences.edit().putInt("screenTimeNudgedSteps", value).apply()

    /** The user swiped today's time out of the shade; it comes back tomorrow. */
    var isScreenTimeShadeDismissed: Boolean
        get() = preferences.getBoolean("isScreenTimeShadeDismissed", false)
        set(value) = preferences.edit().putBoolean("isScreenTimeShadeDismissed", value).apply()

    /** The newest message the user has seen in the Coach tab, for the dot on the tab. */
    var lastSeenMessageId: String
        get() = preferences.getString("lastSeenMessageId", "")!!
        set(value) = preferences.edit().putString("lastSeenMessageId", value).apply()

    /** When the check-ins last reached GitHub: anything written after it still shows as "sending". */
    var uploadedUntilMillis: Long
        get() = preferences.getLong("uploadedUntilMillis", 0)
        set(value) = preferences.edit().putLong("uploadedUntilMillis", value).apply()

    private var hasDownloadedCheckIns: Boolean
        get() = preferences.getBoolean("hasDownloadedCheckIns", false)
        set(value) = preferences.edit().putBoolean("hasDownloadedCheckIns", value).apply()

    val isConfigured: Boolean get() = githubToken.isNotBlank()

    fun messages(): List<CoachMessage> =
        if (messagesFile.exists()) json.decodeFromString(messagesFile.readText()) else emptyList()

    fun checkIns(): List<CheckIn> =
        if (checkInsFile.exists()) json.decodeFromString(checkInsFile.readText()) else emptyList()

    fun board(): Board =
        if (boardFile.exists()) json.decodeFromString(boardFile.readText()) else Board()

    fun workoutDraft(): WorkoutDraft? =
        if (workoutDraftFile.exists()) json.decodeFromString(workoutDraftFile.readText()) else null

    /** Every logged set is written at once, so a closed app or a dead battery never loses the workout. */
    fun saveWorkoutDraft(draft: WorkoutDraft) = workoutDraftFile.writeText(json.encodeToString(draft))

    fun deleteWorkoutDraft() {
        workoutDraftFile.delete()
    }

    fun addChat(text: String) = synchronized(lock) {
        saveCheckIns(checkIns() + newCheckIn(CHAT_KIND).copy(note = text, askCoach = true))
    }

    fun addFocusMinutes(minutes: Int, area: String, blockerTitle: String) = synchronized(lock) {
        saveCheckIns(checkIns() + newCheckIn(FOCUS_KIND).copy(minutes = minutes, area = area, note = blockerTitle.ifBlank { null }))
    }

    fun saveSkip(reason: String) = replaceToday(listOf(SKIP_KIND), newCheckIn(SKIP_KIND).copy(note = reason, askCoach = true))

    fun removeSkipToday() = synchronized(lock) {
        val today = LocalDate.now().toString()
        saveCheckIns(checkIns().filterNot { it.date == today && it.kind == SKIP_KIND })
    }

    /** [time] is null when the user did not pick one. */
    fun savePlan(what: String, time: String?) = replaceToday(listOf(PLAN_KIND), newCheckIn(PLAN_KIND).copy(note = what, time = time))

    /** [day] is yesterday on the first run of a new day, when its full total is known. */
    fun saveScreenTime(day: LocalDate, minutes: Int, topApps: String) = synchronized(lock) {
        val others = checkIns().filterNot { it.date == day.toString() && it.kind == SCREEN_TIME_KIND }
        saveCheckIns(others + newCheckIn(SCREEN_TIME_KIND).copy(date = day.toString(), minutes = minutes, note = topApps))
    }

    fun addLearned(text: String) = synchronized(lock) {
        saveCheckIns(checkIns() + newCheckIn(LEARNED_KIND).copy(note = text, askCoach = true))
    }

    fun addBlockerDone(title: String) = synchronized(lock) {
        saveCheckIns(checkIns() + newCheckIn(BLOCKER_KIND).copy(note = title))
    }

    fun addCardReview(cardId: String, grade: CardGrade) = synchronized(lock) {
        saveCheckIns(checkIns() + newCheckIn(CARD_KIND).copy(cardId = cardId, grade = grade.key))
    }

    /**
     * Saved without a question for the coach; [sendWorkoutFeeling] adds one if the user says how it was.
     * [minutes] is null when the workout was left open and closed the next day, so its length is unknown.
     */
    fun addWorkout(draft: WorkoutDraft, minutes: Int?): CheckIn = synchronized(lock) {
        val workout = newCheckIn(WORKOUT_KIND).copy(
            date = draft.date,
            minutes = minutes,
            programDayId = draft.programDayId,
            programDayName = GymProgram.day(draft.programDayId).name,
            sets = draft.sets,
        )
        saveCheckIns(checkIns() + workout)
        workout
    }

    fun sendWorkoutFeeling(workoutId: String, feeling: Feeling, note: String) = synchronized(lock) {
        val updated = checkIns().map { checkIn ->
            // Stamped now: the reply watch and chat.mjs go by when the question was asked, not when the workout ended.
            if (checkIn.id == workoutId) {
                checkIn.copy(feeling = feeling.key, note = note.ifBlank { null }, askCoach = true, createdAt = OffsetDateTime.now().toString())
            } else {
                checkIn
            }
        }
        saveCheckIns(updated)
    }

    /** Only messages.json: used every few seconds while the user waits for a reply, so it stays one small request. */
    fun downloadMessages() {
        val remoteMessages = GitHubFiles(githubToken, repository).read(MESSAGES_PATH)
        synchronized(lock) { messagesFile.writeText(remoteMessages.text) }
    }

    /** Network call: run it off the main thread. */
    fun sync() {
        val github = GitHubFiles(githubToken, repository)
        val remoteMessages = github.read(MESSAGES_PATH)
        val remoteBoard = github.read(BOARD_PATH)
        val remoteCheckInsFile = github.read(CHECK_INS_PATH)
        val remoteCheckIns: List<CheckIn> = json.decodeFromString(remoteCheckInsFile.text)

        val snapshotMillis = System.currentTimeMillis()
        val localCheckIns = synchronized(lock) {
            messagesFile.writeText(remoteMessages.text)
            boardFile.writeText(remoteBoard.text)
            if (!hasDownloadedCheckIns) {
                saveCheckIns((remoteCheckIns + checkIns()).distinctBy { it.id }.sortedBy { it.createdAt })
                hasDownloadedCheckIns = true
            }
            saveCheckIns(withoutOldChat(checkIns()))
            checkIns()
        }
        if (localCheckIns != remoteCheckIns) {
            github.write(CHECK_INS_PATH, prettyJson.encodeToString(localCheckIns) + "\n", remoteCheckInsFile.sha, "Check-ins from phone")
        }
        uploadedUntilMillis = snapshotMillis
    }

    // GitHub serves files inline only up to 1 MB; old chat lines are the part that grows without bound.
    private fun withoutOldChat(checkIns: List<CheckIn>): List<CheckIn> {
        val oldestKept = LocalDate.now().minusDays(CHAT_DAYS_KEPT).toString()
        return checkIns.filterNot { it.kind == CHAT_KIND && it.date < oldestKept }
    }

    /** One answer per day for these kinds: the new one takes the place of whatever was saved today. */
    private fun replaceToday(kinds: List<String>, replacement: CheckIn) = synchronized(lock) {
        val today = LocalDate.now().toString()
        val others = checkIns().filterNot { it.date == today && it.kind in kinds }
        saveCheckIns(others + replacement)
    }

    private fun saveCheckIns(checkIns: List<CheckIn>) {
        checkInsFile.writeText(json.encodeToString(checkIns))
    }

    private fun newCheckIn(kind: String) = CheckIn(
        id = UUID.randomUUID().toString(),
        date = LocalDate.now().toString(),
        createdAt = OffsetDateTime.now().toString(),
        kind = kind,
    )

    companion object {
        // The one place the data repository is configured: replace it with your own private repository "owner/name".
        const val DEFAULT_REPOSITORY = "your-github-user/pas-data"
        private const val MESSAGES_PATH = "messages.json"
        private const val CHECK_INS_PATH = "checkins.json"
        private const val BOARD_PATH = "board.json"
        private const val CHAT_DAYS_KEPT = 90L

        // The app, the background workers and the notification buttons can all touch the files at once.
        private val lock = Any()

        val json = Json { ignoreUnknownKeys = true }
        private val prettyJson = Json { prettyPrint = true }
    }
}
