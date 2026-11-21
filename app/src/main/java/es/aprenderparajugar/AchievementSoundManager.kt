package es.aprenderparajugar

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal enum class AchievementSoundEvent(val key: String, val title: String, val defaultResource: Int) {
    OBJECT_ACQUIRED("object_acquired", "Objeto conseguido", R.raw.sound_object_acquired),
    COLLECTIBLE_ACQUIRED("collectible_acquired", "Coleccionable conseguido", R.raw.sound_collectible_acquired),
    STORY_COMPLETED("story_completed", "Cuento completado", R.raw.sound_story_completed),
    REWARD_GRANTED("reward_granted", "Recompensa conseguida", R.raw.sound_reward_granted),
    ITEM_PURCHASED("item_purchased", "Objeto comprado", R.raw.sound_item_purchased),
    STORY_UNLOCKED("story_unlocked", "Objeto desbloqueado en la tienda", R.raw.sound_story_unlocked)
}

internal object AchievementSoundManager {
    private const val PREFS = "aprender_para_jugar"
    private val mutex = Mutex()
    fun isEnabled(context: Context) = context.getSharedPreferences(PREFS, 0).getBoolean("achievement_sounds_enabled", true)
    fun setEnabled(context: Context, enabled: Boolean) { context.getSharedPreferences(PREFS, 0).edit().putBoolean("achievement_sounds_enabled", enabled).apply() }
    fun customPath(context: Context, event: AchievementSoundEvent): String? =
        context.getSharedPreferences(PREFS, 0).getString(pathKey(event), null)?.takeIf { File(it).isFile }
    fun setCustomPath(context: Context, event: AchievementSoundEvent, path: String) {
        context.getSharedPreferences(PREFS, 0).edit().putString(pathKey(event), path).apply()
    }
    fun restoreDefault(context: Context, event: AchievementSoundEvent) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        prefs.getString(pathKey(event), null)?.let { runCatching { File(it).delete() } }
        prefs.edit().remove(pathKey(event)).apply()
    }
    fun importAudio(context: Context, event: AchievementSoundEvent, uri: android.net.Uri): String {
        val dir = File(context.filesDir, "achievement_sounds").apply { mkdirs() }
        val target = File(dir, "${event.key}.audio")
        val temp = File(dir, "${event.key}.tmp")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "No se ha podido abrir el audio." }
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        require(temp.length() > 0) { "El archivo está vacío." }
        if (!temp.renameTo(target)) { temp.copyTo(target, true); temp.delete() }
        setCustomPath(context, event, target.absolutePath)
        return target.absolutePath
    }
    suspend fun play(context: Context, event: AchievementSoundEvent, preview: Boolean = false) {
        if (!preview && !isEnabled(context)) return
        NotificationAudioCoordinator.withNotificationFocus(
            resumeStoryAudio = event != AchievementSoundEvent.STORY_COMPLETED
        ) {
            mutex.withLock {
                suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                var player: MediaPlayer? = null
                fun complete(error: Throwable? = null) {
                    if (!finished.compareAndSet(false, true)) return
                    runCatching { player?.release() }
                    player = null
                    if (continuation.isActive) {
                        if (error == null) continuation.resumeWith(Result.success(Unit))
                        else continuation.resumeWith(Result.failure(error))
                    }
                }
                continuation.invokeOnCancellation { complete(CancellationException("Audio cancelado")) }
                try {
                    val path = customPath(context, event)
                    if (path == null) {
                        player = MediaPlayer.create(context.applicationContext, event.defaultResource)
                        if (player == null) { complete(IllegalStateException("No se pudo cargar el efecto.")); return@suspendCancellableCoroutine }
                        player?.setOnCompletionListener { complete() }
                        player?.setOnErrorListener { _, what, extra -> complete(IllegalStateException("Error de audio $what/$extra")); true }
                        player?.start()
                    } else {
                        player = MediaPlayer()
                        player?.setDataSource(path)
                        player?.setOnCompletionListener { complete() }
                        player?.setOnErrorListener { _, what, extra -> complete(IllegalStateException("Error de audio $what/$extra")); true }
                        player?.setOnPreparedListener { it.start() }
                        player?.prepareAsync()
                    }
                } catch (error: Throwable) { complete(error) }
                }
            }
        }
    }
    private fun pathKey(event: AchievementSoundEvent) = "achievement_sound_path_${event.key}"
}
