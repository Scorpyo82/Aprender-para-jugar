package es.aprenderparajugar

import android.content.Context
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal fun LazyListScope.storyObjectManagementItems(
    section: String,
    context: Context,
    rewardMode: RewardMode,
    storedStoryObjectsState: MutableState<List<StoredObject>>,
    storyObjectEditorState: StoryObjectEditorState,
    storyObjectInventoryState: StoryObjectInventoryState,
    storyObjectCollectionsState: StoryObjectCollectionsState,
    objectLibraryCollectionFilterState: MutableState<String>,
    objectStoreMessageState: MutableState<String>,
    objectEditorReturnSectionState: MutableState<String>,
    storyObjectCollectionMenuExpandedState: MutableState<Boolean>,
    parentSectionState: MutableState<String>,
    onChooseGallery: () -> Unit,
    onTakePhoto: () -> Unit
) {
    var storedStoryObjects by storedStoryObjectsState
    var storedInventoryFolders by storyObjectInventoryState.folders
    var storyObjectNameDraft by storyObjectEditorState.nameDraft
    var storyObjectPictureDraft by storyObjectEditorState.pictureDraft
    var storyObjectEditingFolder by storyObjectEditorState.editingFolder
    var storyObjectAvailableInStore by storyObjectEditorState.availableInStore
    var storyObjectRequiresUnlock by storyObjectEditorState.requiresUnlock
    var storyObjectStorePriceMinutes by storyObjectEditorState.storePriceMinutes
    var storyObjectCollectible by storyObjectEditorState.collectible
    var storyObjectMessage by storyObjectEditorState.message
    var storyObjectCollections by storyObjectCollectionsState.collections
    var storyObjectCollectionDraft by storyObjectCollectionsState.draft
    var storyObjectSelectedCollection by storyObjectEditorState.selectedCollection
    var storyObjectCollectionMessage by storyObjectCollectionsState.message
    var storyObjectCollectionToDelete by storyObjectCollectionsState.collectionToDelete
    var objectLibraryCollectionFilter by objectLibraryCollectionFilterState
    var objectStoreMessage by objectStoreMessageState
    var objectEditorReturnSection by objectEditorReturnSectionState
    var storyObjectCollectionMenuExpanded by storyObjectCollectionMenuExpandedState
    var parentSection by parentSectionState

    when (section) {
                    "stories-objects" -> {
                        item {
                            StoryObjectsMenuScreen(
                                onOpenLibrary = {
                                    storedStoryObjects = ObjectRepository.list(context)
                                    objectLibraryCollectionFilter = ALL_OBJECT_COLLECTIONS
                                    parentSection = "stories-object-library"
                                },
                                onOpenCollections = {
                                    storedStoryObjects = ObjectRepository.list(context)
                                    storyObjectCollectionMessage = ""
                                    parentSection = "stories-object-collection"
                                },
                                onOpenStore = {
                                    storedStoryObjects = ObjectRepository.list(context)
                                    storyObjectInventoryState.refresh(context)
                                    objectStoreMessage = ""
                                    parentSection = "stories-object-store"
                                },
                                onOpenInventory = {
                                    storedStoryObjects = ObjectRepository.list(context)
                                    storyObjectInventoryState.refresh(context)
                                    parentSection = "stories-object-inventory"
                                }
                            )
                        }
                    }
                    "stories-object-library" -> {
                        item {
                            StoryObjectLibraryScreen(
                                objects = storedStoryObjects,
                                collections = storyObjectCollections,
                                selectedCollection = objectLibraryCollectionFilter,
                                onCollectionSelected = { objectLibraryCollectionFilter = it },
                                onCreate = {
                                    storyObjectEditorState.reset()
                                    objectEditorReturnSection = "stories-object-library"
                                    parentSection = "stories-object-create"
                                },
                                onEdit = { stored ->
                                    storyObjectEditorState.beginEdit(stored)
                                    storyObjectInventoryState.refresh(context)
                                    objectEditorReturnSection = "stories-object-library"
                                    parentSection = "stories-object-create"
                                },
                                onCheckDelete = { stored ->
                                    ObjectStoryReferenceChecker.check(context, stored.folderName)
                                },
                                onDelete = { stored ->
                                    runCatching {
                                        val latestCheck = ObjectStoryReferenceChecker.check(context, stored.folderName)
                                        check(latestCheck.canDelete) {
                                            when {
                                                latestCheck.storyTitles.isNotEmpty() ->
                                                    "El objeto ahora está siendo utilizado por: ${latestCheck.storyTitles.joinToString(", ")}. No se ha eliminado."
                                                else ->
                                                    "No se ha podido comprobar toda la biblioteca de cuentos. No se ha eliminado el objeto."
                                            }
                                        }
                                        ObjectRepository.delete(context, stored)
                                        InventoryRepository.remove(context, stored.folderName)
                                        ObjectUnlockRepository.remove(context, stored.folderName)
                                        storedStoryObjects = ObjectRepository.list(context)
                                        storyObjectInventoryState.refresh(context)
                                        "«${stored.metadata.name}» se ha eliminado de la biblioteca."
                                    }.getOrElse { error ->
                                        "No se ha podido eliminar el objeto: ${error.message ?: "error desconocido"}"
                                    }
                                }
                            )
                        }
                    }
                    "stories-object-store" -> {
                        item {
                            StoryObjectStoreManagementScreen(
                                objects = storedStoryObjects,
                                inventoryFolders = storedInventoryFolders,
                                unlockedFolders = ObjectUnlockRepository.list(context),
                                message = objectStoreMessage,
                                onToggleInventory = { stored ->
                                    val wasOwned = stored.folderName in storedInventoryFolders
                                    val error = storyObjectInventoryState.toggle(context, stored.folderName)
                                    objectStoreMessage = error ?: if (wasOwned) "${stored.metadata.name} retirado del inventario." else "${stored.metadata.name} añadido al inventario."
                                },
                                onToggleUnlock = { stored ->
                                    val wasUnlocked = stored.folderName in ObjectUnlockRepository.list(context)
                                    runCatching {
                                        if (wasUnlocked) ObjectUnlockRepository.remove(context, stored.folderName)
                                        else ObjectUnlockRepository.unlock(context, listOf(stored.folderName))
                                    }.onSuccess {
                                        objectStoreMessage = if (wasUnlocked) "${stored.metadata.name} bloqueado de nuevo." else "${stored.metadata.name} desbloqueado para la tienda."
                                    }.onFailure { objectStoreMessage = it.message ?: "No se pudo cambiar el estado del objeto." }
                                },
                                onEdit = { stored ->
                                    storyObjectEditorState.beginEdit(stored)
                                    storyObjectInventoryState.refresh(context)
                                    objectEditorReturnSection = "stories-object-store"
                                    parentSection = "stories-object-create"
                                }
                            )
                        }
                    }
                    "stories-object-create" -> {
                        item {
                            StoryObjectEditorScreen(
                                editingFolder = storyObjectEditingFolder,
                                inInventory = storedInventoryFolders.contains(storyObjectEditingFolder),
                                name = storyObjectNameDraft,
                                picture = storyObjectPictureDraft,
                                collections = storyObjectCollections,
                                selectedCollection = storyObjectSelectedCollection,
                                collectionMenuExpanded = storyObjectCollectionMenuExpanded,
                                availableInStore = storyObjectAvailableInStore,
                                requiresUnlock = storyObjectRequiresUnlock,
                                collectible = storyObjectCollectible,
                                storePrice = storyObjectStorePriceMinutes,
                                isStarsMode = rewardMode.isStars,
                                message = storyObjectMessage,
                                onToggleInventory = {
                                    storyObjectInventoryState.toggle(context, storyObjectEditingFolder!!)?.let {
                                        storyObjectMessage = it
                                    }
                                },
                                onNameChange = {
                                    storyObjectNameDraft = if (storyObjectEditingFolder == null) NameRules.filterInput(it) else it
                                },
                                onCollectionMenuChange = { storyObjectCollectionMenuExpanded = it },
                                onCollectionSelected = {
                                    storyObjectSelectedCollection = it
                                    storyObjectCollectionMenuExpanded = false
                                },
                                onAvailableInStoreChange = {
                                    storyObjectAvailableInStore = it
                                    if (!it) storyObjectRequiresUnlock = false
                                },
                                onRequiresUnlockChange = { storyObjectRequiresUnlock = it },
                                onCollectibleChange = { storyObjectCollectible = it },
                                onStorePriceChange = { storyObjectStorePriceMinutes = it.filter(Char::isDigit).take(5) },
                                onChooseGallery = onChooseGallery,
                                onTakePhoto = onTakePhoto,
                                onSave = {
                                    storyObjectEditorState.save(context)?.let { savedObjects ->
                                        storedStoryObjects = savedObjects
                                        storyObjectInventoryState.refresh(context)
                                        parentSection = objectEditorReturnSection
                                    }
                                },
                                canSave = storyObjectNameDraft.isNotBlank() &&
                                    storyObjectSelectedCollection.isNotBlank() &&
                                    storyObjectPictureDraft.isNotBlank() &&
                                    (!storyObjectAvailableInStore || (storyObjectStorePriceMinutes.toIntOrNull() ?: 0) > 0),
                                onCancel = {
                                    storyObjectEditorState.reset()
                                    parentSection = objectEditorReturnSection
                                }
                            )
                        }
                    }
                    "stories-object-collection" -> {
                        item {
                            StoryObjectCollectionManagementScreen(
                                collections = storyObjectCollections,
                                draft = storyObjectCollectionDraft,
                                message = storyObjectCollectionMessage,
                                collectionToDelete = storyObjectCollectionToDelete,
                                objects = storedStoryObjects,
                                onDraftChange = { storyObjectCollectionDraft = NameRules.filterInput(it) },
                                onAddCollection = { storyObjectCollectionsState.addCollection() },
                                onRequestDelete = { storyObjectCollectionsState.requestDelete(it) },
                                onDeleteCollection = { storyObjectCollectionsState.deleteCollection(it) },
                                onCancelDelete = { storyObjectCollectionsState.cancelDelete() },
                                onMoveObject = { stored, targetCollection ->
                                    runCatching {
                                        ObjectRepository.moveToCollection(context, stored, targetCollection)
                                        storedStoryObjects = ObjectRepository.list(context)
                                        storyObjectCollectionMessage = "${stored.metadata.name} reasignado a «$targetCollection»."
                                    }.onFailure { storyObjectCollectionMessage = it.message ?: "No se pudo reasignar el objeto." }
                                }
                            )
                        }
                    }
                    "stories-object-inventory" -> {
                        item {
                            StoryObjectInventoryManagementScreen(
                                objects = storedStoryObjects,
                                inventoryFolders = storedInventoryFolders,
                                onRemove = { stored ->
                                    storyObjectInventoryState.remove(context, stored.folderName)?.let { storyObjectMessage = it }
                                }
                            )
                        }
                    }
    }
}
