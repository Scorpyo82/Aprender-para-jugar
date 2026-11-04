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
    initialConnections: Map<String, List<String>>,
    initialConditions: List<StoryConnectionCondition>,
    initialStartScene: String,
    availableObjects: List<StoredObject>,
    message: String,
    onSave: (Map<String, List<String>>, List<StoryConnectionCondition>, String) -> Boolean,
    onCancel: () -> Unit
) {
    var connections by remember(scenes, initialConnections) {
        mutableStateOf(initialConnections.mapValues { it.value.toList() })
    }
    var conditions by remember(scenes, initialConditions) {
        mutableStateOf(initialConditions.toList())
    }
    var startScene by remember(scenes, initialStartScene) { mutableStateOf(initialStartScene) }
    var startMenuExpanded by remember { mutableStateOf(false) }
    val diagnostics = remember(scenes, connections, conditions, startScene) {
        StoryStructureAnalysis.analyze(scenes, connections, conditions, startScene)
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
            connections = connections,
            conditions = conditions,
            availableObjects = availableObjects,
            onConnectionsChange = { connections = it },
            onConditionsChange = { conditions = it }
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Comprobación de la estructura", fontWeight = FontWeight.Bold)
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
                "Estas comprobaciones son orientativas. Las advertencias no impiden guardar ni activar el cuento.",
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
                onClick = { onSave(connections, conditions, startScene) },
                modifier = Modifier.weight(1f)
            ) { Text("Guardar estructura") }
        }
    }
}
