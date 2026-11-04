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
    val objectMenuExpanded: MutableState<Boolean> = mutableStateOf(false)
    val keepAmbientByScene: MutableState<Map<String, Boolean>> = mutableStateOf(emptyMap())
    val collectionMenuExpanded: MutableState<Boolean> = mutableStateOf(false)
    val connections: MutableState<Map<String, List<String>>> = mutableStateOf(emptyMap())
    val connectionConditions: MutableState<List<StoryConnectionCondition>> = mutableStateOf(emptyList())
    val startScene: MutableState<String> = mutableStateOf("Escena 1")

    /** Carga el grafo persistido usando los nombres visibles de las escenas en el editor. */
    fun loadStructure(metadata: StoryMetadata) {
        val visibleByFolder = metadata.scenes.associate { it to it.replace('_', ' ') }
        startScene.value = visibleByFolder[metadata.startScene] ?: visibleByFolder.values.firstOrNull().orEmpty()
        connections.value = metadata.scenes.associate { folder ->
            visibleByFolder.getValue(folder) to metadata.connections[folder].orEmpty().mapNotNull(visibleByFolder::get)
        }
        connectionConditions.value = metadata.connectionConditions.mapNotNull { condition ->
            val from = visibleByFolder[condition.fromScene] ?: return@mapNotNull null
            val to = visibleByFolder[condition.toScene] ?: return@mapNotNull null
            condition.copy(fromScene = from, toScene = to)
        }
    }

    /** Inicializa un cuento nuevo con el recorrido lineal que se utilizaba hasta ahora. */
    fun resetStructure(sceneNames: List<String>) {
        startScene.value = sceneNames.firstOrNull() ?: ""
        connections.value = sceneNames.mapIndexed { index, scene ->
            scene to listOfNotNull(sceneNames.getOrNull(index + 1))
        }.toMap()
        connectionConditions.value = emptyList()
    }

    /** Añade nodos nuevos sin reconstruir ni destruir las bifurcaciones ya configuradas. */
    private fun reconcileStructure(sceneNames: List<String>) {
        val valid = sceneNames.toSet()
        val next = connections.value
            .filterKeys { it in valid }
            .mapValues { (_, destinations) -> destinations.filter { it in valid }.distinct().take(2) }
            .toMutableMap()
        sceneNames.forEach { next.putIfAbsent(it, emptyList()) }
        sceneNames.zipWithNext().forEach { (previous, current) ->
            if (current !in connections.value && next[previous].isNullOrEmpty()) {
                next[previous] = listOf(current)
            }
        }
        connections.value = next
        val edges = next.flatMap { (from, destinations) -> destinations.map { from to it } }.toSet()
        connectionConditions.value = connectionConditions.value.filter {
            (it.fromScene to it.toScene) in edges
        }
    }

    fun persist(): StoredStory? {
        return runCatching {
            require(title.value.isNotBlank()) { "Escribe el título del cuento." }
            require(collection.value.isNotBlank()) { "Selecciona una colección." }
            val collectionFolder = StoryRepository.collectionFolderName(collection.value)
            val sceneFolders = scenes.value.map { StoryRepository.sceneFolderName(it) }
            require(sceneFolders.distinct().size == sceneFolders.size) { "Hay nombres de escena duplicados." }
            val storyFolder = editingFolder.value ?: StoryRepository.storyFolderName(title.value)
            reconcileStructure(scenes.value)
            val folderByVisible = scenes.value.zip(sceneFolders).toMap()
            val persistedConnections = connections.value.mapNotNull { (from, destinations) ->
                val folderFrom = folderByVisible[from] ?: return@mapNotNull null
                folderFrom to destinations.mapNotNull(folderByVisible::get).distinct().take(2)
            }.toMap()
            val persistedConditions = connectionConditions.value.mapNotNull { condition ->
                val from = folderByVisible[condition.fromScene] ?: return@mapNotNull null
                val to = folderByVisible[condition.toScene] ?: return@mapNotNull null
                condition.copy(fromScene = from, toScene = to)
            }
            val metadata = StoryMetadata(
                title = title.value.trim(),
                collection = collectionFolder,
                cover = cover.value,
                startScene = folderByVisible[startScene.value] ?: sceneFolders.first(),
                scenes = sceneFolders,
                connections = persistedConnections,
                connectionConditions = persistedConditions
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
                        ambientAudio = ambientAudio.value[selectedScene.value] ?: existing?.ambientAudio,
                        keepPreviousAmbient = keepAmbientByScene.value[selectedScene.value] ?: false,
                        narrationAudio = narrationAudio.value[selectedScene.value] ?: existing?.narrationAudio,
                        awardedObject = if (awardedObjectByScene.value.containsKey(selectedScene.value)) awardedObjectByScene.value[selectedScene.value] else existing?.awardedObject,
                        awardTiming = if (objectTiming.value == "Al entrar") "onEnter" else "onExit"
                    )
                } else existing ?: StorySceneMetadata(title = scenes.value[index])
                StoryRepository.saveScene(stored, sceneFolder, sceneMetadata)
            }
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
