package es.aprenderparajugar

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** Estado y evaluación de las partidas de emparejar Tarjetas. */
internal class CardsGameState {
    val activePairs = mutableStateOf<List<PairItem>>(emptyList())
    val selectedWord = mutableStateOf<Map<String, String>>(emptyMap())
    val selectedPicture = mutableStateOf("")
    val evaluatedPictures = mutableStateOf<Map<String, Boolean>>(emptyMap())
    val resolvedWords = mutableStateOf<Set<String>>(emptySet())
    val correctCount = mutableIntStateOf(0)
    val feedback = mutableStateOf("")

    data class Resolution(
        val isCorrect: Boolean,
        val learningStats: Map<String, WordLearning>,
        val learningTurn: Int,
        val correctCount: Int,
        val resolvedCount: Int
    )

    fun begin(pairs: List<PairItem>) {
        activePairs.value = pairs
        resolvedWords.value = emptySet()
        selectedWord.value = emptyMap()
        selectedPicture.value = ""
        evaluatedPictures.value = emptyMap()
        correctCount.intValue = 0
        feedback.value = ""
    }

    fun resolve(
        word: String,
        picture: String,
        pairs: List<PairItem>,
        currentStats: Map<String, WordLearning>,
        currentTurn: Int
    ): Resolution {
        val isCorrect = pairs.first { it.word == word }.picture == picture
        val nextResolved = resolvedWords.value + word
        resolvedWords.value = nextResolved
        val nextTurn = currentTurn + 1
        val previous = currentStats[word] ?: WordLearning()
        val nextStats = currentStats + (
            word to WordLearning(
                if (isCorrect) previous.streak + 1 else 0,
                previous.lastShownTurn,
                isCorrect
            )
        )
        val nextCorrect = correctCount.intValue + if (isCorrect) 1 else 0
        correctCount.intValue = nextCorrect
        evaluatedPictures.value = evaluatedPictures.value + (picture to isCorrect)
        feedback.value = if (isCorrect) {
            "¡Muy bien! «$word» corresponde a ese dibujo."
        } else {
            "No coincide. «$word» queda resuelta; prueba con otra palabra."
        }
        selectedWord.value = emptyMap()
        selectedPicture.value = ""
        return Resolution(isCorrect, nextStats, nextTurn, nextCorrect, nextResolved.size)
    }

    fun clearRound() {
        activePairs.value = emptyList()
        resolvedWords.value = emptySet()
        selectedWord.value = emptyMap()
        selectedPicture.value = ""
        evaluatedPictures.value = emptyMap()
        correctCount.intValue = 0
        feedback.value = ""
    }
}
