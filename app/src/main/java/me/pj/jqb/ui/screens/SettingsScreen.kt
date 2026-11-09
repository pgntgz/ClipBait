package me.pj.jqb.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import me.pj.jqb.AppInfo
import me.pj.jqb.HookState
import me.pj.jqb.model.ActionType
import me.pj.jqb.ui.MainViewModel
import me.pj.jqb.ui.components.ActionPillSelector
import me.pj.jqb.ui.components.PosterGenerationResult
import me.pj.jqb.ui.components.tickHaptic
import me.pj.jqb.ui.isEnglishLocale
import me.pj.jqb.ui.tr
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val hookState by viewModel.hookState.collectAsState()
    val hasUsagePermission by viewModel.hasUsagePermissionState.collectAsState()
    val config by viewModel.configState.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val isEn = isEnglishLocale()

    val hapticEnabled = config.settings.auditLog.hapticEnabled
    val currentAlertMode = config.settings.auditLog.effectiveAlertMode()

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setInterceptAlertMode("notification")
        } else {
            Toast.makeText(
                context,
                if (isEn) "Notification permission denied; switched to Toast mode" else "未授予通知权限，已为你切回 Toast 提醒",
                Toast.LENGTH_SHORT
            ).show()
            viewModel.setInterceptAlertMode("toast")
        }
    }

    var resumeWindowMs by remember(config.settings.smartDetect.resumeWindowMs) {
        mutableFloatStateOf(config.settings.smartDetect.resumeWindowMs.toFloat())
    }
    var posterPreview by remember { mutableStateOf<PosterGenerationResult?>(null) }

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

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = tr("抓现行时当场点名提醒", "Real-Time Intercept Alert"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val alertModeDesc = when (currentAlertMode) {
                            "toast" -> tr(
                                "已开启「弹 Toast」：每当有应用偷读剪贴板被拦下或塞假词，屏幕底部弹出一条轻量气泡提示。",
                                "Toast active: Shows a brief bottom pill toast whenever an app is blocked or fed a decoy."
                            )
                            "notification" -> tr(
                                "已开启「发通知」：抓到偷读时在状态栏弹出横幅通知，展示伸手时机与投喂的假词内容。",
                                "Notification active: Posts a status-bar notification with trigger scenario and decoy preview."
                            )
                            "dialog" -> tr(
                                "已开启「系统弹窗」：直接由系统服务弹出原生 AOSP 样式提示框当场处刑（4.5秒后自动收起，点外部随时关）。",
                                "System Dialog active: Pops up a native AOSP-style system alert dialog right over the offending app (auto-closes in 4.5s)."
                            )
                            else -> tr(
                                "默认安静拦截不打扰。你也可以选抓现行时弹 Toast、发状态栏通知、或者直接弹系统样式提示框：",
                                "Silent by default. Optionally show a Toast, status-bar notification, or native system alert dialog:"
                            )
                        }
                        Text(
                            text = alertModeDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val alertOptions = listOf(
                            "off" to tr("不提醒", "Off"),
                            "toast" to tr("弹 Toast", "Toast"),
                            "notification" to tr("发通知", "Notify"),
                            "dialog" to tr("系统弹窗", "Dialog")
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            alertOptions.forEachIndexed { index, (modeKey, label) ->
                                SegmentedButton(
                                    modifier = Modifier.weight(1f),
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = alertOptions.size),
                                    selected = currentAlertMode == modeKey,
                                    onClick = {
                                        view.tickHaptic(hapticEnabled)
                                        if (modeKey == "notification" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (!granted) {
                                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                return@SegmentedButton
                                            }
                                        }
                                        viewModel.setInterceptAlertMode(modeKey)
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                )
                            }
                        }

                        if (currentAlertMode != "off") {
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    view.tickHaptic(hapticEnabled, confirm = true)
                                    if (currentAlertMode == "dialog") {
                                        // Show instant native AOSP-style AlertDialog preview right on the Activity context
                                        // so the user can see the exact system style even before rebooting!
                                        val sampleDecoy = config.decoys.firstOrNull()?.pool?.randomOrNull()
                                            ?: "一氧化二氢：一种无色无味但致死率高达100%的剧毒工业溶剂。"
                                        val previewDecoy = if (sampleDecoy.length > 60) sampleDecoy.take(60) + "…" else sampleDecoy
                                        val nightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                                            Configuration.UI_MODE_NIGHT_YES
                                        val themeRes = if (nightMode) {
                                            android.R.style.Theme_DeviceDefault_Dialog_Alert
                                        } else {
                                            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                                        }
                                        val title = if (isEn) "🛡️ ClipBait Intercept Alert" else "🛡️ ClipBait 抓现行系统提示"
                                        val body = if (isEn) {
                                            "App: WeChat (com.tencent.mm)\nBehavior: Read in foreground (no paste)\nAction: Spoofed with decoy phrase\nDecoy: \"$previewDecoy\""
                                        } else {
                                            "伸手应用：微信 (com.tencent.mm)\n触发时机：在前台读\n处置结果：已塞入伪装假词糊弄\n投喂内容：「$previewDecoy」"
                                        }
                                        try {
                                            val dlg = android.app.AlertDialog.Builder(context, themeRes)
                                                .setTitle(title)
                                                .setMessage(body)
                                                .setCancelable(true)
                                                .setPositiveButton(if (isEn) "Got it" else "知道了") { d, _ -> d.dismiss() }
                                                .create()
                                            dlg.setCanceledOnTouchOutside(true)
                                            dlg.show()
                                        } catch (_: Throwable) {
                                            viewModel.previewInterceptAlert(currentAlertMode)
                                        }
                                    } else {
                                        viewModel.previewInterceptAlert(currentAlertMode)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tr("点我试一下提醒效果", "Test Current Alert Style"),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // 4. Audit Log, Favorite Leaderboard & Data Cleanup
            item {
                val retentionDays = config.settings.auditLog.retentionDays
                // 0 = 永久保留 (Forever)
                val dayOptions = listOf(3, 7, 15, 30, 90, 0)
                val favoriteEnabled = config.settings.auditLog.favoriteLeaderboardEnabled
                val periodicPosterEnabled = config.settings.auditLog.periodicPosterEnabled
                val posterIntervalDays = config.settings.auditLog.posterIntervalDays
                val posterPeriodStyle = config.settings.auditLog.posterPeriodStyle
                val cal = remember { Calendar.getInstance() }
                val curYear = cal.get(Calendar.YEAR)
                val curMonth = cal.get(Calendar.MONTH) + 1

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

                        if (favoriteEnabled && !hasUsagePermission) {
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
                            text = tr("旧账保留多久（超期自动清，或永久保留）：", "Log retention & cleanup cycle:"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            dayOptions.forEachIndexed { index, days ->
                                val label = when {
                                    days <= 0 -> tr("永久", "Forever")
                                    isEn -> "${days}d"
                                    else -> "${days}天"
                                }
                                SegmentedButton(
                                    selected = retentionDays == days,
                                    onClick = {
                                        view.tickHaptic(hapticEnabled)
                                        viewModel.setRetentionDays(days)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = dayOptions.size),
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (retentionDays > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tr(
                                                    "定期生成并推送「${curMonth}月份 / ${curYear}年」积分榜图片",
                                                    "Send periodic Monthly / Yearly Standings Poster"
                                                ),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = tr(
                                                    "勾选后，在清空旧账周期到来前自动为你画好一张高分率「${curYear}年${curMonth}月」或「${curYear}年度」积分榜战报图，存入相册并发通知提醒。",
                                                    "Automatically renders a high-res Monthly or Yearly Standings poster image before pruning old logs, saves it to Gallery, and notifies you."
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Switch(
                                            checked = periodicPosterEnabled,
                                            onCheckedChange = {
                                                view.tickHaptic(hapticEnabled)
                                                viewModel.setPeriodicPosterEnabled(it)
                                            }
                                        )
                                    }

                                    if (periodicPosterEnabled) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = tr("每隔多少天给你发一张积分榜图：", "Send poster every:"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        val intervalOptions = listOf(7, 15, 30, 90, 365)
                                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                            intervalOptions.forEachIndexed { idx, d ->
                                                val intervalLabel = when {
                                                    d == 365 -> tr("365天", "1yr")
                                                    isEn -> "${d}d"
                                                    else -> "${d}天"
                                                }
                                                SegmentedButton(
                                                    selected = posterIntervalDays == d,
                                                    onClick = {
                                                        view.tickHaptic(hapticEnabled)
                                                        viewModel.setPosterIntervalDays(d)
                                                    },
                                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = intervalOptions.size),
                                                    label = {
                                                        Text(
                                                            text = intervalLabel,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        val styleOptions = listOf(
                                            "month" to tr("${curMonth}月份积分榜", "Monthly (${curYear}-${curMonth})"),
                                            "year" to tr("${curYear}年积分榜", "Yearly ($curYear)")
                                        )
                                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                            styleOptions.forEachIndexed { idx, (styleKey, styleLabel) ->
                                                SegmentedButton(
                                                    selected = posterPeriodStyle == styleKey,
                                                    onClick = {
                                                        view.tickHaptic(hapticEnabled)
                                                        viewModel.setPosterPeriodStyle(styleKey)
                                                    },
                                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = styleOptions.size),
                                                    label = {
                                                        Text(
                                                            text = styleLabel,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                } else {
                                    Text(
                                        text = tr(
                                            "当前已设为「永久保留」，旧账不会自动清空。你随时可以手动生成本月或本年的积分榜战报图：",
                                            "Logs are set to Forever retention. You can generate a Monthly or Yearly Standings poster anytime:"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            view.tickHaptic(hapticEnabled, confirm = true)
                                            viewModel.generatePosterNow(styleOverride = "month") { res ->
                                                posterPreview = res
                                                Toast.makeText(
                                                    context,
                                                    if (isEn) "Saved to Pictures/ClipBait & notified" else "已生成「${res.titleText}」存入相册并推送通知",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = tr("生成${curMonth}月份积分榜图", "Make ${curMonth}M Poster"),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            view.tickHaptic(hapticEnabled, confirm = true)
                                            viewModel.generatePosterNow(styleOverride = "year") { res ->
                                                posterPreview = res
                                                Toast.makeText(
                                                    context,
                                                    if (isEn) "Saved to Pictures/ClipBait & notified" else "已生成「${res.titleText}」存入相册并推送通知",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = tr("生成${curYear}年积分榜图", "Make ${curYear}Y Poster"),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    }
                                }
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
                        Spacer(modifier = Modifier.height(10.dp))

                        val updateCheckMode = config.settings.updateCheckMode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("版本更新检查", "Update Check"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (updateCheckMode != "off") {
                                FilledTonalButton(
                                    onClick = {
                                        view.tickHaptic(hapticEnabled, confirm = true)
                                        viewModel.checkForUpdatesNow { res ->
                                            res.onSuccess { info ->
                                                val msg = if (info.hasUpdate) {
                                                    if (isEn) "New version found: v${info.remoteVersionName}" else "发现新版本：v${info.remoteVersionName}"
                                                } else {
                                                    if (isEn) "Already up to date (v${AppInfo.VERSION_NAME})" else "当前已是最新版本 (v${AppInfo.VERSION_NAME})"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }.onFailure { e ->
                                                Toast.makeText(
                                                    context,
                                                    if (isEn) "Update check failed: ${e.message}" else "检查更新失败：${e.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    enabled = !isCheckingUpdate,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = if (isCheckingUpdate) {
                                            tr("检查中...", "Checking...")
                                        } else {
                                            tr("检查更新", "Check Now")
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val updateModeOptions = listOf(
                            "auto" to tr("自动检查", "Auto"),
                            "manual" to tr("仅手动", "Manual"),
                            "off" to tr("关闭", "Off")
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            updateModeOptions.forEachIndexed { idx, (modeKey, modeLabel) ->
                                SegmentedButton(
                                    selected = updateCheckMode == modeKey,
                                    onClick = {
                                        view.tickHaptic(hapticEnabled)
                                        viewModel.setUpdateCheckMode(modeKey)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = updateModeOptions.size),
                                    label = {
                                        Text(
                                            text = modeLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    }
                                )
                            }
                        }

                        if (updateCheckMode != "off" && updateInfo != null) {
                            val info = updateInfo!!
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (info.hasUpdate) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (info.hasUpdate) {
                                                tr("发现新版本 v${info.remoteVersionName}", "New version v${info.remoteVersionName} available")
                                            } else {
                                                tr("已是最新版本 (v${AppInfo.VERSION_NAME})", "Up to date (v${AppInfo.VERSION_NAME})")
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (info.hasUpdate) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        if (info.hasUpdate) {
                                            FilledTonalButton(
                                                onClick = {
                                                    openUrlSafely(context, info.apkDownloadUrl ?: info.htmlUrl)
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text(
                                                    text = tr("下载更新", "Download"),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                    if (info.hasUpdate && info.releaseNotes.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = info.releaseNotes.take(260),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }

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

    posterPreview?.let { res ->
        AlertDialog(
            onDismissRequest = { posterPreview = null },
            title = {
                Text(
                    text = res.titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        bitmap = res.bitmap.asImageBitmap(),
                        contentDescription = res.titleText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                    Text(
                        text = tr(
                            "已保存至系统相册 Pictures/ClipBait/${res.fileName}，并已向状态栏发送图片通知。",
                            "Saved to Pictures/ClipBait/${res.fileName} and posted to status bar."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                if (res.savedUri != null) {
                    FilledTonalButton(
                        onClick = {
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, res.savedUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        shareIntent,
                                        if (isEn) "Share Standings Poster" else "分享积分榜战报图"
                                    )
                                )
                            } catch (_: Exception) {
                            }
                        }
                    ) {
                        Text(tr("分享图片", "Share"))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { posterPreview = null }) {
                    Text(tr("关闭", "Close"))
                }
            }
        )
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
