package es.aprenderparajugar

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
    onOpenLibrary: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenInventory: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Objetos", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        StoryObjectsMenuCard("📚", "Librería de objetos", "Crear, consultar, filtrar y editar objetos", onOpenLibrary)
        StoryObjectsMenuCard("🗂️", "Colecciones", "Crear colecciones y organizar los objetos", onOpenCollections)
        StoryObjectsMenuCard("🛒", "Tienda", "Gestionar objetos a la venta, bloqueos e inventario", onOpenStore)
        StoryObjectsMenuCard("🎒", "Inventario del niño", "Consultar y retirar los objetos que posee", onOpenInventory)
    }
}

@Composable
private fun StoryObjectsMenuCard(icon: String, title: String, description: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE5))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(icon, fontSize = 30.sp, color = Color(0xFF87451F))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(description, fontSize = 14.sp, color = Color(0xFF667085))
            }
            Text("›", fontSize = 24.sp, color = Color(0xFF87451F))
        }
    }
}
