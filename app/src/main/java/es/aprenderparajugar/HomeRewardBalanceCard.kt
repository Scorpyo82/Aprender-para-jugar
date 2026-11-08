package es.aprenderparajugar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomeRewardBalanceCard(
    isStars: Boolean,
    formattedBalance: String,
    minutes: Int,
    remainderSeconds: Int
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isStars) Color(0xFFDCEBFF) else Color(0xFFFFD968)
        ),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            if (isStars) Color(0xFF78A9E6) else Color(0xFFE8B95D)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (isStars) "⭐" else "⏱️", fontSize = 38.sp)
            Column(Modifier.weight(1f)) {
                Text(
                    if (isStars) "Estrellas acumuladas" else "Tiempo acumulado",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isStars) Color(0xFF244A78) else Color(0xFF79501C)
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isStars) {
                        Text(
                            formattedBalance,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF59357D)
                        )
                    } else {
                        Text(
                            "$minutes",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF59357D)
                        )
                        Text(
                            "min",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF59357D),
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                        Text(
                            "$remainderSeconds",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF59357D)
                        )
                        Text(
                            "s",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF59357D),
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                }
            }
        }
    }
}
