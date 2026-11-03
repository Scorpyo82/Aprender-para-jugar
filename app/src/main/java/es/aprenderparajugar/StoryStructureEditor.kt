package es.aprenderparajugar

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

/**
 * Editor aislado de la estructura narrativa.
 * Solo configura conexiones y condiciones; nunca consulta ni modifica el inventario del jugador.
 */
@Composable
internal fun StoryStructureEditor(
    scenes: List<String>,
    connections: Map<String, List<String>>,
    conditions: List<StoryConnectionCondition>,
    availableObjects: List<StoredObject>,
    onConnectionsChange: (Map<String, List<String>>) -> Unit,
    onConditionsChange: (List<StoryConnectionCondition>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Estructura y caminos", fontWeight = FontWeight.Bold)
        Text(
            "Añade hasta dos salidas por escena. Para crear una bifurcación, al menos una salida debe exigir un objeto. Varias escenas pueden converger en un mismo destino.",
            color = Color(0xFF667085)
        )
        scenes.forEach { origin ->
            var addMenuExpanded by remember(origin, scenes) { mutableStateOf(false) }
            val destinations = connections[origin].orEmpty()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4EA))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(origin, fontWeight = FontWeight.Bold)
                    if (destinations.isEmpty()) {
                        Text("Sin salidas configuradas", color = Color(0xFF667085))
                    }
                    destinations.forEach { destination ->
                        val condition = conditions.firstOrNull {
                            it.fromScene == origin && it.toScene == destination
                        }
                        var objectMenuExpanded by remember(origin, destination) { mutableStateOf(false) }
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("↳ $destination", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                OutlinedButton(onClick = {
                                    onConnectionsChange(
                                        connections.toMutableMap().apply {
                                            put(origin, destinations.filterNot { it == destination })
                                        }
                                    )
                                    onConditionsChange(conditions.filterNot {
                                        it.fromScene == origin && it.toScene == destination
                                    })
                                }) { Text("Quitar") }
                            }
                            OutlinedButton(
                                onClick = { objectMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val selected = availableObjects.firstOrNull { it.folderName == condition?.requiredObject }
                                Text(selected?.metadata?.name ?: "Sin condición de objeto")
                            }
                            DropdownMenu(
                                expanded = objectMenuExpanded,
                                onDismissRequest = { objectMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sin condición de objeto") },
                                    onClick = {
                                        val next = conditions.filterNot {
                                            it.fromScene == origin && it.toScene == destination
                                        }
                                        onConditionsChange(next)
                                        objectMenuExpanded = false
                                    }
                                )
                                availableObjects.forEach { storedObject ->
                                    DropdownMenuItem(
                                        text = { Text(storedObject.metadata.name) },
                                        onClick = {
                                            val next = conditions.filterNot {
                                                it.fromScene == origin && it.toScene == destination
                                            } + StoryConnectionCondition(
                                                fromScene = origin,
                                                toScene = destination,
                                                requiredObject = storedObject.folderName,
                                                consumeRequiredObject = condition?.consumeRequiredObject ?: false
                                            )
                                            onConditionsChange(next)
                                            objectMenuExpanded = false
                                        }
                                    )
                                }
                            }
                            if (condition?.requiredObject != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = condition.consumeRequiredObject,
                                        onCheckedChange = { checked ->
                                            onConditionsChange(
                                                conditions.map {
                                                    if (it.fromScene == origin && it.toScene == destination)
                                                        it.copy(consumeRequiredObject = checked)
                                                    else it
                                                }
                                            )
                                        }
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Se consume")
                                }
                            }
                        }
                    }
                    if (destinations.size < 2) {
                        OutlinedButton(
                            onClick = { addMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("＋ Añadir salida") }
                        DropdownMenu(
                            expanded = addMenuExpanded,
                            onDismissRequest = { addMenuExpanded = false }
                        ) {
                            scenes.filter { it != origin && it !in destinations }.forEach { target ->
                                DropdownMenuItem(
                                    text = { Text(target) },
                                    onClick = {
                                        onConnectionsChange(
                                            connections.toMutableMap().apply {
                                                put(origin, destinations + target)
                                            }
                                        )
                                        addMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (availableObjects.isEmpty()) {
            Text(
                "No hay objetos disponibles. Puedes crear conexiones normales, pero para guardar una bifurcación necesitarás configurar al menos una condición por objeto.",
                color = Color(0xFF8A5A00)
            )
        }
    }
}
