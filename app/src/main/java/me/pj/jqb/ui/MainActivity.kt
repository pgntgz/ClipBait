package me.pj.jqb.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.pj.jqb.ui.components.tickHaptic
import me.pj.jqb.ui.screens.AppRulesScreen
import me.pj.jqb.ui.screens.ConfigScreen
import me.pj.jqb.ui.screens.SettingsScreen
import me.pj.jqb.ui.screens.WallOfShameScreen
import me.pj.jqb.ui.theme.ClipBoxTheme
import kotlin.math.abs

sealed class NavDestination(
    val titleZh: String,
    val titleEn: String,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector
) {
    object WallOfShame : NavDestination("光荣榜", "Hall of Fame", Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents)
    object AppRules : NavDestination("应用规则", "App Rules", Icons.Outlined.Tune, Icons.Filled.Tune)
    object Config : NavDestination("伪装词库", "Decoy Pool", Icons.Outlined.Shuffle, Icons.Filled.Shuffle)
    object Settings : NavDestination("设置", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)

    fun title(isEn: Boolean): String = if (isEn) titleEn else titleZh

    companion object {
        val items = listOf(WallOfShame, AppRules, Config, Settings)
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClipBoxTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.pullPendingAudits()
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val items = NavDestination.items
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { items.size })
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val config by viewModel.configState.collectAsState()
    val hapticEnabled = config.settings.auditLog.hapticEnabled
    val isEn = resolveIsEnglish(config.settings.language)

    var isProgrammaticJump by remember { mutableStateOf(false) }
    var lastHapticPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState, hapticEnabled) {
        snapshotFlow { pagerState.targetPage }
            .distinctUntilChanged()
            .collect { target ->
                if (!isProgrammaticJump && target != lastHapticPage) {
                    view.tickHaptic(enabled = hapticEnabled)
                }
                lastHapticPage = target
            }
    }

    val activeTab = pagerState.targetPage

    CompositionLocalProvider(LocalIsEnglish provides isEn) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    items.forEachIndexed { index, destination ->
                        val isSelected = activeTab == index
                        val tabTitle = destination.title(isEn)
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (activeTab != index) {
                                    lastHapticPage = index
                                    view.tickHaptic(enabled = hapticEnabled)
                                    coroutineScope.launch {
                                        isProgrammaticJump = true
                                        try {
                                            if (abs(pagerState.currentPage - index) > 1) {
                                                pagerState.scrollToPage(index)
                                            } else {
                                                pagerState.animateScrollToPage(
                                                    page = index,
                                                    animationSpec = tween(
                                                        durationMillis = 175,
                                                        easing = FastOutSlowInEasing
                                                    )
                                                )
                                            }
                                        } finally {
                                            isProgrammaticJump = false
                                        }
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = tabTitle
                                )
                            },
                            label = {
                                Text(
                                    text = tabTitle,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 0
                ) { page ->
                    when (page) {
                        0 -> WallOfShameScreen(viewModel = viewModel)
                        1 -> AppRulesScreen(viewModel = viewModel)
                        2 -> ConfigScreen(viewModel = viewModel)
                        3 -> SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
