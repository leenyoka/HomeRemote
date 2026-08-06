package com.homeremote

import android.graphics.Bitmap
import android.graphics.Color
import android.service.dreams.DreamService
import android.widget.ImageView
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class RemoteDreamService : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        setContentView(R.layout.dream_remote)

        val url = "http://homeremote:8080"
        findViewById<TextView>(R.id.dream_tv_url).text = url

        generateQrCode(url, 512)?.let { bmp ->
            findViewById<ImageView>(R.id.dream_iv_qr).setImageBitmap(bmp)
        }
    }

    private fun generateQrCode(text: String, size: Int): Bitmap? = try {
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            for (x in 0 until size) for (y in 0 until size)
                setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
        }
    } catch (_: Exception) { null }
}
