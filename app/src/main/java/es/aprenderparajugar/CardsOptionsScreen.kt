package es.aprenderparajugar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
internal fun CardsOptionsScreen(
    difficulty: String,
    easyReward: Int,
    normalReward: Int,
    hardReward: Int,
    isStarsMode: Boolean,
    manualCategoryLimitReached: Boolean,
    manualCategoryUsesToday: Int,
    dailyManualLimit: Int,
    message: String,
    onBack: () -> Unit,
    onDifficultyChange: (String) -> Unit,
    onRandom: () -> Unit,
    onMixed: () -> Unit,
    onChooseCategory: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedButton(onClick = onBack) { Text("← Volver al inicio") }
            Text("Tarjetas", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
            Text("Elige cómo quieres practicar.", fontSize = 16.sp, color = Color(0xFF667085))
        }
        item {
            Text("Dificultad", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF253344))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Fácil", "Normal", "Difícil").forEach { option ->
                    val reward = when (option) { "Normal" -> normalReward; "Difícil" -> hardReward; else -> easyReward }
                    Card(
                        Modifier.weight(1f).clickable { onDifficultyChange(option) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = if (difficulty == option) Color(0xFFEAF2FF) else Color.White),
                        border = BorderStroke(if (difficulty == option) 2.dp else 1.dp, if (difficulty == option) Color(0xFF3567A8) else Color(0xFFE8E0D2))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(option, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("$reward ${if (isStarsMode) "⭐" else "min"}", fontSize = 12.sp, color = Color(0xFF667085))
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onRandom), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0D9)), border = BorderStroke(1.dp, Color(0xFFE8B66D))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.width(56.dp).height(56.dp).background(Color(0xFFFFD9A3), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("🎲", fontSize = 30.sp) }
                    Column(Modifier.weight(1f)) { Text("Al azar", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7A4618)); Text("Una categoría sorpresa", fontSize = 14.sp, color = Color(0xFF795A3C)) }
                    Text("›", fontSize = 30.sp, color = Color(0xFFB96B20))
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onMixed), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE2F4E8)), border = BorderStroke(1.dp, Color(0xFF9BD4AC))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.width(56.dp).height(56.dp).background(Color(0xFFC6EAD0), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("🗂️", fontSize = 30.sp) }
                    Column(Modifier.weight(1f)) { Text("Mezcladas", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF205F37)); Text("Tarjetas de distintas categorías", fontSize = 14.sp, color = Color(0xFF476C51)) }
                    Text("›", fontSize = 30.sp, color = Color(0xFF25834A))
                }
            }
        }
        item {
            val locked = manualCategoryLimitReached
            Card(Modifier.fillMaxWidth().clickable(enabled = !locked, onClick = onChooseCategory), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = if (locked) Color(0xFFF0F0F0) else Color(0xFFE4EDFF)),
                border = BorderStroke(1.dp, if (locked) Color(0xFFD7D7D7) else Color(0xFFA9C2F3))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.width(56.dp).height(56.dp).background(if (locked) Color(0xFFE0E0E0) else Color(0xFFC9DAFF), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(if (locked) "🔒" else "▦", fontSize = 30.sp, color = Color(0xFF356BC5)) }
                    Column(Modifier.weight(1f)) { Text("Elegir categoría", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = if (locked) Color(0xFF777777) else Color(0xFF244D91)); Text(if (locked) "Límite diario alcanzado" else "Escoge qué quieres practicar", fontSize = 14.sp, color = if (locked) Color(0xFF777777) else Color(0xFF4D6794)) }
                    Text("›", fontSize = 30.sp, color = if (locked) Color(0xFF777777) else Color(0xFF356BC5))
                }
            }
        }
        if (message.isNotBlank()) item { Text(message, color = Color(0xFF9A4E00), fontWeight = FontWeight.SemiBold) }
        if (manualCategoryLimitReached) item {
            Text("Has usado las $dailyManualLimit elecciones manuales de hoy ($manualCategoryUsesToday/$dailyManualLimit). Podrás volver a elegir categoría mañana.", fontSize = 13.sp, color = Color(0xFF8A4B08))
        }
    }
}
