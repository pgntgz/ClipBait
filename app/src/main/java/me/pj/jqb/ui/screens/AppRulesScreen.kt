package me.pj.jqb.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.pj.jqb.model.ActionType
import me.pj.jqb.ui.AppItem
import me.pj.jqb.ui.MainViewModel
import me.pj.jqb.ui.SmartDetectionUiItem
import me.pj.jqb.ui.components.ActionPillSelector
import me.pj.jqb.ui.components.AppIcon
import me.pj.jqb.ui.components.tickHaptic
import me.pj.jqb.ui.isEnglishLocale
import me.pj.jqb.ui.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRulesScreen(viewModel: MainViewModel) {
    val view = LocalView.current
    val apps by viewModel.installedApps.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()
    val smartDetectedApps by viewModel.smartDetectedApps.collectAsState()
    val isScanningSmart by viewModel.isScanningSmart.collectAsState()
    val config by viewModel.configState.collectAsState()
    val hapticEnabled = config.settings.auditLog.hapticEnabled
    val isEn = isEnglishLocale()

    var searchQuery by remember { mutableStateOf("") }
    var filterConfiguredOnly by remember { mutableStateOf(false) }
    var hideSystemApps by remember { mutableStateOf(true) }
    var showGuide by remember { mutableStateOf(false) }
    var showSmartSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val filteredApps = remember(apps, searchQuery, filterConfiguredOnly, hideSystemApps) {
        apps.filter { item ->
            val matchQuery = searchQuery.isBlank() ||
                item.appName.contains(searchQuery, ignoreCase = true) ||
                item.packageName.contains(searchQuery, ignoreCase = true)

            val matchConfigured = !filterConfiguredOnly || item.action != ActionType.DIRECT
            val matchSystem = !hideSystemApps || !item.isSystemApp

            matchQuery && matchConfigured && matchSystem
        }
    }

    if (showSmartSheet) {
        SmartClassifierBottomSheet(
            items = smartDetectedApps,
            isScanning = isScanningSmart,
            onToggleSelect = { pkg ->
                view.tickHaptic(hapticEnabled)
                viewModel.toggleSmartDetectionSelect(pkg)
            },
            onSelectAll = { selectAll ->
                view.tickHaptic(hapticEnabled)
                viewModel.setAllSmartDetectionSelected(selectAll)
            },
            onChangeAction = { pkg, action -> viewModel.changeSmartDetectionAction(pkg, action) },
            onApply = {
                view.tickHaptic(hapticEnabled, confirm = true)
                viewModel.applySmartDetectedRules { count ->
                    showSmartSheet = false
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            if (isEn) {
                                "Applied rules to $count apps (live immediately, no reboot needed)"
                            } else {
                                "已给 $count 个应用套上规则，不用重启直接生效"
                            }
                        )
                    }
                }
            },
            onDismiss = { showSmartSheet = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(tr("应用规则", "App Rules"), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(
                        onClick = {
                            view.tickHaptic(hapticEnabled)
                            showSmartSheet = true
                            viewModel.scanDomesticApps()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoFixHigh,
                            contentDescription = tr("自动挑出嫌疑应用", "Auto-Detect Suspect Apps"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            view.tickHaptic(hapticEnabled)
                            showGuide = !showGuide
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = tr("四档模式有什么区别", "Mode Guide"),
                            tint = if (showGuide) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Animated Mode Guide Card
            AnimatedVisibility(
                visible = showGuide,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = tr("这四档到底有什么区别？", "How the 4 Modes Work"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ActionType.entries.forEach { action ->
                            ModeHelpItem(title = action.label(isEn), desc = action.detail(isEn))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = tr(
                                "小提示：系统已经把你的当前输入法（比如 Fcitx5 / Gboard）自动加进白名单了，用输入法自带的剪贴板粘贴永远不受影响。",
                                "Tip: Your active input method (e.g. Fcitx5 / Gboard) is automatically whitelisted, so keyboard clipboard pasting always works."
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(tr("搜应用名或包名...", "Search app or package name...")) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = tr("清除搜索", "Clear")
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )

            // Filter Chips + Smart Classifier Trigger Chip (Scrollable to prevent cramped text wrapping)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    AssistChip(
                        onClick = {
                            view.tickHaptic(hapticEnabled)
                            showSmartSheet = true
                            viewModel.scanDomesticApps()
                        },
                        label = { Text(tr("一键揪出毒瘤", "Auto-Detect")) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.AutoFixHigh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = null
                    )
                }
                item {
                    val configuredCount = apps.count { it.action != ActionType.DIRECT }
                    FilterChip(
                        selected = filterConfiguredOnly,
                        onClick = {
                            view.tickHaptic(hapticEnabled)
                            filterConfiguredOnly = !filterConfiguredOnly
                        },
                        label = { Text(tr("已设规矩 ($configuredCount)", "Configured ($configuredCount)")) }
                    )
                }
                item {
                    FilterChip(
                        selected = hideSystemApps,
                        onClick = {
                            view.tickHaptic(hapticEnabled)
                            hideSystemApps = !hideSystemApps
                        },
                        label = { Text(tr("不看系统应用", "Hide System")) }
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = tr("没找到对应的应用", "No matching apps found"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName },
                        contentType = { "app_rule_item" }
                    ) { appItem ->
                        AppRuleCard(
                            appItem = appItem,
                            onActionSelected = { newAction ->
                                viewModel.setAppAction(appItem.packageName, newAction)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartClassifierBottomSheet(
    items: List<SmartDetectionUiItem>,
    isScanning: Boolean,
    onToggleSelect: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onChangeAction: (String, ActionType) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedCount = items.count { it.selected }
    val allSelected = items.isNotEmpty() && selectedCount == items.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("自动挑出爱看剪贴板的应用", "Auto-Detect Clipboard Snoopers"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tr(
                            "按常见国内大厂包名和内置广告/统计 SDK 筛出来的名单，你可以挨个微调",
                            "Matched against known package rules and embedded ad/tracking SDK signatures"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (!isScanning && items.isNotEmpty()) {
                    TextButton(onClick = { onSelectAll(!allSelected) }) {
                        Text(if (allSelected) tr("全不选", "None") else tr("全选", "All"))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            if (isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = tr("正在翻看手机里装了哪些熟面孔...", "Scanning installed packages & SDKs..."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("居然一个都没扫出来，你的手机比大多数人都干净", "Zero suspects found—your phone is remarkably clean"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = items,
                        key = { it.packageName }
                    ) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSelect(item.packageName) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.selected) {
                                    MaterialTheme.colorScheme.surfaceContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLowest
                                }
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = item.selected,
                                        onCheckedChange = { onToggleSelect(item.packageName) }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    AppIcon(
                                        packageName = item.packageName,
                                        size = 36.dp,
                                        cornerRadius = 8.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.appName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = item.category,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = item.reason,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (item.selected) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ActionPillSelector(
                                        selectedAction = item.recommendedAction,
                                        onActionSelected = { newAction ->
                                            onChangeAction(item.packageName, newAction)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onApply,
                    enabled = selectedCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(tr("给这 $selectedCount 个应用套上规则", "Apply Rules to $selectedCount Apps"))
                }
            }
        }
    }
}

@Composable
fun AppRuleCard(
    appItem: AppItem,
    onActionSelected: (ActionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEn = isEnglishLocale()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    packageName = appItem.packageName,
                    size = 42.dp,
                    cornerRadius = 10.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = appItem.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (appItem.action != ActionType.DIRECT) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (appItem.action) {
                                    ActionType.SMART -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    ActionType.SPOOF -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                                    ActionType.BLOCK -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                                    else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                }
                            ) {
                                Text(
                                    text = appItem.action.summary(isEn),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = when (appItem.action) {
                                        ActionType.SMART -> MaterialTheme.colorScheme.onPrimaryContainer
                                        ActionType.SPOOF -> MaterialTheme.colorScheme.onTertiaryContainer
                                        ActionType.BLOCK -> MaterialTheme.colorScheme.onErrorContainer
                                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = appItem.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            ActionPillSelector(
                selectedAction = appItem.action,
                onActionSelected = onActionSelected
            )
        }
    }
}

@Composable
fun ModeHelpItem(title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
