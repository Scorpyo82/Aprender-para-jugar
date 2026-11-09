package es.aprenderparajugar

import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
internal fun StoryObjectCollectionManagementScreen(
    collections: List<String>,
    draft: String,
    message: String,
    collectionToDelete: String,
    objects: List<StoredObject>,
    onDraftChange: (String) -> Unit,
    onAddCollection: () -> Unit,
    onRequestDelete: (String) -> Unit,
    onDeleteCollection: (String) -> Unit,
    onCancelDelete: () -> Unit,
    onMoveObject: (StoredObject, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Colecciones", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Crea colecciones y organiza los objetos asignándolos o reasignándolos. La edición de los objetos se realiza desde la librería.", fontSize = 14.sp, color = Color(0xFF667085))
        OutlinedTextField(value = draft, onValueChange = onDraftChange, label = { Text("Nueva colección") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = onAddCollection, modifier = Modifier.fillMaxWidth()) { Text("Añadir colección") }
        if (message.isNotBlank()) Text(message, color = if (message.contains("No se pudo")) Color(0xFFB3261E) else Color(0xFF24733B))
        if (collections.isEmpty()) Text("Todavía no hay colecciones.", color = Color(0xFF667085))
        collections.forEach { name ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    val collectionObjects = objects.filter { it.collection == ObjectRepository.collectionFolderName(name) }
                    Text("${collectionObjects.size} objetos", color = Color(0xFF667085))
                    collectionObjects.forEach { stored ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AndroidView(
                                factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                update = { view -> runCatching { view.setImageURI(Uri.fromFile(File(stored.directory, stored.metadata.image))) } },
                                modifier = Modifier.size(54.dp)
                            )
                            Column(Modifier.weight(1f)) { Text(stored.metadata.name, fontWeight = FontWeight.SemiBold) }
                            var expanded by remember(stored.folderName) { mutableStateOf(false) }
                            androidx.compose.foundation.layout.Box {
                                OutlinedButton(onClick = { expanded = true }, enabled = collections.size > 1) { Text("Reasignar") }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    collections.filter { it != name }.forEach { target ->
                                        DropdownMenuItem(text = { Text(target) }, onClick = {
                                            onMoveObject(stored, target); expanded = false
                                        })
                                    }
                                }
                            }
                        }
                    }
                    if (collectionToDelete == name) {
                        Text("¿Eliminar esta colección?", fontWeight = FontWeight.SemiBold)
                        Text("Solo se puede eliminar si no contiene objetos.", fontSize = 13.sp, color = Color(0xFF667085))
                        Button(onClick = { onDeleteCollection(name) }, enabled = collectionObjects.isEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Eliminar colección") }
                        if (collectionObjects.isNotEmpty()) Text("No se puede eliminar: primero hay que reasignar sus objetos.", fontSize = 13.sp, color = Color(0xFFB3261E))
                        OutlinedButton(onClick = onCancelDelete, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                    } else {
                        OutlinedButton(onClick = { onRequestDelete(name) }, modifier = Modifier.fillMaxWidth()) { Text("Eliminar colección") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StoryObjectInventoryManagementScreen(
    objects: List<StoredObject>,
    inventoryFolders: Set<String>,
    onRemove: (StoredObject) -> Unit
) {
    val inventoryObjects = objects.filter { it.folderName in inventoryFolders }.sortedBy { it.metadata.name.lowercase() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Inventario del niño", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Objetos que posee actualmente. Desde aquí puedes retirarlos del inventario.", fontSize = 14.sp, color = Color(0xFF667085))
        if (inventoryObjects.isEmpty()) {
            Text("El inventario está vacío.", color = Color(0xFF667085))
        } else inventoryObjects.forEach { stored ->
            Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AndroidView(
                        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                        update = { view -> runCatching { view.setImageURI(Uri.fromFile(File(stored.directory, stored.metadata.image))) } },
                        modifier = Modifier.size(64.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(stored.metadata.name, fontWeight = FontWeight.SemiBold)
                        Text(stored.collection, fontSize = 12.sp, color = Color(0xFF667085))
                    }
                    OutlinedButton(onClick = { onRemove(stored) }) { Text("Retirar") }
                }
            }
        }
    }
}
