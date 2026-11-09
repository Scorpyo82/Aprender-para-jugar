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
internal fun StoryObjectLibraryScreen(
    objects: List<StoredObject>,
    collections: List<String>,
    selectedCollection: String,
    onCollectionSelected: (String) -> Unit,
    onCreate: () -> Unit,
    onEdit: (StoredObject) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Librería de objetos", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("＋ Crear objeto") }
        var expanded by remember { mutableStateOf(false) }
        Column {
            Text("Filtrar por colección", fontWeight = FontWeight.SemiBold)
            androidx.compose.foundation.layout.Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selectedCollection == ALL_OBJECT_COLLECTIONS) "Todas las colecciones" else selectedCollection)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Todas las colecciones") }, onClick = {
                        onCollectionSelected(ALL_OBJECT_COLLECTIONS); expanded = false
                    })
                    collections.forEach { collection ->
                        DropdownMenuItem(text = { Text(collection) }, onClick = {
                            onCollectionSelected(collection); expanded = false
                        })
                    }
                }
            }
        }
        val filtered = objects.filter {
            selectedCollection == ALL_OBJECT_COLLECTIONS ||
                it.collection == ObjectRepository.collectionFolderName(selectedCollection)
        }.sortedBy { it.metadata.name.lowercase() }
        if (filtered.isEmpty()) {
            Text(if (objects.isEmpty()) "Todavía no hay objetos. Crea el primero." else "No hay objetos en esta colección.", color = Color(0xFF667085))
        } else {
            filtered.forEach { stored ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AndroidView(
                            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                            update = { view -> runCatching { view.setImageURI(Uri.fromFile(File(stored.directory, stored.metadata.image))) } },
                            modifier = Modifier.size(64.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(stored.metadata.name, fontWeight = FontWeight.SemiBold)
                            Text(collections.firstOrNull { ObjectRepository.collectionFolderName(it) == stored.collection } ?: stored.collection, fontSize = 12.sp, color = Color(0xFF667085))
                        }
                        OutlinedButton(onClick = { onEdit(stored) }) { Text("Editar") }
                    }
                }
            }
        }
    }
}

const val ALL_OBJECT_COLLECTIONS = "Todas"

@Composable
internal fun StoryObjectStoreManagementScreen(
    objects: List<StoredObject>,
    inventoryFolders: Set<String>,
    unlockedFolders: Set<String>,
    message: String,
    onToggleInventory: (StoredObject) -> Unit,
    onToggleUnlock: (StoredObject) -> Unit,
    onEdit: (StoredObject) -> Unit
) {
    val storeObjects = objects.filter { it.metadata.availableInStore }.sortedBy { it.metadata.name.lowercase() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tienda", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Todos los objetos configurados para la venta, independientemente de si el niño ya los posee o los ha desbloqueado.", fontSize = 14.sp, color = Color(0xFF667085))
        if (message.isNotBlank()) Text(message, color = Color(0xFF24733B), fontSize = 13.sp)
        if (storeObjects.isEmpty()) {
            Text("No hay objetos configurados para la tienda.", color = Color(0xFF667085))
        } else storeObjects.forEach { stored ->
            val owned = stored.folderName in inventoryFolders
            val locked = stored.metadata.requiresUnlock && stored.folderName !in unlockedFolders
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (owned) Color(0xFFEAF5EC) else Color.White)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AndroidView(
                            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                            update = { view -> runCatching { view.setImageURI(Uri.fromFile(File(stored.directory, stored.metadata.image))) } },
                            modifier = Modifier.size(68.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(stored.metadata.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    owned && locked -> "En inventario · Bloqueado para compra"
                                    owned && stored.metadata.requiresUnlock -> "En inventario · Desbloqueado"
                                    owned -> "En inventario · Disponible en tienda"
                                    locked -> "Bloqueado"
                                    else -> "Disponible para comprar"
                                },
                                color = if (locked) Color(0xFFB3261E) else Color(0xFF24733B),
                                fontSize = 13.sp
                            )
                            Text("Precio: ${stored.metadata.storePriceMinutes} min/estrellas", color = Color(0xFF667085), fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { onEdit(stored) }) { Text("Editar") }
                    }
                    if (stored.metadata.requiresUnlock) {
                        OutlinedButton(onClick = { onToggleUnlock(stored) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (locked) "Desbloquear para la tienda" else "Volver a bloquear")
                        }
                    }
                    OutlinedButton(onClick = { onToggleInventory(stored) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (owned) "Retirar del inventario" else "Añadir al inventario")
                    }
                }
            }
        }
    }
}
