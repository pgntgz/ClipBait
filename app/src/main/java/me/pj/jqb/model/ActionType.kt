package me.pj.jqb.model

import androidx.annotation.StringRes
import me.pj.jqb.R

enum class ActionType(val key: String, @StringRes val labelRes: Int) {
    DIRECT("direct", R.string.action_direct),
    SMART("smart", R.string.action_smart),
    SPOOF("spoof", R.string.action_spoof),
    BLOCK("block", R.string.action_block);

    companion object {
        fun fromKey(key: String?): ActionType {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DIRECT
        }
    }
}

enum class TriggerType(val code: String, val displayName: String, val isUserInitiated: Boolean) {
    CONTEXT_MENU_PASTE("context_menu", "系统菜单长按粘贴", true),
    USER_CLICK("user_click", "点击界面控件粘贴", true),
    TOUCH_WINDOW("touch_window", "触摸交互窗口期内触发", true),
    LIFECYCLE_RESUME("lifecycle_resume", "应用切台/唤醒偷读 (onResume)", false),
    WINDOW_FOCUS("window_focus", "窗口获焦偷读 (onWindowFocusChanged)", false),
    ASYNC_POLL("async_poll", "后台异步/定时轮询 (Handler/Timer)", false),
    UNKNOWN("unknown", "未知探针读取", false);

    companion object {
        fun fromCode(code: String?): TriggerType {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
