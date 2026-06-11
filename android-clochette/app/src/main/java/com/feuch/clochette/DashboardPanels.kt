package com.feuch.clochette

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OverlayAppearancePanel(
    config: ClochetteOverlayConfig,
    onConfig: (ClochetteOverlayConfig) -> Unit,
) {
    DashboardCard(title = "Apparence") {
        Text("Regle la presence visible de Clochette sans toucher au widget.")
        DashboardChoice(
            title = "Taille",
            value = config.size,
            options = listOf(
                ClochetteOverlaySettings.SIZE_SMALL,
                ClochetteOverlaySettings.SIZE_NORMAL,
                ClochetteOverlaySettings.SIZE_LARGE,
            ),
            onValue = { onConfig(config.copy(size = it)) },
        )
        DashboardChoice(
            title = "Position",
            value = config.position,
            options = listOf(
                ClochetteOverlaySettings.POSITION_BOTTOM_END,
                ClochetteOverlaySettings.POSITION_BOTTOM_START,
            ),
            onValue = { onConfig(config.copy(position = it)) },
        )
        DashboardChoice(
            title = "Style de presence",
            value = config.presenceStyle,
            options = listOf(
                ClochetteOverlaySettings.PRESENCE_DISCRETE,
                ClochetteOverlaySettings.PRESENCE_NORMAL,
                ClochetteOverlaySettings.PRESENCE_INTRUSIVE,
            ),
            onValue = { onConfig(config.copy(presenceStyle = it)) },
        )
        Text("Transparence de la bulle : ${(config.bubbleAlpha * 100).toInt()} %")
        Slider(
            value = config.bubbleAlpha,
            onValueChange = { onConfig(config.copy(bubbleAlpha = it.coerceIn(0.35f, 1f))) },
            valueRange = 0.35f..1f,
        )
    }
}

@Composable
fun ConnectionsAndHabitsPanel(
    config: ConnectionConfig,
    onConfig: (ConnectionConfig) -> Unit,
) {
    DashboardCard(title = "Connexions et habitudes") {
        Text(ConnectionSettings.privacyNote(), style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
        DashboardChoice(
            title = "Surveillance des applications",
            value = config.observationMode,
            options = listOf(
                ConnectionSettings.MODE_OFF,
                ConnectionSettings.MODE_SOFT,
                ConnectionSettings.MODE_NORMAL,
                ConnectionSettings.MODE_TEASING,
            ),
            onValue = { onConfig(config.copy(observationMode = it)) },
        )
        DashboardChoice(
            title = "Temps avant intervention",
            value = ConnectionSettings.delayLabel(config.reminderDelayMinutes),
            options = listOf("30 min", "1 h", "2 h", "3 h"),
            onValue = {
                onConfig(
                    config.copy(
                        reminderDelayMinutes = when (it) {
                            "30 min" -> 30
                            "1 h" -> 60
                            "3 h" -> 180
                            else -> 120
                        },
                    ),
                )
            },
        )
        DashboardSwitchRow("ChatGPT", config.chatgptEnabled) { onConfig(config.copy(chatgptEnabled = it)) }
        DashboardSwitchRow("Chrome", config.chromeEnabled) { onConfig(config.copy(chromeEnabled = it)) }
        DashboardSwitchRow("YouTube", config.youtubeEnabled) { onConfig(config.copy(youtubeEnabled = it)) }
        DashboardSwitchRow("GitHub", config.githubEnabled) { onConfig(config.copy(githubEnabled = it)) }
        DashboardSwitchRow("Remarques spontanees", config.spontaneousRemarks) {
            onConfig(config.copy(spontaneousRemarks = it))
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun DashboardChoice(
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
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValue(option)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DashboardSwitchRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
