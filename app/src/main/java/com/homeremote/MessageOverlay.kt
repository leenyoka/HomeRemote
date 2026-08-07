package com.homeremote

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
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
        playAlertTone()

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

    private fun playAlertTone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        } catch (_: Exception) {}
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

        // Split "SenderName\nMessage body" if sender is present
        val lines = message.split("\n", limit = 2)
        val hasSender = lines.size == 2
        val sender = if (hasSender) lines[0] else null
        val body = if (hasSender) lines[1] else message

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(p, (16 * dp).toInt(), p, (16 * dp).toInt())
            background = GradientDrawable().apply { setColor(0xEE111130.toInt()) }
        }

        val icon = TextView(context).apply {
            text = "📢"
            textSize = 32f
            setPadding(0, 0, (16 * dp).toInt(), 0)
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (sender != null) {
            textCol.addView(TextView(context).apply {
                text = "from $sender"
                textSize = 16f
                setTextColor(0xFFE94560.toInt())
                setPadding(0, 0, 0, (4 * dp).toInt())
            })
        }

        textCol.addView(TextView(context).apply {
            text = body
            textSize = 26f
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        row.addView(icon)
        row.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }
}
