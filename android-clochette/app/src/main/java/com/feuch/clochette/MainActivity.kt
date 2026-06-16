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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
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
    var appearanceConfig by remember { mutableStateOf(ClochetteAppearanceSettings.read(context)) }
    var personalityConfig by remember { mutableStateOf(ClochettePersonalitySettings.read(context)) }
    var characterConfig by remember { mutableStateOf(CharacterSettings.read(context)) }
    val personaModules = remember(refresh) { PersonaModuleLoader(context).loadStatuses() }
    val relationshipModes = remember(refresh) { RelationshipModeSettings.modes(context) }
    val usageSnapshot = remember(refresh) { UsageObserver(context).snapshot() }
    val nowPlayingSnapshot = remember(refresh) { NowPlayingObserver.snapshot(context) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }
    val microphoneLauncher = rememberLauncherForActivityResult(
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

    fun updateAppearanceConfig(config: ClosedAppearanceConfig) {
        appearanceConfig = config
        ClochetteAppearanceSettings.save(context, config)
    }

    fun updatePersonalityConfig(config: ClochettePersonalityConfig) {
        val safe = config.clamp()
        personalityConfig = safe
        ClochettePersonalitySettings.save(context, safe)
    }

    fun updateCharacterConfig(config: CharacterCastingConfig) {
        characterConfig = config
        CharacterSettings.save(context, config)
    }

    fun recordAction(action: String) {
        ClochetteRuntimeStatus.recordAction(context, action)
        runtimeStatus = ClochetteRuntimeStatus.read(context)
        aiConfig = AiGatewaySettings.read(context)
        octopusDiagnostics = OctopusDiagnosticsStore.read(context)
        refresh++
    }

    fun generateLine(autoSpeak: Boolean = true): String {
        val decision = OctopusCore.intervene(
            context = context,
            trigger = OctopusCore.TRIGGER_MANUAL_TAP,
            forceSpeak = autoSpeak,
        )
        currentLine = decision.finalLine
        runtimeStatus = ClochetteRuntimeStatus.read(context)
        octopusDiagnostics = OctopusDiagnosticsStore.read(context)
        refresh++
        return decision.finalLine
    }

    fun generateLineWithAi(autoSpeak: Boolean = true, testOnly: Boolean = false) {
        val trigger = if (testOnly) OctopusCore.TRIGGER_GATEWAY_TEST else OctopusCore.TRIGGER_MANUAL_TAP
        val decision = OctopusCore.intervene(context, trigger, forceSpeak = autoSpeak)
        currentLine = decision.finalLine
        if (testOnly) aiTestLine = decision.finalLine
        aiConfig = AiGatewaySettings.read(context)
        runtimeStatus = ClochetteRuntimeStatus.read(context)
        octopusDiagnostics = OctopusDiagnosticsStore.read(context)
        refresh++
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
                Text("Clochette", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Commence par les autorisations, puis active l'observation.", style = MaterialTheme.typography.bodyMedium)

                StatusPanel(context = context, refresh = refresh)

                SettingsSection("Premier lancement — autorisations")
                PermissionStepCard(
                    step = 1,
                    title = "Notifications",
                    explanation = "Nécessaire pour garder Clochette active avec un service visible sur Android récent.",
                    status = if (Build.VERSION.SDK_INT < 33) {
                        "non nécessaire sur cette version Android"
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        "autorisé"
                    } else {
                        "requis"
                    },
                    primaryButtonLabel = "Autoriser les notifications",
                    primaryAction = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            PermissionStateManager.markAsked(context, ClochettePermissionKey.NOTIFICATIONS)
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            Toast.makeText(context, "Notifications déjà disponibles sur cette version.", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                PermissionStepCard(
                    step = 2,
                    title = "Superposition",
                    explanation = "Permet d'afficher Clochette par-dessus les autres apps.",
                    status = if (Settings.canDrawOverlays(context)) "autorisée" else "requise",
                    primaryButtonLabel = "Autoriser la superposition",
                    primaryAction = {
                        PermissionStateManager.markAsked(context, ClochettePermissionKey.OVERLAY)
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                )
                PermissionStepCard(
                    step = 3,
                    title = "Accès à l'utilisation",
                    explanation = "Permet de détecter l'application au premier plan sans lire son contenu.",
                    status = if (UsageObserver(context).hasPermission()) "autorisé" else "recommandé",
                    primaryButtonLabel = "Ouvrir Accès à l'utilisation",
                    primaryAction = {
                        PermissionStateManager.markAsked(context, ClochettePermissionKey.USAGE_ACCESS)
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                )
                PermissionStepCard(
                    step = 4,
                    title = "Autorisations de l'application",
                    explanation = "Vérifie les autorisations Android de Clochette : micro, notifications et futures options.",
                    status = "à vérifier si besoin",
                    primaryButtonLabel = "Ouvrir les autorisations de l'application",
                    primaryAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                        )
                    },
                )
                PermissionStepCard(
                    step = 5,
                    title = "Micro",
                    explanation = "Sert uniquement à répondre vocalement à Clochette.",
                    status = if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "autorisé" else "requis pour répondre",
                    primaryButtonLabel = "Autoriser le micro",
                    primaryAction = {
                        PermissionStateManager.markAsked(context, ClochettePermissionKey.MICROPHONE)
                        microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                )
                PermissionStepCard(
                    step = 6,
                    title = "Accès notifications / média",
                    explanation = "Recommandé pour Now Playing : app média, titre et artiste exposés par Android.",
                    status = if (NowPlayingObserver.hasPermission(context)) "autorisé" else "recommandé",
                    primaryButtonLabel = "Ouvrir accès notifications",
                    primaryAction = {
                        PermissionStateManager.markAsked(context, ClochettePermissionKey.NOTIFICATION_LISTENER)
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                )
                PermissionStepCard(
                    step = 7,
                    title = "Accessibilité / Assistive Clochette",
                    explanation = "Avancé et optionnel. Désactivé par défaut.",
                    status = if (isAccessibilityServiceEnabled(context)) "autorisé" else "optionnel",
                    primaryButtonLabel = "Ouvrir accessibilité",
                    primaryAction = {
                        PermissionStateManager.markAsked(context, ClochettePermissionKey.ACCESSIBILITY)
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
                PermissionStepCard(
                    step = 8,
                    title = "SMS / téléphone",
                    explanation = "SMS : non utilisé dans cette version.",
                    status = "non utilisé",
                    primaryButtonLabel = "Voir autorisations de l'application",
                    primaryAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                        )
                    },
                )
                PermissionStepCard(
                    step = 9,
                    title = "Widget écran d'accueil",
                    explanation = "Optionnel. Ajoute Clochette depuis les widgets Android.",
                    status = "optionnel",
                    primaryButtonLabel = "Ajouter le widget",
                    primaryAction = {
                        openWidgetPicker(context)
                        Toast.makeText(context, "Ajoute le widget depuis la liste Android.", Toast.LENGTH_LONG).show()
                    },
                )

                VisibleSettingsPanel(
                    context = context,
                    currentLine = currentLine,
                    onNeedLine = { generateLine() },
                    onMic = { testOverlayMic() },
                    onRefresh = {
                        aiConfig = AiGatewaySettings.read(context)
                        runtimeStatus = ClochetteRuntimeStatus.read(context)
                        refresh++
                    },
                )

                BehaviorSettingsPanel(
                    proactiveConfig = proactiveConfig,
                    onProactiveConfig = { updateProactiveConfig(it) },
                    relationshipModeId = relationshipModeId,
                    relationshipModes = relationshipModes,
                    onRelationshipMode = { updateRelationshipMode(it) },
                    onObserve = {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            if (!PermissionStateManager.wasAsked(context, ClochettePermissionKey.NOTIFICATIONS)) {
                                PermissionStateManager.markAsked(context, ClochettePermissionKey.NOTIFICATIONS)
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                Toast.makeText(context, "Notifications non autorisées : ouvre l'étape Notifications si besoin.", Toast.LENGTH_LONG).show()
                            }
                        }
                        ContextCompat.startForegroundService(context, Intent(context, ClochettePresenceService::class.java))
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
                    },
                    onPause = {
                        context.startService(Intent(context, ClochettePresenceService::class.java).setAction(ClochettePresenceService.ACTION_PAUSE))
                        context.startService(Intent(context, ClochetteProactiveService::class.java).setAction(ClochetteProactiveService.ACTION_PAUSE))
                        context.stopService(Intent(context, ClochetteOverlayService::class.java))
                        ClochetteVoice.stop()
                        refresh++
                    },
                )

                ClosedAppearancePanel(
                    config = appearanceConfig,
                    onConfig = { updateAppearanceConfig(it) },
                )

                CharacterSettingsPanel(
                    config = personalityConfig,
                    onConfig = { updatePersonalityConfig(it) },
                )

                BlacklaceCharactersPanel(
                    config = characterConfig,
                    onConfig = { updateCharacterConfig(it) },
                )

                VoiceSettingsPanel(
                    config = voiceConfig,
                    onConfig = { updateVoiceConfig(it) },
                )

                SelectorPanel(
                    project = project,
                    energy = energy,
                    onProject = { project = it },
                    onEnergy = { energy = it },
                    onLine = { generateLineWithAi() },
                    onSpeak = { generateLine(autoSpeak = true) },
                )

                currentLine?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E6))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(it, style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { generateLine(autoSpeak = true) }) { Text("Parler via Octopus") }
                                OutlinedButton(onClick = { generateLine(autoSpeak = false) }) { Text("Rafraîchir via Octopus") }
                            }
                        }
                    }
                }

                AiGatewayPanel(
                    config = aiConfig,
                    latestSource = ClochetteRemarkStore.latestSource(context).id,
                    testLine = aiTestLine,
                    onConfig = { updateAiConfig(it) },
                    onTest = { generateLineWithAi(autoSpeak = false, testOnly = true) },
                    onRelayTest = { testRelayApi() },
                )

                SettingsSection("Diagnostics")
                ProactiveDiagnosticPanel(
                    runtimeStatus = runtimeStatus,
                    relationshipMode = RelationshipModeSettings.selected(context),
                    proactiveConfig = ProactiveSettings.read(context),
                    voiceConfig = voiceConfig,
                    latestSource = ClochetteRemarkStore.latestSource(context).id,
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
                OctopusDiagnosticPanel(
                    diagnostics = octopusDiagnostics,
                    onCopy = { copyOctopusDiagnostic() },
                    onTestLocal = { testOctopusLocal() },
                    onTestSafeVoice = { testOctopusSafeVoice() },
                    onTestOverlay = { testOverlayAppearance() },
                    onTestMic = { testOverlayMic() },
                )

                ResponsePanel(
                    responseText = responseText,
                    onResponseText = { responseText = it },
                    highlighted = startSection == "response",
                )

                SettingsSection("Mémoire locale / modules")
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
private fun VisibleSettingsPanel(
    context: Context,
    currentLine: String?,
    onNeedLine: () -> String,
    onMic: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Clochette visible", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Affiche, masque ou réveille Clochette sans ouvrir les diagnostics.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    if (Settings.canDrawOverlays(context)) {
                        context.startService(
                            Intent(context, ClochetteOverlayService::class.java)
                                .setAction(ClochetteOverlayService.ACTION_SHOW)
                                .putExtra(ClochetteRemarkStore.EXTRA_LINE, currentLine ?: ClochetteRemarkStore.latest(context)),
                        )
                        Toast.makeText(context, "Overlay démarré", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Autorise la superposition.", Toast.LENGTH_LONG).show()
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
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
                    Text("Masquer")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    onNeedLine()
                    onRefresh()
                }) {
                    Text("Nouvelle phrase")
                }
                OutlinedButton(onClick = onMic) {
                    Text("Micro overlay")
                }
            }
            Text(
                if (Settings.canDrawOverlays(context)) "Superposition : autorisée" else "Superposition : requise",
                color = if (Settings.canDrawOverlays(context)) Color(0xFF2E7D5B) else Color(0xFF8A4B25),
            )
        }
    }
}

@Composable
private fun BehaviorSettingsPanel(
    proactiveConfig: ProactiveConfig,
    onProactiveConfig: (ProactiveConfig) -> Unit,
    relationshipModeId: String,
    relationshipModes: List<RelationshipMode>,
    onRelationshipMode: (String) -> Unit,
    onObserve: () -> Unit,
    onPause: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Comportement", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            SettingsDropdown(
                title = "Mode de présence",
                value = relationshipModes.firstOrNull { it.id == relationshipModeId }?.name ?: "Discrète",
                options = relationshipModes.map { it.name },
                onValue = { selected ->
                    relationshipModes.firstOrNull { it.name == selected }?.let { onRelationshipMode(it.id) }
                },
            )
            SettingsDropdown(
                title = "Fréquence",
                value = proactiveConfig.frequency.name.lowercase(),
                options = ProactiveFrequency.values().map { it.name.lowercase() },
                onValue = { selected ->
                    onProactiveConfig(proactiveConfig.copy(frequency = ProactiveFrequency.valueOf(selected.uppercase())))
                },
            )
            SettingsSwitchRow(
                title = "Questions spontanées",
                subtitle = "Clochette peut poser une question courte quand le mode le permet.",
                checked = proactiveConfig.spontaneousQuestions,
                onCheckedChange = { onProactiveConfig(proactiveConfig.copy(spontaneousQuestions = it)) },
            )
            SettingsSwitchRow(
                title = "Interventions vocales",
                subtitle = "Autorise la voix proactive sans dépendre du bouton Parler.",
                checked = proactiveConfig.voiceInterventions,
                onCheckedChange = { onProactiveConfig(proactiveConfig.copy(voiceInterventions = it)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onObserve) { Text("Observer") }
                OutlinedButton(onClick = onPause) { Text("Pause") }
            }
        }
    }
}

@Composable
private fun ClosedAppearancePanel(
    config: ClosedAppearanceConfig,
    onConfig: (ClosedAppearanceConfig) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Apparence fermée", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Choisis comment Clochette se range quand elle n'a rien à dire.")
            SettingsDropdown(
                title = "Apparence fermée",
                value = config.mode.label(),
                options = ClosedAppearanceMode.values().map { it.label() },
                onValue = { selected ->
                    ClosedAppearanceMode.values().firstOrNull { it.label() == selected }?.let {
                        onConfig(config.copy(mode = it))
                    }
                },
            )
            SettingsDropdown(
                title = "Côté préféré",
                value = config.side.label(),
                options = ClosedAppearanceSide.values().map { it.label() },
                onValue = { selected ->
                    ClosedAppearanceSide.values().firstOrNull { it.label() == selected }?.let {
                        onConfig(config.copy(side = it))
                    }
                },
            )
            Text(
                "Le mode point garde une zone tactile confortable, même si le point visible reste discret.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.DarkGray,
            )
        }
    }
}

@Composable
private fun CharacterSettingsPanel(
    config: ClochettePersonalityConfig,
    onConfig: (ClochettePersonalityConfig) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Caractère de Clochette", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Ces réglages influencent les interventions locales, sans toucher à Octopus.")
            PersonalitySlider(
                title = "Bavardage",
                low = "très discrète",
                mid = "équilibrée",
                high = "très bavarde",
                value = config.talkativeness,
                onValue = { onConfig(config.copy(talkativeness = it)) },
            )
            PersonalitySlider(
                title = "Initiative",
                low = "attend qu'on l'appelle",
                mid = "intervient parfois",
                high = "vient souvent",
                value = config.initiative,
                onValue = { onConfig(config.copy(initiative = it)) },
            )
            PersonalitySlider(
                title = "Taquinerie",
                low = "douce et sobre",
                mid = "piquante gentille",
                high = "sarcastique sans méchanceté",
                value = config.teasing,
                onValue = { onConfig(config.copy(teasing = it)) },
            )
            PersonalitySlider(
                title = "Douceur",
                low = "directe",
                mid = "chaleureuse",
                high = "très rassurante",
                value = config.softness,
                onValue = { onConfig(config.copy(softness = it)) },
            )
            PersonalitySlider(
                title = "Longueur des phrases",
                low = "très court",
                mid = "normal",
                high = "plus narratif",
                value = config.phraseLength,
                onValue = { onConfig(config.copy(phraseLength = it)) },
            )
            PersonalitySlider(
                title = "Curiosité",
                low = "presque pas de questions",
                mid = "questions occasionnelles",
                high = "relances plus fréquentes",
                value = config.curiosity,
                onValue = { onConfig(config.copy(curiosity = it)) },
            )
        }
    }
}

@Composable
private fun BlacklaceCharactersPanel(
    config: CharacterCastingConfig,
    onConfig: (CharacterCastingConfig) -> Unit,
) {
    val context = LocalContext.current
    val characters = remember(config.activeCharacterId) { CharacterRegistry.selectable(context) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Personnage actif", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Un seul personnage actif à la fois. Octopus anime celui que tu choisis.")
            characters.forEach { character ->
                CharacterChoiceCard(
                    character = character,
                    selected = character.id == config.activeCharacterId,
                    onChoose = {
                        onConfig(
                            config.copy(
                                activeCharacterId = character.id,
                                castingMode = CastingMode.LOCKED_CHARACTER,
                                guestsEnabled = false,
                            ),
                        )
                    },
                )
            }
            SettingsDropdown(
                title = "Gestion des personnages par Octopus",
                value = config.castingMode.label(),
                options = CastingMode.values().map { it.label() },
                onValue = { selected ->
                    CastingMode.values().firstOrNull { it.label() == selected }?.let {
                        onConfig(config.copy(castingMode = it, guestsEnabled = it != CastingMode.LOCKED_CHARACTER))
                    }
                },
            )
            SettingsSwitchRow(
                title = "Activer les apparitions invitées",
                subtitle = "Uniquement hors mode Personnage verrouillé.",
                checked = config.guestsEnabled,
                onCheckedChange = { onConfig(config.copy(guestsEnabled = it)) },
            )
            SettingsSwitchRow(
                title = "Autoriser Natasha",
                subtitle = "Commentaires lucides, acerbes mais non humiliants.",
                checked = config.allowNatasha,
                onCheckedChange = { onConfig(config.copy(allowNatasha = it)) },
            )
            SettingsSwitchRow(
                title = "Autoriser Feuch",
                subtitle = "Interventions chaotiques courtes pour relancer l'action.",
                checked = config.allowFeuch,
                onCheckedChange = { onConfig(config.copy(allowFeuch = it)) },
            )
            PersonalitySlider(
                title = "Fréquence des invités",
                low = "rares",
                mid = "ponctuels",
                high = "plus présents",
                value = config.guestFrequency,
                onValue = { onConfig(config.copy(guestFrequency = it)) },
            )
            PersonalitySlider(
                title = "Acidité Natasha",
                low = "sobre",
                mid = "lucide",
                high = "acerbe contrôlée",
                value = config.acidity,
                onValue = { onConfig(config.copy(acidity = it)) },
            )
            PersonalitySlider(
                title = "Chaos Feuch",
                low = "calme relatif",
                mid = "énergique",
                high = "volcanique contrôlé",
                value = config.feuchChaos,
                onValue = { onConfig(config.copy(feuchChaos = it)) },
            )
            SettingsSwitchRow(
                title = "Verrouiller le personnage actif comme principal",
                checked = config.lockClochetteHost,
                onCheckedChange = { onConfig(config.copy(lockClochetteHost = it)) },
            )
            SettingsSwitchRow(
                title = "Laisser Octopus choisir le personnage",
                checked = config.letOctopusChoose,
                onCheckedChange = { onConfig(config.copy(letOctopusChoose = it)) },
            )
        }
    }
}

@Composable
private fun CharacterChoiceCard(
    character: CharacterProfile,
    selected: Boolean,
    onChoose: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFFFF9E6) else Color(0xFFF7F3EA))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(character.visualAssets.thumbnail),
                contentDescription = character.displayName,
                modifier = Modifier.size(54.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(character.displayName, fontWeight = FontWeight.SemiBold)
                Text(character.shortDescription, style = MaterialTheme.typography.bodySmall)
                Text(character.roleLabel, style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
            }
            if (selected) {
                Text("Actif", color = Color(0xFF2E7D5B), fontWeight = FontWeight.SemiBold)
            } else {
                OutlinedButton(onClick = onChoose) { Text("Choisir") }
            }
        }
    }
}

@Composable
private fun PersonalitySlider(
    title: String,
    low: String,
    mid: String,
    high: String,
    value: Int,
    onValue: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsSlider(
            title = title,
            value = value.toFloat(),
            valueRange = 0f..100f,
            displayedValue = value.toString(),
            onValueChange = { onValue(it.toInt().coerceIn(0, 100)) },
        )
        Text(
            when {
                value < 34 -> low
                value > 66 -> high
                else -> mid
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color.DarkGray,
        )
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
            Text("Bank : ${diagnostics.lastPhraseBankId.ifBlank { "-" }}")
            Text("Entry : ${diagnostics.lastPhraseEntryId.ifBlank { "-" }}")
            Text("Tone : ${diagnostics.lastPhraseTone.ifBlank { "-" }}")
            Text("Provider : ${diagnostics.lastProviderUsed}")
            Text("Guardian : ${diagnostics.lastGuardianReason}")
            Text("Voix : ${diagnostics.lastVoiceStatus} · shouldSpeak=${diagnostics.lastShouldSpeak}")
            Text("Micro : ${diagnostics.lastMicStatus}")
            Text("Overlay : ${diagnostics.lastOverlayState}")
            Text("Apparence : ${diagnostics.lastAppearance.ifBlank { "-" }}")
            Text("Intention utilisateur : ${diagnostics.lastUserIntent.ifBlank { "-" }}")
            Text("Humeur utilisateur : ${diagnostics.lastUserMood.ifBlank { "-" }}")
            Text("Énergie utilisateur : ${diagnostics.lastUserEnergy.ifBlank { "-" }}")
            Text("Tags sélectionnés : ${diagnostics.lastSelectedTags.ifBlank { "-" }}")
            Text("Raison du choix : ${diagnostics.lastSelectionReason.ifBlank { "-" }}")
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
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Voix", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            SettingsSwitchRow(
                title = "Voix activée",
                checked = config.enabled,
                onCheckedChange = { onConfig(config.copy(enabled = it)) },
            )
            SettingsSwitchRow(
                title = "Parler automatiquement après chaque remarque",
                checked = config.autoSpeak,
                onCheckedChange = { onConfig(config.copy(autoSpeak = it)) },
            )
            SettingsSlider(
                title = "Vitesse de parole",
                value = config.speechRate,
                valueRange = 0.7f..1.4f,
                displayedValue = config.speechRate.formatOneDecimal(),
                onValueChange = { onConfig(config.copy(speechRate = it.coerceIn(0.7f, 1.4f))) },
            )
            SettingsSlider(
                title = "Hauteur / pitch",
                value = config.pitch,
                valueRange = 0.8f..1.7f,
                displayedValue = config.pitch.formatOneDecimal(),
                onValueChange = { onConfig(config.copy(pitch = it.coerceIn(0.8f, 1.7f))) },
            )
            SettingsDropdown(
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
            SettingsDropdown(
                title = "Effet sonore avant parole",
                value = config.soundEffect,
                options = listOf(
                    ClochetteVoiceSettings.EFFECT_NONE,
                    ClochetteVoiceSettings.EFFECT_BELL,
                    ClochetteVoiceSettings.EFFECT_POF,
                ),
                onValue = { onConfig(config.copy(soundEffect = it)) },
            )
            SettingsDropdown(
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
private fun SettingsSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun PermissionStepCard(
    step: Int,
    title: String,
    explanation: String,
    status: String,
    primaryButtonLabel: String,
    primaryAction: () -> Unit,
    secondaryButtonLabel: String? = null,
    secondaryAction: (() -> Unit)? = null,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$step. $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(explanation)
            Text("État : $status", color = if (status.contains("autor")) Color(0xFF2E7D5B) else Color(0xFF8A4B25))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = primaryAction) { Text(primaryButtonLabel) }
                if (secondaryButtonLabel != null && secondaryAction != null) {
                    OutlinedButton(onClick = secondaryAction) { Text(secondaryButtonLabel) }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayedValue: String,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(displayedValue)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@Composable
private fun SettingsDropdown(
    title: String,
    value: String,
    options: List<String>,
    onValue: (String) -> Unit,
) {
    VoiceChoice(title = title, value = value, options = options, onValue = onValue)
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
            val detected = modules.count { it.detected && it.validJson }
            val invalid = modules.count { it.detected && !it.validJson }
            val missing = modules.count { !it.detected }
            Text("Modules : $detected détectés, $invalid invalides, $missing manquants")
            val problems = modules.filter { !it.detected || !it.validJson }.take(4)
            if (problems.isNotEmpty()) {
                Text("À vérifier : ${problems.joinToString { it.fileName }}", style = MaterialTheme.typography.bodySmall)
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

private fun ClosedAppearanceMode.label(): String = when (this) {
    ClosedAppearanceMode.EDGE_PEEK -> "En retrait sur le bord"
    ClosedAppearanceMode.CALL_DOT -> "Simple point d'appel"
}

private fun ClosedAppearanceSide.label(): String = when (this) {
    ClosedAppearanceSide.LEFT -> "Gauche"
    ClosedAppearanceSide.RIGHT -> "Droite"
    ClosedAppearanceSide.AUTO -> "Automatique / dernier bord utilisé"
}

private fun CastingMode.label(): String = when (this) {
    CastingMode.LOCKED_CHARACTER -> "Personnage verrouillé"
    CastingMode.SUGGEST_CHANGES -> "Suggestions de changement"
    CastingMode.OCCASIONAL_GUESTS -> "Apparitions rares"
    CastingMode.BLACKLACE_ALIVE -> "Mode Blacklace vivant"
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

