package es.aprenderparajugar

import android.content.Context
import java.io.File

/**
 * Motor de recorrido narrativo. No depende de Compose ni de la interfaz:
 * el orden se obtiene exclusivamente de startScene y del grafo de conexiones.
 */
internal class StoryPlaybackEngine(
    private val context: Context,
    private val story: StoredStory
) {
    data class Transition(
        val destination: String,
        val requiredObject: String?,
        val consumeRequiredObject: Boolean
    )

    data class Snapshot(
        val scene: StoredStoryScene,
        val inventory: List<StoredObject>,
        val transitions: List<Transition>,
        val canAdvance: Boolean,
        val isFinished: Boolean
    )

    private var currentSceneFolder: String = story.metadata.startScene
    private var started = false
    private var finished = false

    fun start(): Snapshot {
        currentSceneFolder = story.metadata.startScene
        started = true
        finished = false
        award(currentSceneFolder, "onEnter")
        return snapshot()
    }

    fun current(): Snapshot {
        check(started && !finished) { "El cuento no está en reproducción." }
        return snapshot()
    }

    /** Avanza solo por una salida libre. Nunca elige automáticamente una salida condicionada. */
    fun advance(): Snapshot? {
        check(started && !finished) { "El cuento no está en reproducción." }
        val free = transitions().filter { it.requiredObject == null }
        if (free.isEmpty()) return snapshot()
        require(free.size == 1) { "El recorrido tiene más de una salida libre." }
        return moveTo(free.single())
    }

    /** Selecciona la salida cuyo objeto requerido coincide con el objeto de la mochila. */
    fun chooseObject(folderName: String): Snapshot {
        check(started && !finished) { "El cuento no está en reproducción." }
        val selected = transitions().firstOrNull {
            it.requiredObject == folderName && InventoryRepository.contains(context, folderName)
        } ?: return snapshot()
        return moveTo(selected)
    }

    fun finish(): Boolean {
        check(started) { "El cuento todavía no ha comenzado." }
        if (transitions().isNotEmpty()) return false
        award(currentSceneFolder, "onExit")
        finished = true
        return true
    }

    fun stop() {
        finished = true
    }

    private fun moveTo(transition: Transition): Snapshot {
        award(currentSceneFolder, "onExit")
        if (transition.consumeRequiredObject) {
            transition.requiredObject?.let { InventoryRepository.remove(context, it) }
        }
        currentSceneFolder = transition.destination
        award(currentSceneFolder, "onEnter")
        return snapshot()
    }

    private fun transitions(): List<Transition> =
        story.metadata.connections[currentSceneFolder].orEmpty().map { destination ->
            val condition = story.metadata.connectionConditions.firstOrNull {
                it.fromScene == currentSceneFolder && it.toScene == destination
            }
            Transition(
                destination = destination,
                requiredObject = condition?.requiredObject,
                consumeRequiredObject = condition?.consumeRequiredObject == true
            )
        }

    private fun snapshot(): Snapshot {
        val scene = StoryRepository.readScene(story, currentSceneFolder)
        val outgoing = transitions()
        val inventoryNames = InventoryRepository.list(context)
        val objectsByFolder = ObjectRepository.list(context).associateBy { it.folderName }
        val inventory = inventoryNames.mapNotNull(objectsByFolder::get)
        return Snapshot(
            scene = scene,
            inventory = inventory,
            transitions = outgoing,
            canAdvance = outgoing.any { it.requiredObject == null },
            isFinished = outgoing.isEmpty()
        )
    }

    /**
     * Registra la recompensa de salida antes de avanzar. Solo devuelve coleccionables
     * nuevos; si el niño vuelve a pulsar Siguiente, el inventario evita duplicados.
     */
    fun claimCurrentSceneExitCollectible(): StoredObject? {
        check(started && !finished) { "El cuento no está en reproducción." }
        val metadata = runCatching { StoryRepository.readScene(story, currentSceneFolder).metadata }.getOrNull()
            ?: return null
        if (metadata.awardTiming != "onExit") return null
        val objectFolder = metadata.awardedObject ?: return null
        val stored = ObjectRepository.list(context).firstOrNull { it.folderName == objectFolder } ?: return null
        if (!stored.metadata.collectible || InventoryRepository.contains(context, objectFolder)) return null
        return runCatching {
            InventoryRepository.add(context, objectFolder)
            stored
        }.getOrNull()
    }

    private fun award(sceneFolder: String, timing: String) {
        val metadata = runCatching { StoryRepository.readScene(story, sceneFolder).metadata }.getOrNull() ?: return
        if (metadata.awardTiming != timing) return
        val objectFolder = metadata.awardedObject ?: return
        runCatching { InventoryRepository.add(context, objectFolder) }
    }
}
