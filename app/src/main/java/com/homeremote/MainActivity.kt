package com.homeremote

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address

class MainActivity : FragmentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshPermission = object : Runnable {
        override fun run() {
            updateOverlayStatus()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val serviceIntent = Intent(this, RemoteService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val ip = getLocalIp()
        val url = "http://$ip:8080"
        findViewById<TextView>(R.id.tv_url).text = url

        generateQrCode(url, 512)?.let { bmp ->
            findViewById<ImageView>(R.id.iv_qr).setImageBitmap(bmp)
        }

        val deviceName = Settings.Global.getString(contentResolver, "device_name")
            ?.trim()
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]+"), "-")
            ?.trim('-')
        if (!deviceName.isNullOrBlank()) {
            findViewById<TextView>(R.id.tv_local_url).text =
                "or http://$deviceName.local:8080/"
        }

        findViewById<Button>(R.id.btn_overlay_permission).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshPermission)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshPermission)
    }

    private fun updateOverlayStatus() {
        val granted = Settings.canDrawOverlays(this)
        val statusText = findViewById<TextView>(R.id.tv_overlay_status)
        val grantBtn = findViewById<Button>(R.id.btn_overlay_permission)
        if (granted) {
            statusText.text = "✓ Announce overlay: enabled"
            statusText.setTextColor(0xFF22C55E.toInt())
            grantBtn.visibility = View.GONE
        } else {
            statusText.text = "⚠ Announce overlay: tap to enable"
            statusText.setTextColor(0xFFE94560.toInt())
            grantBtn.visibility = View.VISIBLE
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
        val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val rawIp = wifiManager.connectionInfo.ipAddress
        @Suppress("DEPRECATION")
        return android.text.format.Formatter.formatIpAddress(rawIp)
    }
}
