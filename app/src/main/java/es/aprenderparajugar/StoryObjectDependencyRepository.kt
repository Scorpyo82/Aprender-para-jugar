package es.aprenderparajugar

import android.content.Context

/**
 * Índice de dependencias entre cuentos y objetos.
 * Si no se puede leer cualquier cuento/escena, se conserva el error para impedir
 * eliminaciones de objetos que no se puedan verificar con seguridad.
 */
internal data class StoryObjectDependencySnapshot(
    val objectFoldersByStory: Map<String, Set<String>>,
    val storyFoldersByObject: Map<String, Set<String>>,
    val scanErrors: List<String>
)

internal object StoryObjectDependencyRepository {
    fun scan(context: Context): StoryObjectDependencySnapshot {
        val objectsByStory = linkedMapOf<String, Set<String>>()
        val storiesByObject = linkedMapOf<String, MutableSet<String>>()
        val errors = mutableListOf<String>()
        val root = StoryRepository.rootDirectory(context)
        val collections = root.listFiles()
        if (collections == null) {
            return StoryObjectDependencySnapshot(emptyMap(), emptyMap(), listOf("No se pudo leer la biblioteca de cuentos."))
        }

        collections.filter { it.isDirectory && !it.name.startsWith(".") }.forEach collectionLoop@ { collection ->
            val directories = collection.listFiles()
            if (directories == null) {
                errors += "No se pudo leer la colección «${collection.name}»."
                return@collectionLoop
            }
            directories.filter { it.isDirectory && !it.name.startsWith(".") }.forEach storyLoop@ { directory ->
                val story = runCatching { StoryRepository.read(directory) }.getOrElse {
                    errors += "No se pudo leer el cuento «${directory.name}»."
                    return@storyLoop
                }
                val references = linkedSetOf<String>()
                story.metadata.connectionConditions.mapNotNullTo(references) { it.requiredObject }
                story.metadata.connectionAlternatives.mapNotNullTo(references) { it.requiredObject }

                story.metadata.scenes.forEach sceneLoop@ { sceneFolder ->
                    val scene = runCatching { StoryRepository.readScene(story, sceneFolder).metadata }.getOrElse {
                        errors += "No se pudo leer «${story.metadata.title} / $sceneFolder»."
                        return@sceneLoop
                    }
                    scene.awardedObject?.let(references::add)
                    scene.requiredObject?.let(references::add)
                    references.addAll(scene.awardedObjects)
                    references.addAll(scene.unlockedObjects)
                }
                val storyKey = story.directory.canonicalPath
                objectsByStory[storyKey] = references
                references.forEach { folder -> storiesByObject.getOrPut(folder) { linkedSetOf() }.add(storyKey) }
            }
        }
        return StoryObjectDependencySnapshot(
            objectFoldersByStory = objectsByStory,
            storyFoldersByObject = storiesByObject.mapValues { it.value.toSet() },
            scanErrors = errors.distinct()
        )
    }
}
