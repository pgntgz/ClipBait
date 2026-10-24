package me.pj.jqb.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName ?: return

        // 1. In our own app, hook the activation check method to return true
        if (packageName == "io.github.pgntgz.clipbait" || packageName == "me.pj.jqb") {
            try {
                XposedHelpers.findAndHookMethod(
                    "me.pj.jqb.ClipBoxApp",
                    lpparam.classLoader,
                    "isXposedModuleActive",
                    XC_MethodReplacement.returnConstant(true)
                )
            } catch (t: Throwable) {
                // Class or method might not be loaded yet
            }
            return
        }

        // 2. Intercept System Framework (system_server)
        if (packageName == "android") {
            SystemServerHook.init(lpparam)
            return
        }

        if (packageName == "com.android.systemui") {
            return
        }

        // 3. Fallback client-side hook if scoped
        ClipboardHook.init(lpparam)
    }
}
