package com.feuch.clochette

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
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
    private var sourceView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val lineReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ClochetteRemarkStore.ACTION_LINE_CHANGED) {
                updateLine(intent.getStringExtra(ClochetteRemarkStore.EXTRA_LINE) ?: ClochetteRemarkStore.latest(context))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Toast.makeText(this, "Service créé", Toast.LENGTH_SHORT).show()
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
        sourceView = null
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
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 14.dp()
            y = 28.dp()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM or Gravity.END
            setPadding(6.dp(), 6.dp(), 6.dp(), 6.dp())
        }
        val bubbleMaxWidth = (resources.displayMetrics.widthPixels * 0.68f)
            .toInt()
            .coerceIn(220.dp(), 340.dp())

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            background = roundedBackground(Color.rgb(255, 249, 230), Color.rgb(109, 58, 161), 20.dp())
            elevation = 10f
        }

        lineView = TextView(this).apply {
            text = ClochetteRemarkStore.latest(this@ClochetteOverlayService).withVisibleFrenchAccents()
            textSize = 13f
            setTextColor(Color.rgb(44, 24, 63))
            maxWidth = bubbleMaxWidth
            maxLines = 6
            ellipsize = null
            isSingleLine = false
            setPadding(0, 0, 0, 6.dp())
        }

        sourceView = TextView(this).apply {
            text = "source : ${ClochetteRemarkStore.latestSource(this@ClochetteOverlayService).id}"
            textSize = 9f
            setTextColor(Color.rgb(105, 82, 122))
            maxWidth = bubbleMaxWidth
            maxLines = 1
            setPadding(0, 0, 0, 4.dp())
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        buttonRow.addView(actionButton("Parler") { speakNextLine() })
        buttonRow.addView(actionButton("Répondre") { openVoiceReply() })
        buttonRow.addView(actionButton("Réglages") { openMainActivity("settings") })

        bubble.addView(lineView)
        bubble.addView(sourceView)
        bubble.addView(buttonRow)

        val sprite = ImageView(this).apply {
            setImageResource(R.drawable.clochette_overlay_model)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(0, 0, 0, 0)
            elevation = 14f
            setOnClickListener { speakNextLine() }
            setOnLongClickListener {
                pauseOverlay()
                true
            }
        }

        val bubbleParams = LinearLayout.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM
            rightMargin = 6.dp()
            bottomMargin = 20.dp()
        }
        val spriteParams = LinearLayout.LayoutParams(78.dp(), 140.dp()).apply {
            gravity = Gravity.BOTTOM
        }

        root.addView(bubble, bubbleParams)
        root.addView(sprite, spriteParams)

        installDragBehavior(root, params)
        Toast.makeText(this, "Ajout de la vue", Toast.LENGTH_SHORT).show()
        windowManager.addView(root, params)
        Toast.makeText(this, "Overlay affiché", Toast.LENGTH_SHORT).show()
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
        setAllCaps(false)
        setPadding(6.dp(), 3.dp(), 6.dp(), 3.dp())
        setOnClickListener { onClick() }
    }

    private fun installDragBehavior(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var downAt = 0L
        var moved = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        view.setOnTouchListener { touched, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    downAt = System.currentTimeMillis()
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        moved = true
                        params.x = (startX - dx).coerceAtLeast(0)
                        params.y = (startY - dy).coerceAtLeast(0)
                        windowManager.updateViewLayout(touched, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val pressDuration = System.currentTimeMillis() - downAt
                    if (!moved && pressDuration >= 2_500L) {
                        pauseOverlay()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    true
                }
                else -> false
            }
        }
    }

    private fun speakNextLine() {
        val voiceConfig = ClochetteVoiceSettings.read(this)
        val line = ClochetteEngine.remark(
            activity = UsageObserver(this).snapshot(),
            sensors = SensorSnapshot(),
            energy = null,
            project = null,
            memory = memory.recent(24),
            phraseLength = voiceConfig.phraseLength,
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
        ClochetteWidget.updateAll(this, line, PhraseSource.CLOCHETTE_ENGINE)
        ClochetteVoice.speak(this, line)
        updateLine(line)
    }

    private fun updateLine(line: String) {
        lineView?.text = line.withVisibleFrenchAccents()
        sourceView?.text = "source : ${ClochetteRemarkStore.latestSource(this).id}"
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

    private fun openVoiceReply() {
        startActivity(
            Intent(this, VoiceReplyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
