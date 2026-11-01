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
    data class PackageInfo(val modalities: List<ModalityInfo>, internal val staging: File)

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
        usageStatisticsModalities: Set<ContentStorage.Modality>
    ) {
        require(selectedCategories.values.any { it.isNotEmpty() }) { "Selecciona al menos una categoría." }
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
            require(modalitiesJson.length() > 0) { "El paquete no contiene bibliotecas." }
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
            return PackageInfo(result, staging)
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
        importUsageStatistics: Boolean
    ): Int {
        var imported = 0
        try {
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
