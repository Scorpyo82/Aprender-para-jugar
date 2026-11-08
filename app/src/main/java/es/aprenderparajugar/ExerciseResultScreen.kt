package es.aprenderparajugar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ExerciseResultScreen(
    rewardStars: Int,
    rewardIcon: String,
    rewardMessage: String,
    isPerfectCardsResult: Boolean,
    rewardAmountText: String,
    rewardUnitText: String,
    correctCount: Int,
    totalCount: Int,
    onHome: () -> Unit,
    onNextExercise: () -> Unit
) {
    val celebration = rememberInfiniteTransition(label = "celebracion")
    val iconScale by celebration.animateFloat(initialValue = 0.88f, targetValue = 1.12f, animationSpec = infiniteRepeatable(animation = tween(650), repeatMode = RepeatMode.Reverse), label = "rebote")
    val timeScale by celebration.animateFloat(initialValue = 1f, targetValue = 1.045f, animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse), label = "pulsoTiempo")

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (rewardStars > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }
            ) {
                repeat(rewardStars) { Text("⭐", fontSize = 76.sp) }
            }
        } else {
            Text(rewardIcon, fontSize = if (isPerfectCardsResult) 104.sp else 76.sp, modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale })
        }
        Spacer(Modifier.height(8.dp))
        Text(rewardMessage, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344), textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Text(rewardAmountText, fontSize = 58.sp, fontWeight = FontWeight.Black, color = Color(0xFF267A55), modifier = Modifier.graphicsLayer { scaleX = timeScale; scaleY = timeScale })
        Text(rewardUnitText, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF267A55))
        Spacer(Modifier.height(14.dp))
        Text("$correctCount de $totalCount aciertos", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF49445A))
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onHome, modifier = Modifier.weight(1f)) {
                Text("🏠  Volver al inicio", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onNextExercise, modifier = Modifier.weight(1f)) {
                Text("Siguiente ejercicio", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
