package es.aprenderparajugar

import android.Manifest
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File

@Composable
internal fun ExerciseVoiceSettingsSection() {
    val context = LocalContext.current
    var masterEnabled by remember { mutableStateOf(ExerciseVoiceManager.isMasterEnabled(context)) }
    var instructionsEnabled by remember { mutableStateOf(ExerciseVoiceManager.isGroupEnabled(context, ExerciseVoiceManager.Group.INSTRUCTIONS)) }
    var encouragementEnabled by remember { mutableStateOf(ExerciseVoiceManager.isGroupEnabled(context, ExerciseVoiceManager.Group.ENCOURAGEMENT)) }
    var expanded by remember { mutableStateOf<ExerciseVoiceManager.Group?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Locuciones educativas", style = MaterialTheme.typography.titleLarge)
            Text(
                "Mensajes de Tarjetas, Palabras y Secuencias. La música baja mientras habla la locución y recupera gradualmente su volumen al terminar.",
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
                description = "Se reproducen automáticamente una sola vez por modalidad o tipo de Secuencias. También se pueden volver a escuchar manualmente.",
                enabled = instructionsEnabled,
                expanded = expanded == ExerciseVoiceManager.Group.INSTRUCTIONS,
                items = ExerciseVoiceManager.items.filter { it.group == ExerciseVoiceManager.Group.INSTRUCTIONS },
                onToggle = {
                    instructionsEnabled = it
                    ExerciseVoiceManager.setGroupEnabled(context, ExerciseVoiceManager.Group.INSTRUCTIONS, it)
                    if (!it) ExerciseVoiceManager.stop()
                },
                onExpand = { expanded = if (expanded == ExerciseVoiceManager.Group.INSTRUCTIONS) null else ExerciseVoiceManager.Group.INSTRUCTIONS }
            )
            VoiceGroupCard(
                title = "Acompañamiento",
                description = "Mensajes breves al elegir modalidad, dificultad o tipo de ejercicio.",
                enabled = encouragementEnabled,
                expanded = expanded == ExerciseVoiceManager.Group.ENCOURAGEMENT,
                items = ExerciseVoiceManager.items.filter { it.group == ExerciseVoiceManager.Group.ENCOURAGEMENT },
                onToggle = {
                    encouragementEnabled = it
                    ExerciseVoiceManager.setGroupEnabled(context, ExerciseVoiceManager.Group.ENCOURAGEMENT, it)
                    if (!it) ExerciseVoiceManager.stop()
                },
                onExpand = { expanded = if (expanded == ExerciseVoiceManager.Group.ENCOURAGEMENT) null else ExerciseVoiceManager.Group.ENCOURAGEMENT }
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
    onToggle: (Boolean) -> Unit,
    onExpand: () -> Unit
) {
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
            if (expanded) items.forEach { item -> VoiceItemEditor(item = item, groupEnabled = enabled) }
        }
    }
}

@Composable
private fun VoiceItemEditor(item: ExerciseVoiceManager.VoiceItem, groupEnabled: Boolean) {
    val context = LocalContext.current
    var checked by remember(item) { mutableStateOf(ExerciseVoiceManager.isItemEnabled(context, item)) }
    var custom by remember(item) { mutableStateOf(ExerciseVoiceManager.customPath(context, item) != null) }
    var recording by remember(item) { mutableStateOf(false) }
    var recorder by remember(item) { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember(item) { mutableStateOf<File?>(null) }
    var message by remember(item) { mutableStateOf("") }

    fun finishRecording(save: Boolean) {
        val activeRecorder = recorder
        val file = recordingFile
        val stopped = runCatching { activeRecorder?.stop() }.isSuccess
        runCatching { activeRecorder?.release() }
        recorder = null
        recordingFile = null
        recording = false
        if (save && stopped && file != null && file.isFile && file.length() > 0L) {
            runCatching { ExerciseVoiceManager.setRecordedAudio(context, item, file) }
                .onSuccess { custom = true; message = "Grabación guardada." }
                .onFailure { file.delete(); message = "No se pudo guardar: ${it.message ?: "error"}" }
        } else {
            file?.delete()
            message = if (save) "La grabación no contiene audio válido." else "Grabación descartada."
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ExerciseVoiceManager.importAudio(context, item, uri) }
                .onSuccess { custom = true; message = "Audio importado correctamente." }
                .onFailure { message = "No se pudo importar: ${it.message ?: "archivo no válido"}" }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            message = "No se ha concedido permiso para usar el micrófono."
        } else {
            val file = File(context.cacheDir, "${item.key}_${System.currentTimeMillis()}.m4a")
            var created: MediaRecorder? = null
            try {
                val instance = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
                created = instance
                instance.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioChannels(1)
                    setAudioSamplingRate(48_000)
                    setAudioEncodingBitRate(128_000)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                }
                recorder = instance
                recordingFile = file
                recording = true
                message = "Grabando…"
            } catch (error: Exception) {
                runCatching { created?.release() }
                file.delete()
                message = "No se pudo iniciar la grabación: ${error.message ?: "error de audio"}"
            }
        }
    }
    DisposableEffect(recorder) {
        onDispose {
            if (recorder != null) {
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
                recordingFile?.delete()
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium)
                Text(if (custom) "Audio personalizado" else "Audio predeterminado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            OutlinedButton(onClick = { ExerciseVoiceManager.playLatest(context, item, preview = true) }) { Text("▶") }
            Switch(checked = checked, onCheckedChange = {
                checked = it
                ExerciseVoiceManager.setItemEnabled(context, item, it)
                if (!it) ExerciseVoiceManager.stop()
            })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = { importLauncher.launch(arrayOf("audio/*")) }) { Text("Importar", maxLines = 1) }
            OutlinedButton(modifier = Modifier.weight(1f), enabled = !recording, onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Grabar voz", maxLines = 1) }
            OutlinedButton(modifier = Modifier.weight(1f), enabled = custom && !recording, onClick = {
                ExerciseVoiceManager.restoreDefault(context, item)
                custom = false
                message = "Se ha restaurado el audio original."
            }) { Text("Original", maxLines = 1) }
        }
        if (recording) {
            Text("● GRABACIÓN EN CURSO", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = { finishRecording(true) }) { Text("Parar y guardar") }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = { finishRecording(false) }) { Text("Descartar") }
            }
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}
