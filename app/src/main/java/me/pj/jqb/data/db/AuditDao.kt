package me.pj.jqb.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.pj.jqb.model.AuditRecord

data class AppInterceptStat(
    val packageName: String,
    val appName: String,
    val count: Int
)

@Dao
interface AuditDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AuditRecord): Long

    @Query("SELECT * FROM audit_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 100): Flow<List<AuditRecord>>

    @Query("SELECT * FROM audit_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<AuditRecord>>

    @Query("SELECT COUNT(*) FROM audit_records")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM audit_records WHERE timestamp >= :sinceTimestamp")
    fun getCountSince(sinceTimestamp: Long): Flow<Int>

    @Query("SELECT packageName, appName, COUNT(*) as count FROM audit_records GROUP BY packageName ORDER BY count DESC LIMIT :limit")
    fun getTopOffenders(limit: Int = 5): Flow<List<AppInterceptStat>>

    @Query("DELETE FROM audit_records WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    @Query("DELETE FROM audit_records")
    suspend fun clearAll()
}
