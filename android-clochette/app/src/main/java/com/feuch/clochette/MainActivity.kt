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
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    fun updateVoiceConfig(config: ClochetteVoiceConfig) {
        voiceConfig = config
        ClochetteVoiceSettings.save(context, config)
    }

    fun generateLine(autoSpeak: Boolean = true): String {
        val line = ClochetteEngine.remark(
            activity = UsageObserver(context).snapshot(),
                sensors = SensorSnapshot(),
                energy = energy,
                project = project,
                memory = memory.recent(24),
                phraseLength = voiceConfig.phraseLength,
            )
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
            ClochetteWidget.updateAll(context, line)
            if (autoSpeak) ClochetteVoice.speakAfterRemark(context, line)
            return line
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
                    "Phase 1 : Clochette habite l'ecran d'accueil. Widget local, remarque courte, voix Android.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                StatusPanel(context = context, refresh = refresh)

                VisibleClochettePanel(
                    context = context,
                    currentLine = currentLine,
                    onNeedLine = { generateLine() },
                    onRefresh = { refresh++ },
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
                        Toast.makeText(context, "Observation active", Toast.LENGTH_SHORT).show()
                        refresh++
                    }) {
                        Text("Observer")
                    }
                    OutlinedButton(onClick = {
                        context.startService(
                            Intent(context, ClochettePresenceService::class.java)
                                .setAction(ClochettePresenceService.ACTION_PAUSE),
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
                )

                SelectorPanel(
                    project = project,
                    energy = energy,
                    onProject = { project = it },
                    onEnergy = { energy = it },
                    onLine = { generateLine() },
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

                Text("Widget ecran d'accueil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                PermissionCard(
                    title = "Widget ecran d'accueil",
                    explanation = "Ajoute Clochette depuis les widgets Android. Le widget affiche une remarque et peut parler quand tu le touches.",
                    enabled = true,
                    onEnable = {
                        openWidgetPicker(context)
                        Toast.makeText(context, "Ajoute le widget depuis la liste Android.", Toast.LENGTH_LONG).show()
                    },
                    onDecline = { memory.decline("home_widget") },
                )

                Text("Permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                PermissionCard(
                    title = "Surimpression",
                    explanation = "Affiche Clochette par-dessus les apps, en petite presence visible et stoppable.",
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
                    explanation = "Permet de savoir quelles apps sont utilisees, sans lire leur contenu : package, duree approximative, bascules.",
                    enabled = UsageObserver(context).hasPermission(),
                    onEnable = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    onDecline = { memory.decline("usage_access") },
                )
                PermissionCard(
                    title = "Capteurs",
                    explanation = "Utilise mouvement, orientation et lumiere si disponible pour produire des signaux sobres : marche possible, telephone immobile, basse lumiere, ecran actif.",
                    enabled = true,
                    onEnable = { refresh++ },
                    onDecline = { memory.decline("sensors") },
                )
                PermissionCard(
                    title = "Assistive Clochette",
                    explanation = "Mode avance, desactive par defaut. Isole derriere AccessibilityService. Il ne recupere pas le contenu des fenetres dans ce prototype.",
                    enabled = isAccessibilityServiceEnabled(context),
                    onEnable = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onDecline = { memory.decline("assistive_clochette") },
                )

                Text("Memoire locale", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
            Text("Etat : ${stateLabel(state)}", fontWeight = FontWeight.SemiBold)
            Text("Carnet d'indices local. Aucun appel reseau, aucune cle API en dur.")
            Text("Rafraichissement $refresh", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
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
            Text("Affiche une petite Clochette en bas de l'ecran, avec bulle de texte et boutons rapides.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    if (Settings.canDrawOverlays(context)) {
                        val line = currentLine ?: onNeedLine()
                        context.startService(
                            Intent(context, ClochetteOverlayService::class.java)
                                .setAction(ClochetteOverlayService.ACTION_SHOW)
                                .putExtra(ClochetteRemarkStore.EXTRA_LINE, line),
                        )
                        Toast.makeText(context, "Overlay démarré", Toast.LENGTH_SHORT).show()
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
                if (Settings.canDrawOverlays(context)) "Surimpression : autorisee" else "Surimpression : autorisation requise",
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
            Text("Reponse a Clochette", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            Text("Voix de Clochette", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Voix activee")
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { onConfig(config.copy(enabled = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Parler apres chaque remarque")
                Switch(
                    checked = config.autoSpeak,
                    onCheckedChange = { onConfig(config.copy(autoSpeak = it)) },
                )
            }
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
            Text("Contexte declare", fontWeight = FontWeight.SemiBold)
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
                        Text("Energie: $energy")
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
