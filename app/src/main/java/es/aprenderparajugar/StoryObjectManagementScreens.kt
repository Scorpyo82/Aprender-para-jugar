package es.aprenderparajugar

import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import java.io.File

@Composable
internal fun StoryObjectCollectionManagementScreen(
    collections: List<String>,
    draft: String,
    message: String,
    collectionToDelete: String,
    objects: List<StoredObject>,
    inventoryFolders: Set<String>,
    onDraftChange: (String) -> Unit,
    onAddCollection: () -> Unit,
    onRequestDelete: (String) -> Unit,
    onDeleteCollection: (String) -> Unit,
    onCancelDelete: () -> Unit,
    onEditObject: (StoredObject) -> Unit,
    onToggleInventory: (StoredObject, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Colecciones", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Organiza los objetos de la biblioteca común en colecciones.", fontSize = 14.sp, color = Color(0xFF667085))
        OutlinedTextField(value = draft, onValueChange = onDraftChange, label = { Text("Nueva colección") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = onAddCollection, modifier = Modifier.fillMaxWidth()) { Text("Añadir colección") }
        if (message.isNotBlank()) {
            Text(message, color = if (message.contains("No se pudo")) Color(0xFFB3261E) else Color(0xFF24733B))
        }
        if (collections.isEmpty()) Text("Todavía no hay colecciones.", color = Color(0xFF667085))
        collections.forEach { name ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp)) {
                    Text(name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    val collectionObjects = objects.filter { it.collection == ObjectRepository.collectionFolderName(name) }
                    Text("${collectionObjects.size} objetos", color = Color(0xFF667085))
                    collectionObjects.forEach { stored ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AndroidView(
                                    factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = false } },
                                    update = { view -> runCatching { view.setImageURI(Uri.fromFile(File(stored.directory, stored.metadata.image))) } },
                                    modifier = Modifier.size(64.dp)
                                )
                                Column(Modifier.weight(1f)) { Text(stored.metadata.name, fontWeight = FontWeight.SemiBold) }
                                OutlinedButton(onClick = { onEditObject(stored) }) { Text("Editar") }
                            }
                            val isInInventory = stored.folderName in inventoryFolders
                            OutlinedButton(onClick = { onToggleInventory(stored, isInInventory) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (isInInventory) "Retirar del inventario" else "Añadir al inventario")
                            }
                        }
                    }
                    if (collectionToDelete == name) {
                        Text("¿Eliminar esta colección?", fontWeight = FontWeight.SemiBold)
                        Text("Solo se puede eliminar si no contiene objetos.", fontSize = 13.sp, color = Color(0xFF667085))
                        Button(onClick = { onDeleteCollection(name) }, enabled = collectionObjects.isEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Eliminar colección") }
                        if (collectionObjects.isNotEmpty()) {
                            Text("No se puede eliminar: primero hay que reasignar o retirar sus objetos.", fontSize = 13.sp, color = Color(0xFFB3261E))
                        }
                        OutlinedButton(onClick = onCancelDelete, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                    } else {
                        OutlinedButton(onClick = { onRequestDelete(name) }, modifier = Modifier.fillMaxWidth()) { Text("Eliminar") }
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
    val inventoryObjects = objects.filter { inventoryFolders.contains(it.folderName) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Inventario actual", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Objetos que posee actualmente el niño. Los cambios realizados aquí también se reflejan en el editor de cada objeto.", fontSize = 14.sp, color = Color(0xFF667085))
        if (inventoryObjects.isEmpty()) {
            Text("El inventario está vacío. Añade objetos desde «Editar objeto».", color = Color(0xFF667085))
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(inventoryObjects, key = { it.folderName }) { stored ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AndroidView(
                                factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = false } },
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
    }
}
