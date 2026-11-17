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
    stories: List<StoredStory>,
    objects: List<StoredObject>,
    dependencySnapshot: StoryObjectDependencySnapshot?,
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
    val selectedStories = stories.filter { "T|" + it.directory.absolutePath in selectedItems }
    val selectedStoryCount = selectedStories.size
    val selectedObjectCount = selectedItems.count { it.startsWith("O|") }

    item {
        androidx.compose.material3.HorizontalDivider()
        Text("Cuentos", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Selecciona un cuento para ver los objetos que utiliza. Los objetos compartidos por varios cuentos no se podrán borrar desde aquí.",
            fontSize = 13.sp,
            color = Color(0xFF667085)
        )
        if (dependencySnapshot == null) {
            Text("Comprobando dependencias de cuentos y objetos…", fontSize = 13.sp, color = Color(0xFF667085))
        } else if (dependencySnapshot.scanErrors.isNotEmpty()) {
            Text(
                "No se han podido comprobar todas las dependencias. Por seguridad, no se permitirá borrar ningún objeto. " +
                    dependencySnapshot.scanErrors.take(3).joinToString(" "),
                fontSize = 12.sp,
                color = Color(0xFFB3261E)
            )
        }
    }
    stories.forEach { story ->
        val storyPath = story.directory.canonicalPath
        val storyKey = "T|" + story.directory.absolutePath
        val storySelected = storyKey in selectedItems
        val objectFolders = dependencySnapshot?.objectFoldersByStory?.get(storyPath).orEmpty()
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8F2))
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = storySelected,
                            onCheckedChange = { checked ->
                                val next = selectedItems.toMutableSet()
                                if (checked) next.add(storyKey) else {
                                    next.remove(storyKey)
                                    objectFolders.forEach { next.remove("O|" + it) }
                                }
                                onSelectionChanged(next)
                            }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(story.metadata.title, fontWeight = FontWeight.Bold)
                            Text(story.collection.replace('_', ' '), fontSize = 12.sp, color = Color(0xFF667085))
                        }
                        Text(if (storySelected) "Seleccionado" else "Seleccionar", fontSize = 12.sp, color = Color(0xFF6750A4))
                    }
                    if (storySelected) {
                        Text("Objetos asociados a este cuento", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        if (dependencySnapshot == null) {
                            Text("Aún no se han podido verificar los objetos.", fontSize = 12.sp, color = Color(0xFFB3261E))
                        } else if (objectFolders.isEmpty()) {
                            Text("Este cuento no tiene objetos asociados.", fontSize = 12.sp, color = Color(0xFF667085))
                        } else {
                            objectFolders.forEach { folder ->
                                val storedObject = objects.firstOrNull { it.folderName == folder }
                                val dependentStories = dependencySnapshot.storyFoldersByObject[folder].orEmpty()
                                val dependentStoryTitles = dependentStories.mapNotNull { dependentPath ->
                                    stories.firstOrNull { it.directory.canonicalPath == dependentPath }?.metadata?.title
                                }
                                val canDeleteObject = dependencySnapshot.scanErrors.isEmpty() &&
                                    dependentStories.size == 1 && storyPath in dependentStories &&
                                    storedObject != null
                                val objectKey = "O|" + folder
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = objectKey in selectedItems,
                                        enabled = canDeleteObject,
                                        onCheckedChange = { checked ->
                                            val next = selectedItems.toMutableSet()
                                            if (checked) next.add(objectKey) else next.remove(objectKey)
                                            onSelectionChanged(next)
                                        }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(storedObject?.metadata?.name ?: folder, fontWeight = FontWeight.Medium)
                                        Text(
                                            when {
                                                dependencySnapshot.scanErrors.isNotEmpty() -> "No se puede verificar su uso"
                                                storedObject == null -> "El objeto ya no está disponible en la biblioteca"
                                                dependentStories.size > 1 -> "Compartido por: ${dependentStoryTitles.joinToString(", ")}. No se puede borrar"
                                                canDeleteObject -> "Solo pertenece a este cuento; borrado opcional"
                                                else -> "No se puede verificar la dependencia"
                                            },
                                            fontSize = 11.sp,
                                            color = if (canDeleteObject) Color(0xFF24733B) else Color(0xFFB3261E)
                                        )
                                    }
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
                " conjuntos de estadísticas, " + selectedStoryCount + " cuentos y " + selectedObjectCount +
                " objetos. El PIN, los límites y las preferencias generales se conservarán.",
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
                    selectedExerciseCount + " ejercicios, " + selectedStatsCount +
                    " conjuntos de estadísticas, " + selectedStoryCount + " cuentos y " +
                    selectedObjectCount + " objetos. Esta acción no se puede deshacer.",
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
