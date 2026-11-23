package es.aprenderparajugar

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun launchLibraryDeletion(
    scope: CoroutineScope,
    context: Context,
    preferences: SharedPreferences,
    selectedItems: Set<String>,
    onConfirmArmedChanged: (Boolean) -> Unit,
    onMessageChanged: (String) -> Unit,
    onParentSectionChanged: (String) -> Unit,
    onCategoryNamesChanged: (List<String>) -> Unit,
    onSaveWordCategories: (List<String>) -> Unit,
    onSequenceCategoriesChanged: (List<String>) -> Unit,
    onPairsChanged: (List<PairItem>) -> Unit,
    onReloadWordExercises: () -> Unit,
    onReloadSequenceExercises: () -> Unit,
    onStoriesChanged: (List<StoredStory>) -> Unit,
    onObjectsChanged: (List<StoredObject>) -> Unit,
    onResetSelection: () -> Unit
) {
    scope.launch {
        try {
            val selectedStoryPaths = selectedItems
                .filter { it.startsWith("T|") }
                .map { it.removePrefix("T|") }
                .toSet()
            val selectedObjectFolders = selectedItems
                .filter { it.startsWith("O|") }
                .map { it.removePrefix("O|") }
                .toSet()
            val removedStoryAndObjectCounts = withContext(Dispatchers.IO) {
                val latestDependencies = StoryObjectDependencyRepository.scan(context)
                val currentStories = StoryRepository.list(context)
                val currentObjects = ObjectRepository.list(context)
                check(selectedObjectFolders.isEmpty() || latestDependencies.scanErrors.isEmpty()) {
                    "No se pueden borrar objetos porque no se han podido comprobar todas las dependencias de cuentos."
                }
                selectedObjectFolders.forEach { folder ->
                    val dependentStories = latestDependencies.storyFoldersByObject[folder].orEmpty()
                    check(dependentStories.size == 1 && dependentStories.single() in selectedStoryPaths) {
                        "El objeto «$folder» está compartido o ya no pertenece exclusivamente a un cuento seleccionado. No se ha eliminado."
                    }
                    check(currentObjects.any { it.folderName == folder }) {
                        "El objeto «$folder» ya no está disponible."
                    }
                }
                selectedStoryPaths.forEach { storyPath ->
                    val story = currentStories.firstOrNull { it.directory.canonicalPath == storyPath }
                        ?: throw IllegalStateException("Uno de los cuentos seleccionados ya no está disponible.")
                    StoryRepository.deleteStory(context, story)
                }
                selectedObjectFolders.forEach { folder ->
                    val stored = currentObjects.first { it.folderName == folder }
                    ObjectRepository.delete(context, stored)
                    InventoryRepository.remove(context, folder)
                    ObjectUnlockRepository.remove(context, folder)
                }
                selectedStoryPaths.size to selectedObjectFolders.size
            }
            var removedExercises = 0
            var removedStatistics = 0
            ContentStorage.Modality.entries.forEach { modality ->
                val exercises = ExerciseRepository.list(context, modality)
                val categoriesToRemove = selectedItems.filter { it.startsWith("C|" + modality.name + "|") }
                    .map { it.removePrefix("C|" + modality.name + "|") }.toSet()
                exercises.forEach { stored ->
                    val path = stored.directory.absolutePath
                    if (stored.metadata.category in categoriesToRemove || ("E|" + path) in selectedItems) {
                        ExerciseRepository.delete(context, modality, stored.directory)
                        removedExercises++
                    } else if (("S|" + path) in selectedItems) {
                        if (ExerciseRepository.deleteStatistics(stored.directory)) removedStatistics++
                    }
                }
                if (categoriesToRemove.isNotEmpty()) {
                    categoriesToRemove.forEach { category ->
                        val categoryDirectory = ContentStorage.categoryDirectory(context, modality, category)
                        if (categoryDirectory.list()?.isEmpty() == true) categoryDirectory.delete()
                    }
                    val prefKey = when (modality) {
                        ContentStorage.Modality.TARJETAS -> "categories"
                        ContentStorage.Modality.PALABRAS -> "letter_word_categories_v1"
                        ContentStorage.Modality.SECUENCIAS -> "sequence_categories_v1"
                    }
                    val current = (preferences.getString(prefKey, "") ?: "").split("|").filter { it.isNotBlank() }
                    preferences.edit().putString(
                        prefKey,
                        current.filterNot { name -> categoriesToRemove.any { it.equals(name, true) } }.joinToString("|")
                    ).apply()
                }
            }
            onCategoryNamesChanged(
                (preferences.getString("categories", "") ?: "").split("|").filter { it.isNotBlank() }
            )
            onSaveWordCategories(
                (preferences.getString("letter_word_categories_v1", "") ?: "").split("|")
                    .map { it.trim() }.filter { it.isNotBlank() }
            )
            onSequenceCategoriesChanged(
                (preferences.getString("sequence_categories_v1", "") ?: "").split("|").filter { it.isNotBlank() }
            )
            onPairsChanged(ExerciseUiStorage.loadPairs(context, ContentStorage.Modality.TARJETAS))
            onReloadWordExercises()
            onReloadSequenceExercises()
            val refreshedContent = withContext(Dispatchers.IO) {
                StoryRepository.list(context) to ObjectRepository.list(context)
            }
            onStoriesChanged(refreshedContent.first)
            onObjectsChanged(refreshedContent.second)
            onResetSelection()
            onMessageChanged(
                "Eliminación completada: " + removedExercises + " ejercicios, " + removedStatistics +
                    " estadísticas, " + removedStoryAndObjectCounts.first + " cuentos y " +
                    removedStoryAndObjectCounts.second + " objetos eliminados."
            )
            onParentSectionChanged("library-transfer")
        } catch (error: Exception) {
            onConfirmArmedChanged(false)
            onMessageChanged("No se pudo completar la eliminación: " + (error.message ?: "error"))
        }
    }
}
