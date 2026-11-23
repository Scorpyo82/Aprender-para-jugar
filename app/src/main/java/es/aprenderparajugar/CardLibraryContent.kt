package es.aprenderparajugar

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

internal data class CardLibraryState(
    val context: Context,
    val preferences: SharedPreferences,
    val pairs: MutableState<List<PairItem>>,
    val wordDraft: MutableState<String>,
    val pictureDraft: MutableState<String>,
    val categoryDraft: MutableState<String>,
    val difficultyDraft: MutableState<String>,
    val editingWord: MutableState<String?>,
    val libraryMessage: MutableState<String>,
    val libraryPage: MutableState<String>,
    val categoryDraftName: MutableState<String>,
    val categoryNames: MutableState<List<String>>,
    val categoryMessage: MutableState<String>,
    val categoryToRemove: MutableState<String>,
    val categoryMenuExpanded: MutableState<Boolean>,
    val libraryCategoryFilter: MutableState<String>,
    val libraryDifficultyFilter: MutableState<String>,
    val libraryFilterCategoryExpanded: MutableState<Boolean>
)

internal fun LazyListScope.cardLibraryItems(
    state: CardLibraryState,
    section: String,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit
) {
    val context = state.context
    val preferences = state.preferences
    var pairs by state.pairs
    var wordDraft by state.wordDraft
    var pictureDraft by state.pictureDraft
    var categoryDraft by state.categoryDraft
    var difficultyDraft by state.difficultyDraft
    var editingWord by state.editingWord
    var libraryMessage by state.libraryMessage
    var libraryPage by state.libraryPage
    var categoryDraftName by state.categoryDraftName
    var categoryNames by state.categoryNames
    var categoryMessage by state.categoryMessage
    var categoryToRemove by state.categoryToRemove
    var categoryMenuExpanded by state.categoryMenuExpanded
    var libraryCategoryFilter by state.libraryCategoryFilter
    var libraryDifficultyFilter by state.libraryDifficultyFilter
    var libraryFilterCategoryExpanded by state.libraryFilterCategoryExpanded
    val categories = (categoryNames + pairs.map { it.category }).distinctBy { it.trim().lowercase() }.sorted()
    when (section) {
            "words" -> item {
                        if (libraryPage == "list") {
                            Text("Biblioteca de tarjetas", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Gestiona las tarjetas guardadas. Pulsa Añadir para crear una nueva.", fontSize = 14.sp, color = Color(0xFF667085))
                            Button(onClick = { editingWord = null; wordDraft = ""; pictureDraft = ""; categoryDraft = "Primeras palabras"; difficultyDraft = "Fácil"; libraryMessage = ""; libraryPage = "edit" }, modifier = Modifier.fillMaxWidth()) { Text("＋ Añadir tarjeta") }
                            Spacer(Modifier.height(8.dp))

                            Text("Filtrar contenido", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            androidx.compose.foundation.layout.Box {
                                OutlinedButton(onClick = { libraryFilterCategoryExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("🗂️ Categoría: $libraryCategoryFilter  ▾")
                                }
                                DropdownMenu(expanded = libraryFilterCategoryExpanded, onDismissRequest = { libraryFilterCategoryExpanded = false }) {
                                    (listOf("Todas") + categories).forEach { name ->
                                        DropdownMenuItem(text = { Text(name) }, onClick = { libraryCategoryFilter = name; libraryFilterCategoryExpanded = false })
                                    }
                                }
                            }
                            Text("Dificultad", fontWeight = FontWeight.SemiBold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Todas", "Fácil", "Normal", "Difícil").forEach { level ->
                                    val selected = libraryDifficultyFilter == level
                                    OutlinedButton(
                                        onClick = { libraryDifficultyFilter = level },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (selected) Color(0xFFEBDDF8) else Color.Transparent
                                        )
                                    ) {
                                        Text(level, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                            val filteredLibraryPairs = pairs.filter { pair ->
                                (libraryCategoryFilter == "Todas" || pair.category.equals(libraryCategoryFilter, ignoreCase = true)) &&
                                    (libraryDifficultyFilter == "Todas" || pair.difficulty.equals(libraryDifficultyFilter, ignoreCase = true))
                            }
                            Text("Mostrando ${filteredLibraryPairs.size} de ${pairs.size} palabras", fontSize = 13.sp, color = Color(0xFF667085))
                            if (filteredLibraryPairs.isEmpty()) {
                                Text("No hay palabras que coincidan con estos filtros.", color = Color(0xFF667085))
                            }
                            filteredLibraryPairs.forEach { pair ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            if (pair.picture.startsWith("content://") || pair.picture.startsWith("file://")) {
                                                AndroidView(factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } }, update = { view -> try { view.setImageURI(Uri.parse(pair.picture)) } catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) } }, modifier = Modifier.width(64.dp).height(64.dp).clipToBounds())
                                            } else Text(pair.picture, fontSize = 28.sp)
                                            Column(Modifier.weight(1f)) { Text(pair.word, fontSize = 19.sp, fontWeight = FontWeight.SemiBold); Text("${pair.category} · ${pair.difficulty} · ${if (pair.active) "Activa" else "Inactiva"}", color = Color(0xFF667085)) }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(onClick = { editingWord = pair.word; wordDraft = pair.word; pictureDraft = pair.picture; categoryDraft = pair.category; difficultyDraft = pair.difficulty; libraryMessage = ""; libraryPage = "edit" }, modifier = Modifier.weight(1f)) { Text("Editar", maxLines = 1, fontSize = 13.sp) }
                                            OutlinedButton(onClick = { pairs = pairs.map { if (it.word == pair.word) it.copy(active = !it.active) else it }; pairs = ExerciseUiStorage.savePairs(context, ContentStorage.Modality.TARJETAS, pairs) }, modifier = Modifier.weight(1.2f)) { Text(if (pair.active) "Desactivar" else "Activar", maxLines = 1, fontSize = 13.sp) }
                                            OutlinedButton(onClick = { pairs = pairs.filterNot { it.word == pair.word }; pairs = ExerciseUiStorage.savePairs(context, ContentStorage.Modality.TARJETAS, pairs) }, modifier = Modifier.weight(1f)) { Text("Eliminar", maxLines = 1, fontSize = 13.sp) }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(if (editingWord == null) "Añadir tarjeta" else "Editar tarjeta", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Completa los datos de la tarjeta y guarda. La imagen se elige desde la galería.", fontSize = 14.sp, color = Color(0xFF667085))
                            OutlinedTextField(value = wordDraft, onValueChange = { wordDraft = if (editingWord == null) NameRules.filterInput(it) else it }, label = { Text("Palabra") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Column {
                                Text("Categoría", fontWeight = FontWeight.SemiBold)
                                androidx.compose.foundation.layout.Box {
                                    OutlinedButton(onClick = { categoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text(categoryDraft.ifBlank { "Seleccionar categoría" })
                                    }
                                    DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                                        categories.forEach { name ->
                                            DropdownMenuItem(text = { Text(name) }, onClick = { categoryDraft = name; categoryMenuExpanded = false })
                                        }
                                    }
                                }
                            }
                            Text("Dificultad", fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("Fácil", "Normal", "Difícil").forEach { level -> OutlinedButton(onClick = { difficultyDraft = level }) { Text(if (difficultyDraft == level) "✓ $level" else level) } } }
                            if (pictureDraft.startsWith("content://") || pictureDraft.startsWith("file://")) {
                                AndroidView(factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 320) } }, update = { view -> try { view.setImageURI(Uri.parse(pictureDraft)) } catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) } }, modifier = Modifier.fillMaxWidth().height(180.dp))
                            } else Text(if (pictureDraft.isBlank()) "Todavía no has elegido una imagen." else "Dibujo actual: $pictureDraft", fontSize = 18.sp)
                            OutlinedButton(onClick = {
                                // Keep the parental-area guard armed while Android's
                                // system document picker is temporarily in the foreground.
                                onPickImage()
                            }, modifier = Modifier.fillMaxWidth()) { Text("Elegir imagen de la galería") }
                            OutlinedButton(onClick = {
                                // The external camera app temporarily backgrounds this Activity.
                                onTakePhoto()
                            }, modifier = Modifier.fillMaxWidth()) { Text("📷 Hacer foto al objeto") }
                            Button(onClick = {
                                val clean = wordDraft.trim()
                                when {
                                    NameRules.validationError(clean) != null -> libraryMessage = NameRules.validationError(clean)!!
                                    pictureDraft.isBlank() || categoryDraft.isBlank() -> libraryMessage = "Completa la palabra, la categoría y elige una imagen o emoji."
                                    pairs.any { it.word.equals(clean, ignoreCase = true) && it.word != editingWord } -> libraryMessage = "Esa palabra ya existe."
                                    else -> {
                                        val updatedPairs = if (editingWord == null) {
                                            pairs + PairItem(clean, pictureDraft.trim(), categoryDraft.trim(), difficultyDraft)
                                        } else {
                                            pairs.map { if (it.word == editingWord) it.copy(word = clean, picture = pictureDraft.trim(), category = categoryDraft.trim(), difficulty = difficultyDraft) else it }
                                        }
                                        runCatching {
                                            ExerciseUiStorage.savePairs(context, ContentStorage.Modality.TARJETAS, updatedPairs)
                                        }.onSuccess { savedPairs ->
                                            pairs = savedPairs
                                            wordDraft = ""
                                            pictureDraft = ""
                                            categoryDraft = "Primeras palabras"
                                            difficultyDraft = "Fácil"
                                            editingWord = null
                                            libraryMessage = "Tarjeta guardada correctamente."
                                            libraryPage = "list"
                                        }.onFailure { error ->
                                            libraryMessage = "No se ha podido guardar la tarjeta: ${error.message ?: "error de almacenamiento"}. Comprueba la imagen y la categoría e inténtalo de nuevo."
                                        }
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar tarjeta") }
                            OutlinedButton(onClick = { libraryPage = "list"; editingWord = null; libraryMessage = "" }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar y volver a la biblioteca") }
                            if (libraryMessage.isNotBlank()) Text(libraryMessage, color = if (libraryMessage.startsWith("No se ha podido")) Color(0xFFB3261E) else Color(0xFF24733B))
                        }
                    }
            "categories" -> item {
                        Text("Categorías", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Activa o desactiva categorías completas sin borrar sus palabras. También puedes mover o eliminar categorías.", fontSize = 14.sp, color = Color(0xFF667085))
                        OutlinedTextField(value = categoryDraftName, onValueChange = { categoryDraftName = NameRules.filterInput(it) }, label = { Text("Nueva categoría") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = { val clean = categoryDraftName.trim(); if (NameRules.validationError(clean) != null) categoryMessage = NameRules.validationError(clean)!!; else if (categories.any { NameRules.key(it) == NameRules.key(clean) }) categoryMessage = "Esa categoría ya existe."; else { categoryNames = categoryNames + clean; preferences.edit().putString("categories", categoryNames.joinToString("|")).apply(); categoryDraftName = ""; categoryMessage = "Categoría creada." } }, modifier = Modifier.fillMaxWidth()) { Text("Añadir categoría") }
                        if (categoryMessage.isNotBlank()) Text(categoryMessage, color = Color(0xFF24733B))
                        categories.forEach { name ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                    val categoryPairs = pairs.filter { it.category.equals(name, ignoreCase = true) }
                                    val allCategoryWordsActive = categoryPairs.isNotEmpty() && categoryPairs.all { it.active }
                                    val anyCategoryWordsActive = categoryPairs.any { it.active }
                                    Text("${categoryPairs.size} palabras · ${if (categoryPairs.isEmpty()) "vacía" else if (allCategoryWordsActive) "activa" else if (anyCategoryWordsActive) "parcialmente activa" else "desactivada"}", color = Color(0xFF667085))
                                    OutlinedButton(
                                        onClick = {
                                            val activate = !allCategoryWordsActive
                                            pairs = pairs.map { pair -> if (pair.category.equals(name, ignoreCase = true)) pair.copy(active = activate) else pair }
                                            pairs = ExerciseUiStorage.savePairs(context, ContentStorage.Modality.TARJETAS, pairs)
                                            categoryMessage = if (categoryPairs.isEmpty()) "Esta categoría no tiene palabras todavía." else if (activate) "Categoría «$name» activada." else "Categoría «$name» desactivada."
                                        },
                                        enabled = categoryPairs.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(if (allCategoryWordsActive) "Desactivar categoría" else "Activar toda la categoría") }
                                    if (name.equals(categoryToRemove, ignoreCase = true)) {
                                        Text("Borrar categoría y contenido", fontWeight = FontWeight.SemiBold)
                                        Text("Esta opción eliminará también las ${pairs.count { it.category.equals(name, ignoreCase = true) }} palabras de esta categoría.", fontSize = 13.sp, color = Color(0xFFB3261E))
                                        Button(onClick = {
                                            pairs = pairs.filterNot { it.category.equals(name, ignoreCase = true) }
                                            pairs = ExerciseUiStorage.savePairs(context, ContentStorage.Modality.TARJETAS, pairs)
                                            categoryNames = categoryNames.filterNot { it.equals(name, ignoreCase = true) }
                                            preferences.edit().putString("categories", categoryNames.joinToString("|")).apply()
                                            categoryToRemove = ""
                                            categoryMessage = "Categoría y palabras eliminadas."
                                        }, modifier = Modifier.fillMaxWidth()) { Text("Borrar categoría y contenido") }
                                        Text("Borrar categoría pero mover contenido a:", fontWeight = FontWeight.SemiBold)
                                        categories.filterNot { it.equals(name, ignoreCase = true) }.forEach { destination ->
                                            OutlinedButton(onClick = {
                                                val moving = pairs.filter { it.category.equals(name, ignoreCase = true) }
                                                val destinationItems = pairs.filter { it.category.equals(destination, ignoreCase = true) }
                                                val conflicts = NameRules.duplicates(moving.map { it.word }, destinationItems.map { it.word })
                                                if (conflicts.isNotEmpty()) {
                                                    categoryMessage = "No se ha movido el contenido. Ya existen en «$destination»: ${conflicts.joinToString()}. Renombra o resuelve esos duplicados antes de reasignar."
                                                } else {
                                                    pairs = pairs.map { if (it.category.equals(name, ignoreCase = true)) it.copy(category = destination) else it }
                                                    pairs = ExerciseUiStorage.savePairs(context, ContentStorage.Modality.TARJETAS, pairs)
                                                    categoryNames = categoryNames.filterNot { it.equals(name, ignoreCase = true) }
                                                    preferences.edit().putString("categories", categoryNames.joinToString("|")).apply()
                                                    categoryToRemove = ""
                                                    categoryMessage = "Categoría eliminada; palabras movidas a $destination."
                                                }
                                            }, modifier = Modifier.fillMaxWidth()) { Text(destination) }
                                        }
                                        OutlinedButton(onClick = { categoryToRemove = ""; categoryMessage = "Operación cancelada. No se ha borrado nada." }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                                    } else {
                                        OutlinedButton(onClick = { categoryToRemove = name; categoryMessage = "" }, modifier = Modifier.fillMaxWidth()) { Text("Eliminar / reasignar") }
                                    }
                                }
                            }
                        }
                    }

    }
}
