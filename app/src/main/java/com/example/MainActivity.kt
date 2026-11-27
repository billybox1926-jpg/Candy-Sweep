package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.audio.CandySoundManager
import com.example.auth.authStateFlow
import com.example.auth.signOutGoogle
import com.example.data.CandyRepository
import com.example.data.UiState
import com.example.ui.auth.AuthScreen
import com.example.ui.game.GameScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.scores.HighScoresScreen
import com.example.ui.theme.CandySweepTheme
import com.example.viewmodel.CandySweepViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CandySweepTheme {
                AppRoot()
            }
        }
    }
}

private enum class CandyTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    PLAY(
        labelRes = R.string.nav_play,
        selectedIcon = Icons.Rounded.SportsEsports,
        unselectedIcon = Icons.Outlined.SportsEsports,
        tag = "nav_play_tab"
    ),
    SCORES(
        labelRes = R.string.nav_scores,
        selectedIcon = Icons.Rounded.EmojiEvents,
        unselectedIcon = Icons.Outlined.EmojiEvents,
        tag = "nav_scores_tab"
    ),
    PROFILE(
        labelRes = R.string.nav_profile,
        selectedIcon = Icons.Rounded.Person,
        unselectedIcon = Icons.Outlined.Person,
        tag = "nav_profile_tab"
    )
}

@Composable
fun AppRoot() {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            CandySweepMainShell(user = user)
        }
    }
}

@Composable
private fun CandySweepMainShell(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: CandySweepViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CandySweepViewModel(
                    repository = CandyRepository(db),
                    currentUserId = user.uid,
                    initialDisplayName = user.displayName ?: "Sweet Sweeper"
                )
            }
        }
    )

    val soundManager = remember(context) { CandySoundManager(context) }
    DisposableEffect(soundManager) {
        onDispose { soundManager.release() }
    }
    val soundEnabled by soundManager.soundEnabled.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, soundManager) {
        viewModel.soundEvents.collect { event ->
            soundManager.play(event)
        }
    }

    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val scoresState by viewModel.scoresState.collectAsStateWithLifecycle()
    val profileFeedback by viewModel.profileFeedback.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(CandyTab.PLAY) }

    // Return to Play tab on system Back when viewing secondary tabs
    BackHandler(enabled = currentTab != CandyTab.PLAY) {
        currentTab = CandyTab.PLAY
    }

    val currentProfile = (profileState as? UiState.Success)?.data
    val personalBest = currentProfile?.bestScore ?: 0L
    val hapticsEnabled = currentProfile?.hapticsEnabled ?: true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        if (useRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                    CandyTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    TabContent(
                        currentTab = currentTab,
                        user = user,
                        gameState = gameState,
                        profileState = profileState,
                        scoresState = scoresState,
                        profileFeedback = profileFeedback,
                        personalBest = personalBest,
                        hapticsEnabled = hapticsEnabled,
                        soundEnabled = soundEnabled,
                        onToggleSound = soundManager::setSoundEnabled,
                        viewModel = viewModel,
                        onSignOut = {
                            signOutGoogle(credentialManager = credentialManager, scope = scope)
                        }
                    )
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        CandyTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    TabContent(
                        currentTab = currentTab,
                        user = user,
                        gameState = gameState,
                        profileState = profileState,
                        scoresState = scoresState,
                        profileFeedback = profileFeedback,
                        personalBest = personalBest,
                        hapticsEnabled = hapticsEnabled,
                        soundEnabled = soundEnabled,
                        onToggleSound = soundManager::setSoundEnabled,
                        viewModel = viewModel,
                        onSignOut = {
                            signOutGoogle(credentialManager = credentialManager, scope = scope)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    currentTab: CandyTab,
    user: FirebaseUser,
    gameState: com.example.viewmodel.GameBoardUiState,
    profileState: UiState<com.example.data.PlayerProfile?>,
    scoresState: UiState<List<com.example.data.ScoreRecord>>,
    profileFeedback: String?,
    personalBest: Long,
    hapticsEnabled: Boolean,
    soundEnabled: Boolean,
    onToggleSound: (Boolean) -> Unit,
    viewModel: CandySweepViewModel,
    onSignOut: () -> Unit
) {
    when (currentTab) {
        CandyTab.PLAY -> GameScreen(
            gameState = gameState,
            personalBest = personalBest,
            hapticsEnabled = hapticsEnabled,
            soundEnabled = soundEnabled,
            onToggleSound = onToggleSound,
            onCellTapped = viewModel::onCellTapped,
            onSwipe = viewModel::onSwipe,
            onRequestHint = viewModel::requestHint,
            onShuffle = viewModel::manualShuffle,
            onRestart = { viewModel.startNewGame() },
            onNextStage = viewModel::startNextStage,
            onSwitchMode = viewModel::switchMode
        )
        CandyTab.SCORES -> HighScoresScreen(
            profileState = profileState,
            scoresState = scoresState,
            onDeleteScore = viewModel::deleteScore
        )
        CandyTab.PROFILE -> ProfileScreen(
            email = user.email,
            profileState = profileState,
            feedbackMessage = profileFeedback,
            soundEnabled = soundEnabled,
            onToggleSound = onToggleSound,
            onSaveSettings = viewModel::updateProfileSettings,
            onToggleHaptics = viewModel::toggleHaptics,
            onClearFeedback = viewModel::clearProfileFeedback,
            onSignOut = onSignOut
        )
    }
}
