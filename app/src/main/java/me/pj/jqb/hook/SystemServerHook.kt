package me.pj.jqb.hook

import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.UserHandle
import android.util.AtomicFile
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
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
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

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

    // Tracks when an application's window regained focus (e.g. closing dialog/keyboard/notification shade)
    private val appWindowFocusTimeMap = ConcurrentHashMap<String, Long>()

    // Tracks last clipboard read time per package to identify burst/polling patterns
    private val appLastReadTimeMap = ConcurrentHashMap<String, Long>()

    // Tracks when a read for a package was intercepted (blocked/spoofed) to suppress any AOSP toast leak
    private val lastInterceptedReadTimeMap = ConcurrentHashMap<String, Long>()

    // Positive Paste Signals in system_server:
    // 1. Long-press haptic / gesture timestamps per UID and package
    private val uidLongPressTimeMap = ConcurrentHashMap<Int, Long>()
    private val pkgLongPressTimeMap = ConcurrentHashMap<String, Long>()

    // 2. Active PopupWindow / FloatingToolbar / ContextMenu window tracking per package
    private val activePopupWindowTokens = ConcurrentHashMap<IBinder, String>()
    private val pkgPopupWindowTimeMap = ConcurrentHashMap<String, Long>()

    // 3. Physical touch-hold tracking via PointerEventListener
    @Volatile
    private var touchDownTimestamp: Long = 0L
    @Volatile
    private var touchDownX: Float = 0f
    @Volatile
    private var touchDownY: Float = 0f
    @Volatile
    private var touchMovedBeyondSlop: Boolean = false
    @Volatile
    private var lastPhysicalLongPressTime: Long = 0L

    // 4. Zero-Trust Copy / Paste Token Burn tracking:
    // Tracks when setPrimaryClip / clearPrimaryClip happened so apps cannot piggyback on a "Copy" long-press
    private val lastSetClipTimeMap = ConcurrentHashMap<String, Long>()
    @Volatile
    private var globalLastSetClipTime: Long = 0L

    // Tracks when a verified paste read started so the paste token is burned after a short 280ms multi-read window
    private val lastVerifiedPasteTimeMap = ConcurrentHashMap<String, Long>()

    // Tracks system-level intercept alert cooldowns & active dialog reference
    @Volatile
    private var lastSystemToastTime: Long = 0L
    private val lastSystemDialogTimeMap = ConcurrentHashMap<String, Long>()
    @Volatile
    private var activeSystemAlertDialog: AlertDialog? = null

    private fun clearAllPasteSignals() {
        uidLongPressTimeMap.clear()
        pkgLongPressTimeMap.clear()
        pkgPopupWindowTimeMap.clear()
        activePopupWindowTokens.clear()
        lastVerifiedPasteTimeMap.clear()
        lastPhysicalLongPressTime = 0L
        touchDownTimestamp = 0L
    }

    private fun isExplicitPasteToolbarTitle(title: String): Boolean {
        if (title.isEmpty()) return false
        return title.contains("FloatingToolbar", ignoreCase = true) ||
            title.contains("PastePopupWindow", ignoreCase = true) ||
            title.contains("SelectPopupWindow", ignoreCase = true) ||
            title.contains("ContextMenu", ignoreCase = true) ||
            title.contains("Paste", ignoreCase = true)
    }

    private fun hasRecentLongPressOrHandleSignal(pkg: String, uid: Int, now: Long): Boolean {
        val lastUidLp = if (uid > 0) (uidLongPressTimeMap[uid] ?: 0L) else 0L
        val lastPkgLp = pkgLongPressTimeMap[pkg] ?: 0L
        val lastLp = maxOf(lastUidLp, lastPkgLp, lastPhysicalLongPressTime)
        if (lastLp > 0L && (now - lastLp) in 0..2200L) {
            return true
        }
        if (!touchMovedBeyondSlop && touchDownTimestamp > 0L && (now - touchDownTimestamp) >= 300L) {
            return true
        }
        return false
    }

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

        // 2. Hook WindowManagerService & Haptics to track PopupWindows (Paste menu), Long-Presses, and Window Focus
        hookWindowAndPasteSignals(classLoader)

        // 3. Hook ClipboardService
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
                        val prevTop = currentTopPackage
                        currentTopPackage = pkg
                        val now = System.currentTimeMillis()
                        appResumeTimeMap["$userId:$pkg"] = now
                        appResumeTimeMap[pkg] = now
                        if (prevTop != pkg) {
                            // Clear any stale long-press state when switching apps
                            pkgLongPressTimeMap.remove(pkg)
                            lastPhysicalLongPressTime = 0L
                        }
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

    /**
     * Tracks positive user paste signals inside system_server so Smart mode never mistakes normal
     * screen interactions (tapping WeChat chat input box, typing, clicking Send, scrolling) for a paste:
     * 1. PopupWindow / FloatingToolbar / ContextMenu windows added or clicked via WindowManagerService.
     * 2. Long-press haptic feedback (HapticFeedbackConstants.LONG_PRESS / TEXT_HANDLE_MOVE).
     * 3. Stationary touch-hold >= 300ms via PointerEventListener.
     * 4. Window focus transitions (updateFocusedWindowLocked) to catch onWindowFocusChanged probes.
     */
    private fun hookWindowAndPasteSignals(classLoader: ClassLoader) {
        val wmsClass = XposedHelpers.findClassIfExists(
            "com.android.server.wm.WindowManagerService",
            classLoader
        )
        if (wmsClass != null) {
            for (method in wmsClass.declaredMethods) {
                when (method.name) {
                    "addWindow" -> {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                try {
                                    val attrs = param.args.firstOrNull { it is WindowManager.LayoutParams }
                                        as? WindowManager.LayoutParams ?: return
                                    val pkg = attrs.packageName ?: return
                                    val title = attrs.title?.toString() ?: ""
                                    val now = System.currentTimeMillis()
                                    val callingUid = Binder.getCallingUid()
                                    val isExplicitToolbar = isExplicitPasteToolbarTitle(title)
                                    val isGenericPopup = attrs.type in WindowManager.LayoutParams.FIRST_SUB_WINDOW..WindowManager.LayoutParams.LAST_SUB_WINDOW ||
                                        title.contains("PopupWindow", ignoreCase = true)

                                    // Zero-Trust Popup Check:
                                    // Generic PopupWindows (like WeChat's '+' menu or Taobao promotional popups)
                                    // are ONLY treated as paste menus if preceded by a long-press or text-handle move!
                                    if (isExplicitToolbar || (isGenericPopup && hasRecentLongPressOrHandleSignal(pkg, callingUid, now))) {
                                        pkgPopupWindowTimeMap[pkg] = now
                                        val clientBinder = (param.args.getOrNull(1) as? android.os.IInterface)?.asBinder()
                                            ?: (param.args.getOrNull(1) as? IBinder)
                                        if (clientBinder != null) {
                                            activePopupWindowTokens[clientBinder] = pkg
                                        }
                                    }
                                } catch (_: Throwable) {
                                }
                            }
                        })
                    }
                    "relayoutWindow" -> {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                try {
                                    val now = System.currentTimeMillis()
                                    val clientBinder = (param.args.getOrNull(1) as? android.os.IInterface)?.asBinder()
                                        ?: (param.args.getOrNull(1) as? IBinder)
                                    if (clientBinder != null) {
                                        val popupPkg = activePopupWindowTokens[clientBinder]
                                        if (popupPkg != null) {
                                            pkgPopupWindowTimeMap[popupPkg] = now
                                        }
                                    }
                                    val attrs = param.args.firstOrNull { it is WindowManager.LayoutParams }
                                        as? WindowManager.LayoutParams
                                    if (attrs != null) {
                                        val pkg = attrs.packageName
                                        val title = attrs.title?.toString() ?: ""
                                        if (!pkg.isNullOrEmpty() && isExplicitPasteToolbarTitle(title)) {
                                            pkgPopupWindowTimeMap[pkg] = now
                                        }
                                    }
                                } catch (_: Throwable) {
                                }
                            }
                        })
                    }
                    "removeWindow", "removeClientToken" -> {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                try {
                                    for (arg in param.args) {
                                        val binder = (arg as? android.os.IInterface)?.asBinder()
                                            ?: (arg as? IBinder)
                                        if (binder != null) {
                                            val pkg = activePopupWindowTokens.remove(binder)
                                            if (pkg != null) {
                                                // Refresh timestamp on verified paste popup dismissal so the "Paste" button's
                                                // onClick handler (which runs right as the popup closes) is recognized.
                                                pkgPopupWindowTimeMap[pkg] = System.currentTimeMillis()
                                            }
                                        }
                                    }
                                } catch (_: Throwable) {
                                }
                            }
                        })
                    }
                    "updateFocusedWindowLocked" -> {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                try {
                                    val wms = param.thisObject ?: return
                                    registerPointerLongPressMonitor(wms, classLoader)
                                    val focusedWindow = XposedHelpers.getObjectField(wms, "mCurrentFocus") ?: return
                                    val pkg = (XposedHelpers.callMethod(focusedWindow, "getOwningPackage") as? String) ?: return
                                    val attrs = XposedHelpers.getObjectField(focusedWindow, "mAttrs") as? WindowManager.LayoutParams
                                    val title = attrs?.title?.toString() ?: ""
                                    val isSubWindow = attrs != null &&
                                        attrs.type in WindowManager.LayoutParams.FIRST_SUB_WINDOW..WindowManager.LayoutParams.LAST_SUB_WINDOW
                                    val now = System.currentTimeMillis()
                                    if (isExplicitPasteToolbarTitle(title)) {
                                        pkgPopupWindowTimeMap[pkg] = now
                                    } else if (!isSubWindow && !shouldBypassPackage(pkg)) {
                                        val lastFocus = appWindowFocusTimeMap[pkg] ?: 0L
                                        if (now - lastFocus > 800L) {
                                            appWindowFocusTimeMap[pkg] = now
                                        }
                                    }
                                } catch (_: Throwable) {
                                }
                            }
                        })
                    }
                    "systemReady" -> {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                registerPointerLongPressMonitor(param.thisObject, classLoader)
                            }
                        })
                    }
                }
            }
        }

        // Hook performHapticFeedback / performHapticFeedbackAsync across Session, WMS, and PhoneWindowManager
        val hapticTargets = listOf(
            "com.android.server.wm.Session",
            "com.android.server.wm.WindowManagerService",
            "com.android.server.policy.PhoneWindowManager"
        )
        for (className in hapticTargets) {
            try {
                val cls = XposedHelpers.findClassIfExists(className, classLoader) ?: continue
                for (method in cls.declaredMethods) {
                    if (method.name == "performHapticFeedback" || method.name == "performHapticFeedbackAsync") {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                try {
                                    val effectId = param.args.firstOrNull { it is Int } as? Int ?: return
                                    // LONG_PRESS = 0, TEXT_HANDLE_MOVE = 9
                                    if (effectId == HapticFeedbackConstants.LONG_PRESS ||
                                        effectId == HapticFeedbackConstants.TEXT_HANDLE_MOVE ||
                                        effectId == 9
                                    ) {
                                        val now = System.currentTimeMillis()
                                        val uid = Binder.getCallingUid()
                                        if (uid > 0) {
                                            uidLongPressTimeMap[uid] = now
                                        }
                                        val pkg = param.args.firstOrNull { it is String } as? String
                                            ?: currentTopPackage
                                        if (pkg.isNotEmpty()) {
                                            pkgLongPressTimeMap[pkg] = now
                                        }
                                        lastPhysicalLongPressTime = now
                                    }
                                } catch (_: Throwable) {
                                }
                            }
                        })
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    @Volatile
    private var pointerMonitorRegistered = false

    private fun registerPointerLongPressMonitor(wms: Any?, classLoader: ClassLoader) {
        if (wms == null || pointerMonitorRegistered) return
        pointerMonitorRegistered = true
        try {
            val listenerInterface = XposedHelpers.findClassIfExists(
                "android.view.WindowManagerPolicyConstants\$PointerEventListener",
                classLoader
            ) ?: return
            val proxy = Proxy.newProxyInstance(
                classLoader,
                arrayOf(listenerInterface)
            ) { _, method, args ->
                if (method.name == "onPointerEvent" && args != null && args.isNotEmpty()) {
                    val event = args[0] as? MotionEvent
                    if (event != null) {
                        val now = System.currentTimeMillis()
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                touchDownTimestamp = now
                                touchDownX = event.rawX
                                touchDownY = event.rawY
                                touchMovedBeyondSlop = false
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (!touchMovedBeyondSlop) {
                                    if (abs(event.rawX - touchDownX) > 42f || abs(event.rawY - touchDownY) > 42f) {
                                        touchMovedBeyondSlop = true
                                    } else if (touchDownTimestamp > 0L && (now - touchDownTimestamp) >= 300L) {
                                        lastPhysicalLongPressTime = now
                                        if (currentTopPackage.isNotEmpty()) {
                                            pkgLongPressTimeMap[currentTopPackage] = now
                                        }
                                    }
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                if (!touchMovedBeyondSlop && touchDownTimestamp > 0L && (now - touchDownTimestamp) >= 300L) {
                                    lastPhysicalLongPressTime = now
                                    if (currentTopPackage.isNotEmpty()) {
                                        pkgLongPressTimeMap[currentTopPackage] = now
                                    }
                                }
                                touchDownTimestamp = 0L
                                touchMovedBeyondSlop = false
                            }
                        }
                    }
                }
                null
            }
            // Try registerPointerEventListener(listener, displayId = 0) or registerPointerEventListener(listener)
            val registered = try {
                XposedHelpers.callMethod(wms, "registerPointerEventListener", proxy, 0)
                true
            } catch (_: Throwable) {
                try {
                    XposedHelpers.callMethod(wms, "registerPointerEventListener", proxy)
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            if (registered) {
                XposedBridge.log("$TAG Registered PointerEventListener for long-press paste detection.")
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG PointerEventListener registration note: ${t.message}")
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

        // Defense-in-depth: suppress AOSP's "App pasted from your clipboard" toast if we just blocked/spoofed that package
        for (method in clipboardServiceClass.declaredMethods) {
            if (method.name == "showAccessNotificationLocked" || method.name == "notifyTextClassifierLocked") {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args.firstOrNull { it is String } as? String ?: return
                        if (shouldBypassPackage(pkg)) return
                        val action = getEffectiveAction(pkg)
                        val lastIntercept = lastInterceptedReadTimeMap[pkg] ?: 0L
                        if (action == "block" || action == "spoof" || (System.currentTimeMillis() - lastIntercept) in 0..1500L) {
                            param.result = null
                        }
                    }
                })
            }
        }

        val implClass = XposedHelpers.findClassIfExists(
            "com.android.server.clipboard.ClipboardService\$ClipboardImpl",
            classLoader
        )
        if (implClass != null) {
            hookClipboardMethodsOnClass(implClass)
        } else {
            hookClipboardMethodsOnClass(clipboardServiceClass)
        }
    }

    private fun extractCallingPackage(param: XC_MethodHook.MethodHookParam, callingUid: Int): String? {
        for (arg in param.args) {
            if (arg is String && arg.contains(".") && !arg.startsWith("android.intent")) {
                return arg
            }
        }
        val firstStr = param.args.firstOrNull { it is String } as? String
        if (!firstStr.isNullOrBlank()) return firstStr
        val ctx = systemContext ?: return null
        if (callingUid <= 0) return null
        val token = Binder.clearCallingIdentity()
        return try {
            ctx.packageManager.getPackagesForUid(callingUid)?.firstOrNull()
        } catch (_: Throwable) {
            null
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun hookClipboardMethodsOnClass(targetClass: Class<*>) {
        for (method in targetClass.declaredMethods) {
            when (method.name) {
                "setPrimaryClip", "setPrimaryClipAsPackage", "clearPrimaryClip" -> {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            // Zero-Trust Anti-Copy-Piggyback:
                            // The instant the user copies text (setPrimaryClip) or clears the clipboard,
                            // any preceding long-press or popup window was for COPYING, not pasting!
                            // Immediately wipe all paste tokens and record lastSetClipTime so internal SDKs
                            // or OnPrimaryClipChangedListeners reading back the clipboard within 850ms are caught!
                            val now = System.currentTimeMillis()
                            clearAllPasteSignals()
                            globalLastSetClipTime = now
                            val callingUid = Binder.getCallingUid()
                            val pkg = extractCallingPackage(param, callingUid)
                            if (!pkg.isNullOrEmpty()) {
                                lastSetClipTimeMap[pkg] = now
                            }
                        }
                    })
                }

                "getPrimaryClip" -> {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val callingPid = Binder.getCallingPid()
                            val callingUid = Binder.getCallingUid()
                            val pkg = extractCallingPackage(param, callingUid) ?: return
                            if (shouldBypassPackage(pkg)) return

                            val action = getEffectiveAction(pkg)
                            if (action == "direct") return

                            val probeContext = analyzeProbeContext(pkg, callingPid, callingUid, isReadAction = true)

                            when (action) {
                                "block" -> {
                                    lastInterceptedReadTimeMap[pkg] = System.currentTimeMillis()
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
                                    lastInterceptedReadTimeMap[pkg] = System.currentTimeMillis()
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
                                        lastInterceptedReadTimeMap[pkg] = System.currentTimeMillis()
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
                                    } else {
                                        // Verified paste read allowed: record timestamp so the paste token
                                        // is consumed/burned after a short 300ms multi-item paste burst window.
                                        val now = System.currentTimeMillis()
                                        val prevPaste = lastVerifiedPasteTimeMap[pkg] ?: 0L
                                        if (now - prevPaste > 300L) {
                                            lastVerifiedPasteTimeMap[pkg] = now
                                        }
                                    }
                                }
                            }
                        }
                    })
                }

                "hasPrimaryClip", "hasClipboardText" -> {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val callingUid = Binder.getCallingUid()
                            val pkg = extractCallingPackage(param, callingUid) ?: return
                            if (shouldBypassPackage(pkg)) return

                            val action = getEffectiveAction(pkg)
                            if (action == "block") {
                                param.result = false
                            } else if (action == "spoof") {
                                param.result = true
                            } else if (action == "smart") {
                                val probeContext = analyzeProbeContext(pkg, Binder.getCallingPid(), callingUid, isReadAction = false)
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
                            val callingUid = Binder.getCallingUid()
                            val pkg = extractCallingPackage(param, callingUid) ?: return
                            if (shouldBypassPackage(pkg)) return

                            val action = getEffectiveAction(pkg)
                            if (action == "block") {
                                param.result = null
                            } else if (action == "spoof") {
                                param.result = DecoyGenerator.generateClipDescription()
                            } else if (action == "smart") {
                                val probeContext = analyzeProbeContext(pkg, Binder.getCallingPid(), callingUid, isReadAction = false)
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

                "getPrimaryClipSource" -> {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val callingUid = Binder.getCallingUid()
                            val pkg = extractCallingPackage(param, callingUid) ?: return
                            if (shouldBypassPackage(pkg)) return

                            val action = getEffectiveAction(pkg)
                            if (action == "block" || action == "spoof") {
                                param.result = null
                            } else if (action == "smart") {
                                val probeContext = analyzeProbeContext(pkg, Binder.getCallingPid(), callingUid, isReadAction = false)
                                if (isAppInSneakWindow(pkg, probeContext)) {
                                    param.result = null
                                }
                            }
                        }
                    })
                }

                "addPrimaryClipChangedListener" -> {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val callingUid = Binder.getCallingUid()
                            val pkg = extractCallingPackage(param, callingUid) ?: return
                            if (shouldBypassPackage(pkg)) return

                            val action = getEffectiveAction(pkg)
                            if (action == "block" || action == "spoof" || action == "smart") {
                                // Zero-Trust Listener Blinding:
                                // Prevent apps in Block, Spoof, or Smart mode from passively listening to
                                // real-time clipboard change events when the user copies text in any app!
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

    private fun isDeviceInteractiveAndUnlocked(): Boolean {
        val ctx = systemContext ?: return true
        val token = Binder.clearCallingIdentity()
        return try {
            val pm = ctx.getSystemService(PowerManager::class.java)
            if (pm != null && !pm.isInteractive) return false
            val km = ctx.getSystemService(KeyguardManager::class.java)
            if (km != null && km.isKeyguardLocked) return false
            true
        } catch (_: Throwable) {
            true
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    /**
     * Checks whether the calling foreground app has a genuine user paste signal:
     * 1. Device screen must be interactive and unlocked.
     * 2. Must NOT be within 850ms after a setPrimaryClip ("Copy") call (defeats post-copy self-reads).
     * 3. If a verified paste read already occurred > 320ms ago without a newer gesture, the old token is burned.
     * 4. Requires an active verified paste PopupWindow / FloatingToolbar, a just-dismissed paste popup (<= 1800ms),
     *    or a Long-Press / Text-Handle gesture within the last 4000ms.
     */
    private fun hasVerifiedUserPasteSignal(pkg: String, callingUid: Int, now: Long): Boolean {
        if (!isDeviceInteractiveAndUnlocked()) return false

        // Defeat post-copy self-read / SDK piggyback right after setPrimaryClip
        val lastPkgSet = lastSetClipTimeMap[pkg] ?: 0L
        if ((now - globalLastSetClipTime) in 0..850L || (now - lastPkgSet) in 0..1200L) {
            return false
        }

        val lastPasteRead = lastVerifiedPasteTimeMap[pkg] ?: 0L
        val withinSamePasteBurst = lastPasteRead > 0L && (now - lastPasteRead) in 0..320L
        if (withinSamePasteBurst) {
            return true
        }

        if (activePopupWindowTokens.containsValue(pkg)) {
            return true
        }
        val lastPopup = pkgPopupWindowTimeMap[pkg] ?: 0L
        if (lastPopup > lastPasteRead && (now - lastPopup) in 0..1800L) {
            return true
        }
        val lastUidLp = if (callingUid > 0) (uidLongPressTimeMap[callingUid] ?: 0L) else 0L
        val lastPkgLp = pkgLongPressTimeMap[pkg] ?: 0L
        val lastLp = maxOf(lastUidLp, lastPkgLp, lastPhysicalLongPressTime)
        if (lastLp > lastPasteRead && (now - lastLp) in 0..4000L) {
            return true
        }
        if (!touchMovedBeyondSlop && touchDownTimestamp > lastPasteRead && (now - touchDownTimestamp) >= 300L) {
            return true
        }
        return false
    }

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
        val lastFocus = appWindowFocusTimeMap[pkg] ?: 0L
        val lastRead = if (isReadAction) {
            appLastReadTimeMap.put(userPkgKey, now) ?: appLastReadTimeMap.put(pkg, now) ?: 0L
        } else {
            appLastReadTimeMap[userPkgKey] ?: appLastReadTimeMap[pkg] ?: 0L
        }
        val elapsedSinceResume = now - lastResume
        val elapsedSinceFocus = now - lastFocus
        val elapsedSinceLastRead = now - lastRead

        val procName = resolveProcessName(callingPid, pkg)
        val isSubProcess = procName.contains(":")

        // Check if the calling process is physically in the foreground (supports fullscreen, split-screen, and floating windows!)
        val isForegroundApp = isProcessForeground(callingUid, pkg)

        // Check if the user actually performed a paste gesture (long-press / popup paste menu)
        val isVerifiedPaste = isForegroundApp && !isSubProcess && hasVerifiedUserPasteSignal(pkg, callingUid, now)

        return when {
            isSubProcess -> ProbeContextInfo(
                triggerType = TriggerType.BACKGROUND_SILENT,
                processName = procName,
                procState = "BACKGROUND_SUBPROCESS"
            )
            !isForegroundApp -> ProbeContextInfo(
                triggerType = TriggerType.BACKGROUND_SILENT,
                processName = procName,
                procState = "CACHED_OR_BACKGROUND"
            )
            isVerifiedPaste -> ProbeContextInfo(
                triggerType = TriggerType.CONTEXT_MENU_PASTE,
                processName = procName,
                procState = "USER_PASTE_MENU"
            )
            elapsedSinceResume in 0..currentConfig.settings.smartDetect.resumeWindowMs -> ProbeContextInfo(
                triggerType = TriggerType.LIFECYCLE_RESUME,
                processName = procName,
                procState = "TOP_ON_RESUME (${elapsedSinceResume}ms)"
            )
            elapsedSinceFocus in 0..1500L -> ProbeContextInfo(
                triggerType = TriggerType.WINDOW_FOCUS,
                processName = procName,
                procState = "WINDOW_FOCUS (${elapsedSinceFocus}ms)"
            )
            lastRead > 0 && elapsedSinceLastRead in 1..1000L -> ProbeContextInfo(
                triggerType = TriggerType.ASYNC_POLL,
                processName = procName,
                procState = "BURST_POLL (${elapsedSinceLastRead}ms)"
            )
            else -> ProbeContextInfo(
                triggerType = TriggerType.FOREGROUND_POLL,
                processName = procName,
                procState = "TOP_UNSOLICITED_PROBE"
            )
        }
    }

    private fun isProcessForeground(callingUid: Int, pkg: String): Boolean {
        if (!isDeviceInteractiveAndUnlocked()) return false
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
        // Default-deny in Smart mode unless positively verified as a user paste gesture!
        return !probeContext.triggerType.isUserInitiated
    }

    private fun isEnglishConfig(): Boolean {
        val lang = currentConfig.settings.language
        if (lang == "en") return true
        if (lang == "zh") return false
        return java.util.Locale.getDefault().language.startsWith("en", ignoreCase = true)
    }

    private fun triggerLabel(triggerCode: String, isEn: Boolean): String {
        val type = TriggerType.fromCode(triggerCode)
        return if (isEn) type.displayNameEn else type.displayName
    }

    private fun showSystemInterceptAlert(
        ctx: Context,
        mode: String,
        pkg: String,
        appName: String,
        action: String,
        triggerCode: String,
        payload: String?,
        forceTest: Boolean = false
    ): Boolean {
        if (mode != "toast" && mode != "dialog") return false
        val now = System.currentTimeMillis()
        val isEn = isEnglishConfig()

        if (mode == "toast") {
            if (!forceTest && (now - lastSystemToastTime) in 0..2500L) return true
            lastSystemToastTime = now
            val msg = if (isEn) {
                if (action == "BLOCKED") {
                    "ClipBait blocked $appName from reading the clipboard"
                } else {
                    "ClipBait sent a decoy to $appName"
                }
            } else {
                if (action == "BLOCKED") {
                    "ClipBait：已阻断 $appName 读取剪贴板"
                } else {
                    "ClipBait：已向 $appName 返回假内容"
                }
            }
            Handler(Looper.getMainLooper()).post {
                try {
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                } catch (_: Throwable) {
                }
            }
            return true
        }

        // mode == "dialog": Native AOSP System-Style Alert Dialog directly from system_server
        val lastPkgDialog = lastSystemDialogTimeMap[pkg] ?: 0L
        if (!forceTest && (now - lastPkgDialog) in 0..6000L) return true
        lastSystemDialogTimeMap[pkg] = now

        val triggerText = triggerLabel(triggerCode, isEn)
        val title = if (isEn) {
            "ClipBait intercept"
        } else {
            "ClipBait 拦截提醒"
        }
        val body = if (isEn) {
            buildString {
                append("App: $appName ($pkg)\n")
                append("Context: $triggerText\n")
                if (action == "BLOCKED") {
                    append("Action: Blocked")
                } else {
                    append("Action: Spoofed")
                    if (!payload.isNullOrBlank()) {
                        val preview = if (payload.length > 60) payload.take(60) + "…" else payload
                        append("\nDecoy: $preview")
                    }
                }
            }
        } else {
            buildString {
                append("应用：$appName ($pkg)\n")
                append("场景：$triggerText\n")
                if (action == "BLOCKED") {
                    append("处理：阻断")
                } else {
                    append("处理：伪装")
                    if (!payload.isNullOrBlank()) {
                        val preview = if (payload.length > 60) payload.take(60) + "…" else payload
                        append("\n假内容：$preview")
                    }
                }
            }
        }
        val btnText = if (isEn) "OK" else "确定"

        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                activeSystemAlertDialog?.let { prev ->
                    if (prev.isShowing) prev.dismiss()
                }
            } catch (_: Throwable) {
            }
            try {
                val nightMode = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
                val themeRes = if (nightMode) {
                    android.R.style.Theme_DeviceDefault_Dialog_Alert
                } else {
                    android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                }
                val dialog = AlertDialog.Builder(ctx, themeRes)
                    .setTitle(title)
                    .setMessage(body)
                    .setCancelable(true)
                    .setPositiveButton(btnText) { d, _ -> d.dismiss() }
                    .create()

                dialog.window?.apply {
                    setType(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                        } else {
                            @Suppress("DEPRECATION")
                            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                        }
                    )
                    addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    )
                }
                dialog.setCanceledOnTouchOutside(true)
                dialog.show()
                activeSystemAlertDialog = dialog

                // Auto-dismiss after 4.5 seconds so it never blocks the screen if the user ignores it
                mainHandler.postDelayed({
                    try {
                        if (dialog.isShowing) {
                            dialog.dismiss()
                        }
                    } catch (_: Throwable) {
                    }
                }, 4500L)
            } catch (t: Throwable) {
                XposedBridge.log("$TAG System AlertDialog fallback to Toast: ${t.message}")
                try {
                    Toast.makeText(ctx, "$title\n$body", Toast.LENGTH_LONG).show()
                } catch (_: Throwable) {
                }
            }
        }
        return true
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
        val alertMode = currentConfig.settings.auditLog.effectiveAlertMode()
        if (!currentConfig.settings.auditLog.enabled && alertMode == "off") return
        val ctx = systemContext ?: return
        val now = System.currentTimeMillis()
        val lastReport = lastReportTimeMap[pkg] ?: 0L
        if (now - lastReport in 0..600L) {
            // Suppress duplicate log broadcasts within a 600ms multi-read probe burst
            return
        }
        lastReportTimeMap[pkg] = now

        // Run as system: the label lookup, system alert dialog/toast, and cross-user broadcast all need it.
        val identity = Binder.clearCallingIdentity()
        try {
            val appName = resolveAppName(pkg)
            val systemAlertShown = showSystemInterceptAlert(
                ctx = ctx,
                mode = alertMode,
                pkg = pkg,
                appName = appName,
                action = action,
                triggerCode = triggerCode,
                payload = payload
            )

            if (!currentConfig.settings.auditLog.enabled) return

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
                putExtra("system_alert_shown", systemAlertShown)
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
            addAction(AuditLogReceiver.ACTION_TEST_SYSTEM_ALERT)
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
                    AuditLogReceiver.ACTION_TEST_SYSTEM_ALERT -> {
                        val mode = intent.getStringExtra("alert_mode") ?: currentConfig.settings.auditLog.effectiveAlertMode()
                        val pkg = intent.getStringExtra("package_name") ?: "com.tencent.mm"
                        val appName = intent.getStringExtra("app_name") ?: "微信"
                        val action = intent.getStringExtra("action") ?: "SPOOFED"
                        val triggerCode = intent.getStringExtra("trigger_reason") ?: TriggerType.FOREGROUND_POLL.code
                        val payload = intent.getStringExtra("spoofed_payload")
                        showSystemInterceptAlert(
                            ctx = context,
                            mode = mode,
                            pkg = pkg,
                            appName = appName,
                            action = action,
                            triggerCode = triggerCode,
                            payload = payload,
                            forceTest = true
                        )
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
