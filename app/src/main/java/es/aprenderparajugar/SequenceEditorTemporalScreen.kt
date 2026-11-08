package es.aprenderparajugar

import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView

fun LazyListScope.SequenceEditorTemporalScreen(
    editing: Boolean,
    title: String,
    onTitleChange: (String) -> Unit,
    sequenceType: String,
    onSequenceTypeChange: (String) -> Unit,
    difficulty: String,
    onDifficultyChange: (String) -> Unit,
    category: String,
    categories: List<String>,
    categoryMenuExpanded: Boolean,
    onCategoryMenuExpandedChange: (Boolean) -> Unit,
    onCategorySelected: (String) -> Unit,
    cards: List<String>,
    onCardChange: (Int, String) -> Unit,
    pictures: List<String>,
    onRemoveCard: (Int) -> Unit,
    onPickGallery: (Int) -> Unit,
    onTakePhoto: (Int) -> Unit,
    onRemovePicture: (Int) -> Unit,
    onAddCard: () -> Unit,
    alternativeDraft: String,
    onAlternativeDraftChange: (String) -> Unit,
    message: String,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    item {
        Text(if (!editing) "Nuevo ejercicio" else "Editar ejercicio", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
    item {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Nombre del ejercicio") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
    item {
        Text("Tipo de secuencia", fontWeight = FontWeight.Bold)
        listOf("Orden temporal", "Procesos", "Causa y consecuencia", "Ciclos").forEach { type ->
            OutlinedButton(
                onClick = { onSequenceTypeChange(type) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (sequenceType == type) "✓ $type" else type) }
        }
    }
    item {
        Text("Dificultad", fontWeight = FontWeight.Bold)
        listOf("Fácil", "Normal", "Difícil").forEach { level ->
            OutlinedButton(
                onClick = { onDifficultyChange(level) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (difficulty == level) "✓ $level" else level) }
        }
        Text(
            "Cada niño aprende a su ritmo. La dificultad indica la complejidad de la actividad y es orientativa. Elige el nivel que mejor se adapte a sus necesidades y experiencia.",
            fontSize = 13.sp,
            color = Color(0xFF667085)
        )
    }
    item {
        Text("Categoría temática", fontWeight = FontWeight.Bold)
        Box {
            OutlinedButton(
                onClick = { onCategoryMenuExpandedChange(true) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🗂️ $category  ▾", maxLines = 1)
            }
            DropdownMenu(
                expanded = categoryMenuExpanded,
                onDismissRequest = { onCategoryMenuExpandedChange(false) },
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                categories.distinct().sorted().forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onCategoryMenuExpandedChange(false)
                            // Category selection is handled by the supplied callback below.
                            onCategorySelected(name)
                        }
                    )
                }
            }
        }
        Text("Las categorías se crean y gestionan desde el menú Categorías de la biblioteca.", fontSize = 12.sp, color = Color(0xFF667085))
    }
    item {
        Text("Tarjetas en el orden correcto", fontWeight = FontWeight.Bold)
        Text("Edita los pasos en su orden correcto. Máximo 6 tarjetas.", fontSize = 13.sp, color = Color(0xFF667085))
    }
    cards.forEachIndexed { index, card ->
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}.", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = card,
                        onValueChange = { onCardChange(index, it) },
                        label = { Text("Paso ${index + 1}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedButton(onClick = { onRemoveCard(index) }, enabled = cards.size > 2) { Text("−") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val picture = pictures.getOrNull(index).orEmpty()
                    if (picture.startsWith("content://") || picture.startsWith("file://")) {
                        AndroidView(
                            factory = { ctx -> ImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                layoutParams = ViewGroup.LayoutParams(120, 120)
                            } },
                            update = { view ->
                                try { view.setImageURI(Uri.parse(picture)) }
                                catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) }
                            },
                            modifier = Modifier.width(88.dp).height(88.dp).clipToBounds()
                        )
                    } else {
                        Box(
                            Modifier.width(88.dp).height(88.dp)
                                .background(Color(0xFFF2F4F7), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (picture.isBlank()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("▧", fontSize = 30.sp, color = Color(0xFF98A2B3))
                                    Text("Sin imagen", fontSize = 10.sp, color = Color(0xFF667085))
                                }
                            } else {
                                Text("Imagen no válida", fontSize = 10.sp, color = Color(0xFF667085))
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Imagen de la tarjeta ${index + 1}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        OutlinedButton(onClick = { onPickGallery(index) }, modifier = Modifier.fillMaxWidth()) { Text("Elegir imagen de la galería") }
                        OutlinedButton(onClick = { onTakePhoto(index) }, modifier = Modifier.fillMaxWidth()) { Text("📷 Hacer foto con la cámara") }
                        if (picture.isNotBlank()) {
                            OutlinedButton(onClick = { onRemovePicture(index) }, modifier = Modifier.fillMaxWidth()) { Text("Quitar imagen") }
                        }
                    }
                }
            }
        }
    }
    item {
        OutlinedButton(onClick = onAddCard, enabled = cards.size < 6, modifier = Modifier.fillMaxWidth()) {
            Text("＋ Añadir tarjeta (${cards.size}/6)")
        }
    }
    item {
        Text("Soluciones alternativas (opcional)", fontWeight = FontWeight.Bold)
        Text("Una alternativa por línea. Escribe los números de las tarjetas en el orden correcto, separados por comas.", fontSize = 13.sp, color = Color(0xFF667085))
        OutlinedTextField(
            value = alternativeDraft,
            onValueChange = onAlternativeDraftChange,
            label = { Text("Ej.: 1,3,4,2") },
            placeholder = { Text("1,3,4,2\n1,2,3,4") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
    }
    item {
        if (message.isNotBlank()) Text(message, color = Color(0xFFB3261E), fontSize = 13.sp)
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Guardar ejercicio") }
    }
    item {
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}
