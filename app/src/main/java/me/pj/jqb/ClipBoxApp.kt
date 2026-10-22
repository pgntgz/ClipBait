package me.pj.jqb

import android.app.Application
import me.pj.jqb.data.db.AppDatabase
import me.pj.jqb.data.repo.ConfigRepository

class ClipBoxApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var configRepository: ConfigRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        configRepository = ConfigRepository.getInstance(this)
    }

    /**
     * This method is hooked by XposedEntry when the module is enabled in LSPosed / Vector.
     */
    fun isXposedModuleActive(): Boolean {
        return false
    }

    companion object {
        lateinit var instance: ClipBoxApp
            private set
    }
}
