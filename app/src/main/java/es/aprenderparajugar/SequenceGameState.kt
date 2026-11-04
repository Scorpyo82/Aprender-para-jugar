package es.aprenderparajugar

import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Estado y reglas de interacción de una partida de Secuencias.
 *
 * La clase no controla la navegación ni concede recompensas: comunica cuándo
 * se completa una partida para que el coordinador aplique el flujo común.
 */
internal class SequenceGameState(
    private val preferences: SharedPreferences
) {
    val playingExercise = mutableStateOf<SequenceExercise?>(null)
    val playOrder = mutableStateOf(emptyList<Int>())
    val selectedOrder = mutableStateOf(emptyList<Int>())
    val wrongAnswers = mutableIntStateOf(0)
    val playMessage = mutableStateOf("")
    val chosenCategory = mutableStateOf("")
    val chosenDifficulty = mutableStateOf("")

    fun eligibleExercises(
        exercises: List<SequenceExercise>,
        type: String,
        maxCards: Int,
        disabledCategories: Set<String>,
        level: String? = null
    ): List<SequenceExercise> = exercises.filter { exercise ->
        exercise.active &&
            exercise.cards.size <= maxCards &&
            exercise.category !in disabledCategories &&
            exercise.type.equals(type, ignoreCase = true) &&
            (level == null || exercise.difficulty.equals(level, ignoreCase = true))
    }

    fun startExercise(
        exercises: List<SequenceExercise>,
        type: String,
        level: String,
        maxCards: Int,
        disabledCategories: Set<String>
    ): Boolean {
        val candidates = eligibleExercises(exercises, type, maxCards, disabledCategories, level)
        if (candidates.isEmpty()) {
            playMessage.value = "No hay ejercicios activos de $type con dificultad $level y un máximo de $maxCards tarjetas. Revisa los ejercicios disponibles o modifica la configuración."
            return false
        }

        val leastCompleted = candidates.minOf { preferences.getInt(dailyUsagePreferenceKey(it), 0) }
        val leastUsed = candidates.filter {
            preferences.getInt(dailyUsagePreferenceKey(it), 0) == leastCompleted
        }
        val exercise = leastUsed.random()
        playingExercise.value = exercise
        val indexes = exercise.cards.indices.toList()
        val shuffled = indexes.shuffled()
        playOrder.value = if (shuffled.size > 1 && shuffled == indexes) {
            shuffled.drop(1) + shuffled.first()
        } else {
            shuffled
        }
        selectedOrder.value = emptyList()
        wrongAnswers.intValue = 0
        playMessage.value = "Busca la primera tarjeta de la secuencia."
        return true
    }

    /**
     * Procesa una elección. Devuelve true únicamente cuando se ha completado
     * correctamente toda la secuencia.
     */
    fun selectCard(exercise: SequenceExercise, cardIndex: Int): Boolean {
        val currentOrder = selectedOrder.value
        if (cardIndex in currentOrder || currentOrder.size >= exercise.cards.size) return false

        val expectedIndex = currentOrder.size
        val validOrders = listOf(exercise.cards.indices.toList()) + exercise.alternatives.mapNotNull { line ->
            line.split(",").map { it.trim().toIntOrNull()?.minus(1) }.takeIf { values ->
                values.size == exercise.cards.size &&
                    values.all { it != null } &&
                    values.filterNotNull().distinct().size == exercise.cards.size
            }?.map { it!! }
        }
        val isCorrectChoice = validOrders.any { order ->
            order.take(expectedIndex) == currentOrder && order.getOrNull(expectedIndex) == cardIndex
        }

        if (!isCorrectChoice) {
            wrongAnswers.intValue += 1
            playMessage.value = "No es esa todavía. Busca el paso que ocurre antes."
            return false
        }

        val nextOrder = currentOrder + cardIndex
        selectedOrder.value = nextOrder
        if (nextOrder.size == exercise.cards.size) {
            playMessage.value = "¡Secuencia completada!"
            return true
        }
        playMessage.value = "¡Correcto! ¿Qué ocurre después?"
        return false
    }

    fun dailyUsagePreferenceKey(exercise: SequenceExercise): String {
        val today = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
        val identity = listOf(
            exercise.type,
            exercise.title,
            exercise.category,
            exercise.difficulty,
            exercise.cards.joinToString("\u001F"),
            exercise.pictures.joinToString("\u001F")
        ).joinToString("\u001E")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return "sequence_completed_count_${today}_${digest}"
    }

    fun resetSelection() {
        selectedOrder.value = emptyList()
        playMessage.value = ""
    }
}
