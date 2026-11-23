package es.aprenderparajugar

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

internal data class StoryEditorAudioActions(
    val onPickAmbient: () -> Unit,
    val onPickNarration: () -> Unit,
    val load: (StoredStory) -> Unit,
    val reset: () -> Unit
)

/** Selección, importación y gestión de audio del editor de escenas. */
@Composable
internal fun rememberStoryEditorAudioActions(
    state: StoryEditorState,
    preferences: SharedPreferences
): StoryEditorAudioActions {
    val context = LocalContext.current
    val ambientPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) runCatching {
            val stored = state.persist() ?: error(state.message.value.ifBlank { "No se pudo guardar el borrador del cuento." })
            val scene = StoryRepository.sceneFolderName(state.selectedScene.value)
            val fileName = StoryRepository.importMedia(context, uri, File(File(stored.directory, "escenas"), scene), "ambiente")
            state.ambientAudio.value = state.ambientAudio.value.toMutableMap().apply { put(state.selectedScene.value, fileName) }
            state.persist()
        }.onFailure { state.message.value = it.message ?: "No se pudo importar el audio ambiental." }
    }
    val narrationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) runCatching {
            val stored = state.persist() ?: error(state.message.value.ifBlank { "No se pudo guardar el borrador del cuento." })
            val scene = StoryRepository.sceneFolderName(state.selectedScene.value)
            val fileName = StoryRepository.importMedia(context, uri, File(File(stored.directory, "escenas"), scene), "narracion")
            state.narrationAudio.value = state.narrationAudio.value.toMutableMap().apply { put(state.selectedScene.value, fileName) }
            state.persist()
        }.onFailure { state.message.value = it.message ?: "No se pudo importar la narración." }
    }
    return StoryEditorAudioActions(
        onPickAmbient = {
            preferences.edit().putBoolean("content_picker_active", true).apply()
            ambientPicker.launch(arrayOf("audio/*"))
        },
        onPickNarration = {
            preferences.edit().putBoolean("content_picker_active", true).apply()
            narrationPicker.launch(arrayOf("audio/*"))
        },
        load = { stored ->
            state.ambientAudio.value = stored.metadata.scenes.associate { scene ->
                scene to runCatching { StoryRepository.readScene(stored, scene).metadata.ambientAudio }.getOrNull()
            }
            state.narrationAudio.value = stored.metadata.scenes.associate { scene ->
                scene to runCatching { StoryRepository.readScene(stored, scene).metadata.narrationAudio }.getOrNull()
            }
        },
        reset = {
            state.ambientAudio.value = emptyMap()
            state.narrationAudio.value = emptyMap()
        }
    )
}

@Composable
internal fun StoryAudioPreview(file: File?, label: String) {
    val context = LocalContext.current
    val player = remember(file?.absolutePath, file?.lastModified()) {
        file?.takeIf { it.isFile && it.length() > 0L }?.let { runCatching { MediaPlayer.create(context, Uri.fromFile(it)) }.getOrNull() }
    }
    var playing by remember(file?.absolutePath) { mutableStateOf(false) }
    DisposableEffect(player) {
        player?.setOnCompletionListener { playing = false }
        onDispose {
            runCatching { if (player?.isPlaying == true) player.pause() }
            player?.release()
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, Color(0xFFD8D0C7), RoundedCornerShape(12.dp))
            .background(Color(0xFFFFFDF8), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF667085))
        Text(file?.name ?: "Todavía no hay ningún audio seleccionado.", fontSize = 12.sp, color = Color(0xFF667085))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                player?.let {
                    if (it.isPlaying) { it.pause(); playing = false }
                    else { if (it.currentPosition >= it.duration) it.seekTo(0); it.start(); playing = true }
                }
            }, enabled = player != null) { Text(if (playing) "Pausar" else "Reproducir") }
            OutlinedButton(onClick = {
                player?.let {
                    if (it.isPlaying) it.pause()
                    it.seekTo(0)
                    playing = false
                }
            }, enabled = player != null) { Text("Detener") }
        }
        if (file != null && player == null) Text("No se pudo cargar este audio.", color = Color(0xFFB3261E), fontSize = 12.sp)
    }
}
