package es.aprenderparajugar

import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
internal fun StoryObjectEditorScreen(
    editingFolder: String?,
    inInventory: Boolean,
    name: String,
    picture: String,
    collections: List<String>,
    selectedCollection: String,
    collectionMenuExpanded: Boolean,
    availableInStore: Boolean,
    requiresUnlock: Boolean,
    collectible: Boolean,
    storePrice: String,
    isStarsMode: Boolean,
    message: String,
    onToggleInventory: () -> Unit,
    onNameChange: (String) -> Unit,
    onCollectionMenuChange: (Boolean) -> Unit,
    onCollectionSelected: (String) -> Unit,
    onAvailableInStoreChange: (Boolean) -> Unit,
    onRequiresUnlockChange: (Boolean) -> Unit,
    onCollectibleChange: (Boolean) -> Unit,
    onStorePriceChange: (String) -> Unit,
    onChooseGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
    onCancel: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (editingFolder == null) "Añadir objeto" else "Editar objeto", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Completa los datos del objeto y guarda. Podrás reutilizarlo en distintos cuentos.", fontSize = 14.sp, color = Color(0xFF667085))
        if (editingFolder != null) {
            OutlinedButton(onClick = onToggleInventory, modifier = Modifier.fillMaxWidth()) {
                Text(if (inInventory) "Retirar del inventario" else "Añadir al inventario")
            }
            Text(
                if (inInventory) "Este objeto está actualmente en el inventario del niño."
                else "Este objeto todavía no está en el inventario del niño.",
                fontSize = 13.sp,
                color = Color(0xFF667085)
            )
        }
        OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("Nombre del objeto") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Column {
            Text("Colección", fontWeight = FontWeight.SemiBold)
            androidx.compose.foundation.layout.Box {
                OutlinedButton(onClick = { onCollectionMenuChange(true) }, enabled = collections.isNotEmpty() && editingFolder == null, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedCollection.ifBlank { "Seleccionar colección" })
                }
                DropdownMenu(expanded = collectionMenuExpanded && collections.isNotEmpty(), onDismissRequest = { onCollectionMenuChange(false) }) {
                    collections.forEach { name ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { onCollectionSelected(name) })
                    }
                }
            }
            if (collections.isEmpty()) Text("Todavía no existen colecciones. Crea una antes de añadir objetos.", fontSize = 13.sp, color = Color(0xFF667085))
        }
        if (picture.startsWith("content://") || picture.startsWith("file://")) {
            AndroidView(
                factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 320) } },
                update = { view -> try { view.setImageURI(Uri.parse(picture)) } catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) } },
                modifier = Modifier.fillMaxWidth().height(180.dp)
            )
        } else {
            Text(if (picture.isBlank()) "Todavía no has elegido una imagen." else "Imagen: $picture", fontSize = 18.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = availableInStore, onCheckedChange = onAvailableInStoreChange)
                Text("Aparece en la tienda", fontWeight = FontWeight.SemiBold)
            }
            Text("Indica si este objeto se ofrece en la tienda del juego.", fontSize = 12.sp, color = Color(0xFF667085))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = requiresUnlock && availableInStore, enabled = availableInStore, onCheckedChange = onRequiresUnlockChange)
                Text("Requiere desbloqueo", fontWeight = FontWeight.SemiBold, color = if (availableInStore) Color.Unspecified else Color(0xFF98A2B3))
            }
            Text("El niño tendrá que desbloquearlo en un cuento antes de que aparezca para comprarlo.", fontSize = 12.sp, color = Color(0xFF667085))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = collectible, onCheckedChange = onCollectibleChange)
                Text("Es un coleccionable", fontWeight = FontWeight.SemiBold)
            }
            Text("Los coleccionables tienen una sección especial en el inventario.", fontSize = 12.sp, color = Color(0xFF667085))
            if (availableInStore) {
                OutlinedTextField(
                    value = storePrice,
                    onValueChange = onStorePriceChange,
                    label = { Text("Precio · Minutos/Estrellas") },
                    suffix = { Text(if (isStarsMode) "⭐" else "min") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        OutlinedButton(onClick = onChooseGallery, modifier = Modifier.fillMaxWidth()) { Text("Elegir imagen de la galería") }
        OutlinedButton(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) { Text("📷 Hacer foto al objeto") }
        Button(onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth()) { Text("Guardar objeto") }
        if (message.isNotBlank()) Text(message, color = if (message.contains("correctamente")) Color(0xFF24733B) else Color(0xFFB3261E))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar y volver a Objetos") }
    }
}
