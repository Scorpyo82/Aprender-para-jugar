package es.aprenderparajugar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
internal fun ExerciseStatusBar(balance: String, roundsUsed: Int?, roundsLimit: Int?, difficulty: String, balanceSeconds: Int? = null, isStarsMode: Boolean = false) {
    val rounds = if (roundsLimit == null) "∞" else "${(roundsUsed ?: 0).coerceIn(0, roundsLimit)}/$roundsLimit"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isStarsMode && balanceSeconds != null) StarsStatusItem(balanceSeconds, Modifier.weight(1f))
            else StatusItem("SALDO", balance, Modifier.weight(1f))
            StatusItem("RONDAS", rounds, Modifier.weight(1f))
            StatusItem("DIFICULTAD", difficulty, Modifier.weight(1f))
        }
        HorizontalDivider(color = Color(0xFFB9AD98), thickness = 1.dp)
    }
}

@Composable
private fun StatusItem(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F5F9))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF667085), textAlign = TextAlign.Center, maxLines = 1)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344), textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}


@Composable
private fun StarsStatusItem(seconds: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F5F9))) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("SALDO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF667085), textAlign = TextAlign.Center, maxLines = 1)
            StarsBalanceIndicator(seconds = seconds, compact = true, modifier = Modifier.fillMaxWidth())
        }
    }
}
