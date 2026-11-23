package es.aprenderparajugar

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.lazy.LazyListScope

internal fun LazyListScope.libraryDeleteItems(
    context: Context,
    preferences: SharedPreferences,
    stories: List<StoredStory>,
    objects: List<StoredObject>,
    dependencySnapshot: StoryObjectDependencySnapshot?,
    selectedItems: Set<String>,
    expandedCategories: Set<String>,
    confirmArmed: Boolean,
    onSelectionChanged: (Set<String>) -> Unit,
    onExpandedCategoriesChanged: (Set<String>) -> Unit,
    onConfirmArmedChanged: (Boolean) -> Unit,
    onDeleteConfirmed: () -> Unit
) {
    val modalityCategories = ContentStorage.Modality.entries.associateWith { modality ->
        val exercises = ExerciseRepository.list(context, modality)
        val prefKey = when (modality) {
            ContentStorage.Modality.TARJETAS -> "categories"
            ContentStorage.Modality.PALABRAS -> "letter_word_categories_v1"
            ContentStorage.Modality.SECUENCIAS -> "sequence_categories_v1"
        }
        val registered = (preferences.getString(prefKey, "") ?: "").split("|").filter { it.isNotBlank() }
        (registered + exercises.map { it.metadata.category })
            .distinctBy { it.trim().lowercase() }
            .sortedBy { it.lowercase() }
            .map { category -> category to exercises.filter { it.metadata.category.equals(category, true) } }
    }
    LibraryDeleteScreen(
        modalityCategories = modalityCategories,
        stories = stories,
        objects = objects,
        dependencySnapshot = dependencySnapshot,
        selectedItems = selectedItems,
        expandedCategories = expandedCategories,
        confirmArmed = confirmArmed,
        onSelectionChanged = onSelectionChanged,
        onExpandedCategoriesChanged = onExpandedCategoriesChanged,
        onConfirmArmedChanged = onConfirmArmedChanged,
        onDeleteConfirmed = onDeleteConfirmed
    )
}
