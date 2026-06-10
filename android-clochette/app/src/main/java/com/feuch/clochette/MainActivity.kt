package com.feuch.clochette

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
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
import androidx.compose.material3.Surface
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
            ClochetteApp()
        }
    }
}

@Composable
private fun ClochetteApp() {
    val context = LocalContext.current
    val memory = remember { ClochetteMemory(context) }
    var refresh by remember { mutableIntStateOf(0) }
    var project by remember { mutableStateOf(ProjectKnowledge.projects.first().name) }
    var energy by remember { mutableStateOf("moyenne") }
    var currentLine by remember { mutableStateOf<String?>(null) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    fun generateLine(): String {
        val line = ClochetteEngine.remark(
            activity = UsageObserver(context).snapshot(),
            sensors = SensorSnapshot(),
            energy = energy,
            project = project,
            memory = memory.recent(),
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

                SelectorPanel(
                    project = project,
                    energy = energy,
                    onProject = { project = it },
                    onEnergy = { energy = it },
                    onLine = { generateLine() },
                    onSpeak = {
                        val line = currentLine ?: generateLine()
                        ClochetteVoice.speak(context, line)
                    },
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

                Text("Permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                PermissionCard(
                    title = "Widget ecran d'accueil",
                    explanation = "Ajoute Clochette depuis les widgets Android. Le widget affiche une remarque et peut parler quand tu le touches.",
                    enabled = true,
                    onEnable = {
                        val line = currentLine ?: generateLine()
                        ClochetteWidget.updateAll(context, line)
                    },
                    onDecline = { memory.decline("home_widget") },
                )
                PermissionCard(
                    title = "Surimpression",
                    explanation = "Plus tard. Pour l'instant on privilegie l'ecran d'accueil, plus stable et moins envahissant.",
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

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = "${context.packageName}/${ClochetteAccessibilityService::class.java.name}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    )
    return !enabledServices.isNullOrBlank() &&
        enabledServices.split(':').any { TextUtils.equals(it, expected) }
}
