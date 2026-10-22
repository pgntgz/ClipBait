package me.pj.jqb.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.pj.jqb.ClipBoxApp
import me.pj.jqb.data.db.AppInterceptStat
import me.pj.jqb.model.ActionType
import me.pj.jqb.model.AuditRecord
import me.pj.jqb.model.ConfigModel
import java.util.Calendar

data class AppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val action: ActionType
)

class MainViewModel : ViewModel() {

    private val app = ClipBoxApp.instance
    private val dao = app.database.auditDao()
    private val configRepo = app.configRepository

    val configState: StateFlow<ConfigModel> = configRepo.configState

    val auditRecords: StateFlow<List<AuditRecord>> = dao.getRecent(150)
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

    val topOffenders: StateFlow<List<AppInterceptStat>> = dao.getTopOffenders(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val installedApps: StateFlow<List<AppItem>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    init {
        loadInstalledApps()
    }

    fun isModuleActive(): Boolean {
        return app.isXposedModuleActive()
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            val pm = app.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            val items = packages.mapNotNull { appInfo ->
                // Skip our own app
                if (appInfo.packageName == app.packageName) return@mapNotNull null

                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    appInfo.packageName
                }
                val icon = try {
                    pm.getApplicationIcon(appInfo)
                } catch (e: Exception) {
                    null
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val action = configRepo.getActionForPackage(appInfo.packageName)

                AppItem(
                    packageName = appInfo.packageName,
                    appName = label,
                    icon = icon,
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
        }
    }

    fun setAppAction(packageName: String, action: ActionType) {
        viewModelScope.launch {
            configRepo.setAppAction(packageName, action)
            // Update local memory list item
            _installedApps.value = _installedApps.value.map {
                if (it.packageName == packageName) it.copy(action = action) else it
            }
        }
    }

    fun saveRawConfig(jsonString: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = configRepo.saveRawJson(jsonString)
            if (result.isSuccess) {
                loadInstalledApps() // Refresh app actions according to new rules
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
}
