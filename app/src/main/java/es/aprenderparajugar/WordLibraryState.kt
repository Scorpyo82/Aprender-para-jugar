package es.aprenderparajugar

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * Estado, validación y persistencia de la biblioteca de Palabras.
 * La interfaz y la navegación permanecen en MainActivity.
 */
internal class WordLibraryState(
    private val context: Context,
    private val preferences: SharedPreferences
) {
    val exercises = mutableStateOf(ExerciseUiStorage.loadPairs(context, ContentStorage.Modality.PALABRAS))
    val categories = mutableStateOf(
        (preferences.getString(CATEGORIES_KEY, "") ?: "")
            .split("|").map { it.trim() }.filter { it.isNotBlank() }.distinct()
    )
    val disabledCategories = mutableStateOf(
        preferences.getStringSet(DISABLED_CATEGORIES_KEY, emptySet())?.toSet() ?: emptySet()
    )

    val page = mutableStateOf("list")
    val editingIndex = mutableIntStateOf(-1)
    val wordDraft = mutableStateOf("")
    val pictureDraft = mutableStateOf("")
    val categoryDraft = mutableStateOf("")
    val difficultyDraft = mutableStateOf("Fácil")
    val message = mutableStateOf("")
    val categoryMenuExpanded = mutableStateOf(false)
    val filterCategory = mutableStateOf("Todas")
    val filterDifficulty = mutableStateOf("Todas")
    val filterStatus = mutableStateOf("Todos")
    val newCategoryDraft = mutableStateOf("")
    val categoryToDelete = mutableStateOf<String?>(null)

    fun saveExercises(updated: List<PairItem>): List<PairItem> =
        ExerciseUiStorage.savePairs(context, ContentStorage.Modality.PALABRAS, updated).also {
            exercises.value = it
        }

    fun reloadExercises(): List<PairItem> =
        ExerciseUiStorage.loadPairs(context, ContentStorage.Modality.PALABRAS).also {
            exercises.value = it
        }

    fun saveCategories(updated: List<String>) {
        categories.value = updated
        preferences.edit().putString(CATEGORIES_KEY, updated.joinToString("|")).apply()
    }

    fun mergeImportedCategories(imported: List<String>, replace: Boolean) {
        val updated = if (replace) imported.distinctBy { it.trim().lowercase() }
        else (categories.value + imported).map { it.trim() }
            .filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        saveCategories(updated)
    }

    fun setCategoryEnabled(category: String, enabled: Boolean) {
        disabledCategories.value = if (enabled) {
            disabledCategories.value - category
        } else {
            disabledCategories.value + category
        }
        preferences.edit().putStringSet(DISABLED_CATEGORIES_KEY, disabledCategories.value).apply()
    }

    fun createCategory(name: String): String? {
        val cleanName = name.trim()
        return when {
            cleanName.isBlank() -> "Escribe un nombre."
            categories.value.any { it.equals(cleanName, true) } -> "Esa categoría ya existe."
            else -> {
                saveCategories(categories.value + cleanName)
                newCategoryDraft.value = ""
                null
            }
        }
    }

    fun deleteCategoryAndExercises(category: String) {
        saveExercises(exercises.value.filterNot { it.category == category })
        saveCategories(categories.value.filterNot { it == category })
        disabledCategories.value = disabledCategories.value - category
        preferences.edit().putStringSet(DISABLED_CATEGORIES_KEY, disabledCategories.value).apply()
        if (filterCategory.value == category) filterCategory.value = "Todas"
        categoryToDelete.value = null
    }

    fun reassignCategory(category: String, destination: String) {
        saveExercises(exercises.value.map {
            if (it.category == category) it.copy(category = destination) else it
        })
        saveCategories(categories.value.filterNot { it == category })
        if (category in disabledCategories.value) {
            disabledCategories.value = disabledCategories.value - category
            preferences.edit().putStringSet(DISABLED_CATEGORIES_KEY, disabledCategories.value).apply()
        }
        if (filterCategory.value == category) filterCategory.value = "Todas"
        categoryToDelete.value = null
    }

    companion object {
        private const val CATEGORIES_KEY = "letter_word_categories_v1"
        private const val DISABLED_CATEGORIES_KEY = "disabled_letter_word_categories_v1"
    }
}
