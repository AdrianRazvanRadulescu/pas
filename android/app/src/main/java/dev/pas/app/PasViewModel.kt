package dev.pas.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.pas.app.ui.PasStyle
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

class PasViewModel(application: Application) : AndroidViewModel(application) {

    private val store = CoachStore(application)
    private val updater = AppUpdater(application)
    private val screenTimeReader = ScreenTimeReader(application)

    var messages by mutableStateOf(store.messages())
        private set
    var checkIns by mutableStateOf(store.checkIns())
        private set
    var board by mutableStateOf(store.board())
        private set
    var focusEndsAtMillis by mutableStateOf(store.focusEndsAtMillis)
        private set
    var finishedFocusMinutes by mutableStateOf(store.finishedFocusMinutes)
        private set
    var isConfigured by mutableStateOf(store.isConfigured)
        private set
    var isConnecting by mutableStateOf(false)
        private set
    var connectError by mutableStateOf<String?>(null)
        private set
    var availableUpdate by mutableStateOf<GitHubFiles.Release?>(null)
        private set
    var isDownloadingUpdate by mutableStateOf(false)
        private set
    var needsInstallPermission by mutableStateOf(false)
        private set
    var isSyncing by mutableStateOf(false)
        private set
    var syncError by mutableStateOf<String?>(null)
        private set

    var style by mutableStateOf(PasStyle.fromKey(store.styleKey))
        private set

    var readStoryIds by mutableStateOf(store.readStoryIds)
        private set

    var gymReason by mutableStateOf(store.gymReason)
        private set

    var workoutDraft by mutableStateOf(store.workoutDraft())
        private set
    var finishedWorkoutId by mutableStateOf(store.finishedWorkoutId)
        private set
    /** End of the pause between sets; 0 when no pause is running. Lost if the app closes, which is fine for a pause. */
    var restEndsAtMillis by mutableStateOf(0L)
        private set
    var uploadedUntilMillis by mutableStateOf(store.uploadedUntilMillis)
        private set
    var lastSeenMessageId by mutableStateOf(store.lastSeenMessageId)
        private set
    var hasUsageAccess by mutableStateOf(screenTimeReader.hasAccess())
        private set
    /** Null until read, and always without usage access. */
    var screenTime by mutableStateOf<ScreenTimeOverview?>(null)
        private set

    val repository: String get() = store.repository

    /** The blocker the last focus session was for; empty when it was ordinary work. Read once the session is over. */
    val finishedFocusBlockerTitle: String
        get() = if (store.focusArea == BLOCKER_AREA) store.focusBlockerTitle else ""

    val hasUnreadFromCoach: Boolean
        get() = messages.isNotEmpty() && messages.last().id != lastSeenMessageId

    fun thread(now: OffsetDateTime): List<ThreadItem> = CoachThread.build(checkIns, messages, uploadedUntilMillis, now)

    /** Back from the installer settings with permission granted: carry on with the update the user already tapped. */
    fun onResume() {
        closeWorkoutLeftOpenYesterday()
        reload()
        refresh()
        checkForUpdate()
        loadScreenTime()
        if (needsInstallPermission && updater.canInstall()) installUpdate()
    }

    fun selectStyle(selected: PasStyle) {
        style = selected
        store.styleKey = selected.key
        viewModelScope.launch { PasWidget().updateAll(getApplication()) }
    }

    fun refresh() {
        if (!store.isConfigured || isSyncing) return
        isSyncing = true
        viewModelScope.launch {
            syncError = withContext(Dispatchers.IO) {
                try {
                    store.sync()
                    null
                } catch (exception: IOException) {
                    exception.message ?: "Could not sync. Check your connection."
                }
            }
            reload()
            PasWidget().updateAll(getApplication())
            isSyncing = false
        }
    }

    fun sendChat(text: String) {
        store.addChat(text.trim())
        afterMessageToCoach()
    }

    fun saveSkip(reason: String) {
        store.saveSkip(reason)
        afterMessageToCoach()
    }

    fun removeSkip() {
        store.removeSkipToday()
        afterCheckInChange()
    }

    /** [time] is null when the user did not pick one. */
    fun savePlan(what: String, time: String?) {
        store.savePlan(what, time)
        // Both the gym card and the evening plan card can pick Gym, so the reminder follows the plan here.
        if (what == GYM_PLAN && time != null) GymReminderWorker.schedule(getApplication(), time) else GymReminderWorker.cancel(getApplication())
        afterCheckInChange()
    }

    fun saveGymReason(text: String) {
        store.gymReason = text.trim()
        gymReason = store.gymReason
    }

    /** The Coach tab is on screen: its messages count as seen and will not come as notifications. */
    fun markCoachSeen() {
        val latestMessage = messages.lastOrNull() ?: return
        lastSeenMessageId = latestMessage.id
        store.lastSeenMessageId = latestMessage.id
        store.lastNotifiedMessageId = latestMessage.id
        Notifications.cancelReply(getApplication())
    }

    /** Runs while the Coach tab is open and an answer is due; leaving the tab cancels it. */
    suspend fun pollForReply() {
        while (CoachThread.isWaitingForReply(thread(OffsetDateTime.now()), OffsetDateTime.now())) {
            delay(REPLY_POLL_MILLIS)
            withContext(Dispatchers.IO) {
                try {
                    store.downloadMessages()
                } catch (exception: IOException) {
                    // A missed round is fine: the next one comes in a few seconds.
                }
            }
            reload()
        }
    }

    fun startWorkout(isShortVersion: Boolean) {
        val draft = WorkoutDraft(
            programDayId = GymProgram.nextDay(checkIns).id,
            date = LocalDate.now().toString(),
            startedAtMillis = System.currentTimeMillis(),
            isShortVersion = isShortVersion,
        )
        saveDraft(draft)
    }

    fun logSet(kg: Double, reps: Int) {
        val draft = workoutDraft ?: return
        val exercises = GymProgram.exercisesFor(draft)
        val exercise = exercises[draft.exerciseIndex]
        val updated = draft.copy(sets = draft.sets + LoggedSet(exercise.id, exercise.name, kg, reps))
        saveDraft(updated)

        val setsDone = updated.sets.count { it.exerciseId == exercise.id }
        val isLastExercise = draft.exerciseIndex == exercises.lastIndex
        if (setsDone >= GymProgram.SETS && isLastExercise) return
        val nextName = if (setsDone < GymProgram.SETS) exercise.name else exercises[draft.exerciseIndex + 1].name
        restEndsAtMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(exercise.restSeconds.toLong())
        Notifications.showRest(getApplication(), nextName, restEndsAtMillis)
    }

    fun removeLastSet() {
        val draft = workoutDraft ?: return
        saveDraft(draft.copy(sets = draft.sets.dropLast(1)))
        skipRest()
    }

    fun moveToExercise(index: Int) {
        val draft = workoutDraft ?: return
        saveDraft(draft.copy(exerciseIndex = index))
    }

    fun skipRest() {
        restEndsAtMillis = 0
        Notifications.cancelRest(getApplication())
    }

    /** Saved at once, without a question for the coach; the "How was it?" screen that follows may add one. */
    fun finishWorkout() {
        val draft = workoutDraft ?: return
        skipRest()
        store.deleteWorkoutDraft()
        workoutDraft = null
        if (draft.sets.isEmpty()) return
        val minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - draft.startedAtMillis).toInt().coerceAtLeast(1)
        val workout = store.addWorkout(draft, minutes)
        setFinishedWorkout(workout.id)
        afterCheckInChange()
    }

    fun sendWorkoutFeeling(feeling: Feeling, note: String) {
        store.sendWorkoutFeeling(finishedWorkoutId, feeling, note.trim())
        setFinishedWorkout("")
        afterMessageToCoach()
    }

    fun dismissFinishedWorkout() = setFinishedWorkout("")

    fun markStoryRead(storyId: String) {
        readStoryIds = readStoryIds + storyId
        store.readStoryIds = readStoryIds
    }

    fun findStory(storyId: String): Story? =
        board.digests.flatMap { it.stories }.find { it.id == storyId }

    fun addLearned(text: String) {
        store.addLearned(text.trim())
        afterMessageToCoach()
    }

    fun markBlockerDone(title: String) {
        store.addBlockerDone(title)
        afterCheckInChange()
    }

    fun reviewCard(card: Card, grade: CardGrade) {
        store.addCardReview(card.id, grade)
        afterCheckInChange()
    }

    /** [area] is a FocusArea key or BLOCKER_AREA; [blockerTitle] is empty unless it is a blocker. */
    fun startFocus(minutes: Int, area: String, blockerTitle: String) {
        store.focusArea = area
        store.focusBlockerTitle = blockerTitle
        FocusSession.start(getApplication(), minutes)
        reload()
        viewModelScope.launch { PasWidget().updateAll(getApplication()) }
    }

    fun stopFocus() {
        FocusSession.stop(getApplication())
        reload()
        viewModelScope.launch { PasWidget().updateAll(getApplication()) }
    }

    fun dismissFinishedFocus() {
        store.finishedFocusMinutes = 0
        finishedFocusMinutes = 0
    }

    /** Reads messages.json once with the new token, so a wrong token is caught here and not saved. */
    fun connect(token: String, repository: String, onConnected: () -> Unit) {
        if (isConnecting) return
        isConnecting = true
        connectError = null
        viewModelScope.launch {
            connectError = withContext(Dispatchers.IO) {
                try {
                    GitHubFiles(token.trim(), repository.trim()).read("messages.json")
                    null
                } catch (exception: IOException) {
                    exception.message ?: "Could not reach GitHub. Check your connection."
                }
            }
            isConnecting = false
            if (connectError == null) {
                store.githubToken = token.trim()
                store.repository = repository.trim()
                isConfigured = true
                onConnected()
                refresh()
                checkForUpdate()
            }
        }
    }

    /** Kept apart from sync(): a missing or broken release must never stop the messages from arriving. */
    fun checkForUpdate() {
        if (!store.isConfigured) return
        viewModelScope.launch {
            availableUpdate = withContext(Dispatchers.IO) {
                try {
                    updater.findNewerRelease(GitHubFiles(store.githubToken, store.repository))
                } catch (exception: IOException) {
                    null
                }
            }
        }
    }

    fun installUpdate() {
        val release = availableUpdate ?: return
        if (!updater.canInstall()) {
            needsInstallPermission = true
            updater.openInstallPermissionSettings()
            return
        }
        needsInstallPermission = false
        if (isDownloadingUpdate) return
        isDownloadingUpdate = true
        viewModelScope.launch {
            val isDownloaded = withContext(Dispatchers.IO) {
                try {
                    updater.download(GitHubFiles(store.githubToken, store.repository), release)
                    true
                } catch (exception: IOException) {
                    false
                }
            }
            isDownloadingUpdate = false
            if (isDownloaded) updater.openInstaller()
            else syncError = "Could not download the update. Try again."
        }
    }

    fun disconnect() {
        store.githubToken = ""
        isConfigured = false
    }

    /** Something the user wrote expects an answer in a minute or two: upload it now and watch for the reply. */
    private fun afterMessageToCoach() {
        afterCheckInChange()
        ReplyWatchWorker.start(getApplication())
    }

    private fun saveDraft(draft: WorkoutDraft) {
        store.saveWorkoutDraft(draft)
        workoutDraft = draft
    }

    private fun setFinishedWorkout(workoutId: String) {
        store.finishedWorkoutId = workoutId
        finishedWorkoutId = workoutId
    }

    /** A workout still open from an earlier day is closed as it was: kept if it has sets, dropped if not. */
    private fun closeWorkoutLeftOpenYesterday() {
        val draft = store.workoutDraft() ?: return
        if (draft.date == LocalDate.now().toString()) return
        store.deleteWorkoutDraft()
        if (draft.sets.isNotEmpty()) store.addWorkout(draft, minutes = null)
        setFinishedWorkout("")
        skipRest()
    }

    private fun afterCheckInChange() {
        reload()
        viewModelScope.launch { PasWidget().updateAll(getApplication()) }
        // If a sync is already running it may have read the files before this change; the worker uploads it soon after.
        if (isSyncing) SyncWorker.syncSoon(getApplication()) else refresh()
    }

    private fun loadScreenTime() {
        hasUsageAccess = screenTimeReader.hasAccess()
        if (!hasUsageAccess) return
        ScreenTimeWorker.runNow(getApplication())
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val today = now.toLocalDate()
            screenTime = withContext(Dispatchers.IO) {
                ScreenTimeOverview(
                    today = screenTimeReader.span(today.atStartOfDay(), now),
                    yesterdayUntilNowMinutes = screenTimeReader.span(today.minusDays(1).atStartOfDay(), now.minusDays(1)).totalMinutes,
                )
            }
        }
    }

    private fun reload() {
        messages = store.messages()
        checkIns = store.checkIns()
        board = store.board()
        focusEndsAtMillis = store.focusEndsAtMillis
        finishedFocusMinutes = store.finishedFocusMinutes
        workoutDraft = store.workoutDraft()
        uploadedUntilMillis = store.uploadedUntilMillis
    }

    private companion object {
        const val REPLY_POLL_MILLIS = 5_000L
    }
}
