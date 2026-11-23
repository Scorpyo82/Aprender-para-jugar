package es.aprenderparajugar

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.sequenceSettingsItems(
    parentSectionState: MutableState<String>,
    preferences: SharedPreferences,
    sequenceLibraryState: SequenceLibraryState,
    sequenceContentFormatState: MutableState<String>,
    sequenceMaxCardsState: androidx.compose.runtime.MutableIntState,
    onCreateExercise: () -> Unit,
    onEditExercise: (Int, SequenceExercise) -> Unit
) {
    when (parentSectionState.value) {
                "sequences-settings" -> {
                    item { Text("Configurar Secuencias", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    item {
                        Card(Modifier.fillMaxWidth().clickable { parentSectionState.value = "sequence-library" }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5E6))) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("📚", fontSize = 26.sp)
                                Column(Modifier.weight(1f)) { Text("Biblioteca común de ejercicios", fontWeight = FontWeight.Bold); Text("Crear, editar, filtrar y activar ejercicios de los cuatro tipos", fontSize = 13.sp, color = Color(0xFF667085)) }
                                Text("›", fontSize = 24.sp)
                            }
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth().clickable { parentSectionState.value = "sequence-categories" }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5E6))) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("🗂️", fontSize = 26.sp)
                                Column(Modifier.weight(1f)) { Text("Categorías comunes", fontWeight = FontWeight.Bold); Text("Activar o desactivar categorías para todos los tipos", fontSize = 13.sp, color = Color(0xFF667085)) }
                                Text("›", fontSize = 24.sp)
                            }
                        }
                    }
                    item {
                        Text("Tipos de ejercicio", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Estos son los cuatro tipos previstos para esta modalidad.", fontSize = 14.sp, color = Color(0xFF667085))
                    }
                    listOf(
                        Triple("Orden temporal", "Primero, después y al final", "temporal"),
                        Triple("Procesos", "Ordenar los pasos de una actividad", "procesos"),
                        Triple("Causa y consecuencia", "Relacionar una situación con su resultado", "causa"),
                        Triple("Ciclos", "Comprender procesos que se repiten", "ciclos")
                    ).forEach { (name, description, key) ->
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSectionState.value = "sequence-type-$key" }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8EC))) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(when (key) { "temporal" -> "🕒"; "procesos" -> "⚙️"; "causa" -> "🔗"; else -> "🔄" }, fontSize = 24.sp)
                                    Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(description, fontSize = 13.sp, color = Color(0xFF667085)) }
                                    Text("›", fontSize = 26.sp, color = Color(0xFF39713A))
                                }
                            }
                        }
                    }
                    item {
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Qué mostrar en la actividad:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        listOf("Imagen y texto" to "Imágenes con texto", "Solo imágenes" to "Solo imágenes", "Solo texto" to "Solo texto").forEach { (format, label) ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    sequenceContentFormatState.value = format
                                    preferences.edit().putString("sequence_content_format_v1", format).apply()
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, modifier = Modifier.weight(1f))
                                androidx.compose.material3.RadioButton(
                                    selected = sequenceContentFormatState.value == format,
                                    onClick = {
                                        sequenceContentFormatState.value = format
                                        preferences.edit().putString("sequence_content_format_v1", format).apply()
                                    }
                                )
                            }
                        }
                    }
                    item {
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Máximo de tarjetas por secuencia", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Limita el número de tarjetas de las secuencias que se ofrecerán al menor.", fontSize = 14.sp, color = Color(0xFF667085))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { if (sequenceMaxCardsState.intValue > 2) { sequenceMaxCardsState.intValue--; preferences.edit().putInt("sequence_max_cards_v1", sequenceMaxCardsState.intValue).apply() } }, enabled = sequenceMaxCardsState.intValue > 2, modifier = Modifier.weight(1f)) { Text("−") }
                            Text("${sequenceMaxCardsState.intValue}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { if (sequenceMaxCardsState.intValue < 6) { sequenceMaxCardsState.intValue++; preferences.edit().putInt("sequence_max_cards_v1", sequenceMaxCardsState.intValue).apply() } }, enabled = sequenceMaxCardsState.intValue < 6, modifier = Modifier.weight(1f)) { Text("+") }
                        }
                    }
                }
                "sequence-type-temporal", "sequence-type-procesos", "sequence-type-causa", "sequence-type-ciclos" -> {
                    val sequenceType = when (parentSectionState.value) {
                        "sequence-type-temporal" -> "Orden temporal"
                        "sequence-type-procesos" -> "Procesos"
                        "sequence-type-causa" -> "Causa y consecuencia"
                        else -> "Ciclos"
                    }
                    item { Text(sequenceType, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    item { Text("Configura el contenido específico de esta modalidad.", fontSize = 14.sp, color = Color(0xFF667085)) }
                    item {
                        Card(Modifier.fillMaxWidth().clickable {
                            val key = when (parentSectionState.value) {
                                "sequence-type-temporal" -> "temporal"
                                "sequence-type-procesos" -> "procesos"
                                "sequence-type-causa" -> "causa"
                                else -> "ciclos"
                            }
                            parentSectionState.value = "sequence-library"
                        }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8EC))) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("🖼️", fontSize = 26.sp)
                                Column { Text("Biblioteca de secuencias", fontWeight = FontWeight.Bold); Text("Ver, crear y editar ejercicios de $sequenceType", fontSize = 13.sp, color = Color(0xFF667085)) }
                            }
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth().clickable {
                            val key = when (parentSectionState.value) {
                                "sequence-type-temporal" -> "temporal"
                                "sequence-type-procesos" -> "procesos"
                                "sequence-type-causa" -> "causa"
                                else -> "ciclos"
                            }
                            parentSectionState.value = "sequence-categories"
                        }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8EC))) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("🗂️", fontSize = 26.sp)
                                Column { Text("Categorías", fontWeight = FontWeight.Bold); Text("Organizar por temas los ejercicios de $sequenceType", fontSize = 13.sp, color = Color(0xFF667085)) }
                            }
                        }
                    }
                    item { Text(if (parentSectionState.value == "sequence-type-temporal") "Prototipo funcional: biblioteca y editor de Orden temporal." else "Cada modalidad tendrá su propia biblioteca de ejercicios.", fontSize = 13.sp, color = Color(0xFF667085)) }
                }
                "sequence-library", "sequence-library-temporal", "sequence-library-procesos", "sequence-library-causa", "sequence-library-ciclos" -> {
                    val sequenceTypes = listOf("Orden temporal", "Procesos", "Causa y consecuencia", "Ciclos")
                    val sequenceCategories = (sequenceLibraryState.categoryNames.value + sequenceLibraryState.exercises.value.map { it.category }.filter { it.isNotBlank() }).distinct().sorted()
                    val visibleExercises = sequenceLibraryState.exercises.value.withIndex().filter { (_, exercise) ->
                        (sequenceLibraryState.filterType.value == "Todos" || exercise.type == sequenceLibraryState.filterType.value) &&
                        (sequenceLibraryState.filterCategory.value == "Todas" || exercise.category == sequenceLibraryState.filterCategory.value) &&
                        (sequenceLibraryState.filterDifficulty.value == "Todas" || exercise.difficulty == sequenceLibraryState.filterDifficulty.value) &&
                        (sequenceLibraryState.filterStatus.value == "Todos" || if (sequenceLibraryState.filterStatus.value == "Activos") exercise.active else !exercise.active)
                    }
                    item { Text("Biblioteca de secuencias", fontSize = 23.sp, fontWeight = FontWeight.Bold) }
                    item { Text("Control parental · Gestiona ejercicios, categorías y su disponibilidad.", fontSize = 14.sp, color = Color(0xFF667085)) }
                    item {
                        Button(onClick = {
                            onCreateExercise()
                        }, modifier = Modifier.fillMaxWidth()) { Text("＋ Crear ejercicio") }
                    }
                    item {
                        Text("Filtrar contenido", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    item {
                        Text("Tipo", fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            (listOf("Todos") + sequenceTypes).forEach { type ->
                                FilterChip(selected = sequenceLibraryState.filterType.value == type, onClick = { sequenceLibraryState.filterType.value = type }, label = { Text(type, softWrap = false) })
                            }
                        }
                    }
                    item {
                        Text("Categoría", fontWeight = FontWeight.Bold)
                        androidx.compose.foundation.layout.Box {
                            OutlinedButton(onClick = { sequenceLibraryState.filterCategoryExpanded.value = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("🗂️ Categoría: ${sequenceLibraryState.filterCategory.value}  ▾", maxLines = 1)
                            }
                            DropdownMenu(expanded = sequenceLibraryState.filterCategoryExpanded.value, onDismissRequest = { sequenceLibraryState.filterCategoryExpanded.value = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                                (listOf("Todas") + sequenceCategories).forEach { category ->
                                    DropdownMenuItem(text = { Text(category) }, onClick = { sequenceLibraryState.filterCategory.value = category; sequenceLibraryState.filterCategoryExpanded.value = false })
                                }
                            }
                        }
                    }
                    item {
                        Text("Dificultad", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("Todas", "Fácil", "Normal", "Difícil").forEach { level -> FilterChip(selected = sequenceLibraryState.filterDifficulty.value == level, onClick = { sequenceLibraryState.filterDifficulty.value = level }, label = { Text(level) }) }
                        }
                    }
                    item {
                        Text("Estado", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("Todos", "Activos", "Inactivos").forEach { status -> FilterChip(selected = sequenceLibraryState.filterStatus.value == status, onClick = { sequenceLibraryState.filterStatus.value = status }, label = { Text(status) }) }
                        }
                    }
                    if (visibleExercises.isEmpty()) item { Text("No hay ejercicios que coincidan con estos filtros.", color = Color(0xFF667085)) }
                    visibleExercises.forEach { (index, exercise) ->
                        item {
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (exercise.active) Color.White else Color(0xFFF2F4F7))) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).clickable {
                                        onEditExercise(index, exercise)
                                    }) {
                                        Text(exercise.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        Text("${exercise.type} · ${exercise.category} · ${exercise.difficulty}", fontSize = 12.sp, color = Color(0xFF667085))
                                        Text("${exercise.cards.size} tarjetas · ${exercise.format}", fontSize = 12.sp, color = Color(0xFF667085))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Switch(checked = exercise.active, onCheckedChange = { active ->
                                            sequenceLibraryState.exercises.value = sequenceLibraryState.exercises.value.toMutableList().also { it[index] = exercise.copy(active = active) }
                                            sequenceLibraryState.exercises.value = sequenceLibraryState.saveExercises(sequenceLibraryState.exercises.value)
                                        })
                                        Text(if (exercise.active) "Activo" else "Inactivo", fontSize = 11.sp)
                                        OutlinedButton(onClick = {
                                            sequenceLibraryState.exercises.value = sequenceLibraryState.exercises.value.filterIndexed { currentIndex, _ -> currentIndex != index }
                                            sequenceLibraryState.exercises.value = sequenceLibraryState.saveExercises(sequenceLibraryState.exercises.value)
                                            sequenceLibraryState.categoryMessage.value = "Ejercicio eliminado."
                                        }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("Eliminar", fontSize = 12.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
                "sequence-categories", "sequence-categories-temporal", "sequence-categories-procesos", "sequence-categories-causa", "sequence-categories-ciclos" -> {
                    val sequenceCategories = (sequenceLibraryState.categoryNames.value + sequenceLibraryState.exercises.value.map { it.category }.filter { it.isNotBlank() }).distinct().sorted()
                    item { Text("Categorías de secuencias", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    item { Text("Crea y organiza categorías temáticas compartidas por todos los tipos de secuencia. Desactivar una categoría mantiene sus ejercicios guardados, pero evita que aparezcan en las partidas.", fontSize = 14.sp, color = Color(0xFF667085)) }
                    item {
                        OutlinedTextField(
                            value = sequenceLibraryState.newCategoryDraft.value,
                            onValueChange = { sequenceLibraryState.newCategoryDraft.value = NameRules.filterInput(it); sequenceLibraryState.categoryMessage.value = "" },
                            label = { Text("Nueva categoría") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(onClick = {
                            val name = sequenceLibraryState.newCategoryDraft.value.trim()
                            when {
                                NameRules.validationError(name) != null -> sequenceLibraryState.categoryMessage.value = NameRules.validationError(name)!!
                                sequenceCategories.any { NameRules.key(it) == NameRules.key(name) } -> sequenceLibraryState.categoryMessage.value = "Ya existe una categoría con ese nombre."
                                else -> {
                                    sequenceLibraryState.categoryNames.value = (sequenceLibraryState.categoryNames.value + name).distinct()
                                    preferences.edit().putString("sequence_categories_v1", sequenceLibraryState.categoryNames.value.joinToString("|")).apply()
                                    sequenceLibraryState.newCategoryDraft.value = ""
                                    sequenceLibraryState.categoryMessage.value = "Categoría creada."
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("＋ Crear categoría") }
                        if (sequenceLibraryState.categoryMessage.value.isNotBlank()) Text(sequenceLibraryState.categoryMessage.value, color = if (sequenceLibraryState.categoryMessage.value == "Categoría creada.") Color(0xFF24733B) else Color(0xFFB3261E), fontSize = 13.sp)
                    }
                    sequenceCategories.forEach { category ->
                        item {
                            val enabled = category !in sequenceLibraryState.disabledCategories.value
                            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (enabled) Color.White else Color(0xFFF2F4F7))) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(category, fontWeight = FontWeight.Bold)
                                            Text("${sequenceLibraryState.exercises.value.count { it.category == category }} ejercicios · ${sequenceLibraryState.exercises.value.count { it.category == category && it.active }} activos", fontSize = 12.sp, color = Color(0xFF667085))
                                        }
                                        Switch(checked = enabled, onCheckedChange = { checked ->
                                            sequenceLibraryState.disabledCategories.value = if (checked) sequenceLibraryState.disabledCategories.value - category else sequenceLibraryState.disabledCategories.value + category
                                            preferences.edit().putStringSet("disabled_sequence_categories", sequenceLibraryState.disabledCategories.value).apply()
                                        })
                                    }
                                    if (sequenceLibraryState.categoryToDelete.value.equals(category, ignoreCase = true)) {
                                        val assignedCount = sequenceLibraryState.exercises.value.count { it.category.equals(category, ignoreCase = true) }
                                        Text("Borrar categoría y contenido", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Esta opción eliminará también los $assignedCount ejercicios de esta categoría.",
                                            fontSize = 13.sp,
                                            color = Color(0xFFB3261E)
                                        )
                                        Button(onClick = {
                                            sequenceLibraryState.exercises.value = sequenceLibraryState.exercises.value.filterNot { it.category.equals(category, ignoreCase = true) }
                                            sequenceLibraryState.exercises.value = sequenceLibraryState.saveExercises(sequenceLibraryState.exercises.value)
                                            sequenceLibraryState.categoryNames.value = sequenceLibraryState.categoryNames.value.filterNot { it.equals(category, ignoreCase = true) }
                                            preferences.edit().putString("sequence_categories_v1", sequenceLibraryState.categoryNames.value.joinToString("|")).apply()
                                            sequenceLibraryState.disabledCategories.value = sequenceLibraryState.disabledCategories.value.filterNot { it.equals(category, ignoreCase = true) }.toSet()
                                            preferences.edit().putStringSet("disabled_sequence_categories", sequenceLibraryState.disabledCategories.value).apply()
                                            sequenceLibraryState.categoryToDelete.value = null
                                            sequenceLibraryState.categoryMessage.value = "Categoría y ejercicios eliminados."
                                        }, modifier = Modifier.fillMaxWidth()) { Text("Borrar categoría y contenido") }
                                        Text("Borrar categoría pero mover contenido a:", fontWeight = FontWeight.SemiBold)
                                        val destinations = sequenceCategories.filterNot { it.equals(category, ignoreCase = true) }
                                        destinations.forEach { destination ->
                                            OutlinedButton(onClick = {
                                                val moving = sequenceLibraryState.exercises.value.filter { it.category.equals(category, ignoreCase = true) }
                                                val destinationItems = sequenceLibraryState.exercises.value.filter { it.category.equals(destination, ignoreCase = true) }
                                                val conflicts = NameRules.duplicates(moving.map { it.title }, destinationItems.map { it.title })
                                                if (conflicts.isNotEmpty()) {
                                                    sequenceLibraryState.categoryMessage.value = "No se han movido los ejercicios. Ya existen en «$destination»: ${conflicts.joinToString()}. Renombra o resuelve esos duplicados antes de reasignar."
                                                } else {
                                                    sequenceLibraryState.exercises.value = sequenceLibraryState.exercises.value.map {
                                                        if (it.category.equals(category, ignoreCase = true)) it.copy(category = destination) else it
                                                    }
                                                    sequenceLibraryState.exercises.value = sequenceLibraryState.saveExercises(sequenceLibraryState.exercises.value)
                                                    sequenceLibraryState.categoryNames.value = sequenceLibraryState.categoryNames.value.filterNot { it.equals(category, ignoreCase = true) }
                                                    preferences.edit().putString("sequence_categories_v1", sequenceLibraryState.categoryNames.value.joinToString("|")).apply()
                                                    sequenceLibraryState.disabledCategories.value = sequenceLibraryState.disabledCategories.value.filterNot { it.equals(category, ignoreCase = true) }.toSet()
                                                    preferences.edit().putStringSet("disabled_sequence_categories", sequenceLibraryState.disabledCategories.value).apply()
                                                    sequenceLibraryState.categoryToDelete.value = null
                                                    sequenceLibraryState.categoryMessage.value = "Categoría eliminada; ejercicios movidos a $destination."
                                                }
                                            }, modifier = Modifier.fillMaxWidth()) { Text(destination) }
                                        }
                                        OutlinedButton(onClick = {
                                            sequenceLibraryState.categoryToDelete.value = null
                                            sequenceLibraryState.categoryMessage.value = "Operación cancelada. No se ha borrado nada."
                                        }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                                    } else {
                                        OutlinedButton(onClick = { sequenceLibraryState.categoryToDelete.value = category; sequenceLibraryState.categoryMessage.value = "" }, modifier = Modifier.fillMaxWidth()) { Text("Eliminar / reasignar") }
                                    }
                                }
                            }
                        }
                    }
                }

    }
}
