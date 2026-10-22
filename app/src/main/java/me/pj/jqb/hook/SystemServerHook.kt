package me.pj.jqb.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlinx.serialization.json.Json
import me.pj.jqb.data.receiver.AuditLogReceiver
import me.pj.jqb.model.ConfigModel
import me.pj.jqb.model.RuleConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object SystemServerHook {

    private const val TAG = "[ClipBox-System]"

    @Volatile
    private var systemContext: Context? = null

    // In-memory cache of rules and settings inside system_server
    @Volatile
    private var currentConfig: ConfigModel = ConfigModel()

    // Tracks when an application's activity was resumed to catch onResume sneak probes
    private val appResumeTimeMap = ConcurrentHashMap<String, Long>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        XposedBridge.log("$TAG Initializing in system_server...")

        // 1. Hook Activity Lifecycle in system_server to track onResume timestamps
        hookActivityResumeTracking(classLoader)

        // 2. Hook ClipboardService
        hookClipboardService(classLoader)
    }

    private fun hookActivityResumeTracking(classLoader: ClassLoader) {
        try {
            // AOSP ActivityRecord: resumeKeyDispatching() or onWindowsVisible() or setResumedActivityUncheckLocked
            val atmClass = XposedHelpers.findClassIfExists(
                "com.android.server.wm.ActivityTaskManagerService",
                classLoader
            )
            if (atmClass != null) {
                // Hook activityResumed(IBinder token, ...)
                for (method in atmClass.declaredMethods) {
                    if (method.name == "activityResumed") {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                try {
                                    // Extract top activity info if possible
                                    val token = param.args.firstOrNull() ?: return
                                    val topActivity = XposedHelpers.callStaticMethod(
                                        XposedHelpers.findClass("com.android.server.wm.ActivityRecord", classLoader),
                                        "forTokenLocked",
                                        token
                                    )
                                    if (topActivity != null) {
                                        val pkg = XposedHelpers.getObjectField(topActivity, "packageName") as? String
                                        if (!pkg.isNullOrEmpty()) {
                                            appResumeTimeMap[pkg] = System.currentTimeMillis()
                                        }
                                    }
                                } catch (t: Throwable) {
                                    // Silently continue
                                }
                            }
                        })
                    }
                }
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Hook Activity tracking warning: ${t.message}")
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

        // Capture Context from ClipboardService constructor
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
                }
            }
        )

        // Classes to hook: ClipboardService and inner ClipboardImpl (which implements IClipboard.Stub in Android 13+)
        val classesToHook = mutableListOf<Class<*>>(clipboardServiceClass)
        try {
            val implClass = XposedHelpers.findClass(
                "com.android.server.clipboard.ClipboardService\$ClipboardImpl",
                classLoader
            )
            classesToHook.add(implClass)
        } catch (t: Throwable) {
            // Older AOSP directly has methods in ClipboardService
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

                            val action = getEffectiveAction(pkg)
                            val triggerReason = detectTriggerReason(pkg)

                            when (action) {
                                "block" -> {
                                    param.result = null
                                    reportIntercept(pkg, "BLOCKED", triggerReason, null)
                                }
                                "spoof" -> {
                                    val decoy = DecoyGenerator.generateClipData("random_natural", null)
                                    param.result = decoy
                                    val text = decoy?.getItemAt(0)?.text?.toString()
                                    reportIntercept(pkg, "SPOOFED", triggerReason, text)
                                }
                                "smart" -> {
                                    val isSneakProbe = isAppInSneakWindow(pkg)
                                    if (isSneakProbe) {
                                        // App just resumed or in background: sneak probe!
                                        val fallback = currentConfig.settings.smartDetect.fallbackAction
                                        if (fallback == "spoof") {
                                            val decoy = DecoyGenerator.generateClipData("random_natural", null)
                                            param.result = decoy
                                            val text = decoy?.getItemAt(0)?.text?.toString()
                                            reportIntercept(pkg, "SPOOFED", "切台/启动无感偷读 (onResume)", text)
                                        } else {
                                            param.result = null
                                            reportIntercept(pkg, "BLOCKED", "切台/启动无感偷读 (onResume)", null)
                                        }
                                    } else {
                                        // Legitimate user paste after using app, pass through!
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
                            } else if (action == "smart" && isAppInSneakWindow(pkg)) {
                                param.result = currentConfig.settings.smartDetect.fallbackAction != "block"
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
                            } else if (action == "smart" && isAppInSneakWindow(pkg)) {
                                if (currentConfig.settings.smartDetect.fallbackAction == "block") {
                                    param.result = null
                                } else {
                                    param.result = DecoyGenerator.generateClipDescription()
                                }
                            }
                        }
                    })
                }
            }
        }
    }

    private fun shouldBypassPackage(pkg: String): Boolean {
        // System and trusted packages always bypass
        return pkg == "android" ||
                pkg == "com.android.systemui" ||
                pkg == "me.pj.jqb" ||
                pkg == "org.fcitx.fcitx5.android" || // Fcitx5 IME
                pkg.contains("inputmethod")
    }

    private fun getEffectiveAction(pkg: String): String {
        val rule = currentConfig.rules.firstOrNull { it.packageNames.contains(pkg) }
        return rule?.action ?: currentConfig.settings.defaultAction
    }

    private fun isAppInSneakWindow(pkg: String): Boolean {
        val lastResume = appResumeTimeMap[pkg] ?: 0L
        val elapsedSinceResume = System.currentTimeMillis() - lastResume
        // If app read clipboard within 1500ms of resuming, it is 100% an onResume stealth probe!
        return elapsedSinceResume in 0..1500L
    }

    private fun detectTriggerReason(pkg: String): String {
        val lastResume = appResumeTimeMap[pkg] ?: 0L
        val elapsed = System.currentTimeMillis() - lastResume
        return if (elapsed in 0..1500L) {
            "切台/启动偷读 (onResume)"
        } else {
            "后台/窗口轮询偷读"
        }
    }

    private fun reportIntercept(pkg: String, action: String, reason: String, payload: String?) {
        val ctx = systemContext ?: return
        try {
            val intent = Intent(AuditLogReceiver.ACTION_LOG).apply {
                setPackage("me.pj.jqb")
                putExtra("package_name", pkg)
                putExtra("action", action)
                putExtra("trigger_reason", reason)
                putExtra("spoofed_payload", payload)
            }
            // Send broadcast from system_server
            safeSendBroadcast(ctx, intent)
        } catch (e: Exception) {
            XposedBridge.log("$TAG reportIntercept failed: ${e.message}")
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
        } catch (t: Throwable) {
            ctx.sendBroadcast(intent)
        }
    }

    private fun setupConfigReceiver(context: Context) {
        val filter = IntentFilter(AuditLogReceiver.ACTION_CONFIG_UPDATED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val configJson = intent?.getStringExtra("config_json") ?: return
                updateConfigFromJson(configJson)
                XposedBridge.log("$TAG Config updated dynamically via broadcast!")
            }
        }

        Handler(Looper.getMainLooper()).post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
                // Request current config from ClipBox
                val requestIntent = Intent(AuditLogReceiver.ACTION_REQUEST_CONFIG).apply {
                    setPackage("me.pj.jqb")
                }
                safeSendBroadcast(context, requestIntent)
            } catch (e: Exception) {
                XposedBridge.log("$TAG setupConfigReceiver error: ${e.message}")
            }
        }
    }

    private fun loadConfigFromDiskFallback() {
        try {
            val file = File("/data/user/0/me.pj.jqb/files/config.json")
            if (file.exists() && file.canRead()) {
                val content = file.readText()
                updateConfigFromJson(content)
                XposedBridge.log("$TAG Loaded initial config directly from disk fallback")
            }
        } catch (e: Exception) {
            // Ignore if restricted
        }
    }

    fun updateConfigFromJson(jsonString: String) {
        try {
            currentConfig = json.decodeFromString(jsonString)
        } catch (e: Exception) {
            XposedBridge.log("$TAG Failed to parse updated config: ${e.message}")
        }
    }
}
