package es.aprenderparajugar

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Pantallas de biblioteca y edición de cuentos, fuera de la composición principal. */
internal fun LazyListScope.storyManagementItems(
    section: String,
    context: Context,
    preferences: SharedPreferences,
    parentSectionState: MutableState<String>,
    storedStoryObjectsState: MutableState<List<StoredObject>>,
    storyCollectionsState: StoryCollectionsState,
    storyEditorState: StoryEditorState,
    storyEditorAudioActions: StoryEditorAudioActions,
    storyAudioSettingsState: StoryAudioSettingsState,
    storyEditorCoverRevisionState: MutableState<Int>,
    storyEditorIllustrationRevisionState: MutableState<Int>,
    storyCollectionCoverRevisionState: MutableState<Int>,
    editingStoryCollectionState: MutableState<String?>,
    selectedStoriesCollectionState: MutableState<String?>,
    onPickStoryCover: () -> Unit,
    onPickStoryIllustration: () -> Unit,
    onChooseStoryCollectionCover: (String) -> Unit,
    persistStoryDraft: () -> StoredStory?
) {
    var parentSection by parentSectionState
    var storedStoryObjects by storedStoryObjectsState
    var storyCollections by storyCollectionsState.collections
    var storyCollectionDraft by storyCollectionsState.draft
    var storyCollectionMessage by storyCollectionsState.message
    var storyCollectionToDelete by storyCollectionsState.collectionToDelete
    var disabledStoryCollections by storyCollectionsState.disabledCollections
    var storyEditorTitle by storyEditorState.title
    var storyEditorCover by storyEditorState.cover
    var storyEditorIllustrations by storyEditorState.illustrations
    var editingStoryFolder by storyEditorState.editingFolder
    var editingStoryCollectionFolder by storyEditorState.editingCollectionFolder
    var storedStories by storyEditorState.storedStories
    var storyEditorMessage by storyEditorState.message
    var storyEditorCollection by storyEditorState.collection
    var storyEditorSummary by storyEditorState.summary
    var storyEditorScenes by storyEditorState.scenes
    var storyEditorSelectedScene by storyEditorState.selectedScene
    var storyEditorSceneText by storyEditorState.sceneText
    var storyEditorAwardedObjectByScene by storyEditorState.awardedObjectByScene
    var storyEditorAwardedObjectsByScene by storyEditorState.awardedObjectsByScene
    var storyEditorUnlockedObjectsByScene by storyEditorState.unlockedObjectsByScene
    var storyEditorCoverRevision by storyEditorCoverRevisionState
    var storyEditorIllustrationRevision by storyEditorIllustrationRevisionState
    var storyCollectionCoverRevision by storyCollectionCoverRevisionState
    var editingStoryCollection by editingStoryCollectionState
    var selectedStoriesCollection by selectedStoriesCollectionState

    when (section) {
            "stories-library" -> {
                        StoryLibraryScreen(
                            stories = storedStories,
                            collectionName = { collection ->
                                storyCollections.firstOrNull { StoryRepository.collectionFolderName(it) == collection }
                                    ?: collection.replace('_', ' ')
                            },
                            onStorySelected = { stored ->
                                storyEditorState.loadStructure(stored.metadata)
                                storyEditorState.requiredAccessObjects.value = stored.metadata.requiredAccessObjects
                                storyEditorTitle = stored.metadata.title
                                storyEditorCover = stored.metadata.cover
                                storyEditorIllustrations = stored.metadata.scenes.associate { scene -> scene to runCatching { StoryRepository.readScene(stored, scene).metadata.illustration }.getOrNull() }
                                storyEditorAudioActions.load(stored)
                                storyEditorState.loadDecisions(stored)
                                storyEditorAwardedObjectByScene = stored.metadata.scenes.associate { scene -> scene to runCatching { StoryRepository.readScene(stored, scene).metadata.awardedObject }.getOrNull() }
                                storyEditorAwardedObjectsByScene = stored.metadata.scenes.associate { scene ->
                                    scene to runCatching {
                                        val metadata = StoryRepository.readScene(stored, scene).metadata
                                        (listOfNotNull(metadata.awardedObject) + metadata.awardedObjects).distinct()
                                    }.getOrDefault(emptyList())
                                }
                                storyEditorUnlockedObjectsByScene = stored.metadata.scenes.associate { scene -> scene to runCatching { StoryRepository.readScene(stored, scene).metadata.unlockedObjects }.getOrDefault(emptyList()) }
                                storedStoryObjects = ObjectRepository.list(context)
                                storyEditorCollection = storyCollections.firstOrNull { StoryRepository.collectionFolderName(it) == stored.collection } ?: stored.collection
                                storyEditorScenes = stored.metadata.scenes.map { it.replace('_', ' ') }
                                editingStoryFolder = stored.folderName
                                editingStoryCollectionFolder = stored.collection
                                storyEditorSelectedScene = ""
                                storyEditorMessage = ""
                                parentSection = "stories-editor"
                            },
                            onCreateStory = {
                                storyEditorState.resetStructure(listOf("Escena 1"))
                                storyEditorState.requiredAccessObjects.value = emptyList()
                                storyEditorTitle = ""
                                storyEditorCover = null
                                storyEditorIllustrations = emptyMap()
                                storyEditorAudioActions.reset()
                                storyEditorState.resetDecisions()
                                storyEditorAwardedObjectByScene = emptyMap()
                                storyEditorAwardedObjectsByScene = emptyMap()
                                storyEditorUnlockedObjectsByScene = emptyMap()
                                storedStoryObjects = ObjectRepository.list(context)
                                storyEditorCollection = ""
                                storyEditorSummary = ""
                                storyEditorSceneText = ""
                                storyEditorScenes = listOf("Escena 1")
                                storyEditorSelectedScene = ""
                                editingStoryFolder = null
                                editingStoryCollectionFolder = null
                                storyEditorMessage = ""
                                parentSection = "stories-editor"
                            }
                        )
                    }
                    "stories-editor" -> {
                        item {
                            StoryEditorScreen(
                                state = storyEditorState,
                                collections = storyCollections,
                                objects = storedStoryObjects,
                                coverRevision = storyEditorCoverRevision,
                                onPickCover = { onPickStoryCover() },
                                onOpenScene = { scene ->
                                    storedStoryObjects = ObjectRepository.list(context)
                                    parentSection = "stories-scene-editor"
                                },
                                onOpenStructure = { parentSection = "stories-structure" },
                                onBackToLibrary = { parentSection = "stories-library" }
                            )
                        }
                    }
                    "stories-structure" -> {
                        item {
                            StoryStructureScreen(
                                scenes = storyEditorScenes,
                                sceneSummaries = storyEditorScenes.associateWith { scene ->
                                    if (scene == storyEditorSelectedScene) {
                                        storyEditorSummary
                                    } else {
                                        val folder = runCatching { StoryRepository.sceneFolderName(scene) }.getOrNull()
                                        val stored = storedStories.firstOrNull {
                                            it.folderName == editingStoryFolder && it.collection == editingStoryCollectionFolder
                                        }
                                        if (folder != null && stored != null) {
                                            runCatching { StoryRepository.readScene(stored, folder).metadata.illustrationSummary }.getOrNull().orEmpty()
                                        } else ""
                                    }
                                },
                                initialConnections = storyEditorState.connections.value,
                                initialConditions = storyEditorState.connectionConditions.value,
                                initialAlternatives = storyEditorState.connectionAlternatives.value,
                                decisionOptionsByScene = storyEditorState.decisionOptionsByScene.value,
                                decisionPromptsByScene = storyEditorState.decisionPromptByScene.value,
                                initialStartScene = storyEditorState.startScene.value,
                                availableObjects = storedStoryObjects,
                                message = storyEditorMessage,
                                onSave = { connections, conditions, alternatives, startScene ->
                                    storyEditorState.connections.value = connections
                                    storyEditorState.connectionConditions.value = conditions
                                    storyEditorState.connectionAlternatives.value = alternatives
                                    storyEditorState.startScene.value = startScene
                                    if (persistStoryDraft() != null) {
                                        parentSection = "stories-editor"
                                        true
                                    } else false
                                },
                                onCancel = { parentSection = "stories-editor" }
                            )
                        }
                    }
                    "stories-scene-editor" -> {
                        item {
                            StorySceneEditorContent(
                                state = storyEditorState,
                                storyAmbientDuringNarration = storyAudioSettingsState.volume,
                                illustrationRevision = storyEditorIllustrationRevision,
                                onPickIllustration = { onPickStoryIllustration() },
                                onPickAmbient = storyEditorAudioActions.onPickAmbient,
                                onPickNarration = storyEditorAudioActions.onPickNarration,
                                onBackToMap = { parentSection = "stories-editor" }
                            )
                        }
                    }
                    "stories-collections" -> {
                        item {
                            StoryCollectionsScreen(
                                context = context,
                                collections = storyCollections,
                                draft = storyCollectionDraft,
                                message = storyCollectionMessage,
                                editingCollection = editingStoryCollection,
                                disabledCollections = disabledStoryCollections,
                                coverRevision = storyCollectionCoverRevision,
                                collectionToDelete = storyCollectionToDelete,
                                onDraftChange = { storyCollectionDraft = NameRules.filterInput(it) },
                                onAddCollection = { storyCollectionsState.addCollection() },
                                onToggleEditing = { name ->
                                    editingStoryCollection = if (editingStoryCollection == name) null else name
                                },
                                onToggleCollection = { name -> storyCollectionsState.toggleCollection(name) },
                                onChooseCover = { name -> onChooseStoryCollectionCover(name) },
                                onRemoveCover = { name ->
                                    StoryRepository.removeCollectionCover(context, StoryRepository.collectionFolderName(name))
                                    storyCollectionCoverRevision++
                                    storyCollectionMessage = "Portada de «$name» eliminada."
                                },
                                onDeleteAndStories = { name ->
                                    runCatching {
                                        storyCollectionsState.deleteCollection(name)
                                        storedStories = StoryRepository.list(context)
                                        if (selectedStoriesCollection == StoryRepository.collectionFolderName(name)) {
                                            selectedStoriesCollection = null
                                        }
                                        editingStoryCollection = null
                                    }.onFailure {
                                        storyCollectionMessage = it.message ?: "No se pudo eliminar la colección."
                                    }
                                },
                                onRequestDelete = { name -> storyCollectionsState.requestDelete(name) },
                                onCancelDelete = { storyCollectionsState.cancelDelete() }
                            )
                        }
                    }
    }
}
