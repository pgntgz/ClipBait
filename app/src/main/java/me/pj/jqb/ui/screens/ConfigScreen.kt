package me.pj.jqb.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.pj.jqb.data.repo.DecoySourceType
import me.pj.jqb.ui.MainViewModel
import me.pj.jqb.ui.components.tickHaptic
import me.pj.jqb.ui.isEnglishLocale
import me.pj.jqb.ui.tr

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConfigScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val config by viewModel.configState.collectAsState()
    val canUndoDecoy by viewModel.canUndoDecoyState.collectAsState()
    val hapticEnabled = config.settings.auditLog.hapticEnabled
    val isEn = isEnglishLocale()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var customDecoyInput by remember { mutableStateOf("") }
    var showRawJsonEditor by remember { mutableStateOf(false) }
    var showRandomSourceSheet by remember { mutableStateOf(false) }
    var jsonText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isDirty by remember { mutableStateOf(false) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedDecoyItems by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandAllDecoys by remember { mutableStateOf(false) }

    val primaryDecoy = config.decoys.firstOrNull { it.pool.isNotEmpty() } ?: config.decoys.firstOrNull()
    val currentPool = primaryDecoy?.pool ?: emptyList()
    val validSelected = remember(selectedDecoyItems, currentPool) {
        selectedDecoyItems.intersect(currentPool.toSet())
    }

    LaunchedEffect(config, showRawJsonEditor) {
        if (showRawJsonEditor && !isDirty) {
            jsonText = viewModel.getRawConfigJson()
        }
    }

    fun showDeleteUndoSnackbar(deletedCount: Int, decoyId: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = if (isEn) "Deleted $deletedCount" else "已删除 $deletedCount 条",
                actionLabel = if (isEn) "Undo" else "撤销",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastDecoyChange(decoyId) { ok ->
                    if (ok) {
                        isDirty = false
                    }
                }
            }
        }
    }

    if (showRandomSourceSheet) {
        DecoySourceWorkshopSheet(
            hapticEnabled = hapticEnabled,
            canUndo = canUndoDecoy,
            onDrawSource = { source, count, onComplete ->
                viewModel.drawDecoysFromSource(source, count) { added ->
                    isDirty = false
                    onComplete(added)
                }
            },
            onUndo = {
                viewModel.undoLastDecoyChange { ok ->
                    if (ok) {
                        isDirty = false
                        Toast.makeText(
                            context,
                            if (isEn) "Undone" else "已撤销",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDismiss = { showRandomSourceSheet = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isMultiSelectMode) {
                val allSelected = validSelected.size == currentPool.size && currentPool.isNotEmpty()
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                view.tickHaptic(hapticEnabled)
                                isMultiSelectMode = false
                                selectedDecoyItems = emptySet()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = tr("退出多选", "Close")
                            )
                        }
                    },
                    title = {
                        Text(
                            text = tr("已选 ${validSelected.size} 项", "${validSelected.size} selected"),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                view.tickHaptic(hapticEnabled)
                                selectedDecoyItems = if (allSelected) {
                                    emptySet()
                                } else {
                                    currentPool.toSet()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DoneAll,
                                contentDescription = if (allSelected) tr("取消全选", "Deselect all") else tr("全选", "Select all"),
                                tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            enabled = validSelected.isNotEmpty(),
                            onClick = {
                                view.tickHaptic(hapticEnabled)
                                val joined = currentPool.filter { it in validSelected }.joinToString("\n")
                                clipboardManager.setText(AnnotatedString(joined))
                                Toast.makeText(
                                    context,
                                    if (isEn) "Copied ${validSelected.size}" else "已复制 ${validSelected.size} 条",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = tr("复制", "Copy")
                            )
                        }
                        IconButton(
                            enabled = validSelected.isNotEmpty(),
                            onClick = {
                                val decoyId = primaryDecoy?.id ?: "e_commerce_noise"
                                val count = validSelected.size
                                view.tickHaptic(hapticEnabled, confirm = true)
                                viewModel.removeDecoyEntries(decoyId, validSelected)
                                selectedDecoyItems = emptySet()
                                isMultiSelectMode = false
                                showDeleteUndoSnackbar(count, decoyId)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = tr("删除", "Delete"),
                                tint = if (validSelected.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(tr("词库", "Decoy Pool"), style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        IconButton(
                            onClick = {
                                view.tickHaptic(hapticEnabled)
                                clipboardManager.setText(AnnotatedString(viewModel.getRawConfigJson()))
                                Toast.makeText(
                                    context,
                                    if (isEn) "Copied config" else "已复制配置",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = tr("导出配置", "Copy config")
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Decoys Pool Card
            item(key = "decoy_pool_card", contentType = "decoy_pool_card") {
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("假内容词库", "Decoy phrases"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tr(
                                        "伪装模式下，每次读取从这里随机返回一条。",
                                        "In Spoof mode, each read returns a random phrase from this pool."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    view.tickHaptic(hapticEnabled)
                                    showRandomSourceSheet = true
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Casino,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tr("抽取", "Draw"), maxLines = 1, softWrap = false)
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
                                    Text(tr("添加一条…", "Add a phrase…"))
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
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
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(52.dp)
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
                            val visiblePool = if (expandAllDecoys) decoy.pool else decoy.pool.take(15)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tr("共 ${decoy.pool.size} 条", "${decoy.pool.size} entries"),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = tr("长按可多选", "Long-press to select"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                visiblePool.forEachIndexed { idx, sample ->
                                    val isSelected = sample in validSelected
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    if (isMultiSelectMode) {
                                                        view.tickHaptic(hapticEnabled)
                                                        selectedDecoyItems = if (isSelected) {
                                                            validSelected - sample
                                                        } else {
                                                            validSelected + sample
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    view.tickHaptic(hapticEnabled, confirm = true)
                                                    if (!isMultiSelectMode) {
                                                        isMultiSelectMode = true
                                                        selectedDecoyItems = setOf(sample)
                                                    } else {
                                                        selectedDecoyItems = if (isSelected) {
                                                            validSelected - sample
                                                        } else {
                                                            validSelected + sample
                                                        }
                                                    }
                                                }
                                            )
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        if (isMultiSelectMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    view.tickHaptic(hapticEnabled)
                                                    selectedDecoyItems = if (checked) {
                                                        validSelected + sample
                                                    } else {
                                                        validSelected - sample
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = sample,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (!isMultiSelectMode) {
                                            IconButton(
                                                onClick = {
                                                    view.tickHaptic(hapticEnabled)
                                                    viewModel.removeDecoyEntry(decoy.id, sample)
                                                    showDeleteUndoSnackbar(1, decoy.id)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Close,
                                                    contentDescription = tr("删除", "Remove"),
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    if (idx < visiblePool.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    }
                                }

                                if (decoy.pool.size > 15) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    TextButton(
                                        onClick = {
                                            view.tickHaptic(hapticEnabled)
                                            expandAllDecoys = !expandAllDecoys
                                        },
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Icon(
                                            imageVector = if (expandAllDecoys) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (expandAllDecoys) {
                                                tr("收起", "Collapse")
                                            } else {
                                                tr("显示全部（${decoy.pool.size}）", "Show all (${decoy.pool.size})")
                                            },
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Collapsible JSON Editor Card
            item(key = "raw_json_card", contentType = "raw_json_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = tr("编辑配置文件", "Edit config.json"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = tr(
                                            "用于备份、恢复或手动编辑规则",
                                            "Backup, restore, or manually edit rules"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showRawJsonEditor) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = tr("格式错误：$errorMessage", "Invalid JSON: $errorMessage"),
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
                                        shape = RoundedCornerShape(12.dp)
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
                                                        if (isEn) "Saved" else "已保存",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    errorMessage = err
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Save,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(tr("保存", "Save"))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoySourceWorkshopSheet(
    hapticEnabled: Boolean,
    canUndo: Boolean,
    onDrawSource: (DecoySourceType, Int, (Result<List<String>>) -> Unit) -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isEn = isEnglishLocale()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var batchCount by remember { mutableIntStateOf(3) }
    var loadingSource by remember { mutableStateOf<DecoySourceType?>(null) }
    var lastDrawnPreview by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastErrorMsg by remember { mutableStateOf<String?>(null) }

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
            Text(
                text = tr("从来源抽取", "Draw from sources"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = tr(
                    "联网来源会访问对应网站，本地来源离线生成。",
                    "Online sources fetch from the site; local sources generate offline."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(3, 5, 8).forEachIndexed { index, n ->
                    SegmentedButton(
                        modifier = Modifier.weight(1f),
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        onClick = {
                            view.tickHaptic(hapticEnabled)
                            batchCount = n
                        },
                        selected = batchCount == n,
                        label = {
                            Text(
                                text = if (isEn) "$n items" else "$n 条",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DecoySourceType.entries.forEachIndexed { idx, source ->
                        val isBusy = loadingSource == source
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isEn) source.titleEn else source.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
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
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            },
                            supportingContent = {
                                Text(
                                    text = if (isEn) source.subtitleEn else source.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                FilledTonalButton(
                                    onClick = {
                                        if (loadingSource == null) {
                                            view.tickHaptic(hapticEnabled, confirm = true)
                                            loadingSource = source
                                            lastErrorMsg = null
                                            onDrawSource(source, batchCount) { result ->
                                                loadingSource = null
                                                result.onSuccess { added ->
                                                    lastDrawnPreview = added
                                                    lastErrorMsg = null
                                                    Toast.makeText(
                                                        context,
                                                        if (isEn) "Added ${added.size}" else "已添加 ${added.size} 条",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }.onFailure { err ->
                                                    lastDrawnPreview = emptyList()
                                                    val errMsg = err.message ?: (if (isEn) "Network request failed" else "网络请求失败")
                                                    lastErrorMsg = errMsg
                                                }
                                            }
                                        }
                                    },
                                    enabled = loadingSource == null,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(15.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(
                                            text = tr("抽取", "Draw"),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        if (idx < DecoySourceType.entries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            if (lastErrorMsg != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = tr("获取失败", "Fetch failed"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lastErrorMsg.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (lastDrawnPreview.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = tr("本次添加", "Added"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
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
                        lastDrawnPreview = emptyList()
                        onUndo()
                    },
                    enabled = canUndo,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr("撤销", "Undo"), maxLines = 1)
                }
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(tr("完成", "Done"), maxLines = 1)
                }
            }
        }
    }
}
