package me.pj.jqb.data.repo

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.pj.jqb.model.ActionType
import me.pj.jqb.model.ConfigModel
import me.pj.jqb.model.RuleConfig
import me.pj.jqb.model.defaultDecoys
import java.io.File

class ConfigRepository(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val configFile: File
        get() = File(context.filesDir, "config.json")

    private val _configState = MutableStateFlow(loadConfig())
    val configState: StateFlow<ConfigModel> = _configState.asStateFlow()

    fun getConfig(): ConfigModel = _configState.value

    fun getRawConfigJson(): String {
        return json.encodeToString(getConfig())
    }

    fun loadConfig(): ConfigModel {
        return try {
            if (configFile.exists()) {
                val content = configFile.readText()
                val loaded = json.decodeFromString<ConfigModel>(content)
                val prefixRegex = Regex("^([【\\[](百科|百科词条)[】\\]])\\s*")
                // Migrate legacy Special:Random literal URL, chouxiang.world title boilerplate, and strip [百科]/[百科词条] prefixes
                val migratedDecoys = loaded.decoys.map { decoy ->
                    val cleanedPool = decoy.pool
                        .filterNot { it.contains("Special:Random", ignoreCase = true) }
                        .filterNot { it.trim() == "没有抽象段子的无聊世界" || it.contains("没有抽象段子的无聊世界") }
                        .map { it.replace(prefixRegex, "").trim() }
                        .filter { it.isNotBlank() }

                    if (decoy.pool.any { it.contains("Special:Random", ignoreCase = true) }) {
                        val defaultPool = defaultDecoys().firstOrNull()?.pool ?: emptyList()
                        decoy.copy(
                            strategy = "wiki_lucky",
                            pool = (defaultPool + cleanedPool).distinct()
                        )
                    } else if (cleanedPool != decoy.pool) {
                        val fallbackPool = if (cleanedPool.isEmpty() && decoy.id == "e_commerce_noise") {
                            defaultDecoys().firstOrNull()?.pool ?: emptyList()
                        } else {
                            cleanedPool.distinct()
                        }
                        decoy.copy(pool = fallbackPool)
                    } else {
                        decoy
                    }
                }
                val finalConfig = if (migratedDecoys != loaded.decoys) {
                    loaded.copy(decoys = migratedDecoys).also { saveConfigInternal(it) }
                } else {
                    loaded
                }
                finalConfig
            } else {
                val default = ConfigModel()
                saveConfigInternal(default)
                default
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ConfigModel()
        }
    }

    suspend fun saveRawJson(jsonString: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val parsed = json.decodeFromString<ConfigModel>(jsonString)
            saveConfigInternal(parsed)
            _configState.value = parsed
            notifyConfigChanged()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateConfig(transform: (ConfigModel) -> ConfigModel) = withContext(Dispatchers.IO) {
        val updated = transform(_configState.value)
        saveConfigInternal(updated)
        _configState.value = updated
        notifyConfigChanged()
    }

    suspend fun setAppAction(packageName: String, action: ActionType, decoyId: String? = null) = withContext(Dispatchers.IO) {
        updateConfig { current ->
            val rules = current.rules.toMutableList()
            val cleanedRules = rules.mapNotNull { rule ->
                val remainingPackages = rule.packageNames.filterNot { it == packageName }
                if (remainingPackages.isEmpty()) null else rule.copy(packageNames = remainingPackages)
            }.toMutableList()

            if (action != ActionType.DIRECT) {
                cleanedRules.add(
                    RuleConfig(
                        packageNames = listOf(packageName),
                        action = action.key,
                        decoyId = if (action == ActionType.SPOOF) (decoyId ?: "e_commerce_noise") else null,
                        smartFallback = null,
                        description = "用户自定义规则"
                    )
                )
            }
            current.copy(rules = cleanedRules)
        }
    }

    suspend fun applyBatchAppActions(actions: Map<String, ActionType>, description: String = "智能识别规则") = withContext(Dispatchers.IO) {
        if (actions.isEmpty()) return@withContext
        updateConfig { current ->
            val targetPackages = actions.keys
            val cleanedRules = current.rules.mapNotNull { rule ->
                val remaining = rule.packageNames.filterNot { it in targetPackages }
                if (remaining.isEmpty()) null else rule.copy(packageNames = remaining)
            }.toMutableList()

            val groupedByAction = actions.entries
                .filter { it.value != ActionType.DIRECT }
                .groupBy({ it.value }, { it.key })

            groupedByAction.forEach { (action, pkgs) ->
                if (pkgs.isNotEmpty()) {
                    cleanedRules.add(
                        RuleConfig(
                            packageNames = pkgs.sorted(),
                            action = action.key,
                            decoyId = if (action == ActionType.SPOOF) "e_commerce_noise" else null,
                            smartFallback = null,
                            description = description
                        )
                    )
                }
            }
            current.copy(rules = cleanedRules)
        }
    }

    suspend fun setDefaultAction(action: ActionType) = withContext(Dispatchers.IO) {
        updateConfig { current ->
            current.copy(
                settings = current.settings.copy(defaultAction = action.key)
            )
        }
    }

    suspend fun setLanguage(language: String) = withContext(Dispatchers.IO) {
        updateConfig { current ->
            current.copy(
                settings = current.settings.copy(language = language)
            )
        }
    }

    suspend fun setSmartFallbackAction(fallbackKey: String) = withContext(Dispatchers.IO) {
        updateConfig { current ->
            // Also clear hardcoded smartFallback on existing smart rules so the global setting applies everywhere
            val updatedRules = current.rules.map { rule ->
                if (rule.action == "smart") rule.copy(smartFallback = null) else rule
            }
            current.copy(
                settings = current.settings.copy(
                    smartDetect = current.settings.smartDetect.copy(fallbackAction = fallbackKey)
                ),
                rules = updatedRules
            )
        }
    }

    suspend fun setDecoyCacheMs(cacheMs: Long) = withContext(Dispatchers.IO) {
        updateConfig { current ->
            current.copy(
                settings = current.settings.copy(
                    smartDetect = current.settings.smartDetect.copy(decoyCacheMs = cacheMs.coerceIn(0L, 60_000L))
                )
            )
        }
    }

    suspend fun setDeepAnalysisEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        updateConfig { current ->
            current.copy(
                settings = current.settings.copy(
                    auditLog = current.settings.auditLog.copy(deepAnalysis = enabled)
                )
            )
        }
    }

    suspend fun setFavoriteLeaderboardEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        updateConfig { current ->
            current.copy(
                settings = current.settings.copy(
                    auditLog = current.settings.auditLog.copy(favoriteLeaderboardEnabled = enabled)
                )
            )
        }
    }

    suspend fun setShowInterceptToast(enabled: Boolean) = withContext(Dispatchers.IO) {
        updateConfig { current ->
            current.copy(
                settings = current.settings.copy(
                    auditLog = current.settings.auditLog.copy(showInterceptToast = enabled)
                )
            )
        }
    }

    suspend fun setConfettiEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        updateConfig { current ->
            current.copy(
                settings = current.settings.copy(
                    auditLog = current.settings.auditLog.copy(confettiEnabled = enabled)
                )
            )
        }
    }

    suspend fun setHapticEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        updateConfig { current ->
            current.copy(
                settings = current.settings.copy(
                    auditLog = current.settings.auditLog.copy(hapticEnabled = enabled)
                )
            )
        }
    }

    suspend fun setResumeWindowMs(windowMs: Long) = withContext(Dispatchers.IO) {
        updateConfig { current ->
            current.copy(
                settings = current.settings.copy(
                    smartDetect = current.settings.smartDetect.copy(resumeWindowMs = windowMs.coerceIn(500L, 6000L))
                )
            )
        }
    }

    suspend fun setRetentionDays(days: Int) = withContext(Dispatchers.IO) {
        updateConfig { current ->
            current.copy(
                settings = current.settings.copy(
                    auditLog = current.settings.auditLog.copy(retentionDays = days.coerceIn(1, 365))
                )
            )
        }
    }

    suspend fun setAuditLogEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        updateConfig { current ->
            current.copy(
                settings = current.settings.copy(
                    auditLog = current.settings.auditLog.copy(enabled = enabled)
                )
            )
        }
    }

    suspend fun removeDecoyEntry(decoyId: String, entry: String) = withContext(Dispatchers.IO) {
        updateConfig { current ->
            current.copy(decoys = current.decoys.map { decoy ->
                if (decoy.id == decoyId) decoy.copy(pool = decoy.pool - entry) else decoy
            })
        }
    }

    suspend fun resetDecoyPoolToDefault(decoyId: String) = withContext(Dispatchers.IO) {
        val defaultPool = defaultDecoys().firstOrNull { it.id == decoyId }?.pool ?: emptyList()
        updateConfig { current ->
            current.copy(decoys = current.decoys.map { decoy ->
                if (decoy.id == decoyId) decoy.copy(pool = defaultPool) else decoy
            })
        }
    }

    suspend fun appendDecoyEntries(decoyId: String, newEntries: List<String>) = withContext(Dispatchers.IO) {
        if (newEntries.isEmpty()) return@withContext
        updateConfig { current ->
            val updatedDecoys = current.decoys.map { decoy ->
                if (decoy.id == decoyId) {
                    decoy.copy(pool = (newEntries + decoy.pool).distinct())
                } else {
                    decoy
                }
            }
            current.copy(decoys = updatedDecoys)
        }
    }

    fun getActionForPackage(packageName: String): ActionType {
        val config = _configState.value
        val matchedRule = config.rules.firstOrNull { it.packageNames.contains(packageName) }
        return if (matchedRule != null) {
            ActionType.fromKey(matchedRule.action)
        } else {
            ActionType.fromKey(config.settings.defaultAction)
        }
    }

    private fun saveConfigInternal(config: ConfigModel) {
        try {
            val serialized = json.encodeToString(config)
            val atomicFile = AtomicFile(configFile)
            val fos = atomicFile.startWrite()
            try {
                fos.write(serialized.toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(fos)
            } catch (t: Throwable) {
                atomicFile.failWrite(fos)
                throw t
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun broadcastConfigToSystemServer() {
        try {
            val intent = android.content.Intent(me.pj.jqb.data.receiver.AuditLogReceiver.ACTION_CONFIG_UPDATED).apply {
                setPackage("android")
                putExtra("config_json", getRawConfigJson())
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun notifyConfigChanged() {
        try {
            context.contentResolver.notifyChange(
                Uri.parse("content://io.github.pgntgz.clipbait.provider.config/rules"),
                null
            )
            broadcastConfigToSystemServer()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        @Volatile
        private var instance: ConfigRepository? = null

        fun getInstance(context: Context): ConfigRepository {
            return instance ?: synchronized(this) {
                instance ?: ConfigRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
