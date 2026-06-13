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
import android.widget.FrameLayout
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
    private var rootLayout: LinearLayout? = null
    private var bubbleView: View? = null
    private var spriteView: ImageView? = null
    private var lineView: TextView? = null
    private var sourceView: TextView? = null
    private var settingsRowView: View? = null
    private var replyPanel: LinearLayout? = null
    private var replyStatusView: TextView? = null
    private var replyTranscriptView: TextView? = null
    private var spriteContainerView: FrameLayout? = null
    private var micBadgeView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var micOnlyMode = false
    private val hideBubbleRunnable = Runnable { collapseOverlayIfIdle() }
    private val stopListeningRunnable = Runnable { stopVoiceReply("Temps écoulé. Micro fermé.") }

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
                showVoiceReplyOverlay(autoStart = true)
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
        rootLayout = null
        bubbleView = null
        spriteView = null
        lineView = null
        sourceView = null
        settingsRowView = null
        replyPanel = null
        replyStatusView = null
        replyTranscriptView = null
        spriteContainerView = null
        micBadgeView = null
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
        rootLayout = root
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

        val settingsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        settingsRow.addView(actionButton("Répondre") { showVoiceReplyOverlay(autoStart = false) })
        settingsRow.addView(actionButton("Réglages") { openMainActivity("settings") })
        settingsRowView = settingsRow

        bubble.addView(lineView)
        bubble.addView(sourceView)
        bubble.addView(buildReplyPanel())
        bubble.addView(settingsRow)

        val sprite = ImageView(this).apply {
            setImageResource(R.drawable.clochette_overlay_model)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(0, 0, 0, 0)
            elevation = 14f
        }
        spriteView = sprite
        val micBadge = TextView(this).apply {
            text = "\uD83C\uDFA4"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(86, 48, 132))
            background = micBadgeBackground(recording = false)
            elevation = 18f
            visibility = View.GONE
        }
        micBadgeView = micBadge
        val spriteContainer = FrameLayout(this).apply {
            addView(
                sprite,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                micBadge,
                FrameLayout.LayoutParams(MIC_BADGE_DP.dp(), MIC_BADGE_DP.dp(), Gravity.BOTTOM or Gravity.END).apply {
                    rightMargin = 2.dp()
                    bottomMargin = 3.dp()
                },
            )
        }
        spriteContainerView = spriteContainer

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
        root.addView(spriteContainer, spriteParams)

        installDragBehavior(root, spriteContainer, params)
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
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            setPadding(0, 0, 0, 0)
            replyPanel = this

            replyStatusView = TextView(this@ClochetteOverlayService).apply {
                text = ""
                textSize = 12f
                setTextColor(Color.rgb(44, 24, 63))
                visibility = View.GONE
            }
            replyTranscriptView = TextView(this@ClochetteOverlayService).apply {
                text = ""
                textSize = 11f
                setTextColor(Color.rgb(72, 56, 88))
                maxWidth = 220.dp()
                maxLines = 2
                isSingleLine = false
                background = roundedBackground(Color.rgb(255, 253, 244), Color.rgb(215, 199, 229), 12.dp())
                setPadding(8.dp(), 5.dp(), 8.dp(), 5.dp())
                visibility = View.GONE
            }

            addView(replyStatusView)
            addView(replyTranscriptView)
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
            if (micOnlyMode && touched == sprite) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        setMicButtonRecording(true)
                        startVoiceReply(
                            status = "J’écoute tant que tu maintiens.",
                            hint = "",
                        )
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        finishManualVoiceReply()
                        true
                    }
                    else -> true
                }
            } else when (event.action) {
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
        val decision = OctopusCore.intervene(
            context = this,
            trigger = OctopusCore.TRIGGER_MANUAL_TAP,
            forceSpeak = true,
        )
        updateLine(decision.finalLine)
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

    private fun enterMicOnlyMode() {
        micOnlyMode = true
        rootLayout?.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.END
        }
        bubbleView?.apply {
            visibility = View.VISIBLE
            background = null
            elevation = 0f
            setPadding(0, 0, 0, 0)
        }
        lineView?.visibility = View.GONE
        sourceView?.visibility = View.GONE
        settingsRowView?.visibility = View.GONE
        replyPanel?.visibility = View.VISIBLE
        replyStatusView?.visibility = View.GONE
        if (replyTranscriptView?.text.isNullOrBlank()) {
            replyTranscriptView?.visibility = View.GONE
        }
        useMicSprite(recording = false)
        handler.removeCallbacks(hideBubbleRunnable)
    }

    private fun exitMicOnlyMode() {
        micOnlyMode = false
        rootLayout?.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM or Gravity.END
        }
        bubbleView?.apply {
            visibility = View.VISIBLE
            background = roundedBackground(Color.rgb(255, 249, 230), Color.rgb(109, 58, 161), 20.dp())
            elevation = 10f
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
        }
        lineView?.visibility = View.VISIBLE
        sourceView?.visibility = View.VISIBLE
        settingsRowView?.visibility = View.VISIBLE
        replyPanel?.visibility = View.GONE
        replyStatusView?.visibility = View.GONE
        replyTranscriptView?.visibility = View.GONE
        setMicButtonRecording(false)
        expandSprite()
        scheduleBubbleHide()
    }

    private fun scheduleBubbleHide() {
        handler.removeCallbacks(hideBubbleRunnable)
        handler.postDelayed(hideBubbleRunnable, bubbleHideDelay())
    }

    private fun collapseOverlayIfIdle() {
        if (listening) {
            handler.postDelayed(hideBubbleRunnable, BUBBLE_AUTO_HIDE_MS)
            return
        }
        bubbleView?.visibility = View.GONE
        collapseSprite()
    }

    private fun expandSprite() {
        spriteContainerView?.apply {
            layoutParams = LinearLayout.LayoutParams(EXPANDED_SPRITE_WIDTH_DP.dp(), EXPANDED_SPRITE_HEIGHT_DP.dp()).apply {
                gravity = Gravity.BOTTOM
            }
            background = null
            setPadding(0, 0, 0, 0)
            requestLayout()
        }
        spriteView?.apply {
            setImageResource(R.drawable.clochette_overlay_model)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            background = null
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.FIT_CENTER
            requestLayout()
        }
        micBadgeView?.visibility = View.GONE
    }

    private fun collapseSprite() {
        spriteContainerView?.apply {
            layoutParams = LinearLayout.LayoutParams(COLLAPSED_SPRITE_DP.dp(), COLLAPSED_SPRITE_DP.dp()).apply {
                gravity = Gravity.BOTTOM
            }
            background = roundedBackground(Color.rgb(255, 249, 230), Color.rgb(109, 58, 161), COLLAPSED_SPRITE_DP.dp())
            setPadding(5.dp(), 5.dp(), 5.dp(), 5.dp())
            requestLayout()
        }
        spriteView?.apply {
            setImageResource(R.drawable.clochette_blacklace_portrait)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            background = null
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            requestLayout()
        }
        micBadgeView?.visibility = View.GONE
    }

    private fun useMicSprite(recording: Boolean) {
        spriteContainerView?.apply {
            layoutParams = LinearLayout.LayoutParams(MIC_SPRITE_DP.dp(), MIC_SPRITE_DP.dp()).apply {
                gravity = Gravity.BOTTOM
            }
            background = micHaloBackground(recording)
            setPadding(5.dp(), 5.dp(), 5.dp(), 5.dp())
            requestLayout()
        }
        spriteView?.apply {
            setImageResource(R.drawable.clochette_blacklace_portrait)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            background = null
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            requestLayout()
        }
        micBadgeView?.apply {
            visibility = View.VISIBLE
            background = micBadgeBackground(recording)
            setTextColor(if (recording) Color.WHITE else Color.rgb(86, 48, 132))
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

    private fun showVoiceReplyOverlay(autoStart: Boolean) {
        enterMicOnlyMode()
        if (autoStart) {
            replyStatusView?.text = "J’écoute. 15 secondes maximum."
            showMiniTranscript("")
            setMicButtonRecording(true, automatic = true)
            startVoiceReply()
        } else {
            replyStatusView?.text = "Prêt à écouter"
            showMiniTranscript("")
            setMicButtonRecording(false)
        }
    }

    private fun startVoiceReply(
        status: String = "J’écoute. 15 secondes maximum.",
        hint: String = "Parle, je note ici ce que j’entends.",
    ) {
        if (listening) return
        enterMicOnlyMode()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            replyStatusView?.text = "Permission micro requise"
            showMiniTranscript("Autorise le micro dans l’app.")
            setMicButtonRecording(false)
            Toast.makeText(this, "Autorise le micro pour répondre à Clochette.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, VoiceReplyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            replyStatusView?.text = "Micro indisponible"
            showMiniTranscript("Reconnaissance vocale indisponible.")
            setMicButtonRecording(false)
            return
        }
        listening = true
        replyStatusView?.text = status
        showMiniTranscript("")
        setMicButtonRecording(true)
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

    private fun finishManualVoiceReply() {
        if (!listening) return
        listening = false
        handler.removeCallbacks(stopListeningRunnable)
        replyStatusView?.text = "Je transforme ça en mots."
        showMiniTranscript("Je transforme ça en mots...")
        setMicButtonRecording(false)
        recognizer?.stopListening()
    }

    private fun stopVoiceReply(message: String) {
        if (!listening) return
        listening = false
        replyStatusView?.text = message
        showMiniTranscript("Pas grave. Je range la question.")
        ClochetteRuntimeStatus.recordAction(this, "micro fermé overlay")
        setMicButtonRecording(false)
        recognizer?.stopListening()
        handler.postDelayed({
            exitMicOnlyMode()
        }, REPLY_IDLE_COLLAPSE_MS)
    }

    private fun closeVoiceReplyPanel() {
        if (listening) {
            stopVoiceReply("Micro fermé")
        } else {
            replyStatusView?.text = "Micro fermé"
            showMiniTranscript("")
            setMicButtonRecording(false)
        }
        exitMicOnlyMode()
    }

    private val replyListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            replyStatusView?.text = "J’écoute."
            showMiniTranscript("")
            setMicButtonRecording(true)
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            listening = false
            replyStatusView?.text = "Je transforme ça en mots."
            showMiniTranscript("Je transforme ça en mots...")
            setMicButtonRecording(false)
        }

        override fun onError(error: Int) {
            listening = false
            handler.removeCallbacks(stopListeningRunnable)
            replyStatusView?.text = "Je n’ai pas bien attrapé."
            showMiniTranscript("Je n’ai pas bien attrapé.")
            ClochetteRuntimeStatus.recordAction(this@ClochetteOverlayService, "micro fermé overlay")
            setMicButtonRecording(false)
        }

        override fun onResults(results: Bundle?) {
            listening = false
            handler.removeCallbacks(stopListeningRunnable)
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            replyTranscriptView?.text = if (text.isBlank()) {
                "Je n’ai rien entendu."
            } else {
                "J’ai entendu : “$text”"
            }
            replyTranscriptView?.visibility = View.VISIBLE
            setMicButtonRecording(false)
            finishOverlayReply(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (partial.isNotBlank()) {
                showMiniTranscript("J’entends : “$partial”")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun finishOverlayReply(userText: String) {
        ClochetteRuntimeStatus.recordAction(this, "micro fermé overlay")
        val decision = OctopusCore.intervene(
            context = this,
            trigger = OctopusCore.TRIGGER_VOICE_TRANSCRIPTION,
            transcription = userText,
            forceSpeak = true,
        )
        replyStatusView?.text = "Réponse prête"
        lineView?.text = decision.finalLine
        sourceView?.text = debugLine()
        handler.postDelayed({ exitMicOnlyMode() }, 700L)
    }

    private fun showMiniTranscript(text: String) {
        replyTranscriptView?.apply {
            this.text = text
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun setMicButtonRecording(recording: Boolean, automatic: Boolean = false) {
        useMicSprite(recording)
        spriteContainerView?.contentDescription = if (recording) {
            if (automatic) "Écoute automatique en cours" else "Enregistrement en cours"
        } else {
            "Maintenir Clochette pour parler"
        }
    }

    private fun roundedBackground(fill: Int, stroke: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(fill)
            setStroke(2.dp(), stroke)
        }

    private fun micBadgeBackground(recording: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (recording) Color.rgb(128, 75, 169) else Color.rgb(255, 253, 244))
            setStroke(
                if (recording) 4.dp() else 2.dp(),
                if (recording) Color.rgb(224, 190, 255) else Color.rgb(109, 58, 161),
            )
        }

    private fun micHaloBackground(recording: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(255, 249, 230))
            setStroke(
                if (recording) 5.dp() else 2.dp(),
                if (recording) Color.rgb(156, 89, 190) else Color.rgb(109, 58, 161),
            )
        }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_SHOW = "com.feuch.clochette.overlay.SHOW"
        const val ACTION_HIDE = "com.feuch.clochette.overlay.HIDE"
        const val ACTION_NEXT_LINE = "com.feuch.clochette.overlay.NEXT_LINE"
        const val ACTION_OPEN_MIC = "com.feuch.clochette.overlay.OPEN_MIC"
        private const val COLLAPSED_SPRITE_DP = 68
        private const val MIC_SPRITE_DP = 76
        private const val MIC_BADGE_DP = 22
        private const val EXPANDED_SPRITE_WIDTH_DP = 78
        private const val EXPANDED_SPRITE_HEIGHT_DP = 140
        private const val BUBBLE_AUTO_HIDE_MS = 25_000L
        private const val DIAGNOSTIC_BUBBLE_HIDE_MS = 60_000L
        private const val MAX_LISTEN_MS = 15_000L
        private const val REPLY_IDLE_COLLAPSE_MS = 5_000L
    }
}
