package com.controlqr.acceso.data

import com.controlqr.acceso.core.crypto.Crypto
import com.controlqr.acceso.core.qr.ProvisioningCodec
import com.controlqr.acceso.core.qr.ProvisioningPayload
import com.controlqr.acceso.data.db.UserDao
import com.controlqr.acceso.data.db.UserEntity
import com.controlqr.acceso.data.db.UserRole
import com.controlqr.acceso.data.prefs.AppSettings
import kotlinx.coroutines.flow.Flow

sealed interface AuthResult {
    data class Ok(val user: UserEntity) : AuthResult
    data class Error(val message: String) : AuthResult
}

/** Cuentas locales. No hay servidor: la autenticación ocurre contra la base del propio equipo. */
class UserRepository(
    private val userDao: UserDao,
    private val settings: AppSettings
) {

    fun observeUsers(): Flow<List<UserEntity>> = userDao.observeAll()

    suspend fun currentUser(): UserEntity? {
        val id = settings.currentUserId
        return if (id == 0L) null else userDao.byId(id)
    }

    suspend fun needsSetup(): Boolean = !settings.setupDone || userDao.count() == 0

    // ------------------------------------------------------------ alta inicial

    /** Primer arranque en el equipo del master: crea la llave del sitio y la cuenta dueña. */
    suspend fun setupMaster(
        siteName: String,
        deviceCode: Int,
        poolSize: Int,
        username: String,
        displayName: String,
        password: String
    ): AuthResult {
        val user = username.trim().lowercase()
        if (siteName.isBlank()) return AuthResult.Error("Escribe el nombre del sitio o planta.")
        if (user.length < 3) return AuthResult.Error("El usuario debe tener al menos 3 caracteres.")
        if (password.length < 6) return AuthResult.Error("La contraseña debe tener al menos 6 caracteres.")
        if (userDao.byUsername(user) != null) return AuthResult.Error("Ese usuario ya existe.")

        settings.siteName = siteName.trim()
        settings.deviceCode = deviceCode
        settings.poolSize = poolSize
        settings.keyId = 1
        if (settings.siteSecret == null) settings.siteSecret = Crypto.newSiteSecret()

        val entity = UserEntity(
            username = user,
            displayName = displayName.ifBlank { user },
            role = UserRole.MASTER,
            passwordHash = Crypto.hashPassword(password)
        )
        val id = userDao.insert(entity)
        settings.setupDone = true
        settings.currentUserId = id
        settings.refreshCounters()
        return AuthResult.Ok(entity.copy(id = id))
    }

    // ---------------------------------------------------------------- sesión

    suspend fun login(username: String, password: String): AuthResult {
        val user = userDao.byUsername(username.trim().lowercase())
            ?: return AuthResult.Error("Usuario o contraseña incorrectos.")
        if (!user.active) return AuthResult.Error("Esta cuenta está desactivada.")
        if (!Crypto.verifyPassword(password, user.passwordHash)) {
            return AuthResult.Error("Usuario o contraseña incorrectos.")
        }
        val updated = user.copy(lastLoginAt = System.currentTimeMillis())
        userDao.update(updated)
        settings.currentUserId = user.id
        return AuthResult.Ok(updated)
    }

    fun signOut() = settings.signOut()

    suspend fun changePassword(userId: Long, current: String?, newPassword: String): AuthResult {
        val user = userDao.byId(userId) ?: return AuthResult.Error("Usuario no encontrado.")
        if (current != null && !Crypto.verifyPassword(current, user.passwordHash)) {
            return AuthResult.Error("La contraseña actual no coincide.")
        }
        if (newPassword.length < 6) return AuthResult.Error("La nueva contraseña debe tener al menos 6 caracteres.")
        val updated = user.copy(
            passwordHash = Crypto.hashPassword(newPassword),
            mustChangePassword = false
        )
        userDao.update(updated)
        return AuthResult.Ok(updated)
    }

    // ------------------------------------------------------------- vasallos

    suspend fun createUser(
        username: String,
        displayName: String,
        role: UserRole,
        password: String
    ): AuthResult {
        val user = username.trim().lowercase()
        if (user.length < 3) return AuthResult.Error("El usuario debe tener al menos 3 caracteres.")
        if (password.length < 4) return AuthResult.Error("La clave debe tener al menos 4 caracteres.")
        if (userDao.byUsername(user) != null) return AuthResult.Error("Ese usuario ya existe.")

        val entity = UserEntity(
            username = user,
            displayName = displayName.ifBlank { user },
            role = role,
            passwordHash = Crypto.hashPassword(password),
            mustChangePassword = role == UserRole.VASALLO
        )
        val id = userDao.insert(entity)
        return AuthResult.Ok(entity.copy(id = id))
    }

    suspend fun setActive(userId: Long, active: Boolean): AuthResult {
        val user = userDao.byId(userId) ?: return AuthResult.Error("Usuario no encontrado.")
        if (!active && user.role == UserRole.MASTER && userDao.activeMasterCount() <= 1) {
            return AuthResult.Error("No puedes desactivar al único master; primero crea otro.")
        }
        val updated = user.copy(active = active)
        userDao.update(updated)
        return AuthResult.Ok(updated)
    }

    suspend fun deleteUser(userId: Long): AuthResult {
        val user = userDao.byId(userId) ?: return AuthResult.Error("Usuario no encontrado.")
        if (user.role == UserRole.MASTER && userDao.activeMasterCount() <= 1) {
            return AuthResult.Error("No puedes borrar al único master.")
        }
        if (user.id == settings.currentUserId) return AuthResult.Error("No puedes borrar la cuenta con la que iniciaste sesión.")
        userDao.delete(userId)
        return AuthResult.Ok(user)
    }

    // -------------------------------------------------------- aprovisionamiento

    /**
     * Genera el QR que vincula un teléfono nuevo. Se muestra una vez, en persona:
     * lleva la llave del sitio, así que no debe compartirse por mensajería.
     */
    fun provisioningQr(
        username: String,
        displayName: String,
        tempPin: String,
        deviceCode: Int
    ): String? {
        val secret = settings.siteSecret ?: return null
        return ProvisioningCodec.encode(
            ProvisioningPayload(
                siteName = settings.siteName,
                keyId = settings.keyId,
                siteSecret = secret,
                username = username.trim().lowercase(),
                displayName = displayName,
                tempPin = tempPin,
                deviceCode = deviceCode
            )
        )
    }

    /** Lado del vasallo: aplica el QR recibido y deja el equipo listo para escanear. */
    suspend fun applyProvisioning(raw: String): AuthResult {
        val payload = ProvisioningCodec.decode(raw)
            ?: return AuthResult.Error("Ese QR no es de vinculación de equipo.")

        settings.siteName = payload.siteName
        settings.keyId = payload.keyId
        settings.siteSecret = payload.siteSecret
        settings.deviceCode = payload.deviceCode
        settings.setupDone = true

        val existing = userDao.byUsername(payload.username)
        val entity = if (existing == null) {
            val created = UserEntity(
                username = payload.username,
                displayName = payload.displayName,
                role = UserRole.VASALLO,
                passwordHash = Crypto.hashPassword(payload.tempPin),
                mustChangePassword = true
            )
            created.copy(id = userDao.insert(created))
        } else {
            val updated = existing.copy(
                displayName = payload.displayName,
                passwordHash = Crypto.hashPassword(payload.tempPin),
                mustChangePassword = true,
                active = true
            )
            userDao.update(updated)
            updated
        }
        return AuthResult.Ok(entity)
    }
}
