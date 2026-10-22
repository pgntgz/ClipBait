package me.pj.jqb.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import me.pj.jqb.model.ActionType
import me.pj.jqb.ui.AppItem
import me.pj.jqb.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRulesScreen(viewModel: MainViewModel) {
    val apps by viewModel.installedApps.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var filterConfiguredOnly by remember { mutableStateOf(false) }
    var hideSystemApps by remember { mutableStateOf(true) }

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

    var showGuide by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用规则", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { showGuide = !showGuide }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "模式说明",
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
            // Mode Guide Card (Toggleable)
            if (showGuide) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "模式区别说明",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ModeHelpItem(title = "直通", desc = "原样放行，完全不拦截。")
                        ModeHelpItem(title = "智能", desc = "平时放行粘贴。但应用在切台启动（onResume）或后台偷读时自动拦截。")
                        ModeHelpItem(title = "伪装", desc = "无论怎么读，均喂给应用一段无关的正常短语，搞乱客群画像。")
                        ModeHelpItem(title = "阻断", desc = "坚决不给。直接返回空，应用永远读不到任何剪切板。")
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
                placeholder = { Text("搜索应用名称或包名...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterConfiguredOnly,
                    onClick = { filterConfiguredOnly = !filterConfiguredOnly },
                    label = { Text("已配置管控") }
                )
                FilterChip(
                    selected = hideSystemApps,
                    onClick = { hideSystemApps = !hideSystemApps },
                    label = { Text("隐藏系统应用") }
                )
            }

            // App List or Loading
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
                            text = "未找到符合条件的应用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { appItem ->
                        AppRuleCard(
                            appItem = appItem,
                            onActionSelected = { newAction ->
                                viewModel.setAppAction(appItem.packageName, newAction)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRuleCard(
    appItem: AppItem,
    onActionSelected: (ActionType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon
                if (appItem.icon != null) {
                    val bitmap = remember(appItem.packageName) {
                        try {
                            appItem.icon.toBitmap(96, 96, Bitmap.Config.ARGB_8888).asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        DefaultAppIcon()
                    }
                } else {
                    DefaultAppIcon()
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appItem.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = appItem.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Selection Segmented Buttons
            val actions = listOf(ActionType.DIRECT, ActionType.SMART, ActionType.SPOOF, ActionType.BLOCK)
            val selectedIndex = actions.indexOf(appItem.action)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                actions.forEachIndexed { index, action ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = actions.size),
                        onClick = { onActionSelected(action) },
                        selected = index == selectedIndex,
                        label = {
                            val labelText = when (action) {
                                ActionType.DIRECT -> "直通"
                                ActionType.SMART -> "智能"
                                ActionType.SPOOF -> "伪装"
                                ActionType.BLOCK -> "阻断"
                            }
                            Text(labelText, style = MaterialTheme.typography.labelSmall)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DefaultAppIcon() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Apps,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun ModeHelpItem(title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
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
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
