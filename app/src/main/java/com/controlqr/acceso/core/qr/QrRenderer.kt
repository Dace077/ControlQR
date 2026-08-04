package com.controlqr.acceso.core.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Dibuja los códigos QR y la credencial imprimible. Todo local, sin servicios externos. */
object QrRenderer {

    /**
     * Corrección de errores media (~15%): tolera manchas y arrugas del papel sin
     * hacer el código tan denso que las cámaras baratas fallen.
     */
    fun bitmap(content: String, sizePx: Int = 720): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            val offset = y * matrix.width
            for (x in 0 until matrix.width) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        return bitmap
    }

    /**
     * Credencial lista para imprimir o mandar por WhatsApp: el QR más los datos en texto,
     * para que el vigilante pueda cotejar a simple vista lo que la app le muestra.
     */
    fun credential(
        content: String,
        siteName: String,
        folio: String,
        fullName: String,
        carrier: String,
        plate: String,
        validity: String
    ): Bitmap {
        val width = 900
        val height = 1320
        val card = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(card)
        canvas.drawColor(Color.WHITE)

        val brand = Color.parseColor("#0F3D5C")
        val muted = Color.parseColor("#5B6B77")

        val header = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brand }
        canvas.drawRect(0f, 0f, width.toFloat(), 150f, header)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(siteName.take(24), 48f, 78f, titlePaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BBD8E8")
            textSize = 30f
        }
        canvas.drawText("PASE DE ACCESO · FOLIO $folio", 48f, 122f, subtitlePaint)

        val qr = bitmap(content, 640)
        canvas.drawBitmap(qr, ((width - qr.width) / 2).toFloat(), 200f, null)

        var y = 200f + qr.height + 70f
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 26f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#101820")
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        fun row(label: String, value: String) {
            if (value.isBlank()) return
            canvas.drawText(label.uppercase(), 48f, y, labelPaint)
            y += 44f
            canvas.drawText(value.take(30), 48f, y, valuePaint)
            y += 62f
        }

        row("Nombre completo", fullName)
        row("Línea transportista", carrier)
        row("Placas / unidad", plate)
        row("Vigencia", validity)

        val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 24f
        }
        canvas.drawText("Válido para 1 entrada y 1 salida.", 48f, (height - 50).toFloat(), footPaint)

        return card
    }
}
