package me.pj.jqb.ui

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.pj.jqb.ClipBoxApp
import me.pj.jqb.HookState
import me.pj.jqb.data.db.AppInterceptStat
import me.pj.jqb.data.receiver.AuditLogReceiver
import me.pj.jqb.data.repo.DecoySourceType
import me.pj.jqb.data.repo.SmartAppClassifier
import me.pj.jqb.data.repo.WikiRandomFetcher
import me.pj.jqb.hook.DecoyGenerator
import me.pj.jqb.model.ActionType
import me.pj.jqb.model.AuditRecord
import me.pj.jqb.model.ConfigModel
import me.pj.jqb.model.TriggerType
import me.pj.jqb.ui.components.AppIconCache
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Immutable
data class AppItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val action: ActionType
)

@Immutable
data class SmartDetectionUiItem(
    val packageName: String,
    val appName: String,
    val category: String,
    val reason: String,
    val currentAction: ActionType,
    val recommendedAction: ActionType,
    val selected: Boolean = true
)

@Immutable
data class FavoriteOffenderStat(
    val packageName: String,
    val appName: String,
    val interceptCount: Int,
    val usageMinutes: Int,
    val favoriteScore: Int,
    val scoreDetail: String
)

class MainViewModel : ViewModel() {

    private val app = ClipBoxApp.instance
    private val dao = app.database.auditDao()
    private val configRepo = app.configRepository

    val configState: StateFlow<ConfigModel> = configRepo.configState
    val hookState: StateFlow<HookState> = app.hookState

    // Resolve any legacy record whose appName was saved as packageName or has [百科] prefix
    val auditRecords: StateFlow<List<AuditRecord>> = dao.getRecent(200)
        .map { list ->
            list.map { record ->
                val resolvedName = AppIconCache.resolveLabel(app, record.packageName, record.appName)
                val cleanedPayload = record.spoofedPayload?.let { DecoyGenerator.stripLegacyPrefix(it) }
                if (resolvedName != record.appName || cleanedPayload != record.spoofedPayload) {
                    record.copy(appName = resolvedName, spoofedPayload = cleanedPayload)
                } else {
                    record
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalInterceptCount: StateFlow<Int> = dao.getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val startOfToday: Long
        get() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

    val todayInterceptCount: StateFlow<Int> = dao.getCountSince(startOfToday)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val topOffenders: StateFlow<List<AppInterceptStat>> = dao.getTopOffenders(6)
        .map { list ->
            list.map { stat ->
                val resolvedName = AppIconCache.resolveLabel(app, stat.packageName, stat.appName)
                stat.copy(appName = resolvedName)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // "最爱读取积分榜" — combines intercept records with foreground usage time
    val favoriteOffenders: StateFlow<List<FavoriteOffenderStat>> = combine(
        topOffenders,
        auditRecords,
        configState
    ) { offenders, records, cfg ->
        val isEn = resolveIsEnglish(cfg.settings.language)
        val usageMapMs = querySevenDayUsageMapMs()
        offenders.map { stat ->
            val appRecords = records.filter { it.packageName == stat.packageName }
            val bgHits = appRecords.count {
                TriggerType.fromCode(it.triggerReason) == TriggerType.BACKGROUND_SILENT ||
                    it.processName.contains(":")
            }
            val rawUsageMs = usageMapMs[stat.packageName] ?: 0L
            val usageMin = (rawUsageMs / 60_000L).toInt()
            val effectiveMin = usageMin.coerceAtLeast(1)

            // Density score: rewards apps that read often despite low foreground usage time, plus bonus for background sneak reads
            val score = if (usageMin == 0) {
                (stat.count * 26) + (bgHits * 14)
            } else {
                ((stat.count * 22.0 / sqrt(effectiveMin.toDouble())) + (bgHits * 10)).roundToInt().coerceAtLeast(stat.count)
            }

            val detail = if (isEn) {
                when {
                    usageMin == 0 && bgHits > 0 -> "0m foreground · ${stat.count} bg reads"
                    usageMin == 0 -> "Brief open · ${stat.count} reads"
                    else -> "Used ${usageMin}m · ${stat.count} reads"
                }
            } else {
                when {
                    usageMin == 0 && bgHits > 0 -> "前台 0 分钟 · 后台偷摸 ${stat.count} 次"
                    usageMin == 0 -> "短暂停留 · 伸手 ${stat.count} 次"
                    else -> "用 ${usageMin} 分钟 · 偷摸 ${stat.count} 次"
                }
            }

            FavoriteOffenderStat(
                packageName = stat.packageName,
                appName = stat.appName,
                interceptCount = stat.count,
                usageMinutes = usageMin,
                favoriteScore = score,
                scoreDetail = detail
            )
        }.sortedByDescending { it.favoriteScore }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val installedApps: StateFlow<List<AppItem>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _smartDetectedApps = MutableStateFlow<List<SmartDetectionUiItem>>(emptyList())
    val smartDetectedApps: StateFlow<List<SmartDetectionUiItem>> = _smartDetectedApps.asStateFlow()

    private val _isScanningSmart = MutableStateFlow(false)
    val isScanningSmart: StateFlow<Boolean> = _isScanningSmart.asStateFlow()

    init {
        loadInstalledApps()
        recheckHookState()
        pullPendingAudits()
        pruneOldAuditLogs()
    }

    fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = app.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                app.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }

    private fun querySevenDayUsageMapMs(): Map<String, Long> {
        return try {
            val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyMap()
            val now = System.currentTimeMillis()
            val stats = usm.queryAndAggregateUsageStats(now - 7 * 86_400_000L, now)
            stats.mapValues { (_, u) -> u.totalTimeInForeground }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    fun recheckHookState() {
        viewModelScope.launch {
            configRepo.broadcastConfigToSystemServer()
            pullPendingAudits()
            delay(1200)
            app.resolveHandshakeTimeout()
        }
    }

    fun pullPendingAudits() {
        try {
            val intent = Intent(AuditLogReceiver.ACTION_PULL_PENDING_LOGS).apply {
                setPackage("android")
            }
            app.sendBroadcast(intent)
        } catch (_: Throwable) {
        }
    }

    private fun pruneOldAuditLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val retentionDays = configRepo.getConfig().settings.auditLog.retentionDays
                val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 86_400_000L)
                dao.deleteOlderThan(cutoff)
            } catch (_: Exception) {
            }
        }
    }

    fun isModuleActive(): Boolean {
        return hookState.value == HookState.ACTIVE || app.isXposedModuleActive()
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            val pm = app.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            val items = packages.mapNotNull { appInfo ->
                if (appInfo.packageName == app.packageName) return@mapNotNull null

                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    appInfo.packageName
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val action = configRepo.getActionForPackage(appInfo.packageName)

                // Backfill legacy database records that stored packageName as appName
                if (label != appInfo.packageName) {
                    try {
                        dao.fixLegacyAppName(appInfo.packageName, label)
                    } catch (_: Exception) {
                    }
                }

                AppItem(
                    packageName = appInfo.packageName,
                    appName = label,
                    isSystemApp = isSystem,
                    action = action
                )
            }.sortedWith(
                compareBy<AppItem> { it.action == ActionType.DIRECT }
                    .thenBy { it.isSystemApp }
                    .thenBy { it.appName.lowercase() }
            )

            _installedApps.value = items
            _isLoadingApps.value = false

            // Pre-warm icons for configured & top non-system apps on background thread
            items.take(40).forEach { item ->
                AppIconCache.loadIconAsync(app, item.packageName)
            }
        }
    }

    fun getActionForPackage(packageName: String): ActionType {
        return configRepo.getActionForPackage(packageName)
    }

    fun setAppAction(packageName: String, action: ActionType) {
        viewModelScope.launch {
            configRepo.setAppAction(packageName, action)
            _installedApps.value = _installedApps.value.map {
                if (it.packageName == packageName) it.copy(action = action) else it
            }
        }
    }

    fun scanDomesticApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanningSmart.value = true
            val scanned = SmartAppClassifier.scanInstalledApps(app) { pkg ->
                configRepo.getActionForPackage(pkg)
            }
            val detected = scanned.map { item ->
                AppIconCache.loadIconAsync(app, item.packageName)
                SmartDetectionUiItem(
                    packageName = item.packageName,
                    appName = item.appName,
                    category = item.category.badgeText,
                    reason = item.reason,
                    currentAction = item.currentAction,
                    recommendedAction = item.recommendedAction,
                    selected = true
                )
            }
            _smartDetectedApps.value = detected
            _isScanningSmart.value = false
        }
    }

    fun toggleSmartDetectionSelect(packageName: String) {
        _smartDetectedApps.value = _smartDetectedApps.value.map {
            if (it.packageName == packageName) it.copy(selected = !it.selected) else it
        }
    }

    fun setAllSmartDetectionSelected(selected: Boolean) {
        _smartDetectedApps.value = _smartDetectedApps.value.map {
            it.copy(selected = selected)
        }
    }

    fun changeSmartDetectionAction(packageName: String, newAction: ActionType) {
        _smartDetectedApps.value = _smartDetectedApps.value.map {
            if (it.packageName == packageName) it.copy(recommendedAction = newAction) else it
        }
    }

    fun applySmartDetectedRules(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val selectedItems = _smartDetectedApps.value.filter { it.selected }
            val actionMap = selectedItems.associate { it.packageName to it.recommendedAction }
            configRepo.applyBatchAppActions(actionMap)
            loadInstalledApps()
            onComplete(selectedItems.size)
        }
    }

    fun setDefaultAction(action: ActionType) {
        viewModelScope.launch {
            configRepo.setDefaultAction(action)
            loadInstalledApps()
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            configRepo.setLanguage(language)
        }
    }

    fun setSmartFallbackAction(fallbackKey: String) {
        viewModelScope.launch {
            configRepo.setSmartFallbackAction(fallbackKey)
        }
    }

    fun setDecoyCacheMs(cacheMs: Long) {
        viewModelScope.launch {
            configRepo.setDecoyCacheMs(cacheMs)
        }
    }

    fun setDeepAnalysisEnabled(enabled: Boolean) {
        viewModelScope.launch {
            configRepo.setDeepAnalysisEnabled(enabled)
        }
    }

    fun setFavoriteLeaderboardEnabled(enabled: Boolean) {
        viewModelScope.launch {
            configRepo.setFavoriteLeaderboardEnabled(enabled)
        }
    }

    fun setShowInterceptToast(enabled: Boolean) {
        viewModelScope.launch {
            configRepo.setShowInterceptToast(enabled)
        }
    }

    fun setConfettiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            configRepo.setConfettiEnabled(enabled)
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            configRepo.setHapticEnabled(enabled)
        }
    }

    fun setAuditLogEnabled(enabled: Boolean) {
        viewModelScope.launch {
            configRepo.setAuditLogEnabled(enabled)
        }
    }

    fun setResumeWindowMs(windowMs: Long) {
        viewModelScope.launch {
            configRepo.setResumeWindowMs(windowMs)
        }
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch {
            configRepo.setRetentionDays(days)
            pruneOldAuditLogs()
        }
    }

    fun drawDecoysFromSource(source: DecoySourceType, count: Int = 3, onDone: (List<String>) -> Unit) {
        viewModelScope.launch {
            val entries = WikiRandomFetcher.fetchFromSource(source, count)
            configRepo.appendDecoyEntries("e_commerce_noise", entries)
            onDone(entries)
        }
    }

    fun resetDecoyPoolToDefault(decoyId: String = "e_commerce_noise") {
        viewModelScope.launch {
            configRepo.resetDecoyPoolToDefault(decoyId)
        }
    }

    fun addCustomDecoyEntry(decoyId: String, entry: String) {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            configRepo.appendDecoyEntries(decoyId, listOf(trimmed))
        }
    }

    fun removeDecoyEntry(decoyId: String, entry: String) {
        viewModelScope.launch {
            configRepo.removeDecoyEntry(decoyId, entry)
        }
    }

    fun saveRawConfig(jsonString: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = configRepo.saveRawJson(jsonString)
            if (result.isSuccess) {
                loadInstalledApps()
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.localizedMessage)
            }
        }
    }

    fun getRawConfigJson(): String {
        return configRepo.getRawConfigJson()
    }

    fun clearAuditLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAll()
        }
    }

    fun insertTestAuditRecord() {
        viewModelScope.launch(Dispatchers.IO) {
            val samples = listOf(
                Triple("com.taobao.taobao", "淘宝", "SPOOFED"),
                Triple("com.tencent.mobileqq", "QQ", "BLOCKED"),
                Triple("com.xunmeng.pinduoduo", "拼多多", "SPOOFED"),
                Triple("com.jingdong.app.mall", "京东", "SPOOFED")
            )
            val sample = samples.random()
            val triggers = listOf(
                TriggerType.LIFECYCLE_RESUME.code to "TOP_ON_RESUME (42ms)",
                TriggerType.BACKGROUND_SILENT.code to "BACKGROUND_SUBPROCESS",
                TriggerType.ASYNC_POLL.code to "BURST_POLL (180ms)"
            ).random()
            val randomSource = listOf(
                DecoySourceType.UNCYCLOPEDIA,
                DecoySourceType.CHOUXIANG_WORLD,
                DecoySourceType.WILD_PHILOSOPHY,
                DecoySourceType.FAKE_SHARE_TOKEN
            ).random()
            val sampleDecoy = WikiRandomFetcher.fetchFromSource(randomSource, 1).firstOrNull()
                ?: "一氧化二氢：一种无色无味但致死率高达100%的剧毒工业溶剂。"

            dao.insert(
                AuditRecord(
                    packageName = sample.first,
                    appName = AppIconCache.resolveLabel(app, sample.first, sample.second),
                    timestamp = System.currentTimeMillis(),
                    action = sample.third,
                    triggerReason = triggers.first,
                    spoofedPayload = if (sample.third == "SPOOFED") sampleDecoy else null,
                    callingPid = (12000..28000).random(),
                    callingUid = (10200..10450).random(),
                    processName = if (triggers.first == TriggerType.BACKGROUND_SILENT.code) "${sample.first}:push" else sample.first,
                    procState = triggers.second
                )
            )
        }
    }
}
