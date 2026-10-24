package me.pj.jqb.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.pj.jqb.data.repo.DecoySourceType
import me.pj.jqb.ui.MainViewModel
import me.pj.jqb.ui.components.tickHaptic
import me.pj.jqb.ui.isEnglishLocale
import me.pj.jqb.ui.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val config by viewModel.configState.collectAsState()
    val hapticEnabled = config.settings.auditLog.hapticEnabled
    val isEn = isEnglishLocale()

    var customDecoyInput by remember { mutableStateOf("") }
    var showRawJsonEditor by remember { mutableStateOf(false) }
    var showRandomSourceSheet by remember { mutableStateOf(false) }
    var jsonText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isDirty by remember { mutableStateOf(false) }

    // Only serialize JSON when the raw editor is actually opened so switching to this tab is instant
    LaunchedEffect(config, showRawJsonEditor) {
        if (showRawJsonEditor && !isDirty) {
            jsonText = viewModel.getRawConfigJson()
        }
    }

    if (showRandomSourceSheet) {
        DecoySourceWorkshopSheet(
            hapticEnabled = hapticEnabled,
            onDrawSource = { source, count, onComplete ->
                viewModel.drawDecoysFromSource(source, count) { added ->
                    isDirty = false
                    onComplete(added)
                }
            },
            onResetDefault = {
                viewModel.resetDecoyPoolToDefault()
                isDirty = false
                Toast.makeText(
                    context,
                    if (isEn) "Restored default decoy pool" else "已恢复默认词库",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showRandomSourceSheet = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("伪装词库与配置", "Decoy Pool & Config"), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(
                        onClick = {
                            view.tickHaptic(hapticEnabled)
                            clipboardManager.setText(AnnotatedString(viewModel.getRawConfigJson()))
                            Toast.makeText(
                                context,
                                if (isEn) "Copied full JSON config to clipboard" else "已把完整 JSON 配置复制到剪贴板",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = tr("导出配置", "Copy JSON Config")
                        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Decoys & Multi-Source Workshop Trigger Card
            item {
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("假剪贴板词库", "Decoy Phrase Pool"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tr(
                                        "切到「伪装」模式的应用来摸剪贴板时，就从下面随机抽一句塞给它",
                                        "Apps set to Spoof mode get a random entry from this pool whenever they read the clipboard"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    view.tickHaptic(hapticEnabled)
                                    showRandomSourceSheet = true
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Casino,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tr("随机抽词", "Draw Random"), maxLines = 1, softWrap = false)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Add custom sentence row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customDecoyInput,
                                onValueChange = { customDecoyInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(tr("自己写一句怪话或假口令塞进去...", "Write your own decoy phrase..."))
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    if (customDecoyInput.isNotBlank()) {
                                        view.tickHaptic(hapticEnabled, confirm = true)
                                        viewModel.addCustomDecoyEntry("e_commerce_noise", customDecoyInput)
                                        customDecoyInput = ""
                                    }
                                },
                                enabled = customDecoyInput.isNotBlank(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = tr("添加", "Add"),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(tr("添加", "Add"), maxLines = 1, softWrap = false)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        config.decoys.filter { it.pool.isNotEmpty() }.forEach { decoy ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = tr(
                                            "当前词池（共 ${decoy.pool.size} 条，点 × 删除单条）",
                                            "Active Pool (${decoy.pool.size} entries · tap × to remove)"
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    decoy.pool.take(15).forEach { sample ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "· $sample",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    view.tickHaptic(hapticEnabled)
                                                    viewModel.removeDecoyEntry(decoy.id, sample)
                                                },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Close,
                                                    contentDescription = tr("删除这条", "Remove"),
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }
                                    if (decoy.pool.size > 15) {
                                        Text(
                                            text = tr(
                                                "还有 ${decoy.pool.size - 15} 条没展开，可在下方 JSON 查看全部",
                                                "${decoy.pool.size - 15} more entries in config.json below"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Collapsible JSON Editor Card for backup / manual editing
            item {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    view.tickHaptic(hapticEnabled)
                                    showRawJsonEditor = !showRawJsonEditor
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = tr("直接改配置文件 (config.json)", "Raw Config Editor (config.json)"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = tr(
                                            "换机备份、导入导出、或者手写规则可以用这个",
                                            "Backup, restore, or manually edit rules in JSON"
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showRawJsonEditor) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }

                        AnimatedVisibility(
                            visible = showRawJsonEditor,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                OutlinedTextField(
                                    value = jsonText,
                                    onValueChange = {
                                        jsonText = it
                                        isDirty = true
                                        errorMessage = null
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(340.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                                    )
                                )

                                if (errorMessage != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = tr("JSON 格式不对：$errorMessage", "Invalid JSON: $errorMessage"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            view.tickHaptic(hapticEnabled)
                                            jsonText = viewModel.getRawConfigJson()
                                            isDirty = false
                                            errorMessage = null
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(tr("还原", "Revert"))
                                    }
                                    Button(
                                        onClick = {
                                            view.tickHaptic(hapticEnabled, confirm = true)
                                            viewModel.saveRawConfig(jsonText) { success, err ->
                                                if (success) {
                                                    isDirty = false
                                                    errorMessage = null
                                                    Toast.makeText(
                                                        context,
                                                        if (isEn) "Saved & synced to system_server" else "已保存并同步给系统",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    errorMessage = err
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Save,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(tr("保存生效", "Save & Apply"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clean, non-wrapping Secondary Menu (二级菜单) for drawing random decoy phrases.
 * Badges strictly say "联网" or "本地" ("Online" / "Local" in English).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DecoySourceWorkshopSheet(
    hapticEnabled: Boolean,
    onDrawSource: (DecoySourceType, Int, (List<String>) -> Unit) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isEn = isEnglishLocale()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var batchCount by remember { mutableIntStateOf(3) }
    var loadingSource by remember { mutableStateOf<DecoySourceType?>(null) }
    var lastDrawnPreview by remember { mutableStateOf<List<String>>(emptyList()) }

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
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("随机抽词", "Draw Random Decoys"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 5, 8).forEach { n ->
                        FilterChip(
                            selected = batchCount == n,
                            onClick = {
                                view.tickHaptic(hapticEnabled)
                                batchCount = n
                            },
                            label = { Text(if (isEn) "$n each" else "每次 $n 条", maxLines = 1) }
                        )
                    }
                }
            }

            // Concise open-source notice
            Text(
                text = tr(
                    "标「联网」的会请求对应网站拉取随机词条，放轻松，开源软件不夹带私货；标「本地」的不走网络直接生成。",
                    "Sources tagged 'Online' fetch random entries from the respective site (relax, it's open-source); 'Local' sources generate offline."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            // Clean, non-wrapping source cards
            DecoySourceType.entries.forEach { source ->
                val isBusy = loadingSource == source
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isEn) source.titleEn else source.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (source.isOnline) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    }
                                ) {
                                    Text(
                                        text = if (source.isOnline) {
                                            tr("联网", "Online")
                                        } else {
                                            tr("本地", "Local")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (source.isOnline) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isEn) source.subtitleEn else source.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                if (loadingSource == null) {
                                    view.tickHaptic(hapticEnabled, confirm = true)
                                    loadingSource = source
                                    onDrawSource(source, batchCount) { added ->
                                        loadingSource = null
                                        lastDrawnPreview = added
                                        Toast.makeText(
                                            context,
                                            if (isEn) "Added ${added.size} entries" else "已加进词库 ${added.size} 条",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            enabled = loadingSource == null,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (isEn) "Draw $batchCount" else "抽 $batchCount 条",
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            if (lastDrawnPreview.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = tr("刚抽出来的：", "Just drawn:"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        lastDrawnPreview.forEach { line ->
                            Text(
                                text = "· $line",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        view.tickHaptic(hapticEnabled)
                        onResetDefault()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr("恢复默认词库", "Reset Default"), maxLines = 1)
                }
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(tr("完成", "Done"), maxLines = 1)
                }
            }
        }
    }
}
