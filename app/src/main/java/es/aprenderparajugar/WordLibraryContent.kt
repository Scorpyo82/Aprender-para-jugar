package es.aprenderparajugar

import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.wordLibraryItems(
    state: WordLibraryState,
    section: String,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit
) {
        var wordExercises by state.exercises
        var wordExerciseCategories by state.categories
        var disabledWordExerciseCategories by state.disabledCategories
        var wordLibraryPage by state.page
        var wordLibraryEditingIndex by state.editingIndex
        var wordLibraryWordDraft by state.wordDraft
        var wordLibraryPictureDraft by state.pictureDraft
        var wordLibraryCategoryDraft by state.categoryDraft
        var wordLibraryDifficultyDraft by state.difficultyDraft
        var wordLibraryMessage by state.message
        var wordLibraryCategoryMenuExpanded by state.categoryMenuExpanded
        var wordFilterCategory by state.filterCategory
        var wordFilterDifficulty by state.filterDifficulty
        var wordFilterStatus by state.filterStatus
        var wordNewCategoryDraft by state.newCategoryDraft
        var wordCategoryToDelete by state.categoryToDelete
        if (section == "word-library") {
            item { Text(if (wordLibraryPage == "list") "Ejercicios de Palabras" else if (wordLibraryEditingIndex < 0) "Nuevo ejercicio de Palabras" else "Editar ejercicio de Palabras", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            if (wordLibraryPage == "list") {
                item { Text("Esta biblioteca es independiente de Parejas y Secuencias.", fontSize = 14.sp, color = Color(0xFF667085)) }
                item { Button(onClick = { wordLibraryEditingIndex = -1; wordLibraryWordDraft = ""; wordLibraryPictureDraft = ""; wordLibraryCategoryDraft = wordExerciseCategories.firstOrNull() ?: ""; wordLibraryDifficultyDraft = "Fácil"; wordLibraryMessage = ""; wordLibraryPage = "edit" }, modifier = Modifier.fillMaxWidth()) { Text("＋ Añadir ejercicio") } }
                if (wordExercises.isEmpty()) item { Text("Todavía no hay ejercicios de Palabras. Añade uno con su palabra, imagen y categoría para probarlo.", color = Color(0xFF667085)) }
                item {
                    Text("Filtrar ejercicios", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Categoría", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (listOf("Todas") + wordExerciseCategories).forEach { category ->
                            FilterChip(selected = wordFilterCategory == category, onClick = { wordFilterCategory = category }, label = { Text(category) })
                        }
                    }
                    Text("Dificultad", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Todas", "Fácil", "Normal", "Difícil").forEach { level -> FilterChip(selected = wordFilterDifficulty == level, onClick = { wordFilterDifficulty = level }, label = { Text(level) }) }
                    }
                    Text("Estado", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Todos", "Activos", "Inactivos").forEach { status -> FilterChip(selected = wordFilterStatus == status, onClick = { wordFilterStatus = status }, label = { Text(status) }) }
                    }
                }
                val visibleWordExercises = wordExercises.withIndex().filter { (_, exercise) ->
                    (wordFilterCategory == "Todas" || exercise.category == wordFilterCategory) &&
                    (wordFilterDifficulty == "Todas" || exercise.difficulty == wordFilterDifficulty) &&
                    (wordFilterStatus == "Todos" || if (wordFilterStatus == "Activos") exercise.active else !exercise.active)
                }
                if (visibleWordExercises.isEmpty() && wordExercises.isNotEmpty()) item { Text("No hay ejercicios que coincidan con estos filtros.", color = Color(0xFF667085)) }
                visibleWordExercises.forEach { indexed ->
                    val index = indexed.index
                    val exercise = indexed.value
                    item {
                        var pendingWordDeletion by remember { mutableStateOf(false) }
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(exercise.word, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (exercise.picture.startsWith("content://") || exercise.picture.startsWith("file://")) {
                                        AndroidView(
                                            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; layoutParams = ViewGroup.LayoutParams(112, 112) } },
                                            update = { view -> try { view.setImageURI(Uri.parse(exercise.picture)) } catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) } },
                                            modifier = Modifier.width(72.dp).height(72.dp).clipToBounds()
                                        )
                                    } else {
                                        Box(Modifier.width(72.dp).height(72.dp).background(Color(0xFFF2F4F7), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                            Text("Sin imagen", fontSize = 11.sp, color = Color(0xFF667085))
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("${exercise.category} · ${exercise.difficulty}", color = Color(0xFF667085))
                                        Text(if (exercise.active) "Activo" else "Inactivo", color = if (exercise.active) Color(0xFF24733B) else Color(0xFF667085), fontSize = 12.sp)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { wordLibraryEditingIndex = index; wordLibraryWordDraft = exercise.word; wordLibraryPictureDraft = exercise.picture; wordLibraryCategoryDraft = exercise.category; wordLibraryDifficultyDraft = exercise.difficulty; wordLibraryMessage = ""; wordLibraryPage = "edit" }, modifier = Modifier.weight(1f)) { Text("Editar") }
                                    OutlinedButton(onClick = { wordExercises = wordExercises.toMutableList().also { it[index] = exercise.copy(active = !exercise.active) }; wordExercises = state.saveExercises(wordExercises) }, modifier = Modifier.weight(1f)) { Text(if (exercise.active) "Desactivar" else "Activar") }
                                    OutlinedButton(onClick = { pendingWordDeletion = true }, modifier = Modifier.weight(1f)) { Text("Eliminar") }
                                }
                            }

                        if (pendingWordDeletion) {
                            AlertDialog(
                                onDismissRequest = { pendingWordDeletion = false },
                                title = { Text("Confirmar eliminación") },
                                text = { Text("¿Quieres eliminar el ejercicio «" + exercise.word + "» de la biblioteca? Esta acción no se puede deshacer.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        wordExercises = state.saveExercises(wordExercises.filterIndexed { i, _ -> i != index })
                                        pendingWordDeletion = false
                                    }) { Text("Eliminar definitivamente") }
                                },
                                dismissButton = { TextButton(onClick = { pendingWordDeletion = false }) { Text("Cancelar") } }
                            )
                        }
                        }
                    }
                }
            } else {
                item { OutlinedTextField(value = wordLibraryWordDraft, onValueChange = { wordLibraryWordDraft = if (wordLibraryEditingIndex < 0) NameRules.filterInput(it) else it }, label = { Text("Palabra que formará") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Text("Imagen de pista", fontWeight = FontWeight.Bold)
                    Text("Selecciona una imagen de la galería o haz una foto con la cámara.", fontSize = 13.sp, color = Color(0xFF667085))
                    if (wordLibraryPictureDraft.startsWith("content://") || wordLibraryPictureDraft.startsWith("file://")) {
                        AndroidView(
                            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 220) } },
                            update = { view -> try { view.setImageURI(Uri.parse(wordLibraryPictureDraft)) } catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) } },
                            modifier = Modifier.fillMaxWidth().height(180.dp)
                        )
                    } else {
                        Text("Todavía no hay ninguna imagen seleccionada.", color = Color(0xFF667085))
                    }
                    OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) { Text("🖼️ Elegir imagen de la galería") }
                    OutlinedButton(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) { Text("📷 Hacer foto con la cámara") }
                    if (wordLibraryPictureDraft.isNotBlank()) {
                        OutlinedButton(onClick = { wordLibraryPictureDraft = ""; wordLibraryMessage = "" }, modifier = Modifier.fillMaxWidth()) { Text("Quitar imagen") }
                    }
                }
                item {
                    Text("Categoría", fontWeight = FontWeight.Bold)
                    androidx.compose.foundation.layout.Box {
                        OutlinedButton(onClick = { wordLibraryCategoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(if (wordLibraryCategoryDraft.isBlank()) "Seleccionar categoría" else wordLibraryCategoryDraft) }
                        DropdownMenu(expanded = wordLibraryCategoryMenuExpanded, onDismissRequest = { wordLibraryCategoryMenuExpanded = false }) {
                            wordExerciseCategories.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { wordLibraryCategoryDraft = name; wordLibraryCategoryMenuExpanded = false }) }
                        }
                    }
                }
                item {
                    Text("Dificultad", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Fácil", "Normal", "Difícil").forEach { level -> FilterChip(selected = wordLibraryDifficultyDraft == level, onClick = { wordLibraryDifficultyDraft = level }, label = { Text(level) }) }
                    }
                }
                item {
                    if (wordLibraryMessage.isNotBlank()) Text(wordLibraryMessage, color = Color(0xFFB3261E))
                    Button(onClick = {
                        val cleanWord = wordLibraryWordDraft.trim()
                        when {
                            cleanWord.isBlank() -> wordLibraryMessage = "Escribe una palabra."
                            wordLibraryPictureDraft.isBlank() -> wordLibraryMessage = "Selecciona una imagen de pista."
                            wordLibraryCategoryDraft.isBlank() || wordLibraryCategoryDraft !in wordExerciseCategories -> wordLibraryMessage = "Selecciona una categoría creada en el menú Categorías."
                            wordExercises.withIndex().any { (i, e) -> i != wordLibraryEditingIndex && e.word.equals(cleanWord, true) } -> wordLibraryMessage = "Ya existe un ejercicio con esa palabra."
                            else -> {
                                val entry = wordExercises.getOrNull(wordLibraryEditingIndex)?.copy(word = cleanWord, picture = wordLibraryPictureDraft.trim(), category = wordLibraryCategoryDraft, difficulty = wordLibraryDifficultyDraft, active = true) ?: PairItem(cleanWord, wordLibraryPictureDraft.trim(), wordLibraryCategoryDraft, wordLibraryDifficultyDraft, true)
                                wordExercises = if (wordLibraryEditingIndex < 0) wordExercises + entry else wordExercises.toMutableList().also { it[wordLibraryEditingIndex] = entry }
                                wordExercises = state.saveExercises(wordExercises)
                                wordLibraryPage = "list"; wordLibraryMessage = ""
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Guardar ejercicio") }
                }
                item { OutlinedButton(onClick = { wordLibraryPage = "list"; wordLibraryMessage = "" }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") } }
            }
        } else {
            item { Text("Categorías de Palabras", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            item { Text("Estas categorías solo afectan a los ejercicios de Palabras; no modifican Tarjetas ni Secuencias.", fontSize = 14.sp, color = Color(0xFF667085)) }
            item { OutlinedTextField(value = wordNewCategoryDraft, onValueChange = { wordNewCategoryDraft = NameRules.filterInput(it); wordLibraryMessage = "" }, label = { Text("Nueva categoría") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { Button(onClick = {
                val name = wordNewCategoryDraft.trim()
                when {
                    name.isBlank() -> wordLibraryMessage = "Escribe un nombre."
                    wordExerciseCategories.any { it.equals(name, true) } -> wordLibraryMessage = "Esa categoría ya existe."
                    else -> { state.createCategory(name); wordLibraryMessage = "Categoría creada." }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("＋ Crear categoría") } }
            if (wordLibraryMessage.isNotBlank()) item { Text(wordLibraryMessage, color = Color(0xFF24733B)) }
            wordExerciseCategories.forEach { category ->
                item {
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(category, fontWeight = FontWeight.Bold); Text("${wordExercises.count { it.category == category }} ejercicios", color = Color(0xFF667085)) }
                                Switch(checked = category !in disabledWordExerciseCategories, onCheckedChange = { enabled ->
                                    state.setCategoryEnabled(category, enabled)
                                })
                            }
                            if (wordCategoryToDelete == category) {
                                Text("Borrar categoría y contenido", fontWeight = FontWeight.Bold)
                                Text("Se eliminarán también los ${wordExercises.count { it.category == category }} ejercicios asignados.", color = Color(0xFFB3261E))
                                Button(onClick = { state.deleteCategoryAndExercises(category) }, modifier = Modifier.fillMaxWidth()) { Text("Borrar categoría y contenido") }
                                Text("Borrar categoría pero mover contenido a:", fontWeight = FontWeight.Bold)
                                wordExerciseCategories.filterNot { it == category }.forEach { destination ->
                                    OutlinedButton(onClick = { state.reassignCategory(category, destination) }, modifier = Modifier.fillMaxWidth()) { Text(destination) }
                                }
                                OutlinedButton(onClick = { wordCategoryToDelete = null }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                            } else {
                                OutlinedButton(onClick = { wordCategoryToDelete = category }, modifier = Modifier.fillMaxWidth()) { Text("Eliminar / reasignar") }
                            }
                        }
                    }
                }
            }
}
}
