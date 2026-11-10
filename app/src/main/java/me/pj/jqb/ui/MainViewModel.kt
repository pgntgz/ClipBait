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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.pj.jqb.ClipBoxApp
import me.pj.jqb.HookState
import me.pj.jqb.data.db.AppInterceptStat
import me.pj.jqb.data.receiver.AuditLogReceiver
import me.pj.jqb.data.repo.DecoySourceType
import me.pj.jqb.data.repo.ReleaseUpdateInfo
import me.pj.jqb.data.repo.SmartAppClassifier
import me.pj.jqb.data.repo.UpdateChecker
import me.pj.jqb.data.repo.WikiRandomFetcher
import me.pj.jqb.hook.DecoyGenerator
import me.pj.jqb.model.ActionType
import me.pj.jqb.model.AuditRecord
import me.pj.jqb.model.ConfigModel
import me.pj.jqb.model.TriggerType
import me.pj.jqb.ui.components.AppIconCache
import me.pj.jqb.ui.components.HallOfFamePosterGenerator
import me.pj.jqb.ui.components.PosterGenerationResult
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Immutable
data class AppItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val action: ActionType,
    val isCustomRule: Boolean = false
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
    val canUndoDecoyState: StateFlow<Boolean> = configRepo.canUndoDecoyState

    val hapticEnabledState: StateFlow<Boolean> = configState
        .map { it.settings.auditLog.hapticEnabled }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _hasUsagePermission = MutableStateFlow(false)
    val hasUsagePermissionState: StateFlow<Boolean> = _hasUsagePermission.asStateFlow()

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
        .flowOn(Dispatchers.IO)
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
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val favoriteBoardConfigFlow = configState
        .map { it.settings.auditLog.favoriteLeaderboardEnabled to it.settings.language }
        .distinctUntilChanged()

    // "最爱读取积分榜" — combines intercept records with foreground usage time on Dispatchers.IO
    val favoriteOffenders: StateFlow<List<FavoriteOffenderStat>> = combine(
        topOffenders,
        auditRecords,
        favoriteBoardConfigFlow
    ) { offenders, records, (favEnabled, lang) ->
        if (!favEnabled || offenders.isEmpty()) {
            return@combine emptyList()
        }
        val isEn = resolveIsEnglish(lang)
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
                    usageMin == 0 && bgHits > 0 -> "Background reads: ${stat.count}"
                    usageMin == 0 -> "Reads: ${stat.count}"
                    else -> "Used ${usageMin}m · ${stat.count} reads"
                }
            } else {
                when {
                    usageMin == 0 && bgHits > 0 -> "后台读取 ${stat.count} 次"
                    usageMin == 0 -> "读取 ${stat.count} 次"
                    else -> "使用 ${usageMin} 分钟 · 读取 ${stat.count} 次"
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
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val installedApps: StateFlow<List<AppItem>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _smartDetectedApps = MutableStateFlow<List<SmartDetectionUiItem>>(emptyList())
    val smartDetectedApps: StateFlow<List<SmartDetectionUiItem>> = _smartDetectedApps.asStateFlow()

    private val _isScanningSmart = MutableStateFlow(false)
    val isScanningSmart: StateFlow<Boolean> = _isScanningSmart.asStateFlow()

    private val _updateInfo = MutableStateFlow<ReleaseUpdateInfo?>(null)
    val updateInfo: StateFlow<ReleaseUpdateInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    init {
        refreshUsagePermissionState()
        loadInstalledApps()
        recheckHookState()
        pullPendingAudits()
        pruneOldAuditLogs()
        autoCheckUpdateIfEnabled()
    }

    private fun autoCheckUpdateIfEnabled() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = configRepo.getConfig().settings
                if (settings.updateCheckMode != "auto") return@launch
                val now = System.currentTimeMillis()
                // Throttle automatic startup check to once every 6 hours
                if ((now - settings.lastUpdateCheckTime) < 6 * 3600_000L && _updateInfo.value != null) {
                    return@launch
                }
                _isCheckingUpdate.value = true
                val info = UpdateChecker.checkLatestRelease()
                _updateInfo.value = info
                configRepo.markUpdateCheckedNow(now)
            } catch (_: Exception) {
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun setUpdateCheckMode(mode: String) {
        viewModelScope.launch {
            configRepo.setUpdateCheckMode(mode)
            if (mode == "auto" && _updateInfo.value == null) {
                checkForUpdatesNow(null)
            }
        }
    }

    fun checkForUpdatesNow(onComplete: ((Result<ReleaseUpdateInfo>) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingUpdate.value = true
            try {
                val info = UpdateChecker.checkLatestRelease()
                _updateInfo.value = info
                configRepo.markUpdateCheckedNow(System.currentTimeMillis())
                if (onComplete != null) {
                    withContext(Dispatchers.Main) {
                        onComplete(Result.success(info))
                    }
                }
            } catch (e: Exception) {
                if (onComplete != null) {
                    withContext(Dispatchers.Main) {
                        onComplete(Result.failure(e))
                    }
                }
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun refreshUsagePermissionState() {
        _hasUsagePermission.value = hasUsageStatsPermission()
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
            refreshUsagePermissionState()
            configRepo.broadcastConfigToSystemServer()
            pullPendingAudits()
            delay(1200)
            app.resolveHandshakeTimeout()
        }
    }

    fun pullPendingAudits() {
        refreshUsagePermissionState()
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
                val cfg = configRepo.getConfig()
                val auditCfg = cfg.settings.auditLog
                val retentionDays = auditCfg.retentionDays
                // retentionDays <= 0 means "永久保留" (Forever)
                if (retentionDays <= 0) return@launch

                val now = System.currentTimeMillis()
                if (auditCfg.periodicPosterEnabled) {
                    val intervalMs = auditCfg.posterIntervalDays.coerceAtLeast(1).toLong() * 86_400_000L
                    if (auditCfg.lastPosterGeneratedAt == 0L) {
                        configRepo.markPosterGeneratedNow(now)
                    } else if ((now - auditCfg.lastPosterGeneratedAt) >= intervalMs) {
                        val totalCount = dao.getTotalCountSnapshot()
                        if (totalCount > 0) {
                            val rawOffenders = dao.getTopOffendersSnapshot(6).map { stat ->
                                stat.copy(appName = AppIconCache.resolveLabel(app, stat.packageName, stat.appName))
                            }
                            val isEn = resolveIsEnglish(cfg.settings.language)
                            HallOfFamePosterGenerator.generateAndNotify(
                                context = app,
                                offenders = rawOffenders,
                                totalCount = totalCount,
                                periodStyle = auditCfg.posterPeriodStyle,
                                intervalDays = auditCfg.posterIntervalDays,
                                isEn = isEn,
                                postNotification = true
                            )
                            configRepo.markPosterGeneratedNow(now)
                        }
                    }
                }

                val cutoff = now - (retentionDays.toLong() * 86_400_000L)
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

            // Build O(1) lookup map for configured rules once instead of O(N*M) scans
            val currentCfg = configRepo.getConfig()
            val defaultAction = ActionType.fromKey(currentCfg.settings.defaultAction)
            val ruleMap = HashMap<String, ActionType>(currentCfg.rules.size * 2)
            for (rule in currentCfg.rules) {
                val act = ActionType.fromKey(rule.action)
                for (pkgName in rule.packageNames) {
                    ruleMap[pkgName] = act
                }
            }

            val items = packages.mapNotNull { appInfo ->
                if (appInfo.packageName == app.packageName) return@mapNotNull null

                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    appInfo.packageName
                }
                AppIconCache.cacheLabel(appInfo.packageName, label)

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val explicitAction = ruleMap[appInfo.packageName]
                val action = explicitAction ?: defaultAction
                val isCustom = explicitAction != null

                AppItem(
                    packageName = appInfo.packageName,
                    appName = label,
                    isSystemApp = isSystem,
                    action = action,
                    isCustomRule = isCustom
                )
            }.sortedWith(
                compareBy<AppItem> { !it.isCustomRule }
                    .thenBy { it.isSystemApp }
                    .thenBy { it.appName.lowercase() }
            )

            _installedApps.value = items
            _isLoadingApps.value = false

            // Pre-warm icons for all user apps first, then system apps in background so long scrolling never stutters
            for (item in items) {
                if (!item.isSystemApp || item.isCustomRule) {
                    AppIconCache.loadIconAsync(app, item.packageName)
                }
            }
            for (item in items) {
                if (item.isSystemApp && !item.isCustomRule) {
                    AppIconCache.loadIconAsync(app, item.packageName)
                }
            }
        }
    }

    fun getActionForPackage(packageName: String): ActionType {
        return configRepo.getActionForPackage(packageName)
    }

    fun hasCustomRule(packageName: String): Boolean {
        return configRepo.hasCustomRule(packageName)
    }

    fun setAppAction(packageName: String, action: ActionType) {
        viewModelScope.launch {
            configRepo.setAppAction(packageName, action)
            _installedApps.value = _installedApps.value.map {
                if (it.packageName == packageName) it.copy(action = action, isCustomRule = true) else it
            }
        }
    }

    fun resetAppToGlobalDefault(packageName: String) {
        viewModelScope.launch {
            configRepo.resetAppToGlobalDefault(packageName)
            val defaultAction = ActionType.fromKey(configRepo.getConfig().settings.defaultAction)
            _installedApps.value = _installedApps.value.map {
                if (it.packageName == packageName) it.copy(action = defaultAction, isCustomRule = false) else it
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

    fun setInterceptAlertMode(mode: String) {
        viewModelScope.launch {
            configRepo.setInterceptAlertMode(mode)
        }
    }

    fun previewInterceptAlert(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val sampleDecoy = configRepo.getConfig().decoys.firstOrNull()?.pool?.randomOrNull()
                ?: "一氧化二氢：一种无色无味但致死率高达100%的剧毒工业溶剂。"
            AuditLogReceiver.triggerAlertForRecord(
                context = app,
                packageName = "com.tencent.mm",
                appName = AppIconCache.resolveLabel(app, "com.tencent.mm", "微信"),
                actionType = "SPOOFED",
                triggerReason = TriggerType.FOREGROUND_POLL.code,
                spoofedPayload = sampleDecoy,
                systemAlertShown = false,
                forceTest = true,
                overrideMode = mode
            )
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

    fun setPeriodicPosterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            configRepo.setPeriodicPosterEnabled(enabled)
        }
    }

    fun setPosterIntervalDays(days: Int) {
        viewModelScope.launch {
            configRepo.setPosterIntervalDays(days)
        }
    }

    fun setPosterPeriodStyle(style: String) {
        viewModelScope.launch {
            configRepo.setPosterPeriodStyle(style)
        }
    }

    fun generatePosterNow(
        styleOverride: String? = null,
        onResult: (PosterGenerationResult) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = configRepo.getConfig()
            val auditCfg = cfg.settings.auditLog
            val isEn = resolveIsEnglish(cfg.settings.language)
            val totalCount = dao.getTotalCountSnapshot()
            val offenders = dao.getTopOffendersSnapshot(6).map { stat ->
                stat.copy(appName = AppIconCache.resolveLabel(app, stat.packageName, stat.appName))
            }
            val effectiveStyle = styleOverride ?: auditCfg.posterPeriodStyle
            val result = HallOfFamePosterGenerator.generateAndNotify(
                context = app,
                offenders = offenders,
                totalCount = totalCount,
                periodStyle = effectiveStyle,
                intervalDays = if (auditCfg.retentionDays <= 0) 0 else auditCfg.posterIntervalDays,
                isEn = isEn,
                postNotification = true
            )
            configRepo.markPosterGeneratedNow(System.currentTimeMillis())
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun drawDecoysFromSource(source: DecoySourceType, count: Int = 3, onDone: (Result<List<String>>) -> Unit) {
        viewModelScope.launch {
            try {
                val entries = WikiRandomFetcher.fetchFromSource(source, count)
                if (entries.isNotEmpty()) {
                    configRepo.appendDecoyEntries("e_commerce_noise", entries)
                }
                onDone(Result.success(entries))
            } catch (e: Exception) {
                onDone(Result.failure(e))
            }
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

    fun removeDecoyEntries(decoyId: String, entries: Set<String>) {
        viewModelScope.launch {
            configRepo.removeDecoyEntries(decoyId, entries)
        }
    }

    fun undoLastDecoyChange(decoyId: String = "e_commerce_noise", onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val ok = configRepo.undoLastDecoyChange(decoyId)
            onResult?.invoke(ok)
        }
    }

    fun deleteAuditRecord(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteById(id)
        }
    }

    fun deleteAuditRecordsForPackage(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteByPackage(packageName)
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
                DecoySourceType.WILD_PHILOSOPHY,
                DecoySourceType.FAKE_SHARE_TOKEN,
                DecoySourceType.NONSENSE_LITERATURE,
                DecoySourceType.DEV_PANIC_TRAP
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
