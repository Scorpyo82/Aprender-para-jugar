package es.aprenderparajugar

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun AchievementSoundSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(AchievementSoundManager.isEnabled(context)) }
    var message by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    var expandedAudioKey by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<AchievementSoundEvent?>(null) }
    var pendingRecord by remember { mutableStateOf<AchievementSoundEvent?>(null) }
    var preparedRecordEvent by remember { mutableStateOf<AchievementSoundEvent?>(null) }
    var recordingEvent by remember { mutableStateOf<AchievementSoundEvent?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    fun stopRecording(save: Boolean) {
        val current = recorder
        val file = recordingFile
        runCatching { current?.stop() }
        runCatching { current?.release() }
        recorder = null
        recordingFile = null
        val event = recordingEvent
        recordingEvent = null
        if (save && event != null && file != null && file.isFile && file.length() > 0) {
            AchievementSoundManager.setCustomPath(context, event, file.absolutePath)
            message = "Grabación guardada para «${event.title}»."
            refresh++
        } else {
            file?.delete()
            message = "Grabación descartada."
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val event = pendingImport
        pendingImport = null
        if (uri != null && event != null) {
            message = runCatching {
                AchievementSoundManager.importAudio(context, event, uri)
                "Audio guardado para «${event.title}»."
            }.getOrElse { "No se pudo importar: ${it.message ?: "archivo no válido"}" }
            refresh++
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val event = pendingRecord
        pendingRecord = null
        if (!granted || event == null) {
            preparedRecordEvent = null
            message = "No se ha concedido permiso para usar el micrófono."
        } else {
            val file = File(context.filesDir, "achievement_sounds").apply { mkdirs() }
                .let { File(it, "${event.key}_voice_${System.currentTimeMillis()}.m4a") }
            var newRecorder: MediaRecorder? = null
            try {
                val createdRecorder = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
                newRecorder = createdRecorder
                createdRecorder.apply {
                    // Captura mono optimizada para voz, con parámetros constantes
                    // en lugar de depender de los valores predeterminados del fabricante.
                    // Fuente orientada a voz: solicita al dispositivo su ruta de captura de comunicación.
                    setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioChannels(1)
                    setAudioSamplingRate(48_000)
                    setAudioEncodingBitRate(128_000)
                    setOutputFile(file.absolutePath)
                    prepare()
                }

                createdRecorder.start()
                recorder = createdRecorder
                recordingFile = file
                recordingEvent = event
                preparedRecordEvent = null
                message = "Grabando para «${event.title}»…"
            } catch (error: Exception) {
                runCatching { newRecorder?.release() }
                file.delete()
                preparedRecordEvent = null
                message = "No se pudo iniciar la grabación: ${error.message ?: "error de audio"}"
            }
        }
    }

    DisposableEffect(Unit) { onDispose { stopRecording(false) } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExerciseVoiceSettingsSection(expandedAudioKey = expandedAudioKey, onExpandAudio = { expandedAudioKey = it })
        Text("Efectos y notificaciones", style = MaterialTheme.typography.headlineSmall)
        Text("Independientes de la música de fondo. En los cuentos, la narración se pausa mientras suena cada efecto y continúa desde el mismo punto al terminar.")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Activar efectos sonoros", style = MaterialTheme.typography.titleMedium)
                    Text("Logros, objetos, recompensas y compras.")
                }
                Checkbox(checked = enabled, onCheckedChange = {
                    enabled = it
                    AchievementSoundManager.setEnabled(context, it)
                })
            }
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        val resultEvents = listOf(
            AchievementSoundEvent.REWARD_GRANTED,
            AchievementSoundEvent.RESULT_PARTIAL,
            AchievementSoundEvent.RESULT_TRY_AGAIN
        )
        val renderSoundCard: @Composable (AchievementSoundEvent) -> Unit = { event ->
            val custom = remember(event, refresh) { AchievementSoundManager.customPath(context, event) }
            val eventEnabled = remember(event, refresh) { AchievementSoundManager.isEventEnabled(context, event) }
            val cardKey = "effect:" + event.key
            AchievementSoundEventCard(
                event = event,
                custom = custom != null,
                enabled = eventEnabled,
                expanded = expandedAudioKey == cardKey,
                onEnabledChange = { AchievementSoundManager.setEventEnabled(context, event, it); refresh++ },
                onExpandChange = { expandedAudioKey = if (expandedAudioKey == cardKey) null else cardKey },
                recording = recordingEvent == event,
                preparing = preparedRecordEvent == event,
                canPrepare = recordingEvent == null && preparedRecordEvent == null,
                onListen = {
                    scope.launch {
                        runCatching { AchievementSoundManager.play(context, event, preview = true) }
                            .onFailure { message = "No se pudo reproducir: ${it.message}" }
                    }
                },
                onRepeat = {
                    scope.launch {
                        repeat(2) {
                            runCatching { AchievementSoundManager.play(context, event, preview = true) }
                                .onFailure { message = "No se pudo reproducir: ${it.message}" }
                        }
                    }
                },
                onImport = {
                    pendingImport = event
                    importLauncher.launch(arrayOf("audio/*"))
                },
                onPrepareRecord = {
                    preparedRecordEvent = event
                    message = ""
                },
                onStopSave = { stopRecording(true) },
                onStopDiscard = { stopRecording(false) },
                onStartRecording = {
                    pendingRecord = event
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onCancelPreparation = {
                    preparedRecordEvent = null
                    message = "Grabación cancelada."
                },
                onRestore = {
                    AchievementSoundManager.restoreDefault(context, event)
                    message = "Se ha restaurado el sonido original."
                    refresh++
                }
            )
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Locuciones de resultados", style = MaterialTheme.typography.titleLarge)
                Text(
                    "La voz cambia según cómo haya ido el ejercicio. Puedes personalizar cada mensaje por separado.",
                    style = MaterialTheme.typography.bodyMedium
                )
                resultEvents.forEach { event -> renderSoundCard(event) }
            }
        }

        AchievementSoundEvent.values().filterNot { it in resultEvents }.forEach { event ->
            renderSoundCard(event)
        }
    }
}


@Composable
private fun AchievementSoundEventCard(
    event: AchievementSoundEvent,
    custom: Boolean,
    enabled: Boolean,
    expanded: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onExpandChange: () -> Unit,
    recording: Boolean,
    preparing: Boolean,
    canPrepare: Boolean,
    onListen: () -> Unit,
    onRepeat: () -> Unit,
    onImport: () -> Unit,
    onPrepareRecord: () -> Unit,
    onStopSave: () -> Unit,
    onStopDiscard: () -> Unit,
    onStartRecording: () -> Unit,
    onCancelPreparation: () -> Unit,
    onRestore: () -> Unit
) {
    AudioOptionCard(
        key = "effect:" + event.key,
        title = event.title,
        description = event.description,
        enabled = enabled,
        expanded = expanded,
        onEnabledChange = onEnabledChange,
        onExpandChange = onExpandChange
    ) {
        Text(if (custom) "Audio personalizado" else "Sonido predeterminado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        if (!custom && event.defaultSpeech != null) Text("«${event.defaultSpeech}»", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = onListen) { Text("▶ Escuchar", maxLines = 1) }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = onRepeat) { Text("↻ Repetir", maxLines = 1) }
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onRestore, enabled = custom && !recording) { Text("Dejar por defecto") }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onImport) { Text("Cargar audio") }
        if (recording) {
            Text("● GRABACIÓN EN CURSO", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            Text("Cuando termines de hablar, pulsa «Parar y guardar».", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = onStopSave) { Text("Parar y guardar") }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onStopDiscard) { Text("Descartar grabación") }
            }
        } else if (preparing) {
            Text("Preparar grabación", style = MaterialTheme.typography.titleSmall)
            Text("Cuando estés preparado, pulsa «Iniciar grabación». El micrófono no se activará hasta entonces.", style = MaterialTheme.typography.bodySmall)
            Button(modifier = Modifier.fillMaxWidth(), onClick = onStartRecording) { Text("Iniciar grabación") }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onCancelPreparation) { Text("Cancelar") }
        } else {
            OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = canPrepare, onClick = onPrepareRecord) { Text("Grabar audio") }
        }
    }
}