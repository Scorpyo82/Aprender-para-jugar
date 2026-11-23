package es.aprenderparajugar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun ExerciseVoiceSettingsSection() {
    val context = LocalContext.current
    var masterEnabled by remember { mutableStateOf(ExerciseVoiceManager.isMasterEnabled(context)) }
    var instructionsEnabled by remember { mutableStateOf(ExerciseVoiceManager.isGroupEnabled(context, ExerciseVoiceManager.Group.INSTRUCTIONS)) }
    var encouragementEnabled by remember { mutableStateOf(ExerciseVoiceManager.isGroupEnabled(context, ExerciseVoiceManager.Group.ENCOURAGEMENT)) }
    var expanded by remember { mutableStateOf<ExerciseVoiceManager.Group?>(null) }
    var refresh by remember { mutableStateOf(0) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Locuciones educativas", style = MaterialTheme.typography.titleLarge)
            Text(
                "Mensajes de voz de Tarjetas, Palabras y Secuencias. La música de fondo baja de volumen mientras habla la voz y recupera su nivel al terminar.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Activar locuciones", style = MaterialTheme.typography.titleMedium)
                    Text("Control general", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = masterEnabled, onCheckedChange = {
                    masterEnabled = it
                    ExerciseVoiceManager.setMasterEnabled(context, it)
                    if (!it) ExerciseVoiceManager.stop()
                })
            }
            VoiceGroupCard(
                title = "Instrucciones",
                description = "Se reproducen automáticamente la primera vez que se accede a cada modalidad o tipo de Secuencias.",
                enabled = instructionsEnabled,
                expanded = expanded == ExerciseVoiceManager.Group.INSTRUCTIONS,
                items = ExerciseVoiceManager.items.filter { it.group == ExerciseVoiceManager.Group.INSTRUCTIONS },
                refresh = refresh,
                onToggle = {
                    instructionsEnabled = it
                    ExerciseVoiceManager.setGroupEnabled(context, ExerciseVoiceManager.Group.INSTRUCTIONS, it)
                },
                onExpand = { expanded = if (expanded == ExerciseVoiceManager.Group.INSTRUCTIONS) null else ExerciseVoiceManager.Group.INSTRUCTIONS },
                onRefresh = { refresh++ }
            )
            VoiceGroupCard(
                title = "Acompañamiento",
                description = "Mensajes breves al elegir una modalidad, dificultad o tipo de ejercicio.",
                enabled = encouragementEnabled,
                expanded = expanded == ExerciseVoiceManager.Group.ENCOURAGEMENT,
                items = ExerciseVoiceManager.items.filter { it.group == ExerciseVoiceManager.Group.ENCOURAGEMENT },
                refresh = refresh,
                onToggle = {
                    encouragementEnabled = it
                    ExerciseVoiceManager.setGroupEnabled(context, ExerciseVoiceManager.Group.ENCOURAGEMENT, it)
                },
                onExpand = { expanded = if (expanded == ExerciseVoiceManager.Group.ENCOURAGEMENT) null else ExerciseVoiceManager.Group.ENCOURAGEMENT },
                onRefresh = { refresh++ }
            )
        }
    }
}

@Composable
private fun VoiceGroupCard(
    title: String,
    description: String,
    enabled: Boolean,
    expanded: Boolean,
    items: List<ExerciseVoiceManager.VoiceItem>,
    refresh: Int,
    onToggle: (Boolean) -> Unit,
    onExpand: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = onExpand)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall)
                    Text(if (expanded) "Ocultar locuciones ▲" else "Ver locuciones ▼", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (expanded) {
                items.forEach { item ->
                    val itemEnabled = remember(item, refresh) { ExerciseVoiceManager.isItemEnabled(context, item) }
                    var checked by remember(item, refresh) { mutableStateOf(itemEnabled) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            enabled = enabled && checked,
                            onClick = { ExerciseVoiceManager.playLatest(context, item, preview = true) }
                        ) { Text("▶") }
                        Switch(checked = checked, onCheckedChange = {
                            checked = it
                            ExerciseVoiceManager.setItemEnabled(context, item, it)
                            onRefresh()
                        })
                    }
                }
            }
        }
    }
}
