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
    val instructionSessionActive = remember { mutableStateOf(false) }

    val generalWelcomeKeys = listOf(
        "encouragement_cards",
        "encouragement_words",
        "encouragement_sequences",
        "encouragement_stories"
    )
    val sequenceTypeWelcomeKeys = listOf(
        "encouragement_sequences_temporal_order",
        "encouragement_sequences_processes",
        "encouragement_sequences_cause_consequence",
        "encouragement_sequences_cycles"
    )

    LaunchedEffect(
        screen, difficulty, wordSelectedDifficulty, sequenceChosenCategory,
        sequenceChosenDifficulty, sequenceType, appInForeground
    ) {
        if (screen == "home") {
            ExerciseVoiceManager.resetPlayed(
                context,
                ExerciseVoiceManager.items.filter { it.key in generalWelcomeKeys }
            )
        }
        if (screen == "sequences") {
            ExerciseVoiceManager.resetPlayed(
                context,
                ExerciseVoiceManager.items.filter { it.key in sequenceTypeWelcomeKeys }
            )
        }

        if (!appInForeground || screen == "story-player") {
            ExerciseVoiceManager.stop()
        } else {
            val previousScreen = preferences.getString("exercise_voice_last_screen", null)
            val enteredScreen = previousScreen != screen
            preferences.edit().putString("exercise_voice_last_screen", screen).apply()
            val previous = preferences.getString("exercise_voice_last_decision", null)
            val decision: String?
            val voice: ExerciseVoiceManager.VoiceItem?
            val isWelcome: Boolean

            when (screen) {
                "cards-options" -> {
                    decision = "cards|$difficulty"
                    val difficultyChanged = !enteredScreen &&
                        previous?.startsWith("cards|") == true && previous != decision
                    voice = if (difficultyChanged) {
                        ExerciseVoiceManager.items.firstOrNull {
                            it.key == "encouragement_difficulty_${difficulty.toVoiceDifficultyKey()}"
                        }
                    } else {
                        ExerciseVoiceManager.items.firstOrNull { it.key == "encouragement_cards" }
                    }
                    isWelcome = !difficultyChanged
                }
                "word-options" -> {
                    decision = "words|$wordSelectedDifficulty"
                    val difficultyChanged = !enteredScreen &&
                        previous?.startsWith("words|") == true && previous != decision
                    voice = if (difficultyChanged) {
                        ExerciseVoiceManager.items.firstOrNull {
                            it.key == "encouragement_difficulty_${wordSelectedDifficulty.toVoiceDifficultyKey()}"
                        }
                    } else {
                        ExerciseVoiceManager.items.firstOrNull { it.key == "encouragement_words" }
                    }
                    isWelcome = !difficultyChanged
                }
                "sequences" -> {
                    decision = "sequences"
                    voice = ExerciseVoiceManager.items.firstOrNull {
                        it.key == "encouragement_sequences"
                    }
                    isWelcome = true
                }
                "sequence-difficulty" -> {
                    decision = "sequence-difficulty|$sequenceChosenCategory|$sequenceChosenDifficulty"
                    val difficultyChanged = !enteredScreen &&
                        previous?.startsWith("sequence-difficulty|$sequenceChosenCategory|") == true &&
                        previous != decision
                    voice = if (difficultyChanged) {
                        ExerciseVoiceManager.items.firstOrNull {
                            it.key == "encouragement_difficulty_${sequenceChosenDifficulty.toVoiceDifficultyKey()}"
                        }
                    } else {
                        ExerciseVoiceManager.items.firstOrNull {
                            it.key == "encouragement_sequences_${sequenceChosenCategory.toVoiceKey()}"
                        }
                    }
                    isWelcome = !difficultyChanged
                }
                "stories" -> {
                    decision = "stories"
                    voice = ExerciseVoiceManager.items.firstOrNull { it.key == "encouragement_stories" }
                    isWelcome = true
                }
                else -> {
                    decision = null
                    voice = null
                    isWelcome = false
                }
            }

            if (decision != null) {
                instructionSessionActive.value = false
                if (decision != previous || enteredScreen) {
                    preferences.edit().putString("exercise_voice_last_decision", decision).apply()
                    if (voice != null) {
                        if (isWelcome) {
                            if (!ExerciseVoiceManager.hasPlayed(context, voice) &&
                                ExerciseVoiceManager.playLatest(context, voice)
                            ) {
                                ExerciseVoiceManager.markPlayed(context, voice)
                            }
                        } else {
                            ExerciseVoiceManager.playLatest(context, voice)
                        }
                    }
                }
            } else {
                val isExerciseScreen = screen == "exercise" ||
                    screen == "word-exercise" || screen == "sequence-game"
                val isResultScreen = screen == "result" || screen == "sequence-result"

                if (isExerciseScreen) {
                    if (!instructionSessionActive.value) {
                        val instruction = when (screen) {
                            "exercise" -> ExerciseVoiceManager.items.firstOrNull { it.key == "instruction_cards" }
                            "word-exercise" -> ExerciseVoiceManager.items.firstOrNull { it.key == "instruction_words" }
                            "sequence-game" -> {
                                val typeKey = sequenceType?.toVoiceKey()
                                if (typeKey == null) null
                                else ExerciseVoiceManager.items.firstOrNull { it.key == "instruction_sequences_$typeKey" }
                            }
                            else -> null
                        }
                        if (instruction != null) {
                            instructionSessionActive.value = true
                            if (ExerciseVoiceManager.shouldPlay(context, instruction)) {
                                ExerciseVoiceManager.playLatest(context, instruction)
                            }
                        }
                    }
                } else {
                    ExerciseVoiceManager.stop()
                    if (!isResultScreen) instructionSessionActive.value = false
                }
            }
        }
    }
}
