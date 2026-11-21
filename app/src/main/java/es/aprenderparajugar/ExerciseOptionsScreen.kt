package es.aprenderparajugar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
internal fun ExerciseOptionsScreen(
    title: String, difficulty: String, easyReward: Int, normalReward: Int, hardReward: Int,
    isStarsMode: Boolean, message: String, surpriseDescription: String, mixDescription: String,
    surpriseAvailable: Boolean, chooseAvailable: Boolean, mixAvailable: Boolean,
    lockedDifficulties: Set<String> = emptySet(),
    manualChoiceLocked: Boolean = false, manualLimitMessage: String = "",
    onBack: () -> Unit, onDifficultyChange: (String) -> Unit,
    onSurprise: () -> Unit, onChooseTheme: () -> Unit, onMixThemes: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedButton(onClick = onBack) { Text("← Volver") }
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
            Text("Elige la dificultad y cómo quieres practicar.", fontSize = 16.sp, color = Color(0xFF667085))
        }
        item {
            Text("Dificultad", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF253344))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Fácil" to easyReward, "Normal" to normalReward, "Difícil" to hardReward).forEach { (level, reward) ->
                    Card(Modifier.weight(1f).clickable(enabled = level !in lockedDifficulties) { if (level !in lockedDifficulties) onDifficultyChange(level) }, shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = when { level in lockedDifficulties -> Color(0xFFF0F0F0); difficulty == level -> Color(0xFFEAF2FF); else -> Color.White }),
                        border = BorderStroke(if (difficulty == level && level !in lockedDifficulties) 2.dp else 1.dp, if (level in lockedDifficulties) Color(0xFFD7D7D7) else if (difficulty == level) Color(0xFF3567A8) else Color(0xFFE8E0D2))) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Text(level, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (level in lockedDifficulties) Color(0xFF888888) else Color(0xFF222222))
                                if (level in lockedDifficulties) Text(" 🔒⌛", fontSize = 14.sp)
                            }
                            Text("$reward ${if (isStarsMode) "⭐" else "min"}", fontSize = 12.sp, color = if (level in lockedDifficulties) Color(0xFF999999) else Color(0xFF667085))
                        }
                    }
                }
            }
        }
        item { ModeOption("🎲", "Sorpresa", surpriseDescription, Color(0xFFFFF0D9), Color(0xFFE8B66D), Color(0xFF7A4618), surpriseAvailable, onSurprise) }
        item { ModeOption("🗂️", "Elegir tema", "Escoge tú la categoría que quieres practicar.", Color(0xFFE4EDFF), Color(0xFFA9C2F3), Color(0xFF244D91), chooseAvailable && !manualChoiceLocked, onChooseTheme) }
        item { ModeOption("🐶🍎", "Mezclar temas", mixDescription, Color(0xFFE2F4E8), Color(0xFF9BD4AC), Color(0xFF205F37), mixAvailable, onMixThemes) }
        if (manualLimitMessage.isNotBlank()) item { Text(manualLimitMessage, fontSize = 13.sp, color = Color(0xFF8A4B08)) }
        if (message.isNotBlank()) item { Text(message, color = Color(0xFF9A4E00), fontWeight = FontWeight.SemiBold) }
    }
}
@Composable
private fun ModeOption(emoji: String, title: String, description: String, bg: Color, edge: Color, fg: Color, enabled: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (enabled) bg else Color(0xFFF0F0F0)),
        border = BorderStroke(1.dp, if (enabled) edge else Color(0xFFD7D7D7))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(58.dp).background(if (enabled) edge.copy(alpha = .35f) else Color(0xFFE0E0E0), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = if (emoji.length > 3) 21.sp else 30.sp, color = if (enabled) fg else Color(0xFF9A9A9A))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = if (enabled) fg else Color(0xFF777777))
                Text(description, fontSize = 14.sp, color = if (enabled) fg.copy(alpha = .82f) else Color(0xFF777777))
            }
            Text(if (enabled) "›" else "—", fontSize = 28.sp, color = if (enabled) fg else Color(0xFF777777))
        }
    }
}
