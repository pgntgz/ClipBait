package me.pj.jqb.hook

import android.content.Context
import android.net.Uri
import android.os.Bundle
import java.util.concurrent.Executors

data class HookRule(
    val action: String, // direct | smart | spoof | block
    val smartFallback: String,
    val smartEnabled: Boolean,
    val smartTrustWindowMs: Long,
    val auditLogEnabled: Boolean,
    val decoyStrategy: String?,
    val decoyPool: List<String>?
)

object HookClient {

    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var cachedRule: HookRule? = null

    @Volatile
    private var lastFetchTime = 0L

    private const val CACHE_EXPIRY_MS = 10_000L // 10 seconds cache to avoid IPC churn

    fun getRuleForPackage(context: Context, packageName: String): HookRule {
        val now = System.currentTimeMillis()
        val cached = cachedRule
        if (cached != null && (now - lastFetchTime) < CACHE_EXPIRY_MS) {
            return cached
        }

        return try {
            val uri = Uri.parse("content://me.pj.jqb.provider.config")
            val bundle = context.contentResolver.call(uri, "get_config_for_package", packageName, null)
            if (bundle != null) {
                val rule = HookRule(
                    action = bundle.getString("action") ?: "direct",
                    smartFallback = bundle.getString("smart_fallback") ?: "block",
                    smartEnabled = bundle.getBoolean("smart_enabled", true),
                    smartTrustWindowMs = bundle.getLong("smart_trust_window_ms", 300L),
                    auditLogEnabled = bundle.getBoolean("audit_log_enabled", true),
                    decoyStrategy = bundle.getString("decoy_strategy"),
                    decoyPool = bundle.getStringArrayList("decoy_pool")
                )
                cachedRule = rule
                lastFetchTime = now
                rule
            } else {
                defaultFallbackRule()
            }
        } catch (e: Exception) {
            defaultFallbackRule()
        }
    }

    fun reportInterceptAsync(
        context: Context,
        packageName: String,
        appName: String,
        action: String,
        triggerReason: String,
        spoofedPayload: String?
    ) {
        executor.execute {
            try {
                val uri = Uri.parse("content://me.pj.jqb.provider.audit")
                val extras = Bundle().apply {
                    putString("package_name", packageName)
                    putString("app_name", appName)
                    putString("action", action)
                    putString("trigger_reason", triggerReason)
                    putString("spoofed_payload", spoofedPayload)
                }
                context.contentResolver.call(uri, "log_intercept", null, extras)
            } catch (e: Exception) {
                // Silently swallow in client process
            }
        }
    }

    private fun defaultFallbackRule(): HookRule {
        return HookRule(
            action = "direct",
            smartFallback = "block",
            smartEnabled = true,
            smartTrustWindowMs = 300L,
            auditLogEnabled = true,
            decoyStrategy = null,
            decoyPool = null
        )
    }
}
