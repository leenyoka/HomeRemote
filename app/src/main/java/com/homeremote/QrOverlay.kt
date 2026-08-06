package com.homeremote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView

class QrOverlay(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    fun show(url: String, qr: Bitmap) {
        if (view != null || !Settings.canDrawOverlays(context)) return
        val v = LayoutInflater.from(context).inflate(R.layout.overlay_qr, null)
        v.findViewById<ImageView>(R.id.overlay_iv_qr).setImageBitmap(qr)
        v.findViewById<TextView>(R.id.overlay_tv_url).text = url

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 48
            y = 48
        }

        try { wm.addView(v, params); view = v } catch (_: Exception) {}
    }

    fun hide() {
        try { view?.let { wm.removeView(it) } } catch (_: Exception) {}
        view = null
    }
}
