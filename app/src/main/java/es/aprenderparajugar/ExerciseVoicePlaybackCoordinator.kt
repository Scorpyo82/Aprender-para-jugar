package es.aprenderparajugar

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Coordina las locuciones educativas asociadas a la navegación por los ejercicios.
 * La reproducción y la sustitución de audios siguen siendo responsabilidad de
 * ExerciseVoiceManager.
 */
@Composable
internal fun ExerciseVoicePlaybackCoordinator(
    context: Context,
    preferences: SharedPreferences,
    screen: String,
    difficulty: String,
    wordSelectedDifficulty: String,
    sequenceChosenCategory: String,
    sequenceChosenDifficulty: String,
    sequenceType: String?,
    appInForeground: Boolean
) {
    // La marca vive únicamente mientras esta instancia de la aplicación mantiene
    // abierta la sesión de ejercicios; nunca se persiste entre sesiones.
    val instructionSessionActive = remember { mutableStateOf(false) }

    // Un único efecto ordena las locuciones de navegación y las instrucciones.
    // Así, el stop() al abandonar un menú no puede cancelar la instrucción que
    // acaba de iniciarse al entrar en el ejercicio.
    LaunchedEffect(
        screen, difficulty, wordSelectedDifficulty, sequenceChosenCategory,
        sequenceChosenDifficulty, appInForeground
    ) {
        val previousScreen = preferences.getString("exercise_voice_last_screen", null)
        val enteredScreen = previousScreen != screen
        preferences.edit().putString("exercise_voice_last_screen", screen).apply()

        if (!appInForeground || screen == "story-player") {
            ExerciseVoiceManager.stop()
        } else {
            val previous = preferences.getString("exercise_voice_last_decision", null)
            val decision: String?
            val voice: ExerciseVoiceManager.VoiceItem?
            when (screen) {
                "cards-options" -> {
                    decision = "cards|$difficulty"
                    voice = if (previous?.startsWith("cards|") == true)
                        ExerciseVoiceManager.items.firstOrNull { it.key == "encouragement_difficulty_${difficulty.toVoiceDifficultyKey()}" }
                    else ExerciseVoiceManager.items.firstOrNull { it.key == "encouragement_cards" }
                }
                "word-options" -> {
                    decision = "words|$wordSelectedDifficulty"
                    voice = if (previous?.startsWith("words|") == true)
                        ExerciseVoiceManager.items.firstOrNull { it.key == "encouragement_difficulty_${wordSelectedDifficulty.toVoiceDifficultyKey()}" }
                    else ExerciseVoiceManager.items.firstOrNull { it.key == "encouragement_words" }
                }
                "sequences" -> {
                    decision = "sequence-type|$sequenceChosenCategory"
                    voice = if (previous?.startsWith("sequence-type|") == true)
                        ExerciseVoiceManager.items.firstOrNull { it.key == "encouragement_sequences_${sequenceChosenCategory.toVoiceKey()}" }
                    else ExerciseVoiceManager.items.firstOrNull { it.key == "encouragement_sequences" }
                }
                "sequence-difficulty" -> {
                    decision = "sequence-difficulty|$sequenceChosenCategory|$sequenceChosenDifficulty"
                    voice = if (previous?.startsWith("sequence-difficulty|$sequenceChosenCategory|") == true)
                        ExerciseVoiceManager.items.firstOrNull { it.key == "encouragement_difficulty_${sequenceChosenDifficulty.toVoiceDifficultyKey()}" }
                    else ExerciseVoiceManager.items.firstOrNull { it.key == "encouragement_sequences_${sequenceChosenCategory.toVoiceKey()}" }
                }
                "stories" -> {
                    decision = "stories"
                    voice = ExerciseVoiceManager.items.firstOrNull { it.key == "encouragement_stories" }
                }
                else -> { decision = null; voice = null }
            }

            if (decision != null) {
                // Volver a un menú de selección cierra la sesión anterior.
                instructionSessionActive.value = false
                if (decision != previous || enteredScreen) {
                    preferences.edit().putString("exercise_voice_last_decision", decision).apply()
                    if (voice != null) ExerciseVoiceManager.playLatest(context, voice)
                }
            } else {
                val isExerciseScreen = screen == "exercise" ||
                    screen == "word-exercise" || screen == "sequence-game"
                val isResultScreen = screen == "result" || screen == "sequence-result"

                if (isExerciseScreen) {
                    if (!instructionSessionActive.value) {
                        // Abrir una tarea inicia una sesión; los siguientes ejercicios
                        // y la pantalla de resultados pertenecen a esa misma sesión.
                        instructionSessionActive.value = true
                        val instruction = when (screen) {
                            "exercise" -> ExerciseVoiceManager.items.firstOrNull { it.key == "instruction_cards" }
                            "word-exercise" -> ExerciseVoiceManager.items.firstOrNull { it.key == "instruction_words" }
                            "sequence-game" -> {
                                val typeKey = sequenceType?.toVoiceKey()
                                ExerciseVoiceManager.items.firstOrNull { it.key == "instruction_sequences_$typeKey" }
                            }
                            else -> null
                        }
                        if (instruction != null && ExerciseVoiceManager.shouldPlay(context, instruction)) {
                            ExerciseVoiceManager.playLatest(context, instruction)
                        }
                    }
                } else {
                    ExerciseVoiceManager.stop()
                    // Los resultados no cierran la sesión: «Siguiente ejercicio»
                    // debe conservarla. Cualquier menú previo sí la cierra.
                    if (!isResultScreen) instructionSessionActive.value = false
                }
            }
        }
    }
}
