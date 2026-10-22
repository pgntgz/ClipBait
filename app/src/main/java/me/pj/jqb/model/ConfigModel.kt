package me.pj.jqb.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
data class SettingsConfig(
    @SerialName("default_action")
    val defaultAction: String = "direct",
    @SerialName("smart_detect")
    val smartDetect: SmartDetectConfig = SmartDetectConfig(),
    @SerialName("audit_log")
    val auditLog: AuditLogConfig = AuditLogConfig()
)

@Serializable
data class SmartDetectConfig(
    @SerialName("enabled")
    val enabled: Boolean = true,
    @SerialName("trust_window_ms")
    val trustWindowMs: Long = 300L,
    @SerialName("fallback_action")
    val fallbackAction: String = "block"
)

@Serializable
data class AuditLogConfig(
    @SerialName("enabled")
    val enabled: Boolean = true,
    @SerialName("retention_days")
    val retentionDays: Int = 15
)

@Serializable
data class DecoyConfig(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("strategy")
    val strategy: String = "random_natural", // random_natural | fixed | empty
    @SerialName("pool")
    val pool: List<String> = emptyList()
)

@Serializable
data class RuleConfig(
    @SerialName("package_name")
    val packageNames: List<String>,
    @SerialName("action")
    val action: String, // direct | smart | spoof | block
    @SerialName("decoy")
    val decoyId: String? = null,
    @SerialName("smart_fallback")
    val smartFallback: String? = "block",
    @SerialName("description")
    val description: String? = null
)

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
        name = "电商客群污染噪点",
        strategy = "random_natural",
        pool = listOf(
            "今天天气挺凉快，适合出门散步",
            "会议纪要已发送至工作邮箱，请查收",
            "晚饭吃清淡点，稍后发你地址",
            "好的，收到",
            "明天上午十点准时开始",
            "https://zh.wikipedia.org/wiki/Special:Random"
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
        description = "主流电商平台强制噪点伪装，防客群标签画像"
    ),
    RuleConfig(
        packageNames = listOf(
            "com.tencent.mobileqq",
            "com.tencent.mm"
        ),
        action = "smart",
        smartFallback = "block",
        description = "即时通讯应用智能放行，非主动读取全阻断"
    )
)
