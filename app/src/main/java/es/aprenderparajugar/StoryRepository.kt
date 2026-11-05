package es.aprenderparajugar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Datos generales y grafo narrativo de un cuento. */
data class StoryMetadata(
    val title: String,
    val collection: String,
    val cover: String? = null,
    val startScene: String,
    val scenes: List<String>,
    val connections: Map<String, List<String>>,
    val connectionConditions: List<StoryConnectionCondition> = emptyList()
)

/** Condición configurada en una conexión; el compositor solo almacena estos datos. */
data class StoryConnectionCondition(
    val fromScene: String,
    val toScene: String,
    val requiredObject: String? = null,
    val consumeRequiredObject: Boolean = false
)

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
    val requiredObject: String? = null
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
        return JSONObject()
            .put("title", metadata.title.trim())
            .put("collection", metadata.collection)
            .put("cover", metadata.cover ?: JSONObject.NULL)
            .put("startScene", metadata.startScene)
            .put("scenes", JSONArray(metadata.scenes))
            .put("connections", connectionsJson)
            .put("connectionConditions", conditionsJson)
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
        return StoryMetadata(
            title = json.optString("title").trim(),
            collection = json.optString("collection"),
            cover = cover,
            startScene = json.optString("startScene"),
            scenes = scenes,
            connections = connections,
            connectionConditions = conditions
        ).also(::validateStoryMetadata)
    }

    private fun validateStoryMetadata(metadata: StoryMetadata) {
        require(metadata.title.isNotBlank()) { "El cuento necesita un título." }
        require(isSafeSegment(metadata.collection)) { "La colección del cuento no es válida." }
        require(isSafeSegment(metadata.startScene)) { "La escena inicial no es válida." }
        require(metadata.scenes.isNotEmpty()) { "El cuento debe contener al menos una escena." }
        require(metadata.scenes.all(::isSafeSegment)) { "Hay un nombre de escena no válido." }
        require(metadata.scenes.distinct().size == metadata.scenes.size) { "Hay nombres de escena duplicados." }
        require(metadata.startScene in metadata.scenes) { "La escena inicial no pertenece al cuento." }
        require(metadata.connections.keys.all { it in metadata.scenes }) { "Hay conexiones desde escenas desconocidas." }
        metadata.connections.forEach { (origin, destinations) ->
            require(destinations.size <= 2) { "Una escena no puede tener más de dos salidas." }
            require(destinations.distinct().size == destinations.size) { "Una escena no puede conectarse dos veces con el mismo destino." }
            require(destinations.all { it in metadata.scenes }) { "Una conexión apunta a una escena inexistente." }
            require(origin !in destinations) { "Una escena no puede conectarse consigo misma." }
        }
        val edges = metadata.connections.flatMap { (origin, destinations) -> destinations.map { origin to it } }.toSet()
        require(metadata.connectionConditions.map { it.fromScene to it.toScene }.distinct().size == metadata.connectionConditions.size) {
            "Una conexión no puede tener condiciones duplicadas."
        }
        metadata.connectionConditions.forEach { condition ->
            require((condition.fromScene to condition.toScene) in edges) { "Hay una condición asociada a una conexión inexistente." }
            require(condition.requiredObject == null || isSafeSegment(condition.requiredObject)) { "La referencia al objeto requerido no es válida." }
            require(condition.requiredObject != null || !condition.consumeRequiredObject) { "No se puede consumir un objeto si no se ha seleccionado ninguno." }
        }
        metadata.connections.forEach { (origin, destinations) ->
            if (destinations.size == 2) {
                val branchConditions = destinations.map { destination ->
                    metadata.connectionConditions.firstOrNull { it.fromScene == origin && it.toScene == destination }?.requiredObject
                }
                require(branchConditions.any { it != null }) { "Una bifurcación debe tener al menos una salida condicionada por un objeto." }
                require(branchConditions.filterNotNull().distinct().size == branchConditions.filterNotNull().size) {
                    "Las dos salidas de una bifurcación deben utilizar objetos distintos."
                }
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
            requiredObject = json.optString("requiredObject").takeNullable()
        ).also(::validate)

    private fun validate(metadata: StorySceneMetadata) {
        require(metadata.title.isNotBlank()) { "La escena necesita un nombre." }
        listOfNotNull(metadata.illustration, metadata.ambientAudio, metadata.narrationAudio)
            .forEach { require(StoryJsonCodec.isSafeFileName(it)) { "Hay un nombre de archivo multimedia no válido." } }
        require(metadata.awardTiming == "onEnter" || metadata.awardTiming == "onExit") {
            "El momento de obtención del objeto no es válido."
        }
        listOfNotNull(metadata.awardedObject, metadata.requiredObject).forEach {
            require(StoryJsonCodec.isSafeSegment(it)) { "La referencia a un objeto no es válida." }
        }
    }

    private fun String.takeNullable(): String? = takeIf { isNotBlank() && it != "null" }
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
