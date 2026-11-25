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
    compact: Boolean = false,
    showStarIcon: Boolean = true
) {
    val safeSeconds = seconds.coerceAtLeast(0)
    val wholeStars = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    // Redondeo visual por decenas, sin convertir 59 segundos en un 100 % prematuro.
    val dustPercent = if (remainingSeconds == 0) 0 else (ceil(remainingSeconds / 6.0).toInt() * 10).coerceAtMost(90)

    if (compact) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showStarIcon) Text("⭐", fontSize = 17.sp, maxLines = 1)
                Text(wholeStars.toString(), fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF253344), maxLines = 1)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StardustIcon(size = 19.dp, fontSize = 11.sp)
                    Text("$dustPercent%", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFFB27A08), maxLines = 1)
                }
                LinearProgressIndicator(
                    progress = { dustPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = Color(0xFFEAB12E),
                    trackColor = Color(0xFF343434)
                )
            }
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (showStarIcon) Text("⭐", fontSize = 30.sp, maxLines = 1)
                Text(wholeStars.toString(), fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color(0xFF253344), maxLines = 1)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StardustIcon(size = 54.dp, fontSize = 29.sp)
                    Text("$dustPercent %", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFFF0B52E), maxLines = 1)
                }
                LinearProgressIndicator(
                    progress = { dustPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(9.dp),
                    color = Color(0xFFEAB12E),
                    trackColor = Color(0xFF343434)
                )
            }
        }
    }
}

@Composable
private fun StardustIcon(size: androidx.compose.ui.unit.Dp, fontSize: androidx.compose.ui.unit.TextUnit) {
    Box(
        modifier = Modifier.size(size).background(Color(0xFF302A25), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("✨", fontSize = fontSize)
    }
}
