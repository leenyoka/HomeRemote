package com.homeremote

import android.content.Context
import android.util.Log
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File

class AdbKeyInjector(private val context: Context) {

    private var dadb: Dadb? = null
    private val lock = Any()

    private fun keyFile(): File {
        val f = File(context.cacheDir, "adbkey")
        if (!f.exists()) {
            context.assets.open("adb/adbkey").use { src ->
                f.outputStream().use { dst -> src.copyTo(dst) }
            }
        }
        return f
    }

    private fun connection(): Dadb? {
        synchronized(lock) {
            dadb?.let { return it }
            return try {
                val kp = AdbKeyPair.read(keyFile())
                Dadb.create("127.0.0.1", 5555, kp).also { dadb = it }
            } catch (e: Exception) {
                Log.w("AdbKeyInjector", "connect failed: ${e.message}")
                null
            }
        }
    }

    fun injectKeyEvent(keyCode: Int): Boolean {
        synchronized(lock) {
            return try {
                val d = connection() ?: return false
                d.shell("input keyevent $keyCode")
                true
            } catch (e: Exception) {
                Log.w("AdbKeyInjector", "shell failed: ${e.message}")
                dadb?.close(); dadb = null
                false
            }
        }
    }

    fun close() {
        synchronized(lock) { dadb?.close(); dadb = null }
    }
}
