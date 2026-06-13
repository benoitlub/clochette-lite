package com.feuch.clochette

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClochetteApp(startSection = intent.getStringExtra(EXTRA_START_SECTION))
        }
    }

    companion object {
        const val EXTRA_START_SECTION = "start_section"
    }
}

@Composable
private fun ClochetteApp(startSection: String?) {
    val context = LocalContext.current
    val memory = remember { ClochetteMemory(context) }
    var refresh by remember { mutableIntStateOf(0) }
    var project by remember { mutableStateOf(ProjectKnowledge.projects.first().name) }
    var energy by remember { mutableStateOf("moyenne") }
    var currentLine by remember { mutableStateOf<String?>(null) }
    var responseText by remember { mutableStateOf("") }
    var voiceConfig by remember { mutableStateOf(ClochetteVoiceSettings.read(context)) }
    var proactiveConfig by remember { mutableStateOf(ProactiveSettings.read(context)) }
    var aiConfig by remember { mutableStateOf(AiGatewaySettings.read(context)) }
    var aiTestLine by remember { mutableStateOf<String?>(null) }
    var runtimeStatus by remember { mutableStateOf(ClochetteRuntimeStatus.read(context)) }
    var octopusDiagnostics by remember { mutableStateOf(OctopusDiagnosticsStore.read(context)) }
    var relationshipModeId by remember { mutableStateOf(RelationshipModeSettings.selectedId(context)) }
    val personaModules = remember(refresh) { PersonaModuleLoader(context).loadStatuses() }
    val relationshipModes = remember(refresh) { RelationshipModeSettings.modes(context) }
    val usageSnapshot = remember(refresh) { UsageObserver(context).snapshot() }
    val nowPlayingSnapshot = remember(refresh) { NowPlayingObserver.snapshot(context) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    fun updateVoiceConfig(config: ClochetteVoiceConfig) {
        voiceConfig = config
        ClochetteVoiceSettings.save(context, config)
    }

    fun updateProactiveConfig(config: ProactiveConfig) {
        proactiveConfig = config
        ProactiveSettings.save(context, config)
    }

    fun updateAiConfig(config: AiGatewayConfig) {
        aiConfig = config
        AiGatewaySettings.save(context, config)
    }

    fun updateRelationshipMode(modeId: String) {
        relationshipModeId = modeId
        RelationshipModeSettings.saveSelectedId(context, modeId)
    }

    fun recordAction(action: String) {
        ClochetteRuntimeStatus.recordAction(context, action)
        runtimeStatus = ClochetteRuntimeStatus.read(context)
        aiConfig = AiGatewaySettings.read(context)
        octopusDiagnostics = OctopusDiagnosticsStore.read(context)
        refresh++
    }

    fun aiRequest(activity: ActivitySnapshot, recentMemory: List<ClochetteMemoryEntry>, userReply: String? = null): AiRemarkRequest {
        val state = ContextRemarkEngine(context).buildState(activity, energy = energy)
        val nowPlaying = NowPlayingObserver.snapshot(context)
        return AiRemarkRequest(
            relationshipMode = RelationshipModeSettings.selected(context).id,
            preferredProvider = aiConfig.preferredProvider,
            styleLevel = aiConfig.styleLevel,
            foregroundApp = state.currentAppName,
            durationMinutes = state.durationMinutes,
            appSwitchCount = state.recentAppSwitches,
            sensorSummary = "movement=${state.movementState.name.lowercase()} battery=${state.batteryPercent ?: "unknown"}",
            energy = energy,
            recentMemorySummary = recentMemory.mapNotNull { it.clochetteLine }.takeLast(3).joinToString(" | "),
            userLastReply = userReply,
            nowPlayingAppName = nowPlaying.appName,
            nowPlayingTitle = nowPlaying.title,
            nowPlayingArtist = nowPlaying.artist,
        )
    }

    fun acceptLine(
        rawLine: String,
        rawSource: PhraseSource,
        autoSpeak: Boolean,
        shouldSpeakHint: Boolean = autoSpeak,
    ): String {
        val activity = UsageObserver(context).snapshot()
        val recentMemory = memory.recent(24)
        val state = ContextRemarkEngine(context).buildState(activity, energy = energy)
        var source = rawSource
        val guardian = GuardianRulesLoader(context).approve(
            candidate = rawLine.withVisibleFrenchAccents(),
            state = state,
            recentLines = recentMemory.mapNotNull { it.clochetteLine },
            recentEntries = recentMemory,
            relationshipMode = RelationshipModeSettings.selected(context),
            wantsVoice = autoSpeak && shouldSpeakHint,
        )
        val line = guardian.line?.withVisibleFrenchAccents() ?: return ClochetteRemarkStore.latest(context)
        if (line != rawLine.withVisibleFrenchAccents() || guardian.reason != "approved") {
            source = PhraseSource.GUARDIAN_FALLBACK
        }
        currentLine = line
        memory.add(
            ClochetteMemoryEntry(
                context = "main_activity",
                observedSignal = "manual_line",
                project = project,
                energy = energy,
                clochetteLine = line,
                userReaction = null,
                result = "shown",
            ),
        )
        ClochetteWidget.updateAll(context, line, source)
        if (autoSpeak && guardian.shouldSpeak) {
            ClochetteVoice.speakAfterRemark(context, line)
            recordAction("parlé")
        } else {
            recordAction("silencieux")
        }
        return line
    }

    fun generateLine(autoSpeak: Boolean = true): String {
        val activity = UsageObserver(context).snapshot()
        val recentMemory = memory.recent(24)
        val contextEngine = ContextRemarkEngine(context)
        var source = PhraseSource.UNKNOWN
        val rawLine = contextEngine.remark(
            activity = activity,
            memory = recentMemory,
            sensors = SensorSnapshot(),
            energy = energy,
        )?.also {
            source = contextEngine.lastSource()
        } ?: ClochetteEngine.remark(
            activity = activity,
                sensors = SensorSnapshot(),
                energy = energy,
                project = project,
                memory = recentMemory,
                phraseLength = voiceConfig.phraseLength,
            ).also {
                source = PhraseSource.CLOCHETTE_ENGINE
            }
            return acceptLine(rawLine, source, autoSpeak)
        }

    fun naturalLocalTestLine(activity: ActivitySnapshot): String {
        val app = activity.foregroundDisplayName ?: activity.foregroundPackage ?: "cette appli"
        val minutes = activity.approximateDurationMs.toMinutesForUi()
        return when {
            activity.recentSwitchCount >= 4 ->
                "Tu changes souvent d’application. Tu cherches quelque chose ou tu évites quelque chose ?"
            minutes >= 20 ->
                "Je vois que tu es sur $app depuis un moment. Tu veux faire une pause ou continuer ?"
            RelationshipModeSettings.selected(context).id == "alive" ->
                "Je peux poser une question courte, puis ouvrir le micro quinze secondes."
            else ->
                "Je suis là. Tu veux que je reste discrète ou que je t’aide à reprendre le fil ?"
        }.withVisibleFrenchAccents()
    }

    fun generateLineWithAi(autoSpeak: Boolean = true, testOnly: Boolean = false) {
        val config = AiGatewaySettings.read(context)
        aiConfig = config
        if (!config.enabled || config.gatewayUrl.isBlank() || config.preferredProvider == AiGatewaySettings.PROVIDER_LOCAL) {
            val activity = UsageObserver(context).snapshot()
            AiGatewaySettings.record(context, "local", if (config.enabled) "fallback local" else "désactivée")
            val line = acceptLine(naturalLocalTestLine(activity), PhraseSource.LOCAL_FALLBACK, autoSpeak)
            if (testOnly) aiTestLine = line
            aiConfig = AiGatewaySettings.read(context)
            return
        }
        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())
        val activity = UsageObserver(context).snapshot()
        val recentMemory = memory.recent(24)
        val request = aiRequest(activity, recentMemory)
        thread(name = "clochette-ai-gateway") {
            val aiResult = AiGatewayClient(appContext).generateRemark(request)
            mainHandler.post {
                val line = if (aiResult != null) {
                    acceptLine(
                        rawLine = aiResult.line,
                        rawSource = aiResult.source,
                        autoSpeak = autoSpeak,
                        shouldSpeakHint = aiResult.shouldSpeak,
                    )
                } else {
                    AiGatewaySettings.record(appContext, "fallback", "fallback local")
                    generateLine(autoSpeak)
                }
                aiConfig = AiGatewaySettings.read(context)
                if (testOnly) aiTestLine = line
                runtimeStatus = ClochetteRuntimeStatus.read(context)
            }
        }
    }

    fun testLivingIntervention() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ClochetteProactiveService::class.java)
                .setAction(ClochetteProactiveService.ACTION_TEST_INTERVENTION),
        )
        Toast.makeText(context, "Test parole proactive lancé", Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({
            runtimeStatus = ClochetteRuntimeStatus.read(context)
            aiConfig = AiGatewaySettings.read(context)
            currentLine = ClochetteRemarkStore.latest(context)
            aiTestLine = currentLine
            refresh++
        }, 2_500L)
    }

    fun forceSafeSpokenPhrase() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ClochetteProactiveService::class.java)
                .setAction(ClochetteProactiveService.ACTION_FORCE_SAFE_SPOKEN),
        )
        Toast.makeText(context, "Phrase sûre parlée lancée", Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({
            runtimeStatus = ClochetteRuntimeStatus.read(context)
            aiConfig = AiGatewaySettings.read(context)
            currentLine = ClochetteRemarkStore.latest(context)
            aiTestLine = currentLine
            refresh++
        }, 2_500L)
    }

    fun testOctopusLocal() {
        val decision = OctopusCore.intervene(
            context = context,
            trigger = OctopusCore.TRIGGER_PROACTIVE_TEST,
            forceSpeak = false,
        )
        currentLine = decision.finalLine
        aiTestLine = decision.finalLine
        runtimeStatus = ClochetteRuntimeStatus.read(context)
        aiConfig = AiGatewaySettings.read(context)
        octopusDiagnostics = OctopusDiagnosticsStore.read(context)
        refresh++
    }

    fun testOctopusSafeVoice() {
        val decision = OctopusCore.intervene(
            context = context,
            trigger = OctopusCore.TRIGGER_SAFE_VOICE_TEST,
            forceSpeak = true,
        )
        currentLine = decision.finalLine
        aiTestLine = decision.finalLine
        runtimeStatus = ClochetteRuntimeStatus.read(context)
        octopusDiagnostics = OctopusDiagnosticsStore.read(context)
        refresh++
    }

    fun testRelayApi() {
        aiConfig = AiGatewaySettings.recordAndRead(context, "gateway", "Test en cours")
        thread(name = "clochette-gateway-health") {
            val health = OctopusCore.gatewayHealth(context)
            Handler(Looper.getMainLooper()).post {
                aiConfig = AiGatewaySettings.read(context)
                octopusDiagnostics = OctopusDiagnosticsStore.read(context)
                aiTestLine = if (health.ok) {
                    "Relais OK : ${health.service}"
                } else {
                    "Relais indisponible · fallback local actif"
                }
                refresh++
            }
        }
    }

    fun copyOctopusDiagnostic() {
        val copied = OctopusDiagnosticsStore.copyToClipboard(context)
        Toast.makeText(context, if (copied) "Diagnostic copié" else "Copie impossible", Toast.LENGTH_SHORT).show()
    }

    fun testOverlayAppearance() {
        context.startService(
            Intent(context, ClochetteOverlayService::class.java)
                .setAction(ClochetteOverlayService.ACTION_SHOW)
                .putExtra(ClochetteRemarkStore.EXTRA_LINE, currentLine ?: ClochetteRemarkStore.latest(context)),
        )
        ClochetteRuntimeStatus.recordAction(context, "overlay test")
        runtimeStatus = ClochetteRuntimeStatus.read(context)
    }

    fun testOverlayMic() {
        context.startService(
            Intent(context, ClochetteOverlayService::class.java)
                .setAction(ClochetteOverlayService.ACTION_OPEN_MIC),
        )
        ClochetteRuntimeStatus.recordAction(context, "micro overlay test")
        runtimeStatus = ClochetteRuntimeStatus.read(context)
    }

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F3EA)),
            color = Color(0xFFF7F3EA),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Clochette native", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Phase 1 : Clochette habite l'écran d'accueil. Widget local, remarque courte, voix Android.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                StatusPanel(context = context, refresh = refresh)

                ProactiveDiagnosticPanel(
                    runtimeStatus = runtimeStatus,
                    relationshipMode = RelationshipModeSettings.selected(context),
                    proactiveConfig = ProactiveSettings.read(context),
                    voiceConfig = voiceConfig,
                    latestSource = ClochetteRemarkStore.latestSource(context).id,
                )

                OctopusDiagnosticPanel(
                    diagnostics = octopusDiagnostics,
                    onCopy = { copyOctopusDiagnostic() },
                    onTestLocal = { testOctopusLocal() },
                    onTestSafeVoice = { testOctopusSafeVoice() },
                    onTestOverlay = { testOverlayAppearance() },
                    onTestMic = { testOverlayMic() },
                )

                ClochetteControlPanel(
                    context = context,
                    currentLine = currentLine,
                    latestSource = ClochetteRemarkStore.latestSource(context).id,
                    aiConfig = aiConfig,
                    runtimeStatus = runtimeStatus,
                    onTestLiving = { testLivingIntervention() },
                    onForceSafeSpoken = { forceSafeSpokenPhrase() },
                    onNeedLine = { generateLine() },
                    onRefresh = {
                        aiConfig = AiGatewaySettings.read(context)
                        runtimeStatus = ClochetteRuntimeStatus.read(context)
                        refresh++
                    },
                )

                AiGatewayPanel(
                    config = aiConfig,
                    latestSource = ClochetteRemarkStore.latestSource(context).id,
                    testLine = aiTestLine,
                    onConfig = { updateAiConfig(it) },
                    onTest = { generateLineWithAi(autoSpeak = false, testOnly = true) },
                    onRelayTest = { testRelayApi() },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, ClochettePresenceService::class.java),
                        )
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, ClochetteProactiveService::class.java)
                                .setAction(ClochetteProactiveService.ACTION_OBSERVE),
                        )
                        Toast.makeText(context, "Observation active", Toast.LENGTH_SHORT).show()
                        Handler(Looper.getMainLooper()).postDelayed({
                            runtimeStatus = ClochetteRuntimeStatus.read(context)
                            refresh++
                        }, 500L)
                        refresh++
                    }) {
                        Text("Observer")
                    }
                    OutlinedButton(onClick = {
                        context.startService(
                            Intent(context, ClochettePresenceService::class.java)
                                .setAction(ClochettePresenceService.ACTION_PAUSE),
                        )
                        context.startService(
                            Intent(context, ClochetteProactiveService::class.java)
                                .setAction(ClochetteProactiveService.ACTION_PAUSE),
                        )
                        context.stopService(Intent(context, ClochetteOverlayService::class.java))
                        ClochetteVoice.stop()
                        refresh++
                    }) {
                        Text("Pause")
                    }
                }

                VoiceSettingsPanel(
                    config = voiceConfig,
                    onConfig = { updateVoiceConfig(it) },
                    proactiveConfig = proactiveConfig,
                    onProactiveConfig = { updateProactiveConfig(it) },
                    relationshipModeId = relationshipModeId,
                    relationshipModes = relationshipModes,
                    onRelationshipMode = { updateRelationshipMode(it) },
                )

                SelectorPanel(
                    project = project,
                    energy = energy,
                    onProject = { project = it },
                    onEnergy = { energy = it },
                    onLine = { generateLineWithAi() },
                    onSpeak = {
                        val line = currentLine ?: generateLine(autoSpeak = false)
                        ClochetteVoice.speak(context, line)
                    },
                )

                ResponsePanel(
                    responseText = responseText,
                    onResponseText = { responseText = it },
                    highlighted = startSection == "response",
                )

                currentLine?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E6))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(it, style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { ClochetteVoice.speak(context, it) }) { Text("Parler") }
                                OutlinedButton(onClick = { ClochetteWidget.updateAll(context, it) }) { Text("Envoyer au widget") }
                            }
                        }
                    }
                }

                Text("Widget écran d'accueil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                PermissionCard(
                    title = "Widget écran d'accueil",
                    explanation = "Ajoute Clochette depuis les widgets Android. Le widget affiche une remarque et peut parler quand tu le touches.",
                    enabled = true,
                    onEnable = {
                        openWidgetPicker(context)
                        Toast.makeText(context, "Ajoute le widget depuis la liste Android.", Toast.LENGTH_LONG).show()
                    },
                    onDecline = { memory.decline("home_widget") },
                )

                Text("Permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Button(onClick = {
                    val result = PersonaLoader(context).synchronizeLocal()
                    val message = when (result.source) {
                        PersonaSource.CACHE -> "Persona chargé"
                        PersonaSource.ASSET,
                        PersonaSource.DEFAULT -> "Persona local utilisé"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }) {
                    Text("Synchroniser les personas")
                }
                ModulesClochettePanel(modules = personaModules)
                UsageAccessPanel(
                    hasPermission = UsageObserver(context).hasPermission(),
                    activity = usageSnapshot,
                    onOpenSettings = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                )
                NowPlayingPanel(
                    hasPermission = NowPlayingObserver.hasPermission(context),
                    snapshot = nowPlayingSnapshot,
                    onOpenSettings = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                )
                PermissionCard(
                    title = "Surimpression",
                    explanation = "Affiche Clochette par-dessus les apps, en petite présence visible et stoppable.",
                    enabled = Settings.canDrawOverlays(context),
                    onEnable = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    onDecline = { memory.decline("overlay") },
                )
                PermissionCard(
                    title = "Usage Access",
                    explanation = "Permet de savoir quelles apps sont utilisées, sans lire leur contenu : package, durée approximative, bascules.",
                    enabled = UsageObserver(context).hasPermission(),
                    onEnable = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    onDecline = { memory.decline("usage_access") },
                )
                PermissionCard(
                    title = "Capteurs",
                    explanation = "Utilise mouvement, orientation et lumière si disponible pour produire des signaux sobres : marche possible, téléphone immobile, basse lumière, écran actif.",
                    enabled = true,
                    onEnable = { refresh++ },
                    onDecline = { memory.decline("sensors") },
                )
                PermissionCard(
                    title = "Assistive Clochette",
                    explanation = "Mode avancé, désactivé par défaut. Isolé derrière AccessibilityService. Il ne récupère pas le contenu des fenêtres dans ce prototype.",
                    enabled = isAccessibilityServiceEnabled(context),
                    onEnable = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onDecline = { memory.decline("assistive_clochette") },
                )

                Text("Mémoire locale", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                MemoryPreview(memory = memory, refresh = refresh)
            }
        }
    }
}

@Composable
private fun StatusPanel(context: Context, refresh: Int) {
    val state = context.getSharedPreferences("clochette_state", Context.MODE_PRIVATE)
        .getString("state", ClochetteState.ASLEEP.name)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F1EC))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("État : ${stateLabel(state)}", fontWeight = FontWeight.SemiBold)
            Text("Carnet d'indices local. Aucun appel réseau, aucune clé API en dur.")
            Text("Rafraîchissement $refresh", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
        }
    }
}

@Composable
private fun VisibleClochettePanel(
    context: Context,
    currentLine: String?,
    onNeedLine: () -> String,
    onRefresh: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Clochette visible", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Affiche une petite Clochette en bas de l'écran, avec bulle de texte et boutons rapides.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    Toast.makeText(context, "Afficher Clochette appuyé", Toast.LENGTH_SHORT).show()
                    if (Settings.canDrawOverlays(context)) {
                        runCatching {
                            context.startService(
                                Intent(context, ClochetteOverlayService::class.java)
                                    .setAction(ClochetteOverlayService.ACTION_SHOW)
                                    .putExtra(ClochetteRemarkStore.EXTRA_LINE, currentLine ?: ClochetteRemarkStore.latest(context)),
                            )
                        }.onSuccess {
                            Toast.makeText(context, "Overlay démarré", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Erreur overlay: ${it.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Autorise l'affichage par-dessus les apps.", Toast.LENGTH_LONG).show()
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                    onRefresh()
                }) {
                    Text("Afficher Clochette")
                }
                OutlinedButton(onClick = {
                    context.stopService(Intent(context, ClochetteOverlayService::class.java))
                    onRefresh()
                }) {
                    Text("Masquer Clochette")
                }
            }
            Text(
                if (Settings.canDrawOverlays(context)) "Surimpression : autorisée" else "Surimpression : autorisation requise",
                color = if (Settings.canDrawOverlays(context)) Color(0xFF2E7D5B) else Color(0xFF8A4B25),
            )
        }
    }
}

@Composable
private fun ProactiveDiagnosticPanel(
    runtimeStatus: ClochetteRuntimeSnapshot,
    relationshipMode: RelationshipMode,
    proactiveConfig: ProactiveConfig,
    voiceConfig: ClochetteVoiceConfig,
    latestSource: String,
) {
    val now = System.currentTimeMillis()
    val nextDelay = (runtimeStatus.nextAttemptAt - now).coerceAtLeast(0L)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F1EC))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Diagnostic Clochette vivante", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Service proactif : ${if (runtimeStatus.proactiveActive) "actif" else "inactif"}")
            Text("Mode relation : ${relationshipMode.id}")
            Text("Fréquence : ${proactiveConfig.frequency.name.lowercase()}")
            Text("Voix activée : ${voiceConfig.enabled.yesNo()}")
            Text("Interventions vocales : ${proactiveConfig.voiceInterventions.yesNo()}")
            Text("Questions spontanées : ${proactiveConfig.spontaneousQuestions.yesNo()}")
            Text("Parler après chaque remarque : ${voiceConfig.autoSpeak.yesNo()}")
            Text("Dernier tick proactif : ${runtimeStatus.lastTickAt.asClockOrNever()}")
            Text("Dernière décision Guardian : ${runtimeStatus.lastGuardianDecision}")
            Text("Dernier shouldSpeak : ${runtimeStatus.lastShouldSpeak}")
            Text("Dernière source phrase : $latestSource")
            Text("Dernière action voix : ${runtimeStatus.lastVoiceAction}")
            Text("Prochaine tentative dans : ${nextDelay.toDelayLabel()}")
        }
    }
}

@Composable
private fun OctopusDiagnosticPanel(
    diagnostics: OctopusDiagnostics,
    onCopy: () -> Unit,
    onTestLocal: () -> Unit,
    onTestSafeVoice: () -> Unit,
    onTestOverlay: () -> Unit,
    onTestMic: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF0FF))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Octopus / diagnostic", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Dernier trigger : ${diagnostics.lastTrigger}")
            Text("Source phrase : ${diagnostics.lastPhraseSource}")
            Text("Provider : ${diagnostics.lastProviderUsed}")
            Text("Guardian : ${diagnostics.lastGuardianReason}")
            Text("Voix : ${diagnostics.lastVoiceStatus} · shouldSpeak=${diagnostics.lastShouldSpeak}")
            Text("Micro : ${diagnostics.lastMicStatus}")
            Text("Overlay : ${diagnostics.lastOverlayState}")
            Text("Gateway : ${diagnostics.lastGatewayStatus}")
            Text("Dernière transcription : ${diagnostics.lastTranscription.ifBlank { "-" }}")
            if (diagnostics.lastError.isNotBlank()) {
                Text("Dernière erreur : ${diagnostics.lastError}", color = Color(0xFF8A4B25))
            }
            if (diagnostics.lastFinalLine.isNotBlank()) {
                Text("Phrase finale : ${diagnostics.lastFinalLine}")
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onTestLocal) {
                Text("Tester Octopus local")
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onTestSafeVoice) {
                Text("Forcer phrase sûre parlée")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onTestOverlay) {
                    Text("Tester apparition overlay")
                }
                OutlinedButton(onClick = onTestMic) {
                    Text("Tester micro transcription")
                }
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onCopy) {
                Text("Copier diagnostic")
            }
        }
    }
}

@Composable
private fun ClochetteControlPanel(
    context: Context,
    currentLine: String?,
    latestSource: String,
    aiConfig: AiGatewayConfig,
    runtimeStatus: ClochetteRuntimeSnapshot,
    onTestLiving: () -> Unit,
    onForceSafeSpoken: () -> Unit,
    onNeedLine: () -> String,
    onRefresh: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E6))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Contrôle vivant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Dernière source phrase : $latestSource")
            Text("Dernier provider IA : ${aiConfig.lastProviderUsed ?: "aucun"}")
            Text("Dernier statut IA : ${aiConfig.lastStatus ?: if (aiConfig.enabled) "non testé" else "désactivée"}")
            Text("Dernière action : ${runtimeStatus.lastAction}")
            if (aiConfig.enabled && aiConfig.gatewayUrl.isBlank()) {
                Text("IA distante non configurée · fallback local actif", color = Color(0xFF8A4B25))
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onTestLiving,
            ) {
                Text("Tester parole proactive maintenant")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onForceSafeSpoken,
            ) {
                Text("Forcer phrase sûre parlée")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    onNeedLine()
                    onRefresh()
                }) {
                    Text("Nouvelle phrase")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    Toast.makeText(context, "Afficher Clochette appuyé", Toast.LENGTH_SHORT).show()
                    if (Settings.canDrawOverlays(context)) {
                        runCatching {
                            context.startService(
                                Intent(context, ClochetteOverlayService::class.java)
                                    .setAction(ClochetteOverlayService.ACTION_SHOW)
                                    .putExtra(ClochetteRemarkStore.EXTRA_LINE, currentLine ?: ClochetteRemarkStore.latest(context)),
                            )
                        }.onSuccess {
                            Toast.makeText(context, "Overlay démarré", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Erreur overlay: ${it.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Autorise l'affichage par-dessus les apps.", Toast.LENGTH_LONG).show()
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                    onRefresh()
                }) {
                    Text("Afficher Clochette")
                }
                OutlinedButton(onClick = {
                    ClochetteRuntimeStatus.recordAction(context, "overlay fermé")
                    context.stopService(Intent(context, ClochetteOverlayService::class.java))
                    onRefresh()
                }) {
                    Text("Masquer Clochette")
                }
            }
            Text(
                if (Settings.canDrawOverlays(context)) "Surimpression : autorisée" else "Surimpression : autorisation requise",
                color = if (Settings.canDrawOverlays(context)) Color(0xFF2E7D5B) else Color(0xFF8A4B25),
            )
        }
    }
}

@Composable
private fun ResponsePanel(
    responseText: String,
    onResponseText: (String) -> Unit,
    highlighted: Boolean,
) {
    Card(colors = CardDefaults.cardColors(containerColor = if (highlighted) Color(0xFFFFF9E6) else Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Réponse à Clochette", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = responseText,
                onValueChange = onResponseText,
                minLines = 3,
                label = { Text("Note rapide") },
            )
        }
    }
}

@Composable
private fun VoiceSettingsPanel(
    config: ClochetteVoiceConfig,
    onConfig: (ClochetteVoiceConfig) -> Unit,
    proactiveConfig: ProactiveConfig,
    onProactiveConfig: (ProactiveConfig) -> Unit,
    relationshipModeId: String,
    relationshipModes: List<RelationshipMode>,
    onRelationshipMode: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Voix de Clochette", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Voix activée")
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { onConfig(config.copy(enabled = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Parler après chaque remarque")
                Switch(
                    checked = config.autoSpeak,
                    onCheckedChange = { onConfig(config.copy(autoSpeak = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Interventions vocales")
                Switch(
                    checked = proactiveConfig.voiceInterventions,
                    onCheckedChange = { onProactiveConfig(proactiveConfig.copy(voiceInterventions = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Questions spontanées")
                Switch(
                    checked = proactiveConfig.spontaneousQuestions,
                    onCheckedChange = { onProactiveConfig(proactiveConfig.copy(spontaneousQuestions = it)) },
                )
            }
            VoiceChoice(
                title = "Mode de présence",
                value = relationshipModes.firstOrNull { it.id == relationshipModeId }?.name ?: "Discrète",
                options = relationshipModes.map { it.name },
                onValue = { selected ->
                    relationshipModes.firstOrNull { it.name == selected }?.let { onRelationshipMode(it.id) }
                },
            )
            VoiceChoice(
                title = "Fréquence",
                value = proactiveConfig.frequency.name.lowercase(),
                options = ProactiveFrequency.values().map { it.name.lowercase() },
                onValue = { selected ->
                    val frequency = ProactiveFrequency.valueOf(selected.uppercase())
                    onProactiveConfig(proactiveConfig.copy(frequency = frequency))
                },
            )
            Text("Vitesse de parole : ${config.speechRate.formatOneDecimal()}")
            Slider(
                value = config.speechRate,
                onValueChange = { onConfig(config.copy(speechRate = it.coerceIn(0.7f, 1.4f))) },
                valueRange = 0.7f..1.4f,
            )
            Text("Hauteur / pitch : ${config.pitch.formatOneDecimal()}")
            Slider(
                value = config.pitch,
                onValueChange = { onConfig(config.copy(pitch = it.coerceIn(0.8f, 1.7f))) },
                valueRange = 0.8f..1.7f,
            )
            VoiceChoice(
                title = "Mode vocal",
                value = config.mode,
                options = listOf(
                    ClochetteVoiceSettings.MODE_DOUCE,
                    ClochetteVoiceSettings.MODE_ESPIEGLE,
                    ClochetteVoiceSettings.MODE_COACH,
                    ClochetteVoiceSettings.MODE_FEUCHIENNE,
                ),
                onValue = { onConfig(config.copy(mode = it)) },
            )
            VoiceChoice(
                title = "Effet sonore avant parole",
                value = config.soundEffect,
                options = listOf(
                    ClochetteVoiceSettings.EFFECT_NONE,
                    ClochetteVoiceSettings.EFFECT_BELL,
                    ClochetteVoiceSettings.EFFECT_POF,
                ),
                onValue = { onConfig(config.copy(soundEffect = it)) },
            )
            VoiceChoice(
                title = "Longueur des phrases",
                value = config.phraseLength,
                options = listOf(
                    ClochetteVoiceSettings.LENGTH_SHORT,
                    ClochetteVoiceSettings.LENGTH_NORMAL,
                    ClochetteVoiceSettings.LENGTH_CHATTY,
                ),
                onValue = { onConfig(config.copy(phraseLength = it)) },
            )
        }
    }
}

@Composable
private fun AiGatewayPanel(
    config: AiGatewayConfig,
    latestSource: String,
    testLine: String?,
    onConfig: (AiGatewayConfig) -> Unit,
    onTest: () -> Unit,
    onRelayTest: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Relais API Clochette", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Les clés Mistral/Gemini ne sont pas stockées dans l’application. Elles doivent rester côté serveur, dans la gateway.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("IA distante activée")
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { onConfig(config.copy(enabled = it)) },
                )
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = config.gatewayUrl,
                onValueChange = { onConfig(config.copy(gatewayUrl = it)) },
                singleLine = true,
                label = { Text("Gateway URL") },
            )
            VoiceChoice(
                title = "Provider préféré",
                value = config.preferredProvider,
                options = listOf(
                    AiGatewaySettings.PROVIDER_AUTO,
                    AiGatewaySettings.PROVIDER_MISTRAL,
                    AiGatewaySettings.PROVIDER_GEMINI,
                    AiGatewaySettings.PROVIDER_LOCAL,
                ),
                onValue = { onConfig(config.copy(preferredProvider = it)) },
            )
            VoiceChoice(
                title = "Style",
                value = config.styleLevel,
                options = listOf(
                    AiGatewaySettings.STYLE_NATUREL,
                    AiGatewaySettings.STYLE_ESPIEGLE,
                    AiGatewaySettings.STYLE_FEUCH,
                ),
                onValue = { onConfig(config.copy(styleLevel = it)) },
            )
            Text("Dernier provider : ${config.lastProviderUsed ?: "aucun"}")
            Text("Dernier statut : ${config.lastStatus ?: "non testé"}")
            Text("Dernière latence : ${config.lastLatencyMs?.let { "$it ms" } ?: "-"}")
            Text("Dernière source : $latestSource")
            Text("Dernière réponse brute : ${config.lastRawResponse?.take(120) ?: "-"}")
            config.lastError?.takeIf { it.isNotBlank() }?.let {
                Text("Dernière erreur : $it", color = Color(0xFF8A4B25))
            }
            if (config.gatewayUrl.isBlank()) {
                Text("IA distante non configurée · fallback local actif", color = Color(0xFF8A4B25))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRelayTest) {
                    Text("Tester le relais")
                }
                OutlinedButton(onClick = onTest) {
                    Text("Tester génération")
                }
            }
            testLine?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun VoiceChoice(
    title: String,
    value: String,
    options: List<String>,
    onValue: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { open = true }) {
            Text(value)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach {
                DropdownMenuItem(
                    text = { Text(it) },
                    onClick = {
                        onValue(it)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ModulesClochettePanel(modules: List<PersonaModuleStatus>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Modules Clochette", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            modules.forEach { module ->
                val status = when {
                    module.detected && module.validJson -> "détecté"
                    module.detected -> "invalide"
                    else -> "manquant"
                }
                Text("${module.fileName} : $status")
            }
        }
    }
}

@Composable
private fun UsageAccessPanel(
    hasPermission: Boolean,
    activity: ActivitySnapshot,
    onOpenSettings: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Usage Access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (hasPermission) "État : autorisé" else "État : autorisation requise")
            Text("Application : ${activity.foregroundDisplayName ?: activity.foregroundPackage ?: "non détectée"}")
            Text("Temps approximatif : ${activity.approximateDurationMs.toApproximateLabel()}")
            Text("Changements d'apps : ${activity.recentSwitchCount}")
            Button(onClick = onOpenSettings) {
                Text("Ouvrir les paramètres Android")
            }
        }
    }
}

@Composable
private fun NowPlayingPanel(
    hasPermission: Boolean,
    snapshot: NowPlayingSnapshot,
    onOpenSettings: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Now Playing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (hasPermission) "État : autorisé" else "État : autorisation requise")
            Text("App : ${snapshot.appName ?: "non détectée"}")
            Text("Titre : ${snapshot.title ?: "-"}")
            Text("Artiste / chaîne : ${snapshot.artist ?: "-"}")
            Text("Lecture : ${snapshot.playing?.let { if (it) "active" else "pause" } ?: "inconnue"}")
            Button(onClick = onOpenSettings) {
                Text("Ouvrir les paramètres Notification Listener")
            }
        }
    }
}

@Composable
private fun SelectorPanel(
    project: String,
    energy: String,
    onProject: (String) -> Unit,
    onEnergy: (String) -> Unit,
    onLine: () -> Unit,
    onSpeak: () -> Unit,
) {
    var projectOpen by remember { mutableStateOf(false) }
    var energyOpen by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Contexte déclaré", fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { projectOpen = true }) {
                        Text(project)
                    }
                    DropdownMenu(expanded = projectOpen, onDismissRequest = { projectOpen = false }) {
                        ProjectKnowledge.projects.forEach {
                            DropdownMenuItem(
                                text = { Text(it.name) },
                                onClick = {
                                    onProject(it.name)
                                    projectOpen = false
                                },
                            )
                        }
                    }
                }
                Column {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { energyOpen = true }) {
                        Text("Énergie : $energy")
                    }
                    DropdownMenu(expanded = energyOpen, onDismissRequest = { energyOpen = false }) {
                        listOf("basse", "moyenne", "haute").forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = {
                                    onEnergy(it)
                                    energyOpen = false
                                },
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onLine) { Text("Remarque") }
                OutlinedButton(onClick = onSpeak) { Text("Voix") }
            }
        }
    }
}

private fun Float.formatOneDecimal(): String = ((this * 10).toInt() / 10f).toString()

private fun Long.toMinutesForUi(): Int = (this / 60_000L).toInt().coerceAtLeast(0)

private fun Boolean.yesNo(): String = if (this) "oui" else "non"

private fun Long.asClockOrNever(): String {
    if (this <= 0L) return "jamais"
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = this@asClockOrNever }
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val minute = calendar.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
    val second = calendar.get(java.util.Calendar.SECOND).toString().padStart(2, '0')
    return "$hour:$minute:$second"
}

private fun Long.toDelayLabel(): String {
    val seconds = (this / 1000L).coerceAtLeast(0L)
    return when {
        seconds <= 0L -> "maintenant"
        seconds < 60L -> "$seconds s"
        else -> "${seconds / 60L} min ${seconds % 60L} s"
    }
}

private fun Long.toApproximateLabel(): String {
    val minutes = (this / 60_000L).coerceAtLeast(0)
    return when {
        minutes <= 0L -> "moins d'une minute"
        minutes == 1L -> "1 minute"
        minutes < 60L -> "$minutes minutes"
        else -> {
            val hours = minutes / 60
            val rest = minutes % 60
            if (rest == 0L) "$hours h" else "$hours h $rest min"
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    explanation: String,
    enabled: Boolean,
    onEnable: () -> Unit,
    onDecline: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(explanation)
            Text(if (enabled) "Etat actuel : actif" else "Etat actuel : inactif", color = if (enabled) Color(0xFF2E7D5B) else Color(0xFF8A4B25))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onEnable) { Text("Activer") }
                OutlinedButton(onClick = onDecline) { Text("Refuser") }
            }
        }
    }
}

@Composable
private fun MemoryPreview(memory: ClochetteMemory, refresh: Int) {
    val entries = memory.recent(5)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Carnet d'indices", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (entries.isEmpty()) {
            Text("Aucun indice local pour l'instant.")
        } else {
            entries.forEach {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F0E8))) {
                    Column(Modifier.padding(10.dp)) {
                        Text(it.observedSignal, fontWeight = FontWeight.SemiBold)
                        it.clochetteLine?.let { line -> Text(line) }
                        Text("result=${it.result ?: "non note"} refresh=$refresh", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun ClochetteMemory.decline(permission: String) {
    add(
        ClochetteMemoryEntry(
            context = "permissions",
            observedSignal = "declined_$permission",
            project = null,
            energy = null,
            clochetteLine = null,
            userReaction = "refus",
            result = "permission_refused",
        ),
    )
}

private fun stateLabel(raw: String?): String = when (raw) {
    ClochetteState.OBSERVING.name -> "observe"
    ClochetteState.PAUSED.name -> "pause"
    else -> "en veille"
}

private fun openWidgetPicker(context: Context) {
    val host = AppWidgetHost(context.applicationContext, 1248)
    val appWidgetId = host.allocateAppWidgetId()
    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    runCatching { context.startActivity(intent) }
        .onFailure { error ->
            host.deleteAppWidgetId(appWidgetId)
            if (error is ActivityNotFoundException) {
                Toast.makeText(context, "Selecteur de widgets indisponible.", Toast.LENGTH_SHORT).show()
            }
        }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = "${context.packageName}/${ClochetteAccessibilityService::class.java.name}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    )
    return !enabledServices.isNullOrBlank() &&
        enabledServices.split(':').any { TextUtils.equals(it, expected) }
}

