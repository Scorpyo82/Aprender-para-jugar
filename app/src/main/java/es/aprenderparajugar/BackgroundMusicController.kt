package es.aprenderparajugar

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Mantiene la música ambiental independiente de la navegación de AprenderApp.
 * Solo recrea el reproductor cuando cambia la pista seleccionada.
 */
@Composable
internal fun BackgroundMusicController(
    musicUri: String?,
    soundEnabled: Boolean,
    appInForeground: Boolean,
    screen: String,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    var playerState by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(musicUri) {
        var player: MediaPlayer? = null
        try {
            player = if (musicUri.isNullOrBlank()) {
                MediaPlayer.create(context, R.raw.musica_infantil_2)
            } else {
                MediaPlayer.create(context, Uri.parse(musicUri))
            }
            player?.apply {
                isLooping = true
                setVolume(0.22f, 0.22f)
            }
            if (player == null) onError("No se ha podido cargar la música.")
            playerState = player
            ExerciseVoiceManager.setBackgroundMusicPlayer(player)
        } catch (_: Exception) {
            playerState = null
            onError("No se ha podido reproducir la música. Prueba con otro MP3.")
        }
        onDispose {
            if (playerState === player) playerState = null
            if (player != null) ExerciseVoiceManager.setBackgroundMusicPlayer(null)
            try { player?.stop() } catch (_: Exception) { }
            player?.release()
        }
    }

    // La música se pausa en Cuentos y al dejar la aplicación en segundo plano.
    // Al reanudarse, continúa desde la posición conservada.
    DisposableEffect(playerState, soundEnabled, appInForeground, screen) {
        val player = playerState
        if (player != null) {
            try {
                if (soundEnabled && appInForeground && screen != "story-player") {
                    if (!player.isPlaying) player.start()
                } else if (player.isPlaying) {
                    player.pause()
                }
            } catch (_: Exception) { }
        }
        onDispose { }
    }
}
