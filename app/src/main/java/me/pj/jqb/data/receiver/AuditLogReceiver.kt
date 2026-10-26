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
import kotlinx.serialization.json.Json
import me.pj.jqb.data.db.AppDatabase
import me.pj.jqb.data.repo.ConfigRepository
import me.pj.jqb.model.AuditRecord
import me.pj.jqb.model.PendingAuditItem

class AuditLogReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        when (action) {
            ACTION_LOG -> {
                me.pj.jqb.ClipBoxApp.instance.markSystemHookAlive()
                val recordId = intent.getStringExtra("record_id")
                val packageName = intent.getStringExtra("package_name") ?: return
                val rawAppName = intent.getStringExtra("app_name")
                val actionType = intent.getStringExtra("action") ?: "BLOCKED"
                val triggerReason = intent.getStringExtra("trigger_reason") ?: "lifecycle_resume"
                val spoofedPayload = intent.getStringExtra("spoofed_payload")
                val callingPid = intent.getIntExtra("calling_pid", 0)
                val callingUid = intent.getIntExtra("calling_uid", 0)
                val processName = intent.getStringExtra("process_name") ?: packageName
                val procState = intent.getStringExtra("proc_state") ?: ""
                val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())

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
                                timestamp = timestamp,
                                action = actionType,
                                triggerReason = triggerReason,
                                spoofedPayload = spoofedPayload,
                                callingPid = callingPid,
                                callingUid = callingUid,
                                processName = processName,
                                procState = procState
                            )
                        )

                        // Acknowledge receipt to system_server so it can remove it from offline buffer
                        if (!recordId.isNullOrBlank()) {
                            try {
                                val ackIntent = Intent(ACTION_LOG_ACK).apply {
                                    setPackage("android")
                                    putExtra("record_id", recordId)
                                }
                                context.sendBroadcast(ackIntent)
                            } catch (_: Throwable) {
                            }
                        }

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
            ACTION_BATCH_LOG -> {
                me.pj.jqb.ClipBoxApp.instance.markSystemHookAlive()
                val batchJson = intent.getStringExtra("batch_json") ?: return
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val items = json.decodeFromString<List<PendingAuditItem>>(batchJson)
                        val db = AppDatabase.getInstance(context)
                        val pm = context.packageManager
                        val recordsToInsert = mutableListOf<AuditRecord>()

                        for (item in items) {
                            if (db.auditDao().countByPackageAndTimestamp(item.packageName, item.timestamp) == 0) {
                                val resolvedAppName = if (item.appName.isBlank() || item.appName == item.packageName) {
                                    try {
                                        val info = pm.getApplicationInfo(item.packageName, 0)
                                        pm.getApplicationLabel(info).toString()
                                    } catch (_: Exception) {
                                        item.packageName
                                    }
                                } else {
                                    item.appName
                                }
                                recordsToInsert.add(
                                    AuditRecord(
                                        packageName = item.packageName,
                                        appName = resolvedAppName,
                                        timestamp = item.timestamp,
                                        action = item.action,
                                        triggerReason = item.triggerReason,
                                        spoofedPayload = item.spoofedPayload,
                                        callingPid = item.callingPid,
                                        callingUid = item.callingUid,
                                        processName = item.processName,
                                        procState = item.procState
                                    )
                                )
                            }
                        }
                        if (recordsToInsert.isNotEmpty()) {
                            db.auditDao().insertAll(recordsToInsert)
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
        const val ACTION_LOG = "io.github.pgntgz.clipbait.ACTION_LOG"
        const val ACTION_BATCH_LOG = "io.github.pgntgz.clipbait.ACTION_BATCH_LOG"
        const val ACTION_REQUEST_CONFIG = "io.github.pgntgz.clipbait.ACTION_REQUEST_CONFIG"
        const val ACTION_CONFIG_UPDATED = "io.github.pgntgz.clipbait.ACTION_CONFIG_UPDATED"
        const val ACTION_SYSTEM_HOOK_ALIVE = "io.github.pgntgz.clipbait.ACTION_SYSTEM_HOOK_ALIVE"
        const val ACTION_PULL_PENDING_LOGS = "io.github.pgntgz.clipbait.ACTION_PULL_PENDING_LOGS"
        const val ACTION_LOG_ACK = "io.github.pgntgz.clipbait.ACTION_LOG_ACK"

        @Volatile
        private var lastToastTime: Long = 0L
    }
}
