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
    // Locuciones de decisión: una nueva elección sustituye inmediatamente a la anterior.
    LaunchedEffect(
        screen, difficulty, wordSelectedDifficulty, sequenceChosenCategory, sequenceChosenDifficulty,
        appInForeground
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
            if (decision != null && decision != previous) {
                preferences.edit().putString("exercise_voice_last_decision", decision).apply()
                if (voice != null) ExerciseVoiceManager.playLatest(context, voice)
            } else if (decision == null) {
                ExerciseVoiceManager.stop()
            }
        }
    }

    // Cada instrucción se reproduce automáticamente una sola vez por modalidad/tipo.
    LaunchedEffect(screen, sequenceType, appInForeground) {
        if (appInForeground) {
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
