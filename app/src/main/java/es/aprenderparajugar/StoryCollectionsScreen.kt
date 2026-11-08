package es.aprenderparajugar

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StoryCollectionsScreen(
    context: Context,
    collections: List<String>,
    draft: String,
    message: String,
    editingCollection: String?,
    disabledCollections: Set<String>,
    coverRevision: Int,
    collectionToDelete: String?,
    onDraftChange: (String) -> Unit,
    onAddCollection: () -> Unit,
    onToggleEditing: (String) -> Unit,
    onToggleCollection: (String) -> Unit,
    onChooseCover: (String) -> Unit,
    onRemoveCover: (String) -> Unit,
    onDeleteAndStories: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onCancelDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Colecciones", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Organiza tus cuentos en colecciones. Cada una puede tener su propia portada.", fontSize = 14.sp, color = Color(0xFF667085))
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text("Nueva colección") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(onClick = onAddCollection, modifier = Modifier.fillMaxWidth()) {
            Text("Añadir colección")
        }
        if (message.isNotBlank()) {
            Text(message, color = if (message.startsWith("No se pudo")) Color(0xFFB3261E) else Color(0xFF24733B))
        }
        if (collections.isEmpty()) {
            Text("Todavía no hay colecciones.", color = Color(0xFF667085))
        }
        collections.forEach { name ->
            val coverFile = remember(name, coverRevision) {
                StoryRepository.collectionCoverFile(context, StoryRepository.collectionFolderName(name))
            }
            val isEditing = editingCollection == name
            Card(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (coverFile != null) {
                        StoryImagePreview(coverFile, "Portada de $name")
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(150.dp)
                                .background(Color(0xFFFFF3E8), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("📚", fontSize = 42.sp)
                                Text("Sin portada", fontSize = 13.sp, color = Color(0xFF667085))
                            }
                        }
                    }
                    Text(name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = { onToggleEditing(name) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isEditing) "Cerrar edición" else "Editar colección")
                    }
                    if (isEditing) {
                        HorizontalDivider()
                        Text(if (name in disabledCollections) "Colección desactivada" else "Colección activa", color = Color(0xFF667085))
                        OutlinedButton(onClick = { onToggleCollection(name) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (name in disabledCollections) "Activar colección" else "Desactivar colección")
                        }
                        OutlinedButton(onClick = { onChooseCover(name) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (coverFile == null) "Elegir portada" else "Cambiar portada")
                        }
                        if (coverFile != null) {
                            OutlinedButton(onClick = { onRemoveCover(name) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Quitar portada")
                            }
                        }
                        if (collectionToDelete == name) {
                            Text("¿Cómo quieres eliminar esta colección?", fontWeight = FontWeight.SemiBold)
                            Text("Eliminar colección y cuentos", fontWeight = FontWeight.SemiBold)
                            Text("Se eliminarán también todos los cuentos que pertenezcan a esta colección.", fontSize = 13.sp, color = Color(0xFFB3261E))
                            Button(onClick = { onDeleteAndStories(name) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Eliminar colección y cuentos")
                            }
                            Text("Eliminar colección y reasignar cuentos a:", fontWeight = FontWeight.SemiBold)
                            collections.filterNot { it == name }.forEach { destination ->
                                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                    Text(destination)
                                }
                            }
                            if (collections.size <= 1) {
                                Text("No hay otra colección a la que reasignar los cuentos.", fontSize = 13.sp, color = Color(0xFF667085))
                            } else {
                                Text("La reasignación se habilitará cuando existan cuentos vinculados a estas colecciones.", fontSize = 13.sp, color = Color(0xFF667085))
                            }
                            OutlinedButton(onClick = onCancelDelete, modifier = Modifier.fillMaxWidth()) {
                                Text("Cancelar")
                            }
                        } else {
                            OutlinedButton(onClick = { onRequestDelete(name) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Eliminar / reasignar")
                            }
                        }
                    }
                }
            }
        }
    }
}
