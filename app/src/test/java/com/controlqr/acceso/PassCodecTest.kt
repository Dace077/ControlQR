package com.controlqr.acceso

import com.controlqr.acceso.core.crypto.Crypto
import com.controlqr.acceso.core.qr.PassCodec
import com.controlqr.acceso.core.qr.PassDecodeResult
import com.controlqr.acceso.core.qr.PassError
import com.controlqr.acceso.core.qr.PassPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassCodecTest {

    private val secret = ByteArray(32) { it.toByte() }

    private fun samplePayload(tokenId: Int = PassPayload.tokenId(1, 42)) = PassPayload(
        keyId = 1,
        tokenId = tokenId,
        nonce = 123456789,
        issuedAtMin = 29_000_000,
        validFromMin = 29_000_010,
        validUntilMin = 29_000_730,
        fullName = "José Ramírez Ñuño",
        carrier = "Transportes del Bajío S.A. de C.V.",
        plate = "ABC-123-XY",
        company = "Planta Norte",
        notes = "Entrega de refacciones",
        issuedBy = "master"
    )

    @Test
    fun `ida y vuelta conserva todos los campos`() {
        val original = samplePayload()
        val encoded = PassCodec.encode(original, secret)

        val decoded = PassCodec.decode(encoded, secret)
        assertTrue(decoded is PassDecodeResult.Ok)

        val result = (decoded as PassDecodeResult.Ok).payload
        assertEquals(original.tokenId, result.tokenId)
        assertEquals(original.nonce, result.nonce)
        assertEquals(original.validFromMin, result.validFromMin)
        assertEquals(original.validUntilMin, result.validUntilMin)
        assertEquals(original.fullName, result.fullName)
        assertEquals(original.carrier, result.carrier)
        assertEquals(original.plate, result.plate)
        assertEquals(original.company, result.company)
        assertEquals(original.notes, result.notes)
        assertEquals(original.issuedBy, result.issuedBy)
    }

    @Test
    fun `una llave distinta invalida la firma`() {
        val encoded = PassCodec.encode(samplePayload(), secret)
        val otraLlave = ByteArray(32) { (it + 7).toByte() }

        val decoded = PassCodec.decode(encoded, otraLlave)
        assertTrue(decoded is PassDecodeResult.Error)
        assertEquals(PassError.FIRMA_INVALIDA, (decoded as PassDecodeResult.Error).reason)
    }

    @Test
    fun `alterar un byte del contenido invalida la firma`() {
        val encoded = PassCodec.encode(samplePayload(), secret)
        // Cambiamos un carácter del cuerpo sin tocar el prefijo.
        val index = encoded.length / 2
        val original = encoded[index]
        val replacement = if (original == 'A') 'B' else 'A'
        val tampered = encoded.replaceRange(index, index + 1, replacement.toString())

        val decoded = PassCodec.decode(tampered, secret)
        assertTrue(decoded is PassDecodeResult.Error)
    }

    @Test
    fun `un texto cualquiera se rechaza por formato`() {
        val decoded = PassCodec.decode("https://ejemplo.com/algo", secret)
        assertTrue(decoded is PassDecodeResult.Error)
        assertEquals(PassError.FORMATO_INVALIDO, (decoded as PassDecodeResult.Error).reason)
    }

    @Test
    fun `el folio separa codigo de equipo y serie`() {
        val payload = samplePayload(PassPayload.tokenId(deviceCode = 7, serial = 1234))
        assertEquals(7, payload.deviceCode)
        assertEquals(1234, payload.serial)
        assertEquals("07-001234", payload.folio)
    }

    @Test
    fun `el folio se puede leer sin verificar la firma`() {
        val payload = samplePayload(PassPayload.tokenId(3, 99))
        val encoded = PassCodec.encode(payload, secret)
        assertEquals(payload.folio, PassCodec.peekFolio(encoded))
    }

    @Test
    fun `dos pases distintos no generan el mismo contenido`() {
        val a = PassCodec.encode(samplePayload(PassPayload.tokenId(1, 1)), secret)
        val b = PassCodec.encode(samplePayload(PassPayload.tokenId(1, 2)), secret)
        assertNotEquals(a, b)
    }

    @Test
    fun `el QR se mantiene compacto`() {
        val encoded = PassCodec.encode(samplePayload(), secret)
        // Por encima de ~300 caracteres el código se vuelve denso y cuesta leerlo impreso.
        assertTrue("Longitud inesperada: ${encoded.length}", encoded.length < 300)
    }
}

class CryptoTest {

    @Test
    fun `base64url va y regresa para cualquier longitud`() {
        for (size in 0..40) {
            val data = ByteArray(size) { (it * 31 - 7).toByte() }
            val encoded = Crypto.b64(data)
            assertTrue("No debe llevar relleno", !encoded.contains('='))
            assertTrue(data.contentEquals(Crypto.unb64(encoded)))
        }
    }

    @Test
    fun `la contrasena correcta se verifica y la incorrecta no`() {
        val stored = Crypto.hashPassword("clave-Segura-2026")
        assertTrue(Crypto.verifyPassword("clave-Segura-2026", stored))
        assertTrue(!Crypto.verifyPassword("clave-segura-2026", stored))
        assertTrue(!Crypto.verifyPassword("", stored))
    }

    @Test
    fun `dos hashes de la misma contrasena son distintos`() {
        val a = Crypto.hashPassword("misma")
        val b = Crypto.hashPassword("misma")
        assertNotEquals(a, b)
        assertTrue(Crypto.verifyPassword("misma", a))
        assertTrue(Crypto.verifyPassword("misma", b))
    }

    @Test
    fun `la huella de la llave es estable`() {
        val secret = ByteArray(32) { it.toByte() }
        assertEquals(Crypto.keyFingerprint(secret), Crypto.keyFingerprint(secret))
    }
}
