package me.pj.jqb.data.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.pj.jqb.R
import me.pj.jqb.data.db.AppDatabase
import me.pj.jqb.data.repo.ConfigRepository
import me.pj.jqb.model.AuditRecord
import me.pj.jqb.model.PendingAuditItem
import me.pj.jqb.model.TriggerType
import me.pj.jqb.ui.MainActivity

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
                val systemAlertShown = intent.getBooleanExtra("system_alert_shown", false)

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

                        triggerAlertForRecord(
                            context = context,
                            packageName = packageName,
                            appName = resolvedAppName,
                            actionType = actionType,
                            triggerReason = triggerReason,
                            spoofedPayload = spoofedPayload,
                            systemAlertShown = systemAlertShown,
                            forceTest = false
                        )
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
        const val ACTION_TEST_SYSTEM_ALERT = "io.github.pgntgz.clipbait.ACTION_TEST_SYSTEM_ALERT"

        private const val INTERCEPT_CHANNEL_ID = "clipbait_intercept_alerts"

        @Volatile
        private var lastToastTime: Long = 0L

        @Volatile
        private var lastNotificationTime: Long = 0L

        @Volatile
        private var notificationIdSeq: Int = 2000

        fun triggerAlertForRecord(
            context: Context,
            packageName: String,
            appName: String,
            actionType: String,
            triggerReason: String,
            spoofedPayload: String?,
            systemAlertShown: Boolean = false,
            forceTest: Boolean = false,
            overrideMode: String? = null
        ) {
            val cfg = ConfigRepository.getInstance(context).getConfig()
            val mode = overrideMode ?: cfg.settings.auditLog.effectiveAlertMode()
            if (mode == "off") return

            val isEn = me.pj.jqb.ui.resolveIsEnglish(cfg.settings.language)
            val triggerType = TriggerType.fromCode(triggerReason)
            val triggerText = if (isEn) triggerType.displayNameEn else triggerType.displayName
            val now = System.currentTimeMillis()

            when (mode) {
                "toast" -> {
                    if (systemAlertShown && !forceTest) return
                    if (!forceTest && (now - lastToastTime) <= 2500L) return
                    lastToastTime = now
                    val msg = if (isEn) {
                        val verbEn = if (actionType == "BLOCKED") "blocked" else "fed decoy"
                        "ClipBait caught $appName reading clipboard ($verbEn)"
                    } else {
                        val verbZh = if (actionType == "BLOCKED") "已拦回空值" else "已塞假词糊弄"
                        "ClipBait 抓现行：「$appName」刚偷摸剪贴板（$verbZh）"
                    }
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context.applicationContext,
                            msg,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                "notification" -> {
                    if (!forceTest && (now - lastNotificationTime) <= 2000L) return
                    lastNotificationTime = now
                    postInterceptNotification(
                        context = context,
                        packageName = packageName,
                        appName = appName,
                        actionType = actionType,
                        triggerText = triggerText,
                        spoofedPayload = spoofedPayload,
                        isEn = isEn
                    )
                }

                "dialog" -> {
                    // SystemServerHook handles native TYPE_SYSTEM_ALERT dialog directly in system_server.
                    // When testing from Settings UI (forceTest = true), also send broadcast to system_server.
                    if (forceTest) {
                        try {
                            val testIntent = Intent(ACTION_TEST_SYSTEM_ALERT).apply {
                                setPackage("android")
                                putExtra("alert_mode", "dialog")
                                putExtra("package_name", packageName)
                                putExtra("app_name", appName)
                                putExtra("action", actionType)
                                putExtra("trigger_reason", triggerReason)
                                putExtra("spoofed_payload", spoofedPayload)
                            }
                            context.sendBroadcast(testIntent)
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
        }

        private fun postInterceptNotification(
            context: Context,
            packageName: String,
            appName: String,
            actionType: String,
            triggerText: String,
            spoofedPayload: String?,
            isEn: Boolean
        ) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channelName = if (isEn) "Clipboard Intercept Alerts" else "剪贴板抓现行实时通知"
                    val channelDesc = if (isEn) {
                        "Notifies you whenever an app is caught snooping on your clipboard"
                    } else {
                        "当有应用偷读剪贴板被阻断或塞入假词时弹出通知提醒"
                    }
                    val channel = NotificationChannel(
                        INTERCEPT_CHANNEL_ID,
                        channelName,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = channelDesc
                        enableVibration(false)
                    }
                    nm.createNotificationChannel(channel)
                }

                val title = if (isEn) {
                    val verb = if (actionType == "BLOCKED") "Blocked" else "Fed Decoy"
                    "Caught $appName reading clipboard · $verb"
                } else {
                    val verb = if (actionType == "BLOCKED") "已拦回空值" else "已塞假词糊弄"
                    "抓现行：「$appName」偷摸剪贴板 · $verb"
                }

                val shortSummary = if (isEn) {
                    "$triggerText ($packageName)"
                } else {
                    "时机：$triggerText ($packageName)"
                }

                val expandedBody = if (isEn) {
                    buildString {
                        append("Scenario: $triggerText\n")
                        append("Package: $packageName\n")
                        if (actionType == "BLOCKED") {
                            append("Result: Returned empty clipboard")
                        } else if (!spoofedPayload.isNullOrBlank()) {
                            append("Fed decoy: \"$spoofedPayload\"")
                        }
                    }
                } else {
                    buildString {
                        append("触发时机：$triggerText\n")
                        append("应用包名：$packageName\n")
                        if (actionType == "BLOCKED") {
                            append("处置结果：已当场阻断并返回空剪贴板")
                        } else if (!spoofedPayload.isNullOrBlank()) {
                            append("投喂假词：「$spoofedPayload」")
                        }
                    }
                }

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                val contentPendingIntent = PendingIntent.getActivity(context, 0, openIntent, pendingFlags)

                val notification = NotificationCompat.Builder(context, INTERCEPT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_clipbait)
                    .setContentTitle(title)
                    .setContentText(shortSummary)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBody))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent)
                    .build()

                val id = 2000 + (notificationIdSeq++ % 5)
                nm.notify(id, notification)
            } catch (_: Throwable) {
            }
        }
    }
}
