package es.aprenderparajugar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Destino reservado para una salida cuyo destino fue eliminado. */
internal const val STORY_LOST_DESTINATION = "Lost"

/** Datos generales y grafo narrativo de un cuento. */
data class StoryMetadata(
    val title: String,
    val collection: String,
    val cover: String? = null,
    val startScene: String,
    val scenes: List<String>,
    val connections: Map<String, List<String>>,
    val connectionConditions: List<StoryConnectionCondition> = emptyList(),
    val connectionAlternatives: List<StoryConnectionAlternative> = emptyList()
)

/** Condición configurada en una conexión; el compositor solo almacena estos datos. */
data class StoryConnectionCondition(
    val fromScene: String,
    val toScene: String,
    val requiredObject: String? = null,
    val consumeRequiredObject: Boolean = false
)

/** Alternativa de acceso a un destino; varias alternativas se evalúan con OR. */
data class StoryConnectionAlternative(
    val fromScene: String,
    val toScene: String,
    val kind: String,
    val requiredObject: String? = null,
    val decisionOptionKey: String? = null,
    val consumeRequiredObject: Boolean = false
)

/** La clave permanece estable aunque cambie el texto o el orden visible. */
data class StoryDecisionOption(val key: String, val text: String)

/** Contenido independiente de una escena. Las referencias a objetos son nombres de carpeta. */
data class StorySceneMetadata(
    val title: String,
    val illustrationSummary: String = "",
    val text: String = "",
    val illustration: String? = null,
    val ambientAudio: String? = null,
    val keepPreviousAmbient: Boolean = false,
    val narrationAudio: String? = null,
    val awardedObject: String? = null,
    val awardTiming: String = "onExit",
    val requiredObject: String? = null,
    val awardedObjects: List<String> = emptyList(),
    val unlockedObjects: List<String> = emptyList(),
    val decisionPrompt: String = "",
    val decisionOptions: List<StoryDecisionOption> = emptyList(),
    val nextDecisionOptionNumber: Int = 1
)

data class StoredStory(
    val directory: File,
    val collection: String,
    val folderName: String,
    val metadata: StoryMetadata
)

data class StoredStoryScene(
    val directory: File,
    val folderName: String,
    val metadata: StorySceneMetadata
)

/** Codificación JSON del manifiesto del cuento. */
object StoryJsonCodec {
    fun encode(metadata: StoryMetadata): JSONObject {
        validateStoryMetadata(metadata)
        val connectionsJson = JSONObject()
        metadata.scenes.forEach { scene ->
            connectionsJson.put(scene, JSONArray(metadata.connections[scene].orEmpty()))
        }
        val conditionsJson = JSONArray()
        metadata.connectionConditions.forEach { condition ->
            conditionsJson.put(JSONObject()
                .put("fromScene", condition.fromScene)
                .put("toScene", condition.toScene)
                .put("requiredObject", condition.requiredObject ?: JSONObject.NULL)
                .put("consumeRequiredObject", condition.consumeRequiredObject))
        }
        val alternativesToWrite = metadata.connectionAlternatives.ifEmpty {
            metadata.connections.flatMap { (origin, destinations) ->
                destinations.filter { it != STORY_LOST_DESTINATION }.map { destination ->
                    val condition = metadata.connectionConditions.firstOrNull { it.fromScene == origin && it.toScene == destination }
                    StoryConnectionAlternative(
                        fromScene = origin,
                        toScene = destination,
                        kind = if (condition?.requiredObject != null) "object" else "free",
                        requiredObject = condition?.requiredObject,
                        consumeRequiredObject = condition?.consumeRequiredObject == true
                    )
                }
            }
        }
        val alternativesJson = JSONArray()
        alternativesToWrite.forEach { alternative ->
            alternativesJson.put(JSONObject()
                .put("fromScene", alternative.fromScene)
                .put("toScene", alternative.toScene)
                .put("kind", alternative.kind)
                .put("requiredObject", alternative.requiredObject ?: JSONObject.NULL)
                .put("decisionOptionKey", alternative.decisionOptionKey ?: JSONObject.NULL)
                .put("consumeRequiredObject", alternative.consumeRequiredObject))
        }
        return JSONObject()
            .put("title", metadata.title.trim())
            .put("collection", metadata.collection)
            .put("cover", metadata.cover ?: JSONObject.NULL)
            .put("startScene", metadata.startScene)
            .put("scenes", JSONArray(metadata.scenes))
            .put("connections", connectionsJson)
            .put("connectionConditions", conditionsJson)
            .put("connectionAlternatives", alternativesJson)
    }

    fun decode(json: JSONObject): StoryMetadata {
        val sceneArray = json.optJSONArray("scenes") ?: JSONArray()
        val scenes = (0 until sceneArray.length()).map { sceneArray.getString(it) }
        val rawConnections = json.optJSONObject("connections") ?: JSONObject()
        val connections = scenes.associateWith { scene ->
            val destinations = rawConnections.optJSONArray(scene) ?: JSONArray()
            (0 until destinations.length()).map { destinations.getString(it) }
        }
        val cover = json.optString("cover").takeIf { it.isNotBlank() && it != "null" }
        val rawConditions = json.optJSONArray("connectionConditions") ?: JSONArray()
        val conditions = (0 until rawConditions.length()).mapNotNull { index ->
            val item = rawConditions.optJSONObject(index) ?: return@mapNotNull null
            StoryConnectionCondition(
                fromScene = item.optString("fromScene"),
                toScene = item.optString("toScene"),
                requiredObject = item.optString("requiredObject").takeIf { it.isNotBlank() && it != "null" },
                consumeRequiredObject = item.optBoolean("consumeRequiredObject", false)
            )
        }
        val rawAlternatives = json.optJSONArray("connectionAlternatives")
        val alternatives = if (rawAlternatives != null) {
            (0 until rawAlternatives.length()).mapNotNull { index ->
                val item = rawAlternatives.optJSONObject(index) ?: return@mapNotNull null
                StoryConnectionAlternative(
                    fromScene = item.optString("fromScene"),
                    toScene = item.optString("toScene"),
                    kind = item.optString("kind", "free"),
                    requiredObject = item.optString("requiredObject").takeIf { it.isNotBlank() && it != "null" },
                    decisionOptionKey = item.optString("decisionOptionKey").takeIf { it.isNotBlank() && it != "null" },
                    consumeRequiredObject = item.optBoolean("consumeRequiredObject", false)
                )
            }
        } else {
            connections.flatMap { (origin, destinations) ->
                destinations.filter { it != STORY_LOST_DESTINATION }.map { destination ->
                    val condition = conditions.firstOrNull { it.fromScene == origin && it.toScene == destination }
                    StoryConnectionAlternative(
                        fromScene = origin,
                        toScene = destination,
                        kind = if (condition?.requiredObject != null) "object" else "free",
                        requiredObject = condition?.requiredObject,
                        consumeRequiredObject = condition?.consumeRequiredObject == true
                    )
                }
            }
        }
        return StoryMetadata(
            title = json.optString("title").trim(),
            collection = json.optString("collection"),
            cover = cover,
            startScene = json.optString("startScene"),
            scenes = scenes,
            connections = connections,
            connectionConditions = conditions,
            connectionAlternatives = alternatives
        ).also(::validateStoryMetadata)
    }

    private fun validateStoryMetadata(metadata: StoryMetadata) {
        require(metadata.title.isNotBlank()) { "El cuento necesita un título." }
        require(isSafeSegment(metadata.collection)) { "La colección del cuento no es válida." }
        require(isSafeSegment(metadata.startScene)) { "La escena inicial no es válida." }
        require(metadata.scenes.isNotEmpty()) { "El cuento debe contener al menos una escena." }
        require(metadata.scenes.all(::isSafeSegment) && STORY_LOST_DESTINATION !in metadata.scenes) { "Hay un nombre de escena no válido o reservado." }
        require(metadata.scenes.distinct().size == metadata.scenes.size) { "Hay nombres de escena duplicados." }
        require(metadata.startScene in metadata.scenes) { "La escena inicial no pertenece al cuento." }
        require(metadata.connections.keys.all { it in metadata.scenes }) { "Hay conexiones desde escenas desconocidas." }
        metadata.connections.forEach { (origin, destinations) ->
            require(destinations.distinct().size == destinations.size) { "Una escena no puede conectarse dos veces con el mismo destino." }
            require(destinations.all { it == STORY_LOST_DESTINATION || it in metadata.scenes }) { "Una conexión apunta a una escena inexistente." }
            require(origin !in destinations) { "Una escena no puede conectarse consigo misma." }
        }
        val edges = metadata.connections.flatMap { (origin, destinations) -> destinations.map { origin to it } }.toSet()
        require(metadata.connectionConditions.map { it.fromScene to it.toScene }.distinct().size == metadata.connectionConditions.size) {
            "Una conexión no puede tener condiciones duplicadas."
        }
        metadata.connectionConditions.forEach { condition ->
            require(condition.toScene != STORY_LOST_DESTINATION && (condition.fromScene to condition.toScene) in edges) { "Hay una condición asociada a una conexión inexistente." }
            require(condition.requiredObject == null || isSafeSegment(condition.requiredObject)) { "La referencia al objeto requerido no es válida." }
            require(condition.requiredObject != null || !condition.consumeRequiredObject) { "No se puede consumir un objeto si no se ha seleccionado ninguno." }
        }
        metadata.connectionAlternatives.forEach { alternative ->
            require(alternative.toScene != STORY_LOST_DESTINATION && (alternative.fromScene to alternative.toScene) in edges) { "Hay una alternativa asociada a una conexión inexistente." }
            require(alternative.kind in setOf("free", "object", "decision")) { "El tipo de alternativa narrativa no es válido." }
            require(alternative.requiredObject == null || isSafeSegment(alternative.requiredObject)) { "La referencia al objeto requerido no es válida." }
            require((alternative.kind == "object") == (alternative.requiredObject != null)) { "La alternativa por objeto debe indicar exactamente un objeto." }
            require((alternative.kind == "decision") == (alternative.decisionOptionKey != null)) { "La alternativa por decisión debe indicar exactamente una opción." }
            require(alternative.kind != "free" || (alternative.requiredObject == null && alternative.decisionOptionKey == null)) { "Una alternativa libre no puede incluir condiciones." }
            require(alternative.kind != "object" || alternative.decisionOptionKey == null) { "Una alternativa por objeto no puede incluir una opción de decisión." }
            require(alternative.kind != "decision" || (alternative.requiredObject == null && !alternative.consumeRequiredObject)) { "Una alternativa por decisión no puede incluir condiciones de objeto." }
            require(!alternative.consumeRequiredObject || alternative.kind == "object") { "Solo se puede consumir un objeto en una alternativa por objeto." }
        }
        require(metadata.connectionAlternatives.distinct().size == metadata.connectionAlternatives.size) {
            "Hay alternativas narrativas duplicadas."
        }
        metadata.connectionAlternatives
            .filter { it.kind == "decision" }
            .groupBy { it.fromScene to it.decisionOptionKey }
            .forEach { (_, alternatives) ->
                require(alternatives.map { it.toScene }.distinct().size == 1) {
                    "Una misma opción de decisión no puede conducir a destinos diferentes."
                }
            }
        require(metadata.cover == null || isSafeFileName(metadata.cover)) { "El nombre de portada no es válido." }
    }

    internal fun isSafeSegment(value: String): Boolean =
        value.isNotBlank() && value != "." && value != ".." &&
            !value.contains('/') && !value.contains('\\') && !value.contains(':')

    internal fun isSafeFileName(value: String): Boolean =
        isSafeSegment(value) && !value.startsWith("/")
}

/** Codificación JSON del contenido de una escena. */
object StorySceneJsonCodec {
    fun encode(metadata: StorySceneMetadata): JSONObject {
        validate(metadata)
        return JSONObject()
            .put("title", metadata.title.trim())
            .put("illustrationSummary", metadata.illustrationSummary)
            .put("text", metadata.text)
            .put("illustration", metadata.illustration ?: JSONObject.NULL)
            .put("ambientAudio", metadata.ambientAudio ?: JSONObject.NULL)
            .put("keepPreviousAmbient", metadata.keepPreviousAmbient)
            .put("narrationAudio", metadata.narrationAudio ?: JSONObject.NULL)
            .put("awardedObject", metadata.awardedObject ?: JSONObject.NULL)
            .put("awardTiming", metadata.awardTiming)
            .put("requiredObject", metadata.requiredObject ?: JSONObject.NULL)
            .put("awardedObjects", JSONArray(metadata.awardedObjects.distinct()))
            .put("unlockedObjects", JSONArray(metadata.unlockedObjects.distinct()))
            .put("decisionPrompt", metadata.decisionPrompt)
            .put("decisionOptions", JSONArray().apply {
                metadata.decisionOptions.forEach { option -> put(JSONObject().put("key", option.key).put("text", option.text)) }
            })
            .put("nextDecisionOptionNumber", metadata.nextDecisionOptionNumber)
    }

    fun decode(json: JSONObject): StorySceneMetadata =
        StorySceneMetadata(
            title = json.optString("title").trim(),
            illustrationSummary = json.optString("illustrationSummary"),
            text = json.optString("text"),
            illustration = json.optString("illustration").takeNullable(),
            ambientAudio = json.optString("ambientAudio").takeNullable(),
            keepPreviousAmbient = json.optBoolean("keepPreviousAmbient", false),
            narrationAudio = json.optString("narrationAudio").takeNullable(),
            awardedObject = json.optString("awardedObject").takeNullable(),
            awardTiming = json.optString("awardTiming", "onExit"),
            requiredObject = json.optString("requiredObject").takeNullable(),
            awardedObjects = json.optJSONArray("awardedObjects").toStringList(),
            unlockedObjects = json.optJSONArray("unlockedObjects").toStringList(),
            decisionPrompt = json.optString("decisionPrompt"),
            decisionOptions = json.optJSONArray("decisionOptions").toDecisionOptions(),
            nextDecisionOptionNumber = json.optInt("nextDecisionOptionNumber", 1)
        ).also(::validate)

    private fun validate(metadata: StorySceneMetadata) {
        require(metadata.title.isNotBlank()) { "La escena necesita un nombre." }
        listOfNotNull(metadata.illustration, metadata.ambientAudio, metadata.narrationAudio)
            .forEach { require(StoryJsonCodec.isSafeFileName(it)) { "Hay un nombre de archivo multimedia no válido." } }
        require(metadata.awardTiming == "onEnter" || metadata.awardTiming == "onExit") {
            "El momento de obtención del objeto no es válido."
        }
        (listOfNotNull(metadata.awardedObject, metadata.requiredObject) + metadata.awardedObjects + metadata.unlockedObjects).forEach {
            require(StoryJsonCodec.isSafeSegment(it)) { "La referencia a un objeto no es válida." }
        }
        require(metadata.nextDecisionOptionNumber >= 1) { "El contador de opciones de decisión no es válido." }
        require(metadata.decisionOptions.map { it.key }.distinct().size == metadata.decisionOptions.size) { "Hay referencias internas de opciones duplicadas." }
        metadata.decisionOptions.forEach { option ->
            require(option.key.matches(Regex("[a-zA-Z0-9_-]+"))) { "La referencia interna de una opción no es válida." }
            require(option.text.isNotBlank()) { "Las opciones de decisión no pueden estar vacías." }
        }
        require(metadata.decisionOptions.none { option ->
            option.key.startsWith("opcion_") && option.key.removePrefix("opcion_").toIntOrNull()?.let { it >= metadata.nextDecisionOptionNumber } == true
        }) { "El contador de opciones debe ser superior a las referencias ya utilizadas." }
    }

    private fun String.takeNullable(): String? = takeIf { isNotBlank() && it != "null" }
    private fun JSONArray?.toStringList(): List<String> = this?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } } ?: emptyList()
    private fun JSONArray?.toDecisionOptions(): List<StoryDecisionOption> = this?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            StoryDecisionOption(item.optString("key"), item.optString("text"))
        }
    } ?: emptyList()
}

/**
 * Almacenamiento privado:
 * files/cuentos/coleccion/cuento/cuento.json
 * files/cuentos/coleccion/cuento/escenas/nombre_escena/escena.json
 *
 * La identidad de cuentos y escenas es el nombre de sus carpetas; no se generan IDs.
 */
object StoryRepository {
    private const val ROOT = "cuentos"
    private const val STORY_JSON = "cuento.json"
    private const val SCENES_DIRECTORY = "escenas"
    private const val SCENE_JSON = "escena.json"
    private const val COLLECTION_COVER_PREFIX = "portada_coleccion."

    fun collectionFolderName(value: String): String = safeSegment(value, "La colección")
    fun storyFolderName(value: String): String = safeSegment(value, "El cuento")
    fun sceneFolderName(value: String): String = safeSegment(value, "La escena")

    /** Portada opcional de una colección, independiente de las portadas de sus cuentos. */
    fun collectionCoverFile(context: Context, collection: String): File? {
        val directory = File(rootDirectory(context), safeSegment(collection, "La colección"))
        return directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(COLLECTION_COVER_PREFIX) }
            ?.sortedBy { it.name }
            ?.firstOrNull()
    }

    /** Guarda la portada de colección sin tocar las portadas de los cuentos. */
    fun saveCollectionCover(context: Context, collection: String, source: android.net.Uri): File {
        val directory = ensureDirectory(File(rootDirectory(context), safeSegment(collection, "La colección")))
        val savedName = importMedia(context, source, directory, "portada_coleccion")
        directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(COLLECTION_COVER_PREFIX) && it.name != savedName }
            ?.forEach { it.delete() }
        return File(directory, savedName)
    }

    fun removeCollectionCover(context: Context, collection: String) {
        val directory = File(rootDirectory(context), safeSegment(collection, "La colección"))
        directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(COLLECTION_COVER_PREFIX) }
            ?.forEach { it.delete() }
    }

    /** Elimina físicamente una colección y todo su contenido tras confirmar la acción en la interfaz. */
    fun deleteCollection(context: Context, collection: String) {
        val root = rootDirectory(context).canonicalFile
        val target = File(root, safeSegment(collection, "La colección")).canonicalFile
        require(target.parentFile == root && target != root) {
            "La ubicación de la colección no es válida."
        }
        if (target.exists()) {
            check(target.isDirectory) { "La colección no es una carpeta válida." }
            check(target.walkBottomUp().all { it.delete() || !it.exists() }) {
                "No se pudo eliminar todo el contenido de la colección."
            }
        }
    }

    fun rootDirectory(context: Context): File = ensureDirectory(File(context.filesDir, ROOT))

    fun list(context: Context): List<StoredStory> {
        val root = rootDirectory(context)
        return root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }
            ?.flatMap { collection ->
                collection.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
                    ?.sortedBy { it.name.lowercase() }
                    ?.mapNotNull { directory -> runCatching { read(directory) }.getOrNull() }
                    .orEmpty()
            }.orEmpty()
    }

    fun read(directory: File): StoredStory {
        val storyDirectory = directory.canonicalFile
        val root = rootDirectoryFrom(storyDirectory)
        require(storyDirectory.toPath().startsWith(root.toPath())) { "La ubicación del cuento no es válida." }
        val file = File(storyDirectory, STORY_JSON)
        require(file.isFile) { "El cuento no contiene $STORY_JSON." }
        val metadata = StoryJsonCodec.decode(JSONObject(file.readText(Charsets.UTF_8)))
        require(metadata.collection == storyDirectory.parentFile?.name) {
            "La colección indicada en el JSON no coincide con su carpeta."
        }
        return StoredStory(storyDirectory, metadata.collection, storyDirectory.name, metadata)
    }

    fun save(context: Context, collection: String, folderName: String, metadata: StoryMetadata): StoredStory {
        val safeCollection = safeSegment(collection, "La colección")
        val safeFolder = safeSegment(folderName, "El cuento")
        require(metadata.collection == safeCollection) { "La colección del JSON no coincide con la ubicación del cuento." }
        val root = rootDirectory(context).canonicalFile
        val collectionDirectory = ensureDirectory(File(root, safeCollection)).canonicalFile
        val target = File(collectionDirectory, safeFolder).canonicalFile
        require(target.toPath().startsWith(root.toPath())) { "La ubicación del cuento no es válida." }
        ensureDirectory(target)
        val encoded = StoryJsonCodec.encode(metadata).toString(2)
        writeAtomically(File(target, STORY_JSON), encoded)
        val scenesRoot = ensureDirectory(File(target, SCENES_DIRECTORY))
        metadata.scenes.forEach { scene -> ensureDirectory(File(scenesRoot, safeSegment(scene, "La escena"))) }
        return read(target)
    }

    fun deleteSceneDirectory(story: StoredStory, sceneFolderName: String) {
        val safeScene = safeSegment(sceneFolderName, "La escena")
        val storyRoot = story.directory.canonicalFile
        val scenesRoot = File(storyRoot, SCENES_DIRECTORY).canonicalFile
        val target = File(scenesRoot, safeScene).canonicalFile
        require(target.parentFile == scenesRoot && target != scenesRoot) { "La ubicación de la escena no es válida." }
        if (target.exists()) {
            check(target.isDirectory) { "El contenido de la escena no es una carpeta válida." }
            check(target.walkBottomUp().all { it.delete() || !it.exists() }) { "No se pudo eliminar todo el contenido de la escena." }
        }
    }

    /** Limpia cualquier carpeta de escena que ya no figure en el manifiesto. */
    fun deleteSceneDirectoriesExcept(story: StoredStory, activeSceneFolders: Set<String>) {
        val storyRoot = story.directory.canonicalFile
        val scenesRoot = File(storyRoot, SCENES_DIRECTORY).canonicalFile
        require(scenesRoot.parentFile == storyRoot) { "La ubicación de las escenas no es válida." }
        scenesRoot.listFiles()?.filter { it.isDirectory && it.name !in activeSceneFolders }?.forEach { directory ->
            val target = directory.canonicalFile
            require(target.parentFile == scenesRoot) { "La ubicación de una escena no es válida." }
            check(target.walkBottomUp().all { it.delete() || !it.exists() }) {
                "No se pudo eliminar todo el contenido de la escena ${directory.name}."
            }
        }
    }

    fun readScene(story: StoredStory, sceneFolderName: String): StoredStoryScene {
        val safeScene = safeSegment(sceneFolderName, "La escena")
        require(safeScene in story.metadata.scenes) { "La escena no pertenece a este cuento." }
        val storyRoot = story.directory.canonicalFile
        val sceneDirectory = File(File(storyRoot, SCENES_DIRECTORY), safeScene).canonicalFile
        require(sceneDirectory.toPath().startsWith(File(storyRoot, SCENES_DIRECTORY).canonicalFile.toPath())) {
            "La ubicación de la escena no es válida."
        }
        val file = File(sceneDirectory, SCENE_JSON)
        require(file.isFile) { "La escena no contiene $SCENE_JSON." }
        return StoredStoryScene(sceneDirectory, safeScene, StorySceneJsonCodec.decode(JSONObject(file.readText(Charsets.UTF_8))))
    }

    /**
     * Devuelve los orígenes ambientales efectivos que pueden llegar a una escena.
     * Recorre hacia atrás las escenas que heredan el ambiente y detiene cada ruta
     * en la primera escena que establece su propio ambiente (o silencio).
     */
    fun findAmbientSourceCandidates(story: StoredStory, targetSceneFolder: String): List<StoredStoryScene> {
        require(targetSceneFolder in story.metadata.scenes) { "La escena no pertenece a este cuento." }
        val incoming = mutableMapOf<String, MutableList<String>>()
        story.metadata.connections.forEach { (origin, destinations) ->
            destinations.forEach { destination ->
                incoming.getOrPut(destination) { mutableListOf() }.add(origin)
            }
        }
        val candidates = linkedMapOf<String, StoredStoryScene>()

        fun traceInheritedPath(sceneFolder: String, visited: Set<String>) {
            val predecessors = incoming[sceneFolder].orEmpty()
            if (predecessors.isEmpty()) {
                val rootScene = readScene(story, sceneFolder)
                candidates.putIfAbsent(sceneFolder, rootScene)
                return
            }
            predecessors.forEach { predecessor ->
                if (predecessor in visited) return@forEach
                val previousScene = readScene(story, predecessor)
                if (previousScene.metadata.keepPreviousAmbient) {
                    traceInheritedPath(predecessor, visited + predecessor)
                } else {
                    candidates.putIfAbsent(predecessor, previousScene)
                }
            }
        }

        incoming[targetSceneFolder].orEmpty().forEach { predecessor ->
            val previousScene = readScene(story, predecessor)
            if (previousScene.metadata.keepPreviousAmbient) {
                traceInheritedPath(predecessor, setOf(targetSceneFolder, predecessor))
            } else {
                candidates.putIfAbsent(predecessor, previousScene)
            }
        }
        return candidates.values.sortedBy { it.metadata.title.lowercase() }
    }

    /** Copia un recurso elegido por el usuario a la carpeta privada del cuento o escena. */
    fun importMedia(context: Context, source: android.net.Uri, targetDirectory: File, baseName: String): String {
        require(baseName.matches(Regex("[a-zA-Z0-9_-]+"))) { "El nombre del recurso no es válido." }
        val resolver = context.contentResolver
        val mime = resolver.getType(source).orEmpty()
        val extension = when {
            mime.equals("image/png", true) -> "png"
            mime.equals("image/webp", true) -> "webp"
            mime.equals("image/jpeg", true) -> "jpg"
            mime.equals("audio/mpeg", true) -> "mp3"
            mime.equals("audio/mp4", true) || mime.equals("audio/x-m4a", true) -> "m4a"
            mime.equals("audio/wav", true) || mime.equals("audio/x-wav", true) -> "wav"
            mime.equals("audio/ogg", true) -> "ogg"
            else -> {
                val name = resolver.query(source, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
                    ?: throw IllegalArgumentException("Formato de archivo no compatible.")
            }
        }
        if (baseName.startsWith("portada") || baseName == "ilustracion") {
            require(mime.startsWith("image/")) { "Selecciona un archivo de imagen." }
        } else {
            require(mime.startsWith("audio/")) { "Selecciona un archivo de audio." }
        }
        ensureDirectory(targetDirectory)
        val fileName = "$baseName.$extension"
        val target = File(targetDirectory, fileName)
        val temporary = File(targetDirectory, ".$fileName.tmp")
        resolver.openInputStream(source)?.use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("No se pudo abrir el archivo seleccionado.")
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IllegalStateException("No se pudo guardar el recurso multimedia.")
        }
        return fileName
    }

    fun saveScene(story: StoredStory, sceneFolderName: String, metadata: StorySceneMetadata): StoredStoryScene {
        val safeScene = safeSegment(sceneFolderName, "La escena")
        require(safeScene in story.metadata.scenes) { "La escena no pertenece a este cuento." }
        val scenesRoot = ensureDirectory(File(story.directory, SCENES_DIRECTORY)).canonicalFile
        val target = File(scenesRoot, safeScene).canonicalFile
        require(target.toPath().startsWith(scenesRoot.toPath())) { "La ubicación de la escena no es válida." }
        val encoded = StorySceneJsonCodec.encode(metadata).toString(2)
        writeAtomically(File(target, SCENE_JSON), encoded)
        return readScene(story, safeScene)
    }

    private fun rootDirectoryFrom(storyDirectory: File): File {
        val collectionDirectory = storyDirectory.parentFile
            ?: throw IllegalArgumentException("El cuento no pertenece a una colección.")
        val root = collectionDirectory.parentFile
            ?: throw IllegalArgumentException("La colección no pertenece a la biblioteca de cuentos.")
        require(root.name == ROOT) { "El cuento no pertenece a la biblioteca de cuentos." }
        return root.canonicalFile
    }

    private fun writeAtomically(target: File, content: String) {
        val parent = target.parentFile ?: throw IllegalArgumentException("La ruta del archivo no es válida.")
        ensureDirectory(parent)
        val temporary = File(parent, ".${target.name}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IllegalStateException("No se pudo guardar el archivo ${target.name}.")
        }
    }

    private fun safeSegment(value: String, description: String): String {
        val normalized = value.trim()
            .replace(Regex("[\\s]+"), "_")
            .map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }
            .joinToString("")
            .replace(Regex("_+"), "_")
            .trim('_')
        require(normalized.isNotBlank() && normalized != "." && normalized != "..") {
            "$description no válido."
        }
        return normalized
    }

    private fun ensureDirectory(directory: File): File {
        if (directory.exists()) {
            check(directory.isDirectory) { "La ruta existe, pero no es una carpeta: ${directory.absolutePath}" }
            return directory
        }
        check(directory.mkdirs() || directory.isDirectory) { "No se pudo crear la carpeta: ${directory.absolutePath}" }
        return directory
    }
}
