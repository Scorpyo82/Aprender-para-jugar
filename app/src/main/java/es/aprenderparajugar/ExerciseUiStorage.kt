package es.aprenderparajugar

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Adaptador entre los modelos que utiliza la interfaz y el repositorio persistente.
 * Las imágenes seleccionadas se copian a la carpeta privada de cada ejercicio.
 */
internal object ExerciseUiStorage {

    fun loadPairs(context: Context, modality: ContentStorage.Modality): List<PairItem> {
        require(modality == ContentStorage.Modality.TARJETAS || modality == ContentStorage.Modality.PALABRAS)
        return ExerciseRepository.list(context, modality).map { stored ->
            val imageName = when (val value = stored.content) {
                is CardExerciseContent -> value.image
                is WordExerciseContent -> value.hintImage
                else -> throw IllegalStateException("El contenido guardado no corresponde a $modality.")
            }
            PairItem(
                word = when (val value = stored.content) {
                    is CardExerciseContent -> value.word
                    is WordExerciseContent -> value.word
                    else -> ""
                },
                picture = Uri.fromFile(File(stored.directory, imageName)).toString(),
                category = stored.metadata.category,
                difficulty = stored.metadata.difficulty,
                active = stored.metadata.active,
                directory = stored.directory,
                createdAt = stored.metadata.createdAt
            )
        }
    }

    /**
     * Guarda la biblioteca completa y elimina únicamente ejercicios que estaban
     * previamente registrados en esa misma biblioteca y ya no aparecen en la lista.
     */
    fun savePairs(
        context: Context,
        modality: ContentStorage.Modality,
        exercises: List<PairItem>
    ): List<PairItem> {
        require(modality == ContentStorage.Modality.TARJETAS || modality == ContentStorage.Modality.PALABRAS)
        val previous = ExerciseRepository.list(context, modality)
        val saved = exercises.map { exercise ->
            val timestamp = ExerciseRepository.newTimestamp()
            val image = copySelectedImage(context, exercise.picture)
            val metadata = ExerciseMetadata(
                modality = modality,
                title = exercise.word,
                category = exercise.category,
                difficulty = exercise.difficulty,
                active = exercise.active,
                createdAt = exercise.createdAt.ifBlank { timestamp },
                updatedAt = timestamp
            )
            val content: Any = if (modality == ContentStorage.Modality.TARJETAS) {
                CardExerciseContent(exercise.word, image.first)
            } else {
                WordExerciseContent(exercise.word, image.first)
            }
            val source = exercise.directory ?: previous.firstOrNull {
                it.metadata.title.equals(exercise.word, ignoreCase = true) &&
                    it.metadata.category.equals(exercise.category, ignoreCase = true)
            }?.directory
            val stored = ExerciseRepository.save(
                context = context,
                metadata = metadata,
                content = content,
                assets = image.second,
                existingDirectory = source
            )
            exercise.copy(
                picture = Uri.fromFile(File(stored.directory, image.first)).toString(),
                directory = stored.directory,
                createdAt = stored.metadata.createdAt
            )
        }
        val retained = saved.mapNotNull { it.directory?.canonicalFile }.toSet()
        previous.filter { it.directory.canonicalFile !in retained }.forEach {
            if (it.directory.exists()) ExerciseRepository.delete(context, modality, it.directory)
        }
        return saved
    }

    private fun copySelectedImage(context: Context, picture: String): Pair<String, Map<String, ByteArray>> {
        require(picture.isNotBlank()) { "Selecciona una imagen para el ejercicio." }
        val uri = runCatching { Uri.parse(picture) }.getOrNull()
            ?: throw IllegalArgumentException("La imagen seleccionada no es válida.")
        val bytes = when (uri.scheme) {
            "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            "file" -> uri.path?.let(::File)?.takeIf { it.isFile }?.readBytes()
            else -> null
        } ?: throw IllegalArgumentException("No se puede acceder al archivo de imagen seleccionado.")
        val extension = when (context.contentResolver.getType(uri)?.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/jpeg", "image/jpg" -> "jpg"
            else -> uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
                ?.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif") } ?: "jpg"
        }
        val assetName = "images/imagen_principal.$extension"
        return assetName to mapOf(assetName to bytes)
    }
}
