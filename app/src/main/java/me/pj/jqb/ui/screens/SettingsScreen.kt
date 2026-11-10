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
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
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
import me.pj.jqb.ui.components.PosterGenerationResult
import me.pj.jqb.ui.components.tickHaptic
import me.pj.jqb.ui.isEnglishLocale
import me.pj.jqb.ui.tr
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
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
                if (isEn) "Notification permission denied; switched to Toast" else "未授予通知权限，已切换为 Toast",
                Toast.LENGTH_SHORT
            ).show()
            viewModel.setInterceptAlertMode("toast")
        }
    }

    var resumeWindowMs by remember(config.settings.smartDetect.resumeWindowMs) {
        mutableFloatStateOf(config.settings.smartDetect.resumeWindowMs.toFloat())
    }
    var posterPreview by remember { mutableStateOf<PosterGenerationResult?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Module Status Card
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
                    HookState.ACTIVE -> tr("正在运行", "Active")
                    HookState.CHECKING -> tr("正在检测...", "Checking...")
                    HookState.NEEDS_REBOOT -> tr("待重启生效", "Reboot Required")
                    HookState.INACTIVE -> tr("未激活", "Inactive")
                }
                val bodyText = when (hookState) {
                    HookState.ACTIVE -> tr(
                        "已挂载到系统服务，修改规则实时生效。",
                        "Hooked into system_server. Rule changes take effect immediately."
                    )
                    HookState.CHECKING -> tr(
                        "正在检测系统服务连接状态...",
                        "Checking connection to system_server..."
                    )
                    HookState.NEEDS_REBOOT -> tr(
                        "请确认勾选「系统框架 (android)」并重启手机。",
                        "Ensure 'System Framework (android)' is checked in scope and reboot."
                    )
                    HookState.INACTIVE -> tr(
                        "请在 LSPosed 中启用模块，仅勾选「系统框架 (android)」并重启。",
                        "Enable module in LSPosed with only 'System Framework' checked, then reboot."
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
                            modifier = Modifier.size(28.dp),
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
                            Spacer(modifier = Modifier.height(2.dp))
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
                                contentDescription = tr("刷新状态", "Refresh Status"),
                                tint = contentColor
                            )
                        }
                    }
                }
            }

            // 2. Interception Group
            item {
                SectionHeader(tr("拦截", "Interception"))
                val defaultAction = ActionType.fromKey(config.settings.defaultAction)
                val smartFallback = config.settings.smartDetect.fallbackAction
                val decoyCacheMs = config.settings.smartDetect.decoyCacheMs

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = tr("全局默认", "Global Default"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = tr("在「应用规则」页修改", "Change on the App Rules tab"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = defaultAction.label(isEn),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = tr("智能模式拦截方式", "Smart Mode Fallback"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tr(
                                    "智能模式判定为非粘贴读取时的处理方式。",
                                    "Action taken when Smart mode detects a non-paste clipboard read."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        Text(tr("返回空值", "Return Empty"), maxLines = 1)
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
                                        Text(tr("返回伪装词", "Return Decoy"), maxLines = 1)
                                    }
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        val secStr = String.format(java.util.Locale.US, "%.1f", resumeWindowMs / 1000f)
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr("切回前台保护时长", "Foreground Resume Window"),
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
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tr(
                                    "应用切回前台后 $secStr 秒内的无粘贴读取均会拦截。",
                                    "Non-paste reads within ${secStr}s of an app entering foreground are intercepted."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
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
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = tr("伪装内容缓存时长", "Decoy Cache Duration"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tr(
                                    "短时间内对同一应用返回相同伪装词，避免被多次连读识别。",
                                    "Returns the same decoy to the same app within the window to prevent burst-read detection."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val cacheOptions = listOf(
                                0L to tr("不缓存", "None"),
                                1500L to tr("1.5 秒", "1.5s"),
                                5000L to tr("5 秒", "5s"),
                                30000L to tr("30 秒", "30s")
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
            }

            // 3. Alerts Group
            item {
                SectionHeader(tr("提醒", "Alerts"))
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
                                    imageVector = Icons.Outlined.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tr("拦截提醒", "Intercept Alert"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (currentAlertMode != "off") {
                                TextButton(
                                    onClick = {
                                        view.tickHaptic(hapticEnabled, confirm = true)
                                        if (currentAlertMode == "dialog") {
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
                                            val title = if (isEn) "ClipBait Intercept Alert" else "ClipBait 拦截提醒"
                                            val body = if (isEn) {
                                                "App: WeChat (com.tencent.mm)\nReason: Foreground non-paste read\nAction: Spoofed\nDecoy: \"$previewDecoy\""
                                            } else {
                                                "应用：微信 (com.tencent.mm)\n原因：前台无粘贴读取\n结果：已伪装\n返回内容：「$previewDecoy」"
                                            }
                                            try {
                                                val dlg = android.app.AlertDialog.Builder(context, themeRes)
                                                    .setTitle(title)
                                                    .setMessage(body)
                                                    .setCancelable(true)
                                                    .setPositiveButton(if (isEn) "OK" else "知道了") { d, _ -> d.dismiss() }
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
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text(
                                        text = tr("预览提醒", "Preview"),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        val alertModeDesc = when (currentAlertMode) {
                            "toast" -> tr("拦截时在屏幕底部显示 Toast 提示。", "Shows a bottom Toast when a read is intercepted.")
                            "notification" -> tr("拦截时发送状态栏通知。", "Posts a status-bar notification when a read is intercepted.")
                            "dialog" -> tr("拦截时弹出原生系统对话框（4.5 秒自动关闭）。", "Shows a native system alert dialog on intercept (auto-closes in 4.5s).")
                            else -> tr("拦截时不弹出提醒。", "Intercept silently without alerts.")
                        }
                        Text(
                            text = alertModeDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val alertOptions = listOf(
                            "off" to tr("关闭", "Off"),
                            "toast" to "Toast",
                            "notification" to tr("通知", "Notify"),
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
                    }
                }
            }

            // 4. Hall of Fame & Standings Group (Preserving "积分榜" F1 championship comedy)
            item {
                SectionHeader(tr("光荣榜与积分榜", "Hall of Fame & Standings"))
                val retentionDays = config.settings.auditLog.retentionDays
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
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingSwitchRow(
                            title = tr("记录拦截日志", "Record Intercept Logs"),
                            subtitle = tr("关闭后仍正常拦截，仅不再写入光荣榜。", "Intercepts continue when disabled, without logging."),
                            checked = config.settings.auditLog.enabled,
                            onCheckedChange = {
                                view.tickHaptic(hapticEnabled)
                                viewModel.setAuditLogEnabled(it)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        SettingSwitchRow(
                            title = tr("显示进程信息", "Show Process Details"),
                            subtitle = tr("在记录中显示触发读取的具体进程名与活跃时段。", "Displays calling process name and active hours."),
                            checked = config.settings.auditLog.deepAnalysis,
                            onCheckedChange = {
                                view.tickHaptic(hapticEnabled)
                                viewModel.setDeepAnalysisEnabled(it)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        SettingSwitchRow(
                            title = tr("启用「最爱读取积分榜」", "Enable 'Most Obsessed Standings'"),
                            subtitle = tr(
                                "结合应用使用时长与后台读取次数计算积分榜排名。",
                                "Combines foreground screen time and background reads to calculate standings points."
                            ),
                            checked = favoriteEnabled,
                            onCheckedChange = {
                                view.tickHaptic(hapticEnabled)
                                viewModel.setFavoriteLeaderboardEnabled(it)
                            }
                        )

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
                                Text(tr("授予使用情况访问权限", "Grant Usage Access Permission"))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = tr("记录保留时长", "Log Retention"),
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
                                    SettingSwitchRow(
                                        title = tr("定期生成积分榜图片", "Periodic Standings Poster"),
                                        subtitle = tr(
                                            "清理旧记录前自动生成月度或年度积分榜图并保存至相册。",
                                            "Automatically saves a Monthly or Yearly Standings image to Gallery before pruning."
                                        ),
                                        checked = periodicPosterEnabled,
                                        onCheckedChange = {
                                            view.tickHaptic(hapticEnabled)
                                            viewModel.setPeriodicPosterEnabled(it)
                                        }
                                    )

                                    if (periodicPosterEnabled) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = tr("生成周期", "Interval"),
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
                                            "当前为永久保留，可随时手动生成积分榜图片：",
                                            "Retention is set to Forever. Generate a Standings poster anytime:"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                    if (isEn) "Saved to Pictures/ClipBait" else "已保存「${res.titleText}」至相册",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = tr("生成${curMonth}月份积分榜图", "Monthly Poster"),
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
                                                    if (isEn) "Saved to Pictures/ClipBait" else "已保存「${res.titleText}」至相册",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = tr("生成${curYear}年积分榜图", "Yearly Poster"),
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
                                view.tickHaptic(hapticEnabled)
                                showClearConfirm = true
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
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("清空记录", "Clear Logs"))
                        }
                    }
                }
            }

            // 5. Appearance & Haptics Group
            item {
                SectionHeader(tr("界面", "Interface"))
                val currentLang = config.settings.language
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
                                imageVector = Icons.Outlined.Language,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr("语言", "Language"),
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
                            title = tr("触感反馈", "Haptic Feedback"),
                            subtitle = tr("操作开关与切换标签时提供震动反馈。", "Vibrate when toggling switches and tabs."),
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
                            title = tr("礼花动效", "Confetti Effect"),
                            subtitle = tr("在光荣榜与详情页启用彩带动画。", "Enable confetti animation on Hall of Fame and details."),
                            checked = config.settings.auditLog.confettiEnabled,
                            onCheckedChange = {
                                view.tickHaptic(hapticEnabled)
                                viewModel.setConfettiEnabled(it)
                            }
                        )
                    }
                }
            }

            // 6. About Group
            item {
                SectionHeader(tr("关于", "About"))
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
                                    text = AppInfo.APP_NAME,
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = tr(
                                "在系统服务层接管剪贴板，仅需勾选系统框架。",
                                "Intercepts clipboard reads in system_server. Scope only System Framework."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        val updateCheckMode = if (config.settings.updateCheckMode == "manual" || config.settings.updateCheckMode == "off") "manual" else "auto"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("检查更新", "Update Check"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            FilledTonalButton(
                                onClick = {
                                    view.tickHaptic(hapticEnabled, confirm = true)
                                    viewModel.checkForUpdatesNow { res ->
                                        res.onSuccess { info ->
                                            val msg = if (info.hasUpdate) {
                                                if (isEn) "New version found: v${info.remoteVersionName}" else "发现新版本：v${info.remoteVersionName}"
                                            } else {
                                                if (isEn) "Already up to date (v${AppInfo.VERSION_NAME})" else "已是最新版本 (v${AppInfo.VERSION_NAME})"
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
                                        tr("立即检查", "Check Now")
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val updateModeOptions = listOf(
                            "auto" to tr("自动检查", "Auto"),
                            "manual" to tr("仅手动", "Manual")
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

                        if (updateInfo != null) {
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
                            value = tr("GitHub Issues", "GitHub Issues"),
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

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(tr("清空光荣榜记录？", "Clear Hall of Fame logs?")) },
            text = {
                Text(
                    tr(
                        "清空后所有历史拦截记录与积分榜统计将被移除。",
                        "All historical intercept records and standings statistics will be removed."
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.tickHaptic(hapticEnabled, confirm = true)
                        viewModel.clearAuditLogs()
                        showClearConfirm = false
                        Toast.makeText(
                            context,
                            if (isEn) "Logs cleared" else "记录已清空",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(tr("清空", "Clear"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(tr("取消", "Cancel"))
                }
            }
        )
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
                            "已保存至相册 Pictures/ClipBait/${res.fileName}。",
                            "Saved to Pictures/ClipBait/${res.fileName}."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        if (isEn) "Share Standings Poster" else "分享积分榜图"
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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingSwitchRow(
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
