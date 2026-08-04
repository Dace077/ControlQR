package com.controlqr.acceso.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class UserRole {
    /** Acceso total: emite pases, ve todo, administra usuarios y reportes. */
    MASTER,

    /** Solo las pantallas de Entrada y Salida. */
    VASALLO
}

/**
 * Ciclo de vida del pase. Es el corazón de la regla "sirve exactamente dos veces":
 * EMITIDO --escaneo entrada--> DENTRO --escaneo salida--> COMPLETADO (y ya no vuelve a servir).
 */
enum class PassStatus {
    EMITIDO,
    DENTRO,
    COMPLETADO,
    REVOCADO
}

enum class ScanType { ENTRADA, SALIDA, DENEGADO }

/** De dónde salió el registro que tiene este dispositivo. */
enum class PassOrigin {
    /** Se emitió en este equipo (es el master). */
    EMITIDO_AQUI,

    /** Apareció al escanearlo en la caseta. */
    ESCANEADO,

    /** Llegó por un archivo de respaldo/reconciliación. */
    IMPORTADO
}

@Entity(tableName = "users", indices = [Index(value = ["username"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val displayName: String,
    val role: UserRole,
    val passwordHash: String,
    val active: Boolean = true,
    val mustChangePassword: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long? = null
)

@Entity(
    tableName = "passes",
    indices = [
        Index(value = ["status"]),
        Index(value = ["entryAt"]),
        Index(value = ["exitAt"]),
        Index(value = ["fullName"])
    ]
)
data class PassEntity(
    @PrimaryKey val tokenId: Int,
    val folio: String,
    val keyId: Int,
    val nonce: Int,

    val fullName: String,
    val carrier: String,
    val plate: String,
    val company: String,
    val notes: String,

    val issuedBy: String,
    val issuedAt: Long,
    val validFrom: Long,
    val validUntil: Long,

    val status: PassStatus,
    val origin: PassOrigin,

    val entryAt: Long? = null,
    val entryBy: String? = null,
    val entryDevice: Int? = null,

    val exitAt: Long? = null,
    val exitBy: String? = null,
    val exitDevice: Int? = null,

    val revokedAt: Long? = null,
    val revokedReason: String? = null,

    /** Contenido exacto del QR: permite reimprimir o reenviar el pase sin volver a firmarlo. */
    val payload: String,
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Minutos que la persona permaneció dentro; null si el ciclo no ha cerrado. */
    val dwellMinutes: Long?
        get() {
            val start = entryAt ?: return null
            val end = exitAt ?: return null
            return ((end - start) / 60_000L).coerceAtLeast(0)
        }
}

/** Bitácora completa: incluye los intentos rechazados, que es donde se ven los problemas. */
@Entity(tableName = "scan_events", indices = [Index(value = ["at"]), Index(value = ["tokenId"])])
data class ScanEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tokenId: Int?,
    val folio: String?,
    val fullName: String?,
    val type: ScanType,
    val at: Long,
    val byUser: String,
    val deviceCode: Int,
    val accepted: Boolean,
    val message: String
)
