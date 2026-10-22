package me.pj.jqb.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.pj.jqb.data.db.AppDatabase
import me.pj.jqb.data.repo.ConfigRepository
import me.pj.jqb.model.AuditRecord

class AuditLogReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        when (action) {
            ACTION_LOG -> {
                val packageName = intent.getStringExtra("package_name") ?: return
                val appName = intent.getStringExtra("app_name") ?: packageName
                val actionType = intent.getStringExtra("action") ?: "BLOCKED"
                val triggerReason = intent.getStringExtra("trigger_reason") ?: "UNKNOWN"
                val spoofedPayload = intent.getStringExtra("spoofed_payload")

                receiverScope.launch {
                    try {
                        val db = AppDatabase.getInstance(context)
                        db.auditDao().insert(
                            AuditRecord(
                                packageName = packageName,
                                appName = appName,
                                timestamp = System.currentTimeMillis(),
                                action = actionType,
                                triggerReason = triggerReason,
                                spoofedPayload = spoofedPayload
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            ACTION_REQUEST_CONFIG -> {
                receiverScope.launch {
                    try {
                        val repo = ConfigRepository.getInstance(context)
                        repo.broadcastConfigToSystemServer()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_LOG = "me.pj.jqb.ACTION_LOG"
        const val ACTION_REQUEST_CONFIG = "me.pj.jqb.ACTION_REQUEST_CONFIG"
        const val ACTION_CONFIG_UPDATED = "me.pj.jqb.ACTION_CONFIG_UPDATED"
    }
}
