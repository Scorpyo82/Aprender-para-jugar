package es.aprenderparajugar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyListScope

fun LazyListScope.CardsSettingsScreen(
    questionCount: Int,
    dailyManualLimit: Int,
    onOpenLibrary: () -> Unit,
    onOpenCategories: () -> Unit,
    onQuestionCountChange: (Int) -> Unit,
    onDailyManualLimitChange: (Int) -> Unit
) {
    item { Text("Configurar Tarjetas", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    item {
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onOpenLibrary),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🖼️", fontSize = 28.sp)
                Column {
                    Text("Biblioteca de tarjetas", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Palabras, imágenes, dificultad y estado de cada tarjeta", color = Color(0xFF667085))
                }
            }
        }
    }
    item {
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onOpenCategories),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🗂️", fontSize = 28.sp)
                Column {
                    Text("Categorías", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Organiza el vocabulario por temas", color = Color(0xFF667085))
                }
            }
        }
    }
    item {
        Text("Parejas por partida", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Número de parejas de palabras e imágenes que tendrá cada ejercicio.",
            fontSize = 14.sp,
            color = Color(0xFF667085)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { onQuestionCountChange(-1) }, enabled = questionCount > 1) { Text("−") }
            Text("$questionCount", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { onQuestionCountChange(1) }, enabled = questionCount < 10) { Text("+") }
        }
    }
    item {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Límite diario para elegir categoría", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Número máximo de veces al día que el menor puede elegir la categoría. Al alcanzar el límite, podrá seguir practicando con las demás opciones.",
            fontSize = 14.sp,
            color = Color(0xFF667085)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { onDailyManualLimitChange(-1) },
                enabled = dailyManualLimit > 0
            ) { Text("−") }
            Text("$dailyManualLimit", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = { onDailyManualLimitChange(1) },
                enabled = dailyManualLimit < 10
            ) { Text("+") }
        }
    }
    item { Text("Los cambios se guardan en este dispositivo.", fontSize = 13.sp, color = Color(0xFF667085)) }
}
