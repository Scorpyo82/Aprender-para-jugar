package es.aprenderparajugar

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Locuciones educativas de Tarjetas, Palabras y Secuencias.
 * No participa en los efectos de logros ni en el audio de Cuentos.
 */
internal object ExerciseVoiceManager {
    private const val PREFS = "aprender_para_jugar"
    private const val MASTER_KEY = "exercise_voice_enabled"
    private const val INSTRUCTIONS_KEY = "exercise_voice_instructions_enabled"
    private const val ENCOURAGEMENT_KEY = "exercise_voice_encouragement_enabled"
    private const val MUSIC_NORMAL_VOLUME = 0.22f
    private const val MUSIC_DUCK_VOLUME = 0.045f
    private val handler = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0L)
    private var player: MediaPlayer? = null
    private var musicPlayer: MediaPlayer? = null
    private var musicWasPlaying = false
    private var musicRestoreRunnable: Runnable? = null

    data class VoiceItem(val key: String, val title: String, val resource: Int, val group: Group) {
        val description: String
            get() = when (key) {
                "instruction_cards" -> "Explica cómo observar las imágenes y completar el ejercicio de Tarjetas."
                "instruction_words" -> "Explica cómo ordenar las letras y formar palabras."
                "instruction_sequences_temporal_order" -> "Explica cómo ordenar escenas según el momento en que suceden."
                "instruction_sequences_processes" -> "Explica cómo ordenar los pasos de un proceso."
                "instruction_sequences_cause_consequence" -> "Explica cómo relacionar una causa con su consecuencia."
                "instruction_sequences_cycles" -> "Explica cómo ordenar las fases de un ciclo que se repite."
                "encouragement_cards" -> "Mensaje de ánimo al seleccionar la modalidad Tarjetas."
                "encouragement_words" -> "Mensaje de ánimo al seleccionar la modalidad Palabras."
                "encouragement_sequences" -> "Mensaje de ánimo al seleccionar la modalidad Secuencias."
                "encouragement_stories" -> "Mensaje de bienvenida a la sección Cuentos; independiente de la narración."
                "encouragement_difficulty_easy" -> "Mensaje al elegir el nivel de dificultad fácil."
                "encouragement_difficulty_normal" -> "Mensaje al elegir el nivel de dificultad normal."
                "encouragement_difficulty_hard" -> "Mensaje al elegir el nivel de dificultad difícil."
                "encouragement_sequences_temporal_order" -> "Mensaje de ánimo al elegir Orden temporal."
                "encouragement_sequences_processes" -> "Mensaje de ánimo al elegir Procesos."
                "encouragement_sequences_cause_consequence" -> "Mensaje de ánimo al elegir Causa y consecuencia."
                "encouragement_sequences_cycles" -> "Mensaje de ánimo al elegir Ciclos."
                else -> "Locución utilizada durante la selección de esta modalidad."
            }
    }
    enum class Group { INSTRUCTIONS, ENCOURAGEMENT }

    val items: List<VoiceItem> = listOf(
        VoiceItem("instruction_cards", "Instrucciones · Tarjetas", R.raw.instruction_cards, Group.INSTRUCTIONS),
        VoiceItem("instruction_words", "Instrucciones · Palabras", R.raw.instruction_words, Group.INSTRUCTIONS),
        VoiceItem("instruction_sequences_temporal_order", "Instrucciones · Orden temporal", R.raw.instruction_sequences_temporal_order, Group.INSTRUCTIONS),
        VoiceItem("instruction_sequences_processes", "Instrucciones · Procesos", R.raw.instruction_sequences_processes, Group.INSTRUCTIONS),
        VoiceItem("instruction_sequences_cause_consequence", "Instrucciones · Causa y consecuencia", R.raw.instruction_sequences_cause_consequence, Group.INSTRUCTIONS),
        VoiceItem("instruction_sequences_cycles", "Instrucciones · Ciclos", R.raw.instruction_sequences_cycles, Group.INSTRUCTIONS),
        VoiceItem("encouragement_cards", "Tarjetas", R.raw.encouragement_cards, Group.ENCOURAGEMENT),
        VoiceItem("encouragement_words", "Palabras", R.raw.encouragement_words, Group.ENCOURAGEMENT),
        VoiceItem("encouragement_sequences", "Secuencias", R.raw.encouragement_sequences, Group.ENCOURAGEMENT),
        VoiceItem("encouragement_stories", "Cuentos", R.raw.encouragement_stories, Group.ENCOURAGEMENT),
        VoiceItem("encouragement_difficulty_easy", "Dificultad fácil", R.raw.encouragement_difficulty_easy, Group.ENCOURAGEMENT),
        VoiceItem("encouragement_difficulty_normal", "Dificultad normal", R.raw.encouragement_difficulty_normal, Group.ENCOURAGEMENT),
        VoiceItem("encouragement_difficulty_hard", "Dificultad difícil", R.raw.encouragement_difficulty_hard, Group.ENCOURAGEMENT),
        VoiceItem("encouragement_sequences_temporal_order", "Orden temporal", R.raw.encouragement_sequences_temporal_order, Group.ENCOURAGEMENT),
        VoiceItem("encouragement_sequences_processes", "Procesos", R.raw.encouragement_sequences_processes, Group.ENCOURAGEMENT),
        VoiceItem("encouragement_sequences_cause_consequence", "Causa y consecuencia", R.raw.encouragement_sequences_cause_consequence, Group.ENCOURAGEMENT),
        VoiceItem("encouragement_sequences_cycles", "Ciclos", R.raw.encouragement_sequences_cycles, Group.ENCOURAGEMENT)
    )

    fun isMasterEnabled(context: Context) = prefs(context).getBoolean(MASTER_KEY, true)
    fun setMasterEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(MASTER_KEY, enabled).apply()
    fun isGroupEnabled(context: Context, group: Group) = prefs(context).getBoolean(groupKey(group), true)
    fun setGroupEnabled(context: Context, group: Group, enabled: Boolean) = prefs(context).edit().putBoolean(groupKey(group), enabled).apply()
    fun isItemEnabled(context: Context, item: VoiceItem) = prefs(context).getBoolean(itemKey(item), true)
    fun setItemEnabled(context: Context, item: VoiceItem, enabled: Boolean) = prefs(context).edit().putBoolean(itemKey(item), enabled).apply()
    fun shouldPlay(context: Context, item: VoiceItem): Boolean =
        isMasterEnabled(context) && isGroupEnabled(context, item.group) && isItemEnabled(context, item)

    fun hasPlayedInstruction(context: Context, item: VoiceItem): Boolean =
        prefs(context).getBoolean(firstPlayedKey(item), false)

    fun markInstructionPlayed(context: Context, item: VoiceItem): Boolean {
        val preferences = prefs(context)
        if (preferences.getBoolean(firstPlayedKey(item), false)) return false
        preferences.edit().putBoolean(firstPlayedKey(item), true).apply()
        return true
    }

    fun customPath(context: Context, item: VoiceItem): String? =
        prefs(context).getString(customKey(item), null)?.takeIf { File(it).isFile && File(it).length() > 0L }

    fun importAudio(context: Context, item: VoiceItem, uri: android.net.Uri) {
        val directory = File(context.filesDir, "exercise_voice_audio").apply { mkdirs() }
        val target = File(directory, "${item.key}.audio")
        val temp = File(directory, "${item.key}.tmp")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "No se ha podido abrir el audio seleccionado." }
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        require(temp.length() > 0L) { "El archivo está vacío." }
        if (!temp.renameTo(target)) { temp.copyTo(target, true); temp.delete() }
        prefs(context).edit().putString(customKey(item), target.absolutePath).apply()
    }

    fun setRecordedAudio(context: Context, item: VoiceItem, file: File) {
        require(file.isFile && file.length() > 0L) { "La grabación está vacía." }
        val directory = File(context.filesDir, "exercise_voice_audio").apply { mkdirs() }
        val target = File(directory, "${item.key}.m4a")
        if (file.absolutePath != target.absolutePath) {
            if (!file.renameTo(target)) { file.copyTo(target, true); file.delete() }
        }
        prefs(context).edit().putString(customKey(item), target.absolutePath).apply()
    }

    fun restoreDefault(context: Context, item: VoiceItem) {
        customPath(context, item)?.let { runCatching { File(it).delete() } }
        prefs(context).edit().remove(customKey(item)).apply()
    }

    fun setBackgroundMusicPlayer(value: MediaPlayer?) {
        if (musicPlayer !== value) {
            musicRestoreRunnable?.let(handler::removeCallbacks)
            musicRestoreRunnable = null
            musicPlayer = value
            musicWasPlaying = false
        }
        if (value != null && player != null) duckMusic()
    }

    /** Starts immediately; a newer call always releases and replaces the previous voice. */
    fun playLatest(context: Context, item: VoiceItem, preview: Boolean = false): Boolean {
        if (!preview && !shouldPlay(context, item)) return false
        val token = generation.incrementAndGet()
        musicRestoreRunnable?.let(handler::removeCallbacks)
        musicRestoreRunnable = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        duckMusic()
        try {
            val path = customPath(context, item)
            val next = if (path == null) {
                MediaPlayer.create(context.applicationContext, item.resource)
            } else {
                MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                }
            }
            if (next == null) {
                restoreMusic(token)
                return false
            }
            player = next
            next.setOnCompletionListener {
                if (generation.get() == token) {
                    runCatching { it.release() }
                    player = null
                    restoreMusic(token)
                }
            }
            next.setOnErrorListener { failed, _, _ ->
                if (generation.get() == token) {
                    runCatching { failed.release() }
                    player = null
                    restoreMusic(token)
                }
                true
            }
            next.start()
            return true
        } catch (_: Exception) {
            runCatching { player?.release() }
            player = null
            restoreMusic(token)
            return false
        }
    }

    fun stop() {
        val token = generation.incrementAndGet()
        musicRestoreRunnable?.let(handler::removeCallbacks)
        musicRestoreRunnable = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        restoreMusic(token)
    }

    private fun duckMusic() {
        val music = musicPlayer ?: return
        musicRestoreRunnable?.let(handler::removeCallbacks)
        musicRestoreRunnable = null
        musicWasPlaying = musicWasPlaying || runCatching { music.isPlaying }.getOrDefault(false)
        if (musicWasPlaying) runCatching { music.setVolume(MUSIC_DUCK_VOLUME, MUSIC_DUCK_VOLUME) }
    }

    private fun restoreMusic(token: Long) {
        if (generation.get() != token) return
        val music = musicPlayer ?: run { musicWasPlaying = false; return }
        if (!musicWasPlaying) return
        var step = 0
        val restore = object : Runnable {
            override fun run() {
                if (generation.get() != token || musicPlayer !== music) return
                step++
                val volume = MUSIC_DUCK_VOLUME + (MUSIC_NORMAL_VOLUME - MUSIC_DUCK_VOLUME) * step / 8f
                runCatching { music.setVolume(volume, volume) }
                if (step < 8) handler.postDelayed(this, 45L) else {
                    musicWasPlaying = false
                    musicRestoreRunnable = null
                }
            }
        }
        musicRestoreRunnable = restore
        handler.postDelayed(restore, 45L)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun groupKey(group: Group) = if (group == Group.INSTRUCTIONS) INSTRUCTIONS_KEY else ENCOURAGEMENT_KEY
    private fun itemKey(item: VoiceItem) = "exercise_voice_item_${item.key}_enabled"
    private fun customKey(item: VoiceItem) = "exercise_voice_custom_path_${item.key}"
    private fun firstPlayedKey(item: VoiceItem) = "exercise_voice_first_played_v2_${item.key}"
}

internal fun String.toVoiceKey(): String = when (trim().lowercase()) {
    "orden temporal" -> "temporal_order"
    "procesos" -> "processes"
    "causa y consecuencia" -> "cause_consequence"
    "ciclos" -> "cycles"
    else -> trim().lowercase().replace(" ", "_")
}

internal fun String.toVoiceDifficultyKey(): String = when (trim().lowercase()) {
    "fácil", "facil" -> "easy"
    "normal" -> "normal"
    "difícil", "dificil" -> "hard"
    else -> trim().lowercase()
}
