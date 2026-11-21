package es.aprenderparajugar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ExerciseStatusBar(
    balance: String,
    isStarsMode: Boolean,
    roundsRemaining: Int?,
    roundsLimit: Int?,
    difficulty: String
) {
    val rounds = if (roundsLimit == null) "∞" else "${roundsRemaining ?: 0}/$roundsLimit"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusItem("SALDO", "$balance ${if (isStarsMode) "⭐" else "min"}", Modifier.weight(1f))
        StatusItem("RONDAS", rounds, Modifier.weight(1f))
        StatusItem("DIFICULTAD", difficulty, Modifier.weight(1f))
    }
}

@Composable
private fun StatusItem(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F5F9))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF667085), textAlign = TextAlign.Center, maxLines = 1)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344), textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
