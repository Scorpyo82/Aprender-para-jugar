package es.aprenderparajugar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
internal fun CardsCategorySelectionScreen(
    categories: List<String>,
    availableCounts: Map<String, Int>,
    questionCount: Int,
    manualCategoryLimitReached: Boolean,
    message: String,
    onBack: () -> Unit,
    onSelectCategory: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedButton(onClick = onBack) { Text("← Volver a las opciones") }
            Text("Elegir categoría", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
            Text("Toca una categoría para empezar la partida.", fontSize = 16.sp, color = Color(0xFF667085))
        }
        items(categories) { name ->
            val count = availableCounts[name] ?: 0
            Card(
                Modifier.fillMaxWidth().clickable(
                    enabled = count >= questionCount && !manualCategoryLimitReached
                ) { onSelectCategory(name) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (count >= questionCount) Color.White else Color(0xFFF0F0F0)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E0D2))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("📚", fontSize = 30.sp)
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF253344))
                        Text("$count tarjetas disponibles", fontSize = 13.sp, color = Color(0xFF667085))
                    }
                    Text(if (count >= questionCount) "›" else "—", fontSize = 28.sp, color = Color(0xFF3567A8))
                }
            }
        }
        if (manualCategoryLimitReached) {
            item {
                Text(
                    "Has alcanzado el límite diario de elecciones manuales.",
                    color = Color(0xFF8A4B08),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (message.isNotBlank()) {
            item {
                Text(message, color = Color(0xFF9A4E00), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
