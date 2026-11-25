package es.aprenderparajugar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

/**
 * Representación visual del saldo en modo estrellas.
 * El saldo sigue expresándose en segundos; solo se transforma aquí para mostrarlo.
 */
@Composable
internal fun StarsBalanceIndicator(
    seconds: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val safeSeconds = seconds.coerceAtLeast(0)
    val wholeStars = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    val dustPercent = if (remainingSeconds == 0) 0 else (ceil(remainingSeconds / 6.0).toInt() * 10).coerceAtMost(90)
    val iconSize = if (compact) 25.dp else 58.dp
    val starSize = if (compact) 14.sp else 31.sp
    val numberSize = if (compact) 14.sp else 27.sp
    val percentSize = if (compact) 14.sp else 34.sp

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier.size(iconSize).background(Color(0xFF20A9E8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("⭐", fontSize = starSize)
            }
            Text(
                wholeStars.toString(),
                fontSize = numberSize,
                fontWeight = FontWeight.Black,
                color = Color(0xFF253344),
                maxLines = 1
            )
        }
        Row(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(if (compact) 20.dp else 54.dp).background(Color(0xFF302A25), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✨", fontSize = if (compact) 12.sp else 29.sp)
            }
            Column(
                modifier = Modifier.weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 5.dp)
            ) {
                Text(
                    "$dustPercent %",
                    fontSize = percentSize,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFF0B52E),
                    maxLines = 1
                )
                LinearProgressIndicator(
                    progress = { dustPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(if (compact) 5.dp else 9.dp),
                    color = Color(0xFFEAB12E),
                    trackColor = Color(0xFF343434)
                )
            }
        }
    }
}
