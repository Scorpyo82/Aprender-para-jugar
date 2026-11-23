package es.aprenderparajugar

import android.Manifest
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import java.io.File

@Composable
internal fun ExerciseVoiceSettingsSection(expandedAudioKey: String?, onExpandAudio: (String?) -> Unit) {
    val context = LocalContext.current
    var masterEnabled by remember { mutableStateOf(ExerciseVoiceManager.isMasterEnabled(context)) }
    var instructionsEnabled by remember { mutableStateOf(ExerciseVoiceManager.isGroupEnabled(context, ExerciseVoiceManager.Group.INSTRUCTIONS)) }
    var encouragementEnabled by remember { mutableStateOf(ExerciseVoiceManager.isGroupEnabled(context, ExerciseVoiceManager.Group.ENCOURAGEMENT)) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Locuciones educativas", style = MaterialTheme.typography.titleLarge)
        Text("Mensajes de Tarjetas, Palabras y Secuencias. La música baja mientras habla la locución y recupera gradualmente su volumen al terminar.", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Activar locuciones", style = MaterialTheme.typography.titleMedium)
                Text("Control general de todas las locuciones educativas.", style = MaterialTheme.typography.bodySmall)
            }
            Checkbox(checked = masterEnabled, onCheckedChange = {
                masterEnabled = it
                ExerciseVoiceManager.setMasterEnabled(context, it)
                if (!it) ExerciseVoiceManager.stop()
            })
        }

        Text("Instrucciones", style = MaterialTheme.typography.titleMedium)
        Text("Explican cómo realizar cada modalidad o tipo de Secuencias. Se reproducen automáticamente al comenzar y pueden repetirse manualmente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Activar todas las instrucciones", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Checkbox(checked = instructionsEnabled, enabled = masterEnabled, onCheckedChange = {
                instructionsEnabled = it
                ExerciseVoiceManager.setGroupEnabled(context, ExerciseVoiceManager.Group.INSTRUCTIONS, it)
                if (!it) ExerciseVoiceManager.stop()
            })
        }
        ExerciseVoiceManager.items.filter { it.group == ExerciseVoiceManager.Group.INSTRUCTIONS }.forEach { item ->
            VoiceItemEditor(item, masterEnabled && instructionsEnabled, expandedAudioKey == "voice:" + item.key, {
                onExpandAudio(if (expandedAudioKey == "voice:" + item.key) null else "voice:" + item.key)
            })
        }

        Text("Acompañamiento", style = MaterialTheme.typography.titleMedium)
        Text("Mensajes breves que animan al niño al elegir modalidad, dificultad o tipo de ejercicio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Activar todos los mensajes de acompañamiento", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Checkbox(checked = encouragementEnabled, enabled = masterEnabled, onCheckedChange = {
                encouragementEnabled = it
                ExerciseVoiceManager.setGroupEnabled(context, ExerciseVoiceManager.Group.ENCOURAGEMENT, it)
                if (!it) ExerciseVoiceManager.stop()
            })
        }
        ExerciseVoiceManager.items.filter { it.group == ExerciseVoiceManager.Group.ENCOURAGEMENT }.forEach { item ->
            VoiceItemEditor(item, masterEnabled && encouragementEnabled, expandedAudioKey == "voice:" + item.key, {
                onExpandAudio(if (expandedAudioKey == "voice:" + item.key) null else "voice:" + item.key)
            })
        }
    }
}

@Composable
private fun VoiceItemEditor(item: ExerciseVoiceManager.VoiceItem, parentEnabled: Boolean, expanded: Boolean, onExpand: () -> Unit) {
    val context = LocalContext.current
    var checked by remember(item) { mutableStateOf(ExerciseVoiceManager.isItemEnabled(context, item)) }
    var custom by remember(item) { mutableStateOf(ExerciseVoiceManager.customPath(context, item) != null) }
    var preparing by remember(item) { mutableStateOf(false) }
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
                .onFailure { file.delete(); message = "No se pudo guardar: " + (it.message ?: "error") }
        } else {
            file?.delete()
            message = if (save) "La grabación no contiene audio válido." else "Grabación descartada."
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ExerciseVoiceManager.importAudio(context, item, uri) }
                .onSuccess { custom = true; message = "Audio importado correctamente." }
                .onFailure { message = "No se pudo importar: " + (it.message ?: "archivo no válido") }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            preparing = false
            message = "No se ha concedido permiso para usar el micrófono."
        } else {
            val file = File(context.cacheDir, item.key + "_" + System.currentTimeMillis() + ".m4a")
            var created: MediaRecorder? = null
            try {
                val instance = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
                created = instance
                instance.apply {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
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
                preparing = false
                recording = true
                message = "Grabando…"
            } catch (error: Exception) {
                runCatching { created?.release() }
                file.delete()
                preparing = false
                message = "No se pudo iniciar la grabación: " + (error.message ?: "error de audio")
            }
        }
    }
    // Mantener un único efecto durante la vida del editor. Si se usara
    // DisposableEffect(recorder), al asignar el MediaRecorder recién iniciado
    // Compose cerraría el efecto anterior y podría detener esa misma grabación.
    DisposableEffect(Unit) {
        onDispose {
            val activeRecorder = recorder
            val activeFile = recordingFile
            if (activeRecorder != null) {
                runCatching { activeRecorder.stop() }
                runCatching { activeRecorder.release() }
                activeFile?.delete()
            }
        }
    }

    AudioOptionCard(
        key = "voice:" + item.key,
        title = item.title,
        description = item.description,
        enabled = checked,
        expanded = expanded,
        onEnabledChange = {
            checked = it
            ExerciseVoiceManager.setItemEnabled(context, item, it)
            if (!it) ExerciseVoiceManager.stop()
        },
        onExpandChange = onExpand,
        checkboxEnabled = parentEnabled
    ) {
        Text(if (custom) "Audio personalizado" else "Audio predeterminado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(modifier = Modifier.weight(1f), enabled = parentEnabled, onClick = {
                if (!ExerciseVoiceManager.playLatest(context, item, preview = true)) message = "No se ha podido reproducir el audio."
            }) { Text("▶ Escuchar", maxLines = 1) }
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = custom && !recording, onClick = {
            ExerciseVoiceManager.restoreDefault(context, item)
            custom = false
            message = "Se ha restaurado el audio original."
        }) { Text("Dejar por defecto") }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !recording && !preparing, onClick = { importLauncher.launch(arrayOf("audio/*")) }) { Text("Cargar audio") }
        if (recording) {
            Text("● GRABACIÓN EN CURSO", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            Text("Cuando termines de hablar, pulsa «Parar y guardar».", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = { finishRecording(true) }) { Text("Parar y guardar") }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = { finishRecording(false) }) { Text("Descartar") }
            }
        } else if (preparing) {
            Text("Preparar grabación", style = MaterialTheme.typography.titleSmall)
            Text("Cuando estés preparado, pulsa «Iniciar grabación». El micrófono no se activará hasta entonces.", style = MaterialTheme.typography.bodySmall)
            Button(modifier = Modifier.fillMaxWidth(), onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Iniciar grabación") }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                preparing = false
                message = "Grabación cancelada."
            }) { Text("Cancelar") }
        } else {
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                preparing = true
                message = ""
            }) { Text("Grabar audio") }
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}
