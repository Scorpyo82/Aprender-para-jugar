package es.aprenderparajugar

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun LazyListScope.libraryExportContent(
    context: Context,
    transferExportCategoriesState: MutableState<Map<ContentStorage.Modality, Set<String>>>,
    transferExportStatisticsState: MutableState<Set<ContentStorage.Modality>>,
    transferExportUsageStatisticsState: MutableState<Set<ContentStorage.Modality>>,
    transferExportStoriesState: MutableState<Set<String>>,
    transferExportObjectsState: MutableState<Set<String>>,
    expandedExportSequenceCategoriesState: MutableState<Set<String>>,
    transferMessage: String,
    onTransferMessageChange: (String) -> Unit,
    onExportSelection: () -> Unit
) {
                            val options = ContentStorage.Modality.entries.associateWith { LibraryPackageManager.categories(context, it) }
                            item { Text("Exportar bibliotecas", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            item { Text("Selecciona las categorías, cuentos y estadísticas que quieres incluir en un único ZIP.", fontSize = 14.sp, color = Color(0xFF667085)) }
                            val availableStories = StoryRepository.list(context)
                            item { Text("Cuentos", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                            if (availableStories.isEmpty()) {
                                item { Text("No hay cuentos guardados para exportar.", fontSize = 13.sp, color = Color(0xFF667085)) }
                            } else {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = availableStories.all { "${it.collection}/${it.folderName}" in transferExportStoriesState.value },
                                            onCheckedChange = { checked ->
                                                transferExportStoriesState.value = if (checked) availableStories.map { "${it.collection}/${it.folderName}" }.toSet() else emptySet()
                                            }
                                        )
                                        Text("Seleccionar todos los cuentos")
                                    }
                                }
                                availableStories.groupBy { it.collection }.toSortedMap().forEach { (collection, stories) ->
                                    item { Text(collection, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475467)) }
                                    stories.forEach { story ->
                                        val key = "${story.collection}/${story.folderName}"
                                        item {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = key in transferExportStoriesState.value,
                                                    onCheckedChange = { checked ->
                                                        transferExportStoriesState.value = transferExportStoriesState.value.toMutableSet().also {
                                                            if (checked) it.add(key) else it.remove(key)
                                                        }
                                                    }
                                                )
                                                Column {
                                                    Text(story.metadata.title)
                                                    Text("${story.metadata.scenes.size} escenas", fontSize = 12.sp, color = Color(0xFF667085))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            item { androidx.compose.material3.HorizontalDivider() }
                            val availableObjectsForExport = ObjectRepository.list(context)
                            val requiredExportObjects = LibraryPackageManager.requiredExportObjects(context, transferExportStoriesState.value)
                            item {
                                Text("Objetos y coleccionables", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Los objetos requeridos por los cuentos seleccionados se incluirán automáticamente. También puedes seleccionar objetos por separado.", fontSize = 12.sp, color = Color(0xFF667085))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = availableObjectsForExport.isNotEmpty() && availableObjectsForExport.all {
                                            val key = "${it.collection}/${it.folderName}"
                                            key in requiredExportObjects || key in transferExportObjectsState.value
                                        },
                                        onCheckedChange = { checked ->
                                            transferExportObjectsState.value = if (checked) availableObjectsForExport
                                                .map { "${it.collection}/${it.folderName}" }.toSet() else emptySet()
                                        }
                                    )
                                    Text("Seleccionar todos los objetos")
                                }
                                availableObjectsForExport.groupBy { it.collection }.toSortedMap().forEach { (collection, objects) ->
                                    Text(collection, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    objects.forEach { stored ->
                                        val key = "${stored.collection}/${stored.folderName}"
                                        val requiredBy = requiredExportObjects[key].orEmpty()
                                        val isRequired = requiredBy.isNotEmpty()
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = isRequired || key in transferExportObjectsState.value,
                                                enabled = !isRequired,
                                                onCheckedChange = { checked ->
                                                    transferExportObjectsState.value = transferExportObjectsState.value.toMutableSet().also { if (checked) it.add(key) else it.remove(key) }
                                                }
                                            )
                                            Column {
                                                Text(stored.metadata.name)
                                                Text(
                                                    if (isRequired) "REQUERIDO" else if (stored.metadata.collectible) "🏆 COLECCIONABLE" else "📦 OBJETO NORMAL",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isRequired) Color(0xFF16803C) else if (stored.metadata.collectible) Color(0xFF7952A5) else Color(0xFF356B91)
                                                )
                                                if (isRequired) Text("Requerido por: ${requiredBy.joinToString(", ")}", fontSize = 11.sp, color = Color(0xFF667085))
                                            }
                                        }
                                    }
                                }
                                if (availableObjectsForExport.isEmpty()) Text("No hay objetos guardados para exportar.", fontSize = 12.sp, color = Color(0xFF667085))
                            }
                            item { androidx.compose.material3.HorizontalDivider() }
                            ContentStorage.Modality.entries.forEach { modality ->
                                val label = when (modality) { ContentStorage.Modality.TARJETAS -> "Tarjetas"; ContentStorage.Modality.PALABRAS -> "Palabras"; ContentStorage.Modality.SECUENCIAS -> "Secuencias" }
                                val categoriesForModality = options[modality].orEmpty()
                                item {
                                    Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    if (categoriesForModality.isEmpty()) {
                                        Text("No hay categorías con ejercicios guardados.", fontSize = 13.sp, color = Color(0xFF667085))
                                    } else {
                                        if (modality == ContentStorage.Modality.SECUENCIAS) {
                                            val selected = transferExportCategoriesState.value[modality].orEmpty()
                                            SequenceType.entries.forEach { type ->
                                                val exercisesOfType = categoriesForModality.flatMap { category ->
                                                    category.sequenceExercises.filter { it.type == type }.map { exercise ->
                                                        LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, type, exercise.directory)
                                                    }
                                                }
                                                if (exercisesOfType.isNotEmpty()) {
                                                    val typeIsFullySelected = categoriesForModality
                                                        .filter { category -> category.sequenceExercises.any { it.type == type } }
                                                        .all { category ->
                                                            category.directory in selected ||
                                                                LibraryPackageManager.sequenceSelectionKey(category.directory, type) in selected ||
                                                                category.sequenceExercises.filter { it.type == type }.all { exercise ->
                                                                    LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, type, exercise.directory) in selected
                                                                }
                                                        }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = typeIsFullySelected,
                                                            onCheckedChange = { checked ->
                                                                val updated = selected.toMutableSet()
                                                                categoriesForModality.forEach { category ->
                                                                    if (category.directory in updated) {
                                                                        updated.remove(category.directory)
                                                                        category.sequenceExercises.forEach { exercise ->
                                                                            updated.add(LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, exercise.type, exercise.directory))
                                                                        }
                                                                    }
                                                                    val typeKey = LibraryPackageManager.sequenceSelectionKey(category.directory, type)
                                                                    if (typeKey in updated) {
                                                                        updated.remove(typeKey)
                                                                        category.sequenceExercises.filter { it.type == type }.forEach { exercise ->
                                                                            updated.add(LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, type, exercise.directory))
                                                                        }
                                                                    }
                                                                    if (category.sequenceExercises.any { it.type == type }) {
                                                                        val keys = category.sequenceExercises.filter { it.type == type }.map { exercise ->
                                                                            LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, type, exercise.directory)
                                                                        }
                                                                        if (checked) updated.addAll(keys) else updated.removeAll(keys.toSet())
                                                                    }
                                                                }
                                                                transferExportCategoriesState.value = transferExportCategoriesState.value.toMutableMap().also { it[modality] = updated }
                                                            }
                                                        )
                                                        Text(type.displayName, fontWeight = FontWeight.Bold)
                                                    }
                                                    categoriesForModality.forEach { category ->
                                                        val exercises = category.sequenceExercises.filter { it.type == type }
                                                        if (exercises.isNotEmpty()) {
                                                            val categoryKey = "${type.jsonValue}::${category.directory}"
                                                            val exerciseKeys = exercises.map { exercise ->
                                                                LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, type, exercise.directory)
                                                            }
                                                            val typeKey = LibraryPackageManager.sequenceSelectionKey(category.directory, type)
                                                            val categoryIsFullySelected = category.directory in selected || typeKey in selected || exerciseKeys.all { it in selected }
                                                            val expanded = categoryKey in expandedExportSequenceCategoriesState.value
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().clickable {
                                                                    expandedExportSequenceCategoriesState.value = expandedExportSequenceCategoriesState.value.toMutableSet().also {
                                                                        if (expanded) it.remove(categoryKey) else it.add(categoryKey)
                                                                    }
                                                                }.padding(start = 28.dp, top = 4.dp, bottom = 4.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Checkbox(
                                                                    checked = categoryIsFullySelected,
                                                                    onCheckedChange = { checked ->
                                                                        val updated = selected.toMutableSet()
                                                                        if (category.directory in updated) {
                                                                            updated.remove(category.directory)
                                                                            category.sequenceExercises.forEach { exercise ->
                                                                                updated.add(LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, exercise.type, exercise.directory))
                                                                            }
                                                                        }
                                                                        if (typeKey in updated) {
                                                                            updated.remove(typeKey)
                                                                            exercises.forEach { exercise ->
                                                                                updated.add(LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, type, exercise.directory))
                                                                            }
                                                                        }
                                                                        if (checked) updated.addAll(exerciseKeys) else updated.removeAll(exerciseKeys.toSet())
                                                                        transferExportCategoriesState.value = transferExportCategoriesState.value.toMutableMap().also { it[modality] = updated }
                                                                    }
                                                                )
                                                                Text(if (expanded) "▾" else "▸", modifier = Modifier.padding(end = 8.dp), color = Color(0xFF667085))
                                                                Text(category.name, fontWeight = FontWeight.SemiBold, color = Color(0xFF475467))
                                                                Spacer(Modifier.weight(1f))
                                                                Text("${exercises.size}", fontSize = 12.sp, color = Color(0xFF667085))
                                                            }
                                                            if (expanded) exercises.forEach { exercise ->
                                                                val key = LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, type, exercise.directory)
                                                                val inheritedSelection = category.directory in selected || typeKey in selected
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth().padding(start = 68.dp, top = 2.dp, bottom = 2.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Checkbox(
                                                                        checked = inheritedSelection || key in selected,
                                                                        onCheckedChange = { checked ->
                                                                            val updated = selected.toMutableSet()
                                                                            if (category.directory in updated) {
                                                                                updated.remove(category.directory)
                                                                                category.sequenceExercises.forEach { sibling ->
                                                                                    updated.add(LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, sibling.type, sibling.directory))
                                                                                }
                                                                            }
                                                                            if (typeKey in updated) {
                                                                                updated.remove(typeKey)
                                                                                exercises.forEach { sibling ->
                                                                                    updated.add(LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, type, sibling.directory))
                                                                                }
                                                                            }
                                                                            if (checked) updated.add(key) else updated.remove(key)
                                                                            transferExportCategoriesState.value = transferExportCategoriesState.value.toMutableMap().also { it[modality] = updated }
                                                                        }
                                                                    )
                                                                    Text(exercise.title)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = modality in transferExportStatisticsState.value,
                                                onCheckedChange = { checked -> transferExportStatisticsState.value = transferExportStatisticsState.value.toMutableSet().also { if (checked) it.add(modality) else it.remove(modality) } }
                                            )
                                            Text("Incluir estadísticas de aprendizaje")
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = modality in transferExportUsageStatisticsState.value,
                                                onCheckedChange = { checked -> transferExportUsageStatisticsState.value = transferExportUsageStatisticsState.value.toMutableSet().also { if (checked) it.add(modality) else it.remove(modality) } }
                                            )
                                            Text("Incluir estadísticas de uso")
                                        }
                                    }
                                }
                                item { androidx.compose.material3.HorizontalDivider() }
                            }
                            item {
                                Button(
                                    onClick = {
                                        val selected = transferExportCategoriesState.value.filterValues { it.isNotEmpty() }
                                        if (selected.isEmpty() && transferExportStoriesState.value.isEmpty() && transferExportObjectsState.value.isEmpty()) onTransferMessageChange("Selecciona al menos una categoría, cuento u objeto.")
                                        else {
                                            onExportSelection()
                                        }
                                    },
                                    enabled = transferExportCategoriesState.value.values.any { it.isNotEmpty() } || transferExportStoriesState.value.isNotEmpty() || transferExportObjectsState.value.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Exportar selección") }
                            }
                            if (transferMessage.isNotBlank()) item { Text(transferMessage, color = if (transferMessage.startsWith("No se puede") || transferMessage.startsWith("No se pudo")) Color(0xFFB3261E) else Color(0xFF24733B), fontSize = 14.sp) }
}
