package me.pj.jqb.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import me.pj.jqb.model.ActionType
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
    val hasUsagePermission by viewModel.hasUsagePermissionState.collectAsState()
    val config by viewModel.configState.collectAsState()

    val deepAnalysisEnabled = config.settings.auditLog.deepAnalysis
    val favoriteBoardEnabled = config.settings.auditLog.favoriteLeaderboardEnabled
    val confettiEnabled = config.settings.auditLog.confettiEnabled
    val hapticEnabled = config.settings.auditLog.hapticEnabled

    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, BLOCKED, SPOOFED
    var selectedRecordForSheet by remember { mutableStateOf<AuditRecord?>(null) }
    var selectedPackageForSheet by remember { mutableStateOf<String?>(null) }
    var quickActionTargetRecord by remember { mutableStateOf<AuditRecord?>(null) }
    var quickActionTargetPackage by remember { mutableStateOf<String?>(null) }
    var autoConfettiInSheet by remember { mutableStateOf(false) }
    var confettiBurstTrigger by remember { mutableIntStateOf(0) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    val (blockedCount, spoofedCount) = remember(records) {
        var b = 0
        var s = 0
        for (r in records) {
            if (r.action == "BLOCKED") b++
            else if (r.action == "SPOOFED") s++
        }
        b to s
    }

    val filteredRecords = remember(records, selectedFilter) {
        when (selectedFilter) {
            "BLOCKED" -> records.filter { it.action == "BLOCKED" }
            "SPOOFED" -> records.filter { it.action == "SPOOFED" }
            else -> records
        }
    }

    LaunchedEffect(Unit) {
        viewModel.pullPendingAudits()
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(tr("清空所有拦截记录？", "Clear all intercept records?")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.tickHaptic(hapticEnabled, confirm = true)
                        viewModel.clearAuditLogs()
                        showClearAllConfirm = false
                    }
                ) {
                    Text(tr("清空", "Clear"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(tr("取消", "Cancel"))
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(tr("光荣榜", "Hall of Fame"), style = MaterialTheme.typography.titleLarge)
                    },
                    actions = {
                        Box {
                            IconButton(
                                onClick = {
                                    view.tickHaptic(hapticEnabled)
                                    showOverflowMenu = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = tr("更多选项", "More options")
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(tr("显示进程细节", "Show process details")) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Analytics,
                                            contentDescription = null
                                        )
                                    },
                                    trailingIcon = if (deepAnalysisEnabled) {
                                        {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        view.tickHaptic(hapticEnabled)
                                        viewModel.setDeepAnalysisEnabled(!deepAnalysisEnabled)
                                        showOverflowMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("添加测试记录", "Add test record")) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Science,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        view.tickHaptic(hapticEnabled, confirm = true)
                                        viewModel.insertTestAuditRecord()
                                        if (confettiEnabled) {
                                            confettiBurstTrigger++
                                        }
                                        showOverflowMenu = false
                                    }
                                )
                                if (records.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = tr("清空记录", "Clear records"),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteSweep,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            view.tickHaptic(hapticEnabled)
                                            showOverflowMenu = false
                                            showClearAllConfirm = true
                                        }
                                    )
                                }
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Animated Statistics Cards
                item(key = "stats_header", contentType = "stats_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnimatedStatCard(
                            title = tr("今日拦截", "Today"),
                            targetValue = todayCount,
                            icon = Icons.Outlined.Block,
                            modifier = Modifier.weight(1f)
                        )
                        AnimatedStatCard(
                            title = tr("累计拦截", "Total"),
                            targetValue = totalCount,
                            icon = Icons.Outlined.EmojiEvents,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 2. Deep Probe Analysis Panel (Optional toggle)
                if (records.isNotEmpty()) {
                    item(key = "deep_analysis_panel", contentType = "deep_analysis_panel") {
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
                    item(key = "top_offenders_card", contentType = "top_offenders_card") {
                        TopOffendersCard(
                            topOffenders = topOffenders,
                            favoriteOffenders = favoriteOffenders,
                            favoriteBoardEnabled = favoriteBoardEnabled,
                            hasUsagePermission = hasUsagePermission,
                            hapticEnabled = hapticEnabled,
                            onAppClick = { pkg, shouldBurstInSheet ->
                                view.tickHaptic(hapticEnabled)
                                autoConfettiInSheet = shouldBurstInSheet && confettiEnabled
                                selectedRecordForSheet = records.firstOrNull { it.packageName == pkg }
                                selectedPackageForSheet = pkg
                            },
                            onAppLongClick = { pkg ->
                                view.tickHaptic(hapticEnabled, confirm = true)
                                quickActionTargetRecord = records.firstOrNull { it.packageName == pkg }
                                quickActionTargetPackage = pkg
                            }
                        )
                    }
                }

                // 4. Filter Chips
                item(key = "filter_chips", contentType = "filter_chips") {
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
                            label = { Text(tr("全部 ${records.size}", "All ${records.size}")) }
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
                                Text(tr("阻断 $blockedCount", "Blocked $blockedCount"))
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
                                Text(tr("伪装 $spoofedCount", "Spoofed $spoofedCount"))
                            }
                        )
                    }
                }

                // 5. Timeline Records or Empty State
                if (filteredRecords.isEmpty()) {
                    item(key = "empty_placeholder", contentType = "empty_placeholder") {
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
                                    text = tr("暂无拦截记录", "No intercept records"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = tr(
                                        "在「应用规则」中为应用设置规则后，拦截记录会显示在这里。",
                                        "Records will appear here once rules are configured in App Rules."
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredRecords,
                        key = { it.id },
                        contentType = { if (it.spoofedPayload.isNullOrBlank()) "audit_plain" else "audit_spoofed" }
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
                            onLongClick = {
                                view.tickHaptic(hapticEnabled, confirm = true)
                                quickActionTargetRecord = record
                                quickActionTargetPackage = record.packageName
                            }
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

    // Long-press MD3 Context Menu Sheet
    val quickPkg = quickActionTargetPackage
    if (quickPkg != null) {
        HallOfShameQuickActionSheet(
            packageName = quickPkg,
            targetRecord = quickActionTargetRecord,
            appRecordCount = remember(records, quickPkg) {
                records.count { it.packageName == quickPkg }
            },
            hapticEnabled = hapticEnabled,
            viewModel = viewModel,
            onOpenFullDetail = {
                val rec = quickActionTargetRecord ?: records.firstOrNull { it.packageName == quickPkg }
                quickActionTargetRecord = null
                quickActionTargetPackage = null
                autoConfettiInSheet = false
                selectedRecordForSheet = rec
                selectedPackageForSheet = quickPkg
            },
            onDismiss = {
                quickActionTargetRecord = null
                quickActionTargetPackage = null
            }
        )
    }

    // Secondary Detail BottomSheet
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                    style = MaterialTheme.typography.labelMedium,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TopOffendersCard(
    topOffenders: List<AppInterceptStat>,
    favoriteOffenders: List<FavoriteOffenderStat>,
    favoriteBoardEnabled: Boolean,
    hasUsagePermission: Boolean,
    hapticEnabled: Boolean,
    onAppClick: (String, Boolean) -> Unit,
    onAppLongClick: (String) -> Unit = {}
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tr("积分榜", "Standings"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Two-board switcher when enabled in Settings
            if (favoriteBoardEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                val tabs = listOf(
                    tr("读取次数积分榜", "Read Count Standings"),
                    tr("最爱读取积分榜", "Most Obsessed Standings")
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    tabs.forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = activeTab == idx,
                            onClick = {
                                if (selectedBoardTab != idx) {
                                    view.tickHaptic(hapticEnabled)
                                    selectedBoardTab = idx
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                if (activeTab == 1 && !hasUsagePermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                } catch (_: Exception) {
                                }
                            }
                    ) {
                        Text(
                            text = tr(
                                "授予「使用情况访问权限」后可按使用时长计算积分",
                                "Grant Usage Access to calculate points by screen time"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { onAppClick(entry.packageName, index <= 2 || isLeclercWinner) },
                            onLongClick = { onAppLongClick(entry.packageName) }
                        )
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
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text(
                                                text = tr("勒克莱尔奖 #16", "Leclerc Award #16"),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (entry.subNote != null) {
                                    Text(
                                        text = entry.subNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
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
                "On launch" to "${(resumeCount * 100) / total}%",
                "Background" to "${(bgOrSubCount * 100) / total}%",
                "Burst reads" to "$burstCount"
            )
        } else {
            mapOf(
                "启动时" to "${(resumeCount * 100) / total}%",
                "后台" to "${(bgOrSubCount * 100) / total}%",
                "连续读取" to "$burstCount 次"
            )
        }

        Triple(metrics, worstBurstApp, buckets)
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
                    text = tr("读取方式统计", "Read breakdown"),
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
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    text = tr("连续读取最多：${analysis.second}", "Most burst reads: ${analysis.second}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val buckets = analysis.third
            val maxBucket = (buckets.maxOrNull() ?: 1).coerceAtLeast(1)
            val periodNames = if (isEn) {
                listOf("00-06h", "06-12h", "12-18h", "18-24h")
            } else {
                listOf("0-6点", "6-12点", "12-18点", "18-24点")
            }
            val periodDescs = if (isEn) {
                listOf("Night", "Morning", "Afternoon", "Evening")
            } else {
                listOf("凌晨", "上午", "下午", "夜间")
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = tr("读取时段分布", "Time distribution"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    buckets.forEachIndexed { idx, count ->
                        val barRatio = if (maxBucket > 0) (count.toFloat() / maxBucket).coerceIn(0.08f, 1f) else 0.08f
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (count > 0) "$count" else "-",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (count > 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(barRatio)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (count == maxBucket && count > 0) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                            }
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = periodDescs[idx],
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = periodNames[idx],
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

private val AuditCardShape = RoundedCornerShape(16.dp)
private val AuditBadgeShape = RoundedCornerShape(6.dp)
private val AuditPayloadShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuditRecordCard(
    record: AuditRecord,
    showDeepInfo: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
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
            .clip(AuditCardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = AuditCardShape
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = record.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Action Pill
                Surface(
                    shape = AuditBadgeShape,
                    color = if (isBlocked) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    }
                ) {
                    Text(
                        text = if (isBlocked) {
                            tr("阻断", "Blocked")
                        } else {
                            tr("伪装", "Spoofed")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isBlocked) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Trigger Reason Badge
                Surface(
                    shape = AuditBadgeShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = trigger.displayName(isEn),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Deep Process Badge (optional)
                if (showDeepInfo && record.processName.isNotBlank()) {
                    val isSub = record.processName.contains(":")
                    val shortProc = if (isSub) {
                        ":" + record.processName.substringAfter(":")
                    } else {
                        tr("主进程", "main")
                    }
                    Surface(
                        shape = AuditBadgeShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = if (record.callingPid > 0) "$shortProc (PID ${record.callingPid})" else shortProc,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!record.spoofedPayload.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = AuditPayloadShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = record.spoofedPayload,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
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
    var isCustomRule by remember(packageName) {
        mutableStateOf(viewModel.hasCustomRule(packageName))
    }

    var showWhyMenu by remember { mutableStateOf(false) }
    val sheetConfettiTrigger by remember { mutableIntStateOf(if (initialBurstConfetti) 1 else 0) }

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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. App Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        packageName = packageName,
                        size = 48.dp,
                        cornerRadius = 12.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appMeta.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 2. Quick Rule Switcher
                Card(
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
                                text = tr("规则", "Rule"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isCustomRule) {
                                        tr("单独设置", "Custom")
                                    } else {
                                        tr("跟随全局", "Global")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isCustomRule) {
                                    IconButton(
                                        onClick = {
                                            view.tickHaptic(hapticEnabled)
                                            viewModel.resetAppToGlobalDefault(packageName)
                                            isCustomRule = false
                                            currentAction = viewModel.getActionForPackage(packageName)
                                        },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Undo,
                                            contentDescription = tr("恢复跟随全局", "Reset to global"),
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ActionPillSelector(
                            selectedAction = currentAction,
                            onActionSelected = { newAction ->
                                currentAction = newAction
                                isCustomRule = true
                                viewModel.setAppAction(packageName, newAction)
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currentAction.detail(isEn),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3. Possible Reason (Collapsible)
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    view.tickHaptic(hapticEnabled)
                                    showWhyMenu = !showWhyMenu
                                },
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
                                Text(
                                    text = tr("可能的读取原因", "Possible reason"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                imageVector = if (showWhyMenu) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(
                            visible = showWhyMenu,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            val specificNote = remember(packageName, highlightedRecord, isEn) {
                                buildAppSpecificMotiveNote(packageName, highlightedRecord, isEn)
                            }
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = specificNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr("本次记录", "This record"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (highlightedRecord.action == "BLOCKED") {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    }
                                ) {
                                    Text(
                                        text = if (highlightedRecord.action == "BLOCKED") {
                                            tr("阻断", "Blocked")
                                        } else {
                                            tr("伪装", "Spoofed")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (highlightedRecord.action == "BLOCKED") {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = triggerIconFor(trigger),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = trigger.displayName(isEn),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = trigger.explain(isEn),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            DetailRow(label = tr("时间", "Time"), value = fullTime)
                            DetailRow(
                                label = tr("进程", "Process"),
                                value = highlightedRecord.processName.ifBlank { packageName }
                            )
                            if (highlightedRecord.callingPid > 0) {
                                DetailRow(
                                    label = "PID · UID",
                                    value = "${highlightedRecord.callingPid} · ${highlightedRecord.callingUid}"
                                )
                            }
                            if (highlightedRecord.procState.isNotBlank()) {
                                DetailRow(
                                    label = tr("进程状态", "Process state"),
                                    value = formatProcState(highlightedRecord.procState, isEn)
                                )
                            }
                            DetailRow(
                                label = tr("应用版本", "Version"),
                                value = "v${appMeta.versionName.ifBlank { "1.0" }} (TargetSDK ${appMeta.targetSdk})"
                            )

                            if (!highlightedRecord.spoofedPayload.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tr("返回的假内容", "Decoy returned"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = highlightedRecord.spoofedPayload,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                view.tickHaptic(hapticEnabled)
                                                clipboard.setText(AnnotatedString(highlightedRecord.spoofedPayload))
                                                Toast.makeText(
                                                    context,
                                                    if (isEn) "Copied" else "已复制",
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
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = tr(
                                "历史记录 · 共 ${allRecordsForApp.size} 次",
                                "History · ${allRecordsForApp.size} total"
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        val resumeHits = allRecordsForApp.count {
                            TriggerType.fromCode(it.triggerReason) == TriggerType.LIFECYCLE_RESUME
                        }
                        val bgHits = allRecordsForApp.count {
                            TriggerType.fromCode(it.triggerReason) == TriggerType.BACKGROUND_SILENT ||
                                it.processName.contains(":")
                        }
                        val fgPollHits = (allRecordsForApp.size - resumeHits - bgHits).coerceAtLeast(0)
                        Text(
                            text = tr(
                                "启动时 $resumeHits · 前台 $fgPollHits · 后台 $bgHits",
                                "Launch $resumeHits · Foreground $fgPollHits · Background $bgHits"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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
            lower == "com.google.android.as" || lower.contains("systemintelligence") ->
                "Android System Intelligence scans clipboard text to show smart paste suggestions on the keyboard. Safe to block."
            isSub -> "Read from background subprocess '${record?.processName}', unrelated to UI interaction. Block or Spoof recommended."
            lower.contains("taobao") || lower.contains("pinduoduo") || lower.contains("jingdong") || lower.contains("jd.") || lower.contains("idlefish") ->
                "Commonly reads share tokens and product links. Spoof recommended."
            lower.contains("tencent") || lower.contains("qq") || lower.contains("mm") ->
                "Often triggered by embedded mini-programs or webviews. Smart mode keeps manual paste working."
            lower.contains("aweme") || lower.contains("kuaishou") || lower.contains("xingin") || lower.contains("bilibili") || lower.contains("weibo") ->
                "Commonly scans share codes on launch. Smart or Spoof recommended."
            else ->
                "Read without a paste action, likely from an analytics or ad attribution SDK."
        }
    }
    return when {
        lower == "com.google.android.as" || lower.contains("systemintelligence") ->
            "系统智能服务，用于在输入法上显示智能粘贴建议。设为阻断不影响正常使用。"
        isSub -> "由后台子进程「${record?.processName}」读取，与界面操作无关。建议阻断或伪装。"
        lower.contains("taobao") || lower.contains("pinduoduo") || lower.contains("jingdong") || lower.contains("jd.") || lower.contains("idlefish") ->
            "常见于识别分享口令和商品链接。建议伪装。"
        lower.contains("tencent") || lower.contains("qq") || lower.contains("mm") ->
            "常由内置小程序或网页组件读取。建议智能，不影响手动粘贴。"
        lower.contains("aweme") || lower.contains("kuaishou") || lower.contains("xingin") || lower.contains("bilibili") || lower.contains("weibo") ->
            "常用于识别分享码。建议智能或伪装。"
        else ->
            "在没有粘贴操作时读取，可能来自统计或广告 SDK。"
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
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

private fun formatProcState(procState: String, isEn: Boolean): String {
    if (procState.isBlank()) return ""
    if (procState.contains("BURST_POLL", ignoreCase = true)) {
        val ms = Regex("""\d+ms""").find(procState)?.value ?: ""
        return if (isEn) "Burst reads ($ms interval)" else "连续读取（间隔 $ms）"
    }
    if (procState.contains("TOP_ON_RESUME", ignoreCase = true)) {
        val ms = Regex("""\d+ms""").find(procState)?.value ?: ""
        return if (isEn) "Read after foreground ($ms)" else "切到前台后读取（$ms）"
    }
    if (procState.contains("WINDOW_FOCUS", ignoreCase = true)) {
        val ms = Regex("""\d+ms""").find(procState)?.value ?: ""
        return if (isEn) "Read after focus ($ms)" else "获得焦点后读取（$ms）"
    }
    if (procState.contains("TOP_UNSOLICITED_PROBE", ignoreCase = true)) {
        return if (isEn) "Foreground read without paste" else "前台读取，无粘贴操作"
    }
    if (procState.contains("USER_PASTE_MENU", ignoreCase = true)) {
        return if (isEn) "Menu paste" else "菜单粘贴"
    }
    if (procState.contains("BACKGROUND_SUBPROCESS", ignoreCase = true)) {
        return if (isEn) "Background subprocess read" else "后台子进程读取"
    }
    return when (procState.uppercase()) {
        "TOP", "TOP_ACTIVE" -> if (isEn) "Foreground" else "前台"
        "TOP_SLEEPING" -> if (isEn) "Screen off" else "息屏"
        "FOREGROUND_SERVICE" -> if (isEn) "Foreground service" else "前台服务"
        "BOUND_TOP" -> if (isEn) "Bound to foreground" else "绑定前台进程"
        "SERVICE" -> if (isEn) "Background service" else "后台服务"
        "CACHED_EMPTY", "CACHED_ACTIVITY" -> if (isEn) "Cached background" else "后台缓存"
        else -> procState
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HallOfShameQuickActionSheet(
    packageName: String,
    targetRecord: AuditRecord?,
    appRecordCount: Int,
    hapticEnabled: Boolean,
    viewModel: MainViewModel,
    onOpenFullDetail: () -> Unit,
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
    var isCustomRule by remember(packageName) {
        mutableStateOf(viewModel.hasCustomRule(packageName))
    }
    var showClearAppConfirm by remember { mutableStateOf(false) }

    if (showClearAppConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAppConfirm = false },
            title = {
                Text(
                    tr(
                        "删除「${appMeta.appName}」的全部 $appRecordCount 条记录？",
                        "Delete all $appRecordCount records for ${appMeta.appName}?"
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.tickHaptic(hapticEnabled, confirm = true)
                        viewModel.deleteAuditRecordsForPackage(packageName)
                        Toast.makeText(
                            context,
                            if (isEn) "Deleted $appRecordCount records" else "已删除 $appRecordCount 条记录",
                            Toast.LENGTH_SHORT
                        ).show()
                        showClearAppConfirm = false
                        onDismiss()
                    }
                ) {
                    Text(tr("删除", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAppConfirm = false }) {
                    Text(tr("取消", "Cancel"))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    packageName = packageName,
                    size = 44.dp,
                    cornerRadius = 10.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appMeta.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (targetRecord != null) {
                        val fullTime = FULL_TIME_FORMATTER.get()?.format(Date(targetRecord.timestamp)) ?: ""
                        val trigger = TriggerType.fromCode(targetRecord.triggerReason)
                        Text(
                            text = "${trigger.displayName(isEn)} · $fullTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2. Quick Rule Switcher Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("规则", "Rule"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isCustomRule) {
                                    tr("单独设置", "Custom")
                                } else {
                                    tr("跟随全局", "Global")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isCustomRule) {
                                IconButton(
                                    onClick = {
                                        view.tickHaptic(hapticEnabled)
                                        viewModel.resetAppToGlobalDefault(packageName)
                                        isCustomRule = false
                                        currentAction = viewModel.getActionForPackage(packageName)
                                        Toast.makeText(
                                            context,
                                            if (isEn) "Reset to global" else "已恢复跟随全局",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Undo,
                                        contentDescription = tr("恢复跟随全局", "Reset to global"),
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionPillSelector(
                        selectedAction = currentAction,
                        onActionSelected = { newAction ->
                            currentAction = newAction
                            isCustomRule = true
                            viewModel.setAppAction(packageName, newAction)
                            Toast.makeText(
                                context,
                                if (isEn) {
                                    "Set to ${newAction.label(true)}"
                                } else {
                                    "已设为${newAction.label(false)}"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }

            // 3. Action List (MD3 ListItems inside a single Card)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    QuickActionListItem(
                        icon = Icons.Outlined.Description,
                        title = tr("查看详情", "View details"),
                        onClick = {
                            view.tickHaptic(hapticEnabled)
                            onOpenFullDetail()
                        }
                    )

                    if (!targetRecord?.spoofedPayload.isNullOrBlank()) {
                        val payload = targetRecord?.spoofedPayload.orEmpty()
                        QuickActionListItem(
                            icon = Icons.Outlined.ContentCopy,
                            title = tr("复制假内容", "Copy decoy"),
                            onClick = {
                                view.tickHaptic(hapticEnabled)
                                clipboard.setText(AnnotatedString(payload))
                                Toast.makeText(
                                    context,
                                    if (isEn) "Copied" else "已复制",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            }
                        )
                    }

                    QuickActionListItem(
                        icon = Icons.Outlined.ContentCopy,
                        title = tr("复制包名", "Copy package name"),
                        onClick = {
                            view.tickHaptic(hapticEnabled)
                            clipboard.setText(AnnotatedString(packageName))
                            Toast.makeText(
                                context,
                                if (isEn) "Copied" else "已复制",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        }
                    )

                    QuickActionListItem(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        title = tr("系统应用信息", "App info"),
                        onClick = {
                            view.tickHaptic(hapticEnabled)
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                            onDismiss()
                        }
                    )

                    if (targetRecord != null || appRecordCount > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }

                    if (targetRecord != null) {
                        QuickActionListItem(
                            icon = Icons.Outlined.DeleteOutline,
                            title = tr("删除此记录", "Delete this record"),
                            isDestructive = true,
                            onClick = {
                                view.tickHaptic(hapticEnabled, confirm = true)
                                viewModel.deleteAuditRecord(targetRecord.id)
                                Toast.makeText(
                                    context,
                                    if (isEn) "Deleted" else "已删除",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            }
                        )
                    }

                    if (appRecordCount > 0) {
                        QuickActionListItem(
                            icon = Icons.Outlined.DeleteSweep,
                            title = tr(
                                "删除该应用全部记录（$appRecordCount）",
                                "Delete all records for this app ($appRecordCount)"
                            ),
                            isDestructive = true,
                            onClick = {
                                view.tickHaptic(hapticEnabled)
                                showClearAppConfirm = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionListItem(
    icon: ImageVector,
    title: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconColor
            )
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    )
}
