package me.pj.jqb.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class ConfigModel(
    @SerialName("\$schema")
    val schema: String = "https://pj-jqb.local/schema/v1.json",
    @SerialName("version")
    val version: Int = 1,
    @SerialName("settings")
    val settings: SettingsConfig = SettingsConfig(),
    @SerialName("decoys")
    val decoys: List<DecoyConfig> = defaultDecoys(),
    @SerialName("rules")
    val rules: List<RuleConfig> = defaultRules(),
    @SerialName("subscriptions")
    val subscriptions: List<SubscriptionConfig> = emptyList()
)

@Immutable
@Serializable
data class SettingsConfig(
    @SerialName("default_action")
    val defaultAction: String = "direct",
    @SerialName("language")
    val language: String = "system", // system | zh | en
    @SerialName("smart_detect")
    val smartDetect: SmartDetectConfig = SmartDetectConfig(),
    @SerialName("audit_log")
    val auditLog: AuditLogConfig = AuditLogConfig()
)

@Immutable
@Serializable
data class SmartDetectConfig(
    @SerialName("enabled")
    val enabled: Boolean = true,
    @SerialName("trust_window_ms")
    val trustWindowMs: Long = 300L,
    // How long after an app comes to the foreground its reads still count as "sneaky".
    // Read by SystemServerHook in SMART mode.
    @SerialName("resume_window_ms")
    val resumeWindowMs: Long = 2200L,
    @SerialName("fallback_action")
    val fallbackAction: String = "block",
    // How long the same spoofed text is cached per package so burst reads don't expose random spoofing.
    @SerialName("decoy_cache_ms")
    val decoyCacheMs: Long = 1500L
)

@Immutable
@Serializable
data class AuditLogConfig(
    @SerialName("enabled")
    val enabled: Boolean = true,
    @SerialName("retention_days")
    val retentionDays: Int = 15,
    @SerialName("deep_analysis")
    val deepAnalysis: Boolean = true,
    @SerialName("favorite_leaderboard_enabled")
    val favoriteLeaderboardEnabled: Boolean = false,
    @SerialName("show_intercept_toast")
    val showInterceptToast: Boolean = false,
    @SerialName("confetti_enabled")
    val confettiEnabled: Boolean = true,
    @SerialName("haptic_enabled")
    val hapticEnabled: Boolean = true
)

@Immutable
@Serializable
data class DecoyConfig(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("strategy")
    val strategy: String = "wiki_lucky", // wiki_lucky | random_natural | fixed | empty
    @SerialName("pool")
    val pool: List<String> = emptyList()
)

@Immutable
@Serializable
data class RuleConfig(
    @SerialName("package_name")
    val packageNames: List<String>,
    @SerialName("action")
    val action: String, // direct | smart | spoof | block
    @SerialName("decoy")
    val decoyId: String? = null,
    @SerialName("smart_fallback")
    val smartFallback: String? = null,
    @SerialName("description")
    val description: String? = null
)

@Immutable
@Serializable
data class SubscriptionConfig(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("url")
    val url: String,
    @SerialName("auto_update_interval")
    val updateIntervalSeconds: Long = 86400L,
    @SerialName("enabled")
    val enabled: Boolean = true,
    @SerialName("last_updated")
    val lastUpdated: Long = 0L
)

fun defaultDecoys(): List<DecoyConfig> = listOf(
    DecoyConfig(
        id = "e_commerce_noise",
        name = "默认伪装池 · 百科与日常假话",
        strategy = "wiki_lucky",
        pool = listOf(
            "开普勒-452b：位于天鹅座的一颗系外行星，距离地球约1400光年，公转周期约385地球日。",
            "哥特式建筑：12至16世纪流行于欧洲的建筑风格，以尖拱、肋架拱顶与飞扶壁为核心特征。",
            "美拉德反应：还原糖与氨基酸在常温或加热时发生的一系列复杂非酶褐变反应，赋予烘焙食品独特风味。",
            "拉格朗日点：在两大天体引力作用下，小物体能保持相对静止的五个空间平衡点。",
            "巴赫平均律钢琴曲集：涵盖二十四个大小调的前奏曲与赋格，被誉为西方键盘音乐的旧约圣经。",
            "景泰蓝：正名铜胎掐丝珐琅，明代景泰年间工艺达到巅峰，以宝石蓝釉色最为典型。",
            "今天天气挺凉快，适合出门散步",
            "会议纪要已发送至工作邮箱，请查收",
            "晚饭吃清淡点，稍后发你地址",
            "明天上午十点准时开会，别忘带材料"
        )
    ),
    DecoyConfig(
        id = "empty_decoy",
        name = "彻底置空",
        strategy = "empty",
        pool = emptyList()
    )
)

fun defaultRules(): List<RuleConfig> = listOf(
    RuleConfig(
        packageNames = listOf(
            "com.taobao.taobao",
            "com.xunmeng.pinduoduo",
            "com.jingdong.app.mall"
        ),
        action = "spoof",
        decoyId = "e_commerce_noise",
        description = "主流电商默认塞假词"
    ),
    RuleConfig(
        packageNames = listOf(
            "com.tencent.mobileqq",
            "com.tencent.mm"
        ),
        action = "smart",
        smartFallback = null,
        description = "聊天软件默认智能模式"
    )
)
