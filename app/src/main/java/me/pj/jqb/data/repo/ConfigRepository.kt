package me.pj.jqb.data.repo

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.pj.jqb.model.ActionType
import me.pj.jqb.model.ConfigModel
import me.pj.jqb.model.DecoyConfig
import me.pj.jqb.model.RuleConfig
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
                json.decodeFromString<ConfigModel>(content)
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
            // Remove package from existing rules
            val cleanedRules = rules.mapNotNull { rule ->
                val remainingPackages = rule.packageNames.filterNot { it == packageName }
                if (remainingPackages.isEmpty()) null else rule.copy(packageNames = remainingPackages)
            }.toMutableList()

            // If action is DIRECT, and it's default, we can just leave it out, or add specific rule
            if (action != ActionType.DIRECT) {
                cleanedRules.add(
                    RuleConfig(
                        packageNames = listOf(packageName),
                        action = action.key,
                        decoyId = if (action == ActionType.SPOOF) (decoyId ?: "e_commerce_noise") else null,
                        smartFallback = if (action == ActionType.SMART) "block" else null,
                        description = "用户自定义规则"
                    )
                )
            }
            current.copy(rules = cleanedRules)
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
            configFile.writeText(serialized)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun notifyConfigChanged() {
        try {
            context.contentResolver.notifyChange(
                Uri.parse("content://me.pj.jqb.provider.config/rules"),
                null
            )
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
