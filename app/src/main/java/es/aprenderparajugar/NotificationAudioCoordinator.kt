package es.aprenderparajugar

import android.media.MediaPlayer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Prioriza las locuciones de notificación sobre la narración y el ambiente
 * del cuento. Una transición cancela la narración anterior y mantiene el
 * ambiente pausado hasta que termina toda la presentación de recompensas.
 */
internal object NotificationAudioCoordinator {
    private val notificationMutex = Mutex()
    @Volatile private var narrationPlayer: MediaPlayer? = null
    @Volatile private var ambientPlayer: MediaPlayer? = null
    @Volatile private var narrationGeneration = 0L
    @Volatile private var pendingAmbientResumePlayer: MediaPlayer? = null
    @Volatile private var sceneTransitionPending = false

    fun registerStoryPlayers(narration: MediaPlayer?, ambient: MediaPlayer?) {
        val previousAmbient = ambientPlayer
        if (previousAmbient !== ambient && pendingAmbientResumePlayer !== ambient) {
            pendingAmbientResumePlayer = null
        }
        narrationPlayer = narration
        ambientPlayer = ambient
    }

    /** Cancela definitivamente la narración de la escena que se abandona. */
    fun prepareSceneTransition() {
        sceneTransitionPending = true
        narrationGeneration++
        runCatching { narrationPlayer?.pause() }

        val ambient = ambientPlayer
        if (ambient != null && runCatching { ambient.isPlaying }.getOrDefault(false)) {
            pendingAmbientResumePlayer = ambient
            runCatching { ambient.pause() }
        }
    }

    /**
     * Cierra la transición. Sólo reanuda el ambiente si la nueva escena
     * conserva el reproductor anterior; nunca reanuda la narración abandonada.
     */
    fun completeSceneTransition(resumeAmbient: Boolean) {
        sceneTransitionPending = false
        val ambient = ambientPlayer
        if (resumeAmbient && ambient != null && pendingAmbientResumePlayer === ambient) {
            runCatching { ambient.start() }
        }
        pendingAmbientResumePlayer = null
    }

    suspend fun <T> withNotificationFocus(
        resumeStoryAudio: Boolean = true,
        action: suspend () -> T
    ): T = notificationMutex.withLock {
        val narration = narrationPlayer
        val ambient = ambientPlayer
        val capturedNarrationGeneration = narrationGeneration
        val resumeNarration = runCatching { narration?.isPlaying == true }.getOrDefault(false)
        val resumeAmbient = runCatching { ambient?.isPlaying == true }.getOrDefault(false) ||
            (ambient != null && pendingAmbientResumePlayer === ambient)

        if (resumeNarration) runCatching { narration?.pause() }
        if (runCatching { ambient?.isPlaying == true }.getOrDefault(false)) {
            runCatching { ambient?.pause() }
        }

        try {
            action()
        } finally {
            if (resumeStoryAudio) {
                if (!sceneTransitionPending && resumeAmbient && ambientPlayer === ambient) {
                    runCatching { ambient?.start() }
                    if (pendingAmbientResumePlayer === ambient) pendingAmbientResumePlayer = null
                }
                if (
                    narrationGeneration == capturedNarrationGeneration &&
                    resumeNarration &&
                    narrationPlayer === narration
                ) {
                    runCatching { narration?.start() }
                }
            } else {
                pendingAmbientResumePlayer = null
                sceneTransitionPending = false
            }
        }
    }
}
