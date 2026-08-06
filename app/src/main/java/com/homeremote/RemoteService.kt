package com.homeremote

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address

class RemoteService : Service() {

    private lateinit var server: RemoteServer
    private lateinit var audioManager: AudioManager
    private lateinit var overlay: MessageOverlay
    private lateinit var qrOverlay: QrOverlay
    private var nsdManager: NsdManager? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private var tvUrl = ""
    private var qrBitmap: Bitmap? = null

    private val dreamReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_DREAMING_STARTED -> qrBitmap?.let { qrOverlay.show(tvUrl, it) }
                Intent.ACTION_DREAMING_STOPPED -> qrOverlay.hide()
            }
        }
    }

    private val commandListener: (Command) -> Unit = { cmd ->
        when (cmd.action) {
            "volume" -> handleVolume(cmd.value)
            "launch" -> launchApp(cmd.value)
            "message" -> {
                val parts = cmd.value.split("|", limit = 2)
                val text = parts.getOrElse(0) { "" }
                val durationMs = parts.getOrNull(1)?.toLongOrNull() ?: 10_000L
                overlay.show(text, durationMs)
            }
            "dismiss" -> overlay.dismiss()
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        overlay = MessageOverlay(this)
        qrOverlay = QrOverlay(this)
        server = RemoteServer(this, AppDiscovery(this))

        tvUrl = "http://${getLocalIp()}:8080"
        Thread { qrBitmap = generateQrCode(tvUrl, 256) }.start()

        registerReceiver(dreamReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
        })

        CommandBus.subscribe(commandListener)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        server.start()
        registerNsd()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(dreamReceiver)
        qrOverlay.hide()
        CommandBus.unsubscribe(commandListener)
        overlay.dismiss()
        server.stop()
        try { nsdListener?.let { nsdManager?.unregisterService(it) } } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getLocalIp(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val lp = cm.getLinkProperties(cm.activeNetwork)
            lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }
                ?.let { return it.address.hostAddress ?: "?" }
        }
        @Suppress("DEPRECATION")
        val wm = getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return android.text.format.Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
    }

    private fun generateQrCode(text: String, size: Int): Bitmap? = try {
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            for (x in 0 until size) for (y in 0 until size)
                setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
        }
    } catch (_: Exception) { null }

    private fun registerNsd() {
        try {
            val info = NsdServiceInfo().apply {
                serviceName = "HomeRemote"
                serviceType = "_http._tcp."
                port = 8080
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) { nsdListener = null }
                override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
                override fun onServiceRegistered(i: NsdServiceInfo) {}
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
            }
            nsdListener = listener
            nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
            nsdManager?.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {}
    }

    private fun handleVolume(dir: String) {
        val flag = AudioManager.FLAG_SHOW_UI
        when (dir) {
            "up"   -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, flag)
            "down" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, flag)
            "mute" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, flag)
        }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "HomeRemote Server", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Remote control server is running" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HomeRemote")
            .setContentText("Remote server running on port 8080")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "homeremote_service"
        private const val NOTIFICATION_ID = 1
    }
}
