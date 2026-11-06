package me.pj.jqb.hook

import android.os.SystemClock
import me.pj.jqb.model.TriggerType

object CallStackAnalyzer {

    @Volatile
    var lastLongPressTimestamp: Long = 0L

    @Volatile
    var lastExplicitPasteTimestamp: Long = 0L

    @Volatile
    var lastSetClipTimestamp: Long = 0L

    fun recordLongPress() {
        lastLongPressTimestamp = SystemClock.uptimeMillis()
    }

    fun recordExplicitPasteAction() {
        lastExplicitPasteTimestamp = SystemClock.uptimeMillis()
    }

    fun clearPasteSignalsOnCopy() {
        lastLongPressTimestamp = 0L
        lastExplicitPasteTimestamp = 0L
        lastSetClipTimestamp = SystemClock.uptimeMillis()
    }

    /**
     * Determines whether the current clipboard access is a legitimate user-initiated paste action
     * or a sneaky probe (including probes disguised inside normal input-box taps, Send clicks, or Copy readbacks).
     */
    fun analyze(trustWindowMs: Long = 300L): TriggerType {
        val now = SystemClock.uptimeMillis()
        val withinPostCopyCooldown = lastSetClipTimestamp > 0L && (now - lastSetClipTimestamp) in 0..850L
        val withinExplicitPaste = !withinPostCopyCooldown &&
            lastExplicitPasteTimestamp > 0L && (now - lastExplicitPasteTimestamp) in 0..1500L
        val withinLongPressMenu = !withinPostCopyCooldown &&
            lastLongPressTimestamp > 0L && (now - lastLongPressTimestamp) in 0..4000L

        val stackTrace = Thread.currentThread().stackTrace

        var hasContextMenuPaste = false
        var hasPopupOrMenuClick = false
        var hasClipboardChangeListener = false
        var hasLifecycleResume = false
        var hasWindowFocus = false
        var hasAsyncPoll = false

        for (element in stackTrace) {
            val className = element.className
            val methodName = element.methodName

            if (methodName == "onPrimaryClipChanged" ||
                methodName == "reportPrimaryClipChanged" ||
                className.contains("OnPrimaryClipChangedListener", ignoreCase = true)
            ) {
                hasClipboardChangeListener = true
            }

            // 1. Check for standard Android Text Context Menu (Long press -> Paste)
            if (methodName == "onTextContextMenuItem" ||
                methodName == "paste" ||
                methodName == "canPaste" ||
                methodName == "prepareSpacesAroundPaste" ||
                className.contains("PastePopupWindow", ignoreCase = true) ||
                className.contains("floatingtoolbar", ignoreCase = true)
            ) {
                hasContextMenuPaste = true
                break
            }

            // 2. Check for click inside a PopupWindow / ContextMenu after a long-press
            if (withinLongPressMenu && (
                    methodName == "performClick" ||
                        methodName == "onClick" ||
                        methodName == "onItemClick" ||
                        className.contains("PopupWindow", ignoreCase = true) ||
                        className.contains("MenuPopup", ignoreCase = true)
                    )
            ) {
                hasPopupOrMenuClick = true
            }

            // 3. Check for Activity onResume偷读
            if (methodName == "onResume" ||
                methodName == "performResume" ||
                methodName == "handleResumeActivity"
            ) {
                hasLifecycleResume = true
            }

            // 4. Check for Window Focus偷读
            if (methodName == "onWindowFocusChanged" ||
                methodName == "handleWindowFocusChanged"
            ) {
                hasWindowFocus = true
            }

            // 5. Check for timer / worker polling
            if (className.contains("Handler") && methodName == "dispatchMessage" ||
                className.contains("Runnable") && methodName == "run" ||
                className.contains("ThreadPoolExecutor")
            ) {
                hasAsyncPoll = true
            }
        }

        return when {
            hasClipboardChangeListener || withinPostCopyCooldown -> TriggerType.ASYNC_POLL
            hasLifecycleResume -> TriggerType.LIFECYCLE_RESUME
            hasWindowFocus -> TriggerType.WINDOW_FOCUS
            withinExplicitPaste || hasContextMenuPaste || hasPopupOrMenuClick -> TriggerType.CONTEXT_MENU_PASTE
            withinLongPressMenu && !hasAsyncPoll -> TriggerType.CONTEXT_MENU_PASTE
            hasAsyncPoll -> TriggerType.ASYNC_POLL
            else -> TriggerType.FOREGROUND_POLL
        }
    }
}
