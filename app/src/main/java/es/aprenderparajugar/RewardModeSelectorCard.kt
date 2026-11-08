package es.aprenderparajugar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RewardModeSelectorCard(
    rewardMode: RewardMode,
    onSelectTime: () -> Unit,
    onSelectStars: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF2FF))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Modo de funcionamiento", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Elige cómo se presentará el mismo saldo de recompensas.",
                fontSize = 13.sp,
                color = Color(0xFF667085)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !rewardMode.isStars,
                    onClick = { if (rewardMode.isStars) onSelectTime() },
                    label = {
                        Text(
                            if (!rewardMode.isStars) "✓  ⏱ Tiempo" else "⏱ Tiempo",
                            fontWeight = if (!rewardMode.isStars) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFFF7F7FA),
                        labelColor = Color(0xFF475467),
                        selectedContainerColor = Color(0xFF2E7D32),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = rewardMode.isStars,
                    onClick = { if (!rewardMode.isStars) onSelectStars() },
                    label = {
                        Text(
                            if (rewardMode.isStars) "✓  ⭐ Estrellas" else "⭐ Estrellas",
                            fontWeight = if (rewardMode.isStars) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFFF7F7FA),
                        labelColor = Color(0xFF475467),
                        selectedContainerColor = Color(0xFF2E7D32),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}
