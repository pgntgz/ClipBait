package me.pj.jqb.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.pj.jqb.model.AuditRecord

@Database(entities = [AuditRecord::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun auditDao(): AuditDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audit_records ADD COLUMN callingPid INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE audit_records ADD COLUMN callingUid INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE audit_records ADD COLUMN processName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE audit_records ADD COLUMN procState TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_records_packageName ON audit_records(packageName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_records_timestamp ON audit_records(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_records_action ON audit_records(action)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "clipbox.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
