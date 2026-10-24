package me.pj.jqb

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.pj.jqb.data.db.AppDatabase
import me.pj.jqb.data.repo.ConfigRepository

enum class HookState {
    /** Waiting for system_server to answer the handshake. */
    CHECKING,
    /** system_server answered: interception is live. */
    ACTIVE,
    /** Module is enabled for ClipBox itself, but system_server never answered — needs a reboot. */
    NEEDS_REBOOT,
    /** Nothing answered. */
    INACTIVE
}

class ClipBoxApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var configRepository: ConfigRepository
        private set

    private val _hookState = MutableStateFlow(HookState.CHECKING)
    val hookState: StateFlow<HookState> = _hookState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        configRepository = ConfigRepository.getInstance(this)
    }

    /**
     * Replaced with `return true` by XposedEntry when ClipBox itself is in the module scope.
     * Keep this body trivial and the name unchanged.
     */
    fun isXposedModuleActive(): Boolean {
        return false
    }

    /** Called whenever system_server talks to us (ack, log, config request). */
    fun markSystemHookAlive() {
        _hookState.value = HookState.ACTIVE
    }

    /** Called after the handshake timeout if system_server stayed silent. */
    fun resolveHandshakeTimeout() {
        if (_hookState.value == HookState.CHECKING) {
            _hookState.value = if (isXposedModuleActive()) HookState.NEEDS_REBOOT else HookState.INACTIVE
        }
    }

    val prefs by lazy { getSharedPreferences("clipbox_ui", MODE_PRIVATE) }

    companion object {
        lateinit var instance: ClipBoxApp
            private set
    }
}
