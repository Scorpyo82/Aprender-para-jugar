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

    fun persist(): StoredStory? {
        return runCatching {
            require(title.value.isNotBlank()) { "Escribe el título del cuento." }
            require(collection.value.isNotBlank()) { "Selecciona una colección." }
            val collectionFolder = StoryRepository.collectionFolderName(collection.value)
            val sceneFolders = scenes.value.map { StoryRepository.sceneFolderName(it) }
            require(sceneFolders.distinct().size == sceneFolders.size) { "Hay nombres de escena duplicados." }
            val storyFolder = editingFolder.value ?: StoryRepository.storyFolderName(title.value)
            val connections = sceneFolders.mapIndexed { index, scene ->
                scene to listOfNotNull(sceneFolders.getOrNull(index + 1))
            }.toMap()
            val metadata = StoryMetadata(
                title = title.value.trim(),
                collection = collectionFolder,
                cover = cover.value,
                startScene = sceneFolders.first(),
                scenes = sceneFolders,
                connections = connections
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
