package com.controlqr.acceso.data

import com.controlqr.acceso.core.Formats
import com.controlqr.acceso.core.qr.PassCodec
import com.controlqr.acceso.core.qr.PassDecodeResult
import com.controlqr.acceso.core.qr.PassPayload
import com.controlqr.acceso.data.db.PassDao
import com.controlqr.acceso.data.db.PassEntity
import com.controlqr.acceso.data.db.PassOrigin
import com.controlqr.acceso.data.db.PassStatus
import com.controlqr.acceso.data.db.ScanEventDao
import com.controlqr.acceso.data.db.ScanEventEntity
import com.controlqr.acceso.data.db.ScanType
import com.controlqr.acceso.data.prefs.AppSettings
import com.controlqr.acceso.core.crypto.Crypto
import kotlinx.coroutines.flow.Flow

/** Datos que captura el master al emitir un pase. */
data class PassRequest(
    val fullName: String,
    val carrier: String,
    val plate: String,
    val company: String,
    val notes: String,
    val validFrom: Long,
    val validUntil: Long,
    val flags: Int = 0
)

sealed interface IssueResult {
    data class Ok(val pass: PassEntity, val payload: PassPayload) : IssueResult
    data class Error(val message: String) : IssueResult
}

sealed interface ScanOutcome {
    data class Granted(
        val pass: PassEntity,
        val type: ScanType,
        val dwellMinutes: Long?,
        val warning: String? = null
    ) : ScanOutcome

    data class Denied(
        val title: String,
        val detail: String,
        val pass: PassEntity? = null
    ) : ScanOutcome
}

/**
 * Reglas de negocio del acceso. Aquí vive la garantía de que un pase sirve
 * exactamente una vez para entrar y una vez para salir.
 */
class AccessRepository(
    private val passDao: PassDao,
    private val eventDao: ScanEventDao,
    private val settings: AppSettings
) {

    /**
     * Ganchos para replicar cambios hacia otros equipos. Se dejan como callbacks para
     * que esta clase no dependa de Firebase: las reglas de acceso deben poder probarse
     * y funcionar aunque no exista capa de red.
     *
     * Solo se disparan con cambios originados aquí; lo que llega de la nube entra por
     * mergePasses y no rebota.
     */
    var onPassChanged: ((PassEntity) -> Unit)? = null
    var onEventLogged: ((ScanEventEntity) -> Unit)? = null

    // ------------------------------------------------------------- consultas

    fun observeRecent(limit: Int = 300): Flow<List<PassEntity>> = passDao.observeRecent(limit)
    fun observeInside(): Flow<List<PassEntity>> = passDao.observeByStatus(PassStatus.DENTRO)
    fun observeInsideCount(): Flow<Int> = passDao.observeInsideCount()
    fun observeActiveCount(now: Long = System.currentTimeMillis()): Flow<Int> = passDao.observeActiveCount(now)
    fun observeEntries(from: Long, to: Long): Flow<Int> = passDao.observeEntriesBetween(from, to)
    fun observeExits(from: Long, to: Long): Flow<Int> = passDao.observeExitsBetween(from, to)
    fun search(query: String): Flow<List<PassEntity>> = passDao.search(query)
    fun observeEvents(limit: Int = 200): Flow<List<ScanEventEntity>> = eventDao.observeRecent(limit)
    fun observeDeniedSince(since: Long): Flow<Int> = eventDao.observeDeniedSince(since)

    suspend fun byToken(tokenId: Int): PassEntity? = passDao.byToken(tokenId)
    suspend fun totalPasses(): Int = passDao.total()

    // -------------------------------------------------------------- emisión

    suspend fun issue(request: PassRequest, issuedByUsername: String): IssueResult {
        val secret = settings.siteSecret
            ?: return IssueResult.Error("Este equipo no tiene llave de sitio configurada.")

        if (request.fullName.isBlank()) return IssueResult.Error("El nombre completo es obligatorio.")
        if (request.carrier.isBlank()) return IssueResult.Error("La línea transportista es obligatoria.")
        if (request.validUntil <= request.validFrom) {
            return IssueResult.Error("La vigencia debe terminar después de la hora de ingreso.")
        }

        val serial = settings.consumeToken()
            ?: return IssueResult.Error(
                "Se agotaron los ${settings.poolSize} QR de este equipo. Amplía la bolsa en Ajustes."
            )

        val tokenId = PassPayload.tokenId(settings.deviceCode, serial)
        val now = System.currentTimeMillis()

        val payload = PassPayload(
            keyId = settings.keyId,
            tokenId = tokenId,
            nonce = Crypto.randomNonce(),
            issuedAtMin = PassPayload.minutesOf(now),
            validFromMin = PassPayload.minutesOf(request.validFrom),
            validUntilMin = PassPayload.minutesOf(request.validUntil),
            flags = request.flags,
            fullName = request.fullName.trim(),
            carrier = request.carrier.trim(),
            plate = request.plate.trim(),
            company = request.company.trim(),
            notes = request.notes.trim(),
            issuedBy = issuedByUsername
        )

        return runCatching {
            val encoded = PassCodec.encode(payload, secret)
            val entity = PassEntity(
                tokenId = tokenId,
                folio = payload.folio,
                keyId = payload.keyId,
                nonce = payload.nonce,
                fullName = payload.fullName,
                carrier = payload.carrier,
                plate = payload.plate,
                company = payload.company,
                notes = payload.notes,
                issuedBy = issuedByUsername,
                issuedAt = now,
                validFrom = request.validFrom,
                validUntil = request.validUntil,
                status = PassStatus.EMITIDO,
                origin = PassOrigin.EMITIDO_AQUI,
                payload = encoded
            )
            passDao.upsert(entity)
            onPassChanged?.invoke(entity)
            IssueResult.Ok(entity, payload)
        }.getOrElse { error ->
            // No cobramos el token si la emisión no llegó a guardarse.
            settings.releaseToken(serial)
            IssueResult.Error(error.message ?: "No se pudo generar el pase.")
        }
    }

    suspend fun revoke(tokenId: Int, reason: String) {
        passDao.revoke(tokenId, System.currentTimeMillis(), reason)
        passDao.byToken(tokenId)?.let { onPassChanged?.invoke(it) }
    }

    // -------------------------------------------------------------- escaneo

    suspend fun registerEntry(rawQr: String, byUser: String): ScanOutcome =
        processScan(rawQr, byUser, ScanType.ENTRADA)

    suspend fun registerExit(rawQr: String, byUser: String): ScanOutcome =
        processScan(rawQr, byUser, ScanType.SALIDA)

    private suspend fun processScan(rawQr: String, byUser: String, type: ScanType): ScanOutcome {
        val secret = settings.siteSecret
            ?: return deny(null, null, byUser, type, "Sin configurar", "Este equipo no está vinculado a un sitio.")

        val decoded = PassCodec.decode(rawQr, secret)
        if (decoded is PassDecodeResult.Error) {
            return deny(
                tokenId = null,
                folio = PassCodec.peekFolio(rawQr),
                byUser = byUser,
                type = type,
                title = "QR no válido",
                detail = decoded.detail
            )
        }

        val payload = (decoded as PassDecodeResult.Ok).payload
        val existing = passDao.byToken(payload.tokenId)
        val now = System.currentTimeMillis()

        return if (type == ScanType.ENTRADA) {
            handleEntry(payload, existing, byUser, now, rawQr)
        } else {
            handleExit(payload, existing, byUser, now, rawQr)
        }
    }

    private suspend fun handleEntry(
        payload: PassPayload,
        existing: PassEntity?,
        byUser: String,
        now: Long,
        rawQr: String
    ): ScanOutcome {
        if (existing != null) {
            when (existing.status) {
                PassStatus.DENTRO -> return deny(
                    payload.tokenId, payload.folio, byUser, ScanType.ENTRADA,
                    "Ya está adentro",
                    "Este pase registró entrada el ${Formats.dateTime(existing.entryAt)}. Debe registrar salida primero.",
                    existing, payload.fullName
                )

                PassStatus.COMPLETADO -> return deny(
                    payload.tokenId, payload.folio, byUser, ScanType.ENTRADA,
                    "Pase agotado",
                    "Ya se usó para entrar y salir. Solicita un pase nuevo al administrador.",
                    existing, payload.fullName
                )

                PassStatus.REVOCADO -> return deny(
                    payload.tokenId, payload.folio, byUser, ScanType.ENTRADA,
                    "Pase cancelado",
                    existing.revokedReason ?: "El administrador canceló este pase.",
                    existing, payload.fullName
                )

                PassStatus.EMITIDO -> Unit
            }
        }

        if (now > payload.validUntilMillis) {
            return deny(
                payload.tokenId, payload.folio, byUser, ScanType.ENTRADA,
                "Pase vencido",
                "Venció el ${Formats.dateTime(payload.validUntilMillis)}.",
                existing, payload.fullName
            )
        }

        val graceMillis = settings.earlyEntryGraceMinutes * 60_000L
        if (now < payload.validFromMillis - graceMillis) {
            return deny(
                payload.tokenId, payload.folio, byUser, ScanType.ENTRADA,
                "Todavía no es válido",
                "Su ingreso está programado para el ${Formats.dateTime(payload.validFromMillis)}.",
                existing, payload.fullName
            )
        }

        val entity = (existing ?: payload.toEntity(PassOrigin.ESCANEADO, rawQr)).copy(
            status = PassStatus.DENTRO,
            entryAt = now,
            entryBy = byUser,
            entryDevice = settings.deviceCode,
            updatedAt = now
        )
        passDao.upsert(entity)
        onPassChanged?.invoke(entity)

        val event = ScanEventEntity(
            tokenId = entity.tokenId,
            folio = entity.folio,
            fullName = entity.fullName,
            type = ScanType.ENTRADA,
            at = now,
            byUser = byUser,
            deviceCode = settings.deviceCode,
            accepted = true,
            message = "Entrada registrada"
        )
        eventDao.insert(event)
        onEventLogged?.invoke(event)

        val warning = if (now > payload.validFromMillis + 24 * 60 * 60_000L) {
            "Ingreso muy posterior a la hora programada."
        } else null

        return ScanOutcome.Granted(entity, ScanType.ENTRADA, null, warning)
    }

    private suspend fun handleExit(
        payload: PassPayload,
        existing: PassEntity?,
        byUser: String,
        now: Long,
        rawQr: String
    ): ScanOutcome {
        if (existing != null && existing.status == PassStatus.COMPLETADO) {
            return deny(
                payload.tokenId, payload.folio, byUser, ScanType.SALIDA,
                "Salida ya registrada",
                "Salió el ${Formats.dateTime(existing.exitAt)}. Este pase ya no sirve.",
                existing, payload.fullName
            )
        }

        val sinEntradaLocal = existing == null || existing.status == PassStatus.EMITIDO
        if (sinEntradaLocal && !settings.allowExitWithoutEntry) {
            return deny(
                payload.tokenId, payload.folio, byUser, ScanType.SALIDA,
                "Sin entrada registrada",
                "Este equipo no tiene la entrada de este pase. Regístrala en el equipo de la caseta de entrada.",
                existing, payload.fullName
            )
        }

        val base = existing ?: payload.toEntity(PassOrigin.ESCANEADO, rawQr)
        val entity = base.copy(
            status = PassStatus.COMPLETADO,
            exitAt = now,
            exitBy = byUser,
            exitDevice = settings.deviceCode,
            updatedAt = now
        )
        passDao.upsert(entity)
        onPassChanged?.invoke(entity)

        val warning = when {
            existing?.status == PassStatus.REVOCADO ->
                "El pase estaba cancelado. Se permitió la salida y quedó registrado."
            sinEntradaLocal ->
                "No hay entrada registrada en este equipo. Verifica con la caseta de entrada."
            else -> null
        }

        val event = ScanEventEntity(
            tokenId = entity.tokenId,
            folio = entity.folio,
            fullName = entity.fullName,
            type = ScanType.SALIDA,
            at = now,
            byUser = byUser,
            deviceCode = settings.deviceCode,
            accepted = true,
            message = warning ?: "Salida registrada"
        )
        eventDao.insert(event)
        onEventLogged?.invoke(event)

        return ScanOutcome.Granted(entity, ScanType.SALIDA, entity.dwellMinutes, warning)
    }

    private suspend fun deny(
        tokenId: Int?,
        folio: String?,
        byUser: String,
        type: ScanType,
        title: String,
        detail: String,
        pass: PassEntity? = null,
        fullName: String? = null
    ): ScanOutcome.Denied {
        val event = ScanEventEntity(
            tokenId = tokenId,
            folio = folio,
            fullName = fullName ?: pass?.fullName,
            type = ScanType.DENEGADO,
            at = System.currentTimeMillis(),
            byUser = byUser,
            deviceCode = settings.deviceCode,
            accepted = false,
            message = "$title · $detail (intento de ${type.name.lowercase()})"
        )
        eventDao.insert(event)
        onEventLogged?.invoke(event)
        return ScanOutcome.Denied(title, detail, pass)
    }

    // -------------------------------------------------- respaldo / conciliación

    suspend fun allPasses(): List<PassEntity> = passDao.all()
    suspend fun allEvents(): List<ScanEventEntity> = eventDao.all()

    /**
     * Funde registros que vienen de otro equipo. Ante conflicto gana el que tenga
     * más información: una entrada o salida registrada nunca se pierde.
     */
    suspend fun mergePasses(incoming: List<PassEntity>): Int {
        var merged = 0
        for (remote in incoming) {
            val local = passDao.byToken(remote.tokenId)
            val result = if (local == null) {
                remote.copy(origin = PassOrigin.IMPORTADO)
            } else {
                val entryAt = local.entryAt ?: remote.entryAt
                val exitAt = local.exitAt ?: remote.exitAt
                local.copy(
                    entryAt = entryAt,
                    entryBy = local.entryBy ?: remote.entryBy,
                    entryDevice = local.entryDevice ?: remote.entryDevice,
                    exitAt = exitAt,
                    exitBy = local.exitBy ?: remote.exitBy,
                    exitDevice = local.exitDevice ?: remote.exitDevice,
                    revokedAt = local.revokedAt ?: remote.revokedAt,
                    revokedReason = local.revokedReason ?: remote.revokedReason,
                    payload = local.payload.ifBlank { remote.payload },
                    status = resolveStatus(local, remote, entryAt, exitAt),
                    updatedAt = maxOf(local.updatedAt, remote.updatedAt)
                )
            }
            passDao.upsert(result)
            merged++
        }
        return merged
    }

    private fun resolveStatus(
        local: PassEntity,
        remote: PassEntity,
        entryAt: Long?,
        exitAt: Long?
    ): PassStatus = when {
        local.status == PassStatus.REVOCADO || remote.status == PassStatus.REVOCADO -> PassStatus.REVOCADO
        exitAt != null -> PassStatus.COMPLETADO
        entryAt != null -> PassStatus.DENTRO
        else -> PassStatus.EMITIDO
    }

    suspend fun mergeEvents(incoming: List<ScanEventEntity>) {
        // Los eventos son bitácora: se agregan con id nuevo, nunca se sobrescriben.
        eventDao.insertAll(incoming.map { it.copy(id = 0) })
    }
}

private fun PassPayload.toEntity(origin: PassOrigin, rawPayload: String) = PassEntity(
    tokenId = tokenId,
    folio = folio,
    keyId = keyId,
    nonce = nonce,
    fullName = fullName,
    carrier = carrier,
    plate = plate,
    company = company,
    notes = notes,
    issuedBy = issuedBy,
    issuedAt = issuedAtMillis,
    validFrom = validFromMillis,
    validUntil = validUntilMillis,
    status = PassStatus.EMITIDO,
    origin = origin,
    payload = rawPayload
)
