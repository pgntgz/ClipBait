package me.pj.jqb.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Immutable
@Entity(
    tableName = "audit_records",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["timestamp"]),
        Index(value = ["action"])
    ]
)
data class AuditRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String, // BLOCKED, SPOOFED
    val triggerReason: String, // TriggerType.code
    val spoofedPayload: String? = null,
    val callingPid: Int = 0,
    val callingUid: Int = 0,
    val processName: String = "",
    val procState: String = ""
)

/**
 * Compact serializable DTO for offline intercept buffering inside system_server.
 * When the UI app process is killed / swiped from recents, system_server holds
 * these items in memory and on disk until the app reopens and synchronizes them.
 */
@Serializable
data class PendingAuditItem(
    val recordId: String,
    val packageName: String,
    val appName: String,
    val action: String,
    val triggerReason: String,
    val spoofedPayload: String? = null,
    val callingPid: Int = 0,
    val callingUid: Int = 0,
    val processName: String = "",
    val procState: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
