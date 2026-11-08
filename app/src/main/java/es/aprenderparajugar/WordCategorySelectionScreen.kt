package es.aprenderparajugar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
internal fun WordCategorySelectionScreen(
    difficulty: String,
    availableCounts: Map<String, Int>,
    message: String,
    onBack: () -> Unit,
    onSelectCategory: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Volver a Palabras") }
        Text("Elegir categoría", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Dificultad seleccionada: $difficulty", fontSize = 16.sp, color = Color(0xFF667085))
        if (availableCounts.isEmpty()) {
            Text("No hay categorías con ejercicios activos de dificultad $difficulty. Vuelve atrás y prueba otra dificultad o crea/activa ejercicios.", color = Color(0xFFB3261E), fontSize = 16.sp)
        } else {
            availableCounts.forEach { (category, count) ->
                Card(
                    Modifier.fillMaxWidth().clickable { onSelectCategory(category) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFD8DDE5))
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(category, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("$count ejercicios disponibles", color = Color(0xFF667085), fontSize = 14.sp)
                        }
                        Text("›", fontSize = 26.sp, color = Color(0xFF3973AC))
                    }
                }
            }
        }
        if (message.isNotBlank()) Text(message, color = Color(0xFFB3261E), fontSize = 15.sp)
    }
}
