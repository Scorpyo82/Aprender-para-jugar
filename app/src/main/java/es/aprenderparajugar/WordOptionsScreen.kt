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
internal fun WordOptionsScreen(
    selectedDifficulty: String,
    easyReward: Int,
    normalReward: Int,
    hardReward: Int,
    isStarsMode: Boolean,
    message: String,
    onBack: () -> Unit,
    onDifficultyChange: (String) -> Unit,
    onRandom: () -> Unit,
    onChooseCategory: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Volver al inicio") }
        Text("Palabras", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text("Elige dificultad y cómo seleccionar la categoría.", fontSize = 17.sp, color = Color(0xFF667085))
        Text("Dificultad", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Fácil" to easyReward, "Normal" to normalReward, "Difícil" to hardReward).forEach { (level, minutes) ->
                Card(
                    Modifier.weight(1f).clickable { onDifficultyChange(level) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (selectedDifficulty == level) Color(0xFFE8F0FE) else Color.White),
                    border = BorderStroke(if (selectedDifficulty == level) 2.dp else 1.dp, if (selectedDifficulty == level) Color(0xFF3E6FA8) else Color(0xFFE4E0D8))
                ) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(level, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("$minutes ${if (isStarsMode) "estrellas" else "min"}", fontSize = 14.sp, color = Color(0xFF667085))
                    }
                }
            }
        }
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onRandom),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0D9)),
            border = BorderStroke(1.dp, Color(0xFFE4C07B))
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("🎲", fontSize = 38.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Al azar", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7B4B16))
                    Text("Elegir una categoría disponible al azar", fontSize = 15.sp, color = Color(0xFF795F43))
                }
                Text("›", fontSize = 30.sp, color = Color(0xFFB7791F))
            }
        }
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onChooseCategory),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F4E9)),
            border = BorderStroke(1.dp, Color(0xFFA4D3B2))
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("🗂️", fontSize = 38.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Elegir categoría", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF286344))
                    Text("Selecciona el tema que quieres practicar", fontSize = 15.sp, color = Color(0xFF52725F))
                }
                Text("›", fontSize = 30.sp, color = Color(0xFF27804D))
            }
        }
        if (message.isNotBlank()) Text(message, color = Color(0xFFB3261E), fontSize = 15.sp)
    }
}
