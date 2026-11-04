package es.aprenderparajugar

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** Estado y reglas de selección/validación de una partida de Palabras. */
internal class WordGameState {
    val target = mutableStateOf<PairItem?>(null)
    val letterOrder = mutableStateOf(emptyList<Int>())
    val placedLetters = mutableStateOf(emptyList<Int>())
    val wrongAnswers = mutableIntStateOf(0)
    val message = mutableStateOf("")
    val selectedDifficulty = mutableStateOf("Fácil")
    val currentCategory = mutableStateOf<String?>(null)
    val startMessage = mutableStateOf("")
    val categoryCandidates = mutableStateOf(emptyList<String>())

    data class LetterResult(
        val correct: Boolean,
        val completed: Boolean,
        val wrongAnswers: Int
    )

    fun start(
        exercises: List<PairItem>,
        disabledCategories: Set<String>,
        category: String?
    ): Boolean {
        currentCategory.value = category
        val level = selectedDifficulty.value
        val eligible = exercises.filter {
            it.active &&
                it.category !in disabledCategories &&
                it.difficulty == level &&
                (category == null || it.category == category)
        }
        if (eligible.isEmpty()) {
            target.value = null
            startMessage.value = if (category == null) {
                "No hay ejercicios activos de Palabras con dificultad $level. Prueba otra dificultad o crea/activa ejercicios en Configuración > Palabras."
            } else {
                "No hay ejercicios activos de la categoría $category con dificultad $level. Prueba otra dificultad o elige otra categoría."
            }
            return false
        }

        val chosen = eligible.random()
        target.value = chosen
        letterOrder.value = chosen.word.indices.toList().shuffled().let { order ->
            if (chosen.word.length > 1 && order == chosen.word.indices.toList()) order.drop(1) + order.first()
            else order
        }
        placedLetters.value = emptyList()
        wrongAnswers.intValue = 0
        message.value = ""
        startMessage.value = ""
        return true
    }

    fun chooseLetter(letterIndex: Int): LetterResult? {
        val word = target.value?.word ?: return null
        if (letterIndex in placedLetters.value || placedLetters.value.size >= word.length) return null
        val expected = word.getOrNull(placedLetters.value.size)
        val chosen = word.getOrNull(letterIndex)
        if (expected != null && chosen != null && expected.equals(chosen, ignoreCase = true)) {
            val next = placedLetters.value + letterIndex
            placedLetters.value = next
            val completed = next.size == word.length
            message.value = if (completed) "¡Palabra completada!" else "¡Bien! Sigue con la siguiente letra."
            return LetterResult(true, completed, wrongAnswers.intValue)
        }
        wrongAnswers.intValue += 1
        message.value = "Prueba con otra letra."
        return LetterResult(false, false, wrongAnswers.intValue)
    }

    fun resetExercise() {
        target.value = null
        placedLetters.value = emptyList()
        message.value = ""
    }
}
