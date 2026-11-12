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

        root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.forEach { collection ->
                collection.listFiles()
                    ?.filter { it.isDirectory && !it.name.startsWith(".") }
                    ?.forEach { directory ->
                        val story = runCatching { StoryRepository.read(directory) }
                            .onFailure { errors += directory.name }
                            .getOrNull() ?: return@forEach

                        val manifestReferences = story.metadata.connectionConditions.any {
                            it.requiredObject == objectFolder
                        } || story.metadata.connectionAlternatives.any {
                            it.requiredObject == objectFolder
                        }

                        val sceneReferences = story.metadata.scenes.any { sceneFolder ->
                            val scene = runCatching { StoryRepository.readScene(story, sceneFolder).metadata }
                                .onFailure { errors += "${story.metadata.title} / $sceneFolder" }
                                .getOrNull() ?: return@any true
                            scene.awardedObject == objectFolder ||
                                scene.requiredObject == objectFolder ||
                                objectFolder in scene.awardedObjects ||
                                objectFolder in scene.unlockedObjects
                        }

                        if (manifestReferences || sceneReferences) titles += story.metadata.title
                    }
            }

        return ObjectStoryReferenceCheck(titles.toList(), errors.distinct())
    }
}
