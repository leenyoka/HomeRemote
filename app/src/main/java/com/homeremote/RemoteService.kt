package com.homeremote

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RemoteService : Service() {

    private lateinit var server: RemoteServer
    private lateinit var audioManager: AudioManager
    private lateinit var overlay: MessageOverlay

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
        server = RemoteServer(this, AppDiscovery(this))

        CommandBus.subscribe(commandListener)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        server.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        CommandBus.unsubscribe(commandListener)
        overlay.dismiss()
        server.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleVolume(dir: String) {
        val flag = AudioManager.FLAG_SHOW_UI
        when (dir) {
            "up" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, flag)
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
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
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
