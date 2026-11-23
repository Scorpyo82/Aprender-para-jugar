package es.aprenderparajugar

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

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
    // Un único efecto ordena las locuciones de navegación y las instrucciones.
    // Así, el stop() al abandonar un menú no puede cancelar la instrucción que
    // acaba de iniciarse al entrar en el ejercicio.
    LaunchedEffect(
        screen, difficulty, wordSelectedDifficulty, sequenceChosenCategory,
        sequenceChosenDifficulty, sequenceType, appInForeground
    ) {
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
                else -> { decision = null; voice = null }
            }

            if (decision != null) {
                if (decision != previous) {
                    preferences.edit().putString("exercise_voice_last_decision", decision).apply()
                    if (voice != null) ExerciseVoiceManager.playLatest(context, voice)
                }
            } else {
                // Al entrar en un ejercicio, detener el mensaje de selección anterior
                // antes de intentar reproducir la instrucción correspondiente.
                ExerciseVoiceManager.stop()
                val instruction = when (screen) {
                    "exercise" -> ExerciseVoiceManager.items.firstOrNull { it.key == "instruction_cards" }
                    "word-exercise" -> ExerciseVoiceManager.items.firstOrNull { it.key == "instruction_words" }
                    "sequence-game" -> {
                        val typeKey = sequenceType?.toVoiceKey()
                        ExerciseVoiceManager.items.firstOrNull { it.key == "instruction_sequences_$typeKey" }
                    }
                    else -> null
                }
                if (instruction != null && ExerciseVoiceManager.shouldPlay(context, instruction) &&
                    !ExerciseVoiceManager.hasPlayedInstruction(context, instruction) &&
                    ExerciseVoiceManager.playLatest(context, instruction)
                ) ExerciseVoiceManager.markInstructionPlayed(context, instruction)
            }
        }
    }
}
