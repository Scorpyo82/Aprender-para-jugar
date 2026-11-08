package es.aprenderparajugar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun LazyListScope.StoryContentSettingsScreen(
    onOpenLibrary: () -> Unit,
    onOpenCollections: () -> Unit
) {
    item { Text("Gestión de cuentos", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    item {
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onOpenLibrary),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE5))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("📖", fontSize = 30.sp)
                Column {
                    Text("Biblioteca de cuentos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Crear, editar y organizar cuentos ilustrados", color = Color(0xFF667085))
                }
            }
        }
    }
    item {
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onOpenCollections),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE5))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🗂️", fontSize = 30.sp)
                Column {
                    Text("Colecciones de cuentos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Organizar cuentos por colecciones, sagas y universos", color = Color(0xFF667085))
                }
            }
        }
    }
}
