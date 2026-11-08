package es.aprenderparajugar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Pantalla independiente para editar el grafo narrativo de un cuento.
 * Los cambios se mantienen como borrador local hasta pulsar Guardar.
 */
@Composable
internal fun StoryStructureScreen(
    scenes: List<String>,
    sceneSummaries: Map<String, String>,
    initialConnections: Map<String, List<String>>,
    initialConditions: List<StoryConnectionCondition>,
    initialAlternatives: List<StoryConnectionAlternative>,
    initialStartScene: String,
    availableObjects: List<StoredObject>,
    message: String,
    onSave: (Map<String, List<String>>, List<StoryConnectionCondition>, List<StoryConnectionAlternative>, String) -> Boolean,
    onCancel: () -> Unit
) {
    var connections by remember(scenes, initialConnections) {
        mutableStateOf(initialConnections.mapValues { it.value.toList() })
    }
    var conditions by remember(scenes, initialConditions) {
        mutableStateOf(initialConditions.toList())
    }
    var alternatives by remember(scenes, initialAlternatives) {
        mutableStateOf(initialAlternatives.toList())
    }
    var startScene by remember(scenes, initialStartScene) { mutableStateOf(initialStartScene) }
    var startMenuExpanded by remember { mutableStateOf(false) }
    val diagnostics = remember(scenes, connections, conditions, alternatives, startScene) {
        StoryStructureAnalysis.analyze(scenes, connections, conditions, startScene, alternatives)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Estructura narrativa", fontWeight = FontWeight.Bold)
        Text("Puedes configurar las conexiones en cualquier orden. El cuento siempre comienza en la escena inicial que selecciones aquí.")
        Text("Escena inicial", fontWeight = FontWeight.SemiBold)
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { startMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(startScene.ifBlank { "Seleccionar escena inicial" })
            }
            DropdownMenu(expanded = startMenuExpanded, onDismissRequest = { startMenuExpanded = false }) {
                scenes.forEach { scene ->
                    DropdownMenuItem(text = { Text(scene) }, onClick = { startScene = scene; startMenuExpanded = false })
                }
            }
        }
        StoryStructureEditor(
            scenes = scenes,
            sceneSummaries = sceneSummaries,
            connections = connections,
            conditions = conditions,
            availableObjects = availableObjects,
            onConnectionsChange = { connections = it },
            onConditionsChange = { conditions = it }
        )
        OrphanedNarrativeReferences(
            scenes = scenes,
            conditions = conditions,
            alternatives = alternatives,
            onConditionsChange = { conditions = it },
            onAlternativesChange = { alternatives = it },
            onConnectionsChange = { connections = it }
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Comprobación de la estructura", fontWeight = FontWeight.Bold)
            if (diagnostics.orphanedConnections.isNotEmpty()) {
                Text("Hay ${diagnostics.orphanedConnections.size} conexión(es) huérfana(s). Reasigna su destino o elimina esas salidas antes de reproducir el cuento.", color = androidx.compose.ui.graphics.Color(0xFFB3261E))
            }
            if (diagnostics.finalScenes.isEmpty()) {
                Text(
                    "No se ha encontrado ninguna escena final alcanzable desde la escena inicial.",
                    color = androidx.compose.ui.graphics.Color(0xFFB3261E)
                )
            } else {
                Text("Escenas finales: ${diagnostics.finalScenes.joinToString()}.")
            }
            if (diagnostics.unreachableScenes.isNotEmpty()) {
                Text(
                    "Escenas inaccesibles desde el inicio: ${diagnostics.unreachableScenes.sorted().joinToString()}. No cuentan como finales válidos.",
                    color = androidx.compose.ui.graphics.Color(0xFF8A5A00)
                )
            }
            if (diagnostics.cycleComponents.isNotEmpty()) {
                Text("Circuitos detectados: ${diagnostics.cycleComponents.size}.")
                if (diagnostics.cyclesWithoutUnconditionalExit.isNotEmpty()) {
                    Text(
                        "Advertencia: estos circuitos no tienen una salida incondicional desde todas sus escenas hacia un final alcanzable: ${diagnostics.cyclesWithoutUnconditionalExit.joinToString { it.sorted().joinToString(" ↔ ") }}. Comprueba que las condiciones y los objetos permitan salir del circuito.",
                        color = androidx.compose.ui.graphics.Color(0xFF8A5A00)
                    )
                } else {
                    Text("Todos los circuitos detectados disponen de una ruta incondicional hacia algún final alcanzable.")
                }
            }
            Text(
                "Las conexiones huérfanas se guardan para que puedas repararlas después, pero impiden reproducir el cuento.",
                color = androidx.compose.ui.graphics.Color(0xFF667085)
            )
        }
        if (message.isNotBlank() && message != "Cuento guardado.") {
            Text(message)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) { Text("Cancelar") }
            Button(
                onClick = { onSave(connections, conditions, alternatives, startScene) },
                modifier = Modifier.weight(1f)
            ) { Text("Guardar estructura") }
        }
    }
}


@Composable
private fun OrphanedNarrativeReferences(
    scenes: List<String>,
    conditions: List<StoryConnectionCondition>,
    alternatives: List<StoryConnectionAlternative>,
    onConditionsChange: (List<StoryConnectionCondition>) -> Unit,
    onAlternativesChange: (List<StoryConnectionAlternative>) -> Unit,
    onConnectionsChange: (Map<String, List<String>>) -> Unit
) {
    val orphanConditions = conditions.filter { it.fromScene == STORY_LOST_ORIGIN || it.toScene == STORY_LOST_DESTINATION }
    val orphanAlternatives = alternatives.filter { it.fromScene == STORY_LOST_ORIGIN || it.toScene == STORY_LOST_DESTINATION }
    if (orphanConditions.isEmpty() && orphanAlternatives.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Referencias que necesitan reparación", fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFFB3261E))
        Text("Estas conexiones se conservaron porque una de sus escenas fue eliminada. Reasigna la referencia o elimínala expresamente.")
        orphanConditions.forEach { item ->
            OrphanReferenceRow(
                title = "Condición por objeto",
                fromScene = item.fromScene,
                toScene = item.toScene,
                scenes = scenes,
                onRepair = { from, to ->
                    onConditionsChange(conditions.map { if (it == item) it.copy(fromScene = from, toScene = to) else it })
                },
                onRemove = { onConditionsChange(conditions - item) }
            )
        }
        orphanAlternatives.forEach { item ->
            OrphanReferenceRow(
                title = when (item.kind) {
                    "object" -> "Alternativa por objeto"
                    "decision" -> "Alternativa por decisión"
                    else -> "Alternativa libre"
                },
                fromScene = item.fromScene,
                toScene = item.toScene,
                scenes = scenes,
                onRepair = { from, to ->
                    onAlternativesChange(alternatives.map { if (it == item) it.copy(fromScene = from, toScene = to) else it })
                },
                onRemove = { onAlternativesChange(alternatives - item) }
            )
        }
    }
}

@Composable
private fun OrphanReferenceRow(
    title: String,
    fromScene: String,
    toScene: String,
    scenes: List<String>,
    onRepair: (String, String) -> Unit,
    onRemove: () -> Unit
) {
    var fromMenu by remember(fromScene, toScene) { mutableStateOf(false) }
    var toMenu by remember(fromScene, toScene) { mutableStateOf(false) }
    var selectedFrom by remember(fromScene, toScene) { mutableStateOf(fromScene) }
    var selectedTo by remember(fromScene, toScene) { mutableStateOf(toScene) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (fromScene == STORY_LOST_ORIGIN) {
                OutlinedButton(onClick = { fromMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Origen: ${selectedFrom.takeUnless { it == STORY_LOST_ORIGIN } ?: "Escena eliminada"}") }
                DropdownMenu(expanded = fromMenu, onDismissRequest = { fromMenu = false }) {
                    scenes.forEach { scene -> DropdownMenuItem(text = { Text(scene) }, onClick = { selectedFrom = scene; fromMenu = false }) }
                }
            } else Text("Origen: $fromScene")
            if (toScene == STORY_LOST_DESTINATION) {
                OutlinedButton(onClick = { toMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Destino: ${selectedTo.takeUnless { it == STORY_LOST_DESTINATION } ?: "Escena eliminada"}") }
                DropdownMenu(expanded = toMenu, onDismissRequest = { toMenu = false }) {
                    scenes.filter { it != selectedFrom }.forEach { scene -> DropdownMenuItem(text = { Text(scene) }, onClick = { selectedTo = scene; toMenu = false }) }
                }
            } else Text("Destino: $toScene")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f)) { Text("Eliminar referencia") }
                Button(
                    onClick = { onRepair(selectedFrom, selectedTo) },
                    enabled = (fromScene != STORY_LOST_ORIGIN || selectedFrom != STORY_LOST_ORIGIN) &&
                        (toScene != STORY_LOST_DESTINATION || selectedTo != STORY_LOST_DESTINATION) &&
                        selectedFrom != selectedTo,
                    modifier = Modifier.weight(1f)
                ) { Text("Reparar") }
            }
        }
    }
}
