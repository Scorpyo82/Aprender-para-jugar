package es.aprenderparajugar

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object LibraryPackageManager {
    private const val MANIFEST = "manifest.json"
    private const val FORMAT = "aprender-para-jugar-library"
    private const val VERSION = 1
    private const val EXERCISE_FILE = "ejercicio.json"
    private const val STATISTICS_FILE = "statistics.json"

    data class SequenceExerciseInfo(val directory: String, val title: String, val type: SequenceType)
    data class CategoryInfo(
        val directory: String,
        val name: String,
        val exerciseCount: Int,
        val sequenceTypeCounts: Map<SequenceType, Int> = emptyMap(),
        val sequenceExercises: List<SequenceExerciseInfo> = emptyList()
    )

    // La selección conserva la jerarquía categoría/tipo/ejercicio, sin cambiar
    // la estructura física de carpetas del paquete.
    fun sequenceSelectionKey(directory: String, type: SequenceType): String =
        "$directory::${type.jsonValue}"

    fun sequenceExerciseSelectionKey(directory: String, type: SequenceType, exerciseDirectory: String): String =
        "$directory::${type.jsonValue}::$exerciseDirectory"

    private fun selectedCategoryDirectories(
        modality: ContentStorage.Modality,
        selection: Set<String>
    ): Set<String> = if (modality == ContentStorage.Modality.SECUENCIAS) {
        selection.map { it.substringBefore("::") }.toSet()
    } else selection

    private fun isExerciseSelected(
        modality: ContentStorage.Modality,
        categoryDirectory: String,
        exerciseDirectory: String,
        content: Any,
        selection: Set<String>
    ): Boolean {
        if (modality != ContentStorage.Modality.SECUENCIAS) return categoryDirectory in selection
        if (categoryDirectory in selection) return true
        val sequence = content as? SequenceExerciseContent ?: return false
        val typeKey = sequenceSelectionKey(categoryDirectory, sequence.type)
        return typeKey in selection ||
            sequenceExerciseSelectionKey(categoryDirectory, sequence.type, exerciseDirectory) in selection
    }
    data class ModalityInfo(
        val modality: ContentStorage.Modality,
        val categories: List<CategoryInfo>,
        val includesLearningStatistics: Boolean,
        val includesUsageStatistics: Boolean
    )
    data class StoryInfo(val key: String, val collection: String, val directory: String, val title: String, val sceneCount: Int)
    data class ObjectDependencyInfo(val key: String, val collection: String, val directory: String, val name: String)

    data class ObjectConflictInfo(
        val key: String,
        val incoming: StoredObject,
        val existing: StoredObject,
        val differences: List<String>,
        val referencingStories: List<String>
    )
    data class ExerciseConflictInfo(
        val key: String,
        val modality: ContentStorage.Modality,
        val categoryName: String,
        val incoming: StoredExercise,
        val existing: StoredExercise
    )
    data class PackageInfo(
        val modalities: List<ModalityInfo>,
        val stories: List<StoryInfo>,
        val objects: List<ObjectDependencyInfo>,
        internal val staging: File
    )

    fun categories(context: Context, modality: ContentStorage.Modality): List<CategoryInfo> {
        val root = ContentStorage.modalityDirectory(context, modality)
        return root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.mapNotNull { dir ->
            val exercises = ExerciseRepository.list(context, modality).filter { it.directory.parentFile?.canonicalFile == dir.canonicalFile }
            if (exercises.isEmpty()) null else CategoryInfo(
                dir.name,
                exercises.first().metadata.category,
                exercises.size,
                if (modality == ContentStorage.Modality.SECUENCIAS) {
                    exercises.groupingBy { (it.content as SequenceExerciseContent).type }.eachCount()
                } else emptyMap(),
                if (modality == ContentStorage.Modality.SECUENCIAS) {
                    exercises.map { stored ->
                        SequenceExerciseInfo(stored.directory.name, stored.metadata.title,
                            (stored.content as SequenceExerciseContent).type)
                    }
                } else emptyList()
            )
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    fun export(
        context: Context,
        target: Uri,
        selectedCategories: Map<ContentStorage.Modality, Set<String>>,
        learningStatisticsModalities: Set<ContentStorage.Modality>,
        usageStatisticsModalities: Set<ContentStorage.Modality>,
        selectedStories: Set<String> = emptySet(),
        selectedObjects: Set<String> = emptySet()
    ) {
        require(selectedCategories.values.any { it.isNotEmpty() } || selectedStories.isNotEmpty() || selectedObjects.isNotEmpty()) { "Selecciona al menos una categoría, cuento u objeto." }
        val resolver = context.contentResolver
        val output = resolver.openOutputStream(target)?.buffered()
            ?: throw IllegalArgumentException("No se puede escribir en el archivo seleccionado.")
        ZipOutputStream(output).use { zip ->
            val manifest = JSONObject().put("format", FORMAT).put("version", VERSION)
            val modalitiesJson = JSONArray()
            ContentStorage.Modality.entries.forEach { modality ->
                val selected = selectedCategories[modality].orEmpty()
                val selectedDirectories = selectedCategoryDirectories(modality, selected)
                if (selectedDirectories.isNotEmpty()) {
                    val categoriesJson = JSONArray()
                    selectedDirectories.sorted().forEach { categoryDirectoryName ->
                        val categoryDir = File(ContentStorage.modalityDirectory(context, modality), categoryDirectoryName).canonicalFile
                        val root = ContentStorage.modalityDirectory(context, modality).canonicalFile
                        require(categoryDir.parentFile == root && categoryDir.isDirectory) { "La categoría seleccionada ya no está disponible." }
                        val allExercises = ExerciseRepository.list(context, modality).filter { it.directory.parentFile?.canonicalFile == categoryDir }
                        val validExercises = allExercises.filter { exercise ->
                            isExerciseSelected(modality, categoryDir.name, exercise.directory.name, exercise.content, selected)
                        }
                        require(validExercises.isNotEmpty()) { "La categoría ${categoryDir.name} no contiene ejercicios válidos para la selección." }
                        validExercises.forEach { exercise ->
                            val relative = "${modality.directoryName}/${categoryDir.name}/${exercise.directory.name}"
                            addDirectory(zip, exercise.directory, relative, learningStatisticsModalities.contains(modality))
                        }
                        val categoryManifest = JSONObject().put("directory", categoryDir.name)
                            .put("name", validExercises.first().metadata.category)
                            .put("exerciseCount", validExercises.size)
                        if (modality == ContentStorage.Modality.SECUENCIAS) {
                            val counts = JSONObject()
                            validExercises.groupingBy { (it.content as SequenceExerciseContent).type.jsonValue }
                                .eachCount().forEach { (type, count) -> counts.put(type, count) }
                            categoryManifest.put("sequenceTypeCounts", counts)
                        }
                        categoriesJson.put(categoryManifest)
                    }
                    val includeLearning = learningStatisticsModalities.contains(modality)
                    val includeUsage = usageStatisticsModalities.contains(modality)
                    if (includeUsage) {
                        // Si aún no se ha completado ninguna partida, crea y exporta
                        // el esquema vacío para que la copia de seguridad sea restaurable.
                        UsageStatisticsRepository.read(context, modality)
                        val usageFile = File(ContentStorage.modalityDirectory(context, modality), UsageStatisticsRepository.FILE_NAME)
                        UsageStatisticsRepository.validateFile(usageFile, modality)
                        zip.putNextEntry(ZipEntry("${modality.directoryName}/${UsageStatisticsRepository.FILE_NAME}"))
                        usageFile.inputStream().buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                    modalitiesJson.put(JSONObject().put("id", modality.directoryName)
                        .put("includesLearningStatistics", includeLearning)
                        .put("includesUsageStatistics", includeUsage)
                        .put("categories", categoriesJson))
                }
            }
            manifest.put("modalities", modalitiesJson)

            // Los cuentos se exportan como unidades completas: estructura narrativa,
            // escenas y recursos propios. Solo se incluyen los objetos realmente
            // referenciados por los cuentos seleccionados; nunca el inventario.
            val storiesJson = JSONArray()
            val selectedStoredStories = StoryRepository.list(context).filter {
                "${it.collection}/${it.folderName}" in selectedStories
            }
            require(selectedStoredStories.size == selectedStories.size) {
                "Uno o varios cuentos seleccionados ya no están disponibles."
            }
            val objectDependencies = linkedMapOf<String, StoredObject>()
            selectedStoredStories.forEach { story ->
                val storyPrefix = "cuentos/${story.collection}/${story.folderName}"
                addDirectory(zip, story.directory, storyPrefix, includeStatistics = false)

                val referencedObjects = linkedSetOf<String>()
                addTransitionObjectReferences(story.metadata, referencedObjects)
                story.metadata.scenes.forEach { sceneName ->
                    // Un cuento puede estar guardado a medio crear. Si todavía no
                    // existe escena.json, se conserva su carpeta sin exigir contenido.
                    val sceneFile = File(File(File(story.directory, "escenas"), sceneName), "escena.json")
                    if (sceneFile.isFile) {
                        val scene = StoryRepository.readScene(story, sceneName)
                        (listOfNotNull(scene.metadata.awardedObject, scene.metadata.requiredObject) + scene.metadata.awardedObjects + scene.metadata.unlockedObjects)
                            .forEach { referencedObjects.add(it) }
                    }
                }
                referencedObjects.forEach { folderName ->
                    val storedObject = ObjectRepository.list(context).firstOrNull { it.folderName == folderName }
                        ?: throw IllegalArgumentException("No se encuentra el objeto necesario '$folderName' del cuento '${story.metadata.title}'.")
                    objectDependencies["${storedObject.collection}/${storedObject.folderName}"] = storedObject
                }
                storiesJson.put(JSONObject()
                    .put("collection", story.collection)
                    .put("directory", story.folderName)
                    .put("title", story.metadata.title)
                    .put("sceneCount", story.metadata.scenes.size))
            }
            val availableObjects = ObjectRepository.list(context)
            val selectedStoredObjects = availableObjects.filter { "${it.collection}/${it.folderName}" in selectedObjects }
            require(selectedStoredObjects.size == selectedObjects.size) {
                "Uno o varios objetos seleccionados ya no están disponibles."
            }
            selectedStoredObjects.forEach { storedObject ->
                objectDependencies["${storedObject.collection}/${storedObject.folderName}"] = storedObject
            }
            selectedStoredStories.map { it.collection }.distinct().forEach { collection ->
                StoryRepository.collectionCoverFile(context, collection)?.let { cover ->
                    zip.putNextEntry(ZipEntry("cuentos/$collection/${cover.name}"))
                    cover.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            val objectsJson = JSONArray()
            objectDependencies.values.forEach { storedObject ->
                addDirectory(zip, storedObject.directory,
                    "objetos/${storedObject.collection}/${storedObject.folderName}", includeStatistics = false)
                objectsJson.put(JSONObject()
                    .put("collection", storedObject.collection)
                    .put("directory", storedObject.folderName)
                    .put("name", storedObject.metadata.name)
                    .put("image", storedObject.metadata.image)
                    .put("availableInStore", storedObject.metadata.availableInStore)
                    .put("storePriceMinutes", storedObject.metadata.storePriceMinutes)
                    .put("collectible", storedObject.metadata.collectible)
                    .put("requiresUnlock", storedObject.metadata.requiresUnlock))
            }
            manifest.put("stories", storiesJson)
            manifest.put("objectDependencies", objectsJson)

            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun inspect(
        context: Context,
        source: Uri,
        onProgress: ((message: String, current: Int, total: Int) -> Unit)? = null
    ): PackageInfo {
        val staging = File(context.cacheDir, "library-import-${System.nanoTime()}")
        check(staging.mkdirs()) { "No se pudo preparar la validación del archivo." }
        try {
            val entries = mutableSetOf<String>()
            var extractedEntries = 0
            ZipInputStream(context.contentResolver.openInputStream(source)?.buffered()
                ?: throw IllegalArgumentException("No se puede leer el archivo seleccionado.")).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(isSafeZipPath(name)) { "El ZIP contiene una ruta no válida." }
                    require(entries.add(name)) { "El ZIP contiene rutas duplicadas." }
                    val destination = File(staging, name).canonicalFile
                    require(destination.toPath().startsWith(staging.canonicalFile.toPath())) { "El ZIP contiene una ruta fuera del paquete." }
                    if (entry.isDirectory) {
                        check(destination.mkdirs() || destination.isDirectory)
                    } else {
                        destination.parentFile?.let { check(it.mkdirs() || it.isDirectory) }
                        onProgress?.invoke("Descomprimiendo: ${entry.name}", 0, 0)
                        destination.outputStream().buffered().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var copiedBytes = 0L
                            var lastProgressBytes = 0L
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                copiedBytes += read
                                if (copiedBytes - lastProgressBytes >= 512L * 1024L) {
                                    val entrySize = entry.size
                                    if (entrySize > 0L) {
                                        val totalBytes = entrySize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                        val currentBytes = copiedBytes.coerceAtMost(totalBytes.toLong()).toInt()
                                        onProgress?.invoke("Descomprimiendo: ${entry.name}", currentBytes, totalBytes)
                                    } else {
                                        onProgress?.invoke("Descomprimiendo: ${entry.name} · ${copiedBytes / 1024L} KB", 0, 0)
                                    }
                                    lastProgressBytes = copiedBytes
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    extractedEntries++
                    if (extractedEntries == 1 || extractedEntries % 5 == 0) {
                        onProgress?.invoke("Descomprimiendo paquete · $extractedEntries archivos", 0, 0)
                    }
                }
            }
            onProgress?.invoke("Comprobando la estructura del paquete…", 0, 0)
            val manifestFile = File(staging, MANIFEST)
            require(manifestFile.isFile) { "El archivo no contiene el manifiesto de biblioteca." }
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            require(manifest.optString("format") == FORMAT && manifest.optInt("version", -1) == VERSION) {
                "El formato o la versión del paquete no son compatibles."
            }
            val modalitiesJson = manifest.optJSONArray("modalities")
                ?: throw IllegalArgumentException("El manifiesto no declara bibliotecas.")
            // Un paquete puede contener exclusivamente cuentos, sin modalidades
            // de ejercicios. La existencia de contenido se comprueba tras leer ambas secciones.
            val storiesJson = manifest.optJSONArray("stories") ?: JSONArray()
            val objectsJson = manifest.optJSONArray("objectDependencies") ?: JSONArray()
            val exerciseValidationTotal = (0 until modalitiesJson.length()).sumOf { modalityIndex ->
                val modalityItem = modalitiesJson.getJSONObject(modalityIndex)
                val modality = ContentStorage.Modality.entries.first { it.directoryName == modalityItem.optString("id") }
                val categoryItems = modalityItem.optJSONArray("categories") ?: JSONArray()
                (0 until categoryItems.length()).sumOf { categoryIndex ->
                    val directory = categoryItems.getJSONObject(categoryIndex).optString("directory")
                    File(staging, "${modality.directoryName}/$directory").listFiles()?.count { it.isDirectory } ?: 0
                }
            }
            val validationTotal = (exerciseValidationTotal + modalitiesJson.length() + storiesJson.length() + objectsJson.length()).coerceAtLeast(1)
            var validationProgress = 0
            val seenModalities = mutableSetOf<ContentStorage.Modality>()
            val result = (0 until modalitiesJson.length()).map { index ->
                val item = modalitiesJson.getJSONObject(index)
                val modality = ContentStorage.Modality.entries.firstOrNull { it.directoryName == item.optString("id") }
                    ?: throw IllegalArgumentException("El paquete contiene una modalidad desconocida.")
                require(seenModalities.add(modality)) { "El manifiesto repite una modalidad." }
                val categoriesJson = item.optJSONArray("categories")
                    ?: throw IllegalArgumentException("Faltan las categorías de ${modality.directoryName}.")
                val seenDirectories = mutableSetOf<String>()
                val categories = (0 until categoriesJson.length()).map { categoryIndex ->
                    val category = categoriesJson.getJSONObject(categoryIndex)
                    val directory = category.optString("directory")
                    val name = category.optString("name")
                    require(isSafeSegment(directory) && name.isNotBlank()) { "El paquete contiene una categoría no válida." }
                    require(seenDirectories.add(directory)) { "El paquete repite una categoría." }
                    val categoryDir = File(staging, "${modality.directoryName}/$directory")
                    require(categoryDir.isDirectory) { "Falta la carpeta de categoría $name." }
                    val exerciseDirs = categoryDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
                    require(exerciseDirs.isNotEmpty()) { "La categoría $name no contiene ejercicios." }
                    val storedExercises = exerciseDirs.map { ExerciseRepository.read(it) }
                    storedExercises.forEach { stored ->
                        val exerciseDir = stored.directory
                        require(stored.metadata.modality == modality && stored.metadata.category == name) {
                            "Los metadatos de ${exerciseDir.name} no coinciden con su ubicación."
                        }
                        validateAssets(stored.content, exerciseDir)
                        validationProgress++
                        onProgress?.invoke("Validando ejercicio: ${stored.metadata.title}", validationProgress, validationTotal)
                        val stats = File(exerciseDir, STATISTICS_FILE)
                        if (stats.exists()) {
                            require(item.optBoolean("includesLearningStatistics")) { "El paquete contiene estadísticas de aprendizaje no declaradas." }
                            val json = JSONObject(stats.readText(Charsets.UTF_8))
                            require(json.optInt("schemaVersion", -1) == 1) { "Las estadísticas de ${stored.metadata.title} no son compatibles." }
                        }
                    }
                    val sequenceCounts = if (modality == ContentStorage.Modality.SECUENCIAS) {
                        storedExercises.groupingBy { (it.content as SequenceExerciseContent).type }.eachCount()
                    } else emptyMap()
                    val declaredCounts = category.optJSONObject("sequenceTypeCounts")
                    if (modality == ContentStorage.Modality.SECUENCIAS && declaredCounts != null) {
                        val countsMatch = declaredCounts.length() == sequenceCounts.size &&
                            sequenceCounts.all { (type, count) -> declaredCounts.optInt(type.jsonValue, -1) == count }
                        require(countsMatch) {
                            "El recuento por tipo de la categoría $name no coincide con sus ejercicios."
                        }
                    }
                    val sequenceExercises = if (modality == ContentStorage.Modality.SECUENCIAS) {
                        storedExercises.map { stored ->
                            SequenceExerciseInfo(stored.directory.name, stored.metadata.title,
                                (stored.content as SequenceExerciseContent).type)
                        }
                    } else emptyList()
                    CategoryInfo(directory, name, exerciseDirs.size, sequenceCounts, sequenceExercises)
                }
                val includesUsage = item.optBoolean("includesUsageStatistics")
                val usageFile = File(staging, "${modality.directoryName}/${UsageStatisticsRepository.FILE_NAME}")
                if (usageFile.exists()) {
                    require(includesUsage) { "El paquete contiene estadísticas de uso no declaradas." }
                    UsageStatisticsRepository.validateFile(usageFile, modality)
                }
                validationProgress++
                onProgress?.invoke("Validando biblioteca ${modality.directoryName}", validationProgress, validationTotal)
                ModalityInfo(modality, categories, item.optBoolean("includesLearningStatistics"), includesUsage)
            }

            val storyInfos = (0 until storiesJson.length()).map { index ->
                val item = storiesJson.getJSONObject(index)
                val collection = item.optString("collection")
                val directory = item.optString("directory")
                val title = item.optString("title")
                require(isSafeSegment(collection) && isSafeSegment(directory) && title.isNotBlank()) {
                    "El paquete contiene un cuento con datos no válidos."
                }
                val key = "$collection/$directory"
                val storyDirectory = File(staging, "cuentos/$key")
                require(storyDirectory.isDirectory && File(storyDirectory, "cuento.json").isFile) {
                    "Faltan los datos del cuento $title."
                }
                val storedStory = StoryRepository.read(storyDirectory)
                require(storedStory.collection == collection && storedStory.folderName == directory &&
                    storedStory.metadata.title == title && storedStory.metadata.scenes.size == item.optInt("sceneCount", -1)) {
                    "Los metadatos del cuento $title no coinciden con el manifiesto."
                }
                storedStory.metadata.scenes.forEach { sceneName ->
                    val sceneDir = File(File(storyDirectory, "escenas"), sceneName)
                    val sceneJson = File(sceneDir, "escena.json")
                    if (sceneJson.isFile) {
                        val scene = StorySceneJsonCodec.decode(JSONObject(sceneJson.readText(Charsets.UTF_8)))
                        listOfNotNull(scene.illustration, scene.ambientAudio, scene.narrationAudio).forEach { resource ->
                            require(isSafeSegment(resource) && File(sceneDir, resource).isFile) {
                                "Falta el recurso $resource de la escena $sceneName."
                            }
                        }
                    }
                }
                storedStory.metadata.cover?.let { cover ->
                    require(File(storyDirectory, cover).isFile) { "Falta la portada del cuento $title." }
                }
                validationProgress++
                onProgress?.invoke("Validando cuento: $title", validationProgress, validationTotal)
                StoryInfo(key, collection, directory, title, storedStory.metadata.scenes.size)
            }
            require(storyInfos.map { it.key }.distinct().size == storyInfos.size) { "El manifiesto repite cuentos." }
            val objectInfos = (0 until objectsJson.length()).map { index ->
                val item = objectsJson.getJSONObject(index)
                val collection = item.optString("collection")
                val directory = item.optString("directory")
                val name = item.optString("name")
                require(isSafeSegment(collection) && isSafeSegment(directory) && name.isNotBlank()) {
                    "El paquete contiene una dependencia de objeto no válida."
                }
                val key = "$collection/$directory"
                val objectDir = File(staging, "objetos/$key")
                val storedObject = ObjectRepository.read(objectDir)
                require(storedObject.metadata.name == name && storedObject.metadata.image == item.optString("image")) {
                    "Los datos del objeto $name no coinciden con el manifiesto."
                }
                validationProgress++
                onProgress?.invoke("Validando objeto: $name", validationProgress, validationTotal)
                ObjectDependencyInfo(key, collection, directory, name)
            }
            require(objectInfos.map { it.key }.distinct().size == objectInfos.size) { "El manifiesto repite objetos." }
            require(modalitiesJson.length() > 0 || storyInfos.isNotEmpty() || objectInfos.isNotEmpty()) {
                "El paquete no contiene bibliotecas, cuentos ni objetos."
            }

            // Comprueba que todas las referencias de objetos del cuento estén resueltas
            // dentro del paquete; evita importar historias con dependencias rotas.
            storyInfos.forEach { storyInfo ->
                val story = StoryRepository.read(File(staging, "cuentos/${storyInfo.key}"))
                val referenced = linkedSetOf<String>()
                addTransitionObjectReferences(story.metadata, referenced)
                story.metadata.scenes.forEach { sceneName ->
                    val sceneFile = File(File(File(story.directory, "escenas"), sceneName), "escena.json")
                    if (sceneFile.isFile) {
                        val scene = StorySceneJsonCodec.decode(JSONObject(sceneFile.readText(Charsets.UTF_8)))
                        (listOfNotNull(scene.awardedObject, scene.requiredObject) + scene.awardedObjects + scene.unlockedObjects).forEach { referenced.add(it) }
                    }
                }
                val packagedNames = objectInfos.map { it.directory }.toSet()
                require(referenced.all { it in packagedNames }) {
                    "El cuento ${storyInfo.title} referencia un objeto que no está incluido en el ZIP."
                }
            }
            return PackageInfo(result, storyInfos, objectInfos, staging)
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    /**
     * Devuelve las dependencias de objetos de los cuentos locales seleccionados,
     * agrupadas por clave de objeto y con los títulos de los cuentos que las requieren.
     */
    fun requiredExportObjects(context: Context, selectedStories: Set<String>): Map<String, List<String>> {
        if (selectedStories.isEmpty()) return emptyMap()
        val availableObjects = ObjectRepository.list(context)
        val requiredByObject = linkedMapOf<String, MutableSet<String>>()
        StoryRepository.list(context)
            .filter { "${it.collection}/${it.folderName}" in selectedStories }
            .forEach { story ->
                val referencedNames = linkedSetOf<String>()
                addTransitionObjectReferences(story.metadata, referencedNames)
                story.metadata.scenes.forEach { sceneName ->
                    val sceneFile = File(File(File(story.directory, "escenas"), sceneName), "escena.json")
                    if (sceneFile.isFile) {
                        val scene = StoryRepository.readScene(story, sceneName)
                        (listOfNotNull(scene.metadata.awardedObject, scene.metadata.requiredObject) +
                            scene.metadata.awardedObjects + scene.metadata.unlockedObjects).forEach { referencedNames.add(it) }
                    }
                }
                referencedNames.forEach { folderName ->
                    val storedObject = availableObjects.firstOrNull { it.folderName == folderName }
                    if (storedObject != null) {
                        requiredByObject.getOrPut("${storedObject.collection}/${storedObject.folderName}") { linkedSetOf() }
                            .add(story.metadata.title)
                    }
                }
            }
        return requiredByObject.mapValues { it.value.toList() }
    }

    /**
     * Devuelve las dependencias de objetos de los cuentos seleccionados en el ZIP.
     */
    fun requiredImportObjects(packageInfo: PackageInfo, selectedStories: Set<String>): Map<String, List<String>> {
        if (selectedStories.isEmpty()) return emptyMap()
        val requiredByDirectory = linkedMapOf<String, MutableSet<String>>()
        packageInfo.stories.filter { it.key in selectedStories }.forEach { info ->
            val story = StoryRepository.read(File(packageInfo.staging, "cuentos/${info.key}"))
            val referencedNames = linkedSetOf<String>()
            addTransitionObjectReferences(story.metadata, referencedNames)
            story.metadata.scenes.forEach { sceneName ->
                val sceneFile = File(File(File(story.directory, "escenas"), sceneName), "escena.json")
                if (sceneFile.isFile) {
                    val scene = StorySceneJsonCodec.decode(JSONObject(sceneFile.readText(Charsets.UTF_8)))
                    (listOfNotNull(scene.awardedObject, scene.requiredObject) +
                        scene.awardedObjects + scene.unlockedObjects).forEach { referencedNames.add(it) }
                }
            }
            referencedNames.forEach { directory ->
                if (packageInfo.objects.any { it.directory == directory }) {
                    requiredByDirectory.getOrPut(directory) { linkedSetOf() }.add(info.title)
                }
            }
        }
        return packageInfo.objects.mapNotNull { objectInfo ->
            requiredByDirectory[objectInfo.directory]?.let { objectInfo.key to it.toList() }
        }.toMap()
    }

    /**
     * Compara las dependencias de los cuentos seleccionados con los objetos locales.
     * La identidad se determina por el nombre de carpeta; la imagen se compara por bytes.
     */
    fun objectConflicts(
        context: Context,
        packageInfo: PackageInfo,
        selectedStories: Set<String>,
        selectedObjects: Set<String> = emptySet(),
        localObjectsOverride: List<StoredObject>? = null
    ): List<ObjectConflictInfo> {
        val requiredNames = linkedSetOf<String>()
        packageInfo.objects.filter { it.key in selectedObjects || it.directory in selectedObjects }
            .mapTo(requiredNames) { it.directory }
        packageInfo.stories.filter { it.key in selectedStories }.forEach { info ->
            val story = StoryRepository.read(File(packageInfo.staging, "cuentos/${info.key}"))
            addTransitionObjectReferences(story.metadata, requiredNames)
            story.metadata.scenes.forEach { sceneName ->
                val sceneFile = File(File(File(story.directory, "escenas"), sceneName), "escena.json")
                if (sceneFile.isFile) {
                    val scene = StorySceneJsonCodec.decode(JSONObject(sceneFile.readText(Charsets.UTF_8)))
                    (listOfNotNull(scene.awardedObject, scene.requiredObject) + scene.awardedObjects + scene.unlockedObjects).forEach { requiredNames.add(it) }
                }
            }
        }
        val localObjects = localObjectsOverride ?: ObjectRepository.list(context)
        return packageInfo.objects.filter { it.directory in requiredNames }.mapNotNull { dependency ->
            val incoming = ObjectRepository.read(File(packageInfo.staging, "objetos/${dependency.key}"))
            val existing = localObjects.firstOrNull {
                it.collection == incoming.collection && NameRules.key(it.metadata.name) == NameRules.key(incoming.metadata.name)
            } ?: return@mapNotNull null
            val sameImageBytes = runCatching {
                File(existing.directory, existing.metadata.image).readBytes().contentEquals(
                    File(incoming.directory, incoming.metadata.image).readBytes()
                )
            }.getOrDefault(false)
            val differences = buildList {
                if (existing.metadata.name != incoming.metadata.name) add("Nombre: «${existing.metadata.name}» → «${incoming.metadata.name}»")
                if (existing.metadata.image != incoming.metadata.image) add("Archivo de imagen: ${existing.metadata.image} → ${incoming.metadata.image}")
                if (!sameImageBytes) add("El contenido de la imagen es diferente")
                if (existing.metadata.availableInStore != incoming.metadata.availableInStore) add("Aparece en la tienda: ${if (existing.metadata.availableInStore) "Sí" else "No"} → ${if (incoming.metadata.availableInStore) "Sí" else "No"}")
                if (existing.metadata.storePriceMinutes != incoming.metadata.storePriceMinutes) add("Precio: ${existing.metadata.storePriceMinutes} min → ${incoming.metadata.storePriceMinutes} min")
                if (existing.metadata.collectible != incoming.metadata.collectible) add("Es coleccionable: ${if (existing.metadata.collectible) "Sí" else "No"} → ${if (incoming.metadata.collectible) "Sí" else "No"}")
            }
            val displayedDifferences = if (differences.isEmpty()) {
                listOf("Las dos fichas tienen el mismo nombre, imagen, precio, disponibilidad y tipo de objeto.")
            } else differences
            val references = StoryRepository.list(context).filter { story ->
                val refs = linkedSetOf<String>()
                addTransitionObjectReferences(story.metadata, refs)
                story.metadata.scenes.forEach { sceneName ->
                    val sceneFile = File(File(File(story.directory, "escenas"), sceneName), "escena.json")
                    if (sceneFile.isFile) runCatching {
                        val scene = StorySceneJsonCodec.decode(JSONObject(sceneFile.readText(Charsets.UTF_8)))
                        (listOfNotNull(scene.awardedObject, scene.requiredObject) + scene.awardedObjects + scene.unlockedObjects).forEach { refs.add(it) }
                    }
                }
                existing.folderName in refs
            }.map { "${it.metadata.title} (${it.collection})" }
            ObjectConflictInfo(dependency.key, incoming, existing, displayedDifferences, references)
        }
    }

    fun exerciseConflicts(
        context: Context,
        packageInfo: PackageInfo,
        selectedCategories: Map<ContentStorage.Modality, Set<String>>,
        localExercisesByModality: Map<ContentStorage.Modality, List<StoredExercise>>? = null
    ): List<ExerciseConflictInfo> {
        val conflicts = mutableListOf<ExerciseConflictInfo>()
        packageInfo.modalities.forEach { modalityInfo ->
            val modality = modalityInfo.modality
            val localExercises = localExercisesByModality?.get(modality) ?: ExerciseRepository.list(context, modality)
            val selected = selectedCategories[modality].orEmpty()
            modalityInfo.categories.filter { it.directory in selectedCategoryDirectories(modality, selected) }.forEach { category ->
                val sourceCategory = File(packageInfo.staging, "${modality.directoryName}/${category.directory}")
                val targetCategory = File(ContentStorage.modalityDirectory(context, modality), category.directory)
                sourceCategory.listFiles()?.filter { it.isDirectory }?.forEach { incomingDirectory ->
                    val incoming = ExerciseRepository.read(incomingDirectory)
                    if (!isExerciseSelected(modality, category.directory, incomingDirectory.name, incoming.content, selected)) return@forEach
                    val matching = localExercises.firstOrNull {
                        it.metadata.category.equals(incoming.metadata.category, true) &&
                            it.metadata.title.equals(incoming.metadata.title, true)
                    }
                    val pathCollision = localExercises.firstOrNull {
                        runCatching { it.directory.canonicalFile == File(targetCategory, incomingDirectory.name).canonicalFile }.getOrDefault(false)
                    }
                    val existing = matching ?: pathCollision
                    if (existing != null) {
                        val key = "${modality.directoryName}/${category.directory}/${incomingDirectory.name}"
                        conflicts.add(ExerciseConflictInfo(key, modality, category.name, incoming, existing))
                    }
                }
            }
        }
        return conflicts
    }

    fun import(
        context: Context,
        packageInfo: PackageInfo,
        selectedCategories: Map<ContentStorage.Modality, Set<String>>,
        replaceSameName: Boolean,
        importLearningStatistics: Boolean,
        importUsageStatistics: Boolean,
        selectedStories: Set<String> = emptySet(),
        objectConflictDecisions: Map<String, String> = emptyMap(),
        storyConflictDecisions: Map<String, String> = emptyMap(),
        selectedObjects: Set<String> = emptySet(),
        exerciseConflictDecisions: Map<String, String> = emptyMap(),
        onProgress: ((message: String, current: Int, total: Int) -> Unit)? = null
    ): Int {
        var imported = 0
        val rollbackTargets = mutableListOf<Pair<File, File?>>()
        try {
            val selectedStoryInfos = packageInfo.stories.filter { it.key in selectedStories }
            val localExercisesByModality = packageInfo.modalities.associate { modalityInfo ->
                modalityInfo.modality to ExerciseRepository.list(context, modalityInfo.modality).toMutableList()
            }.toMutableMap()
            val unresolvedExerciseConflicts = exerciseConflicts(context, packageInfo, selectedCategories, localExercisesByModality)
                .filter { exerciseConflictDecisions[it.key] !in setOf("reuse", "overwrite") }
            require(unresolvedExerciseConflicts.isEmpty()) {
                "Debes resolver los conflictos de ejercicios antes de importar."
            }
            require(selectedStories.all { key -> packageInfo.stories.any { it.key == key } }) {
                "Uno de los cuentos seleccionados no está disponible en el paquete."
            }
            val selectedObjectNames = linkedSetOf<String>()
            packageInfo.objects.filter { it.key in selectedObjects || it.directory in selectedObjects }
                .mapTo(selectedObjectNames) { it.directory }
            selectedStoryInfos.forEach { info ->
                val story = StoryRepository.read(File(packageInfo.staging, "cuentos/${info.key}"))
                addTransitionObjectReferences(story.metadata, selectedObjectNames)
                story.metadata.scenes.forEach { sceneName ->
                    val sceneFile = File(File(File(story.directory, "escenas"), sceneName), "escena.json")
                    if (sceneFile.isFile) {
                        val scene = StorySceneJsonCodec.decode(JSONObject(sceneFile.readText(Charsets.UTF_8)))
                        (listOfNotNull(scene.awardedObject, scene.requiredObject) + scene.awardedObjects + scene.unlockedObjects).forEach { selectedObjectNames.add(it) }
                    }
                }
            }
            val selectedObjectInfos = packageInfo.objects.filter { it.directory in selectedObjectNames }
            val selectedExerciseCount = packageInfo.modalities.sumOf { modalityInfo ->
                val modality = modalityInfo.modality
                val selected = selectedCategories[modality].orEmpty()
                modalityInfo.categories.filter { it.directory in selectedCategoryDirectories(modality, selected) }.sumOf { category ->
                    if (modality != ContentStorage.Modality.SECUENCIAS) {
                        category.exerciseCount
                    } else {
                        category.sequenceExercises.count { exercise ->
                            category.directory in selected ||
                                sequenceSelectionKey(category.directory, exercise.type) in selected ||
                                sequenceExerciseSelectionKey(category.directory, exercise.type, exercise.directory) in selected
                        }
                    }
                }
            }
            val usageStatisticsCount = packageInfo.modalities.count { modalityInfo ->
                selectedCategories[modalityInfo.modality].orEmpty().isNotEmpty() && importUsageStatistics && modalityInfo.includesUsageStatistics &&
                    File(packageInfo.staging, "${modalityInfo.modality.directoryName}/${UsageStatisticsRepository.FILE_NAME}").isFile
            }
            val progressTotal = (selectedObjectInfos.size + selectedStoryInfos.size + selectedExerciseCount + usageStatisticsCount).coerceAtLeast(1)
            var progressCurrent = 0
            fun reportProgress(message: String) {
                progressCurrent++
                onProgress?.invoke(message, progressCurrent, progressTotal)
            }
            onProgress?.invoke("Preparando la importación…", 0, progressTotal)
            val localObjects = ObjectRepository.list(context).toMutableList()
            val objectReferenceRemapping = mutableMapOf<String, String>()
            selectedObjectInfos.forEach { incoming ->
                val source = File(packageInfo.staging, "objetos/${incoming.key}")
                val incomingObject = ObjectRepository.read(source)
                val existing = localObjects.firstOrNull {
                    it.collection == incomingObject.collection &&
                        NameRules.key(it.metadata.name) == NameRules.key(incomingObject.metadata.name)
                }
                if (existing != null) {
                    val sameDefinition = existing.metadata.name == incomingObject.metadata.name &&
                        existing.metadata.image == incomingObject.metadata.image &&
                        existing.metadata.availableInStore == incomingObject.metadata.availableInStore &&
                        existing.metadata.storePriceMinutes == incomingObject.metadata.storePriceMinutes &&
                        existing.metadata.collectible == incomingObject.metadata.collectible &&
                        existing.metadata.requiresUnlock == incomingObject.metadata.requiresUnlock &&
                        runCatching { File(existing.directory, existing.metadata.image).readBytes().contentEquals(
                            File(incomingObject.directory, incomingObject.metadata.image).readBytes()
                        ) }.getOrDefault(false)
                    if (!sameDefinition) {
                        require(objectConflictDecisions[incoming.directory] in setOf("reuse", "overwrite")) {
                            "Conflicto con el objeto «${incomingObject.metadata.name}». Debes elegir cuál conservar."
                        }
                    }
                    objectReferenceRemapping[incoming.directory] = existing.folderName
                }
            }
            val localStories = StoryRepository.list(context).toMutableList()
            selectedStoryInfos.forEach { incoming ->
                val existing = localStories.firstOrNull {
                    it.collection == incoming.collection && NameRules.key(it.metadata.title) == NameRules.key(incoming.title)
                }
                if (existing != null) {
                    require(storyConflictDecisions[incoming.key] == "overwrite") {
                        "Ya existe el cuento «${incoming.title}» en «${incoming.collection}». Debes confirmar su sobrescritura completa o cancelar."
                    }
                }
            }

            // Importa primero las definiciones de objetos requeridas. No toca nunca
            // el inventario local del niño: solo se copia la biblioteca de objetos.
            selectedObjectInfos.forEach { incoming ->
                val source = File(packageInfo.staging, "objetos/${incoming.key}")
                val incomingObject = ObjectRepository.read(source)
                val existing = localObjects.firstOrNull {
                    it.collection == incomingObject.collection &&
                        NameRules.key(it.metadata.name) == NameRules.key(incomingObject.metadata.name)
                }
                val decision = objectConflictDecisions[incoming.directory]
                val identical = existing != null &&
                    existing.metadata.name == incomingObject.metadata.name &&
                    existing.metadata.image == incomingObject.metadata.image &&
                    existing.metadata.availableInStore == incomingObject.metadata.availableInStore &&
                    existing.metadata.storePriceMinutes == incomingObject.metadata.storePriceMinutes &&
                    existing.metadata.collectible == incomingObject.metadata.collectible &&
                    runCatching {
                        File(existing.directory, existing.metadata.image).readBytes().contentEquals(
                            File(incomingObject.directory, incomingObject.metadata.image).readBytes()
                        )
                    }.getOrDefault(false)
                if (existing != null && (decision == "reuse" || identical)) {
                    reportProgress("Objeto ya existente: ${incomingObject.metadata.name}")
                    return@forEach
                }
                val target = existing?.directory ?: File(File(ObjectRepository.rootDirectory(context), incoming.collection), incoming.directory)
                val temporary = File(target.parentFile, ".import-${incoming.directory}-${System.nanoTime()}")
                copyDirectory(source, temporary) { copiedFile ->
                    onProgress?.invoke("Copiando objeto ${incomingObject.metadata.name}: $copiedFile", progressCurrent, progressTotal)
                }
                if (target.exists()) {
                    val backup = File(target.parentFile, ".backup-${incoming.directory}-${System.nanoTime()}")
                    check(target.renameTo(backup)) { "No se pudo reservar el objeto anterior «${incoming.name}»." }
                    if (!temporary.renameTo(target)) {
                        backup.renameTo(target)
                        throw IllegalStateException("No se pudo sobrescribir el objeto «${incoming.name}».")
                    }
                    rollbackTargets.add(target to backup)
                } else {
                    check(temporary.renameTo(target)) { "No se pudo importar el objeto «${incoming.name}»." }
                    rollbackTargets.add(target to null)
                }
                localObjects.removeAll { it.directory.canonicalFile == target.canonicalFile }
                localObjects.add(ObjectRepository.read(target))
                reportProgress("Importando objeto: ${incomingObject.metadata.name}")
            }
            selectedStoryInfos.forEach { incoming ->
                val source = File(packageInfo.staging, "cuentos/${incoming.key}")
                if (objectReferenceRemapping.isNotEmpty()) remapStoryObjectReferences(source, objectReferenceRemapping)
                val collection = File(StoryRepository.rootDirectory(context), incoming.collection)
                check(collection.mkdirs() || collection.isDirectory)
                val existing = localStories.firstOrNull {
                    it.collection == incoming.collection && NameRules.key(it.metadata.title) == NameRules.key(incoming.title)
                }
                val target = existing?.directory ?: File(collection, incoming.directory)
                val temporary = File(collection, ".import-${incoming.directory}-${System.nanoTime()}")
                copyDirectory(source, temporary) { copiedFile ->
                    onProgress?.invoke("Copiando cuento ${incoming.title}: $copiedFile", progressCurrent, progressTotal)
                }
                if (target.exists()) {
                    val backup = File(collection, ".backup-story-${incoming.directory}-${System.nanoTime()}")
                    check(target.renameTo(backup)) { "No se pudo reservar el cuento anterior «${incoming.title}»." }
                    if (!temporary.renameTo(target)) {
                        backup.renameTo(target)
                        throw IllegalStateException("No se pudo sobrescribir el cuento «${incoming.title}».")
                    }
                    rollbackTargets.add(target to backup)
                } else {
                    check(temporary.renameTo(target)) { "No se pudo importar el cuento «${incoming.title}»." }
                    rollbackTargets.add(target to null)
                }
                localStories.removeAll {
                    it.directory.canonicalFile == target.canonicalFile ||
                        (it.collection == incoming.collection && NameRules.key(it.metadata.title) == NameRules.key(incoming.title))
                }
                localStories.add(StoryRepository.read(target))
                reportProgress("Importando cuento: ${incoming.title}")
            }
            // Las portadas viajan con los cuentos, pero nunca sustituyen una portada local.
            selectedStoryInfos.map { it.collection }.distinct().forEach { collectionName ->
                val sourceCollection = File(packageInfo.staging, "cuentos/$collectionName")
                val incomingCover = sourceCollection.listFiles()
                    ?.firstOrNull { it.isFile && it.name.startsWith("portada_coleccion.") }
                if (incomingCover != null) {
                    val targetCollection = File(StoryRepository.rootDirectory(context), collectionName)
                    check(targetCollection.mkdirs() || targetCollection.isDirectory)
                    if (StoryRepository.collectionCoverFile(context, collectionName) == null) {
                        val targetCover = File(targetCollection, incomingCover.name)
                        val temporaryCover = File(targetCollection, ".${incomingCover.name}.import-${System.nanoTime()}")
                        incomingCover.copyTo(temporaryCover, overwrite = true)
                        check(temporaryCover.renameTo(targetCover)) { "No se pudo importar la portada de la colección «$collectionName»." }
                        rollbackTargets.add(targetCover to null)
                    }
                }
            }
            packageInfo.modalities.forEach { modalityInfo ->
                val modality = modalityInfo.modality
                val selected = selectedCategories[modality].orEmpty()
                val localExercises = localExercisesByModality.getValue(modality)
                modalityInfo.categories.filter { it.directory in selectedCategoryDirectories(modality, selected) }.forEach { category ->
                    val sourceCategory = File(packageInfo.staging, "${modality.directoryName}/${category.directory}")
                    val targetCategory = File(ContentStorage.modalityDirectory(context, modality), category.directory)
                    val categoryExisted = targetCategory.isDirectory
                    check(targetCategory.mkdirs() || targetCategory.isDirectory) { "No se pudo crear la categoría ${category.name}." }
                    if (!categoryExisted) rollbackTargets.add(targetCategory to null)
                    sourceCategory.listFiles()?.filter { it.isDirectory }?.forEach { incoming ->
                        val incomingStored = ExerciseRepository.read(incoming)
                        if (!isExerciseSelected(modality, category.directory, incoming.name, incomingStored.content, selected)) return@forEach
                        val existing = localExercises.firstOrNull {
                            it.metadata.category.equals(category.name, true) &&
                                it.metadata.title.equals(incomingStored.metadata.title, true)
                        }
                        val conflictKey = "${modality.directoryName}/${category.directory}/${incoming.name}"
                        val pathExisting = localExercises.firstOrNull {
                            runCatching { it.directory.canonicalFile == File(targetCategory, incoming.name).canonicalFile }.getOrDefault(false)
                        }
                        val conflictExisting = existing ?: pathExisting
                        val decision = exerciseConflictDecisions[conflictKey]
                        if (conflictExisting != null && decision == "reuse") {
                            reportProgress("Ejercicio ya existente: ${incomingStored.metadata.title}")
                            return@forEach
                        }
                        val target = conflictExisting?.directory ?: File(targetCategory, incoming.name)
                        val stagingExercise = File(targetCategory, ".import-${System.nanoTime()}")
                        copyDirectory(incoming, stagingExercise) { copiedFile ->
                            onProgress?.invoke("Copiando ejercicio ${incomingStored.metadata.title}: $copiedFile", progressCurrent, progressTotal)
                        }
                        val incomingStatistics = File(stagingExercise, STATISTICS_FILE)
                        if (!importLearningStatistics) {
                            // El ZIP no debe sobrescribir las estadísticas locales si el usuario
                            // ha decidido importar únicamente los ejercicios.
                            incomingStatistics.delete()
                            if (conflictExisting != null) {
                                val existingStatistics = File(conflictExisting.directory, STATISTICS_FILE)
                                if (existingStatistics.isFile) {
                                    existingStatistics.copyTo(incomingStatistics, overwrite = true)
                                }
                            }
                        }
                        if (target.exists()) {
                            val backup = File(targetCategory, ".backup-import-${System.nanoTime()}")
                            check(target.renameTo(backup)) { "No se pudo reservar el ejercicio anterior." }
                            if (!stagingExercise.renameTo(target)) {
                                backup.renameTo(target)
                                throw IllegalStateException("No se pudo sustituir ${incomingStored.metadata.title}.")
                            }
                            rollbackTargets.add(target to backup)
                        } else {
                            check(stagingExercise.renameTo(target)) { "No se pudo incorporar ${incomingStored.metadata.title}." }
                            rollbackTargets.add(target to null)
                        }
                        imported++
                        if (conflictExisting != null) {
                            localExercises.removeAll { it.directory.canonicalFile == conflictExisting.directory.canonicalFile }
                        }
                        localExercises.add(incomingStored.copy(directory = target))
                        reportProgress("Importando ejercicio: ${incomingStored.metadata.title}")
                    }
                }
                if (selected.isNotEmpty() && importUsageStatistics && modalityInfo.includesUsageStatistics) {
                    val incomingUsage = File(packageInfo.staging, "${modality.directoryName}/${UsageStatisticsRepository.FILE_NAME}")
                    if (incomingUsage.isFile) {
                        UsageStatisticsRepository.validateFile(incomingUsage, modality)
                        val targetUsage = File(ContentStorage.modalityDirectory(context, modality), UsageStatisticsRepository.FILE_NAME)
                        val temporaryUsage = File(targetUsage.parentFile, ".${targetUsage.name}.import-${System.nanoTime()}")
                        val backupUsage = File(targetUsage.parentFile, ".${targetUsage.name}.backup-${System.nanoTime()}")
                        incomingUsage.copyTo(temporaryUsage, overwrite = true)
                        if (targetUsage.exists() && !targetUsage.renameTo(backupUsage)) {
                            temporaryUsage.delete()
                            throw IllegalStateException("No se pudieron reservar las estadísticas de uso anteriores de ${modality.directoryName}.")
                        }
                        if (!temporaryUsage.renameTo(targetUsage)) {
                            temporaryUsage.delete()
                            if (backupUsage.exists()) backupUsage.renameTo(targetUsage)
                            throw IllegalStateException("No se pudieron guardar las estadísticas de uso de ${modality.directoryName}.")
                        }
                        rollbackTargets.add(targetUsage to backupUsage.takeIf { it.exists() })
                        reportProgress("Importando estadísticas de uso: ${modality.directoryName}")
                    }
                }
            }
        rollbackTargets.forEach { (_, backup) -> backup?.deleteRecursively() }
        return imported
        } catch (error: Exception) {
            rollbackTargets.asReversed().forEach { (target, backup) ->
                target.deleteRecursively()
                if (backup != null && backup.exists()) backup.renameTo(target)
            }
            throw error
        }
    }

    private fun remapStoryObjectReferences(storyDirectory: File, mapping: Map<String, String>) {
        val storyFile = File(storyDirectory, "cuento.json")
        if (storyFile.isFile) {
            val metadata = StoryJsonCodec.decode(JSONObject(storyFile.readText(Charsets.UTF_8)))
            val remapped = metadata.copy(
                connectionConditions = metadata.connectionConditions.map { condition ->
                    condition.copy(requiredObject = condition.requiredObject?.let { mapping[it] ?: it })
                },
                connectionAlternatives = metadata.connectionAlternatives.map { alternative ->
                    alternative.copy(requiredObject = alternative.requiredObject?.let { mapping[it] ?: it })
                }
            )
            storyFile.writeText(StoryJsonCodec.encode(remapped).toString(2), Charsets.UTF_8)
        }
        val scenesRoot = File(storyDirectory, "escenas")
        scenesRoot.listFiles()?.filter { it.isDirectory }?.forEach { sceneDirectory ->
            val sceneFile = File(sceneDirectory, "escena.json")
            if (sceneFile.isFile) {
                val scene = StorySceneJsonCodec.decode(JSONObject(sceneFile.readText(Charsets.UTF_8)))
                val remapped = scene.copy(
                    awardedObject = scene.awardedObject?.let { mapping[it] ?: it },
                    requiredObject = scene.requiredObject?.let { mapping[it] ?: it },
                    awardedObjects = scene.awardedObjects.map { mapping[it] ?: it }.distinct(),
                    unlockedObjects = scene.unlockedObjects.map { mapping[it] ?: it }.distinct()
                )
                sceneFile.writeText(StorySceneJsonCodec.encode(remapped).toString(2), Charsets.UTF_8)
            }
        }
    }

    /**
     * Recoge las referencias de objetos de las transiciones usando la misma
     * precedencia por origen que el motor: alternativas nuevas o condiciones legacy.
     */
    private fun addTransitionObjectReferences(
        metadata: StoryMetadata,
        destination: MutableSet<String>
    ) {
        val configuredOrigins = metadata.connectionAlternatives.map { it.fromScene }.toSet()
        metadata.connectionAlternatives
            .filter { it.kind == "object" }
            .mapNotNullTo(destination) { it.requiredObject }
        metadata.connectionConditions
            .filterNot { it.fromScene in configuredOrigins }
            .mapNotNullTo(destination) { it.requiredObject }
    }

    fun discard(packageInfo: PackageInfo) {
        packageInfo.staging.deleteRecursively()
    }

    private fun addDirectory(zip: ZipOutputStream, source: File, prefix: String, includeStatistics: Boolean) {
        source.walkTopDown().filter { it.isFile }.forEach { file ->
            if (!includeStatistics && file.name == STATISTICS_FILE) return@forEach
            if (file.name.endsWith(".tmp") || file.name.endsWith(".bak")) return@forEach
            val relative = file.relativeTo(source).path.replace(File.separatorChar, '/')
            val name = "$prefix/$relative"
            zip.putNextEntry(ZipEntry(name))
            file.inputStream().buffered().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun validateAssets(content: Any, directory: File) {
        val references = when (content) {
            is CardExerciseContent -> listOf(content.image)
            is WordExerciseContent -> listOf(content.hintImage)
            is SequenceExerciseContent -> content.steps.map { it.image }
            else -> throw IllegalArgumentException("Contenido de ejercicio no compatible.")
        }
        references.forEach { path ->
            require(isSafeZipPath(path) && File(directory, path).isFile) { "Falta un recurso de imagen: $path." }
        }
    }

    private fun copyDirectory(
        source: File,
        destination: File,
        onFileProgress: ((fileName: String) -> Unit)? = null
    ) {
        source.walkTopDown().forEach { file ->
            val target = File(destination, file.relativeTo(source).path)
            if (file.isDirectory) check(target.mkdirs() || target.isDirectory)
            else {
                target.parentFile?.let { check(it.mkdirs() || it.isDirectory) }
                var copiedBytes = 0L
                file.inputStream().buffered().use { input ->
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var lastProgressBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copiedBytes += read
                            if (copiedBytes - lastProgressBytes >= 512L * 1024L) {
                                onFileProgress?.invoke("${file.name} · ${copiedBytes / 1024L} KB")
                                lastProgressBytes = copiedBytes
                            }
                        }
                    }
                }
                if (copiedBytes < 512L * 1024L) onFileProgress?.invoke(file.name)
            }
        }
    }

    private fun isSafeZipPath(value: String): Boolean =
        value.isNotBlank() && !value.startsWith("/") && !value.startsWith("\\") &&
            !value.contains("\\") && !value.contains(":") &&
            value.split('/').all { it.isNotBlank() && it != "." && it != ".." }

    private fun isSafeSegment(value: String): Boolean =
        value.isNotBlank() && value != "." && value != ".." &&
            !value.contains('/') && !value.contains('\\') && !value.contains(':')
}
