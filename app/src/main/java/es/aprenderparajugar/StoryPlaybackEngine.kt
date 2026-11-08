package es.aprenderparajugar

import android.content.Context
import java.io.File

/**
 * Motor de recorrido narrativo. Evalúa alternativas de acceso por destino:
 * varias alternativas pueden conducir al mismo destino (OR).
 */
internal class StoryPlaybackEngine(
    private val context: Context,
    private val story: StoredStory
) {
    data class Transition(
        val destination: String,
        val requiredObject: String?,
        val consumeRequiredObject: Boolean,
        val kind: String = if (requiredObject == null) "free" else "object",
        val decisionOptionKey: String? = null
    )

    data class Snapshot(
        val scene: StoredStoryScene,
        val inventory: List<StoredObject>,
        val transitions: List<Transition>,
        val canAdvance: Boolean,
        val isFinished: Boolean,
        val decisionPrompt: String = "",
        val decisionOptions: List<StoryDecisionOption> = emptyList()
    )

    private var currentSceneFolder: String = story.metadata.startScene
    private var started = false
    private var finished = false

    fun start(): Snapshot {
        require(story.metadata.connections.values.none { STORY_LOST_DESTINATION in it } &&
            story.metadata.connectionAlternatives.none { it.fromScene == STORY_LOST_ORIGIN || it.toScene == STORY_LOST_DESTINATION }) {
            "Este cuento tiene conexiones huérfanas. Repara su estructura narrativa antes de reproducirlo."
        }
        val sceneMetadata = story.metadata.scenes.associateWith { folder -> StoryRepository.readScene(story, folder).metadata }
        val availableObjectFolders = ObjectRepository.list(context).map { it.folderName }.toSet()
        val configuredOrigins = story.metadata.connectionAlternatives.map { it.fromScene }.toSet()
        val missingAlternativeObject = story.metadata.connectionAlternatives.firstOrNull { alternative ->
            alternative.kind == "object" && alternative.requiredObject !in availableObjectFolders
        }
        require(missingAlternativeObject == null) {
            "Hay una alternativa que requiere un objeto eliminado o inexistente. Repara la referencia en la estructura narrativa antes de reproducir el cuento."
        }
        val missingLegacyObject = story.metadata.connectionConditions.firstOrNull { condition ->
            condition.fromScene !in configuredOrigins &&
                condition.requiredObject != null &&
                condition.requiredObject !in availableObjectFolders
        }
        require(missingLegacyObject == null) {
            "Hay una conexión heredada que requiere un objeto eliminado o inexistente. Repara la referencia en la estructura narrativa antes de reproducir el cuento."
        }
        val orphanedDecision = story.metadata.connectionAlternatives.firstOrNull { alternative ->
            alternative.kind == "decision" && sceneMetadata[alternative.fromScene]?.decisionOptions.orEmpty().none { it.key == alternative.decisionOptionKey }
        }
        require(orphanedDecision == null) {
            "Hay una alternativa que apunta a una opción de decisión eliminada o inexistente. Repara la estructura narrativa antes de reproducir el cuento."
        }
        val unassignedOption = sceneMetadata.entries.firstNotNullOfOrNull { (scene, metadata) ->
            metadata.decisionOptions.firstOrNull { option ->
                story.metadata.connectionAlternatives.none { it.fromScene == scene && it.kind == "decision" && it.decisionOptionKey == option.key }
            }?.let { scene to it }
        }
        require(unassignedOption == null) {
            "Hay una opción de decisión sin destino asignado. Completa la estructura narrativa antes de reproducir el cuento."
        }
        require(sceneMetadata.values.all { metadata -> metadata.decisionOptions.isEmpty() || metadata.decisionPrompt.isNotBlank() }) {
            "Hay una escena con opciones de decisión pero sin pregunta o planteamiento."
        }
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

    /** Avanza únicamente por una alternativa libre y no ambigua. */
    fun advance(): Snapshot? {
        check(started && !finished) { "El cuento no está en reproducción." }
        val free = transitions().filter { it.kind == "free" }
        if (free.isEmpty()) return snapshot()
        require(free.map { it.destination }.distinct().size == 1) {
            "El recorrido tiene más de una salida libre. Debe elegirse una opción."
        }
        return moveTo(free.first())
    }

    /** Comprueba si el objeto permite salir de la escena actual sin modificar el inventario. */
    fun canUseObject(folderName: String): Boolean {
        check(started && !finished) { "El cuento no está en reproducción." }
        return transitions().any {
            it.kind == "object" && it.requiredObject == folderName
        } && InventoryRepository.contains(context, folderName)
    }

    /** Selecciona una alternativa por objeto. Una alternativa OR válida basta para habilitar el destino. */
    fun chooseObject(folderName: String): Snapshot {
        check(started && !finished) { "El cuento no está en reproducción." }
        if (!canUseObject(folderName)) return snapshot()
        val selected = transitions().first {
            it.kind == "object" && it.requiredObject == folderName
        }
        return moveTo(selected)
    }

    /** Selecciona el destino asociado a la opción textual elegida por el niño. */
    fun chooseDecisionOption(optionKey: String): Snapshot {
        check(started && !finished) { "El cuento no está en reproducción." }
        val selected = transitions().firstOrNull {
            it.kind == "decision" && it.decisionOptionKey == optionKey
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

    /**
     * Si el manifiesto contiene alternativas nuevas, son la fuente de verdad.
     * Los cuentos antiguos se traducen en memoria desde connections y
     * connectionConditions, sin exigir una migración previa del archivo.
     */
    private fun transitions(): List<Transition> {
        val origin = currentSceneFolder
        val configured = story.metadata.connectionAlternatives.filter { it.fromScene == origin }
        if (configured.isNotEmpty()) {
            return configured.map { alternative ->
                Transition(
                    destination = alternative.toScene,
                    requiredObject = alternative.requiredObject,
                    consumeRequiredObject = alternative.consumeRequiredObject,
                    kind = alternative.kind,
                    decisionOptionKey = alternative.decisionOptionKey
                )
            }
        }
        return story.metadata.connections[origin].orEmpty()
            .filter { it != STORY_LOST_DESTINATION }
            .flatMap { destination ->
                val condition = story.metadata.connectionConditions.firstOrNull {
                    it.fromScene == origin && it.toScene == destination
                }
                listOf(
                    Transition(
                        destination = destination,
                        requiredObject = condition?.requiredObject,
                        consumeRequiredObject = condition?.consumeRequiredObject == true,
                        kind = if (condition?.requiredObject != null) "object" else "free"
                    )
                )
            }
    }

    private fun snapshot(): Snapshot {
        val scene = StoryRepository.readScene(story, currentSceneFolder)
        val outgoing = transitions()
        val inventoryNames = InventoryRepository.list(context)
        val objectsByFolder = ObjectRepository.list(context).associateBy { it.folderName }
        val inventory = inventoryNames.mapNotNull(objectsByFolder::get)
        val decisionKeys = outgoing
            .filter { it.kind == "decision" }
            .mapNotNull { it.decisionOptionKey }
            .toSet()
        return Snapshot(
            scene = scene,
            inventory = inventory,
            transitions = outgoing,
            canAdvance = outgoing.any { it.kind == "free" },
            isFinished = outgoing.isEmpty(),
            decisionPrompt = scene.metadata.decisionPrompt,
            decisionOptions = scene.metadata.decisionOptions.filter { it.key in decisionKeys }
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
        ObjectUnlockRepository.unlock(context, metadata.unlockedObjects)
        val objectFolders = (listOfNotNull(metadata.awardedObject) + metadata.awardedObjects).distinct()
        val collectible = objectFolders.asSequence()
            .mapNotNull { folder -> ObjectRepository.list(context).firstOrNull { it.folderName == folder } }
            .firstOrNull { it.metadata.collectible && !InventoryRepository.contains(context, it.folderName) }
        if (collectible != null) {
            runCatching { InventoryRepository.add(context, collectible.folderName) }
            return collectible
        }
        return null
    }

    private fun award(sceneFolder: String, timing: String) {
        val metadata = runCatching { StoryRepository.readScene(story, sceneFolder).metadata }.getOrNull() ?: return
        if (metadata.awardTiming != timing) return
        ObjectUnlockRepository.unlock(context, metadata.unlockedObjects)
        (listOfNotNull(metadata.awardedObject) + metadata.awardedObjects).distinct().forEach { objectFolder ->
            runCatching { InventoryRepository.add(context, objectFolder) }
        }
    }
}
