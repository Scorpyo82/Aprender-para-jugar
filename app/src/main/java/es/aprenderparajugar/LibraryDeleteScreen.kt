package es.aprenderparajugar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun LazyListScope.LibraryDeleteScreen(
    modalityCategories: Map<ContentStorage.Modality, List<Pair<String, List<StoredExercise>>>>,
    selectedItems: Set<String>,
    expandedCategories: Set<String>,
    confirmArmed: Boolean,
    onSelectionChanged: (Set<String>) -> Unit,
    onExpandedCategoriesChanged: (Set<String>) -> Unit,
    onConfirmArmedChanged: (Boolean) -> Unit,
    onDeleteConfirmed: () -> Unit
) {
    val labels = mapOf(
        ContentStorage.Modality.TARJETAS to "Tarjetas",
        ContentStorage.Modality.PALABRAS to "Palabras",
        ContentStorage.Modality.SECUENCIAS to "Secuencias"
    )
    val allExercises = modalityCategories.values.flatten().flatMap { it.second }
    val selectedCategories = selectedItems.filter { it.startsWith("C|") }
    val selectedCategoryCount = selectedCategories.size
    val selectedExerciseCount = allExercises.count { stored ->
        ("C|" + stored.metadata.modality.name + "|" + stored.metadata.category) in selectedCategories ||
            ("E|" + stored.directory.absolutePath) in selectedItems
    }
    val selectedStatsCount = allExercises.count { stored ->
        val path = stored.directory.absolutePath
        ("S|" + path) in selectedItems && ("E|" + path) !in selectedItems &&
            ("C|" + stored.metadata.modality.name + "|" + stored.metadata.category) !in selectedCategories
    }

    item {
        Text("Eliminar contenido", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB3261E))
        Text(
            "Selecciona categorías completas, ejercicios individuales o solamente las estadísticas de cada ejercicio. Las imágenes se eliminan junto con su ejercicio.",
            fontSize = 14.sp,
            color = Color(0xFF667085)
        )
    }
    ContentStorage.Modality.entries.forEach { modality ->
        val categories = modalityCategories[modality].orEmpty()
        val modalityExercises = categories.flatMap { it.second }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(labels[modality] ?: modality.directoryName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = categories.isNotEmpty() && categories.all { pair ->
                            val key = "C|" + modality.name + "|" + pair.first
                            key in selectedItems || (pair.second.isNotEmpty() && pair.second.all { ("E|" + it.directory.absolutePath) in selectedItems })
                        },
                        onCheckedChange = { checked ->
                            val next = selectedItems.toMutableSet()
                            categories.forEach { pair ->
                                val key = "C|" + modality.name + "|" + pair.first
                                if (checked) {
                                    next.add(key)
                                    pair.second.forEach { stored ->
                                        next.remove("E|" + stored.directory.absolutePath)
                                        next.remove("S|" + stored.directory.absolutePath)
                                    }
                                } else {
                                    next.remove(key)
                                    pair.second.forEach { stored ->
                                        next.remove("E|" + stored.directory.absolutePath)
                                        next.remove("S|" + stored.directory.absolutePath)
                                    }
                                }
                            }
                            onSelectionChanged(next)
                        }
                    )
                    Text("Seleccionar toda la biblioteca (" + modalityExercises.size + ")", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        categories.forEach { pair ->
            val category = pair.first
            val exercises = pair.second
            val categoryKey = "C|" + modality.name + "|" + category
            val selectedCount = if (categoryKey in selectedItems) exercises.size else
                exercises.count { ("E|" + it.directory.absolutePath) in selectedItems }
            val expanded = categoryKey in expandedCategories
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = categoryKey in selectedItems || (exercises.isNotEmpty() && selectedCount == exercises.size),
                                onCheckedChange = { checked ->
                                    val next = selectedItems.toMutableSet()
                                    if (checked) {
                                        next.add(categoryKey)
                                        exercises.forEach { stored ->
                                            next.remove("E|" + stored.directory.absolutePath)
                                            next.remove("S|" + stored.directory.absolutePath)
                                        }
                                    } else {
                                        next.remove(categoryKey)
                                        exercises.forEach { stored ->
                                            next.remove("E|" + stored.directory.absolutePath)
                                            next.remove("S|" + stored.directory.absolutePath)
                                        }
                                    }
                                    onSelectionChanged(next)
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(category, fontWeight = FontWeight.Bold)
                                Text(
                                    exercises.size.toString() + " ejercicios" +
                                        if (selectedCount in 1 until exercises.size) " · selección parcial" else "",
                                    fontSize = 12.sp,
                                    color = Color(0xFF667085)
                                )
                            }
                            Text(
                                if (expanded) "Ocultar" else "Ver",
                                color = Color(0xFF6750A4),
                                modifier = Modifier.clickable {
                                    onExpandedCategoriesChanged(
                                        if (expanded) expandedCategories - categoryKey else expandedCategories + categoryKey
                                    )
                                }.padding(8.dp)
                            )
                        }
                        if (expanded) {
                            if (exercises.isEmpty()) {
                                Text("Categoría vacía: selecciónala para eliminarla.", fontSize = 13.sp, color = Color(0xFF667085))
                            }
                            exercises.forEach { stored ->
                                val path = stored.directory.absolutePath
                                val exerciseKey = "E|" + path
                                val statsKey = "S|" + path
                                val exerciseChecked = categoryKey in selectedItems || exerciseKey in selectedItems
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = exerciseChecked,
                                        onCheckedChange = { checked ->
                                            val next = selectedItems.toMutableSet()
                                            next.remove(categoryKey)
                                            next.remove("S|" + path)
                                            if (checked) next.add(exerciseKey) else next.remove(exerciseKey)
                                            onSelectionChanged(next)
                                        }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(stored.metadata.title, fontWeight = FontWeight.Medium)
                                        Text(stored.metadata.difficulty, fontSize = 12.sp, color = Color(0xFF667085))
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 42.dp)
                                ) {
                                    Checkbox(
                                        checked = statsKey in selectedItems,
                                        enabled = !exerciseChecked,
                                        onCheckedChange = { checked ->
                                            val next = selectedItems.toMutableSet()
                                            if (checked) next.add(statsKey) else next.remove(statsKey)
                                            onSelectionChanged(next)
                                        }
                                    )
                                    Text("Eliminar solo las estadísticas", fontSize = 12.sp, color = Color(0xFF667085))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    item {
        Text(
            "Selección: " + selectedCategoryCount + " categorías, " + selectedExerciseCount +
                " ejercicios y " + selectedStatsCount +
                " conjuntos de estadísticas. El PIN, los límites y las preferencias generales se conservarán.",
            fontSize = 13.sp,
            color = Color(0xFF667085)
        )
    }
    item {
        if (!confirmArmed) {
            Button(
                onClick = { onConfirmArmedChanged(true) },
                enabled = selectedItems.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
            ) {
                Text("Continuar con la eliminación")
            }
        } else {
            Text(
                "CONFIRMACIÓN: se eliminarán " + selectedCategoryCount + " categorías, " +
                    selectedExerciseCount + " ejercicios y " + selectedStatsCount +
                    " conjuntos de estadísticas. Esta acción no se puede deshacer.",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB3261E)
            )
            Button(
                onClick = onDeleteConfirmed,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
            ) {
                Text("Sí, eliminar definitivamente")
            }
            OutlinedButton(
                onClick = { onConfirmArmedChanged(false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancelar y conservar los datos")
            }
        }
    }
}
