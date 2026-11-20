package es.aprenderparajugar

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
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
    var pendingImport by remember { mutableStateOf<AchievementSoundEvent?>(null) }
    var pendingRecord by remember { mutableStateOf<AchievementSoundEvent?>(null) }
    var recordingEvent by remember { mutableStateOf<AchievementSoundEvent?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var automaticGainControl by remember { mutableStateOf<AutomaticGainControl?>(null) }
    var noiseSuppressor by remember { mutableStateOf<NoiseSuppressor?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    fun stopRecording(save: Boolean) {
        val current = recorder
        val file = recordingFile
        runCatching { current?.stop() }
        runCatching { automaticGainControl?.release() }
        runCatching { noiseSuppressor?.release() }
        automaticGainControl = null
        noiseSuppressor = null
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
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioChannels(1)
                    setAudioSamplingRate(48_000)
                    setAudioEncodingBitRate(128_000)
                    setOutputFile(file.absolutePath)
                    prepare()
                }

                // Los efectos son opcionales: dependen del hardware y del fabricante.
                // Se conectan a la sesión de captura cuando Android lo permite.
                if (android.os.Build.VERSION.SDK_INT >= 24) {
                    val audioSessionId = createdRecorder.audioSessionId
                    if (AutomaticGainControl.isAvailable()) {
                        automaticGainControl = runCatching {
                            AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
                        }.getOrNull()
                    }
                    if (NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = runCatching {
                            NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
                        }.getOrNull()
                    }
                }

                createdRecorder.start()
                recorder = createdRecorder
                recordingFile = file
                recordingEvent = event
                message = "Grabando para «${event.title}»…"
            } catch (error: Exception) {
                runCatching { automaticGainControl?.release() }
                runCatching { noiseSuppressor?.release() }
                automaticGainControl = null
                noiseSuppressor = null
                runCatching { newRecorder?.release() }
                file.delete()
                message = "No se pudo iniciar la grabación: ${error.message ?: "error de audio"}"
            }
        }
    }

    DisposableEffect(Unit) { onDispose { stopRecording(false) } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Efectos y notificaciones", style = MaterialTheme.typography.headlineSmall)
        Text("Independientes de la música de fondo. En los cuentos, la narración se pausa mientras suena cada efecto y continúa desde el mismo punto al terminar.")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Activar efectos sonoros", style = MaterialTheme.typography.titleMedium)
                    Text("Logros, objetos, recompensas y compras.")
                }
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    AchievementSoundManager.setEnabled(context, it)
                })
            }
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        AchievementSoundEvent.values().forEach { event ->
            val custom = remember(event, refresh) { AchievementSoundManager.customPath(context, event) }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleMedium)
                    Text(if (custom == null) "Sonido predeterminado" else "Audio personalizado", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    runCatching { AchievementSoundManager.play(context, event, preview = true) }
                                        .onFailure { message = "No se pudo reproducir: ${it.message}" }
                                }
                            }
                        ) { Text("▶ Escuchar", maxLines = 1) }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    repeat(2) {
                                        runCatching { AchievementSoundManager.play(context, event, preview = true) }
                                            .onFailure { message = "No se pudo reproducir: ${it.message}" }
                                    }
                                }
                            }
                        ) { Text("↻ Repetir", maxLines = 1) }
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            pendingImport = event
                            importLauncher.launch(arrayOf("audio/*"))
                        }
                    ) { Text("Importar audio") }

                    if (recordingEvent == event) {
                        Text(
                            "● GRABACIÓN EN CURSO",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "Cuando termines de hablar, pulsa «Parar y guardar».",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { stopRecording(true) }
                            ) { Text("Parar y guardar") }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { stopRecording(false) }
                            ) { Text("Descartar grabación") }
                        }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = recordingEvent == null,
                            onClick = {
                                pendingRecord = event
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        ) { Text("Grabar voz") }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = custom != null,
                            onClick = {
                                AchievementSoundManager.restoreDefault(context, event)
                                message = "Se ha restaurado el sonido original."
                                refresh++
                            }
                        ) { Text("Restaurar sonido original") }
                    }
                }
            }
        }
    }
}
