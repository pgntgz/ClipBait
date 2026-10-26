package me.pj.jqb.hook

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.AtomicFile
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.pj.jqb.data.receiver.AuditLogReceiver
import me.pj.jqb.model.ConfigModel
import me.pj.jqb.model.PendingAuditItem
import me.pj.jqb.model.TriggerType
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SystemServerHook {

    private const val TAG = "[ClipBox-System]"

    @Volatile
    private var systemContext: Context? = null

    @Volatile
    private var currentConfig: ConfigModel = ConfigModel()

    @Volatile
    private var currentTopPackage: String = ""

    // Tracks when an application's activity was resumed to catch onResume sneak probes
    private val appResumeTimeMap = ConcurrentHashMap<String, Long>()

    // Tracks last clipboard read time per package to identify burst/polling patterns
    private val appLastReadTimeMap = ConcurrentHashMap<String, Long>()

    // Debounces audit log broadcasts so multi-read bursts within 600ms produce 1 clean entry
    private val lastReportTimeMap = ConcurrentHashMap<String, Long>()

    // Caches generated decoy ClipData per package for 1500ms so burst probes get consistent text
    private data class CachedDecoy(val clipData: ClipData?, val timestamp: Long)
    private val decoyCacheMap = ConcurrentHashMap<String, CachedDecoy>()

    // Cache resolved app labels in system_server
    private val appLabelCache = ConcurrentHashMap<String, String>()

    // Cache of verified input method packages to unconditionally bypass
    private val imePackageSet = ConcurrentHashMap.newKeySet<String>()

    // Holds pending audit records when the UI app process is closed/swiped from recents
    private val pendingAuditBuffer = ConcurrentHashMap<String, PendingAuditItem>()
    private const val MAX_PENDING_AUDITS = 500
    private val PENDING_AUDITS_FILE = File("/data/system/clipbait_pending_audits.json")

    private fun loadPendingAuditsFromDisk() {
        try {
            if (PENDING_AUDITS_FILE.exists() && PENDING_AUDITS_FILE.canRead()) {
                val text = PENDING_AUDITS_FILE.readText()
                if (text.isNotBlank()) {
                    val list = json.decodeFromString<List<PendingAuditItem>>(text)
                    for (item in list) {
                        pendingAuditBuffer[item.recordId] = item
                    }
                    XposedBridge.log("$TAG Loaded ${list.size} pending audits from disk.")
                }
            }
        } catch (e: Exception) {
            XposedBridge.log("$TAG loadPendingAuditsFromDisk error: ${e.message}")
        }
    }

    private fun persistPendingAuditsToDisk() {
        try {
            val list = pendingAuditBuffer.values.toList()
            if (list.isEmpty()) {
                if (PENDING_AUDITS_FILE.exists()) {
                    PENDING_AUDITS_FILE.delete()
                }
                return
            }
            val atomicFile = AtomicFile(PENDING_AUDITS_FILE)
            val fos = atomicFile.startWrite()
            try {
                fos.write(json.encodeToString(list).toByteArray())
                atomicFile.finishWrite(fos)
            } catch (e: Exception) {
                atomicFile.failWrite(fos)
            }
        } catch (e: Exception) {
            XposedBridge.log("$TAG persistPendingAuditsToDisk error: ${e.message}")
        }
    }

    private fun addPendingAudit(item: PendingAuditItem) {
        if (pendingAuditBuffer.size >= MAX_PENDING_AUDITS) {
            val sorted = pendingAuditBuffer.values.sortedBy { it.timestamp }
            for (i in 0 until 50.coerceAtMost(sorted.size)) {
                pendingAuditBuffer.remove(sorted[i].recordId)
            }
        }
        pendingAuditBuffer[item.recordId] = item
        persistPendingAuditsToDisk()
    }

    private fun removePendingAudit(recordId: String) {
        if (pendingAuditBuffer.remove(recordId) != null) {
            persistPendingAuditsToDisk()
        }
    }

    private fun flushPendingAudits(ctx: Context) {
        try {
            val items = pendingAuditBuffer.values.toList().sortedBy { it.timestamp }
            if (items.isEmpty()) return
            val jsonStr = json.encodeToString(items)
            val intent = Intent(AuditLogReceiver.ACTION_BATCH_LOG).apply {
                component = ComponentName("io.github.pgntgz.clipbait", "me.pj.jqb.data.receiver.AuditLogReceiver")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra("batch_json", jsonStr)
            }
            safeSendBroadcast(ctx, intent)
            pendingAuditBuffer.clear()
            if (PENDING_AUDITS_FILE.exists()) {
                PENDING_AUDITS_FILE.delete()
            }
            XposedBridge.log("$TAG Flushed ${items.size} pending audits to ClipBait UI.")
        } catch (e: Exception) {
            XposedBridge.log("$TAG flushPendingAudits error: ${e.message}")
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        XposedBridge.log("$TAG Initializing in system_server...")

        // 1. Hook Activity Lifecycle across Android 11-16 (ActivityTaskManagerService & ActivityClientController)
        hookActivityResumeTracking(classLoader)

        // 2. Hook ClipboardService
        hookClipboardService(classLoader)
    }

    private fun hookActivityResumeTracking(classLoader: ClassLoader) {
        val recordActivityResume: (Any?) -> Unit = { activityRecord ->
            if (activityRecord != null) {
                try {
                    val pkg = (XposedHelpers.getObjectField(activityRecord, "packageName") as? String)
                        ?: (XposedHelpers.getObjectField(activityRecord, "mActivityComponent") as? ComponentName)?.packageName
                        ?: (XposedHelpers.callMethod(activityRecord, "getPackageName") as? String)
                    val userId = try {
                        XposedHelpers.getIntField(activityRecord, "mUserId")
                    } catch (_: Throwable) {
                        0
                    }
                    if (!pkg.isNullOrEmpty()) {
                        currentTopPackage = pkg
                        val now = System.currentTimeMillis()
                        appResumeTimeMap["$userId:$pkg"] = now
                        appResumeTimeMap[pkg] = now
                    }
                } catch (_: Throwable) {
                }
            }
        }

        // Hook 1: Android 12-16 ActivityClientController.activityResumed(IBinder token, ...)
        try {
            val accClass = XposedHelpers.findClassIfExists(
                "com.android.server.wm.ActivityClientController",
                classLoader
            )
            val arClass = XposedHelpers.findClassIfExists(
                "com.android.server.wm.ActivityRecord",
                classLoader
            )
            if (accClass != null && arClass != null) {
                for (method in accClass.declaredMethods) {
                    if (method.name == "activityResumed") {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                try {
                                    val token = param.args.firstOrNull() ?: return
                                    val topActivity = XposedHelpers.callStaticMethod(arClass, "forTokenLocked", token)
                                    recordActivityResume(topActivity)
                                } catch (_: Throwable) {
                                }
                            }
                        })
                    }
                }
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG ActivityClientController hook note: ${t.message}")
        }

        // Hook 2: ActivityTaskManagerService (setLastResumedActivityUncheckLocked / activityResumed)
        try {
            val atmClass = XposedHelpers.findClassIfExists(
                "com.android.server.wm.ActivityTaskManagerService",
                classLoader
            )
            if (atmClass != null) {
                for (method in atmClass.declaredMethods) {
                    if (method.name == "setLastResumedActivityUncheckLocked" || method.name == "setResumedActivityUncheckLocked") {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                recordActivityResume(param.args.firstOrNull())
                            }
                        })
                    }
                }
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG ActivityTaskManagerService hook note: ${t.message}")
        }
    }

    private fun hookClipboardService(classLoader: ClassLoader) {
        val clipboardServiceClass = XposedHelpers.findClassIfExists(
            "com.android.server.clipboard.ClipboardService",
            classLoader
        ) ?: run {
            XposedBridge.log("$TAG ClipboardService not found in system_server!")
            return
        }

        XposedHelpers.findAndHookConstructor(
            clipboardServiceClass,
            Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val ctx = param.args[0] as? Context ?: return
                    systemContext = ctx
                    XposedBridge.log("$TAG Captured systemContext from ClipboardService")
                    setupConfigReceiver(ctx)
                    loadConfigFromDiskFallback()
                    loadPendingAuditsFromDisk()
                }
            }
        )

        val classesToHook = mutableListOf<Class<*>>(clipboardServiceClass)
        try {
            val implClass = XposedHelpers.findClass(
                "com.android.server.clipboard.ClipboardService\$ClipboardImpl",
                classLoader
            )
            classesToHook.add(implClass)
        } catch (_: Throwable) {
        }

        for (targetClass in classesToHook) {
            hookClipboardMethodsOnClass(targetClass)
        }
    }

    private fun hookClipboardMethodsOnClass(targetClass: Class<*>) {
        for (method in targetClass.declaredMethods) {
            when (method.name) {
                "getPrimaryClip" -> {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val pkg = param.args.firstOrNull() as? String ?: return
                            if (shouldBypassPackage(pkg)) return

                            val callingPid = Binder.getCallingPid()
                            val callingUid = Binder.getCallingUid()
                            val action = getEffectiveAction(pkg)
                            if (action == "direct") return

                            val probeContext = analyzeProbeContext(pkg, callingPid, callingUid, isReadAction = true)

                            when (action) {
                                "block" -> {
                                    param.result = null
                                    reportIntercept(
                                        pkg = pkg,
                                        action = "BLOCKED",
                                        triggerCode = probeContext.triggerType.code,
                                        payload = null,
                                        callingPid = callingPid,
                                        callingUid = callingUid,
                                        processName = probeContext.processName,
                                        procState = probeContext.procState
                                    )
                                }
                                "spoof" -> {
                                    val decoy = getOrCreateConsistentDecoy(pkg)
                                    param.result = decoy
                                    val text = decoy?.getItemAt(0)?.text?.toString()
                                    reportIntercept(
                                        pkg = pkg,
                                        action = "SPOOFED",
                                        triggerCode = probeContext.triggerType.code,
                                        payload = text,
                                        callingPid = callingPid,
                                        callingUid = callingUid,
                                        processName = probeContext.processName,
                                        procState = probeContext.procState
                                    )
                                }
                                "smart" -> {
                                    val isSneakProbe = isAppInSneakWindow(pkg, probeContext)
                                    if (isSneakProbe) {
                                        val fallback = getSmartFallbackForPackage(pkg)
                                        if (fallback == "spoof") {
                                            val decoy = getOrCreateConsistentDecoy(pkg)
                                            param.result = decoy
                                            val text = decoy?.getItemAt(0)?.text?.toString()
                                            reportIntercept(
                                                pkg = pkg,
                                                action = "SPOOFED",
                                                triggerCode = probeContext.triggerType.code,
                                                payload = text,
                                                callingPid = callingPid,
                                                callingUid = callingUid,
                                                processName = probeContext.processName,
                                                procState = probeContext.procState
                                            )
                                        } else {
                                            param.result = null
                                            reportIntercept(
                                                pkg = pkg,
                                                action = "BLOCKED",
                                                triggerCode = probeContext.triggerType.code,
                                                payload = null,
                                                callingPid = callingPid,
                                                callingUid = callingUid,
                                                processName = probeContext.processName,
                                                procState = probeContext.procState
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    })
                }

                "hasPrimaryClip" -> {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val pkg = param.args.firstOrNull() as? String ?: return
                            if (shouldBypassPackage(pkg)) return

                            val action = getEffectiveAction(pkg)
                            if (action == "block") {
                                param.result = false
                            } else if (action == "spoof") {
                                param.result = true
                            } else if (action == "smart") {
                                val probeContext = analyzeProbeContext(pkg, Binder.getCallingPid(), Binder.getCallingUid(), isReadAction = false)
                                if (isAppInSneakWindow(pkg, probeContext)) {
                                    param.result = getSmartFallbackForPackage(pkg) != "block"
                                }
                            }
                        }
                    })
                }

                "getPrimaryClipDescription" -> {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val pkg = param.args.firstOrNull() as? String ?: return
                            if (shouldBypassPackage(pkg)) return

                            val action = getEffectiveAction(pkg)
                            if (action == "block") {
                                param.result = null
                            } else if (action == "spoof") {
                                param.result = DecoyGenerator.generateClipDescription()
                            } else if (action == "smart") {
                                val probeContext = analyzeProbeContext(pkg, Binder.getCallingPid(), Binder.getCallingUid(), isReadAction = false)
                                if (isAppInSneakWindow(pkg, probeContext)) {
                                    if (getSmartFallbackForPackage(pkg) == "block") {
                                        param.result = null
                                    } else {
                                        param.result = DecoyGenerator.generateClipDescription()
                                    }
                                }
                            }
                        }
                    })
                }
                "addPrimaryClipChangedListener" -> {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val pkg = (param.args.getOrNull(1) as? String)
                                ?: (param.args.firstOrNull() as? String)
                                ?: return
                            if (shouldBypassPackage(pkg)) return

                            val action = getEffectiveAction(pkg)
                            if (action == "block") {
                                // Block rogue app from registering background clipboard change listeners
                                param.result = null
                            }
                        }
                    })
                }
            }
        }
    }

    private data class ProbeContextInfo(
        val triggerType: TriggerType,
        val processName: String,
        val procState: String
    )

    private fun analyzeProbeContext(
        pkg: String,
        callingPid: Int,
        callingUid: Int,
        isReadAction: Boolean = true
    ): ProbeContextInfo {
        val now = System.currentTimeMillis()
        val userId = if (callingUid > 0) callingUid / 100000 else 0
        val userPkgKey = "$userId:$pkg"

        val lastResume = appResumeTimeMap[userPkgKey] ?: appResumeTimeMap[pkg] ?: 0L
        val lastRead = if (isReadAction) {
            appLastReadTimeMap.put(userPkgKey, now) ?: appLastReadTimeMap.put(pkg, now) ?: 0L
        } else {
            appLastReadTimeMap[userPkgKey] ?: appLastReadTimeMap[pkg] ?: 0L
        }
        val elapsedSinceResume = now - lastResume
        val elapsedSinceLastRead = now - lastRead

        val procName = resolveProcessName(callingPid, pkg)
        val isSubProcess = procName.contains(":")

        // Check if the calling process is physically in the foreground (supports fullscreen, split-screen, and floating windows!)
        val isForegroundApp = isProcessForeground(callingUid, pkg)

        return when {
            isSubProcess -> ProbeContextInfo(
                triggerType = TriggerType.BACKGROUND_SILENT,
                processName = procName,
                procState = "BACKGROUND_SUBPROCESS"
            )
            elapsedSinceResume in 0..currentConfig.settings.smartDetect.resumeWindowMs -> ProbeContextInfo(
                triggerType = TriggerType.LIFECYCLE_RESUME,
                processName = procName,
                procState = "TOP_ON_RESUME (${elapsedSinceResume}ms)"
            )
            !isForegroundApp -> ProbeContextInfo(
                triggerType = TriggerType.BACKGROUND_SILENT,
                processName = procName,
                procState = "CACHED_OR_BACKGROUND"
            )
            lastRead > 0 && elapsedSinceLastRead in 1..1000L -> ProbeContextInfo(
                triggerType = TriggerType.ASYNC_POLL,
                processName = procName,
                procState = "BURST_POLL (${elapsedSinceLastRead}ms)"
            )
            else -> ProbeContextInfo(
                triggerType = TriggerType.FOREGROUND_POLL,
                processName = procName,
                procState = "TOP_ACTIVE"
            )
        }
    }

    private fun isProcessForeground(callingUid: Int, pkg: String): Boolean {
        if (currentTopPackage == pkg) return true
        val ctx = systemContext ?: return currentTopPackage.isEmpty()
        if (callingUid <= 0) return currentTopPackage.isEmpty()
        // We are on the app's binder thread; AMS checks the *caller's* permission,
        // so drop to system identity or the query is rejected.
        val token = Binder.clearCallingIdentity()
        return try {
            val am = ctx.getSystemService(android.app.ActivityManager::class.java) ?: return currentTopPackage.isEmpty()
            val importance = XposedHelpers.callMethod(am, "getUidImportance", callingUid) as? Int
                ?: return currentTopPackage.isEmpty()
            importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        } catch (_: Throwable) {
            currentTopPackage.isEmpty()
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun resolveProcessName(pid: Int, fallbackPkg: String): String {
        if (pid <= 0) return fallbackPkg
        try {
            val cmdlineFile = File("/proc/$pid/cmdline")
            if (cmdlineFile.exists()) {
                val raw = cmdlineFile.readBytes()
                val end = raw.indexOf(0.toByte()).let { if (it >= 0) it else raw.size }
                val name = String(raw, 0, end, Charsets.UTF_8).trim()
                if (name.isNotEmpty()) return name
            }
        } catch (_: Throwable) {
        }
        val ctx = systemContext ?: return fallbackPkg
        return try {
            val am = ctx.getSystemService(android.app.ActivityManager::class.java)
            am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: fallbackPkg
        } catch (_: Throwable) {
            fallbackPkg
        }
    }

    private fun resolveAppName(pkg: String): String {
        appLabelCache[pkg]?.let { return it }
        val ctx = systemContext ?: return pkg
        return try {
            val pm = ctx.packageManager
            val appInfo = pm.getApplicationInfo(pkg, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.isNotBlank()) {
                appLabelCache[pkg] = label
                label
            } else {
                pkg
            }
        } catch (_: Throwable) {
            pkg
        }
    }

    @Volatile
    private var imeCacheTime = 0L

    private fun shouldBypassPackage(pkg: String): Boolean {
        if (pkg == "android" ||
            pkg == "com.android.systemui" ||
            pkg == "io.github.pgntgz.clipbait" ||
            pkg == "me.pj.jqb"
        ) {
            return true
        }
        refreshImeCacheIfStale()
        return imePackageSet.contains(pkg)
    }

    /**
     * Only packages that actually register an InputMethod service are trusted.
     * Name-based guesses ("keyboard", ".input") were removed: any app could name itself that way.
     * Refreshed at most once a minute so we don't hit PackageManager on every clipboard read.
     */
    private fun refreshImeCacheIfStale() {
        val now = System.currentTimeMillis()
        if (now - imeCacheTime < 60_000L && imePackageSet.isNotEmpty()) return
        val ctx = systemContext ?: return
        imeCacheTime = now
        val token = Binder.clearCallingIdentity()
        try {
            val services = ctx.packageManager.queryIntentServices(Intent("android.view.InputMethod"), 0)
            val fresh = services.mapNotNull { it.serviceInfo?.packageName }.toSet()
            if (fresh.isNotEmpty()) {
                imePackageSet.retainAll(fresh)
                imePackageSet.addAll(fresh)
            }
        } catch (_: Throwable) {
        } finally {
            Binder.restoreCallingIdentity(token)
        }
        // Fcitx5 always trusted even before the first successful query
        imePackageSet.add("org.fcitx.fcitx5.android")
    }

    private fun getEffectiveAction(pkg: String): String {
        val rule = currentConfig.rules.firstOrNull { it.packageNames.contains(pkg) }
        return rule?.action ?: currentConfig.settings.defaultAction
    }

    private fun getSmartFallbackForPackage(pkg: String): String {
        val rule = currentConfig.rules.firstOrNull { it.packageNames.contains(pkg) }
        return rule?.smartFallback ?: currentConfig.settings.smartDetect.fallbackAction
    }

    private fun getDecoyForPackage(pkg: String) =
        currentConfig.rules.firstOrNull { it.packageNames.contains(pkg) }?.decoyId?.let { id ->
            currentConfig.decoys.firstOrNull { it.id == id }
        } ?: currentConfig.decoys.firstOrNull()

    private fun getOrCreateConsistentDecoy(pkg: String): ClipData? {
        val now = System.currentTimeMillis()
        val cacheDuration = currentConfig.settings.smartDetect.decoyCacheMs.coerceIn(0L, 60_000L)
        val cached = decoyCacheMap[pkg]
        if (cacheDuration > 0L && cached != null && (now - cached.timestamp) in 0..cacheDuration) {
            return cached.clipData
        }
        val decoyConfig = getDecoyForPackage(pkg)
        val generated = DecoyGenerator.generateClipData(
            decoyConfig?.strategy,
            decoyConfig?.pool
        )
        if (cacheDuration > 0L) {
            decoyCacheMap[pkg] = CachedDecoy(generated, now)
        }
        return generated
    }

    private fun isAppInSneakWindow(pkg: String, probeContext: ProbeContextInfo): Boolean {
        return probeContext.triggerType == TriggerType.LIFECYCLE_RESUME ||
                probeContext.triggerType == TriggerType.BACKGROUND_SILENT ||
                probeContext.triggerType == TriggerType.ASYNC_POLL
    }

    private fun reportIntercept(
        pkg: String,
        action: String,
        triggerCode: String,
        payload: String?,
        callingPid: Int,
        callingUid: Int,
        processName: String,
        procState: String
    ) {
        if (!currentConfig.settings.auditLog.enabled) return
        val ctx = systemContext ?: return
        val now = System.currentTimeMillis()
        val lastReport = lastReportTimeMap[pkg] ?: 0L
        if (now - lastReport in 0..600L) {
            // Suppress duplicate log broadcasts within a 600ms multi-read probe burst
            return
        }
        lastReportTimeMap[pkg] = now

        // Run as system: the label lookup and the cross-user broadcast both need it.
        val identity = Binder.clearCallingIdentity()
        try {
            val appName = resolveAppName(pkg)
            val recordId = UUID.randomUUID().toString()
            val pendingItem = PendingAuditItem(
                recordId = recordId,
                packageName = pkg,
                appName = appName,
                action = action,
                triggerReason = triggerCode,
                spoofedPayload = payload,
                callingPid = callingPid,
                callingUid = callingUid,
                processName = processName,
                procState = procState,
                timestamp = now
            )
            addPendingAudit(pendingItem)

            val intent = Intent(AuditLogReceiver.ACTION_LOG).apply {
                component = ComponentName("io.github.pgntgz.clipbait", "me.pj.jqb.data.receiver.AuditLogReceiver")
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra("record_id", recordId)
                putExtra("package_name", pkg)
                putExtra("app_name", appName)
                putExtra("action", action)
                putExtra("trigger_reason", triggerCode)
                putExtra("spoofed_payload", payload)
                putExtra("calling_pid", callingPid)
                putExtra("calling_uid", callingUid)
                putExtra("process_name", processName)
                putExtra("proc_state", procState)
                putExtra("timestamp", now)
            }
            safeSendBroadcast(ctx, intent)
        } catch (e: Exception) {
            XposedBridge.log("$TAG reportIntercept failed: ${e.message}")
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun safeSendBroadcast(ctx: Context, intent: Intent) {
        try {
            val allUser = XposedHelpers.getStaticObjectField(UserHandle::class.java, "ALL") as? UserHandle
            if (allUser != null) {
                XposedHelpers.callMethod(ctx, "sendBroadcastAsUser", intent, allUser)
            } else {
                ctx.sendBroadcast(intent)
            }
        } catch (_: Throwable) {
            ctx.sendBroadcast(intent)
        }
    }

    private fun sendAliveAck(ctx: Context) {
        try {
            val ackIntent = Intent(AuditLogReceiver.ACTION_SYSTEM_HOOK_ALIVE).apply {
                component = ComponentName("io.github.pgntgz.clipbait", "me.pj.jqb.data.receiver.AuditLogReceiver")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            safeSendBroadcast(ctx, ackIntent)
        } catch (_: Throwable) {
        }
    }

    private fun setupConfigReceiver(context: Context) {
        val filter = IntentFilter().apply {
            addAction(AuditLogReceiver.ACTION_CONFIG_UPDATED)
            addAction(AuditLogReceiver.ACTION_PULL_PENDING_LOGS)
            addAction(AuditLogReceiver.ACTION_LOG_ACK)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    AuditLogReceiver.ACTION_CONFIG_UPDATED -> {
                        val configJson = intent.getStringExtra("config_json")
                        if (configJson != null) {
                            updateConfigFromJson(configJson, persistToSystemDisk = true)
                        }
                        sendAliveAck(context)
                        flushPendingAudits(context)
                        XposedBridge.log("$TAG Config updated dynamically via broadcast!")
                    }
                    AuditLogReceiver.ACTION_PULL_PENDING_LOGS -> {
                        sendAliveAck(context)
                        flushPendingAudits(context)
                        XposedBridge.log("$TAG Responded to pull pending logs.")
                    }
                    AuditLogReceiver.ACTION_LOG_ACK -> {
                        val ackId = intent.getStringExtra("record_id")
                        if (!ackId.isNullOrBlank()) {
                            removePendingAudit(ackId)
                        }
                    }
                }
            }
        }

        Handler(Looper.getMainLooper()).post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(
                        receiver,
                        filter,
                        "io.github.pgntgz.clipbait.permission.INTERNAL_IPC",
                        null,
                        Context.RECEIVER_EXPORTED
                    )
                } else {
                    context.registerReceiver(
                        receiver,
                        filter,
                        "io.github.pgntgz.clipbait.permission.INTERNAL_IPC",
                        null
                    )
                }
                val requestIntent = Intent(AuditLogReceiver.ACTION_REQUEST_CONFIG).apply {
                    setPackage("io.github.pgntgz.clipbait")
                }
                safeSendBroadcast(context, requestIntent)
            } catch (_: Throwable) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                    } else {
                        context.registerReceiver(receiver, filter)
                    }
                    val requestIntent = Intent(AuditLogReceiver.ACTION_REQUEST_CONFIG).apply {
                        setPackage("io.github.pgntgz.clipbait")
                    }
                    safeSendBroadcast(context, requestIntent)
                } catch (e: Exception) {
                    XposedBridge.log("$TAG setupConfigReceiver error: ${e.message}")
                }
            }
        }
    }

    private fun loadConfigFromDiskFallback() {
        val candidates = listOf(
            File("/data/system/clipbox_config.json"),
            File("/data/user/0/io.github.pgntgz.clipbait/files/config.json"),
            File("/data/user/0/me.pj.jqb/files/config.json")
        )
        for (file in candidates) {
            try {
                if (file.exists() && file.canRead()) {
                    val content = file.readText()
                    if (content.isNotBlank()) {
                        updateConfigFromJson(content, persistToSystemDisk = false)
                        XposedBridge.log("$TAG Loaded initial config from ${file.absolutePath}")
                        return
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun updateConfigFromJson(jsonString: String, persistToSystemDisk: Boolean = false) {
        try {
            currentConfig = json.decodeFromString(jsonString)
            decoyCacheMap.clear()
            if (persistToSystemDisk) {
                try {
                    val atomicFile = AtomicFile(File("/data/system/clipbox_config.json"))
                    val fos = atomicFile.startWrite()
                    try {
                        fos.write(jsonString.toByteArray(Charsets.UTF_8))
                        atomicFile.finishWrite(fos)
                    } catch (t: Throwable) {
                        atomicFile.failWrite(fos)
                        throw t
                    }
                } catch (_: Throwable) {
                }
            }
        } catch (e: Exception) {
            XposedBridge.log("$TAG Failed to parse updated config: ${e.message}")
        }
    }
}
