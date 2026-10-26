package me.pj.jqb.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.pj.jqb.AppInfo
import me.pj.jqb.HookState
import me.pj.jqb.model.ActionType
import me.pj.jqb.ui.MainViewModel
import me.pj.jqb.ui.components.ActionPillSelector
import me.pj.jqb.ui.components.tickHaptic
import me.pj.jqb.ui.isEnglishLocale
import me.pj.jqb.ui.tr

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val hookState by viewModel.hookState.collectAsState()
    val config by viewModel.configState.collectAsState()
    val isEn = isEnglishLocale()

    val hapticEnabled = config.settings.auditLog.hapticEnabled

    var resumeWindowMs by remember(config.settings.smartDetect.resumeWindowMs) {
        mutableFloatStateOf(config.settings.smartDetect.resumeWindowMs.toFloat())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("设置", "Settings"), style = MaterialTheme.typography.titleLarge) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live 4-State Module Status Card
            item {
                val isOk = hookState == HookState.ACTIVE
                val isWarn = hookState == HookState.NEEDS_REBOOT || hookState == HookState.CHECKING
                val containerColor = when {
                    isOk -> MaterialTheme.colorScheme.primaryContainer
                    isWarn -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                }
                val contentColor = when {
                    isOk -> MaterialTheme.colorScheme.onPrimaryContainer
                    isWarn -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onErrorContainer
                }
                val statusIcon = when (hookState) {
                    HookState.ACTIVE -> Icons.Outlined.CheckCircle
                    HookState.CHECKING -> Icons.Outlined.HourglassTop
                    HookState.NEEDS_REBOOT -> Icons.Outlined.RestartAlt
                    HookState.INACTIVE -> Icons.Outlined.Warning
                }
                val titleText = when (hookState) {
                    HookState.ACTIVE -> tr("已经在后台盯着剪贴板了", "Clipboard Hook Active")
                    HookState.CHECKING -> tr("正在跟系统服务打招呼...", "Checking system_server hook...")
                    HookState.NEEDS_REBOOT -> tr(
                        "模块开了，但系统服务还没接上（需要重启一次手机）",
                        "Module enabled, waiting for reboot"
                    )
                    HookState.INACTIVE -> tr("模块还没生效", "Module Inactive")
                }
                val bodyText = when (hookState) {
                    HookState.ACTIVE -> tr(
                        "已经挂在系统框架 (system_server) 上。以后不管你新装什么应用、或者在列表里改任何规则，都是当场生效，不用再重启手机。",
                        "Hooked into system_server. Any rule changes or newly installed apps take effect immediately without rebooting."
                    )
                    HookState.CHECKING -> tr(
                        "正在确认 system_server 里的钩子有没有回话...",
                        "Verifying handshake with ClipboardService in system_server..."
                    )
                    HookState.NEEDS_REBOOT -> tr(
                        "请确认在 LSPosed / Vector 作用域里勾选了「Android 系统」，然后重启一次手机即可。",
                        "Make sure 'System Framework (android)' is checked in LSPosed / Vector scope, then reboot once."
                    )
                    HookState.INACTIVE -> tr(
                        "去 LSPosed / Vector 把本模块打开，作用域只勾「Android 系统」就行（不用挨个勾其他应用），重启一次手机就好。",
                        "Enable ClipBait in LSPosed / Vector with only 'System Framework' checked in scope (no need to check individual apps), then reboot."
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = contentColor
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = bodyText,
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor
                            )
                        }
                        IconButton(
                            onClick = {
                                view.tickHaptic(hapticEnabled)
                                viewModel.recheckHookState()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = tr("重新检测状态", "Recheck Status"),
                                tint = contentColor
                            )
                        }
                    }
                }
            }

            // 2. Global Default Action & Smart Mode Behavior Card
            item {
                val defaultAction = ActionType.fromKey(config.settings.defaultAction)
                val smartFallback = config.settings.smartDetect.fallbackAction
                val decoyCacheMs = config.settings.smartDetect.decoyCacheMs

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr("全局拦截与伪装策略", "Global Interception & Spoof Policy"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = tr("没单独设过规矩的应用，默认怎么管：", "Default rule for unconfigured apps:"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tr(
                                "如果你懒得一个个去点，可以把全局默认直接设成「智能」，新装的应用也会自动按这个来。",
                                "Set the global default to Smart if you want all newly installed apps protected automatically."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ActionPillSelector(
                            selectedAction = defaultAction,
                            onActionSelected = { newDefault ->
                                viewModel.setDefaultAction(newDefault)
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = tr("「智能」模式抓到偷看时怎么伺候：", "When Smart mode catches a sneaky read:"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tr(
                                "当挂在「智能」档的应用刚打开或躲在后台伸手时，你是想直接回它一个空剪贴板，还是顺手塞条假词糊弄它？",
                                "When an app in Smart mode reads the clipboard on launch or in the background, should ClipBait return empty or feed a decoy?"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                modifier = Modifier.weight(1f),
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                selected = smartFallback != "spoof",
                                onClick = {
                                    view.tickHaptic(hapticEnabled)
                                    viewModel.setSmartFallbackAction("block")
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Block,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tr("阻断 · 回空值", "Block (Empty)"),
                                        maxLines = 1
                                    )
                                }
                            )
                            SegmentedButton(
                                modifier = Modifier.weight(1f),
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                selected = smartFallback == "spoof",
                                onClick = {
                                    view.tickHaptic(hapticEnabled)
                                    viewModel.setSmartFallbackAction("spoof")
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Shuffle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tr("伪装 · 塞假词", "Spoof (Decoy)"),
                                        maxLines = 1
                                    )
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        val secStr = String.format(java.util.Locale.US, "%.1f", resumeWindowMs / 1000f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("刚切前台多长时间内算偷看", "Foreground resume guard window"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isEn) "${secStr}s" else "$secStr 秒",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tr(
                                "很多应用刚从后台切回来头两秒就会顺手摸兜。前 $secStr 秒内来读一律按偷看处理；过了这阵子你自己点粘贴，正常放行。",
                                "Reads within the first ${secStr}s after an app enters the foreground without a paste gesture are treated as sneaky reads."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = resumeWindowMs,
                            onValueChange = { resumeWindowMs = it },
                            onValueChangeFinished = {
                                view.tickHaptic(hapticEnabled)
                                viewModel.setResumeWindowMs(resumeWindowMs.toLong())
                            },
                            valueRange = 500f..4000f,
                            steps = 6
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tr("假剪贴板防连读穿帮保鲜期", "Anti-burst decoy cache TTL"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tr(
                                "有些鸡贼的应用会在一秒内连读两三次剪贴板，看每次读到的内容一不一样。保鲜期内对同一个应用返回同一句假话，防它察觉：",
                                "Some apps read the clipboard multiple times in a second to check if the result changes. Caching the decoy per app prevents detection:"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val cacheOptions = listOf(
                            0L to tr("每次换新", "Always"),
                            1500L to tr("1.5秒", "1.5s"),
                            5000L to tr("5秒", "5s"),
                            30000L to tr("30秒", "30s")
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            cacheOptions.forEachIndexed { index, (ms, label) ->
                                SegmentedButton(
                                    selected = decoyCacheMs == ms,
                                    onClick = {
                                        view.tickHaptic(hapticEnabled)
                                        viewModel.setDecoyCacheMs(ms)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = cacheOptions.size),
                                    label = { Text(label, maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. UI, Language, Haptics, Confetti & Live Toast Options Card
            item {
                val currentLang = config.settings.language
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = tr("界面手感与花活设置", "UI, Language & Haptics"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = tr("界面语言 / Language", "Language / 界面语言"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val langOptions = listOf(
                            "system" to tr("跟随系统", "System"),
                            "zh" to "中文",
                            "en" to "English"
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            langOptions.forEachIndexed { index, (code, label) ->
                                SegmentedButton(
                                    modifier = Modifier.weight(1f),
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = langOptions.size),
                                    selected = currentLang == code,
                                    onClick = {
                                        view.tickHaptic(hapticEnabled)
                                        viewModel.setLanguage(code)
                                    },
                                    label = { Text(label, maxLines = 1) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        SettingToggleRow(
                            icon = Icons.Outlined.Vibration,
                            title = tr("点击与翻页触感震动", "Haptic Feedback"),
                            subtitle = tr(
                                "切换底栏标签、点选四档胶囊和开关时给指尖一点清脆震感。",
                                "Crisp tactile ticks when switching tabs, pills, and toggles."
                            ),
                            checked = hapticEnabled,
                            onCheckedChange = {
                                view.tickHaptic(enabled = true)
                                viewModel.setHapticEnabled(it)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        SettingToggleRow(
                            icon = Icons.Outlined.Celebration,
                            title = tr("光荣榜与二级菜单彩带礼花", "Celebratory Confetti"),
                            subtitle = tr(
                                "点「放礼花」或点开前三名详情时，发射双管彩带礼花并配上一串震动反馈。",
                                "Launch twin-cannon confetti with rumble haptics on the Hall of Fame and detail sheets."
                            ),
                            checked = config.settings.auditLog.confettiEnabled,
                            onCheckedChange = {
                                view.tickHaptic(hapticEnabled)
                                viewModel.setConfettiEnabled(it)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        SettingToggleRow(
                            icon = Icons.Outlined.NotificationsActive,
                            title = tr("抓现行时弹 Toast 当场点名", "Live Intercept Toast"),
                            subtitle = tr(
                                "平时用手机时，只要有应用在后台或刚打开时摸剪贴板被拦下，就弹个小提示告诉你。",
                                "Show a brief Toast notification whenever an app is caught reading your clipboard."
                            ),
                            checked = config.settings.auditLog.showInterceptToast,
                            onCheckedChange = {
                                view.tickHaptic(hapticEnabled)
                                viewModel.setShowInterceptToast(it)
                            }
                        )
                    }
                }
            }

            // 4. Audit Log, Favorite Leaderboard & Data Cleanup
            item {
                val retentionDays = config.settings.auditLog.retentionDays
                val dayOptions = listOf(3, 7, 15, 30, 90)
                val favoriteEnabled = config.settings.auditLog.favoriteLeaderboardEnabled

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = tr("光荣榜记账与排行榜设置", "Hall of Fame & Standings"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("启用「最爱读取积分榜」", "Enable 'Most Obsessed Standings'"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tr(
                                        "默认关闭。开启后光荣榜可切换双榜，结合应用真实使用时长做深入分析，揪出「明明没用几分钟、手却伸得最勤快」的选手。",
                                        "Off by default. Combines clipboard intercept counts with foreground screen time to expose apps that barely get used yet snoop relentlessly."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = favoriteEnabled,
                                onCheckedChange = {
                                    view.tickHaptic(hapticEnabled)
                                    viewModel.setFavoriteLeaderboardEnabled(it)
                                }
                            )
                        }

                        if (favoriteEnabled && !viewModel.hasUsageStatsPermission()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    } catch (_: Exception) {
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    tr(
                                        "去授权「使用情况访问权限」（用于读取应用使用时长）",
                                        "Grant Usage Access Permission (for foreground time analysis)"
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("把抓到的记录写进光荣榜", "Record intercepts in Hall of Fame"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tr(
                                        "关掉以后照样拦剪贴板，只是不再往光荣榜里记账了。",
                                        "When disabled, ClipBait still blocks/spoofs clipboard reads without logging them."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = config.settings.auditLog.enabled,
                                onCheckedChange = {
                                    view.tickHaptic(hapticEnabled)
                                    viewModel.setAuditLogEnabled(it)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("在光荣榜里显示进程细节", "Show subprocess & timing details"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tr(
                                        "顺手扒一下到底是主进程还是 `:push` 这种后台推送子进程在伸手，以及一天里的活跃时段。",
                                        "Reveals whether reads came from the main UI process or a background `:push` daemon, plus peak hours."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = config.settings.auditLog.deepAnalysis,
                                onCheckedChange = {
                                    view.tickHaptic(hapticEnabled)
                                    viewModel.setDeepAnalysisEnabled(it)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = tr("旧账保留多久（超期自动清）：", "Auto-prune logs older than:"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            dayOptions.forEachIndexed { index, days ->
                                SegmentedButton(
                                    selected = retentionDays == days,
                                    onClick = {
                                        view.tickHaptic(hapticEnabled)
                                        viewModel.setRetentionDays(days)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = dayOptions.size),
                                    label = { Text(if (isEn) "${days}d" else "${days}天", maxLines = 1) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                view.tickHaptic(hapticEnabled, confirm = true)
                                viewModel.clearAuditLogs()
                                Toast.makeText(
                                    context,
                                    if (isEn) "Hall of Fame logs cleared" else "光荣榜已经清空了",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tr("现在就把光荣榜清空", "Clear Hall of Fame Now"))
                        }
                    }
                }
            }

            // 5. About Card (wired to AppInfo.kt with pgntgz's real info)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tr("关于 ${AppInfo.APP_NAME}", "About ${AppInfo.APP_NAME}"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "v${AppInfo.VERSION_NAME} · ${AppInfo.LICENSE}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = tr(
                                "直接在 Android 系统服务层接管剪贴板。不用挨个去勾应用作用域，也不用为了加减规则反复重启手机。",
                                "Intercepts clipboard reads directly inside Android's system_server. Scope only System Framework—no per-app hooking or reboots required when changing rules."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(6.dp))

                        AboutLinkRow(
                            icon = Icons.Outlined.Info,
                            label = tr("作者", "Author"),
                            value = AppInfo.AUTHOR,
                            onClick = { openUrlSafely(context, AppInfo.AUTHOR_URL) }
                        )
                        AboutLinkRow(
                            icon = Icons.Outlined.Code,
                            label = tr("源码仓库", "Source Code"),
                            value = "GitHub · pgntgz/ClipBait",
                            onClick = { openUrlSafely(context, AppInfo.SOURCE_URL) }
                        )
                        AboutLinkRow(
                            icon = Icons.Outlined.BugReport,
                            label = tr("问题反馈", "Issue Tracker"),
                            value = tr("提交 Issue 或建议", "GitHub Issues & Feedback"),
                            onClick = { openUrlSafely(context, AppInfo.ISSUES_URL) }
                        )
                        AboutLinkRow(
                            icon = Icons.Outlined.Forum,
                            label = "Matrix",
                            value = AppInfo.MATRIX_HANDLE,
                            onClick = { openUrlSafely(context, AppInfo.MATRIX_URL) }
                        )
                        AboutLinkRow(
                            icon = Icons.Outlined.Email,
                            label = tr("联系邮箱", "Email"),
                            value = AppInfo.EMAIL,
                            onClick = { openUrlSafely(context, "mailto:${AppInfo.EMAIL}") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp).padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun openUrlSafely(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, url, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .let { mod -> if (onClick != null) mod.clickable { onClick() } else mod }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
