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

    data class CategoryInfo(val directory: String, val name: String, val exerciseCount: Int)
    data class ModalityInfo(
        val modality: ContentStorage.Modality,
        val categories: List<CategoryInfo>,
        val includesLearningStatistics: Boolean,
        val includesUsageStatistics: Boolean
    )
    data class StoryInfo(val key: String, val collection: String, val directory: String, val title: String, val sceneCount: Int)
    data class ObjectDependencyInfo(val key: String, val collection: String, val directory: String, val name: String)
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
            if (exercises.isEmpty()) null else CategoryInfo(dir.name, exercises.first().metadata.category, exercises.size)
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    fun export(
        context: Context,
        target: Uri,
        selectedCategories: Map<ContentStorage.Modality, Set<String>>,
        learningStatisticsModalities: Set<ContentStorage.Modality>,
        usageStatisticsModalities: Set<ContentStorage.Modality>,
        selectedStories: Set<String> = emptySet()
    ) {
        require(selectedCategories.values.any { it.isNotEmpty() } || selectedStories.isNotEmpty()) { "Selecciona al menos una categoría o cuento." }
        val resolver = context.contentResolver
        val output = resolver.openOutputStream(target)?.buffered()
            ?: throw IllegalArgumentException("No se puede escribir en el archivo seleccionado.")
        ZipOutputStream(output).use { zip ->
            val manifest = JSONObject().put("format", FORMAT).put("version", VERSION)
            val modalitiesJson = JSONArray()
            ContentStorage.Modality.entries.forEach { modality ->
                val selected = selectedCategories[modality].orEmpty()
                if (selected.isNotEmpty()) {
                    val categoriesJson = JSONArray()
                    selected.sorted().forEach { categoryDirectoryName ->
                        val categoryDir = File(ContentStorage.modalityDirectory(context, modality), categoryDirectoryName).canonicalFile
                        val root = ContentStorage.modalityDirectory(context, modality).canonicalFile
                        require(categoryDir.parentFile == root && categoryDir.isDirectory) { "La categoría seleccionada ya no está disponible." }
                        val validExercises = ExerciseRepository.list(context, modality).filter { it.directory.parentFile?.canonicalFile == categoryDir }
                        require(validExercises.isNotEmpty()) { "La categoría ${categoryDir.name} no contiene ejercicios válidos." }
                        validExercises.forEach { exercise ->
                            val relative = "${modality.directoryName}/${categoryDir.name}/${exercise.directory.name}"
                            addDirectory(zip, exercise.directory, relative, learningStatisticsModalities.contains(modality))
                        }
                        categoriesJson.put(JSONObject().put("directory", categoryDir.name)
                            .put("name", validExercises.first().metadata.category)
                            .put("exerciseCount", validExercises.size))
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
                story.metadata.connectionConditions.mapNotNullTo(referencedObjects) { it.requiredObject }
                story.metadata.scenes.forEach { sceneName ->
                    // Un cuento puede estar guardado a medio crear. Si todavía no
                    // existe escena.json, se conserva su carpeta sin exigir contenido.
                    val sceneFile = File(File(File(story.directory, "escenas"), sceneName), "escena.json")
                    if (sceneFile.isFile) {
                        val scene = StoryRepository.readScene(story, sceneName)
                        listOfNotNull(scene.metadata.awardedObject, scene.metadata.requiredObject)
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
            val objectsJson = JSONArray()
            objectDependencies.values.forEach { storedObject ->
                addDirectory(zip, storedObject.directory,
                    "objetos/${storedObject.collection}/${storedObject.folderName}", includeStatistics = false)
                objectsJson.put(JSONObject()
                    .put("collection", storedObject.collection)
                    .put("directory", storedObject.folderName)
                    .put("name", storedObject.metadata.name)
                    .put("image", storedObject.metadata.image))
            }
            manifest.put("stories", storiesJson)
            manifest.put("objectDependencies", objectsJson)

            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun inspect(context: Context, source: Uri): PackageInfo {
        val staging = File(context.cacheDir, "library-import-${System.nanoTime()}")
        check(staging.mkdirs()) { "No se pudo preparar la validación del archivo." }
        try {
            val entries = mutableSetOf<String>()
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
                        destination.outputStream().buffered().use { output -> zip.copyTo(output) }
                    }
                    zip.closeEntry()
                }
            }
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
                    exerciseDirs.forEach { exerciseDir ->
                        val stored = ExerciseRepository.read(exerciseDir)
                        require(stored.metadata.modality == modality && stored.metadata.category == name) {
                            "Los metadatos de ${exerciseDir.name} no coinciden con su ubicación."
                        }
                        validateAssets(stored.content, exerciseDir)
                        val stats = File(exerciseDir, STATISTICS_FILE)
                        if (stats.exists()) {
                            require(item.optBoolean("includesLearningStatistics")) { "El paquete contiene estadísticas de aprendizaje no declaradas." }
                            val json = JSONObject(stats.readText(Charsets.UTF_8))
                            require(json.optInt("schemaVersion", -1) == 1) { "Las estadísticas de ${stored.metadata.title} no son compatibles." }
                        }
                    }
                    CategoryInfo(directory, name, exerciseDirs.size)
                }
                val includesUsage = item.optBoolean("includesUsageStatistics")
                val usageFile = File(staging, "${modality.directoryName}/${UsageStatisticsRepository.FILE_NAME}")
                if (usageFile.exists()) {
                    require(includesUsage) { "El paquete contiene estadísticas de uso no declaradas." }
                    UsageStatisticsRepository.validateFile(usageFile, modality)
                }
                ModalityInfo(modality, categories, item.optBoolean("includesLearningStatistics"), includesUsage)
            }
            val storiesJson = manifest.optJSONArray("stories") ?: JSONArray()
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
                        listOfNotNull(scene.metadata.illustration, scene.metadata.ambientAudio, scene.metadata.narrationAudio).forEach { resource ->
                            require(isSafeSegment(resource) && File(sceneDir, resource).isFile) {
                                "Falta el recurso $resource de la escena $sceneName."
                            }
                        }
                    }
                }
                storedStory.metadata.cover?.let { cover ->
                    require(File(storyDirectory, cover).isFile) { "Falta la portada del cuento $title." }
                }
                StoryInfo(key, collection, directory, title, storedStory.metadata.scenes.size)
            }
            require(storyInfos.map { it.key }.distinct().size == storyInfos.size) { "El manifiesto repite cuentos." }
            require(modalitiesJson.length() > 0 || storyInfos.isNotEmpty()) {
                "El paquete no contiene bibliotecas ni cuentos."
            }

            val objectsJson = manifest.optJSONArray("objectDependencies") ?: JSONArray()
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
                ObjectDependencyInfo(key, collection, directory, name)
            }
            require(objectInfos.map { it.key }.distinct().size == objectInfos.size) { "El manifiesto repite objetos." }

            // Comprueba que todas las referencias de objetos del cuento estén resueltas
            // dentro del paquete; evita importar historias con dependencias rotas.
            storyInfos.forEach { storyInfo ->
                val story = StoryRepository.read(File(staging, "cuentos/${storyInfo.key}"))
                val referenced = linkedSetOf<String>()
                story.metadata.connectionConditions.mapNotNullTo(referenced) { it.requiredObject }
                story.metadata.scenes.forEach { sceneName ->
                    val sceneFile = File(File(File(story.directory, "escenas"), sceneName), "escena.json")
                    if (sceneFile.isFile) {
                        val scene = StorySceneJsonCodec.decode(JSONObject(sceneFile.readText(Charsets.UTF_8)))
                        listOfNotNull(scene.awardedObject, scene.requiredObject).forEach { referenced.add(it) }
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

    fun import(
        context: Context,
        packageInfo: PackageInfo,
        selectedCategories: Map<ContentStorage.Modality, Set<String>>,
        replaceSameName: Boolean,
        importLearningStatistics: Boolean,
        importUsageStatistics: Boolean,
        selectedStories: Set<String> = emptySet(),
        objectConflictDecisions: Map<String, String> = emptyMap()
    ): Int {
        var imported = 0
        try {
            val selectedStoryInfos = packageInfo.stories.filter { it.key in selectedStories }
            require(selectedStories.all { key -> packageInfo.stories.any { it.key == key } }) {
                "Uno de los cuentos seleccionados no está disponible en el paquete."
            }
            val selectedObjectNames = linkedSetOf<String>()
            selectedStoryInfos.forEach { info ->
                val story = StoryRepository.read(File(packageInfo.staging, "cuentos/${info.key}"))
                story.metadata.connectionConditions.mapNotNullTo(selectedObjectNames) { it.requiredObject }
                story.metadata.scenes.forEach { sceneName ->
                    val sceneFile = File(File(File(story.directory, "escenas"), sceneName), "escena.json")
                    if (sceneFile.isFile) {
                        val scene = StorySceneJsonCodec.decode(JSONObject(sceneFile.readText(Charsets.UTF_8)))
                        listOfNotNull(scene.awardedObject, scene.requiredObject).forEach { selectedObjectNames.add(it) }
                    }
                }
            }
            val selectedObjectInfos = packageInfo.objects.filter { it.directory in selectedObjectNames }
            val localObjects = ObjectRepository.list(context)
            selectedObjectInfos.forEach { incoming ->
                val existing = localObjects.firstOrNull { it.folderName == incoming.directory }
                if (existing != null) {
                    val source = File(packageInfo.staging, "objetos/${incoming.key}")
                    val incomingObject = ObjectRepository.read(source)
                    val sameDefinition = existing.metadata.name == incomingObject.metadata.name &&
                        existing.metadata.image == incomingObject.metadata.image &&
                        runCatching { File(existing.directory, existing.metadata.image).readBytes().contentEquals(
                            File(incomingObject.directory, incomingObject.metadata.image).readBytes()
                        ) }.getOrDefault(false)
                    if (!sameDefinition) {
                        require(objectConflictDecisions[incoming.directory] in setOf("reuse", "overwrite")) {
                            "Conflicto con el objeto «${incoming.name}». Debes elegir si conservar el objeto existente o sobrescribirlo."
                        }
                    }
                }
            }
            selectedStoryInfos.forEach { incoming ->
                val target = File(File(StoryRepository.rootDirectory(context), incoming.collection), incoming.directory)
                require(!target.exists()) {
                    "Ya existe un cuento llamado «${incoming.title}» en la colección «${incoming.collection}». Renómbralo o elimina el existente antes de importarlo."
                }
            }

            // Importa primero las definiciones de objetos requeridas. No toca nunca
            // el inventario local del niño: solo se copia la biblioteca de objetos.
            selectedObjectInfos.forEach { incoming ->
                val source = File(packageInfo.staging, "objetos/${incoming.key}")
                val incomingObject = ObjectRepository.read(source)
                val existing = ObjectRepository.list(context).firstOrNull { it.folderName == incoming.directory }
                val decision = objectConflictDecisions[incoming.directory]
                if (existing != null && decision == "reuse") return@forEach
                val targetCollection = if (existing != null) existing.collection else incoming.collection
                val target = File(File(ObjectRepository.rootDirectory(context), targetCollection), incoming.directory)
                val temporary = File(target.parentFile, ".import-${incoming.directory}-${System.nanoTime()}")
                copyDirectory(source, temporary)
                if (target.exists()) {
                    val backup = File(target.parentFile, ".backup-${incoming.directory}-${System.nanoTime()}")
                    check(target.renameTo(backup)) { "No se pudo reservar el objeto anterior «${incoming.name}»." }
                    if (!temporary.renameTo(target)) {
                        backup.renameTo(target)
                        throw IllegalStateException("No se pudo sobrescribir el objeto «${incoming.name}».")
                    }
                    backup.deleteRecursively()
                } else {
                    check(temporary.renameTo(target)) { "No se pudo importar el objeto «${incoming.name}»." }
                }
            }
            selectedStoryInfos.forEach { incoming ->
                val source = File(packageInfo.staging, "cuentos/${incoming.key}")
                val collection = File(StoryRepository.rootDirectory(context), incoming.collection)
                check(collection.mkdirs() || collection.isDirectory)
                val target = File(collection, incoming.directory)
                val temporary = File(collection, ".import-${incoming.directory}-${System.nanoTime()}")
                copyDirectory(source, temporary)
                check(temporary.renameTo(target)) { "No se pudo importar el cuento «${incoming.title}»." }
                imported++
            }
            packageInfo.modalities.forEach { modalityInfo ->
                val modality = modalityInfo.modality
                val selected = selectedCategories[modality].orEmpty()
                modalityInfo.categories.filter { it.directory in selected }.forEach { category ->
                    val sourceCategory = File(packageInfo.staging, "${modality.directoryName}/${category.directory}")
                    val targetCategory = File(ContentStorage.modalityDirectory(context, modality), category.directory)
                    check(targetCategory.mkdirs() || targetCategory.isDirectory) { "No se pudo crear la categoría ${category.name}." }
                    sourceCategory.listFiles()?.filter { it.isDirectory }?.forEach { incoming ->
                        val incomingStored = ExerciseRepository.read(incoming)
                        val existing = ExerciseRepository.list(context, modality).firstOrNull {
                            it.metadata.category.equals(category.name, true) &&
                                it.metadata.title.equals(incomingStored.metadata.title, true)
                        }
                        if (existing != null && !replaceSameName) return@forEach
                        val target = if (existing != null) existing.directory else File(targetCategory, incoming.name)
                        val stagingExercise = File(targetCategory, ".import-${System.nanoTime()}")
                        copyDirectory(incoming, stagingExercise)
                        val incomingStatistics = File(stagingExercise, STATISTICS_FILE)
                        if (!importLearningStatistics) {
                            // El ZIP no debe sobrescribir las estadísticas locales si el usuario
                            // ha decidido importar únicamente los ejercicios.
                            incomingStatistics.delete()
                            if (existing != null) {
                                val existingStatistics = File(existing.directory, STATISTICS_FILE)
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
                            backup.deleteRecursively()
                        } else {
                            check(stagingExercise.renameTo(target)) { "No se pudo incorporar ${incomingStored.metadata.title}." }
                        }
                        imported++
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
                        if (backupUsage.exists()) backupUsage.delete()
                    }
                }
            }
            return imported
        } finally {
            packageInfo.staging.deleteRecursively()
        }
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

    private fun copyDirectory(source: File, destination: File) {
        source.walkTopDown().forEach { file ->
            val target = File(destination, file.relativeTo(source).path)
            if (file.isDirectory) check(target.mkdirs() || target.isDirectory)
            else {
                target.parentFile?.let { check(it.mkdirs() || it.isDirectory) }
                file.copyTo(target, overwrite = true)
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
