package me.pj.jqb.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import me.pj.jqb.data.repo.ConfigRepository
import me.pj.jqb.model.ActionType

class ConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        val repo = ConfigRepository.getInstance(ctx)
        val config = repo.getConfig()

        when (method) {
            "get_config_for_package" -> {
                val packageName = arg ?: return null
                val matchedRule = config.rules.firstOrNull { it.packageNames.contains(packageName) }
                val actionKey = matchedRule?.action ?: config.settings.defaultAction
                val decoyId = matchedRule?.decoyId
                val decoy = config.decoys.firstOrNull { it.id == decoyId }

                return Bundle().apply {
                    putString("action", actionKey)
                    putString("smart_fallback", matchedRule?.smartFallback ?: config.settings.smartDetect.fallbackAction)
                    putBoolean("smart_enabled", config.settings.smartDetect.enabled)
                    putLong("smart_trust_window_ms", config.settings.smartDetect.trustWindowMs)
                    putBoolean("audit_log_enabled", config.settings.auditLog.enabled)

                    if (decoy != null) {
                        putString("decoy_strategy", decoy.strategy)
                        putStringArrayList("decoy_pool", ArrayList(decoy.pool))
                    }
                }
            }
            "is_module_active" -> {
                return Bundle().apply { putBoolean("active", true) }
            }
        }
        return super.call(method, arg, extras)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
