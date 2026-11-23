package es.aprenderparajugar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context

/** Estado persistente compartido entre la configuración y el reproductor de cuentos. */
internal class StoryAudioSettingsState(context: Context) {
    var volume by mutableIntStateOf(
        context.getSharedPreferences("aprender_para_jugar", Context.MODE_PRIVATE)
            .getInt("story_ambient_during_narration_percent", 20).coerceIn(0, 30)
    )
        private set

    fun setVolume(context: Context, value: Int) {
        volume = value.coerceIn(0, 30)
        context.getSharedPreferences("aprender_para_jugar", Context.MODE_PRIVATE)
            .edit().putInt("story_ambient_during_narration_percent", volume).apply()
    }
}

@Composable
internal fun SoundSettingsMenu(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ajustes de sonido", fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        SoundSettingsNavigationCard(
            title = "🎵 Música de fondo",
            description = "Elige la música que acompaña el uso general de la aplicación."
        ) { onNavigate("music") }
        SoundSettingsNavigationCard(
            title = "📖 Audio de cuentos",
            description = "Configura el volumen del ambiente durante la narración."
        ) { onNavigate("stories-audio") }
        SoundSettingsNavigationCard(
            title = "🏆 Efectos y notificaciones",
            description = "Personaliza los sonidos de objetos, logros, recompensas y compras."
        ) { onNavigate("achievement-sounds") }
    }
}

@Composable
private fun SoundSettingsNavigationCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(title, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun StoryAudioSettingsScreen(state: StoryAudioSettingsState) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Audio de cuentos", fontSize = 23.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(
            "Cuando se reproduce una narración, el sonido ambiental se atenúa para que la voz se escuche con claridad. Al terminar, recupera progresivamente su volumen original.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Volumen ambiental durante la narración", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Text(
            "${state.volume} % del volumen original (máximo 30 %)",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = state.volume.toFloat(),
            onValueChange = { state.setVolume(context, it.toInt()) },
            valueRange = 0f..30f,
            steps = 29
        )
        Text(
            "Este ajuste es global: se aplica a todos los cuentos y se guarda automáticamente en este dispositivo.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun MusicSettingsScreen(
    musicUri: String?,
    onMusicUriChange: (String?) -> Unit,
    soundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    message: String,
    onMessageChange: (String) -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("aprender_para_jugar", Context.MODE_PRIVATE)
    }
    val uriHandler = LocalUriHandler.current
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            onMusicUriChange(uri.toString())
            preferences.edit().putString("background_music_uri", uri.toString()).apply()
            onMessageChange("Música seleccionada. Se reproducirá en bucle cuando el sonido esté activado.")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Música de fondo", fontSize = 23.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(
            "La música comienza al abrir la aplicación y se repite en bucle. Usa el altavoz de la pantalla principal para silenciar o activar la música de fondo.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (musicUri.isNullOrBlank()) "Música predeterminada: Música Infantil 2."
            else "Archivo de música personalizado seleccionado.",
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
        Button(
            onClick = {
                preferences.edit()
                    .putBoolean("content_picker_active", true)
                    .putBoolean("parent_area_active", true)
                    .commit()
                musicPicker.launch(arrayOf("audio/mpeg", "audio/mp3", "audio/*"))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Elegir archivo MP3") }
        OutlinedButton(
            onClick = {
                onMusicUriChange(null)
                preferences.edit().remove("background_music_uri").apply()
                onMessageChange("Se ha restaurado la música predeterminada.")
            },
            enabled = !musicUri.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Quitar música") }
        OutlinedButton(
            onClick = { onSoundEnabledChange(!soundEnabled) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (soundEnabled) "Silenciar música" else "Activar música") }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        Text(
            "Consejo: elige una melodía instrumental tranquila, sin cambios bruscos de volumen y con un final que encaje bien al repetirse.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text("Agradecimientos musicales", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text("Música Infantil 2 — Ezequiel David Urbano", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Text(
            "Licencia declarada por el autor: Creative Commons Zero (CC0 1.0).",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { uriHandler.openUri("https://open.spotify.com/artist/3FnwImaOicMeRPERcj6ueb?si=LBbyDbPYRyyO1Z6ium2P0w") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Escuchar al autor en Spotify") }
        OutlinedButton(
            onClick = { uriHandler.openUri("https://www.instagram.com/ezequieldavidurbano/") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Instagram del autor") }
    }
}
