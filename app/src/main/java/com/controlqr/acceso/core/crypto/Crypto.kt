package com.controlqr.acceso.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Primitivas criptográficas de la app. Todo se resuelve en el dispositivo:
 * no hay llamadas de red en ningún punto de este archivo.
 */
object Crypto {

    private const val HMAC_ALG = "HmacSHA256"
    private val random = SecureRandom()

    // ---------------------------------------------------------------- aleatorio

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    /** Entero positivo usado como nonce anti-colisión dentro del pase. */
    fun randomNonce(): Int = random.nextInt() and 0x7FFFFFFF

    /** Genera la llave maestra del sitio (256 bits). Se crea una sola vez, en el master. */
    fun newSiteSecret(): ByteArray = randomBytes(32)

    // ------------------------------------------------------------------- HMAC

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALG)
        mac.init(SecretKeySpec(key, HMAC_ALG))
        return mac.doFinal(data)
    }

    /**
     * Firma truncada que viaja dentro del QR. 12 bytes = 96 bits: suficiente para que
     * falsificar un pase sea inviable, y lo bastante corto para no inflar el código.
     */
    fun signature(key: ByteArray, data: ByteArray, length: Int = 12): ByteArray =
        hmacSha256(key, data).copyOf(length)

    /** Comparación en tiempo constante: no filtra cuántos bytes coincidieron. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    // --------------------------------------------------------------- passwords

    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LEN = 32

    /**
     * PBKDF2-HMAC-SHA256 implementado a mano: el proveedor `PBKDF2WithHmacSHA256`
     * de Android solo existe desde API 26 y la app soporta desde API 24.
     */
    fun pbkdf2(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS,
        keyLength: Int = PBKDF2_KEY_LEN
    ): ByteArray {
        val passwordBytes = String(password).toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance(HMAC_ALG)
        mac.init(SecretKeySpec(passwordBytes, HMAC_ALG))

        val hLen = mac.macLength
        val blocks = (keyLength + hLen - 1) / hLen
        val output = ByteArray(blocks * hLen)

        for (block in 1..blocks) {
            mac.update(salt)
            mac.update(byteArrayOf(
                (block ushr 24).toByte(),
                (block ushr 16).toByte(),
                (block ushr 8).toByte(),
                block.toByte()
            ))
            var u = mac.doFinal()
            val acc = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in acc.indices) acc[i] = (acc[i].toInt() xor u[i].toInt()).toByte()
            }
            System.arraycopy(acc, 0, output, (block - 1) * hLen, hLen)
        }
        return output.copyOf(keyLength)
    }

    /** Devuelve "salt:hash", ambos en Base64, para guardar en la tabla de usuarios. */
    fun hashPassword(plain: String): String {
        val salt = randomBytes(16)
        val hash = pbkdf2(plain.toCharArray(), salt)
        return "${b64(salt)}:${b64(hash)}"
    }

    fun verifyPassword(plain: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        return runCatching {
            val salt = unb64(parts[0])
            val expected = unb64(parts[1])
            constantTimeEquals(pbkdf2(plain.toCharArray(), salt, keyLength = expected.size), expected)
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------------ base64

    /**
     * Base64URL sin relleno, implementado a mano.
     *
     * `java.util.Base64` exige API 26 y `android.util.Base64` no existe fuera del
     * dispositivo, lo que impediría probar el códec del QR con tests normales de JVM.
     */
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun b64(data: ByteArray): String {
        val out = StringBuilder((data.size * 4 + 2) / 3)
        var i = 0
        while (i + 2 < data.size) {
            val chunk = ((data[i].toInt() and 0xFF) shl 16) or
                ((data[i + 1].toInt() and 0xFF) shl 8) or
                (data[i + 2].toInt() and 0xFF)
            out.append(ALPHABET[(chunk ushr 18) and 0x3F])
            out.append(ALPHABET[(chunk ushr 12) and 0x3F])
            out.append(ALPHABET[(chunk ushr 6) and 0x3F])
            out.append(ALPHABET[chunk and 0x3F])
            i += 3
        }
        when (data.size - i) {
            1 -> {
                val chunk = (data[i].toInt() and 0xFF) shl 16
                out.append(ALPHABET[(chunk ushr 18) and 0x3F])
                out.append(ALPHABET[(chunk ushr 12) and 0x3F])
            }

            2 -> {
                val chunk = ((data[i].toInt() and 0xFF) shl 16) or ((data[i + 1].toInt() and 0xFF) shl 8)
                out.append(ALPHABET[(chunk ushr 18) and 0x3F])
                out.append(ALPHABET[(chunk ushr 12) and 0x3F])
                out.append(ALPHABET[(chunk ushr 6) and 0x3F])
            }
        }
        return out.toString()
    }

    fun unb64(text: String): ByteArray {
        val clean = text.trim().trimEnd('=')
        val out = java.io.ByteArrayOutputStream(clean.length * 3 / 4 + 1)
        var buffer = 0
        var bits = 0
        for (character in clean) {
            val value = ALPHABET.indexOf(character)
            require(value >= 0) { "Carácter Base64 inválido: $character" }
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer ushr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    /** Huella corta y legible de la llave del sitio, para confirmar que dos equipos están vinculados. */
    fun keyFingerprint(secret: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(secret)
        return digest.take(4).joinToString("-") { "%02X".format(it) }
    }
}
