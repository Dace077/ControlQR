package com.controlqr.acceso.core.qr

import com.controlqr.acceso.core.crypto.Crypto
import java.io.ByteArrayOutputStream

/**
 * Contenido de un QR de acceso.
 *
 * El QR es **autocontenido**: lleva dentro todos los datos que el vigilante necesita ver,
 * más una firma HMAC. Por eso el dispositivo que escanea no necesita internet ni haber
 * visto antes ese pase — verifica la firma con la llave del sitio y ya sabe que es legítimo.
 */
data class PassPayload(
    val version: Int = PassCodec.VERSION,
    val keyId: Int,
    /** Identificador único: byte alto = código del dispositivo emisor, 3 bytes bajos = serie. */
    val tokenId: Int,
    val nonce: Int,
    /** Épocas en minutos (UTC). */
    val issuedAtMin: Int,
    val validFromMin: Int,
    val validUntilMin: Int,
    val flags: Int = 0,
    val fullName: String,
    val carrier: String,
    val plate: String,
    val company: String,
    val notes: String,
    val issuedBy: String
) {
    val deviceCode: Int get() = (tokenId ushr 24) and 0xFF
    val serial: Int get() = tokenId and 0xFFFFFF

    /** Folio legible que se imprime junto al QR, p. ej. "01-004532". */
    val folio: String get() = "%02d-%06d".format(deviceCode, serial)

    val issuedAtMillis: Long get() = issuedAtMin.toLong() * 60_000L
    val validFromMillis: Long get() = validFromMin.toLong() * 60_000L
    val validUntilMillis: Long get() = validUntilMin.toLong() * 60_000L

    companion object {
        const val FLAG_REQUIERE_ESCOLTA = 1 shl 0
        const val FLAG_VEHICULO = 1 shl 1

        fun tokenId(deviceCode: Int, serial: Int): Int =
            ((deviceCode and 0xFF) shl 24) or (serial and 0xFFFFFF)

        fun minutesOf(millis: Long): Int = (millis / 60_000L).toInt()
    }
}

/** Motivos por los que un QR puede ser rechazado al escanear. */
enum class PassError {
    FORMATO_INVALIDO,
    VERSION_NO_SOPORTADA,
    FIRMA_INVALIDA,
    LLAVE_DISTINTA
}

sealed interface PassDecodeResult {
    data class Ok(val payload: PassPayload) : PassDecodeResult
    data class Error(val reason: PassError, val detail: String = "") : PassDecodeResult
}

/**
 * Serialización binaria compacta del pase. Se busca que el QR quede pequeño
 * (~120-180 bytes) para que se lea rápido y desde lejos, incluso impreso en papel.
 *
 * Formato:  `CQR1.<base64url(cuerpo || firma[12])>`
 */
object PassCodec {

    const val VERSION = 1
    const val PREFIX = "CQR1"
    private const val SIGNATURE_LEN = 12
    private const val MAX_FIELD = 200

    fun encode(payload: PassPayload, siteSecret: ByteArray): String {
        val body = writeBody(payload)
        val signature = Crypto.signature(siteSecret, body, SIGNATURE_LEN)
        return "$PREFIX." + Crypto.b64(body + signature)
    }

    fun decode(raw: String, siteSecret: ByteArray): PassDecodeResult {
        val text = raw.trim()
        if (!text.startsWith("$PREFIX.")) {
            return PassDecodeResult.Error(PassError.FORMATO_INVALIDO, "No es un pase de Control QR")
        }

        val blob = runCatching { Crypto.unb64(text.substring(PREFIX.length + 1)) }
            .getOrNull()
            ?: return PassDecodeResult.Error(PassError.FORMATO_INVALIDO, "Contenido ilegible")

        if (blob.size <= SIGNATURE_LEN) {
            return PassDecodeResult.Error(PassError.FORMATO_INVALIDO, "Pase incompleto")
        }

        val body = blob.copyOfRange(0, blob.size - SIGNATURE_LEN)
        val signature = blob.copyOfRange(blob.size - SIGNATURE_LEN, blob.size)

        if (!Crypto.constantTimeEquals(Crypto.signature(siteSecret, body, SIGNATURE_LEN), signature)) {
            return PassDecodeResult.Error(
                PassError.FIRMA_INVALIDA,
                "El pase no fue emitido por este sitio o fue alterado"
            )
        }

        return runCatching { readBody(body) }
            .fold(
                onSuccess = { payload ->
                    if (payload.version != VERSION) {
                        PassDecodeResult.Error(
                            PassError.VERSION_NO_SOPORTADA,
                            "Pase versión ${payload.version}; actualiza la app"
                        )
                    } else {
                        PassDecodeResult.Ok(payload)
                    }
                },
                onFailure = { PassDecodeResult.Error(PassError.FORMATO_INVALIDO, "Estructura inválida") }
            )
    }

    /** Permite mostrar el folio en pantalla aunque la firma no valide (para diagnóstico). */
    fun peekFolio(raw: String): String? = runCatching {
        val blob = Crypto.unb64(raw.trim().substring(PREFIX.length + 1))
        val tokenId = ((blob[2].toInt() and 0xFF) shl 24) or
            ((blob[3].toInt() and 0xFF) shl 16) or
            ((blob[4].toInt() and 0xFF) shl 8) or
            (blob[5].toInt() and 0xFF)
        "%02d-%06d".format((tokenId ushr 24) and 0xFF, tokenId and 0xFFFFFF)
    }.getOrNull()

    // ------------------------------------------------------------------ cuerpo

    private fun writeBody(p: PassPayload): ByteArray {
        val out = ByteArrayOutputStream(192)
        out.write(p.version and 0xFF)
        out.write(p.keyId and 0xFF)
        writeInt(out, p.tokenId)
        writeInt(out, p.nonce)
        writeInt(out, p.issuedAtMin)
        writeInt(out, p.validFromMin)
        writeInt(out, p.validUntilMin)
        out.write(p.flags and 0xFF)
        writeString(out, p.fullName)
        writeString(out, p.carrier)
        writeString(out, p.plate)
        writeString(out, p.company)
        writeString(out, p.notes)
        writeString(out, p.issuedBy)
        return out.toByteArray()
    }

    private fun readBody(body: ByteArray): PassPayload {
        val cursor = Cursor(body)
        val version = cursor.readByte()
        val keyId = cursor.readByte()
        val tokenId = cursor.readInt()
        val nonce = cursor.readInt()
        val issuedAt = cursor.readInt()
        val validFrom = cursor.readInt()
        val validUntil = cursor.readInt()
        val flags = cursor.readByte()
        return PassPayload(
            version = version,
            keyId = keyId,
            tokenId = tokenId,
            nonce = nonce,
            issuedAtMin = issuedAt,
            validFromMin = validFrom,
            validUntilMin = validUntil,
            flags = flags,
            fullName = cursor.readString(),
            carrier = cursor.readString(),
            plate = cursor.readString(),
            company = cursor.readString(),
            notes = cursor.readString(),
            issuedBy = cursor.readString()
        )
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeString(out: ByteArrayOutputStream, value: String) {
        val bytes = value.trim().take(MAX_FIELD).toByteArray(Charsets.UTF_8)
        val safe = if (bytes.size > 255) bytes.copyOf(255) else bytes
        out.write(safe.size)
        out.write(safe, 0, safe.size)
    }

    private class Cursor(private val data: ByteArray) {
        private var index = 0

        fun readByte(): Int {
            require(index < data.size)
            return data[index++].toInt() and 0xFF
        }

        fun readInt(): Int {
            var value = 0
            repeat(4) { value = (value shl 8) or readByte() }
            return value
        }

        fun readString(): String {
            val length = readByte()
            require(index + length <= data.size)
            val text = String(data, index, length, Charsets.UTF_8)
            index += length
            return text
        }
    }
}
