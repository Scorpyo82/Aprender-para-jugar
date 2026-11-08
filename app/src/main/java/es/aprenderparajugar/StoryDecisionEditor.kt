package es.aprenderparajugar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Editor independiente de las decisiones de una escena.
 * Lee y actualiza directamente el estado especializado del editor de cuentos.
 */
@Composable
internal fun StoryDecisionEditor(
    state: StoryEditorState,
    selectedScene: String
) {
    val prompt = state.decisionPromptByScene.value[selectedScene].orEmpty()
    val options = state.decisionOptionsByScene.value[selectedScene].orEmpty()
    val nextNumber = state.nextDecisionOptionNumberByScene.value[selectedScene] ?: 1

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Decisión de la escena", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Opcional. Si esta escena plantea una elección, escribe la pregunta y añade las respuestas que podrá seleccionar el niño.",
            fontSize = 13.sp,
            color = Color(0xFF667085)
        )
        OutlinedTextField(
            value = prompt,
            onValueChange = { value ->
                state.decisionPromptByScene.value = state.decisionPromptByScene.value.toMutableMap().apply {
                    put(selectedScene, value)
                }
            },
            label = { Text("Pregunta o planteamiento") },
            placeholder = { Text("¿Qué debería hacer?") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        options.forEachIndexed { index, option ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FA))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = option.text,
                        onValueChange = { value ->
                            state.decisionOptionsByScene.value = state.decisionOptionsByScene.value.toMutableMap().apply {
                                put(selectedScene, options.map { if (it.key == option.key) it.copy(text = value) else it })
                            }
                        },
                        label = { Text("Opción ${index + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (index > 0) {
                                    val reordered = options.toMutableList()
                                    val moved = reordered.removeAt(index)
                                    reordered.add(index - 1, moved)
                                    state.decisionOptionsByScene.value = state.decisionOptionsByScene.value.toMutableMap().apply {
                                        put(selectedScene, reordered)
                                    }
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.weight(1f)
                        ) { Text("↑ Subir") }
                        OutlinedButton(
                            onClick = {
                                if (index < options.lastIndex) {
                                    val reordered = options.toMutableList()
                                    val moved = reordered.removeAt(index)
                                    reordered.add(index + 1, moved)
                                    state.decisionOptionsByScene.value = state.decisionOptionsByScene.value.toMutableMap().apply {
                                        put(selectedScene, reordered)
                                    }
                                }
                            },
                            enabled = index < options.lastIndex,
                            modifier = Modifier.weight(1f)
                        ) { Text("↓ Bajar") }
                        OutlinedButton(
                            onClick = {
                                state.decisionOptionsByScene.value = state.decisionOptionsByScene.value.toMutableMap().apply {
                                    put(selectedScene, options.filterNot { it.key == option.key })
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Eliminar") }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = {
                state.decisionOptionsByScene.value = state.decisionOptionsByScene.value.toMutableMap().apply {
                    put(selectedScene, options + StoryDecisionOption("option_$nextNumber", ""))
                }
                state.nextDecisionOptionNumberByScene.value = state.nextDecisionOptionNumberByScene.value.toMutableMap().apply {
                    put(selectedScene, nextNumber + 1)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("＋ Añadir opción") }
        if (options.isNotEmpty()) {
            Text(
                "Las opciones conservan su identificador interno aunque cambies el texto o el orden.",
                fontSize = 12.sp,
                color = Color(0xFF667085)
            )
        }
    }
}
