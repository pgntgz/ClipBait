package me.pj.jqb.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val action: String, // BLOCKED, SPOOFED, SMART_PASSED
    val triggerReason: String, // CONTEXT_MENU_PASTE, LIFECYCLE_RESUME, WINDOW_FOCUS, ASYNC_POLL, etc.
    val spoofedPayload: String? = null
)
