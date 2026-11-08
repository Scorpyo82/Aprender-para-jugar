package es.aprenderparajugar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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

/** Permite reparar referencias a objetos eliminados sin convertir la salida en libre. */
@Composable
internal fun StoryMissingObjectReferences(
    availableObjects: List<StoredObject>,
    conditions: List<StoryConnectionCondition>,
    alternatives: List<StoryConnectionAlternative>,
    onConditionsChange: (List<StoryConnectionCondition>) -> Unit,
    onAlternativesChange: (List<StoryConnectionAlternative>) -> Unit,
    connections: Map<String, List<String>>,
    onConnectionsChange: (Map<String, List<String>>) -> Unit
) {
    val availableFolders = availableObjects.map { it.folderName }.toSet()
    val missingAlternatives = alternatives.withIndex().filter { (_, item) ->
        item.kind == "object" && (item.requiredObject == null || item.requiredObject !in availableFolders)
    }
    val configuredOrigins = alternatives.map { it.fromScene }.toSet()
    val missingConditions = conditions.withIndex().filter { (_, item) ->
        item.fromScene !in configuredOrigins &&
            item.requiredObject != null &&
            item.requiredObject !in availableFolders
    }
    if (missingAlternatives.isEmpty() && missingConditions.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Referencias a objetos que ya no existen", fontWeight = FontWeight.Bold)
        Text("Reasigna cada referencia a un objeto disponible o elimina la alternativa. Una referencia rota no debe convertirse accidentalmente en una salida libre.")
        missingAlternatives.forEach { indexed ->
            val item = indexed.value
            MissingObjectReferenceRow(
                title = "Alternativa por objeto",
                oldObject = item.requiredObject ?: "Sin objeto asignado",
                availableObjects = availableObjects,
                onRepair = { folder ->
                    onAlternativesChange(alternatives.mapIndexed { index, current ->
                        if (index == indexed.index) current.copy(requiredObject = folder) else current
                    })
                    onConditionsChange(conditions.filterNot { it.fromScene == item.fromScene })
                },
                onRemove = {
                    val next = alternatives.filterIndexed { index, _ -> index != indexed.index }
                    onAlternativesChange(next)
                    onConditionsChange(conditions.filterNot { it.fromScene == item.fromScene })
                    if (next.none { it.fromScene == item.fromScene && it.toScene == item.toScene }) {
                        onConnectionsChange(connections.mapValues { (origin, destinations) ->
                            if (origin == item.fromScene) destinations.filterNot { it == item.toScene } else destinations
                        })
                    }
                }
            )
        }
        missingConditions.forEach { indexed ->
            val item = indexed.value
            MissingObjectReferenceRow(
                title = "Condición heredada",
                oldObject = item.requiredObject.orEmpty(),
                availableObjects = availableObjects,
                onRepair = { folder ->
                    onConditionsChange(conditions.mapIndexed { index, current ->
                        if (index == indexed.index) current.copy(requiredObject = folder) else current
                    })
                },
                onRemove = {
                    onConditionsChange(conditions.filterIndexed { index, _ -> index != indexed.index })
                    onConnectionsChange(connections.mapValues { (origin, destinations) ->
                        if (origin == item.fromScene) destinations.filterNot { it == item.toScene } else destinations
                    })
                }
            )
        }
    }
}

@Composable
private fun MissingObjectReferenceRow(
    title: String,
    oldObject: String,
    availableObjects: List<StoredObject>,
    onRepair: (String) -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember(oldObject) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text("Objeto no disponible: $oldObject")
            OutlinedButton(
                onClick = { menuExpanded = true },
                enabled = availableObjects.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Reasignar a otro objeto") }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                availableObjects.forEach { storedObject ->
                    DropdownMenuItem(
                        text = { Text(storedObject.metadata.name) },
                        onClick = {
                            onRepair(storedObject.folderName)
                            menuExpanded = false
                        }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Text("Eliminar esta referencia")
                }
            }
        }
    }
}
