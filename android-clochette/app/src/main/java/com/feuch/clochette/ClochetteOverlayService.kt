package com.feuch.clochette

import android.app.Service
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var bubbleView: View? = null
    private var spriteView: ImageView? = null
    private var lineView: TextView? = null
    private var sourceView: TextView? = null
    private var replyPanel: LinearLayout? = null
    private var replyStatusView: TextView? = null
    private var replyTranscriptView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private val hideBubbleRunnable = Runnable { collapseOverlayIfIdle() }
    private val stopListeningRunnable = Runnable { stopVoiceReply("Temps écoulé.") }

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
            ACTION_OPEN_MIC -> {
                if (Settings.canDrawOverlays(this) && overlay == null) showOverlay()
                showVoiceReplyOverlay()
            }
            else -> updateLine(ClochetteRemarkStore.latest(this))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(lineReceiver) }
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        bubbleView = null
        spriteView = null
        lineView = null
        sourceView = null
        replyPanel = null
        replyStatusView = null
        replyTranscriptView = null
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
        bubbleView = bubble

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
            text = debugLine()
            textSize = 9f
            setTextColor(Color.rgb(105, 82, 122))
            maxWidth = bubbleMaxWidth
            maxLines = 3
            setPadding(0, 0, 0, 4.dp())
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        buttonRow.addView(actionButton("Parler") { speakNextLine() })
        buttonRow.addView(actionButton("Micro") { showVoiceReplyOverlay() })
        buttonRow.addView(actionButton("Réglages") { openMainActivity("settings") })

        bubble.addView(lineView)
        bubble.addView(sourceView)
        bubble.addView(buttonRow)
        bubble.addView(buildReplyPanel())

        val sprite = ImageView(this).apply {
            setImageResource(R.drawable.clochette_overlay_model)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(0, 0, 0, 0)
            elevation = 14f
            setOnClickListener {
                showBubbleTemporarily()
                speakNextLine()
            }
            setOnLongClickListener {
                pauseOverlay()
                true
            }
        }
        spriteView = sprite

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

        installDragBehavior(root, sprite, params)
        Toast.makeText(this, "Ajout de la vue", Toast.LENGTH_SHORT).show()
        windowManager.addView(root, params)
        Toast.makeText(this, "Overlay affiché", Toast.LENGTH_SHORT).show()
        overlay = root
        layoutParams = params
        expandSprite()
        scheduleBubbleHide()
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

    private fun buildReplyPanel(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 8.dp(), 0, 0)
            replyPanel = this

            replyStatusView = TextView(this@ClochetteOverlayService).apply {
                text = "Micro fermé"
                textSize = 12f
                setTextColor(Color.rgb(44, 24, 63))
            }
            replyTranscriptView = TextView(this@ClochetteOverlayService).apply {
                text = "Appuie sur Micro pour parler 15 secondes."
                textSize = 12f
                setTextColor(Color.rgb(72, 56, 88))
                maxLines = 4
            }
            val replyButtons = LinearLayout(this@ClochetteOverlayService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                addView(actionButton("Parler 15 s") { startVoiceReply() })
                addView(actionButton("Fermer") { closeVoiceReplyPanel() })
            }

            addView(replyStatusView)
            addView(replyTranscriptView)
            addView(replyButtons)
        }

    private fun installDragBehavior(root: View, sprite: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var downAt = 0L
        var moved = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        val listener = View.OnTouchListener { touched, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    showBubbleTemporarily()
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
                        overlay?.let { windowManager.updateViewLayout(it, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val pressDuration = System.currentTimeMillis() - downAt
                    if (!moved && pressDuration >= 2_500L) {
                        pauseOverlay()
                    } else if (!moved && touched == sprite) {
                        showBubbleTemporarily()
                        speakNextLine()
                    } else {
                        scheduleBubbleHide()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    true
                }
                else -> false
            }
        }
        root.setOnTouchListener(listener)
        sprite.setOnTouchListener(listener)
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
        sourceView?.text = debugLine()
        showBubbleTemporarily()
    }

    private fun debugLine(): String {
        val ai = AiGatewaySettings.read(this)
        val runtime = ClochetteRuntimeStatus.read(this)
        return "source : ${ClochetteRemarkStore.latestSource(this).id} · voix : ${runtime.lastVoiceAction} · guardian : ${runtime.lastGuardianDecision} · provider : ${ai.lastProviderUsed ?: "aucun"}"
    }

    private fun showBubbleTemporarily() {
        bubbleView?.visibility = View.VISIBLE
        expandSprite()
        scheduleBubbleHide()
    }

    private fun scheduleBubbleHide() {
        handler.removeCallbacks(hideBubbleRunnable)
        handler.postDelayed(hideBubbleRunnable, bubbleHideDelay())
    }

    private fun collapseOverlayIfIdle() {
        if (listening || replyPanel?.visibility == View.VISIBLE) {
            handler.postDelayed(hideBubbleRunnable, BUBBLE_AUTO_HIDE_MS)
            return
        }
        bubbleView?.visibility = View.GONE
        collapseSprite()
    }

    private fun expandSprite() {
        spriteView?.apply {
            layoutParams = LinearLayout.LayoutParams(EXPANDED_SPRITE_WIDTH_DP.dp(), EXPANDED_SPRITE_HEIGHT_DP.dp()).apply {
                gravity = Gravity.BOTTOM
            }
            background = null
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.FIT_CENTER
            requestLayout()
        }
    }

    private fun collapseSprite() {
        spriteView?.apply {
            layoutParams = LinearLayout.LayoutParams(COLLAPSED_SPRITE_DP.dp(), COLLAPSED_SPRITE_DP.dp()).apply {
                gravity = Gravity.BOTTOM
            }
            background = roundedBackground(Color.rgb(255, 249, 230), Color.rgb(109, 58, 161), COLLAPSED_SPRITE_DP.dp())
            setPadding(5.dp(), 5.dp(), 5.dp(), 5.dp())
            scaleType = ImageView.ScaleType.CENTER_CROP
            requestLayout()
        }
    }

    private fun bubbleHideDelay(): Long {
        val runtime = ClochetteRuntimeStatus.read(this)
        val source = ClochetteRemarkStore.latestSource(this)
        return if (runtime.lastVoiceAction.startsWith("skipped_") || source == PhraseSource.GUARDIAN_FALLBACK) {
            DIAGNOSTIC_BUBBLE_HIDE_MS
        } else {
            BUBBLE_AUTO_HIDE_MS
        }
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
        ClochetteRuntimeStatus.recordAction(this, "overlay fermé")
        stopSelf()
    }

    private fun openMainActivity(section: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_START_SECTION, section),
        )
    }

    private fun showVoiceReplyOverlay() {
        showBubbleTemporarily()
        replyPanel?.visibility = View.VISIBLE
        replyStatusView?.text = "Micro prêt"
        replyTranscriptView?.text = "Le micro s’ouvre ici, sans changer d’écran."
        startVoiceReply()
    }

    private fun startVoiceReply() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            replyPanel?.visibility = View.VISIBLE
            replyStatusView?.text = "Permission micro requise"
            replyTranscriptView?.text = "Android demande l’autorisation depuis l’app. J’ouvre juste l’écran de permission."
            Toast.makeText(this, "Autorise le micro pour répondre à Clochette.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, VoiceReplyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            replyPanel?.visibility = View.VISIBLE
            replyStatusView?.text = "Micro indisponible"
            replyTranscriptView?.text = "Reconnaissance vocale Android indisponible sur ce téléphone."
            return
        }
        listening = true
        replyPanel?.visibility = View.VISIBLE
        replyStatusView?.text = "J’écoute. 15 secondes maximum."
        replyTranscriptView?.text = "..."
        ClochetteRuntimeStatus.recordAction(this, "micro ouvert overlay")
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(replyListener)
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1),
            )
        }
        handler.removeCallbacks(stopListeningRunnable)
        handler.postDelayed(stopListeningRunnable, MAX_LISTEN_MS)
    }

    private fun stopVoiceReply(message: String) {
        if (!listening) return
        listening = false
        replyStatusView?.text = message
        replyTranscriptView?.text = "Pas grave. Je reste là, sans faire de scène."
        ClochetteRuntimeStatus.recordAction(this, "micro fermé overlay")
        recognizer?.stopListening()
        handler.postDelayed({
            replyPanel?.visibility = View.GONE
            scheduleBubbleHide()
        }, REPLY_IDLE_COLLAPSE_MS)
    }

    private fun closeVoiceReplyPanel() {
        stopVoiceReply("Micro fermé")
        replyPanel?.visibility = View.GONE
        scheduleBubbleHide()
    }

    private val replyListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            replyStatusView?.text = "J’écoute."
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            listening = false
            replyStatusView?.text = "Je transforme ça en mots."
        }

        override fun onError(error: Int) {
            listening = false
            handler.removeCallbacks(stopListeningRunnable)
            replyStatusView?.text = "Je n’ai pas bien attrapé la phrase."
            replyTranscriptView?.text = "Réessaie depuis le bouton Micro."
            ClochetteRuntimeStatus.recordAction(this@ClochetteOverlayService, "micro fermé overlay")
        }

        override fun onResults(results: Bundle?) {
            listening = false
            handler.removeCallbacks(stopListeningRunnable)
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            replyTranscriptView?.text = if (text.isBlank()) "Je n’ai rien entendu." else text
            finishOverlayReply(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            replyTranscriptView?.text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun finishOverlayReply(userText: String) {
        ClochetteRuntimeStatus.recordAction(this, "micro fermé overlay")
        val reply = localReplyTo(userText).withVisibleFrenchAccents()
        replyStatusView?.text = "Réponse prête"
        lineView?.text = reply
        ClochetteWidget.updateAll(this, reply, PhraseSource.LOCAL_FALLBACK)
        ClochetteMemory(this).add(
            ClochetteMemoryEntry(
                context = "overlay_voice_reply",
                observedSignal = "voice_reply_overlay",
                project = null,
                energy = null,
                clochetteLine = reply,
                userReaction = userText,
                result = "answered_overlay",
            ),
        )
        ClochetteVoice.speak(this, reply)
        sourceView?.text = debugLine()
    }

    private fun localReplyTo(text: String): String {
        if (text.isBlank()) return "Je peux me tromper, mais le silence avait l’air très occupé."
        val lower = text.lowercase()
        return when {
            "pause" in lower || "fatigu" in lower -> "Je remarque la fatigue. On réduit la voilure, pas la dignité."
            "reprendre" in lower || "continuer" in lower -> "Hypothèse : tu veux reprendre. Très bien. Un petit geste, puis on parade."
            "bloqu" in lower || "bug" in lower -> "Je soupçonne un blocage. On le nomme, puis on lui vole ses chaussures."
            else -> "Je note. Je peux me tromper, mais il y a une piste exploitable là-dedans."
        }
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
        const val ACTION_OPEN_MIC = "com.feuch.clochette.overlay.OPEN_MIC"
        private const val COLLAPSED_SPRITE_DP = 58
        private const val EXPANDED_SPRITE_WIDTH_DP = 78
        private const val EXPANDED_SPRITE_HEIGHT_DP = 140
        private const val BUBBLE_AUTO_HIDE_MS = 25_000L
        private const val DIAGNOSTIC_BUBBLE_HIDE_MS = 60_000L
        private const val MAX_LISTEN_MS = 15_000L
        private const val REPLY_IDLE_COLLAPSE_MS = 5_000L
    }
}
