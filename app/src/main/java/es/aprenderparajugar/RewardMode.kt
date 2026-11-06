package es.aprenderparajugar

import kotlin.math.roundToInt

internal enum class RewardMode {
    TIME,
    STARS;

    val isStars: Boolean get() = this == STARS
    val unitLabel: String get() = if (isStars) "estrellas" else "minutos"

    companion object {
        const val PREFERENCE_KEY = "reward_mode"
        fun fromPreferences(preferences: android.content.SharedPreferences): RewardMode =
            if (preferences.getString(PREFERENCE_KEY, "time") == "stars") STARS else TIME
    }
}

internal fun formatRewardBalance(seconds: Int, mode: RewardMode): String {
    val safe = seconds.coerceAtLeast(0)
    return if (mode.isStars) (safe / 60.0).roundToInt().toString()
    else String.format(java.util.Locale.ROOT, "%02d:%02d", safe / 60, safe % 60)
}

internal fun formatRewardAmount(seconds: Int, mode: RewardMode): String =
    if (mode.isStars) "${(seconds.coerceAtLeast(0) / 60.0).roundToInt()} ⭐"
    else String.format(java.util.Locale.ROOT, "%d:%02d min", seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)
