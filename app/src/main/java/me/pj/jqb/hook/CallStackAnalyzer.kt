package me.pj.jqb.hook

import android.os.SystemClock
import me.pj.jqb.model.TriggerType

object CallStackAnalyzer {

    @Volatile
    var lastUserTouchTimestamp: Long = 0L

    fun recordUserTouch() {
        lastUserTouchTimestamp = SystemClock.uptimeMillis()
    }

    /**
     * Determines whether the current clipboard access is a legitimate user-initiated action
     * or a silent / stealth background probe.
     */
    fun analyze(trustWindowMs: Long = 300L): TriggerType {
        val now = SystemClock.uptimeMillis()
        val withinTouchWindow = (now - lastUserTouchTimestamp) in 0..trustWindowMs

        val stackTrace = Thread.currentThread().stackTrace

        var hasContextMenuPaste = false
        var hasUserClick = false
        var hasLifecycleResume = false
        var hasWindowFocus = false
        var hasAsyncPoll = false

        for (element in stackTrace) {
            val className = element.className
            val methodName = element.methodName

            // 1. Check for standard Android Text Context Menu (Long press -> Paste)
            if (methodName == "onTextContextMenuItem" ||
                methodName == "paste" ||
                className.contains("widget.Editor") ||
                className.contains("floatingtoolbar")
            ) {
                hasContextMenuPaste = true
                break
            }

            // 2. Check for View click event dispatch
            if (methodName == "performClick" ||
                methodName == "onClick" ||
                className.contains("OnClickListener")
            ) {
                hasUserClick = true
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
            hasContextMenuPaste -> TriggerType.CONTEXT_MENU_PASTE
            hasUserClick -> TriggerType.USER_CLICK
            withinTouchWindow -> TriggerType.TOUCH_WINDOW
            hasLifecycleResume -> TriggerType.LIFECYCLE_RESUME
            hasWindowFocus -> TriggerType.WINDOW_FOCUS
            hasAsyncPoll -> TriggerType.ASYNC_POLL
            else -> TriggerType.UNKNOWN
        }
    }
}
