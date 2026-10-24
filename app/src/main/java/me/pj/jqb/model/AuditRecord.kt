package me.pj.jqb.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
