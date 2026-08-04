package com.controlqr.acceso

import android.app.Application
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.controlqr.acceso.data.AccessRepository
import com.controlqr.acceso.data.BackupManager
import com.controlqr.acceso.data.UserRepository
import com.controlqr.acceso.data.db.AppDatabase
import com.controlqr.acceso.data.prefs.AppSettings
import com.controlqr.acceso.update.UpdateChecker

/**
 * Contenedor de dependencias. La app es lo bastante pequeña para no necesitar un
 * framework de inyección: un solo objeto construido al arrancar alcanza y evita
 * procesadores de anotaciones extra en la compilación.
 */
class AppContainer(context: Context) {
    val settings = AppSettings(context)
    private val database = AppDatabase.get(context)

    val access = AccessRepository(database.passDao(), database.scanEventDao(), settings)
    val users = UserRepository(database.userDao(), settings)
    val backup = BackupManager(context.applicationContext, access, settings)
    val updates = UpdateChecker()
}

class ControlQrApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer no disponible en este árbol de composición")
}
