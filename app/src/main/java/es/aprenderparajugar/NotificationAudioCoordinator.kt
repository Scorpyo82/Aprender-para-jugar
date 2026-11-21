package es.aprenderparajugar

import android.media.MediaPlayer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Prioriza las locuciones de notificación sobre la narración y el ambiente
 * del cuento. Conserva el estado previo de cada reproductor.
 */
internal object NotificationAudioCoordinator {
    private val notificationMutex = Mutex()
    @Volatile private var narrationPlayer: MediaPlayer? = null
    @Volatile private var ambientPlayer: MediaPlayer? = null

    fun registerStoryPlayers(narration: MediaPlayer?, ambient: MediaPlayer?) {
        narrationPlayer = narration
        ambientPlayer = ambient
    }

    suspend fun <T> withNotificationFocus(
        resumeStoryAudio: Boolean = true,
        action: suspend () -> T
    ): T = notificationMutex.withLock {
        val narration = narrationPlayer
        val ambient = ambientPlayer
        val resumeNarration = runCatching { narration?.isPlaying == true }.getOrDefault(false)
        val resumeAmbient = runCatching { ambient?.isPlaying == true }.getOrDefault(false)

        if (resumeNarration) runCatching { narration?.pause() }
        if (resumeAmbient) runCatching { ambient?.pause() }

        try {
            action()
        } finally {
            if (resumeStoryAudio) {
                if (resumeAmbient && ambientPlayer === ambient) runCatching { ambient?.start() }
                if (resumeNarration && narrationPlayer === narration) runCatching { narration?.start() }
            }
        }
    }
}
