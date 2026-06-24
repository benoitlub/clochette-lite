package com.feuch.clochette

import android.app.Service
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
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
    private var debugPanelView: LinearLayout? = null
    private var debugContentView: TextView? = null
    private var settingsPanelView: LinearLayout? = null
    private var settingsRowView: View? = null
    private var replyPanel: LinearLayout? = null
    private var replyStatusView: TextView? = null
    private var replyTranscriptView: TextView? = null
    private var spriteContainerView: FrameLayout? = null
    private var medallionBaseView: View? = null
    private var micBadgeView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var collapsedCallDot = false
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var micOnlyMode = false
    private var voiceState = VoiceCaptureState.IDLE
    private var voiceCaptureStartedAt = 0L
    private var voiceCaptureDurationMs = INITIAL_LISTEN_MS
    private var voiceAppendMode = false
    private var voiceTriggerSource = VoiceTriggerSource.MICRO_BUTTON
    private var recognizerReady = false
    private var pendingAutoMicRequestedAt = 0L
    private var autoMicAttemptedForRequest = false
    private var accumulatedTranscript = ""
    private var latestPartialTranscript = ""
    private var voiceSessionId = 0L
    private val hideBubbleRunnable = Runnable { collapseOverlayIfIdle() }
    private val stopListeningRunnable = Runnable { stopVoiceCapture(process = true) }
    private val forceProcessRunnable = Runnable { forceProcessCurrentCapture() }
    private val recognizerReadyTimeoutRunnable = Runnable {
        if (!recognizerReady && voiceState == VoiceCaptureState.PREPARING) {
            resetRecognizer("ready_timeout")
            showVoiceFailure("Le micro ne répond pas. Touche l’icône micro pour réessayer.", "ready_timeout")
        }
    }
    private val showReplyPromptRunnable = object : Runnable {
        override fun run() {
            val requestedAt = pendingAutoMicRequestedAt
            if (requestedAt == 0L || autoMicAttemptedForRequest) return
            val elapsed = System.currentTimeMillis() - requestedAt
            val ttsSpeaking = VoiceInteractionController.isTtsSpeakingNow(this@ClochetteOverlayService)
            val ttsDoneAt = VoiceInteractionController.lastTtsDoneAt(this@ClochetteOverlayService)
            val completedThisRequest = ttsDoneAt >= requestedAt ||
                ttsDoneAt > 0L && requestedAt - ttsDoneAt in 0L..RECENT_TTS_DONE_TOLERANCE_MS
            val safetyElapsed = VoiceInteractionController.timeSinceTtsDoneMs(this@ClochetteOverlayService) >= POST_TTS_PROMPT_DELAY_MS

            if (elapsed >= AUTO_MIC_TTS_HARD_TIMEOUT_MS) {
                pendingAutoMicRequestedAt = 0L
                autoMicAttemptedForRequest = true
                VoiceInteractionController.markTtsError(this@ClochetteOverlayService, "auto_mic_wait_timeout")
                showManualReplyPrompt()
                Log.d(TAG, "auto mic cancelled: TTS completion timeout")
            } else if (ttsSpeaking || completedThisRequest && !safetyElapsed) {
                handler.postDelayed(this, 150L)
            } else if (completedThisRequest && safetyElapsed) {
                autoMicAttemptedForRequest = true
                pendingAutoMicRequestedAt = 0L
                startVoiceCapture(
                    durationMs = INITIAL_LISTEN_MS,
                    appendMode = false,
                    source = VoiceTriggerSource.AUTO_AFTER_TTS,
                )
            } else if (elapsed >= AUTO_MIC_NO_TTS_TIMEOUT_MS) {
                pendingAutoMicRequestedAt = 0L
                autoMicAttemptedForRequest = true
                showManualReplyPrompt()
                Log.d(TAG, "auto mic cancelled: no TTS completion callback state=${VoiceInteractionController.ttsState(this@ClochetteOverlayService)}")
            } else {
                handler.postDelayed(this, 150L)
            }
        }
    }
    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (!listening) return
            updateVoiceCountdown()
            handler.postDelayed(this, 1_000L)
        }
    }

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
                handler.removeCallbacks(showReplyPromptRunnable)
                if (intent.getBooleanExtra(EXTRA_AUTO_AFTER_TTS, false)) {
                    pendingAutoMicRequestedAt = System.currentTimeMillis()
                    autoMicAttemptedForRequest = false
                    handler.post(showReplyPromptRunnable)
                } else {
                    pendingAutoMicRequestedAt = 0L
                    autoMicAttemptedForRequest = true
                    showManualReplyPrompt()
                }
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
        debugPanelView = null
        debugContentView = null
        settingsPanelView = null
        settingsRowView = null
        replyPanel = null
        replyStatusView = null
        replyTranscriptView = null
        spriteContainerView = null
        medallionBaseView = null
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
            val appearance = ClochetteAppearanceSettings.read(this@ClochetteOverlayService)
            gravity = Gravity.BOTTOM or horizontalGravity(appearance.side)
            x = appearance.x
            y = appearance.y
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM or Gravity.END
            setPadding(6.dp(), 6.dp(), 6.dp(), 6.dp())
            clipChildren = false
            clipToPadding = false
        }
        rootLayout = root
        val personality = ClochettePersonalitySettings.read(this)
        val bubbleWidthFactor = when {
            personality.phraseLength >= 70 -> 0.74f
            personality.phraseLength <= 30 -> 0.58f
            else -> 0.68f
        }
        val bubbleMaxWidth = (resources.displayMetrics.widthPixels * bubbleWidthFactor)
            .toInt()
            .coerceIn(200.dp(), 360.dp())

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
            maxLines = bubbleMaxLines()
            ellipsize = null
            isSingleLine = false
            setPadding(0, 0, 0, 6.dp())
        }

        val settingsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        settingsRow.addView(actionButton("Répondre") { showManualReplyPrompt() })
        settingsRow.addView(actionButton("Réglages") { showOverlaySettingsPanel() })
        settingsRowView = settingsRow

        bubble.addView(lineView)
        bubble.addView(buildReplyPanel())
        bubble.addView(buildDebugPanel(bubbleMaxWidth))
        bubble.addView(buildSettingsPanel(bubbleMaxWidth))
        bubble.addView(settingsRow)

        val sprite = ImageView(this).apply {
            setImageResource(currentCharacter().visualAssets.idle)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(0, 0, 0, 0)
            elevation = 14f
        }
        spriteView = sprite
        val medallionBase = View(this).apply {
            background = roundedBackground(Color.rgb(255, 249, 230), Color.rgb(109, 58, 161), COLLAPSED_SPRITE_DP.dp())
            elevation = 10f
            visibility = View.GONE
        }
        medallionBaseView = medallionBase
        val micBadge = TextView(this).apply {
            text = "\uD83C\uDFA4"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(86, 48, 132))
            background = micBadgeBackground(recording = false)
            elevation = 18f
            isClickable = true
            contentDescription = "Micro Clochette"
            setOnClickListener {
                if (VoiceInteractionController.shouldAcceptTap(this@ClochetteOverlayService)) {
                    VoiceInteractionController.recordTouch(this@ClochetteOverlayService, "MICRO_BUTTON", overlayMode(), canExpand = false)
                    if (micOnlyMode || listening || voiceState != VoiceCaptureState.IDLE) {
                        handleMicTap(VoiceTriggerSource.MICRO_BUTTON)
                    } else {
                        startVoiceCapture(INITIAL_LISTEN_MS, appendMode = false, source = VoiceTriggerSource.MICRO_BUTTON)
                    }
                }
            }
            visibility = View.GONE
        }
        micBadgeView = micBadge
        val spriteContainer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            addView(
                medallionBase,
                FrameLayout.LayoutParams(COLLAPSED_SPRITE_DP.dp(), COLLAPSED_SPRITE_DP.dp(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL),
            )
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

        installTapRefreshBehavior(bubble)
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

    private fun buildDebugPanel(maxWidth: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp(), 6.dp(), 8.dp(), 6.dp())
            background = roundedBackground(Color.rgb(244, 238, 250), Color.rgb(180, 153, 204), 12.dp())
            visibility = View.GONE
            debugPanelView = this

            addView(actionButton("Diagnostic") {
                val expanded = !OverlayDebugSettings.isExpanded(this@ClochetteOverlayService)
                OverlayDebugSettings.setExpanded(this@ClochetteOverlayService, expanded)
                refreshDebugPanel()
            })
            debugContentView = TextView(this@ClochetteOverlayService).apply {
                textSize = 8f
                setTextColor(Color.rgb(82, 61, 101))
                this.maxWidth = maxWidth
                maxLines = 12
                isSingleLine = false
                setPadding(2.dp(), 4.dp(), 2.dp(), 2.dp())
            }
            addView(debugContentView)
            refreshDebugPanel()
        }

    private fun buildSettingsPanel(maxWidth: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10.dp(), 8.dp(), 10.dp(), 8.dp())
            background = roundedBackground(Color.rgb(250, 245, 255), Color.rgb(146, 102, 184), 14.dp())
            visibility = View.GONE
            minimumWidth = 210.dp()
            this.layoutParams = LinearLayout.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
            settingsPanelView = this
            refreshOverlaySettingsPanel(maxWidth)
        }

    private fun showOverlaySettingsPanel() {
        handler.removeCallbacks(hideBubbleRunnable)
        lineView?.visibility = View.GONE
        replyPanel?.visibility = View.GONE
        debugPanelView?.visibility = View.GONE
        settingsRowView?.visibility = View.GONE
        settingsPanelView?.visibility = View.VISIBLE
        refreshOverlaySettingsPanel()
        bubbleView?.visibility = View.VISIBLE
        expandSprite()
    }

    private fun closeOverlaySettingsPanel() {
        settingsPanelView?.visibility = View.GONE
        lineView?.visibility = View.VISIBLE
        settingsRowView?.visibility = View.VISIBLE
        refreshDebugPanel()
        scheduleBubbleHide()
    }

    private fun refreshOverlaySettingsPanel(maxWidth: Int = 320.dp()) {
        val panel = settingsPanelView ?: return
        panel.removeAllViews()
        val voice = ClochetteVoiceSettings.read(this)
        val personality = ClochettePersonalitySettings.read(this)
        val relationship = RelationshipModeSettings.selected(this)
        val guardian = ClochetteRuntimeStatus.read(this).lastGuardianDecision
        val debugEnabled = OverlayDebugSettings.isEnabled(this)

        panel.addView(settingsText("Réglages", 15f, bold = true, maxWidth = maxWidth))
        panel.addView(settingsText("Personnage : ${currentCharacter().displayName}", maxWidth = maxWidth))
        panel.addView(actionButton("Voix : ${if (voice.enabled) "activée" else "désactivée"}") {
            ClochetteVoiceSettings.save(this, voice.copy(enabled = !voice.enabled))
            refreshOverlaySettingsPanel(maxWidth)
        })
        panel.addView(settingsText("Bavardise : ${personality.talkativeness}/100", maxWidth = maxWidth))
        panel.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(actionButton("−") {
                    ClochettePersonalitySettings.save(
                        this@ClochetteOverlayService,
                        personality.copy(talkativeness = personality.talkativeness - 10),
                    )
                    refreshOverlaySettingsPanel(maxWidth)
                })
                addView(actionButton("+") {
                    ClochettePersonalitySettings.save(
                        this@ClochetteOverlayService,
                        personality.copy(talkativeness = personality.talkativeness + 10),
                    )
                    refreshOverlaySettingsPanel(maxWidth)
                })
            },
        )
        panel.addView(settingsText("Mode : ${relationship.name}", maxWidth = maxWidth))
        panel.addView(settingsText("Guardian : $guardian", maxWidth = maxWidth))
        panel.addView(actionButton("Debug : ${if (debugEnabled) "activé" else "désactivé"}") {
            OverlayDebugSettings.setEnabled(this, !debugEnabled)
            refreshOverlaySettingsPanel(maxWidth)
        })
        panel.addView(actionButton("Réglages complets") { openMainActivity("settings") })
        panel.addView(actionButton("Fermer") { closeOverlaySettingsPanel() })
    }

    private fun settingsText(
        value: String,
        size: Float = 11f,
        bold: Boolean = false,
        maxWidth: Int,
    ): TextView = TextView(this).apply {
        text = value.withVisibleFrenchAccents()
        textSize = size
        setTextColor(Color.rgb(44, 24, 63))
        this.maxWidth = maxWidth
        maxLines = 3
        isSingleLine = false
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(2.dp(), 3.dp(), 2.dp(), 3.dp())
    }

    private fun refreshDebugPanel() {
        val enabled = OverlayDebugSettings.isEnabled(this)
        val expanded = enabled && OverlayDebugSettings.isExpanded(this)
        debugPanelView?.visibility = if (enabled && !micOnlyMode && settingsPanelView?.visibility != View.VISIBLE) {
            View.VISIBLE
        } else {
            View.GONE
        }
        debugContentView?.apply {
            text = debugLine().withVisibleFrenchAccents()
            visibility = if (expanded) View.VISIBLE else View.GONE
        }
    }

    private fun installTapRefreshBehavior(bubble: View) {
        var downX = 0f
        var downY = 0f
        var downAt = 0L
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downAt = System.currentTimeMillis()
                    handler.removeCallbacks(hideBubbleRunnable)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop
                    val longPress = System.currentTimeMillis() - downAt >= LONG_PRESS_MIC_MS
                    VoiceInteractionController.recordTouch(this@ClochetteOverlayService, "BUBBLE_TAP", overlayMode(), canExpand = true)
                    if (!moved && !longPress) {
                        showBubbleTemporarily()
                    } else {
                        scheduleBubbleHide()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    scheduleBubbleHide()
                    true
                }
                else -> true
            }
        }
    }

    private fun installDragBehavior(root: View, sprite: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var downAt = 0L
        var moved = false
        var longPressTriggered = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val longPressRunnable = Runnable {
            if (!moved) {
                longPressTriggered = true
                VoiceInteractionController.recordTouch(this, "AVATAR_LONG_PRESS", overlayMode(), canExpand = true, canDrag = true)
                Toast.makeText(this, "Glisse-moi pour déplacer. Le micro a son bouton.", Toast.LENGTH_SHORT).show()
            }
        }

        val listener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(hideBubbleRunnable)
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    downAt = System.currentTimeMillis()
                    moved = false
                    longPressTriggered = false
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MIC_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        moved = true
                        handler.removeCallbacks(longPressRunnable)
                        VoiceInteractionController.recordTouch(this, "AVATAR_DRAG", overlayMode(), canExpand = true, canDrag = true)
                        params.x = (startX - dx).coerceAtLeast(0)
                        params.y = (startY - dy).coerceAtLeast(0)
                        overlay?.let { windowManager.updateViewLayout(it, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    val pressDuration = System.currentTimeMillis() - downAt
                    if (longPressTriggered || (!moved && pressDuration >= LONG_PRESS_MIC_MS)) {
                        VoiceInteractionController.recordTouch(this, "AVATAR_LONG_PRESS", overlayMode(), canExpand = true, canDrag = true)
                    } else if (!moved) {
                        val wasReduced = bubbleView?.visibility != View.VISIBLE
                        VoiceInteractionController.recordTouch(
                            this,
                            if (wasReduced) "AVATAR_REDUCED_TAP" else "AVATAR_EXPANDED_TAP",
                            overlayMode(),
                            canExpand = true,
                            canDrag = true,
                        )
                        if (wasReduced || listening || micOnlyMode) showBubbleTemporarily() else collapseOverlayIfIdle()
                    } else {
                        ClochetteAppearanceSettings.savePosition(this, params.x, params.y)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (moved) ClochetteAppearanceSettings.savePosition(this, params.x, params.y)
                    true
                }
                else -> false
            }
        }
        root.setOnTouchListener(null)
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
        lineView?.apply {
            maxLines = bubbleMaxLines()
            text = line.withVisibleFrenchAccents()
        }
        applyCharacterVisual("talking")
        refreshDebugPanel()
        showBubbleTemporarily()
    }

    private fun bubbleMaxLines(): Int {
        val length = ClochettePersonalitySettings.read(this).phraseLength
        return when {
            length <= 30 -> 4
            length >= 70 -> 8
            else -> 6
        }
    }

    private fun debugLine(): String {
        val ai = AiGatewaySettings.read(this)
        val runtime = ClochetteRuntimeStatus.read(this)
        val voiceState = VoiceInteractionController.state(this)
        return "perso : ${currentCharacter().displayName} · source : ${ClochetteRemarkStore.latestSource(this).id} · voix : ${runtime.lastVoiceAction} · état : ${voiceState.name.lowercase()} · guardian : ${runtime.lastGuardianDecision} · provider : ${ai.lastProviderUsed ?: "aucun"} · ${VoiceInteractionController.diagnostic(this)}"
    }

    private fun overlayMode(): String =
        if (bubbleView?.visibility == View.VISIBLE) "EXPANDED" else "REDUCED"

    private fun currentCharacter(): CharacterProfile =
        CharacterRegistry.get(this, ClochetteRemarkStore.latestCharacterId(this))

    private fun applyCharacterVisual(state: String) {
        val character = currentCharacter()
        val asset = when (state) {
            "listening" -> character.visualAssets.listening ?: character.visualAssets.idle
            "closed_edge" -> character.visualAssets.closedEdge ?: character.visualAssets.idle
            "call_dot" -> character.visualAssets.callDot ?: character.visualAssets.idle
            "talking" -> character.visualAssets.talking
            else -> character.visualAssets.idle
        }
        spriteView?.setImageResource(asset)
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
        debugPanelView?.visibility = View.GONE
        settingsPanelView?.visibility = View.GONE
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
        settingsRowView?.visibility = View.VISIBLE
        settingsPanelView?.visibility = View.GONE
        replyPanel?.visibility = View.GONE
        replyStatusView?.visibility = View.GONE
        replyTranscriptView?.visibility = View.GONE
        setMicButtonRecording(false)
        expandSprite()
        refreshDebugPanel()
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
        when (ClochetteAppearanceSettings.read(this).mode) {
            ClosedAppearanceMode.CALL_DOT -> collapseToCallDot()
            ClosedAppearanceMode.EDGE_PEEK -> collapseToEdgePeek()
        }
    }

    private fun expandSprite() {
        collapsedCallDot = false
        spriteContainerView?.apply {
            layoutParams = LinearLayout.LayoutParams(EXPANDED_SPRITE_WIDTH_DP.dp(), EXPANDED_SPRITE_HEIGHT_DP.dp()).apply {
                gravity = Gravity.BOTTOM
            }
            background = null
            setPadding(0, 0, 0, 0)
            clipChildren = false
            clipToPadding = false
            requestLayout()
        }
        medallionBaseView?.visibility = View.GONE
        spriteView?.apply {
            visibility = View.VISIBLE
            alpha = 0.96f
            setImageResource(currentCharacter().visualAssets.talking)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            background = null
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.FIT_CENTER
            requestLayout()
        }
        micBadgeView?.apply {
            visibility = View.VISIBLE
            background = micBadgeBackground(recording = false)
            setTextColor(Color.rgb(86, 48, 132))
        }
    }

    private fun collapseToEdgePeek() {
        collapsedCallDot = false
        val appearance = ClochetteAppearanceSettings.read(this)
        layoutParams?.let { params ->
            params.gravity = Gravity.BOTTOM or horizontalGravity(appearance.side)
            params.x = if (appearance.side == ClosedAppearanceSide.LEFT) (-18).dp() else (-16).dp()
            overlay?.let { windowManager.updateViewLayout(it, params) }
        }
        spriteContainerView?.apply {
            layoutParams = LinearLayout.LayoutParams(EDGE_PEEK_TOUCH_DP.dp(), COLLAPSED_SPRITE_DP.dp()).apply {
                gravity = Gravity.BOTTOM
            }
            background = roundedBackground(Color.TRANSPARENT, Color.TRANSPARENT, COLLAPSED_SPRITE_DP.dp())
            clipChildren = false
            clipToPadding = false
            clipToOutline = false
            outlineProvider = roundedOutlineProvider(COLLAPSED_SPRITE_DP.dp())
            setPadding(0, 0, 0, 0)
            requestLayout()
        }
        medallionBaseView?.apply {
            visibility = View.VISIBLE
            layoutParams = FrameLayout.LayoutParams(COLLAPSED_SPRITE_DP.dp(), COLLAPSED_SPRITE_DP.dp(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            requestLayout()
        }
        spriteView?.apply {
            applyCharacterVisual("closed_edge")
            alpha = 0.90f
            layoutParams = FrameLayout.LayoutParams(COLLAPSED_PORTRAIT_DP.dp(), COLLAPSED_PORTRAIT_DP.dp(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = (-6).dp()
            }
            background = null
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            requestLayout()
        }
        micBadgeView?.apply {
            visibility = View.VISIBLE
            background = micBadgeBackground(recording = false)
            setTextColor(Color.rgb(86, 48, 132))
        }
    }

    private fun collapseToCallDot() {
        collapsedCallDot = true
        val appearance = ClochetteAppearanceSettings.read(this)
        layoutParams?.let { params ->
            params.gravity = Gravity.BOTTOM or horizontalGravity(appearance.side)
            params.x = appearance.x
            params.y = appearance.y
            overlay?.let { windowManager.updateViewLayout(it, params) }
        }
        spriteContainerView?.apply {
            layoutParams = LinearLayout.LayoutParams(CALL_DOT_TOUCH_DP.dp(), CALL_DOT_TOUCH_DP.dp()).apply {
                gravity = Gravity.BOTTOM
            }
            background = null
            clipChildren = false
            clipToPadding = false
            setPadding(0, 0, 0, 0)
            requestLayout()
        }
        medallionBaseView?.apply {
            visibility = View.VISIBLE
            alpha = 0.96f
            background = roundedBackground(Color.rgb(126, 75, 180), Color.rgb(255, 249, 230), CALL_DOT_VISUAL_DP.dp())
            layoutParams = FrameLayout.LayoutParams(CALL_DOT_VISUAL_DP.dp(), CALL_DOT_VISUAL_DP.dp(), Gravity.CENTER)
            requestLayout()
        }
        spriteView?.apply {
            visibility = View.GONE
            requestLayout()
        }
        micBadgeView?.apply {
            visibility = View.VISIBLE
            background = micBadgeBackground(recording = false)
            setTextColor(Color.rgb(86, 48, 132))
        }
    }

    private fun roundedOutlineProvider(radius: Int): ViewOutlineProvider =
        object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius.toFloat())
            }
        }

    private fun isClosedCallDot(): Boolean =
        collapsedCallDot && bubbleView?.visibility != View.VISIBLE

    private fun useMicSprite(recording: Boolean) {
        spriteContainerView?.apply {
            layoutParams = LinearLayout.LayoutParams(MIC_SPRITE_DP.dp(), MIC_SPRITE_DP.dp()).apply {
                gravity = Gravity.BOTTOM
            }
            background = micHaloBackground(recording)
            setPadding(5.dp(), 5.dp(), 5.dp(), 5.dp())
            clipChildren = false
            clipToPadding = false
            requestLayout()
        }
        medallionBaseView?.visibility = View.GONE
        spriteView?.apply {
            visibility = View.VISIBLE
            applyCharacterVisual("listening")
            alpha = 0.96f
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

    private fun showManualReplyPrompt() {
        micOnlyMode = false
        bubbleView?.visibility = View.VISIBLE
        lineView?.visibility = View.VISIBLE
        settingsRowView?.visibility = View.VISIBLE
        settingsPanelView?.visibility = View.GONE
        replyPanel?.visibility = View.VISIBLE
        replyStatusView?.apply {
            text = "À toi — touche le micro pour répondre."
            visibility = View.VISIBLE
        }
        replyTranscriptView?.visibility = View.GONE
        expandSprite()
        refreshDebugPanel()
        handler.removeCallbacks(hideBubbleRunnable)
        handler.postDelayed(hideBubbleRunnable, DIAGNOSTIC_BUBBLE_HIDE_MS)
    }

    private fun handleMicTap(source: VoiceTriggerSource) {
        when (voiceState) {
            VoiceCaptureState.PREPARING -> {
                resetRecognizer("user_cancelled_preparing")
                voiceState = VoiceCaptureState.IDLE
                VoiceInteractionController.transition(this, VoiceInteractionState.IDLE, "mic_preparing_cancelled")
                showManualReplyPrompt()
            }
            VoiceCaptureState.LISTENING_INITIAL,
            VoiceCaptureState.LISTENING_EXTRA -> stopVoiceCapture(process = true)
            VoiceCaptureState.PROCESSING -> Unit
            VoiceCaptureState.ERROR -> startVoiceCapture(INITIAL_LISTEN_MS, appendMode = false, source = source)
            VoiceCaptureState.IDLE -> {
                val append = accumulatedTranscript.isNotBlank()
                startVoiceCapture(if (append) EXTRA_LISTEN_MS else INITIAL_LISTEN_MS, appendMode = append, source = source)
            }
        }
    }

    private fun startVoiceCapture(durationMs: Long, appendMode: Boolean = false, source: VoiceTriggerSource) {
        if (listening || voiceState == VoiceCaptureState.PREPARING) return
        if (!VoiceInteractionController.canStartListening(this, source)) {
            lineView?.text = if (VoiceInteractionController.isTtsSpeakingNow(this)) {
                "Je termine ma phrase. Le micro reste fermé."
            } else {
                "Un instant. Touche le micro quand il redevient disponible."
            }
            showBubbleTemporarily()
            return
        }
        if (!ClochetteVoice.prepareForListening(this)) {
            lineView?.text = "Je termine ma phrase. Le micro reste fermé."
            showBubbleTemporarily()
            return
        }
        val sessionId = resetVoiceSessionForStart(appendMode)
        enterMicOnlyMode()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            voiceState = VoiceCaptureState.ERROR
            showMiniTranscript("Micro non autorisé. Ouvre les réglages de l’app.")
            setMicButtonRecording(false)
            Toast.makeText(this, "Autorise le micro pour r\u00e9pondre \u00e0 Clochette.", Toast.LENGTH_LONG).show()
            VoiceInteractionController.transition(this, VoiceInteractionState.IDLE, "micro_permission_missing")
            if (!PermissionStateManager.wasAsked(this, ClochettePermissionKey.MICROPHONE)) {
                PermissionStateManager.markAsked(this, ClochettePermissionKey.MICROPHONE)
                openAppPermissionSettings()
            }
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceState = VoiceCaptureState.ERROR
            showMiniTranscript("Reconnaissance vocale indisponible.")
            setMicButtonRecording(false)
            VoiceInteractionController.transition(this, VoiceInteractionState.IDLE, "speech_recognizer_unavailable")
            return
        }
        if (!appendMode) {
            accumulatedTranscript = ""
        }
        latestPartialTranscript = ""
        recognizerReady = false
        voiceAppendMode = appendMode
        voiceTriggerSource = source
        voiceCaptureDurationMs = durationMs
        voiceState = VoiceCaptureState.PREPARING
        listening = false
        VoiceInteractionController.markRecognizerState(this, "PREPARING")
        showMiniTranscript(
            if (source == VoiceTriggerSource.AUTO_AFTER_TTS) "À toi — préparation du micro…" else "Préparation du micro…",
        )
        setMicButtonRecording(false)
        Log.d(TAG, "voice session $sessionId preparing durationMs=$durationMs append=$appendMode source=$source")

        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(newReplyListener(sessionId))
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
            )
        }
        handler.removeCallbacks(stopListeningRunnable)
        handler.removeCallbacks(countdownRunnable)
        handler.removeCallbacks(recognizerReadyTimeoutRunnable)
        handler.postDelayed(recognizerReadyTimeoutRunnable, RECOGNIZER_READY_TIMEOUT_MS)
    }

    private fun stopVoiceCapture(process: Boolean = true) {
        if (!listening) return
        listening = false
        val sessionId = voiceSessionId
        voiceState = if (process) VoiceCaptureState.PROCESSING else VoiceCaptureState.IDLE
        VoiceInteractionController.transition(this, if (process) VoiceInteractionState.TRANSCRIBING else VoiceInteractionState.IDLE, "voice_stop_process_$process")
        handler.removeCallbacks(stopListeningRunnable)
        handler.removeCallbacks(countdownRunnable)
        handler.removeCallbacks(forceProcessRunnable)
        setMicButtonRecording(false)
        Log.d(TAG, "voice session $sessionId stop process=$process")
        if (process) {
            showMiniTranscript(currentTranscriptText().ifBlank { "Je transforme \u00e7a en mots..." })
            recognizer?.stopListening()
            handler.postDelayed(forceProcessRunnable, RESULT_GRACE_MS)
        } else {
            recognizer?.cancel()
            ClochetteRuntimeStatus.recordAction(this, "micro ferm\u00e9 overlay")
            offerExtraListeningWindow()
        }
    }

    private fun resetVoiceSessionForStart(appendMode: Boolean): Long {
        handler.removeCallbacks(stopListeningRunnable)
        handler.removeCallbacks(countdownRunnable)
        handler.removeCallbacks(forceProcessRunnable)
        handler.removeCallbacks(recognizerReadyTimeoutRunnable)
        voiceSessionId += 1L
        listening = false
        recognizerReady = false
        voiceState = VoiceCaptureState.IDLE
        latestPartialTranscript = ""
        if (!appendMode) {
            accumulatedTranscript = ""
            showMiniTranscript("")
        }
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        VoiceInteractionController.markRecognizerState(this, "IDLE")
        Log.d(TAG, "voice session $voiceSessionId reset append=$appendMode")
        VoiceInteractionController.transition(this, VoiceInteractionState.IDLE, "voice_session_reset")
        return voiceSessionId
    }

    private fun resetRecognizer(reason: String) {
        handler.removeCallbacks(stopListeningRunnable)
        handler.removeCallbacks(countdownRunnable)
        handler.removeCallbacks(forceProcessRunnable)
        handler.removeCallbacks(recognizerReadyTimeoutRunnable)
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        voiceSessionId += 1L
        listening = false
        recognizerReady = false
        latestPartialTranscript = ""
        VoiceInteractionController.markRecognizerState(this, "IDLE:$reason")
        Log.d(TAG, "voice session $voiceSessionId recognizer reset reason=$reason")
    }

    private fun showVoiceFailure(message: String, reason: String) {
        voiceState = VoiceCaptureState.ERROR
        VoiceInteractionController.transition(this, VoiceInteractionState.ERROR, reason)
        exitMicOnlyMode()
        lineView?.text = message.withVisibleFrenchAccents()
        refreshDebugPanel()
        showBubbleTemporarily()
    }

    private fun forceProcessCurrentCapture() {
        if (voiceState != VoiceCaptureState.PROCESSING) return
        Log.d(TAG, "voice session $voiceSessionId force process after recognizer silence")
        val partial = latestPartialTranscript
        resetRecognizer("force_process")
        mergeRecognizedText(partial)
        setMicButtonRecording(false)
        ClochetteRuntimeStatus.recordAction(this, "micro ferm\u00e9 overlay")
        VoiceInteractionController.transition(this, VoiceInteractionState.COOLDOWN, "voice_force_process")
        offerExtraListeningWindow()
    }

    private fun updateVoiceCountdown() {
        if (!recognizerReady || !listening) return
        val elapsed = System.currentTimeMillis() - voiceCaptureStartedAt
        val remainingMs = (voiceCaptureDurationMs - elapsed).coerceAtLeast(0L)
        val remainingSeconds = ((remainingMs + 999L) / 1_000L).toInt()
        val heard = currentTranscriptText()
        val prefix = when {
            voiceState == VoiceCaptureState.LISTENING_EXTRA -> "+20 s"
            voiceTriggerSource == VoiceTriggerSource.AUTO_AFTER_TTS -> "À toi — j’écoute"
            else -> "J’écoute"
        }
        showMiniTranscript(if (heard.isBlank()) "$prefix… ${remainingSeconds}s" else "$heard · ${remainingSeconds}s")
    }

    private fun currentTranscriptText(): String {
        val base = accumulatedTranscript.trim()
        val partial = latestPartialTranscript.trim()
        return when {
            base.isNotBlank() && partial.isNotBlank() -> "J’entends : $base $partial"
            partial.isNotBlank() -> "J’entends : $partial"
            base.isNotBlank() -> "J’ai entendu : $base"
            else -> ""
        }
    }
    private fun offerExtraListeningWindow() {
        voiceState = VoiceCaptureState.IDLE
        if (accumulatedTranscript.isBlank()) {
            pendingAutoMicRequestedAt = 0L
            autoMicAttemptedForRequest = true
            VoiceInteractionController.recordNoSpeech(this, "empty_transcript")
            VoiceInteractionController.transition(this, VoiceInteractionState.IDLE, "no_speech_idle")
            resetRecognizer("no_speech")
            exitMicOnlyMode()
            lineView?.text = "Je n’ai rien entendu. Touche le micro pour réessayer."
            refreshDebugPanel()
            showBubbleTemporarily()
            Log.d(TAG, "voice session $voiceSessionId no transcript; readable idle")
        } else {
            VoiceInteractionController.transition(this, VoiceInteractionState.COOLDOWN, "voice_offer_extra")
            showMiniTranscript("J’ai entendu : $accumulatedTranscript · touche le micro pour +20 s")
            handler.postDelayed({
                if (!listening && micOnlyMode && voiceState == VoiceCaptureState.IDLE) {
                    finishOverlayReply(accumulatedTranscript)
                }
            }, EXTRA_OFFER_MS)
        }
        setMicButtonRecording(false)
    }
    private fun openAppPermissionSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }


    private fun newReplyListener(sessionId: Long): RecognitionListener = object : RecognitionListener {
        private fun isActiveCallback(): Boolean {
            val active = sessionId == voiceSessionId
            if (!active) Log.d(TAG, "voice session $sessionId ignored stale callback; active=$voiceSessionId")
            return active
        }

        override fun onReadyForSpeech(params: Bundle?) {
            if (!isActiveCallback()) return
            handler.removeCallbacks(recognizerReadyTimeoutRunnable)
            recognizerReady = true
            listening = true
            voiceCaptureStartedAt = System.currentTimeMillis()
            voiceState = if (voiceAppendMode) VoiceCaptureState.LISTENING_EXTRA else VoiceCaptureState.LISTENING_INITIAL
            VoiceInteractionController.markRecognizerState(this@ClochetteOverlayService, "READY_LISTENING")
            VoiceInteractionController.transition(this@ClochetteOverlayService, VoiceInteractionState.LISTENING, "recognizer_ready")
            ClochetteRuntimeStatus.recordAction(this@ClochetteOverlayService, "micro ouvert overlay")
            updateVoiceCountdown()
            setMicButtonRecording(true)
            handler.postDelayed(stopListeningRunnable, voiceCaptureDurationMs)
            handler.postDelayed(countdownRunnable, 1_000L)
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (!isActiveCallback()) return
            if (listening) {
                listening = false
                voiceState = VoiceCaptureState.PROCESSING
                VoiceInteractionController.markRecognizerState(this@ClochetteOverlayService, "PROCESSING")
                VoiceInteractionController.transition(this@ClochetteOverlayService, VoiceInteractionState.TRANSCRIBING, "recognizer_end_of_speech")
                showMiniTranscript(currentTranscriptText().ifBlank { "Je transforme \u00e7a en mots..." })
                setMicButtonRecording(false)
            }
        }

        override fun onError(error: Int) {
            if (!isActiveCallback()) return
            listening = false
            handler.removeCallbacks(stopListeningRunnable)
            handler.removeCallbacks(countdownRunnable)
            handler.removeCallbacks(forceProcessRunnable)
            handler.removeCallbacks(recognizerReadyTimeoutRunnable)
            setMicButtonRecording(false)
            val partial = latestPartialTranscript
            resetRecognizer("error_${recognizerErrorLabel(error)}")
            ClochetteRuntimeStatus.recordAction(this@ClochetteOverlayService, "micro ferm\u00e9 overlay")
            Log.d(TAG, "voice session $sessionId error=${recognizerErrorLabel(error)}")
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    mergeRecognizedText(partial)
                    offerExtraListeningWindow()
                }
                else -> {
                    showVoiceFailure(
                        "Le micro s’est arrêté (${recognizerErrorLabel(error)}). Touche l’icône micro pour réessayer.",
                        "recognizer_error_${recognizerErrorLabel(error)}",
                    )
                }
            }
        }

        override fun onResults(results: Bundle?) {
            if (!isActiveCallback()) return
            listening = false
            handler.removeCallbacks(stopListeningRunnable)
            handler.removeCallbacks(countdownRunnable)
            handler.removeCallbacks(forceProcessRunnable)
            handler.removeCallbacks(recognizerReadyTimeoutRunnable)
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            mergeRecognizedText(text)
            setMicButtonRecording(false)
            resetRecognizer("results")
            ClochetteRuntimeStatus.recordAction(this@ClochetteOverlayService, "micro ferm\u00e9 overlay")
            VoiceInteractionController.transition(this@ClochetteOverlayService, VoiceInteractionState.COOLDOWN, "recognizer_results")
            Log.d(TAG, "voice session $sessionId final='${accumulatedTranscript.take(80)}'")
            offerExtraListeningWindow()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isActiveCallback()) return
            latestPartialTranscript = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (latestPartialTranscript.isNotBlank()) {
                updateVoiceCountdown()
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun recognizerErrorLabel(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "audio"
        SpeechRecognizer.ERROR_CLIENT -> "client"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permission"
        SpeechRecognizer.ERROR_NETWORK -> "network"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network_timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "no_match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer_busy"
        SpeechRecognizer.ERROR_SERVER -> "server"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech_timeout"
        else -> "error_$error"
    }

    private fun mergeRecognizedText(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        accumulatedTranscript = listOf(accumulatedTranscript, clean)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
        latestPartialTranscript = ""
    }

    private fun finishOverlayReply(userText: String) {
        ClochetteRuntimeStatus.recordAction(this, "micro fermé overlay")
        Log.d(TAG, "voice session $voiceSessionId process transcript='${userText.take(80)}'")
        VoiceInteractionController.transition(this, VoiceInteractionState.THINKING, "overlay_transcription_ready")
        val decision = OctopusCore.intervene(
            context = this,
            trigger = OctopusCore.TRIGGER_VOICE_TRANSCRIPTION,
            transcription = userText,
            forceSpeak = true,
        )
        replyStatusView?.text = "Réponse prête"
        lineView?.text = decision.finalLine.withVisibleFrenchAccents()
        refreshDebugPanel()
        handler.postDelayed({
            accumulatedTranscript = ""
            latestPartialTranscript = ""
            exitMicOnlyMode()
            VoiceInteractionController.transition(this, VoiceInteractionState.IDLE, "overlay_reply_finished")
        }, 700L)
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
            "Toucher Clochette pour parler"
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

    private enum class VoiceCaptureState {
        IDLE,
        PREPARING,
        LISTENING_INITIAL,
        LISTENING_EXTRA,
        PROCESSING,
        ERROR,
    }

    companion object {
        private const val TAG = "ClochetteOverlay"
        const val ACTION_SHOW = "com.feuch.clochette.overlay.SHOW"
        const val ACTION_HIDE = "com.feuch.clochette.overlay.HIDE"
        const val ACTION_NEXT_LINE = "com.feuch.clochette.overlay.NEXT_LINE"
        const val ACTION_OPEN_MIC = "com.feuch.clochette.overlay.OPEN_MIC"
        const val EXTRA_AUTO_AFTER_TTS = "auto_after_tts"
        private const val COLLAPSED_SPRITE_DP = 68
        private const val COLLAPSED_OVERFLOW_DP = 14
        private const val COLLAPSED_PORTRAIT_DP = 78
        private const val EDGE_PEEK_TOUCH_DP = 54
        private const val CALL_DOT_TOUCH_DP = 48
        private const val CALL_DOT_VISUAL_DP = 18
        private const val MIC_SPRITE_DP = 76
        private const val MIC_BADGE_DP = 22
        private const val EXPANDED_SPRITE_WIDTH_DP = 78
        private const val EXPANDED_SPRITE_HEIGHT_DP = 140
        private const val BUBBLE_AUTO_HIDE_MS = 25_000L
        private const val DIAGNOSTIC_BUBBLE_HIDE_MS = 60_000L
        private const val INITIAL_LISTEN_MS = 15_000L
        private const val EXTRA_LISTEN_MS = 20_000L
        private const val EXTRA_OFFER_MS = 3_500L
        private const val RESULT_GRACE_MS = 2_500L
        private const val RECOGNIZER_READY_TIMEOUT_MS = 5_000L
        private const val POST_TTS_PROMPT_DELAY_MS = 650L
        private const val AUTO_MIC_NO_TTS_TIMEOUT_MS = 1_500L
        private const val AUTO_MIC_TTS_HARD_TIMEOUT_MS = 30_000L
        private const val RECENT_TTS_DONE_TOLERANCE_MS = 2_000L
        private const val REPLY_IDLE_COLLAPSE_MS = 5_000L
        private const val LONG_PRESS_MIC_MS = 650L
    }

    private fun horizontalGravity(side: ClosedAppearanceSide): Int = when (side) {
        ClosedAppearanceSide.LEFT -> Gravity.START
        ClosedAppearanceSide.RIGHT -> Gravity.END
        ClosedAppearanceSide.AUTO -> Gravity.END
    }
}
