package com.controlqr.acceso.data

import android.content.Context
import android.net.Uri
import com.controlqr.acceso.core.Formats
import com.controlqr.acceso.data.db.PassEntity
import com.controlqr.acceso.data.db.PassOrigin
import com.controlqr.acceso.data.db.PassStatus
import com.controlqr.acceso.data.db.ScanEventEntity
import com.controlqr.acceso.data.db.ScanType
import com.controlqr.acceso.data.prefs.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ImportSummary(val passes: Int, val events: Int)

/**
 * Respaldo y conciliación entre equipos.
 *
 * Sirve para el caso real de tener la caseta de entrada en un teléfono y la de salida
 * en otro: cada uno exporta su archivo y el master los funde para tener la historia completa.
 */
class BackupManager(
    private val context: Context,
    private val repository: AccessRepository,
    private val settings: AppSettings
) {

    private val shareDir: File
        get() = File(context.cacheDir, "compartidos").apply { mkdirs() }

    // -------------------------------------------------------------- exportar

    suspend fun exportJson(): File = withContext(Dispatchers.IO) {
        val passes = repository.allPasses()
        val events = repository.allEvents()

        val root = JSONObject().apply {
            put("formato", "controlqr-respaldo")
            put("version", 1)
            put("sitio", settings.siteName)
            put("equipo", settings.deviceCode)
            put("generado", System.currentTimeMillis())
            put("pases", JSONArray().apply { passes.forEach { put(it.toJson()) } })
            put("eventos", JSONArray().apply { events.forEach { put(it.toJson()) } })
        }

        File(shareDir, "respaldo-${Formats.fileStamp()}.json").apply {
            writeText(root.toString())
        }
    }

    /** CSV plano para abrirlo en Excel: una fila por pase con entrada, salida y permanencia. */
    suspend fun exportCsv(): File = withContext(Dispatchers.IO) {
        val passes = repository.allPasses().sortedByDescending { it.entryAt ?: it.issuedAt }
        val header = listOf(
            "Folio", "Nombre completo", "Linea transportista", "Placas", "Empresa",
            "Estado", "Emitido por", "Emitido", "Vigente desde", "Vigente hasta",
            "Entrada", "Registro entrada", "Salida", "Registro salida", "Permanencia (min)", "Notas"
        ).joinToString(",")

        val rows = passes.joinToString("\n") { pass ->
            listOf(
                pass.folio,
                pass.fullName,
                pass.carrier,
                pass.plate,
                pass.company,
                pass.status.name,
                pass.issuedBy,
                Formats.dateTime(pass.issuedAt),
                Formats.dateTime(pass.validFrom),
                Formats.dateTime(pass.validUntil),
                Formats.dateTime(pass.entryAt),
                pass.entryBy.orEmpty(),
                Formats.dateTime(pass.exitAt),
                pass.exitBy.orEmpty(),
                pass.dwellMinutes?.toString().orEmpty(),
                pass.notes
            ).joinToString(",") { csv(it) }
        }

        File(shareDir, "accesos-${Formats.fileStamp()}.csv").apply {
            // BOM para que Excel en Windows respete los acentos.
            writeText("﻿$header\n$rows")
        }
    }

    private fun csv(value: String): String =
        "\"" + value.replace("\"", "\"\"").replace("\n", " ") + "\""

    // -------------------------------------------------------------- importar

    suspend fun importJson(uri: Uri): Result<ImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: error("No se pudo leer el archivo.")

            val root = JSONObject(text)
            require(root.optString("formato") == "controlqr-respaldo") {
                "El archivo no es un respaldo de Control QR."
            }

            val passesArray = root.optJSONArray("pases") ?: JSONArray()
            val passes = (0 until passesArray.length()).map { passesArray.getJSONObject(it).toPass() }
            val merged = repository.mergePasses(passes)

            val eventsArray = root.optJSONArray("eventos") ?: JSONArray()
            val events = (0 until eventsArray.length()).map { eventsArray.getJSONObject(it).toEvent() }
            repository.mergeEvents(events)

            ImportSummary(merged, events.size)
        }
    }

    // ------------------------------------------------------------ serialización

    private fun PassEntity.toJson() = JSONObject().apply {
        put("tokenId", tokenId)
        put("folio", folio)
        put("keyId", keyId)
        put("nonce", nonce)
        put("fullName", fullName)
        put("carrier", carrier)
        put("plate", plate)
        put("company", company)
        put("notes", notes)
        put("issuedBy", issuedBy)
        put("issuedAt", issuedAt)
        put("validFrom", validFrom)
        put("validUntil", validUntil)
        put("status", status.name)
        put("origin", origin.name)
        putOpt("entryAt", entryAt)
        putOpt("entryBy", entryBy)
        putOpt("entryDevice", entryDevice)
        putOpt("exitAt", exitAt)
        putOpt("exitBy", exitBy)
        putOpt("exitDevice", exitDevice)
        putOpt("revokedAt", revokedAt)
        putOpt("revokedReason", revokedReason)
        put("payload", payload)
        put("updatedAt", updatedAt)
    }

    private fun JSONObject.toPass() = PassEntity(
        tokenId = getInt("tokenId"),
        folio = optString("folio"),
        keyId = optInt("keyId", 1),
        nonce = optInt("nonce"),
        fullName = optString("fullName"),
        carrier = optString("carrier"),
        plate = optString("plate"),
        company = optString("company"),
        notes = optString("notes"),
        issuedBy = optString("issuedBy"),
        issuedAt = optLong("issuedAt"),
        validFrom = optLong("validFrom"),
        validUntil = optLong("validUntil"),
        status = runCatching { PassStatus.valueOf(optString("status")) }.getOrDefault(PassStatus.EMITIDO),
        origin = runCatching { PassOrigin.valueOf(optString("origin")) }.getOrDefault(PassOrigin.IMPORTADO),
        entryAt = optLongOrNull("entryAt"),
        entryBy = optStringOrNull("entryBy"),
        entryDevice = optIntOrNull("entryDevice"),
        exitAt = optLongOrNull("exitAt"),
        exitBy = optStringOrNull("exitBy"),
        exitDevice = optIntOrNull("exitDevice"),
        revokedAt = optLongOrNull("revokedAt"),
        revokedReason = optStringOrNull("revokedReason"),
        payload = optString("payload"),
        updatedAt = optLong("updatedAt", System.currentTimeMillis())
    )

    private fun ScanEventEntity.toJson() = JSONObject().apply {
        putOpt("tokenId", tokenId)
        putOpt("folio", folio)
        putOpt("fullName", fullName)
        put("type", type.name)
        put("at", at)
        put("byUser", byUser)
        put("deviceCode", deviceCode)
        put("accepted", accepted)
        put("message", message)
    }

    private fun JSONObject.toEvent() = ScanEventEntity(
        tokenId = optIntOrNull("tokenId"),
        folio = optStringOrNull("folio"),
        fullName = optStringOrNull("fullName"),
        type = runCatching { ScanType.valueOf(optString("type")) }.getOrDefault(ScanType.DENEGADO),
        at = optLong("at"),
        byUser = optString("byUser"),
        deviceCode = optInt("deviceCode", 0),
        accepted = optBoolean("accepted", false),
        message = optString("message")
    )

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)
    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)
    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
}
