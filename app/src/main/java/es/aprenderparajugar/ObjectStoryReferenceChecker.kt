package es.aprenderparajugar

import android.content.Context

/**
 * Resultado de la comprobación de referencias antes de borrar un objeto.
 * Si hay errores de lectura no se permite considerar segura la eliminación.
 */
internal data class ObjectStoryReferenceCheck(
    val storyTitles: List<String>,
    val scanErrors: List<String>
) {
    val canDelete: Boolean get() = storyTitles.isEmpty() && scanErrors.isEmpty()
}

internal object ObjectStoryReferenceChecker {
    fun check(context: Context, objectFolder: String): ObjectStoryReferenceCheck {
        val titles = linkedSetOf<String>()
        val errors = mutableListOf<String>()
        val root = StoryRepository.rootDirectory(context)
        val collections = root.listFiles()
        if (collections == null) {
            return ObjectStoryReferenceCheck(emptyList(), listOf("No se pudo leer la biblioteca de cuentos."))
        }

        for (collection in collections.filter { it.isDirectory && !it.name.startsWith(".") }) {
            val directories = collection.listFiles()
            if (directories == null) {
                errors += collection.name
                continue
            }

            for (directory in directories.filter { it.isDirectory && !it.name.startsWith(".") }) {
                val story = runCatching { StoryRepository.read(directory) }.getOrNull()
                if (story == null) {
                    errors += directory.name
                    continue
                }

                val manifestReferences =
                    story.metadata.connectionConditions.any { it.requiredObject == objectFolder } ||
                    story.metadata.connectionAlternatives.any { it.requiredObject == objectFolder }

                var sceneReferences = false
                for (sceneFolder in story.metadata.scenes) {
                    val scene = runCatching { StoryRepository.readScene(story, sceneFolder).metadata }.getOrNull()
                    if (scene == null) {
                        errors += "${story.metadata.title} / $sceneFolder"
                        sceneReferences = true
                        continue
                    }
                    if (
                        scene.awardedObject == objectFolder ||
                        scene.requiredObject == objectFolder ||
                        objectFolder in scene.awardedObjects ||
                        objectFolder in scene.unlockedObjects
                    ) {
                        sceneReferences = true
                    }
                }

                if (manifestReferences || sceneReferences) titles += story.metadata.title
            }
        }

        return ObjectStoryReferenceCheck(titles.toList(), errors.distinct())
    }
}
