package com.controlqr.acceso.core.qr

import com.controlqr.acceso.core.crypto.Crypto
import org.json.JSONObject

/**
 * QR de aprovisionamiento: lo genera el master y lo escanea un dispositivo vasallo
 * recién instalado para quedar vinculado al sitio.
 *
 * Lleva la llave del sitio en claro, así que se entrega en persona y de una sola vez.
 * Es el equivalente a entregar una llave física: quien lo fotografíe puede validar
 * pases, por eso la app obliga a cambiar el PIN en el primer ingreso.
 */
data class ProvisioningPayload(
    val siteName: String,
    val keyId: Int,
    val siteSecret: ByteArray,
    val username: String,
    val displayName: String,
    val tempPin: String,
    val deviceCode: Int
) {
    override fun equals(other: Any?): Boolean =
        other is ProvisioningPayload &&
            siteName == other.siteName &&
            keyId == other.keyId &&
            siteSecret.contentEquals(other.siteSecret) &&
            username == other.username &&
            displayName == other.displayName &&
            tempPin == other.tempPin &&
            deviceCode == other.deviceCode

    override fun hashCode(): Int {
        var result = siteName.hashCode()
        result = 31 * result + keyId
        result = 31 * result + siteSecret.contentHashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + tempPin.hashCode()
        result = 31 * result + deviceCode
        return result
    }
}

object ProvisioningCodec {

    const val PREFIX = "CQRPROV1"

    fun encode(payload: ProvisioningPayload): String {
        val json = JSONObject().apply {
            put("s", payload.siteName)
            put("k", payload.keyId)
            put("q", Crypto.b64(payload.siteSecret))
            put("u", payload.username)
            put("n", payload.displayName)
            put("p", payload.tempPin)
            put("d", payload.deviceCode)
        }
        return "$PREFIX." + Crypto.b64(json.toString().toByteArray(Charsets.UTF_8))
    }

    fun decode(raw: String): ProvisioningPayload? {
        val text = raw.trim()
        if (!text.startsWith("$PREFIX.")) return null
        return runCatching {
            val json = JSONObject(String(Crypto.unb64(text.substring(PREFIX.length + 1)), Charsets.UTF_8))
            ProvisioningPayload(
                siteName = json.getString("s"),
                keyId = json.getInt("k"),
                siteSecret = Crypto.unb64(json.getString("q")),
                username = json.getString("u"),
                displayName = json.getString("n"),
                tempPin = json.getString("p"),
                deviceCode = json.getInt("d")
            )
        }.getOrNull()
    }
}
