package me.pj.jqb.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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
                me.pj.jqb.ClipBoxApp.instance.markSystemHookAlive()
                val packageName = intent.getStringExtra("package_name") ?: return
                val rawAppName = intent.getStringExtra("app_name")
                val actionType = intent.getStringExtra("action") ?: "BLOCKED"
                val triggerReason = intent.getStringExtra("trigger_reason") ?: "lifecycle_resume"
                val spoofedPayload = intent.getStringExtra("spoofed_payload")
                val callingPid = intent.getIntExtra("calling_pid", 0)
                val callingUid = intent.getIntExtra("calling_uid", 0)
                val processName = intent.getStringExtra("process_name") ?: packageName
                val procState = intent.getStringExtra("proc_state") ?: ""

                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val resolvedAppName = if (rawAppName.isNullOrBlank() || rawAppName == packageName) {
                            try {
                                val pm = context.packageManager
                                val info = pm.getApplicationInfo(packageName, 0)
                                pm.getApplicationLabel(info).toString()
                            } catch (_: Exception) {
                                packageName
                            }
                        } else {
                            rawAppName
                        }

                        val db = AppDatabase.getInstance(context)
                        db.auditDao().insert(
                            AuditRecord(
                                packageName = packageName,
                                appName = resolvedAppName,
                                timestamp = System.currentTimeMillis(),
                                action = actionType,
                                triggerReason = triggerReason,
                                spoofedPayload = spoofedPayload,
                                callingPid = callingPid,
                                callingUid = callingUid,
                                processName = processName,
                                procState = procState
                            )
                        )

                        val cfg = ConfigRepository.getInstance(context).getConfig()
                        if (cfg.settings.auditLog.showInterceptToast) {
                            val now = System.currentTimeMillis()
                            if (now - lastToastTime > 3000L) {
                                lastToastTime = now
                                val isEn = me.pj.jqb.ui.resolveIsEnglish(cfg.settings.language)
                                val msg = if (isEn) {
                                    val verbEn = if (actionType == "BLOCKED") "blocked" else "fed decoy"
                                    "ClipBait caught $resolvedAppName reading clipboard ($verbEn)"
                                } else {
                                    val verbZh = if (actionType == "BLOCKED") "已拦回空值" else "已塞假词糊弄"
                                    "ClipBait 抓现行：「$resolvedAppName」刚偷摸剪贴板（$verbZh）"
                                }
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(
                                        context.applicationContext,
                                        msg,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_REQUEST_CONFIG -> {
                me.pj.jqb.ClipBoxApp.instance.markSystemHookAlive()
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val repo = ConfigRepository.getInstance(context)
                        repo.broadcastConfigToSystemServer()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_SYSTEM_HOOK_ALIVE -> {
                me.pj.jqb.ClipBoxApp.instance.markSystemHookAlive()
            }
        }
    }

    companion object {
        const val ACTION_LOG = "me.pj.jqb.ACTION_LOG"
        const val ACTION_REQUEST_CONFIG = "me.pj.jqb.ACTION_REQUEST_CONFIG"
        const val ACTION_CONFIG_UPDATED = "me.pj.jqb.ACTION_CONFIG_UPDATED"
        const val ACTION_SYSTEM_HOOK_ALIVE = "me.pj.jqb.ACTION_SYSTEM_HOOK_ALIVE"

        @Volatile
        private var lastToastTime: Long = 0L
    }
}
