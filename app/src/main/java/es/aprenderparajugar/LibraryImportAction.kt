package es.aprenderparajugar

import android.content.Context
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ejecuta la importación seleccionada y actualiza las bibliotecas locales
 * después de que el paquete se haya incorporado correctamente.
 */
internal fun launchLibraryImportSelection(
    context: Context,
    scope: CoroutineScope,
    preferences: android.content.SharedPreferences,
    transferPackageState: MutableState<LibraryPackageManager.PackageInfo?>,
    transferImportCategoriesState: MutableState<Map<ContentStorage.Modality, Set<String>>>,
    transferImportStatisticsState: MutableState<Boolean>,
    transferImportUsageStatisticsState: MutableState<Boolean>,
    transferImportStoriesState: MutableState<Set<String>>,
    transferObjectConflictDecisionsState: MutableState<Map<String, String>>,
    transferStoryConflictDecisionsState: MutableState<Map<String, String>>,
    transferImportObjectsState: MutableState<Set<String>>,
    transferExerciseConflictDecisionsState: MutableState<Map<String, String>>,
    transferRemoteLibraryNameState: MutableState<String?>,
    remoteLibraryPackagesState: MutableState<List<RemoteLibraryPackage>>,
    storedStoriesState: MutableState<List<StoredStory>>,
    storedStoryObjectsState: MutableState<List<StoredObject>>,
    storyObjectCollectionsState: MutableState<List<String>>,
    storyCollectionsState: MutableState<List<String>>,
    pairsState: MutableState<List<PairItem>>,
    categoryNamesState: MutableState<List<String>>,
    transferMessageState: MutableState<String>,
    parentSectionState: MutableState<String>,
    libraryTaskRunningState: MutableState<Boolean>,
    wordLibraryState: WordLibraryState,
    sequenceLibraryState: SequenceLibraryState,
    updateProgress: (String, Int, Int) -> Unit
) {
    val info = transferPackageState.value ?: return
    libraryTaskRunningState.value = true
    updateProgress("Preparando la importación…", 0, 0)
    scope.launch {
        try {
            val count = withContext(Dispatchers.IO) {
                LibraryPackageManager.import(
                    context, info, transferImportCategoriesState.value, false,
                    transferImportStatisticsState.value, transferImportUsageStatisticsState.value,
                    transferImportStoriesState.value, transferObjectConflictDecisionsState.value,
                    transferStoryConflictDecisionsState.value, transferImportObjectsState.value,
                    transferExerciseConflictDecisionsState.value, updateProgress
                )
            }
            updateProgress("Actualizando las bibliotecas locales…", 0, 0)
            // Mark the exact release as installed only after the import completed successfully.
            transferRemoteLibraryNameState.value?.let { packageName ->
                remoteLibraryPackagesState.value.firstOrNull { it.name == packageName }?.let { remotePackage ->
                    withContext(Dispatchers.IO) {
                        RemoteLibraryRepository.saveInstalled(context, packageName, remotePackage.sha)
                    }
                }
            }
            transferPackageState.value = null
            withContext(Dispatchers.IO) {
                LibraryPackageManager.discard(info)
                transferRemoteLibraryNameState.value?.let { RemoteLibraryRepository.deleteCached(context, it) }
            }
            transferRemoteLibraryNameState.value = null

            // Refresh story and object repositories so imported content appears immediately.
            storedStoriesState.value = withContext(Dispatchers.IO) { StoryRepository.list(context) }
            storedStoryObjectsState.value = withContext(Dispatchers.IO) { ObjectRepository.list(context) }

            val knownObjectCollectionFolders = storyObjectCollectionsState.value.map {
                ObjectRepository.collectionFolderName(it)
            }.toSet()
            val importedObjectCollectionNames = storedStoryObjectsState.value
                .map { it.collection }
                .distinct()
                .filter { it !in knownObjectCollectionFolders }
                .map { it.replace('_', ' ') }
            if (importedObjectCollectionNames.isNotEmpty()) {
                storyObjectCollectionsState.value = (storyObjectCollectionsState.value + importedObjectCollectionNames)
                    .distinctBy { ObjectRepository.collectionFolderName(it).lowercase() }
                    .sortedBy { it.lowercase() }
                preferences.edit()
                    .putString("story_object_collections", storyObjectCollectionsState.value.joinToString("|"))
                    .apply()
            }

            // Register imported story collections without changing their folder identifiers.
            val knownCollectionFolders = storyCollectionsState.value.map {
                StoryRepository.collectionFolderName(it)
            }.toSet()
            val importedCollectionNames = storedStoriesState.value.map { it.collection }.distinct()
                .filter { it !in knownCollectionFolders }
                .map { it.replace('_', ' ') }
            if (importedCollectionNames.isNotEmpty()) {
                storyCollectionsState.value = (storyCollectionsState.value + importedCollectionNames)
                    .distinctBy { it.lowercase() }
                    .sortedBy { it.lowercase() }
                preferences.edit()
                    .putString("story_collections", storyCollectionsState.value.joinToString("|"))
                    .apply()
            }

            val refreshedLibraryData = withContext(Dispatchers.IO) {
                Triple(
                    ExerciseUiStorage.loadPairs(context, ContentStorage.Modality.TARJETAS),
                    ExerciseUiStorage.loadPairs(context, ContentStorage.Modality.PALABRAS),
                    ExerciseUiStorage.loadSequences(context)
                ) to Triple(
                    ExerciseRepository.list(context, ContentStorage.Modality.TARJETAS).map { it.metadata.category },
                    ExerciseRepository.list(context, ContentStorage.Modality.PALABRAS).map { it.metadata.category },
                    ExerciseRepository.list(context, ContentStorage.Modality.SECUENCIAS).map { it.metadata.category }
                )
            }
            pairsState.value = refreshedLibraryData.first.first
            wordLibraryState.exercises.value = refreshedLibraryData.first.second
            sequenceLibraryState.exercises.value = refreshedLibraryData.first.third
            categoryNamesState.value = (categoryNamesState.value + refreshedLibraryData.second.first).distinct()
            wordLibraryState.mergeImportedCategories(refreshedLibraryData.second.second, false)
            sequenceLibraryState.categoryNames.value =
                (sequenceLibraryState.categoryNames.value + refreshedLibraryData.second.third).distinct()
            preferences.edit()
                .putString("categories", categoryNamesState.value.joinToString("|"))
                .putString("letter_word_categories_v1", wordLibraryState.categories.value.joinToString("|"))
                .putString("sequence_categories_v1", sequenceLibraryState.categoryNames.value.joinToString("|"))
                .apply()

            transferMessageState.value =
                "Importación completada: $count ejercicios y ${transferImportStoriesState.value.size} cuentos incorporados. El inventario del niño no se ha modificado."
            parentSectionState.value = "library-transfer"
        } catch (e: Exception) {
            transferMessageState.value = "No se pudo completar la importación: ${e.message ?: "error"}"
        } finally {
            libraryTaskRunningState.value = false
        }
    }
}
