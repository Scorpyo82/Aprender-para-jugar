package es.aprenderparajugar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
    availableObjects: List<StoredObject>,
    message: String,
    onSave: (Map<String, List<String>>, List<StoryConnectionCondition>) -> Boolean,
    onCancel: () -> Unit
) {
    var connections by remember(scenes, initialConnections) {
        mutableStateOf(initialConnections.mapValues { it.value.toList() })
    }
    var conditions by remember(scenes, initialConditions) {
        mutableStateOf(initialConditions.toList())
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Estructura narrativa", fontWeight = FontWeight.Bold)
        Text("Define qué escenas pueden seguir a cada una. Puedes crear bifurcaciones condicionadas por objetos y hacer que varias ramas converjan.")
        StoryStructureEditor(
            scenes = scenes,
            connections = connections,
            conditions = conditions,
            availableObjects = availableObjects,
            onConnectionsChange = { connections = it },
            onConditionsChange = { conditions = it }
        )
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
                onClick = { onSave(connections, conditions) },
                modifier = Modifier.weight(1f)
            ) { Text("Guardar estructura") }
        }
    }
}
