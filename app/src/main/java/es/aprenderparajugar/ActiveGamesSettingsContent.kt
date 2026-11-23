package es.aprenderparajugar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable

internal fun LazyListScope.activeGamesItems(
    gameActiveCards: Boolean,
    gameActiveSequences: Boolean,
    gameActiveWords: Boolean,
    gameActiveStories: Boolean,
    onCardsChange: (Boolean) -> Unit,
    onSequencesChange: (Boolean) -> Unit,
    onWordsChange: (Boolean) -> Unit,
    onStoriesChange: (Boolean) -> Unit
) {
    item { Text("Actividades activas", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    item {
        Text(
            "Desactiva los modos que no quieras incluir en la versión de pruebas. Los modos bloqueados mostrarán un candado y no se podrán abrir.",
            fontSize = 14.sp,
            color = Color(0xFF667085)
        )
    }
    item {
        ActiveGameSettingCard("Tarjetas", "Parejas de imágenes y palabras", gameActiveCards, onCardsChange)
    }
    item {
        ActiveGameSettingCard("Secuencias", "Ordenar secuencias y procesos", gameActiveSequences, onSequencesChange)
    }
    item {
        ActiveGameSettingCard("Palabras", "Formar palabras con letras", gameActiveWords, onWordsChange)
    }
    item {
        ActiveGameSettingCard("Cuentos", "Historias ilustradas e interactivas", gameActiveStories, onStoriesChange)
    }
}

@Composable
private fun ActiveGameSettingCard(
    title: String,
    description: String,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(checked = active, onCheckedChange = onActiveChange)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(description, fontSize = 14.sp, color = Color(0xFF667085))
            }
            Text(
                if (active) "Activo" else "🔒",
                color = if (active) Color(0xFF24733B) else Color(0xFFB3261E)
            )
        }
    }
}
