package me.pj.jqb.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.pj.jqb.data.db.AppDatabase
import me.pj.jqb.model.AuditRecord

class AuditLogProvider : ContentProvider() {

    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "log_intercept" && extras != null) {
            val packageName = extras.getString("package_name") ?: return null
            val appName = extras.getString("app_name") ?: packageName
            val action = extras.getString("action") ?: "BLOCKED"
            val triggerReason = extras.getString("trigger_reason") ?: "UNKNOWN"
            val spoofedPayload = extras.getString("spoofed_payload")

            val ctx = context ?: return null
            providerScope.launch {
                try {
                    val db = AppDatabase.getInstance(ctx)
                    db.auditDao().insert(
                        AuditRecord(
                            packageName = packageName,
                            appName = appName,
                            timestamp = System.currentTimeMillis(),
                            action = action,
                            triggerReason = triggerReason,
                            spoofedPayload = spoofedPayload
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return Bundle().apply { putBoolean("success", true) }
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
