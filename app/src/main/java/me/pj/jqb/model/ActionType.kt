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
        summary = "不管",
        summaryEn = "Unrestricted",
        detail = "跟没装 ClipBait 一样。",
        detailEn = "Behaves as if ClipBait isn't installed."
    ),
    SMART(
        key = "smart",
        label = "智能",
        labelEn = "Smart",
        summary = "你粘贴才给",
        summaryEn = "Only when you paste",
        detail = "你自己点粘贴，照常能用。它一打开就读、或者躲在后台读，拿到的是空的。微信、QQ 这种还得正常聊天的，选这个。",
        detailEn = "Manual paste works normally. Reads triggered automatically on app launch or in the background get blocked or spoofed. Ideal for chat apps."
    ),
    SPOOF(
        key = "spoof",
        label = "伪装",
        labelEn = "Spoof",
        summary = "给它假的",
        summaryEn = "Feed decoys",
        detail = "不管什么时候读，都给它一段随机的假文字。它以为读到了，其实什么也没拿到。注意：你自己在里面粘贴，也会变成假的。",
        detailEn = "Every clipboard read receives a random decoy phrase from your pool. Note: manual pasting inside this app will also paste the decoy."
    ),
    BLOCK(
        key = "block",
        label = "阻断",
        labelEn = "Block",
        summary = "什么都不给",
        summaryEn = "Always empty",
        detail = "不管什么时候读，都是空的，也收不到「剪贴板变了」的通知。你自己在里面也粘贴不了。",
        detailEn = "Always returns an empty clipboard and silences clipboard-change listeners. Manual pasting inside this app is also blocked."
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
        "长按菜单粘贴",
        "Context Menu Paste",
        "你长按输入框点了粘贴。",
        "You long-pressed an input field and tapped Paste.",
        true
    ),
    USER_CLICK(
        "user_click",
        "点了粘贴按钮",
        "Paste Button Click",
        "你点了应用里的粘贴按钮。",
        "You tapped an in-app paste button.",
        true
    ),
    TOUCH_WINDOW(
        "touch_window",
        "刚碰过屏幕",
        "Recent Touch",
        "读取发生在你刚刚点过屏幕之后。",
        "Read happened right after a screen touch event.",
        true
    ),
    LIFECYCLE_RESUME(
        "lifecycle_resume",
        "一打开就读",
        "Read on Launch/Resume",
        "应用刚切到前台，你还什么都没点，它就先去读了剪贴板。这是最常见的偷看方式。",
        "The app read your clipboard immediately upon coming to the foreground before you touched anything.",
        false
    ),
    WINDOW_FOCUS(
        "window_focus",
        "切回来就读",
        "Read on Window Focus",
        "窗口一拿到焦点就去读了。",
        "Read triggered the instant the window gained focus.",
        false
    ),
    FOREGROUND_POLL(
        "foreground_poll",
        "在前台读",
        "Foreground Read",
        "应用在前台时读的。也有可能是你自己按了粘贴——在「伪装」和「阻断」模式下，你的粘贴也会被拦。",
        "Read while in the foreground. In Spoof or Block mode, even manual pastes are intercepted.",
        false
    ),
    BACKGROUND_SILENT(
        "background_silent",
        "躲在后台读",
        "Silent Background Read",
        "你根本没在用它，它在后台（或者某个推送进程里）读的。",
        "You weren't even using the app—it read the clipboard from the background or a push subprocess.",
        false
    ),
    ASYNC_POLL(
        "async_poll",
        "连着读了好几次",
        "Rapid Burst Polling",
        "很短时间里反复读，像是在定时检查。",
        "Repeatedly polled the clipboard in a short burst.",
        false
    ),
    UNKNOWN(
        "unknown",
        "读剪贴板",
        "Clipboard Read",
        "读了剪贴板，没法判断是什么场景。",
        "Read the clipboard under an unclassified context.",
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
