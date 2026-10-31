package es.aprenderparajugar

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.Instant

data class StoredExercise(
    val directory: File,
    val metadata: ExerciseMetadata,
    val content: Any
)

/**
 * Repositorio único para leer y escribir las bibliotecas de ejercicios.
 *
 * Cada operación de guardado prepara una carpeta temporal y sustituye la
 * carpeta anterior al final. statistics.json se conserva al actualizar.
 */
object ExerciseRepository {

    private const val EXERCISE_FILE = "ejercicio.json"
    private const val STATISTICS_FILE = "statistics.json"
    private const val EMPTY_STATISTICS = """{"schemaVersion":1}"""

    fun newTimestamp(): String = Instant.now().toString()

    fun list(context: Context, modality: ContentStorage.Modality): List<StoredExercise> {
        val root = ContentStorage.modalityDirectory(context, modality)
        val exercises = mutableListOf<StoredExercise>()

        root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { categoryDirectory ->
                categoryDirectory.listFiles()
                    ?.filter { it.isDirectory && !it.name.startsWith(".") }
                    ?.sortedBy { it.name.lowercase() }
                    ?.forEach { exerciseDirectory ->
                        readExercise(exerciseDirectory)?.let { stored ->
                            if (stored.metadata.modality == modality) exercises.add(stored)
                        }
                    }
            }

        return exercises
    }

    fun read(directory: File): StoredExercise {
        require(directory.isDirectory) { "La carpeta del ejercicio no existe." }
        val jsonFile = File(directory, EXERCISE_FILE)
        require(jsonFile.isFile) { "La carpeta no contiene $EXERCISE_FILE." }
        val decoded = ExerciseJsonCodec.decode(JSONObject(jsonFile.readText(Charsets.UTF_8)))
        return StoredExercise(directory, decoded.first, decoded.second)
    }

    /**
     * Guarda un ejercicio y sus recursos locales.
     *
     * existingDirectory identifica el ejercicio que se está editando. Si cambia
     * de categoría o título, se mueve a su nueva ubicación sin perder estadísticas.
     * Si no se proporciona, el destino debe estar libre.
     */
    fun save(
        context: Context,
        metadata: ExerciseMetadata,
        content: Any,
        assets: Map<String, ByteArray> = emptyMap(),
        existingDirectory: File? = null
    ): StoredExercise {
        val json = ExerciseJsonCodec.encode(metadata, content)
        val root = ContentStorage.modalityDirectory(context, metadata.modality).canonicalFile
        val target = ContentStorage.exerciseDirectory(
            context,
            metadata.modality,
            metadata.category,
            metadata.title
        ).canonicalFile
        require(target.toPath().startsWith(root.toPath())) { "La ubicación del ejercicio no es válida." }

        val source = existingDirectory?.canonicalFile
        if (source != null) {
            require(source.toPath().startsWith(root.toPath()) && source.isDirectory) {
                "El ejercicio que se intenta editar no pertenece a esta biblioteca."
            }
        }
        if (target.exists() && target != source) {
            throw IllegalArgumentException("Ya existe un ejercicio con ese nombre en la categoría seleccionada.")
        }

        assets.keys.forEach { assetName ->
            require(isSafeAssetPath(assetName)) { "Nombre de recurso no válido: $assetName" }
        }

        val parent = target.parentFile ?: throw IllegalStateException("No se pudo determinar la carpeta de destino.")
        check(parent.mkdirs() || parent.isDirectory) { "No se pudo crear la carpeta de categoría." }
        val staging = File(parent, ".staging-${System.nanoTime()}")
        val backup = File(parent, ".backup-${System.nanoTime()}")
        check(staging.mkdir()) { "No se pudo preparar el guardado del ejercicio." }

        try {
            val assetSource = source ?: target.takeIf { it.isDirectory }
            if (assetSource != null) {
                copyExerciseAssets(assetSource, staging)
                val oldStatistics = File(assetSource, STATISTICS_FILE)
                if (oldStatistics.isFile) {
                    oldStatistics.copyTo(File(staging, STATISTICS_FILE), overwrite = true)
                }
            }

            assets.forEach { (relativePath, bytes) ->
                val destination = File(staging, relativePath).canonicalFile
                require(destination.toPath().startsWith(staging.canonicalFile.toPath())) {
                    "La ruta del recurso sale de la carpeta del ejercicio."
                }
                destination.parentFile?.let { directory ->
                    check(directory.mkdirs() || directory.isDirectory) {
                        "No se pudo crear la carpeta del recurso."
                    }
                }
                destination.writeBytes(bytes)
            }

            validateReferencedAssets(content, staging)
            File(staging, EXERCISE_FILE).writeText(json.toString(2), Charsets.UTF_8)
            val statistics = File(staging, STATISTICS_FILE)
            if (!statistics.exists()) statistics.writeText(EMPTY_STATISTICS, Charsets.UTF_8)

            if (target.exists()) {
                check(target.renameTo(backup)) { "No se pudo reservar la carpeta anterior del ejercicio." }
            }
            if (!staging.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                throw IllegalStateException("No se pudo completar el guardado del ejercicio.")
            }

            if (source != null && source != target && source.exists()) {
                check(source.deleteRecursively()) {
                    "El ejercicio se guardó, pero no se pudo retirar su carpeta anterior."
                }
            }
            if (backup.exists()) backup.deleteRecursively()
            return read(target)
        } catch (error: Exception) {
            if (staging.exists()) staging.deleteRecursively()
            if (backup.exists() && !target.exists()) backup.renameTo(target)
            throw error
        }
    }

    fun readStatistics(directory: File): JSONObject {
        val file = File(directory, STATISTICS_FILE)
        if (!file.isFile) {
            file.writeText(EMPTY_STATISTICS, Charsets.UTF_8)
        }
        return JSONObject(file.readText(Charsets.UTF_8))
    }

    fun writeStatistics(directory: File, statistics: JSONObject) {
        require(directory.isDirectory && File(directory, EXERCISE_FILE).isFile) {
            "La carpeta indicada no corresponde a un ejercicio guardado."
        }
        val temporary = File(directory, ".$STATISTICS_FILE.tmp")
        temporary.writeText(statistics.toString(2), Charsets.UTF_8)
        val destination = File(directory, STATISTICS_FILE)
        if (destination.exists() && !destination.delete()) {
            temporary.delete()
            throw IllegalStateException("No se pudieron actualizar las estadísticas.")
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            throw IllegalStateException("No se pudieron guardar las estadísticas.")
        }
    }

    private fun readExercise(directory: File): StoredExercise? =
        runCatching { read(directory) }.getOrNull()

    private fun copyExerciseAssets(source: File, destination: File) {
        source.walkTopDown()
            .filter { it.isFile && it.name != EXERCISE_FILE && it.name != STATISTICS_FILE }
            .forEach { file ->
                val relative = file.relativeTo(source)
                val target = File(destination, relative.path)
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = true)
            }
    }

    private fun validateReferencedAssets(content: Any, directory: File) {
        val references = when (content) {
            is CardExerciseContent -> listOf(content.image)
            is WordExerciseContent -> listOf(content.hintImage)
            is SequenceExerciseContent -> content.steps.map { it.image }
            else -> throw IllegalArgumentException("Tipo de contenido no compatible.")
        }
        references.forEach { reference ->
            require(isSafeAssetPath(reference) && File(directory, reference).isFile) {
                "No se encuentra el recurso asociado al ejercicio: $reference"
            }
        }
    }

    private fun isSafeAssetPath(value: String): Boolean {
        if (value.isBlank() || value.startsWith("/") || value.contains('\\') || value.contains(':')) return false
        return value.split('/').all { it.isNotBlank() && it != "." && it != ".." }
    }
}
