package es.aprenderparajugar

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Estado de la biblioteca de Secuencias.
 *
 * Mantiene separado de AprenderApp el estado observable de la biblioteca,
 * sus filtros y las operaciones de recarga/guardado. La persistencia sigue
 * delegada en ExerciseUiStorage y ExerciseRepository.
 */
internal class SequenceLibraryState(
    private val context: Context,
    private val preferences: SharedPreferences
) {
    val exercises: MutableState<List<SequenceExercise>> =
        mutableStateOf(ExerciseUiStorage.loadSequences(context))

    val categoryNames: MutableState<List<String>> = mutableStateOf(
        (preferences.getString("sequence_categories_v1", null) ?: "")
            .split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    )

    val categoryMessage = mutableStateOf("")
    val categoryToDelete = mutableStateOf<String?>(null)
    val disabledCategories: MutableState<Set<String>> = mutableStateOf(
        preferences.getStringSet("disabled_sequence_categories", emptySet())?.toSet() ?: emptySet()
    )

    val filterType = mutableStateOf("Todos")
    val filterCategory = mutableStateOf("Todas")
    val filterDifficulty = mutableStateOf("Todas")
    val filterStatus = mutableStateOf("Todos")
    val filterCategoryExpanded = mutableStateOf(false)
    val newCategoryDraft = mutableStateOf("")

    /** Guarda la biblioteca y actualiza el estado con las rutas locales resultantes. */
    fun saveExercises(exercises: List<SequenceExercise>): List<SequenceExercise> =
        ExerciseUiStorage.saveSequences(context, exercises).also { saved ->
            this.exercises.value = saved
        }

    /** Recarga la biblioteca desde el repositorio persistente. */
    fun reloadExercises(): List<SequenceExercise> =
        ExerciseUiStorage.loadSequences(context).also { loaded ->
            exercises.value = loaded
        }

    /** Incorpora categorías encontradas en una importación sin eliminar las existentes. */
    fun mergeCategories(categories: Collection<String>): List<String> {
        val merged = (categoryNames.value + categories)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        categoryNames.value = merged
        preferences.edit().putString("sequence_categories_v1", merged.joinToString("|")).apply()
        return merged
    }

    /** Persiste los nombres actuales de categoría. */
    fun persistCategories() {
        preferences.edit()
            .putString("sequence_categories_v1", categoryNames.value.joinToString("|"))
            .apply()
    }

    /** Persiste las categorías que no deben ofrecerse en las partidas. */
    fun persistDisabledCategories() {
        preferences.edit()
            .putStringSet("disabled_sequence_categories", disabledCategories.value)
            .apply()
    }
}
