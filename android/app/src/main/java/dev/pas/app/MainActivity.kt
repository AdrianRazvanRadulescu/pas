package dev.pas.app

import android.Manifest
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.res.painterResource
import android.content.Intent
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
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
import java.time.LocalDate
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {

    /** Set by a tap on a coach reply notification; read once by the navigation, then cleared. */
    private var opensCoach by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(PasStyle.fromKey(CoachStore(this).styleKey))
        Notifications.createChannels(this)
        SyncWorker.schedulePeriodic(this)
        ScreenTimeWorker.schedulePeriodic(this)
        // Only on a fresh start: after a rotation the same intent would pull the user back to Coach.
        if (savedInstanceState == null) opensCoach = intent.getBooleanExtra(EXTRA_OPEN_COACH, false)
        setContent {
            val viewModel: PasViewModel = viewModel()
            LaunchedEffect(viewModel.style) { enableEdgeToEdge(viewModel.style) }
            PasAppEffects(viewModel)
            val navigation = rememberSaveable(saver = PasNavigation.Saver) { PasNavigation(Tab.TODAY, null, false, false) }
            LaunchedEffect(opensCoach) {
                if (opensCoach) {
                    navigation.openStoryId = null
                    navigation.isShowingThemes = false
                    navigation.tab = Tab.COACH
                    opensCoach = false
                }
            }
            val themeScroll = rememberScrollState()
            Crossfade(targetState = viewModel.style, animationSpec = tween(300), label = "theme") { style ->
                PasTheme(style = style) {
                    PasApp(viewModel, navigation, themeScroll)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        opensCoach = intent.getBooleanExtra(EXTRA_OPEN_COACH, false)
    }

    companion object {
        const val EXTRA_OPEN_COACH = "openCoach"
    }
}

/** System bar icons follow the theme, not the phone's dark mode, because every theme is fixed light or fixed dark. */
private fun ComponentActivity.enableEdgeToEdge(style: PasStyle) {
    val barStyle = if (style.isDark) {
        SystemBarStyle.dark(Color.Transparent.toArgb())
    } else {
        SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
    }
    enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
}

enum class Tab(val label: String, val icon: Int) {
    TODAY("Today", R.drawable.ic_tab_today),
    GYM("Gym", R.drawable.ic_tab_gym),
    COACH("Coach", R.drawable.ic_tab_coach),
    NEWS("News", R.drawable.ic_tab_news),
}

/** Where the user is. Lives above the theme crossfade, so switching themes never loses the place. */
class PasNavigation(tab: Tab, openStoryId: String?, isEditingConnection: Boolean, isShowingThemes: Boolean) {
    var tab by mutableStateOf(tab)
    var openStoryId by mutableStateOf(openStoryId)
    var isEditingConnection by mutableStateOf(isEditingConnection)
    var isShowingThemes by mutableStateOf(isShowingThemes)

    companion object {
        val Saver = listSaver<PasNavigation, Any?>(
            save = { listOf(it.tab.name, it.openStoryId, it.isEditingConnection, it.isShowingThemes) },
            restore = { PasNavigation(Tab.valueOf(it[0] as String), it[1] as String?, it[2] as Boolean, it[3] as Boolean) },
        )
    }
}

private sealed interface Screen {
    data class OnTab(val tab: Tab) : Screen
    data class Reading(val story: Story) : Screen
    data object Themes : Screen
}

/** Effects that must run once, whatever theme is fading in or out. */
@Composable
private fun PasAppEffects(viewModel: PasViewModel) {
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
}

@Composable
private fun PasApp(viewModel: PasViewModel, navigation: PasNavigation, themeScroll: ScrollState) {
    BackHandler(enabled = viewModel.isConfigured && navigation.isEditingConnection) { navigation.isEditingConnection = false }
    if (!viewModel.isConfigured || navigation.isEditingConnection) {
        SetupScreen(
            initialRepository = viewModel.repository,
            isReconnecting = viewModel.isConfigured,
            isConnecting = viewModel.isConnecting,
            connectError = viewModel.connectError,
            onConnect = { token, repository ->
                viewModel.connect(token, repository, onConnected = { navigation.isEditingConnection = false })
            },
            onCancel = { navigation.isEditingConnection = false },
        )
        return
    }

    // A saved story can drop out of the board when its digest gets old; then the tab shows again.
    val openStory = navigation.openStoryId?.let { viewModel.findStory(it) }
    val screen = when {
        openStory != null -> Screen.Reading(openStory)
        navigation.isShowingThemes -> Screen.Themes
        else -> Screen.OnTab(navigation.tab)
    }
    BackHandler(enabled = screen is Screen.Reading) { navigation.openStoryId = null }
    BackHandler(enabled = screen is Screen.Themes) { navigation.isShowingThemes = false }
    BackHandler(enabled = screen is Screen.OnTab && navigation.tab != Tab.TODAY) { navigation.tab = Tab.TODAY }

    val showStory = { storyId: String ->
        viewModel.markStoryRead(storyId)
        navigation.openStoryId = storyId
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.pasBackdrop(
            backdrop = Pas.decor.backdrop,
            background = MaterialTheme.colorScheme.background,
            pattern = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        bottomBar = {
            if (screen is Screen.OnTab) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    for (tab in Tab.entries) {
                        NavigationBarItem(
                            selected = navigation.tab == tab,
                            onClick = { navigation.tab = tab },
                            icon = {
                                BadgedBox(badge = { if (tab == Tab.COACH && viewModel.hasUnreadFromCoach) Badge() }) {
                                    Icon(painterResource(tab.icon), contentDescription = null)
                                }
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val direction = if (screenOrder(targetState) > screenOrder(initialState)) 1 else -1
                (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { width -> direction * width / 12 })
                    .togetherWith(fadeOut(tween(150)))
            },
            label = "screen",
            modifier = Modifier.padding(innerPadding),
        ) { shownScreen ->
            when (shownScreen) {
                is Screen.Reading -> StoryScreen(
                    story = shownScreen.story,
                    onBack = { navigation.openStoryId = null },
                    onLearned = { sentence -> viewModel.addLearned("${shownScreen.story.title}: $sentence") },
                )
                Screen.Themes -> ThemeScreen(
                    selected = viewModel.style,
                    onSelect = viewModel::selectStyle,
                    repository = viewModel.repository,
                    onEditConnection = { navigation.isEditingConnection = true },
                    scrollState = themeScroll,
                    onBack = { navigation.isShowingThemes = false },
                )
                is Screen.OnTab -> when (shownScreen.tab) {
                    Tab.TODAY -> TodayScreen(
                        viewModel = viewModel,
                        now = LocalDateTime.now(),
                        onOpenStory = showStory,
                        onOpenGym = { navigation.tab = Tab.GYM },
                        onOpenThemes = { navigation.isShowingThemes = true },
                    )
                    Tab.GYM -> GymScreen(viewModel = viewModel, today = LocalDate.now())
                    Tab.COACH -> CoachScreen(viewModel = viewModel)
                    Tab.NEWS -> NewsScreen(
                        digests = viewModel.board.digests,
                        messages = viewModel.messages,
                        readStoryIds = viewModel.readStoryIds,
                        onOpenStory = showStory,
                    )
                }
            }
        }
    }
}

// A story opens "to the right" of every tab; tabs slide in the direction of the bar.
private fun screenOrder(screen: Screen): Int = when (screen) {
    is Screen.OnTab -> screen.tab.ordinal
    is Screen.Reading, Screen.Themes -> Tab.entries.size
}
