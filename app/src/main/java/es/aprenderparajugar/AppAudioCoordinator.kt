package es.aprenderparajugar

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Estado único de la música de fondo y del control global de sonido de la aplicación.
 * Se mantiene en AprenderApp para que las pantallas puedan leerlo y modificarlo.
 */
internal class AppAudioState(
    context: Context,
    firstLaunchNeedsSetup: Boolean
) {
    var musicUri by mutableStateOf(
        context.getSharedPreferences("aprender_para_jugar", Context.MODE_PRIVATE)
            .getString("background_music_uri", null)
    )
    var musicMessage by mutableStateOf("")
    var soundEnabled by mutableStateOf(
        if (firstLaunchNeedsSetup) false else context.getSharedPreferences(
            "aprender_para_jugar",
            Context.MODE_PRIVATE
        ).getBoolean("sound_enabled", true)
    )

    init {
        if (firstLaunchNeedsSetup) {
            context.getSharedPreferences("aprender_para_jugar", Context.MODE_PRIVATE)
                .edit().putBoolean("sound_enabled", false).apply()
        }
    }
}

/**
 * Centraliza los coordinadores de audio de la aplicación: música de fondo,
 * locuciones educativas y activación inicial del sonido.
 */
@Composable
internal fun AppAudioCoordinator(
    state: AppAudioState,
    preferences: SharedPreferences,
    screen: String,
    firstLaunchNeedsSetup: Boolean,
    onFirstLaunchSetupComplete: () -> Unit,
    appInForeground: Boolean,
    context: Context,
    difficulty: String,
    wordSelectedDifficulty: String,
    sequenceChosenCategory: String,
    sequenceChosenDifficulty: String,
    sequenceType: String?
) {
    BackgroundMusicController(
        musicUri = state.musicUri,
        soundEnabled = state.soundEnabled,
        appInForeground = appInForeground,
        screen = screen,
        onError = { state.musicMessage = it }
    )

    ExerciseVoicePlaybackCoordinator(
        context = context,
        preferences = preferences,
        screen = screen,
        difficulty = difficulty,
        wordSelectedDifficulty = wordSelectedDifficulty,
        sequenceChosenCategory = sequenceChosenCategory,
        sequenceChosenDifficulty = sequenceChosenDifficulty,
        sequenceType = sequenceType,
        appInForeground = appInForeground
    )

    LaunchedEffect(screen) {
        if (screen == "home" && firstLaunchNeedsSetup) {
            state.soundEnabled = true
            preferences.edit()
                .putBoolean("sound_enabled", true)
                .putBoolean("first_home_sound_activation_done", true)
                .putBoolean("first_launch_setup_seen", true)
                .apply()
            onFirstLaunchSetupComplete()
        }
    }
}
