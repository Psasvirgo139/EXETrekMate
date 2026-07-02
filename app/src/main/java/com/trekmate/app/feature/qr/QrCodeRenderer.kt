package com.trekmate.app.feature.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import javax.inject.Inject
import javax.inject.Singleton

interface QrCodeRenderer {
    fun render(payload: String, sizePx: Int = 512): Bitmap?
}

@Singleton
class QrCodeRendererImpl @Inject constructor() : QrCodeRenderer {

    override fun render(payload: String, sizePx: Int): Bitmap? = runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    }.getOrNull()
}
