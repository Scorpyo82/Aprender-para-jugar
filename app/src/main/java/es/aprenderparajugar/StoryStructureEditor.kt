package es.aprenderparajugar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Editor de destinos y alternativas OR de la estructura narrativa. */
@Composable
internal fun StoryStructureEditor(
    scenes: List<String>,
    sceneSummaries: Map<String, String>,
    connections: Map<String, List<String>>,
    conditions: List<StoryConnectionCondition>,
    alternatives: List<StoryConnectionAlternative>,
    availableObjects: List<StoredObject>,
    decisionOptionsByScene: Map<String, List<StoryDecisionOption>>,
    decisionPromptsByScene: Map<String, String>,
    onConnectionsChange: (Map<String, List<String>>) -> Unit,
    onConditionsChange: (List<StoryConnectionCondition>) -> Unit,
    onAlternativesChange: (List<StoryConnectionAlternative>) -> Unit
) {
    fun updateAlternatives(next: List<StoryConnectionAlternative>) {
        onAlternativesChange(next)

        // Las alternativas nuevas admiten varias condiciones OR por conexión.
        // No deben duplicarse en el formato heredado, que solo admite una
        // condición por pareja origen/destino. Conservamos las condiciones
        // antiguas de las escenas que todavía no usan el modelo nuevo.
        val migratedOrigins = (alternatives.map { it.fromScene } + next.map { it.fromScene }).toSet()
        onConditionsChange(conditions.filterNot { it.fromScene in migratedOrigins })
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Estructura y caminos", fontWeight = FontWeight.Bold)
        Text(
            "Cada escena puede tener tantos destinos como necesite. Dentro de un mismo destino puedes añadir varias alternativas: basta con cumplir una de ellas (O). Las alternativas pueden ser libres, depender de un objeto o de una opción de decisión.",
            color = Color(0xFF667085)
        )
        scenes.forEach { origin ->
            var addDestinationExpanded by remember(origin, scenes) { mutableStateOf(false) }
            val destinations = connections[origin].orEmpty()
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.5.dp, Color(0xFFC9A98F)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E4D5))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(origin, fontWeight = FontWeight.Bold)
                    decisionPromptsByScene[origin].orEmpty().takeIf { it.isNotBlank() }?.let { prompt ->
                        Text("Pregunta: $prompt", color = Color(0xFF475467))
                    }
                    Text(
                        sceneSummaries[origin].orEmpty().ifBlank { "Sin resumen de ilustración" },
                        color = if (sceneSummaries[origin].isNullOrBlank()) Color(0xFF98A2B3) else Color(0xFF667085)
                    )
                    if (destinations.isEmpty()) Text("Sin destinos configurados", color = Color(0xFF667085))
                    destinations.forEach { destination ->
                        val isLost = destination == STORY_LOST_DESTINATION
                        val destinationAlternatives = alternatives.withIndex().filter {
                            it.value.fromScene == origin && it.value.toScene == destination
                        }
                        var replacementMenuExpanded by remember(origin, destination, scenes) { mutableStateOf(false) }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFFD6C5B7)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (isLost) "Destino huérfano" else "Destino: $destination",
                                            color = if (isLost) Color(0xFFB3261E) else Color(0xFF344054),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            if (isLost) "La escena de destino fue eliminada." else "Se llega aquí si se cumple cualquiera de las alternativas.",
                                            color = Color(0xFF667085)
                                        )
                                    }
                                    OutlinedButton(onClick = {
                                        onConnectionsChange(connections.toMutableMap().apply {
                                            put(origin, destinations.filterNot { it == destination })
                                        })
                                        updateAlternatives(alternatives.filterNot { it.fromScene == origin && it.toScene == destination })
                                    }) { Text("Quitar") }
                                }
                                if (isLost) {
                                    OutlinedButton(
                                        onClick = { replacementMenuExpanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Reasignar destino") }
                                    DropdownMenu(
                                        expanded = replacementMenuExpanded,
                                        onDismissRequest = { replacementMenuExpanded = false }
                                    ) {
                                        scenes.filter { it != origin && it !in destinations }.forEach { target ->
                                            DropdownMenuItem(text = { Text(target) }, onClick = {
                                                onConnectionsChange(connections.toMutableMap().apply {
                                                    put(origin, destinations.map { if (it == STORY_LOST_DESTINATION) target else it })
                                                })
                                                val repaired = alternatives.map {
                                                    if (it.fromScene == origin && it.toScene == STORY_LOST_DESTINATION) it.copy(toScene = target) else it
                                                }
                                                updateAlternatives(repaired)
                                                replacementMenuExpanded = false
                                            })
                                        }
                                    }
                                } else {
                                    if (destinationAlternatives.isEmpty()) {
                                        Text(
                                            "Este destino todavía no tiene alternativas configuradas.",
                                            color = Color(0xFF8A5A00)
                                        )
                                    }
                                    destinationAlternatives.forEach { indexed ->
                                        val alternative = indexed.value
                                        var objectMenuExpanded by remember(origin, destination, indexed.index) { mutableStateOf(false) }
                                        var consumeExpanded by remember(origin, destination, indexed.index) { mutableStateOf(false) }
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "Alternativa ${destinationAlternatives.indexOf(indexed) + 1}",
                                                    modifier = Modifier.weight(1f),
                                                    fontWeight = FontWeight.Medium
                                                )
                                                OutlinedButton(onClick = {
                                                    updateAlternatives(alternatives.filterIndexed { index, _ -> index != indexed.index })
                                                }) { Text("Eliminar") }
                                            }
                                            when (alternative.kind) {
                                                "object" -> {
                                                    OutlinedButton(
                                                        onClick = { objectMenuExpanded = true },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        val selected = availableObjects.firstOrNull { it.folderName == alternative.requiredObject }
                                                        Text(selected?.metadata?.name ?: "Seleccionar objeto requerido")
                                                    }
                                                    DropdownMenu(
                                                        expanded = objectMenuExpanded,
                                                        onDismissRequest = { objectMenuExpanded = false }
                                                    ) {
                                                        availableObjects.forEach { storedObject ->
                                                            DropdownMenuItem(text = { Text(storedObject.metadata.name) }, onClick = {
                                                                updateAlternatives(alternatives.mapIndexed { index, item ->
                                                                    if (index == indexed.index) item.copy(requiredObject = storedObject.folderName) else item
                                                                })
                                                                objectMenuExpanded = false
                                                            })
                                                        }
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = alternative.consumeRequiredObject,
                                                            onCheckedChange = { checked ->
                                                                updateAlternatives(alternatives.mapIndexed { index, item ->
                                                                    if (index == indexed.index) item.copy(consumeRequiredObject = checked) else item
                                                                })
                                                            }
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Consumir el objeto al utilizarlo")
                                                    }
                                                }
                                                "decision" -> {
                                                    val options = decisionOptionsByScene[origin].orEmpty()
                                                    val selected = options.firstOrNull { it.key == alternative.decisionOptionKey }
                                                    var decisionMenuExpanded by remember(origin, destination, indexed.index, alternative.decisionOptionKey, options) { mutableStateOf(false) }
                                                    OutlinedButton(onClick = { decisionMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                                        Text(selected?.text ?: "⚠ Opción eliminada o sin referencia: ${alternative.decisionOptionKey ?: "sin ID"}")
                                                    }
                                                    DropdownMenu(expanded = decisionMenuExpanded, onDismissRequest = { decisionMenuExpanded = false }) {
                                                        options.filter { option ->
                                                            alternatives.none { other -> other.fromScene == origin && other.kind == "decision" && other.decisionOptionKey == option.key && other.toScene != destination }
                                                        }.forEach { option ->
                                                            DropdownMenuItem(text = { Text(option.text) }, onClick = {
                                                                updateAlternatives(alternatives.mapIndexed { index, item -> if (index == indexed.index) item.copy(decisionOptionKey = option.key) else item })
                                                                decisionMenuExpanded = false
                                                            })
                                                        }
                                                    }
                                                    if (selected == null) Text("Esta referencia debe reasignarse a una opción existente o eliminarse. No se ha borrado automáticamente.", color = Color(0xFFB3261E))
                                                }
                                                else -> Text("Libre: no requiere objeto ni decisión", color = Color(0xFF667085))
                                            }
                                        }
                                    }
                                    var addAlternativeExpanded by remember(origin, destination) { mutableStateOf(false) }
                                    OutlinedButton(
                                        onClick = { addAlternativeExpanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("＋ Añadir alternativa a este destino") }
                                    DropdownMenu(
                                        expanded = addAlternativeExpanded,
                                        onDismissRequest = { addAlternativeExpanded = false }
                                    ) {
                                        DropdownMenuItem(text = { Text("Alternativa libre") }, onClick = {
                                            updateAlternatives(alternatives + StoryConnectionAlternative(origin, destination, "free"))
                                            addAlternativeExpanded = false
                                        })
                                        DropdownMenuItem(
                                            text = { Text("Depende de una decisión") },
                                            enabled = decisionOptionsByScene[origin].orEmpty().any { option -> alternatives.none { it.fromScene == origin && it.kind == "decision" && it.decisionOptionKey == option.key && it.toScene != destination } },
                                            onClick = {
                                                val option = decisionOptionsByScene[origin].orEmpty().first { candidate -> alternatives.none { it.fromScene == origin && it.kind == "decision" && it.decisionOptionKey == candidate.key && it.toScene != destination } }
                                                updateAlternatives(alternatives + StoryConnectionAlternative(origin, destination, "decision", decisionOptionKey = option.key))
                                                addAlternativeExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Requiere un objeto") },
                                            enabled = availableObjects.isNotEmpty(),
                                            onClick = {
                                                val first = availableObjects.first()
                                                updateAlternatives(alternatives + StoryConnectionAlternative(origin, destination, "object", first.folderName))
                                                addAlternativeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { addDestinationExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("＋ Añadir destino") }
                    DropdownMenu(
                        expanded = addDestinationExpanded,
                        onDismissRequest = { addDestinationExpanded = false }
                    ) {
                        scenes.filter { it != origin && it !in destinations }.forEach { target ->
                            DropdownMenuItem(text = { Text(target) }, onClick = {
                                onConnectionsChange(connections.toMutableMap().apply { put(origin, destinations + target) })
                                updateAlternatives(alternatives + StoryConnectionAlternative(origin, target, "free"))
                                addDestinationExpanded = false
                            })
                        }
                    }
                }
            }
        }
        if (availableObjects.isEmpty()) {
            Text(
                "No hay objetos disponibles. Puedes configurar alternativas libres; para las alternativas condicionadas por objeto, primero tendrás que crear objetos.",
                color = Color(0xFF8A5A00)
            )
        }
    }
}
