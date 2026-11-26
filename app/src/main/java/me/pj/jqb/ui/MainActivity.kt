package me.pj.jqb.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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

sealed class NavDestination(
    val titleZh: String,
    val titleEn: String,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector
) {
    object WallOfShame : NavDestination("光荣榜", "Hall of Fame", Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents)
    object AppRules : NavDestination("应用规则", "App Rules", Icons.Outlined.Tune, Icons.Filled.Tune)
    object Config : NavDestination("词库", "Decoy Pool", Icons.Outlined.Shuffle, Icons.Filled.Shuffle)
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
            MainScreen(viewModel = viewModel)
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
    val config by viewModel.configState.collectAsState()
    val settings = config.settings
    val hapticEnabled = settings.auditLog.hapticEnabled
    val isEn = resolveIsEnglish(settings.language)
    val transitionStyle = settings.pageTransitionStyle

    ClipBoxTheme(
        themeMode = settings.themeMode,
        amoledBlack = settings.amoledBlack,
        dynamicColor = settings.dynamicColor,
        colorSeed = settings.colorSeed
    ) {
        CompositionLocalProvider(LocalIsEnglish provides isEn) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    MainBottomBar(
                        pagerState = pagerState,
                        items = items,
                        isEn = isEn,
                        hapticEnabled = hapticEnabled,
                        transitionStyle = transitionStyle
                    )
                }
            ) { innerPadding ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val flingStiffness = if (transitionStyle == "smooth") 360f else 620f
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { it },
                        beyondViewportPageCount = 3,
                        userScrollEnabled = settings.swipeNavigationEnabled,
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = pagerState,
                            pagerSnapDistance = PagerSnapDistance.atMost(1),
                            snapAnimationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = flingStiffness
                            )
                        )
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
}

@Composable
private fun MainBottomBar(
    pagerState: PagerState,
    items: List<NavDestination>,
    isEn: Boolean,
    hapticEnabled: Boolean,
    transitionStyle: String
) {
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val activeTab by remember(pagerState) {
        derivedStateOf { pagerState.targetPage }
    }
    val hapticState = remember { intArrayOf(0, 0) } // [lastHapticPage, isProgrammaticJump (0/1)]

    LaunchedEffect(pagerState, hapticEnabled) {
        snapshotFlow { pagerState.targetPage }
            .distinctUntilChanged()
            .collect { target ->
                if (hapticState[1] == 0 && target != hapticState[0]) {
                    view.tickHaptic(enabled = hapticEnabled)
                }
                hapticState[0] = target
            }
    }

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
                        hapticState[0] = index
                        view.tickHaptic(enabled = hapticEnabled)
                        coroutineScope.launch {
                            hapticState[1] = 1
                            try {
                                if (transitionStyle == "none") {
                                    pagerState.scrollToPage(index)
                                } else {
                                    val stiffness = if (transitionStyle == "smooth") 360f else 620f
                                    pagerState.animateScrollToPage(
                                        page = index,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = stiffness
                                        )
                                    )
                                }
                            } finally {
                                hapticState[1] = 0
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
