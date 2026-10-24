package me.pj.jqb.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.pj.jqb.data.db.AppInterceptStat
import me.pj.jqb.model.AuditRecord
import me.pj.jqb.model.TriggerType
import me.pj.jqb.ui.FavoriteOffenderStat
import me.pj.jqb.ui.MainViewModel
import me.pj.jqb.ui.components.ActionPillSelector
import me.pj.jqb.ui.components.AppIcon
import me.pj.jqb.ui.components.AppIconCache
import me.pj.jqb.ui.components.ConfettiCelebrationOverlay
import me.pj.jqb.ui.components.tickHaptic
import me.pj.jqb.ui.isEnglishLocale
import me.pj.jqb.ui.tr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val TIME_FORMATTER = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm:ss", Locale.getDefault())
}

private val FULL_TIME_FORMATTER = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WallOfShameScreen(viewModel: MainViewModel) {
    val view = LocalView.current
    val records by viewModel.auditRecords.collectAsState()
    val totalCount by viewModel.totalInterceptCount.collectAsState()
    val todayCount by viewModel.todayInterceptCount.collectAsState()
    val topOffenders by viewModel.topOffenders.collectAsState()
    val favoriteOffenders by viewModel.favoriteOffenders.collectAsState()
    val config by viewModel.configState.collectAsState()

    val deepAnalysisEnabled = config.settings.auditLog.deepAnalysis
    val favoriteBoardEnabled = config.settings.auditLog.favoriteLeaderboardEnabled
    val confettiEnabled = config.settings.auditLog.confettiEnabled
    val hapticEnabled = config.settings.auditLog.hapticEnabled

    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, BLOCKED, SPOOFED
    var selectedRecordForSheet by remember { mutableStateOf<AuditRecord?>(null) }
    var selectedPackageForSheet by remember { mutableStateOf<String?>(null) }
    var autoConfettiInSheet by remember { mutableStateOf(false) }
    var confettiBurstTrigger by remember { mutableIntStateOf(0) }

    val filteredRecords = remember(records, selectedFilter) {
        when (selectedFilter) {
            "BLOCKED" -> records.filter { it.action == "BLOCKED" }
            "SPOOFED" -> records.filter { it.action == "SPOOFED" }
            else -> records
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.EmojiEvents,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tr("光荣榜", "Hall of Fame"), style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                view.tickHaptic(hapticEnabled)
                                viewModel.setDeepAnalysisEnabled(!deepAnalysisEnabled)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Analytics,
                                contentDescription = tr("进程细节分析", "Process Analysis"),
                                tint = if (deepAnalysisEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            )
                        }
                        IconButton(
                            onClick = {
                                view.tickHaptic(hapticEnabled, confirm = true)
                                viewModel.insertTestAuditRecord()
                                if (confettiEnabled) {
                                    confettiBurstTrigger++
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Science,
                                contentDescription = tr("塞一条测试记录", "Add Test Record")
                            )
                        }
                        if (records.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    view.tickHaptic(hapticEnabled, confirm = true)
                                    viewModel.clearAuditLogs()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteSweep,
                                    contentDescription = tr("清空记录", "Clear Logs")
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Animated Statistics Cards
                item(key = "stats_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnimatedStatCard(
                            title = tr("今天拦下", "Caught Today"),
                            targetValue = todayCount,
                            icon = Icons.Outlined.Block,
                            modifier = Modifier.weight(1f)
                        )
                        AnimatedStatCard(
                            title = tr("一共抓到", "Total Caught"),
                            targetValue = totalCount,
                            icon = Icons.Outlined.EmojiEvents,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 2. Deep Probe Analysis Panel (Optional toggle)
                if (records.isNotEmpty()) {
                    item(key = "deep_analysis_panel") {
                        AnimatedVisibility(
                            visible = deepAnalysisEnabled,
                            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            DeepProbeAnalysisCard(records = records)
                        }
                    }
                }

                // 3. Dual Leaderboard Podium ("读取次数积分榜" & "最爱读取积分榜")
                if (topOffenders.isNotEmpty()) {
                    item(key = "top_offenders_card") {
                        TopOffendersCard(
                            topOffenders = topOffenders,
                            favoriteOffenders = favoriteOffenders,
                            favoriteBoardEnabled = favoriteBoardEnabled,
                            hasUsagePermission = viewModel.hasUsageStatsPermission(),
                            hapticEnabled = hapticEnabled,
                            onCelebrateClick = {
                                confettiBurstTrigger++
                            },
                            onAppClick = { pkg, shouldBurstInSheet ->
                                view.tickHaptic(hapticEnabled)
                                autoConfettiInSheet = shouldBurstInSheet && confettiEnabled
                                selectedRecordForSheet = records.firstOrNull { it.packageName == pkg }
                                selectedPackageForSheet = pkg
                            }
                        )
                    }
                }

                // 4. Filter Chips
                item(key = "filter_chips") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilter == "ALL",
                            onClick = {
                                view.tickHaptic(hapticEnabled)
                                selectedFilter = "ALL"
                            },
                            label = { Text(tr("全部 (${records.size})", "All (${records.size})")) }
                        )
                        FilterChip(
                            selected = selectedFilter == "BLOCKED",
                            onClick = {
                                view.tickHaptic(hapticEnabled)
                                selectedFilter = "BLOCKED"
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                val blockedCount = records.count { it.action == "BLOCKED" }
                                Text(tr("拦回去了 ($blockedCount)", "Blocked ($blockedCount)"))
                            }
                        )
                        FilterChip(
                            selected = selectedFilter == "SPOOFED",
                            onClick = {
                                view.tickHaptic(hapticEnabled)
                                selectedFilter = "SPOOFED"
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                val spoofedCount = records.count { it.action == "SPOOFED" }
                                Text(tr("塞了假词 ($spoofedCount)", "Spoofed ($spoofedCount)"))
                            }
                        )
                    }
                }

                // 5. Timeline Records or Empty State
                if (filteredRecords.isEmpty()) {
                    item(key = "empty_placeholder") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.VerifiedUser,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = tr("暂时还没抓到谁", "Nobody caught yet"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = tr(
                                        "要么是你装的应用真挺老实，要么是你还没去「应用规则」页给它们上规矩。想先看看颁奖礼花和吐槽的话，点右上角烧瓶图标可以塞几条测试记录。",
                                        "Either your apps are surprisingly well-behaved, or you haven't set rules for them yet. Tap the flask icon in the top bar to inject a sample record and test the confetti."
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredRecords,
                        key = { it.id }
                    ) { record ->
                        AuditRecordCard(
                            record = record,
                            showDeepInfo = deepAnalysisEnabled,
                            onClick = {
                                view.tickHaptic(hapticEnabled)
                                autoConfettiInSheet = false
                                selectedRecordForSheet = record
                                selectedPackageForSheet = record.packageName
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        // Celebratory Confetti Shower Overlay on Primary Screen
        if (confettiEnabled) {
            ConfettiCelebrationOverlay(
                burstTrigger = confettiBurstTrigger,
                hapticEnabled = hapticEnabled
            )
        }
    }

    // Secondary Detail BottomSheet (二级菜单，内含礼花特效与「为什么偷看」三级菜单)
    val sheetPkg = selectedPackageForSheet
    if (sheetPkg != null) {
        AuditDetailBottomSheet(
            packageName = sheetPkg,
            highlightedRecord = selectedRecordForSheet,
            allRecordsForApp = remember(records, sheetPkg) {
                records.filter { it.packageName == sheetPkg }
            },
            initialBurstConfetti = autoConfettiInSheet,
            confettiEnabled = confettiEnabled,
            hapticEnabled = hapticEnabled,
            viewModel = viewModel,
            onDismiss = {
                selectedRecordForSheet = null
                selectedPackageForSheet = null
                autoConfettiInSheet = false
            }
        )
    }
}

@Composable
fun AnimatedStatCard(
    title: String,
    targetValue: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val animatedNumber by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 450),
        label = "StatCounter"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = animatedNumber.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = tr("次", "hits"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

private data class PodiumRowEntry(
    val packageName: String,
    val appName: String,
    val scoreValue: Int,
    val scoreText: String,
    val subNote: String?
)

@Composable
fun TopOffendersCard(
    topOffenders: List<AppInterceptStat>,
    favoriteOffenders: List<FavoriteOffenderStat>,
    favoriteBoardEnabled: Boolean,
    hasUsagePermission: Boolean,
    hapticEnabled: Boolean,
    onCelebrateClick: () -> Unit,
    onAppClick: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isEn = isEnglishLocale()

    // 0 = 读取次数积分榜, 1 = 最爱读取积分榜
    var selectedBoardTab by remember(favoriteBoardEnabled) { mutableIntStateOf(0) }
    val activeTab = if (favoriteBoardEnabled) selectedBoardTab else 0

    val rows: List<PodiumRowEntry> = remember(activeTab, topOffenders, favoriteOffenders, isEn) {
        val unit = if (isEn) "pts" else "分"
        if (activeTab == 0) {
            topOffenders.map {
                PodiumRowEntry(
                    packageName = it.packageName,
                    appName = it.appName,
                    scoreValue = it.count,
                    scoreText = "${it.count} $unit",
                    subNote = null
                )
            }
        } else {
            favoriteOffenders.map {
                PodiumRowEntry(
                    packageName = it.packageName,
                    appName = it.appName,
                    scoreValue = it.favoriteScore,
                    scoreText = "${it.favoriteScore} $unit",
                    subNote = it.scoreDetail
                )
            }
        }
    }

    val maxScore = (rows.maxOfOrNull { it.scoreValue } ?: 1).coerceAtLeast(1)
    val champion = rows.firstOrNull()

    // Check if there is a tie among 1st, 2nd, or 3rd place — if so, 4th place wins the Charles Leclerc Award!
    val hasTopThreeTie = remember(rows) {
        val top3 = rows.take(3)
        top3.size >= 2 && top3.map { it.scoreValue }.distinct().size < top3.size
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Trophy Banner Header + Celebration Confetti Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (activeTab == 0) {
                            tr("读取次数积分榜", "Read Count Standings")
                        } else {
                            tr("最爱读取积分榜", "Most Obsessed Standings")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCelebrateClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Celebration,
                            contentDescription = tr("放礼花祝贺", "Pop Confetti"),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tr("放礼花", "Confetti"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Two-board switcher when enabled in Settings
            if (favoriteBoardEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        tr("读取次数积分榜", "Read Count"),
                        tr("最爱读取积分榜", "Most Obsessed")
                    )
                    tabs.forEachIndexed { idx, label ->
                        val isSel = activeTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                                .clickable {
                                    if (selectedBoardTab != idx) {
                                        view.tickHaptic(hapticEnabled)
                                        selectedBoardTab = idx
                                    }
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                if (activeTab == 1 && !hasUsagePermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                } catch (_: Exception) {
                                }
                            }
                    ) {
                        Text(
                            text = tr(
                                "点此授予「使用情况访问权限」，就能结合每个应用的真实前台使用分钟数来算积分",
                                "Tap to grant Usage Access so scores can factor in each app's actual foreground screen time"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (champion != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAppClick(champion.packageName, true) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("榜一大哥：${champion.appName}", "P1 Leader: ${champion.appName}"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            val roast = if (activeTab == 1) {
                                tr(
                                    "${champion.subNote ?: ""}，折合执念积分 ${champion.scoreText}",
                                    "${champion.subNote ?: ""} · Obsession Score ${champion.scoreText}"
                                )
                            } else {
                                tr(
                                    "已被抓到 ${champion.scoreValue} 次，手脚相当勤快（点我查看详情并放礼花）",
                                    "Caught ${champion.scoreValue} times—busy little fingers (tap for details & confetti)"
                                )
                            }
                            Text(
                                text = roast,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            rows.forEachIndexed { index, entry ->
                val isLeclercWinner = (index == 3 && hasTopThreeTie)
                val ratio by animateFloatAsState(
                    targetValue = (entry.scoreValue.toFloat() / maxScore.toFloat()).coerceIn(0.08f, 1f),
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "OffenderRatio"
                )

                val rankSubtext = when {
                    isLeclercWinner -> tr(
                        "第四名 · 前排打平神仙打架，喜提法拉利策略组痛苦面具",
                        "P4 · Top 3 tied in a photo finish—winner of the Ferrari Strategy Pain Award"
                    )
                    entry.subNote != null -> entry.subNote
                    index == 0 -> tr("冠军 · 剪贴板几乎焊死在手上", "1st Place · Practically welded to your clipboard")
                    index == 1 -> tr("亚军 · 一切回前台就先摸兜", "2nd Place · Checks your pockets on every resume")
                    index == 2 -> tr("季军 · 没事总想瞄两眼", "3rd Place · Always peeking when bored")
                    else -> null
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAppClick(entry.packageName, index <= 2 || isLeclercWinner) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            index == 0 || isLeclercWinner -> MaterialTheme.colorScheme.errorContainer
                            index == 1 -> MaterialTheme.colorScheme.tertiaryContainer
                            index == 2 -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when {
                                index == 0 -> Icon(
                                    imageVector = Icons.Outlined.EmojiEvents,
                                    contentDescription = "P1",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                index == 1 -> Icon(
                                    imageVector = Icons.Outlined.WorkspacePremium,
                                    contentDescription = "P2",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                index == 2 -> Icon(
                                    imageVector = Icons.Outlined.MilitaryTech,
                                    contentDescription = "P3",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                isLeclercWinner -> Icon(
                                    imageVector = Icons.Outlined.Flag,
                                    contentDescription = "Leclerc #16",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                else -> Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    AppIcon(packageName = entry.packageName, size = 36.dp, cornerRadius = 8.dp)
                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = entry.appName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isLeclercWinner) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text(
                                                text = tr("勒克莱尔奖 #16", "Leclerc Award #16"),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                if (rankSubtext != null) {
                                    Text(
                                        text = rankSubtext,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isLeclercWinner) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = entry.scoreText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (index == 0 || isLeclercWinner) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ratio)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(
                                        if (index == 0 || isLeclercWinner) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun DeepProbeAnalysisCard(records: List<AuditRecord>) {
    val isEn = isEnglishLocale()
    val analysis = remember(records, isEn) {
        val total = records.size.coerceAtLeast(1)
        val resumeCount = records.count {
            TriggerType.fromCode(it.triggerReason) == TriggerType.LIFECYCLE_RESUME
        }
        val bgOrSubCount = records.count {
            TriggerType.fromCode(it.triggerReason) == TriggerType.BACKGROUND_SILENT ||
                it.processName.contains(":")
        }
        var burstCount = 0
        val sortedByPkg = records.groupBy { it.packageName }
        var worstBurstApp: String? = null
        var worstBurstHits = 0
        sortedByPkg.forEach { (_, appLogs) ->
            var appBursts = 0
            for (i in 0 until appLogs.size - 1) {
                val delta = kotlin.math.abs(appLogs[i].timestamp - appLogs[i + 1].timestamp)
                if (delta in 1..2500L) {
                    burstCount++
                    appBursts++
                }
            }
            if (appBursts > worstBurstHits) {
                worstBurstHits = appBursts
                worstBurstApp = appLogs.firstOrNull()?.appName
            }
        }

        val buckets = IntArray(4)
        val cal = Calendar.getInstance()
        records.forEach { rec ->
            cal.timeInMillis = rec.timestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            buckets[(hour / 6).coerceIn(0, 3)]++
        }

        val metrics = if (isEn) {
            mapOf(
                "On Launch" to "${(resumeCount * 100) / total}%",
                "In Background" to "${(bgOrSubCount * 100) / total}%",
                "Burst Polls" to "$burstCount"
            )
        } else {
            mapOf(
                "一打开就读" to "${(resumeCount * 100) / total}%",
                "躲在后台读" to "${(bgOrSubCount * 100) / total}%",
                "连着读好几次" to "$burstCount 次"
            )
        }

        val worstText = worstBurstApp?.let {
            if (isEn) "$it (polls multiple times per launch)" else "$it（切一次前台要连读好几遍）"
        }

        Triple(metrics, worstText, buckets)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Analytics,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tr("它们一般怎么偷看", "How They Usually Snoop"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                analysis.first.forEach { (label, value) ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = value,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (analysis.second != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = tr("最急不可耐：${analysis.second}", "Most Impatient: ${analysis.second}"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            val buckets = analysis.third
            val maxBucket = (buckets.maxOrNull() ?: 1).coerceAtLeast(1)
            val labels = if (isEn) {
                listOf("00-06h", "06-12h", "12-18h", "18-24h")
            } else {
                listOf("凌晨 0-6点", "上午 6-12点", "下午 12-18点", "晚上 18-24点")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = tr("一天里什么时候最爱读", "Peak Snooping Hours"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                buckets.forEachIndexed { idx, count ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((count.toFloat() / maxBucket).coerceIn(0.05f, 1f))
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${labels[idx]} ($count)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun triggerIconFor(trigger: TriggerType): ImageVector {
    return when (trigger) {
        TriggerType.LIFECYCLE_RESUME, TriggerType.WINDOW_FOCUS -> Icons.Outlined.FlashOn
        TriggerType.BACKGROUND_SILENT -> Icons.Outlined.VisibilityOff
        TriggerType.ASYNC_POLL -> Icons.Outlined.Repeat
        else -> Icons.Outlined.History
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuditRecordCard(
    record: AuditRecord,
    showDeepInfo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEn = isEnglishLocale()
    val isBlocked = record.action == "BLOCKED"
    val timeFormatted = remember(record.timestamp, isEn) {
        val diffSec = (System.currentTimeMillis() - record.timestamp) / 1000
        val clock = TIME_FORMATTER.get()?.format(Date(record.timestamp)) ?: ""
        when {
            diffSec < 60 -> if (isEn) "Just now · $clock" else "刚刚 · $clock"
            diffSec < 3600 -> if (isEn) "${diffSec / 60}m ago · $clock" else "${diffSec / 60} 分钟前 · $clock"
            else -> clock
        }
    }
    val trigger = remember(record.triggerReason) {
        TriggerType.fromCode(record.triggerReason)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(packageName = record.packageName, size = 40.dp, cornerRadius = 10.dp)
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = record.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = record.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Action Pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isBlocked) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isBlocked) Icons.Outlined.Block else Icons.Outlined.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (isBlocked) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBlocked) {
                                tr("给它空剪贴板", "Returned Empty")
                            } else {
                                tr("塞了条假词", "Fed Decoy")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isBlocked) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            }
                        )
                    }
                }

                // Trigger Reason Badge with Icon
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = triggerIconFor(trigger),
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = trigger.displayName(isEn),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Deep Process Badge (if subprocess or deep analysis enabled)
                if (showDeepInfo && record.processName.isNotBlank()) {
                    val isSub = record.processName.contains(":")
                    val shortProc = if (isSub) {
                        ":" + record.processName.substringAfter(":")
                    } else {
                        tr("主进程", "main")
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSub) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    ) {
                        Text(
                            text = if (record.callingPid > 0) "$shortProc (PID ${record.callingPid})" else shortProc,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = if (isSub) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                }
            }

            if (!record.spoofedPayload.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = tr("塞给它的假内容", "Decoy Payload Fed"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = record.spoofedPayload,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditDetailBottomSheet(
    packageName: String,
    highlightedRecord: AuditRecord?,
    allRecordsForApp: List<AuditRecord>,
    initialBurstConfetti: Boolean,
    confettiEnabled: Boolean,
    hapticEnabled: Boolean,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val clipboard = LocalClipboardManager.current
    val isEn = isEnglishLocale()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val appMeta = remember(packageName) {
        AppIconCache.getAppMeta(context, packageName)
    }

    var currentAction by remember(packageName) {
        mutableStateOf(viewModel.getActionForPackage(packageName))
    }

    // State for the 3rd-level menu ("它到底图什么？")
    var showWhyThirdLevelMenu by remember { mutableStateOf(false) }

    // Confetti trigger inside the secondary BottomSheet window!
    var sheetConfettiTrigger by remember { mutableIntStateOf(if (initialBurstConfetti) 1 else 0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. App Header with Icon, Metadata & Direct Confetti Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(packageName = packageName, size = 52.dp, cornerRadius = 12.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appMeta.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "v${appMeta.versionName} · TargetSDK ${appMeta.targetSdk} · UID ${appMeta.uid}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (confettiEnabled) {
                        IconButton(
                            onClick = { sheetConfettiTrigger++ }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Celebration,
                                contentDescription = tr("放礼花", "Pop Confetti"),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 2. Quick Rule Switcher directly inside the sheet
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("怎么对付它（点了马上生效）", "Rule for this app (applies immediately)"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currentAction.summary(isEn),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ActionPillSelector(
                            selectedAction = currentAction,
                            onActionSelected = { newAction ->
                                currentAction = newAction
                                viewModel.setAppAction(packageName, newAction)
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currentAction.detail(isEn),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // 3. Third-Level Menu: "它老翻你剪贴板到底图什么？"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (showWhyThirdLevelMenu) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    view.tickHaptic(hapticEnabled)
                                    showWhyThirdLevelMenu = !showWhyThirdLevelMenu
                                }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Psychology,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = tr("它老翻你剪贴板到底图什么？", "Why does it keep reading your clipboard?"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (showWhyThirdLevelMenu) {
                                            tr("收起动机扒皮", "Hide motive breakdown")
                                        } else {
                                            tr("点开看看它明面上和暗地里的小算盘", "Tap to see its official excuse vs. hidden agenda")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showWhyThirdLevelMenu) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(
                            visible = showWhyThirdLevelMenu,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            val specificNote = remember(packageName, highlightedRecord, isEn) {
                                buildAppSpecificMotiveNote(packageName, highlightedRecord, isEn)
                            }
                            Column(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                MotiveItem(
                                    badge = tr("明面借口", "Official Excuse"),
                                    text = tr(
                                        "多半是冲着所谓的「淘口令、分享口令、网盘提取码、快递单号」去的。为了省掉你点粘贴那一下，只要你一切回前台，哪怕你根本没复制口令，它也非要先把剪贴板翻个底朝天。",
                                        "Usually looking for share tokens, promo codes, or tracking numbers. To save you a single tap on Paste, it scrapes your clipboard every time it enters the foreground."
                                    )
                                )
                                MotiveItem(
                                    badge = tr("暗地算盘", "Hidden Agenda"),
                                    text = tr(
                                        "跨应用归因与画像：你刚在微信或浏览器里复制了什么商品名、地址或者某句吐槽，切过来被它顺走，就能猜出你刚看了哪条广告、最近对什么感兴趣。至于拿去对完暗号之后，有没有偷偷塞进自家的用户喜好推荐模型里训练，咱也不好说。",
                                        "Cross-app attribution & profiling: whatever product name, link, or chat snippet you copied in another app helps their ad SDK infer what you've been browsing and feed recommendation models."
                                    )
                                )
                                MotiveItem(
                                    badge = tr("针对这家的诊断", "App-Specific Verdict"),
                                    text = specificNote
                                )
                            }
                        }
                    }
                }

                // 4. Highlighted Single Record Snapshot
                if (highlightedRecord != null) {
                    val trigger = TriggerType.fromCode(highlightedRecord.triggerReason)
                    val fullTime = FULL_TIME_FORMATTER.get()?.format(Date(highlightedRecord.timestamp)) ?: ""

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = tr("这次是怎么抓到的", "How This Read Was Caught"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = trigger.explain(isEn),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            DetailRow(label = tr("时间", "Timestamp"), value = fullTime)
                            DetailRow(
                                label = tr("当时怎么处理的", "Action Taken"),
                                value = if (highlightedRecord.action == "BLOCKED") {
                                    tr("直接回了个空剪贴板", "Returned an empty clipboard")
                                } else {
                                    tr("塞了一条假文字糊弄过去", "Fed a decoy phrase")
                                }
                            )
                            DetailRow(label = tr("触发场景", "Trigger"), value = trigger.displayName(isEn))
                            DetailRow(
                                label = tr("伸手的进程", "Process"),
                                value = highlightedRecord.processName.ifBlank { packageName }
                            )
                            if (highlightedRecord.callingPid > 0) {
                                DetailRow(
                                    label = tr("进程号", "PID / UID"),
                                    value = "PID ${highlightedRecord.callingPid} · UID ${highlightedRecord.callingUid}"
                                )
                            }
                            if (highlightedRecord.procState.isNotBlank()) {
                                DetailRow(label = tr("系统判定", "System State"), value = highlightedRecord.procState)
                            }

                            if (!highlightedRecord.spoofedPayload.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tr("当时塞给它的假内容", "Decoy Payload Fed"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = highlightedRecord.spoofedPayload,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                view.tickHaptic(hapticEnabled)
                                                clipboard.setText(AnnotatedString(highlightedRecord.spoofedPayload))
                                                Toast.makeText(
                                                    context,
                                                    if (isEn) "Copied decoy text" else "已复制这条假内容",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = tr("复制", "Copy"),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. App Historical Summary
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tr(
                                "前科记录（最近共抓到 ${allRecordsForApp.size} 次）",
                                "Prior Record (${allRecordsForApp.size} recent intercepts)"
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val resumeHits = allRecordsForApp.count {
                            TriggerType.fromCode(it.triggerReason) == TriggerType.LIFECYCLE_RESUME
                        }
                        val bgHits = allRecordsForApp.count {
                            TriggerType.fromCode(it.triggerReason) == TriggerType.BACKGROUND_SILENT ||
                                it.processName.contains(":")
                        }
                        Text(
                            text = tr(
                                "刚打开就伸手 $resumeHits 次 · 躲在后台或子进程摸兜 $bgHits 次",
                                "$resumeHits reads on launch · $bgHits silent background/subprocess reads"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Confetti overlay rendered directly inside the Secondary Menu (ModalBottomSheet) window!
            if (confettiEnabled) {
                ConfettiCelebrationOverlay(
                    burstTrigger = sheetConfettiTrigger,
                    hapticEnabled = hapticEnabled,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

private fun buildAppSpecificMotiveNote(pkg: String, record: AuditRecord?, isEn: Boolean): String {
    val lower = pkg.lowercase()
    val isSub = record?.processName?.contains(":") == true
    if (isEn) {
        return when {
            isSub -> "Notice the process name: '${record?.processName}' is a background subprocess reading your clipboard without you even opening the main UI. Block or Spoof recommended."
            lower.contains("taobao") || lower.contains("pinduoduo") || lower.contains("jingdong") || lower.contains("jd.") || lower.contains("idlefish") ->
                "Typical e-commerce app: scans for share tokens and competitor links on every launch. Set it to Spoof to feed its recommendation algorithm pure nonsense."
            lower.contains("tencent") || lower.contains("qq") || lower.contains("mm") ->
                "Messaging super-app packed with mini-program & webview SDKs. Keep it on Smart mode so your own chat pasting works while launch snooping gets blocked."
            lower.contains("aweme") || lower.contains("kuaishou") || lower.contains("xingin") || lower.contains("bilibili") || lower.contains("weibo") ->
                "Short-video / social feed app: loves harvesting share codes and cross-app campaign tokens. Smart or Spoof works great here."
            else ->
                "This app called ClipboardService without a user paste event—most likely an embedded analytics or ad-attribution SDK polling on resume."
        }
    }
    return when {
        isSub -> "注意看进程名：这次居然是「${record?.processName}」子进程在后台偷读的，连你打开主界面的功夫都不想等，建议直接「阻断」或「伪装」伺候。"
        lower.contains("taobao") || lower.contains("pinduoduo") || lower.contains("jingdong") || lower.contains("jd.") || lower.contains("idlefish") ->
            "典型电商软件：一打开就扫口令和竞品链接。你如果给它开「伪装」模式塞几条假口令或抽象怪话，它后台的猜你喜欢算法很快就会开始怀疑人生。"
        lower.contains("tencent") || lower.contains("qq") || lower.contains("mm") ->
            "日常聊天软件：里头往往塞满了小程序 SDK 或网页容器，切前台时经常顺手摸一下剪贴板。推荐挂在「智能」档，不影响你自己平时聊天粘贴。"
        lower.contains("aweme") || lower.contains("kuaishou") || lower.contains("xingin") || lower.contains("bilibili") || lower.contains("weibo") ->
            "内容与短视频软件：最喜欢抓分享口令和跨平台热词。用「智能」或「伪装」都能很好地治住它。"
        else ->
            "这家应用在没收到你粘贴指令的时候主动调了系统 ClipboardService，多半是内置的统计或广告归因 SDK 在例行摸兜。"
    }
}

@Composable
private fun MotiveItem(badge: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
