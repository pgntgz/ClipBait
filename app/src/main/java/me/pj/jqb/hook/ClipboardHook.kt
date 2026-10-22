package me.pj.jqb.hook

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.MotionEvent
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import me.pj.jqb.model.TriggerType

object ClipboardHook {

    @Volatile
    private var appContext: Context? = null
    private var appName: String = ""

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName

        // 1. Hook Application to capture Context
        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.thisObject as? Application ?: return
                    appContext = app.applicationContext
                    try {
                        appName = app.packageManager.getApplicationLabel(app.applicationInfo).toString()
                    } catch (e: Exception) {
                        appName = packageName
                    }
                }
            }
        )

        // 2. Hook View.dispatchTouchEvent to record user touch interaction window
        try {
            XposedHelpers.findAndHookMethod(
                View::class.java,
                "dispatchTouchEvent",
                MotionEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val event = param.args[0] as? MotionEvent ?: return
                        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
                            CallStackAnalyzer.recordUserTouch()
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            // Some specialized views might fail, safely ignore
        }

        // 3. Hook ClipboardManager.getPrimaryClip()
        try {
            XposedHelpers.findAndHookMethod(
                ClipboardManager::class.java,
                "getPrimaryClip",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val context = appContext ?: return
                        val rule = HookClient.getRuleForPackage(context, packageName)
                        if (rule.action == "direct") return

                        val trigger = CallStackAnalyzer.analyze(rule.smartTrustWindowMs)

                        // If rule is SMART and trigger is user-initiated, allow real clip!
                        if (rule.action == "smart" && trigger.isUserInitiated) {
                            return
                        }

                        // Determine effective action for this event
                        val effectiveAction = if (rule.action == "smart") rule.smartFallback else rule.action

                        when (effectiveAction) {
                            "block" -> {
                                param.result = null
                                if (rule.auditLogEnabled) {
                                    HookClient.reportInterceptAsync(
                                        context = context,
                                        packageName = packageName,
                                        appName = appName,
                                        action = "BLOCKED",
                                        triggerReason = trigger.code,
                                        spoofedPayload = null
                                    )
                                }
                            }
                            "spoof" -> {
                                val decoyClip = DecoyGenerator.generateClipData(rule.decoyStrategy, rule.decoyPool)
                                param.result = decoyClip
                                if (rule.auditLogEnabled) {
                                    val payload = decoyClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                    HookClient.reportInterceptAsync(
                                        context = context,
                                        packageName = packageName,
                                        appName = appName,
                                        action = "SPOOFED",
                                        triggerReason = trigger.code,
                                        spoofedPayload = payload
                                    )
                                }
                            }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[ClipBox] Hook getPrimaryClip failed: ${t.message}")
        }

        // 4. Hook ClipboardManager.hasPrimaryClip()
        try {
            XposedHelpers.findAndHookMethod(
                ClipboardManager::class.java,
                "hasPrimaryClip",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val context = appContext ?: return
                        val rule = HookClient.getRuleForPackage(context, packageName)
                        if (rule.action == "direct") return

                        val trigger = CallStackAnalyzer.analyze(rule.smartTrustWindowMs)
                        if (rule.action == "smart" && trigger.isUserInitiated) return

                        val effectiveAction = if (rule.action == "smart") rule.smartFallback else rule.action
                        if (effectiveAction == "block") {
                            param.result = false
                        } else if (effectiveAction == "spoof") {
                            param.result = true
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[ClipBox] Hook hasPrimaryClip failed: ${t.message}")
        }

        // 5. Hook ClipboardManager.getPrimaryClipDescription()
        try {
            XposedHelpers.findAndHookMethod(
                ClipboardManager::class.java,
                "getPrimaryClipDescription",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val context = appContext ?: return
                        val rule = HookClient.getRuleForPackage(context, packageName)
                        if (rule.action == "direct") return

                        val trigger = CallStackAnalyzer.analyze(rule.smartTrustWindowMs)
                        if (rule.action == "smart" && trigger.isUserInitiated) return

                        val effectiveAction = if (rule.action == "smart") rule.smartFallback else rule.action
                        if (effectiveAction == "block") {
                            param.result = null
                        } else if (effectiveAction == "spoof") {
                            param.result = DecoyGenerator.generateClipDescription()
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            // Ignore
        }
    }
}
