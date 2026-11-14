package com.safeguard.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Floating "Jamming Active" badge drawn over other apps via
 * WindowManager + TYPE_APPLICATION_OVERLAY. Requires SYSTEM_ALERT_WINDOW
 * (the user grants it from Settings; the dashboard deep-links there).
 */
class OverlayBadge(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var view: LinearLayout? = null
    private var label: TextView? = null
    private var pulse = 0f
    private var pulsing = false

    fun canDraw(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else true

    fun show(text: String = "Jamming Active") {
        if (!canDraw() || view != null) return
        val wm = windowManager ?: return
        val density = context.resources.displayMetrics.density

        val dot = TextView(context).apply {
            text = "●"
            setTextColor(Color.parseColor("#FFFF3B5C"))
            textSize = 11f
        }
        label = TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 11f
            letterSpacing = 0.12f
            setPadding((10 * density).toInt(), 0, (4 * density).toInt(), 0)
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (12 * density).toInt(),
                (7 * density).toInt(),
                (14 * density).toInt(),
                (7 * density).toInt()
            )
            background = GradientDrawable().apply {
                cornerRadius = 22 * density
                setColor(Color.parseColor("#E60B1220"))
                setStroke((1 * density).toInt(), Color.parseColor("#6625E5C4"))
            }
            addView(dot)
            addView(label)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (14 * density).toInt()
            y = (64 * density).toInt()
        }

        try {
            wm.addView(root, params)
            view = root
            startPulse(root)
        } catch (t: Throwable) {
            Log.w(TAG, "overlay add failed: ${t.message}")
        }
    }

    fun update(text: String) {
        label?.text = text
    }

    fun hide() {
        pulsing = false
        handler.removeCallbacksAndMessages(null)
        view?.let { runCatching { windowManager?.removeView(it) } }
        view = null
        label = null
    }

    private fun startPulse(root: LinearLayout) {
        pulsing = true
        val tick = object : Runnable {
            override fun run() {
                if (!pulsing) return
                pulse += 0.14f
                val alpha = (150 + 105 * kotlin.math.sin(pulse.toDouble())).toInt()
                root.alpha = alpha / 255f
                handler.postDelayed(this, 60L)
            }
        }
        handler.post(tick)
    }

    private companion object {
        const val TAG = "OverlayBadge"
    }
}
