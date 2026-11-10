package me.pj.jqb.model

import androidx.compose.runtime.Immutable

/**
 * What ClipBait does when an app reads the clipboard.
 * [key] is persisted in config.json — never rename it.
 */
@Immutable
enum class ActionType(
    val key: String,
    val label: String,
    val labelEn: String,
    val summary: String,
    val summaryEn: String,
    val detail: String,
    val detailEn: String
) {
    DIRECT(
        key = "direct",
        label = "放行",
        labelEn = "Allow",
        summary = "不限制",
        summaryEn = "Unrestricted",
        detail = "不做任何处理。",
        detailEn = "No restrictions."
    ),
    SMART(
        key = "smart",
        label = "智能",
        labelEn = "Smart",
        summary = "仅手动粘贴",
        summaryEn = "Only when you paste",
        detail = "手动粘贴正常可用，其他时候的读取会被拦截。适合聊天类应用。",
        detailEn = "Manual paste works normally; automatic reads on launch or in background are intercepted. Suitable for chat apps."
    ),
    SPOOF(
        key = "spoof",
        label = "伪装",
        labelEn = "Spoof",
        summary = "返回假内容",
        summaryEn = "Return decoys",
        detail = "每次读取都返回词库里的假内容。手动粘贴也会变成假内容。",
        detailEn = "Every read returns a random decoy phrase from your pool. Manual paste also receives the decoy."
    ),
    BLOCK(
        key = "block",
        label = "阻断",
        labelEn = "Block",
        summary = "始终为空",
        summaryEn = "Always empty",
        detail = "每次读取都返回空，也不会收到剪贴板变化通知。手动粘贴不可用。",
        detailEn = "Always returns empty and blocks clipboard change listeners. Manual paste is disabled."
    );

    fun label(isEn: Boolean): String = if (isEn) labelEn else label
    fun summary(isEn: Boolean): String = if (isEn) summaryEn else summary
    fun detail(isEn: Boolean): String = if (isEn) detailEn else detail

    companion object {
        fun fromKey(key: String?): ActionType {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DIRECT
        }
    }
}

/**
 * Why a read was judged the way it was. [code] is persisted in the database — never rename it.
 */
@Immutable
enum class TriggerType(
    val code: String,
    val displayName: String,
    val displayNameEn: String,
    val explain: String,
    val explainEn: String,
    val isUserInitiated: Boolean
) {
    CONTEXT_MENU_PASTE(
        "context_menu",
        "菜单粘贴",
        "Menu paste",
        "通过长按菜单粘贴。",
        "Pasted from the context menu.",
        true
    ),
    USER_CLICK(
        "user_click",
        "按钮粘贴",
        "Button paste",
        "点击了应用内的粘贴按钮。",
        "Tapped an in-app paste button.",
        true
    ),
    TOUCH_WINDOW(
        "touch_window",
        "触摸后读取",
        "Read after touch",
        "读取发生在触摸屏幕后不久。",
        "Read occurred shortly after a touch event.",
        true
    ),
    LIFECYCLE_RESUME(
        "lifecycle_resume",
        "启动时读取",
        "Read on launch",
        "应用切到前台后、未进行任何操作前读取。",
        "Read immediately upon entering foreground before any user interaction.",
        false
    ),
    WINDOW_FOCUS(
        "window_focus",
        "获得焦点时读取",
        "Read on focus",
        "窗口获得焦点时立即读取。",
        "Read as soon as the window gained focus.",
        false
    ),
    FOREGROUND_POLL(
        "foreground_poll",
        "前台主动读取",
        "Foreground read",
        "应用在前台，但没有粘贴操作时读取。",
        "Read while in foreground without any paste action.",
        false
    ),
    BACKGROUND_SILENT(
        "background_silent",
        "后台读取",
        "Background read",
        "应用在后台或子进程中读取。",
        "Read from the background or a subprocess.",
        false
    ),
    ASYNC_POLL(
        "async_poll",
        "连续读取",
        "Burst reads",
        "短时间内多次读取。",
        "Polled the clipboard repeatedly in a short interval.",
        false
    ),
    UNKNOWN(
        "unknown",
        "未知场景",
        "Unknown",
        "无法判断读取场景。",
        "Read under an unclassified context.",
        false
    );

    fun displayName(isEn: Boolean): String = if (isEn) displayNameEn else displayName
    fun explain(isEn: Boolean): String = if (isEn) explainEn else explain

    companion object {
        fun fromCode(raw: String?): TriggerType {
            if (raw.isNullOrBlank()) return UNKNOWN
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) }?.let { return it }
            return when {
                raw.contains("onResume", ignoreCase = true) || raw.contains("切台") -> LIFECYCLE_RESUME
                raw.contains("Focus", ignoreCase = true) || raw.contains("获焦") -> WINDOW_FOCUS
                raw.contains("后台") && raw.contains("轮询") -> FOREGROUND_POLL
                raw.contains("后台") || raw.contains("子进程") -> BACKGROUND_SILENT
                raw.contains("异步") || raw.contains("定时") -> ASYNC_POLL
                else -> UNKNOWN
            }
        }
    }
}
