package dev.pas.app

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.pas.app.ui.ScreenTimeAccessCard
import dev.pas.app.ui.ScreenTimeCard
import dev.pas.app.ui.CoachScreen
import dev.pas.app.ui.GymScreen
import dev.pas.app.ui.NewsScreen
import dev.pas.app.ui.Pas
import dev.pas.app.ui.PasStyle
import dev.pas.app.ui.PasTheme
import dev.pas.app.ui.ThemeScreen
import dev.pas.app.ui.pasBackdrop
import dev.pas.app.ui.SetupScreen
import dev.pas.app.ui.StoryScreen
import dev.pas.app.ui.TodayScreen
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/** Renders every screen to build/screenshots so the design can be reviewed without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1400dp-xxhdpi")
class ScreenshotTest {

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun writeSampleData() {
        val today = LocalDate.now()
        val morning = OffsetDateTime.now().withHour(8).withMinute(30)
        val midday = OffsetDateTime.now().withHour(13).withMinute(30)
        File(application.filesDir, "messages.json").writeText(
            """
            [{"id":"m1","createdAt":"$morning","slot":"morning",
              "body":"Good morning, Alex. Thursday is a good day for the gym, and you are at 1 of 4 this week. Before that, one small thing: open the landing page project and write the headline. The rest can wait.",
              "focus":"Write the landing page headline, 25 minutes",
              "quote":{"text":"A journey of a thousand miles begins with a single step.","author":"Lao Tzu"},
              "news":[{"text":"Northwind releases a faster mid-size model with a larger context window and lower prices.","source":"Northwind, Oct 7"},
                      {"text":"A new paper trains small models to check their own reasoning steps.","source":"Example Research, Oct 6"}]},
             {"id":"m2","createdAt":"$midday","slot":"midday",
              "body":"A small step for today: 25 minutes on the pricing section of the landing page. Just the first draft, not the whole page.",
              "focus":"Write the landing page headline, 25 minutes","news":[]}]
            """.trimIndent(),
        )
        val checkIns = (0..20).flatMap { daysAgo ->
            val day = today.minusDays(daysAgo.toLong())
            val kinds = when (daysAgo % 4) {
                0 -> listOf("learning")
                1 -> listOf("gym", "project")
                2 -> listOf("skip")
                else -> listOf("gym", "learning")
            }
            val focusSession = """{"id":"$day-focus","date":"$day","createdAt":"${day}T11:00:00Z","kind":"focus","minutes":${if (daysAgo % 3 == 0) 25 else 5}}"""
            kinds.map { """{"id":"$day-$it","date":"$day","createdAt":"${day}T10:00:00Z","kind":"$it"}""" } + focusSession
        }
        val workouts = listOf(9L to "A", 6L to "B", 3L to "A").map { (daysAgo, dayId) ->
            val day = today.minusDays(daysAgo)
            val exercises = GymProgram.day(dayId).exercises
            val sets = exercises.joinToString(",") { exercise ->
                val kg = exercise.startKg + (9 - daysAgo) / 3 * 2.5
                (1..3).joinToString(",") { """{"exerciseId":"${exercise.id}","exerciseName":"${exercise.name}","kg":$kg,"reps":${9 + it}}""" }
            }
            """{"id":"$day-workout","date":"$day","createdAt":"${day}T18:00:00Z","kind":"workout","minutes":48,"programDayId":"$dayId","programDayName":"Day $dayId","sets":[$sets],"feeling":"ok","askCoach":true}"""
        }
        File(application.filesDir, "checkins.json").writeText((checkIns + workouts).joinToString(",", "[", "]"))
        CoachStore(application).gymReason = "I want more energy in the afternoon and a back that does not hurt."
        val realBoard = CoachStore.json.decodeFromString<Board>(File("src/test/resources/board.json").readText())
        val sampleBoard = realBoard.copy(
            cards = listOf(
                Card("c1", today.minusDays(1).toString(), "What does a sparse autoencoder do?", "It breaks activations into a large, sparse set of features that people can inspect."),
                Card("c2", today.minusDays(1).toString(), "Why does a coroutine not block its thread?", "It suspends at a suspension point and lets the thread run other work until it can resume."),
            ),
        )
        File(application.filesDir, "board.json").writeText(Json.encodeToString(sampleBoard))
    }

    @Test
    fun today() = capture("today") { TodayScreen(viewModel = PasViewModel(application), now = morningNow(), onOpenStory = {}, onOpenGym = {}, onOpenThemes = {}) }

    @Test
    @Config(qualifiers = "w411dp-h5200dp-xxhdpi")
    fun newsInEveryStyle() {
        for (style in PasStyle.entries) {
            capture("news-${style.key}", style) {
                val viewModel = PasViewModel(application)
                NewsScreen(
                    digests = viewModel.board.digests,
                    messages = viewModel.messages,
                    readStoryIds = setOf(viewModel.board.digests.first().stories.first { it.coreRank == 1 }.id),
                    onOpenStory = {},
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    fun gymInEveryStyle() {
        for (style in PasStyle.entries) {
            capture("gym-${style.key}", style) { GymScreen(viewModel = PasViewModel(application), today = LocalDate.now()) }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    fun todayOnAGymDay() {
        val workoutDay = LocalDate.now().minusDays(3)
        val squats = (1..3).joinToString(",") { """{"exerciseId":"squat","exerciseName":"Barbell squat","kg":20.0,"reps":12}""" }
        val workout = """{"id":"w","date":"$workoutDay","createdAt":"${workoutDay}T18:00:00Z","kind":"workout","programDayId":"B","sets":[$squats]}"""
        File(application.filesDir, "checkins.json").writeText("[$workout]")
        capture("today-gym") { TodayScreen(viewModel = PasViewModel(application), now = morningNow(), onOpenStory = {}, onOpenGym = {}, onOpenThemes = {}) }
    }

    @Test
    fun screenTime() = capture("screen-time") {
        val today = ScreenTimeSpan(
            totalMinutes = 134,
            apps = listOf(AppMinutes("YouTube", 48), AppMinutes("Chrome", 31), AppMinutes("WhatsApp", 12), AppMinutes("Gmail", 4)),
        )
        Column(Modifier.padding(20.dp)) {
            ScreenTimeCard(overview = ScreenTimeOverview(today, yesterdayUntilNowMinutes = 110), weekAverageMinutes = 162)
            Spacer(Modifier.height(16.dp))
            ScreenTimeAccessCard()
        }
    }

    @Test
    fun coachInEveryStyle() {
        for (style in PasStyle.entries) {
            capture("coach-${style.key}", style) { CoachScreen(viewModel = PasViewModel(application)) }
        }
    }

    @Test
    fun setup() = capture("setup") {
        SetupScreen(
            initialRepository = CoachStore.DEFAULT_REPOSITORY,
            isReconnecting = false,
            isConnecting = false,
            connectError = null,
            onConnect = { _, _ -> },
            onCancel = {},
        )
    }

    @Test
    fun todayAfterFocus() {
        CoachStore(application).finishedFocusMinutes = 25
        for (style in PasStyle.entries) {
            capture("today-after-focus-${style.key}", style) { TodayScreen(viewModel = PasViewModel(application), now = morningNow(), onOpenStory = {}, onOpenGym = {}, onOpenThemes = {}) }
        }
    }

    @Test
    fun todayInEveryStyle() {
        for (style in PasStyle.entries) {
            capture("today-${style.key}", style) { TodayScreen(viewModel = PasViewModel(application), now = morningNow(), onOpenStory = {}, onOpenGym = {}, onOpenThemes = {}) }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    fun workoutInProgress() {
        val viewModel = PasViewModel(application)
        viewModel.startWorkout(isShortVersion = false)
        viewModel.logSet(kg = 20.0, reps = 10)
        for (style in PasStyle.entries) {
            capture("gym-workout-${style.key}", style) { GymScreen(viewModel = viewModel, today = LocalDate.now()) }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    fun themes() = capture("themes") {
        ThemeScreen(
            selected = PasStyle.DEFAULT,
            onSelect = {},
            repository = CoachStore.DEFAULT_REPOSITORY,
            onEditConnection = {},
            scrollState = rememberScrollState(),
            onBack = {},
        )
    }

    @Test
    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    fun story() {
        for (style in listOf(PasStyle.DEFAULT, PasStyle.CHALKBOARD)) {
            capture("story-${style.key}", style) {
                val story = PasViewModel(application).board.digests.first().stories.first { it.coreRank == 1 }
                StoryScreen(story = story, onBack = {}, onLearned = {})
            }
        }
    }

    /** The launcher icon as phones mask it: circle and squircle, small and large, plus the themed monochrome version. */
    @Test
    @Config(qualifiers = "w800dp-h400dp-xxhdpi")
    fun launcherIcon() = captureRoboImage("build/screenshots/launcher-icon.png") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color(0xFF8E9AAF))
                .padding(16.dp),
        ) {
            for (iconSize in listOf(48.dp, 192.dp)) {
                AdaptiveIcon(size = iconSize, shape = CircleShape)
                AdaptiveIcon(size = iconSize, shape = RoundedCornerShape(30))
            }
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDCE5F5)),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_monochrome),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color(0xFF2B3A55)),
                    modifier = Modifier.requiredSize(144.dp),
                )
            }
        }
    }

    // Launchers show the middle 72 of the icon's 108 units, so the layers are drawn 1.5 times bigger than the mask.
    @Composable
    private fun AdaptiveIcon(size: Dp, shape: Shape) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(shape),
        ) {
            Box(Modifier.requiredSize(size * 1.5f)) {
                Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.fillMaxSize())
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    fun todayEvening() {
        val store = CoachStore(application)
        store.savePlan(GYM_PLAN, "20:30")
        for (style in PasStyle.entries) {
            capture("today-evening-${style.key}", style) { TodayScreen(viewModel = PasViewModel(application), now = LocalDate.now().atTime(19, 40), onOpenStory = {}, onOpenGym = {}, onOpenThemes = {}) }
        }
    }

    private fun morningNow(): LocalDateTime = LocalDate.now().atTime(10, 0)

    private fun capture(name: String, style: PasStyle = PasStyle.DEFAULT, content: @Composable () -> Unit) {
        captureRoboImage("build/screenshots/$name.png") {
            PasTheme(style = style) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pasBackdrop(Pas.decor.backdrop, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { content() }
                }
            }
        }
    }
}
