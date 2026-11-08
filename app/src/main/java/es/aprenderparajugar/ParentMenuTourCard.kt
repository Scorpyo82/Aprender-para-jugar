package es.aprenderparajugar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ParentMenuTourCard(
    onOpenTour: () -> Unit
) {
    Card(
        Modifier.clickable(onClick = onOpenTour),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF8))
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("📘", fontSize = 30.sp)
            Column {
                Text("Conoce Aprender para jugar", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Actividades, cuentos, recompensas y cómo personalizarlos",
                    color = Color(0xFF667085)
                )
            }
        }
    }
}
