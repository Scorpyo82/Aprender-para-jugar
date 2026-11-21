package es.aprenderparajugar

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Control centralizado de cuotas por modalidad, dificultad y tipo de secuencia.
 * Cada inicio de partida consume un intento, aunque el ejercicio no llegue a completarse.
 */
internal object ExerciseQuotaRepository {
    data class QuotaStatus(
        val available: Boolean,
        val remaining: Int?,
        val limit: Int?,
        val reason: String? = null
    )

    private const val PREFIX = "quota_"
    private const val PERIOD_KEY = "quota_active_period"
    private const val CYCLE_KEY = "quota_exhausted_cycle"
    private const val RESET_LATCH_KEY = "quota_exhausted_reset_latch"

    private val difficultyIds = listOf("easy", "normal", "hard")
    private val sequenceTypes = listOf("temporal", "procesos", "causa", "ciclos")

    fun allQuotaIds(): List<String> =
        listOf("cards", "words").flatMap { modality -> difficultyIds.map { "${modality}_${it}" } } +
            sequenceTypes.flatMap { type -> difficultyIds.map { "sequences_${type}_${it}" } }

    fun id(modality: String, difficulty: String, sequenceType: String? = null): String {
        val level = when (difficulty.lowercase(Locale.ROOT)) {
            "fácil", "facil", "easy" -> "easy"
            "normal" -> "normal"
            "difícil", "dificil", "hard" -> "hard"
            else -> throw IllegalArgumentException("Dificultad no reconocida: $difficulty")
        }
        return if (modality == "sequences") {
            val type = when (sequenceType?.lowercase(Locale.ROOT)) {
                "orden temporal", "temporal" -> "temporal"
                "procesos" -> "procesos"
                "causa y consecuencia", "causa" -> "causa"
                "ciclos" -> "ciclos"
                else -> throw IllegalArgumentException("Tipo de secuencia no reconocido: $sequenceType")
            }
            "sequences_${type}_${level}"
        } else "${modality}_${level}"
    }

    private fun period(preferences: SharedPreferences): String {
        val mode = preferences.getString("quota_reset_mode", "either") ?: "either"
        if (mode == "exhausted") return "cycle_${preferences.getLong(CYCLE_KEY, 0L)}"
        val reset = preferences.getString("quota_reset_time", "00:00") ?: "00:00"
        val parts = reset.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val calendar = Calendar.getInstance().apply {
            val nowMinutes = get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
            if (nowMinutes < hour * 60 + minute) add(Calendar.DAY_OF_YEAR, -1)
        }
        val day = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(calendar.time)
        if (mode == "either") {
            val previousDay = preferences.getString("quota_either_day", "")
            if (previousDay != day) {
                preferences.edit().putString("quota_either_day", day).putLong(CYCLE_KEY, 0L).remove(RESET_LATCH_KEY).apply()
            }
            return "either_" + day + "_cycle_" + preferences.getLong(CYCLE_KEY, 0L)
        }
        return "day_" + day
    }

    private fun usedKey(id: String, period: String) = "${PREFIX}used_${period}_${id}"
    private fun unavailableKey(id: String, period: String) = "${PREFIX}unavailable_${period}_${id}"

    fun status(preferences: SharedPreferences, id: String, hasExercises: Boolean): QuotaStatus {
        val period = period(preferences)
        if (!hasExercises) {
            preferences.edit().putBoolean(unavailableKey(id, period), true).apply()
            return QuotaStatus(false, 0, configuredLimit(preferences, id), "No hay ejercicios suficientes para esta dificultad.")
        }
        if (preferences.getBoolean(unavailableKey(id, period), false)) {
            return QuotaStatus(false, 0, configuredLimit(preferences, id), "Esta dificultad ya se ha dado por consumida en la jornada actual.")
        }
        val limit = configuredLimit(preferences, id) ?: return QuotaStatus(true, null, null)
        val used = preferences.getInt(usedKey(id, period), 0).coerceAtLeast(0)
        val remaining = (limit - used).coerceAtLeast(0)
        return QuotaStatus(remaining > 0, remaining, limit, if (remaining == 0) "Has agotado los intentos de esta dificultad." else null)
    }

    fun consume(preferences: SharedPreferences, id: String, hasExercises: Boolean): QuotaStatus {
        val before = status(preferences, id, hasExercises)
        if (!before.available) return before
        if (before.limit != null) {
            val period = period(preferences)
            preferences.edit().remove(RESET_LATCH_KEY).putInt(usedKey(id, period), preferences.getInt(usedKey(id, period), 0) + 1).apply()
        }
        return status(preferences, id, hasExercises)
    }

    /**
     * En el modo "al agotar", todas las cuotas finitas sin contenido suficiente
     * también cuentan como agotadas. Se inicia un nuevo ciclo cuando existe contenido
     * jugable y todas las cuotas finitas han llegado a su límite.
     */
    fun refreshExhaustedCycle(preferences: SharedPreferences, availability: Map<String, Boolean>) {
        val mode = preferences.getString("quota_reset_mode", "either") ?: "either"
        if (mode == "daily") return
        val finite = allQuotaIds().filter { configuredLimit(preferences, it) != null }
        if (finite.isEmpty()) return
        val currentPeriod = period(preferences)
        val anyPlayable = finite.any { availability[it] == true }
        if (!anyPlayable) return
        val allConsumed = finite.all { id ->
            val unavailable = availability[id] != true || preferences.getBoolean(unavailableKey(id, currentPeriod), false)
            val limit = configuredLimit(preferences, id) ?: return@all
            unavailable || preferences.getInt(usedKey(id, currentPeriod), 0) >= limit
        }
        if (allConsumed) {
            val availableIds = finite.filter { availability[it] == true }.joinToString("|") { id -> id }
            val latch = if (mode == "exhausted") availableIds else currentPeriod + "|" + availableIds
            if (preferences.getString(RESET_LATCH_KEY, "") != latch) {
                preferences.edit()
                    .putString(RESET_LATCH_KEY, latch)
                    .putLong(CYCLE_KEY, preferences.getLong(CYCLE_KEY, 0L) + 1L)
                    .apply()
            }
        }
    }

    private fun configuredLimit(preferences: SharedPreferences, id: String): Int? {
        if (preferences.getBoolean("quota_${id}_unlimited", true)) return null
        return preferences.getInt("quota_${id}_attempts", 3).coerceIn(1, 999)
    }
}
