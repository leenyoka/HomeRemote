package com.homeremote

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class MessageOverlay(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var current: android.view.View? = null
    private val main = Handler(Looper.getMainLooper())
    private val autoDismiss = Runnable { dismiss() }

    fun show(message: String, durationMs: Long = 10_000L) {
        if (!Settings.canDrawOverlays(context)) return
        main.removeCallbacks(autoDismiss)
        dismiss()

        val view = buildView(message)
        val params = overlayParams()

        main.post {
            try {
                wm.addView(view, params)
                current = view
                if (durationMs > 0) main.postDelayed(autoDismiss, durationMs)
            } catch (_: Exception) {}
        }
    }

    fun dismiss() {
        main.removeCallbacks(autoDismiss)
        main.post {
            current?.let {
                try { wm.removeView(it) } catch (_: Exception) {}
                current = null
            }
        }
    }

    private fun overlayParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // Pass-through: key events and touch still reach the playing app
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.FILL_HORIZONTAL
        }
    }

    private fun buildView(message: String): android.view.View {
        val dp = context.resources.displayMetrics.density

        val p = (24 * dp).toInt()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(p, (18 * dp).toInt(), p, (18 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(0xEE111130.toInt())
            }
        }

        val icon = TextView(context).apply {
            text = "📢"
            textSize = 32f
            setPadding(0, 0, (16 * dp).toInt(), 0)
        }

        val body = TextView(context).apply {
            text = message
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        row.addView(icon)
        row.addView(body, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }
}
