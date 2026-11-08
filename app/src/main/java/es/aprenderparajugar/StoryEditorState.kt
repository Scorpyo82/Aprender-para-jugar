package es.aprenderparajugar

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.io.File

/**
 * Estado y persistencia del editor de cuentos.
 *
 * Mantiene fuera de AprenderApp los estados de edición y la operación que guarda
 * el borrador completo. La interfaz solo lee/modifica estas propiedades y llama
 * a persist() cuando necesita guardar antes de importar recursos o confirmar.
 */
internal class StoryEditorState(private val context: Context) {
    val title: MutableState<String> = mutableStateOf("")
    val cover: MutableState<String?> = mutableStateOf(null)
    val illustrations: MutableState<Map<String, String?>> = mutableStateOf(emptyMap())
    val ambientAudio: MutableState<Map<String, String?>> = mutableStateOf(emptyMap())
    val narrationAudio: MutableState<Map<String, String?>> = mutableStateOf(emptyMap())
    val editingFolder: MutableState<String?> = mutableStateOf(null)
    val editingCollectionFolder: MutableState<String?> = mutableStateOf(null)
    val storedStories: MutableState<List<StoredStory>> = mutableStateOf(StoryRepository.list(context))
    val message: MutableState<String> = mutableStateOf("")
    val collection: MutableState<String> = mutableStateOf("")
    val summary: MutableState<String> = mutableStateOf("")
    val scenes: MutableState<List<String>> = mutableStateOf(listOf("Escena 1"))
    val selectedScene: MutableState<String> = mutableStateOf("")
    val sceneText: MutableState<String> = mutableStateOf("")
    val objectTiming: MutableState<String> = mutableStateOf("Al salir")
    val awardedObjectByScene: MutableState<Map<String, String?>> = mutableStateOf(emptyMap())
    val awardedObjectsByScene: MutableState<Map<String, List<String>>> = mutableStateOf(emptyMap())
    val unlockedObjectsByScene: MutableState<Map<String, List<String>>> = mutableStateOf(emptyMap())
    val objectMenuExpanded: MutableState<Boolean> = mutableStateOf(false)
    val keepAmbientByScene: MutableState<Map<String, Boolean>> = mutableStateOf(emptyMap())
    val collectionMenuExpanded: MutableState<Boolean> = mutableStateOf(false)
    val connections: MutableState<Map<String, List<String>>> = mutableStateOf(emptyMap())
    val connectionConditions: MutableState<List<StoryConnectionCondition>> = mutableStateOf(emptyList())
    val connectionAlternatives: MutableState<List<StoryConnectionAlternative>> = mutableStateOf(emptyList())
    val startScene: MutableState<String> = mutableStateOf("Escena 1")

    /** Carga el grafo persistido usando los nombres visibles de las escenas en el editor. */
    fun loadStructure(metadata: StoryMetadata) {
        val visibleByFolder = metadata.scenes.associate { it to it.replace('_', ' ') }
        startScene.value = visibleByFolder[metadata.startScene] ?: visibleByFolder.values.firstOrNull().orEmpty()
        connections.value = metadata.scenes.associate { folder ->
            visibleByFolder.getValue(folder) to metadata.connections[folder].orEmpty().map { destination -> if (destination == STORY_LOST_DESTINATION) STORY_LOST_DESTINATION else visibleByFolder[destination] ?: STORY_LOST_DESTINATION }
        }
        connectionConditions.value = metadata.connectionConditions.map { condition ->
            condition.copy(
                fromScene = when (condition.fromScene) {
                    STORY_LOST_ORIGIN -> STORY_LOST_ORIGIN
                    else -> visibleByFolder[condition.fromScene] ?: STORY_LOST_ORIGIN
                },
                toScene = when (condition.toScene) {
                    STORY_LOST_DESTINATION -> STORY_LOST_DESTINATION
                    else -> visibleByFolder[condition.toScene] ?: STORY_LOST_DESTINATION
                }
            )
        }
        connectionAlternatives.value = metadata.connectionAlternatives.map { alternative ->
            alternative.copy(
                fromScene = when (alternative.fromScene) {
                    STORY_LOST_ORIGIN -> STORY_LOST_ORIGIN
                    else -> visibleByFolder[alternative.fromScene] ?: STORY_LOST_ORIGIN
                },
                toScene = when (alternative.toScene) {
                    STORY_LOST_DESTINATION -> STORY_LOST_DESTINATION
                    else -> visibleByFolder[alternative.toScene] ?: STORY_LOST_DESTINATION
                }
            )
        }
    }

    /** Inicializa un cuento nuevo con el recorrido lineal que se utilizaba hasta ahora. */
    fun resetStructure(sceneNames: List<String>) {
        startScene.value = sceneNames.firstOrNull() ?: ""
        connections.value = sceneNames.mapIndexed { index, scene ->
            scene to listOfNotNull(sceneNames.getOrNull(index + 1))
        }.toMap()
        connectionConditions.value = emptyList()
        connectionAlternatives.value = emptyList()
    }

    /** Añade nodos nuevos sin reconstruir ni destruir las bifurcaciones ya configuradas. */
    private fun reconcileStructure(sceneNames: List<String>) {
        val valid = sceneNames.toSet()
        val next = connections.value
            .filterKeys { it in valid }
            .mapValues { (_, destinations) -> destinations.filter { it == STORY_LOST_DESTINATION || it in valid }.distinct() }
            .toMutableMap()
        sceneNames.forEach { next.putIfAbsent(it, emptyList()) }
        sceneNames.zipWithNext().forEach { (previous, current) ->
            if (current !in connections.value && next[previous].isNullOrEmpty()) {
                next[previous] = listOf(current)
            }
        }
        connections.value = next
        val edges = next.flatMap { (from, destinations) -> destinations.map { from to it } }.toSet()
        // Las referencias huérfanas se conservan para que el editor pueda repararlas.
        // Solo se descartan referencias normales cuya arista ya no existe por una edición explícita.
        connectionConditions.value = connectionConditions.value.filter {
            it.fromScene == STORY_LOST_ORIGIN || it.toScene == STORY_LOST_DESTINATION ||
                (it.fromScene to it.toScene) in edges
        }
        connectionAlternatives.value = connectionAlternatives.value.filter {
            it.fromScene == STORY_LOST_ORIGIN || it.toScene == STORY_LOST_DESTINATION ||
                (it.fromScene to it.toScene) in edges
        }
    }

    fun removeScene(sceneName: String): Boolean {
        val currentScenes = scenes.value
        if (sceneName !in currentScenes || currentScenes.size <= 1) return false
        val remaining = currentScenes.filterNot { it == sceneName }
        val currentConnections = connections.value
        scenes.value = remaining
        connections.value = remaining.associateWith { origin ->
            if (origin == sceneName) emptyList()
            else currentConnections[origin].orEmpty().map { if (it == sceneName) STORY_LOST_DESTINATION else it }.distinct()
        }
        val outgoingDestinations = currentConnections[sceneName].orEmpty().filter { it != STORY_LOST_DESTINATION }
        val alternativesBeforeRemoval = connectionAlternatives.value.toMutableList()
        outgoingDestinations.forEach { destination ->
            if (alternativesBeforeRemoval.none { it.fromScene == sceneName && it.toScene == destination }) {
                val legacyCondition = connectionConditions.value.firstOrNull {
                    it.fromScene == sceneName && it.toScene == destination
                }
                alternativesBeforeRemoval += StoryConnectionAlternative(
                    fromScene = sceneName,
                    toScene = destination,
                    kind = if (legacyCondition?.requiredObject != null) "object" else "free",
                    requiredObject = legacyCondition?.requiredObject,
                    consumeRequiredObject = legacyCondition?.consumeRequiredObject == true
                )
            }
        }
        connectionConditions.value = connectionConditions.value.map { condition ->
            condition.copy(
                fromScene = if (condition.fromScene == sceneName) STORY_LOST_ORIGIN else condition.fromScene,
                toScene = if (condition.toScene == sceneName) STORY_LOST_DESTINATION else condition.toScene
            )
        }
        connectionAlternatives.value = alternativesBeforeRemoval.map { alternative ->
            alternative.copy(
                fromScene = if (alternative.fromScene == sceneName) STORY_LOST_ORIGIN else alternative.fromScene,
                toScene = if (alternative.toScene == sceneName) STORY_LOST_DESTINATION else alternative.toScene
            )
        }
        illustrations.value = illustrations.value - sceneName
        ambientAudio.value = ambientAudio.value - sceneName
        narrationAudio.value = narrationAudio.value - sceneName
        awardedObjectByScene.value = awardedObjectByScene.value - sceneName
        awardedObjectsByScene.value = awardedObjectsByScene.value - sceneName
        unlockedObjectsByScene.value = unlockedObjectsByScene.value - sceneName
        keepAmbientByScene.value = keepAmbientByScene.value - sceneName
        if (selectedScene.value == sceneName) {
            selectedScene.value = ""
            sceneText.value = ""
            summary.value = ""
        }
        if (startScene.value == sceneName) startScene.value = remaining.first()
        return true
    }

    fun persist(): StoredStory? {
        return runCatching {
            val originalStory = editingFolder.value?.let { folder ->
                StoryRepository.list(context).firstOrNull { it.folderName == folder && it.collection == editingCollectionFolder.value }
            }
            require(NameRules.validationError(title.value) == null || title.value == originalStory?.metadata?.title) {
                NameRules.validationError(title.value) ?: "Escribe el título del cuento."
            }
            require(NameRules.validationError(collection.value) == null || collection.value == editingCollectionFolder.value) {
                NameRules.validationError(collection.value) ?: "Selecciona una colección."
            }
            val duplicateStory = StoryRepository.list(context).firstOrNull {
                it.collection == StoryRepository.collectionFolderName(collection.value) &&
                    NameRules.key(it.metadata.title) == NameRules.key(title.value) &&
                    it.folderName != editingFolder.value
            }
            require(duplicateStory == null) { "Ya existe un cuento con ese nombre en la colección seleccionada." }
            val collectionFolder = StoryRepository.collectionFolderName(collection.value)
            val sceneFolders = scenes.value.map { StoryRepository.sceneFolderName(it) }
            require(sceneFolders.distinct().size == sceneFolders.size) { "Hay nombres de escena duplicados." }
            val storyFolder = editingFolder.value ?: StoryRepository.storyFolderName(title.value)
            reconcileStructure(scenes.value)
            val folderByVisible = scenes.value.zip(sceneFolders).toMap()
            val persistedConnections = connections.value.mapNotNull { (from, destinations) ->
                val folderFrom = folderByVisible[from] ?: return@mapNotNull null
                folderFrom to destinations.map { if (it == STORY_LOST_DESTINATION) it else folderByVisible[it] ?: STORY_LOST_DESTINATION }.distinct()
            }.toMap()
            val persistedConditions = connectionConditions.value.map { condition ->
                condition.copy(
                    fromScene = if (condition.fromScene == STORY_LOST_ORIGIN) STORY_LOST_ORIGIN else folderByVisible[condition.fromScene] ?: STORY_LOST_ORIGIN,
                    toScene = if (condition.toScene == STORY_LOST_DESTINATION) STORY_LOST_DESTINATION else folderByVisible[condition.toScene] ?: STORY_LOST_DESTINATION
                )
            }
            val persistedAlternatives = connectionAlternatives.value.map { alternative ->
                alternative.copy(
                    fromScene = if (alternative.fromScene == STORY_LOST_ORIGIN) STORY_LOST_ORIGIN else folderByVisible[alternative.fromScene] ?: STORY_LOST_ORIGIN,
                    toScene = if (alternative.toScene == STORY_LOST_DESTINATION) STORY_LOST_DESTINATION else folderByVisible[alternative.toScene] ?: STORY_LOST_DESTINATION
                )
            }
            val metadata = StoryMetadata(
                title = title.value.trim(),
                collection = collectionFolder,
                cover = cover.value,
                startScene = folderByVisible[startScene.value] ?: sceneFolders.first(),
                scenes = sceneFolders,
                connections = persistedConnections,
                connectionConditions = persistedConditions,
                connectionAlternatives = persistedAlternatives
            )
            var stored = StoryRepository.save(context = context, collection = collectionFolder, folderName = storyFolder, metadata = metadata)
            sceneFolders.forEachIndexed { index, sceneFolder ->
                val existing = runCatching { StoryRepository.readScene(stored, sceneFolder).metadata }.getOrNull()
                val isSelected = selectedScene.value == scenes.value[index]
                val sceneMetadata = if (isSelected) {
                    StorySceneMetadata(
                        title = selectedScene.value,
                        illustrationSummary = summary.value,
                        text = sceneText.value,
                        illustration = illustrations.value[selectedScene.value] ?: existing?.illustration,
                        ambientAudio = if (ambientAudio.value.containsKey(selectedScene.value)) ambientAudio.value[selectedScene.value] else existing?.ambientAudio,
                        keepPreviousAmbient = keepAmbientByScene.value[selectedScene.value] ?: false,
                        narrationAudio = if (narrationAudio.value.containsKey(selectedScene.value)) narrationAudio.value[selectedScene.value] else existing?.narrationAudio,
                        awardedObject = if (awardedObjectsByScene.value.containsKey(selectedScene.value)) null else existing?.awardedObject,
                        awardTiming = if (objectTiming.value == "Al entrar") "onEnter" else "onExit",
                        awardedObjects = awardedObjectsByScene.value[selectedScene.value] ?: (listOfNotNull(existing?.awardedObject) + existing?.awardedObjects.orEmpty()).distinct(),
                        unlockedObjects = unlockedObjectsByScene.value[selectedScene.value] ?: existing?.unlockedObjects.orEmpty(),
                        decisionPrompt = existing?.decisionPrompt.orEmpty(),
                        decisionOptions = existing?.decisionOptions.orEmpty(),
                        nextDecisionOptionNumber = existing?.nextDecisionOptionNumber ?: 1
                    )
                } else existing ?: StorySceneMetadata(title = scenes.value[index])
                StoryRepository.saveScene(stored, sceneFolder, sceneMetadata)
            }
            StoryRepository.deleteSceneDirectoriesExcept(stored, sceneFolders.toSet())
            editingFolder.value = storyFolder
            editingCollectionFolder.value = collectionFolder
            stored = StoryRepository.read(File(File(StoryRepository.rootDirectory(context), collectionFolder), storyFolder))
            storedStories.value = StoryRepository.list(context)
            message.value = "Cuento guardado."
            stored
        }.getOrElse {
            message.value = it.message ?: "No se pudo guardar el cuento."
            null
        }
    }
}
