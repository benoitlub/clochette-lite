package com.feuch.clochette

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlin.math.abs

class ClochetteOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var memory: ClochetteMemory
    private var overlay: View? = null
    private var lineView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    private val lineReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ClochetteRemarkStore.ACTION_LINE_CHANGED) {
                updateLine(intent.getStringExtra(ClochetteRemarkStore.EXTRA_LINE) ?: ClochetteRemarkStore.latest(context))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        memory = ClochetteMemory(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ContextCompat.registerReceiver(
            this,
            lineReceiver,
            IntentFilter(ClochetteRemarkStore.ACTION_LINE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (Settings.canDrawOverlays(this)) showOverlay() else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> stopSelf()
            ACTION_SHOW -> {
                if (Settings.canDrawOverlays(this) && overlay == null) showOverlay()
                updateLine(intent.getStringExtra(ClochetteRemarkStore.EXTRA_LINE) ?: ClochetteRemarkStore.latest(this))
            }
            ACTION_NEXT_LINE -> speakNextLine()
            else -> updateLine(ClochetteRemarkStore.latest(this))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(lineReceiver) }
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        lineView = null
        layoutParams = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 14.dp()
            y = 28.dp()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
        }

        lineView = TextView(this).apply {
            text = ClochetteRemarkStore.latest(this@ClochetteOverlayService)
            textSize = 14f
            setTextColor(Color.rgb(44, 24, 63))
            maxWidth = 292.dp()
            maxLines = 4
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            background = roundedBackground(Color.rgb(255, 249, 230), Color.rgb(109, 58, 161), 18.dp())
            elevation = 10f
        }

        val sprite = TextView(this).apply {
            text = "C"
            textSize = 42f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(44, 24, 63))
            background = roundedBackground(Color.rgb(233, 213, 255), Color.rgb(109, 58, 161), 52.dp())
            elevation = 12f
            setOnClickListener { speakNextLine() }
            setOnLongClickListener {
                pauseOverlay()
                true
            }
        }

        val spriteParams = LinearLayout.LayoutParams(104.dp(), 104.dp()).apply {
            gravity = Gravity.END
            topMargin = 6.dp()
            bottomMargin = 6.dp()
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        buttonRow.addView(actionButton("Parler") { speakNextLine() })
        buttonRow.addView(actionButton("Repondre") { openMainActivity("response") })
        buttonRow.addView(actionButton("Reglages") { openMainActivity("settings") })

        root.addView(lineView)
        root.addView(sprite, spriteParams)
        root.addView(buttonRow)

        installDragBehavior(root, params)
        windowManager.addView(root, params)
        overlay = root
        layoutParams = params
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 11f
        minHeight = 0
        minWidth = 0
        minimumHeight = 0
        minimumWidth = 0
        setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
        setOnClickListener { onClick() }
    }

    private fun installDragBehavior(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        val longPress = Runnable { pauseOverlay() }

        view.setOnTouchListener { touched, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    handler.postDelayed(longPress, 900)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        moved = true
                        handler.removeCallbacks(longPress)
                        params.x = (startX - dx).coerceAtLeast(0)
                        params.y = (startY - dy).coerceAtLeast(0)
                        windowManager.updateViewLayout(touched, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    true
                }
                else -> false
            }
        }
    }

    private fun speakNextLine() {
        val line = ClochetteEngine.remark(
            activity = UsageObserver(this).snapshot(),
            sensors = SensorSnapshot(),
            energy = null,
            project = null,
            memory = memory.recent(24),
        )
        memory.add(
            ClochetteMemoryEntry(
                context = "overlay",
                observedSignal = "overlay_next_line",
                project = null,
                energy = null,
                clochetteLine = line,
                userReaction = "tap",
                result = "spoken_from_overlay",
            ),
        )
        ClochetteWidget.updateAll(this, line)
        ClochetteVoice.speak(this, line)
        updateLine(line)
    }

    private fun updateLine(line: String) {
        lineView?.text = line
    }

    private fun pauseOverlay() {
        memory.add(
            ClochetteMemoryEntry(
                context = "overlay",
                observedSignal = "overlay_pause",
                project = null,
                energy = null,
                clochetteLine = ClochetteRemarkStore.latest(this),
                userReaction = "pause",
                result = "overlay_closed",
            ),
        )
        Toast.makeText(this, "Clochette se met en pause.", Toast.LENGTH_SHORT).show()
        stopSelf()
    }

    private fun openMainActivity(section: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_START_SECTION, section),
        )
    }

    private fun roundedBackground(fill: Int, stroke: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(fill)
            setStroke(2.dp(), stroke)
        }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_SHOW = "com.feuch.clochette.overlay.SHOW"
        const val ACTION_HIDE = "com.feuch.clochette.overlay.HIDE"
        const val ACTION_NEXT_LINE = "com.feuch.clochette.overlay.NEXT_LINE"
    }
}
