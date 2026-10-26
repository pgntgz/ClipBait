package me.pj.jqb.data.db

import androidx.compose.runtime.Immutable
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.pj.jqb.model.AuditRecord

@Immutable
data class AppInterceptStat(
    val packageName: String,
    val appName: String,
    val count: Int
)

@Dao
interface AuditDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AuditRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<AuditRecord>)

    @Query("SELECT COUNT(*) FROM audit_records WHERE packageName = :pkg AND timestamp = :ts")
    suspend fun countByPackageAndTimestamp(pkg: String, ts: Long): Int

    @Query("SELECT * FROM audit_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 200): Flow<List<AuditRecord>>

    @Query("SELECT * FROM audit_records WHERE packageName = :pkg ORDER BY timestamp DESC LIMIT :limit")
    fun getRecordsByPackage(pkg: String, limit: Int = 50): Flow<List<AuditRecord>>

    @Query("SELECT * FROM audit_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<AuditRecord>>

    @Query("SELECT COUNT(*) FROM audit_records")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM audit_records WHERE timestamp >= :sinceTimestamp")
    fun getCountSince(sinceTimestamp: Long): Flow<Int>

    @Query("SELECT packageName, MAX(appName) as appName, COUNT(*) as count FROM audit_records GROUP BY packageName ORDER BY count DESC LIMIT :limit")
    fun getTopOffenders(limit: Int = 6): Flow<List<AppInterceptStat>>

    @Query("UPDATE audit_records SET appName = :realAppName WHERE packageName = :pkg AND (appName = :pkg OR appName = '')")
    suspend fun fixLegacyAppName(pkg: String, realAppName: String)

    @Query("DELETE FROM audit_records WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    @Query("DELETE FROM audit_records")
    suspend fun clearAll()
}
