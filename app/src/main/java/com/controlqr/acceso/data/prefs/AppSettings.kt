package com.controlqr.acceso.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.controlqr.acceso.core.crypto.Crypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Configuración del sitio y la llave maestra.
 *
 * Va en EncryptedSharedPreferences porque aquí vive el secreto con el que se firman
 * todos los pases: si alguien lo extrae, puede fabricar QRs válidos.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context)

    private val _tokensUsed = MutableStateFlow(nextSerial - 1)
    val tokensUsed: StateFlow<Int> = _tokensUsed.asStateFlow()

    private val _session = MutableStateFlow(currentUserId)
    val session: StateFlow<Long> = _session.asStateFlow()

    // ------------------------------------------------------------------- sitio

    var siteName: String
        get() = prefs.getString(KEY_SITE_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SITE_NAME, value).apply()

    var keyId: Int
        get() = prefs.getInt(KEY_KEY_ID, 1)
        set(value) = prefs.edit().putInt(KEY_KEY_ID, value).apply()

    /** Llave HMAC del sitio. Idéntica en el master y en todos los vasallos vinculados. */
    var siteSecret: ByteArray?
        get() = prefs.getString(KEY_SITE_SECRET, null)?.let { runCatching { Crypto.unb64(it) }.getOrNull() }
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_SITE_SECRET) else editor.putString(KEY_SITE_SECRET, Crypto.b64(value))
            editor.apply()
        }

    val keyFingerprint: String
        get() = siteSecret?.let { Crypto.keyFingerprint(it) } ?: "—"

    /**
     * Código de este equipo (1-255). Va en el byte alto del token, así que dos masters
     * con códigos distintos nunca emiten folios repetidos.
     */
    var deviceCode: Int
        get() = prefs.getInt(KEY_DEVICE_CODE, 1)
        set(value) = prefs.edit().putInt(KEY_DEVICE_CODE, value.coerceIn(1, 255)).apply()

    var setupDone: Boolean
        get() = prefs.getBoolean(KEY_SETUP_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_DONE, value).apply()

    // ------------------------------------------------------------ bolsa de QRs

    /** Cuántos pases puede emitir este equipo antes de tener que ampliar la bolsa. */
    var poolSize: Int
        get() = prefs.getInt(KEY_POOL_SIZE, DEFAULT_POOL_SIZE)
        set(value) = prefs.edit().putInt(KEY_POOL_SIZE, value.coerceIn(1, 16_777_215)).apply()

    var nextSerial: Int
        get() = prefs.getInt(KEY_NEXT_SERIAL, 1)
        private set(value) = prefs.edit().putInt(KEY_NEXT_SERIAL, value).apply()

    val tokensRemaining: Int get() = (poolSize - (nextSerial - 1)).coerceAtLeast(0)

    /** Toma el siguiente token de la bolsa. Devuelve null si ya se agotó. */
    @Synchronized
    fun consumeToken(): Int? {
        val serial = nextSerial
        if (serial > poolSize) return null
        nextSerial = serial + 1
        _tokensUsed.value = serial
        return serial
    }

    /** Devuelve el token si la emisión falló a medio camino, para no desperdiciarlo. */
    @Synchronized
    fun releaseToken(serial: Int) {
        if (nextSerial == serial + 1) {
            nextSerial = serial
            _tokensUsed.value = serial - 1
        }
    }

    fun refreshCounters() {
        _tokensUsed.value = nextSerial - 1
    }

    // ----------------------------------------------------------------- sesión

    var currentUserId: Long
        get() = prefs.getLong(KEY_CURRENT_USER, 0L)
        set(value) {
            prefs.edit().putLong(KEY_CURRENT_USER, value).apply()
            _session.value = value
        }

    fun signOut() {
        currentUserId = 0L
    }

    // ---------------------------------------------------------------- políticas

    /** Vigencia por omisión de un pase nuevo, en horas. */
    var defaultValidityHours: Int
        get() = prefs.getInt(KEY_VALIDITY_HOURS, 12)
        set(value) = prefs.edit().putInt(KEY_VALIDITY_HOURS, value.coerceIn(1, 720)).apply()

    /**
     * Si la entrada se registró en otro teléfono, este equipo no la tiene en su base.
     * Con esto activado la salida se permite y queda marcada para reconciliar después.
     */
    var allowExitWithoutEntry: Boolean
        get() = prefs.getBoolean(KEY_EXIT_WITHOUT_ENTRY, true)
        set(value) = prefs.edit().putBoolean(KEY_EXIT_WITHOUT_ENTRY, value).apply()

    /** Tolerancia en minutos para escanear un pase antes de su hora de ingreso. */
    var earlyEntryGraceMinutes: Int
        get() = prefs.getInt(KEY_EARLY_GRACE, 60)
        set(value) = prefs.edit().putInt(KEY_EARLY_GRACE, value.coerceIn(0, 1440)).apply()

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    companion object {
        const val DEFAULT_POOL_SIZE = 10_000

        private const val FILE_NAME = "controlqr_secure"
        private const val KEY_SITE_NAME = "site_name"
        private const val KEY_SITE_SECRET = "site_secret"
        private const val KEY_KEY_ID = "key_id"
        private const val KEY_DEVICE_CODE = "device_code"
        private const val KEY_SETUP_DONE = "setup_done"
        private const val KEY_POOL_SIZE = "pool_size"
        private const val KEY_NEXT_SERIAL = "next_serial"
        private const val KEY_CURRENT_USER = "current_user"
        private const val KEY_VALIDITY_HOURS = "validity_hours"
        private const val KEY_EXIT_WITHOUT_ENTRY = "exit_without_entry"
        private const val KEY_EARLY_GRACE = "early_grace"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"

        private fun createPrefs(context: Context): SharedPreferences = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Algunos equipos con keystore dañado no pueden crear la llave maestra.
            // Preferimos operar en almacenamiento privado sin cifrar a dejar la app inservible.
            Log.w("AppSettings", "Keystore no disponible, se usa almacenamiento privado", e)
            context.getSharedPreferences("${FILE_NAME}_plain", Context.MODE_PRIVATE)
        }
    }
}
