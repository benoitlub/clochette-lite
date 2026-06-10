package com.feuch.clochette

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class ClochetteOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var memory: ClochetteMemory

    override fun onCreate() {
        super.onCreate()
        memory = ClochetteMemory(this)
        if (Settings.canDrawOverlays(this)) showBubble() else stopSelf()
    }

    override fun onDestroy() {
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 240
        }

        val view = TextView(this).apply {
            text = "C"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xFF1D3329.toInt())
            setBackgroundResource(R.drawable.overlay_bubble)
            elevation = 8f
            minWidth = 64.dp()
            minHeight = 64.dp()
            setPadding(18.dp(), 12.dp(), 18.dp(), 12.dp())
        }
        installTouchBehavior(view, params)
        windowManager.addView(view, params)
        bubble = view
    }

    private fun installTouchBehavior(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        val longPress = Runnable {
            memory.add(
                ClochetteMemoryEntry(
                    context = "overlay",
                    observedSignal = "long_press_close",
                    project = null,
                    energy = null,
                    clochetteLine = null,
                    userReaction = "pause",
                    result = "overlay_closed",
                ),
            )
            Toast.makeText(this, "Clochette se met en pause.", Toast.LENGTH_SHORT).show()
            stopSelf()
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    handler.postDelayed(longPress, 650)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) {
                        moved = true
                        handler.removeCallbacks(longPress)
                        params.x = startX + dx
                        params.y = startY + dy
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (!moved) speak()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    true
                }
                else -> false
            }
        }
    }

    private fun speak() {
        val line = ClochetteEngine.remark(
            activity = UsageObserver(this).snapshot(),
            sensors = SensorSnapshot(),
            energy = null,
            project = null,
            memory = memory.recent(),
        )
        memory.add(
            ClochetteMemoryEntry(
                context = "overlay",
                observedSignal = "tap_bubble",
                project = null,
                energy = null,
                clochetteLine = line,
                userReaction = null,
                result = "shown",
            ),
        )
        Toast.makeText(this, line, Toast.LENGTH_LONG).show()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
