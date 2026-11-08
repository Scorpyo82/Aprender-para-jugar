package es.aprenderparajugar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun StoryObjectsMenuScreen(
    onCreateObjects: () -> Unit,
    onOpenCollection: () -> Unit,
    onOpenInventory: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Objetos", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        StoryObjectsMenuCard(
            icon = "＋",
            title = "Creación de objetos",
            description = "Crear y editar objetos reutilizables en los cuentos",
            onClick = onCreateObjects
        )
        StoryObjectsMenuCard(
            icon = "📚",
            title = "Colección de objetos",
            description = "Consultar y administrar la biblioteca común de objetos",
            onClick = onOpenCollection
        )
        StoryObjectsMenuCard(
            icon = "🎒",
            title = "Inventario actual",
            description = "Consultar y gestionar los objetos que posee el niño",
            onClick = onOpenInventory
        )
    }
}

@Composable
private fun StoryObjectsMenuCard(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE5))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(icon, fontSize = 30.sp, color = Color(0xFF87451F))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(description, fontSize = 14.sp, color = Color(0xFF667085))
            }
            Text("›", fontSize = 24.sp, color = Color(0xFF87451F))
        }
    }
}
