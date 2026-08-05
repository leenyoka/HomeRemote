package com.homeremote

import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.service.dreams.DreamService
import android.widget.ImageView
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address

class RemoteDreamService : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        setContentView(R.layout.dream_remote)

        val ip = getLocalIp()
        val url = "http://$ip:8080"

        findViewById<TextView>(R.id.dream_tv_url).text = url

        generateQrCode(url, 512)?.let { bmp ->
            findViewById<ImageView>(R.id.dream_iv_qr).setImageBitmap(bmp)
        }

        val deviceName = Settings.Global.getString(contentResolver, "device_name")
            ?.trim()
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]+"), "-")
            ?.trim('-')
        if (!deviceName.isNullOrBlank()) {
            findViewById<TextView>(R.id.dream_tv_local_url).text =
                "or http://$deviceName.local:8080/"
        }
    }

    private fun generateQrCode(text: String, size: Int): Bitmap? {
        return try {
            val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
                for (x in 0 until size) for (y in 0 until size) {
                    setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
                }
            }
        } catch (e: Exception) { null }
    }

    private fun getLocalIp(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val lp = cm.getLinkProperties(cm.activeNetwork)
            lp?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address }
                ?.let { return it.address.hostAddress ?: "?" }
        }
        @Suppress("DEPRECATION")
        val wm = getSystemService(WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return android.text.format.Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
    }
}
