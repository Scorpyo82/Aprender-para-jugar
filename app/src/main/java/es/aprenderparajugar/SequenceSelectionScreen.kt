package es.aprenderparajugar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SequenceSelectionScreen(
    isDifficultyScreen: Boolean,
    chosenCategory: String,
    message: String,
    maxCards: Int,
    availableCounts: Map<String, Int>,
    rewardLabels: Map<String, String>,
    onBack: () -> Unit,
    onChooseType: (String, Boolean) -> Unit,
    onChooseDifficulty: (String) -> Unit
) {
    if (!isDifficultyScreen) {
        val sequenceTypes = listOf("Orden temporal", "Procesos", "Causa y consecuencia", "Ciclos")
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedButton(onClick = onBack) { Text("← Volver al inicio") }
                Text("Secuencias", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                Text("Elige el tipo de actividad que quieres practicar.", fontSize = 16.sp, color = Color(0xFF667085))
            }
            if (message.startsWith("No hay ejercicios")) item { Text(message, color = Color(0xFFB3261E), fontSize = 15.sp) }
            items(sequenceTypes) { type ->
                val availableCount = availableCounts[type] ?: 0
                val available = availableCount > 0
                Card(Modifier.fillMaxWidth().clickable { onChooseType(type, available) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (available) Color(0xFFF1F8EC) else Color(0xFFF0F0F0)), border = androidx.compose.foundation.BorderStroke(1.dp, if (available) Color(0xFFA9D79A) else Color(0xFFD0D0D0))) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(when(type) { "Orden temporal" -> "⏳"; "Procesos" -> "⚙️"; "Causa y consecuencia" -> "🔗"; else -> "🔄" }, fontSize = 30.sp)
                        Column(Modifier.weight(1f)) {
                            Text(type, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (available) Color(0xFF245C2A) else Color(0xFF777777))
                            Text(if (available) "$availableCount ejercicios disponibles" else "No hay ejercicios que cumplan la configuración", fontSize = 13.sp, color = Color(0xFF667085))
                        }
                        Text(if (available) "›" else "—", fontSize = 26.sp, color = Color(0xFF667085))
                    }
                }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedButton(onClick = onBack) { Text("← Volver a las actividades") }
                Text(chosenCategory, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                Text("Elige la dificultad. El premio dependerá del nivel seleccionado.", fontSize = 16.sp, color = Color(0xFF667085))
                if (message.startsWith("No hay ejercicios")) Text(message, color = Color(0xFFB3261E), fontSize = 15.sp)
            }
            items(listOf("Fácil", "Normal", "Difícil")) { level ->
                val availableCount = availableCounts[level] ?: 0
                val available = availableCount > 0
                Card(Modifier.fillMaxWidth().clickable { if (available) onChooseDifficulty(level) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (available) Color.White else Color(0xFFF0F0F0)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E5EA))) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(level, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text(if (available) "Premio: ${rewardLabels[level] ?: ""} · $availableCount ejercicios" else "Sin ejercicios que cumplan los filtros", fontSize = 14.sp, color = Color(0xFF667085))
                        }
                        Text(if (available) "EMPEZAR  ›" else "NO DISPONIBLE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (available) Color(0xFF39713A) else Color(0xFF9A4E00))
                    }
                }
            }
        }
    }
}
