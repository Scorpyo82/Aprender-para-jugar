package es.aprenderparajugar

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun LazyListScope.libraryImportContent(
    context: Context,
    transferPackage: LibraryPackageManager.PackageInfo?,
    expandedImportSequenceCategoriesState: MutableState<Set<String>>,
    transferImportCategoriesState: MutableState<Map<ContentStorage.Modality, Set<String>>>,
    transferImportStoriesState: MutableState<Set<String>>,
    transferImportObjectsState: MutableState<Set<String>>,
    transferObjectConflictDecisionsState: MutableState<Map<String, String>>,
    transferStoryConflictDecisionsState: MutableState<Map<String, String>>,
    transferExerciseConflictDecisionsState: MutableState<Map<String, String>>,
    transferConflictIndexState: MutableState<Int>,
    transferImportStatisticsState: MutableState<Boolean>,
    transferImportUsageStatisticsState: MutableState<Boolean>,
    transferImportPreparationLoadingState: MutableState<Boolean>,
    transferImportPreparationErrorState: MutableState<Boolean>,
    transferExerciseConflictsState: MutableState<List<LibraryPackageManager.ExerciseConflictInfo>>,
    transferObjectConflictsState: MutableState<List<LibraryPackageManager.ObjectConflictInfo>>,
    transferStoryNameConflictsState: MutableState<List<Pair<LibraryPackageManager.StoryInfo, StoredStory>>>,
    transferImportObjectEntriesState: MutableState<List<Pair<LibraryPackageManager.ObjectDependencyInfo, StoredObject>>>,
    transferRequiredImportObjectsState: MutableState<Map<String, List<String>>>,
    transferLocalObjectsState: MutableState<List<StoredObject>>,
    transferObjectConflictThumbnailsState: MutableState<Map<String, Pair<Bitmap?, Bitmap?>>>,
    transferMessage: String,
    onImportSelection: () -> Unit,
    onCancelImport: () -> Unit
) {
                            val info = transferPackage
                            item { Text("Contenido del archivo", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            if (transferImportPreparationLoadingState.value) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Comprobando coincidencias y dependencias…", fontSize = 13.sp, color = Color(0xFF667085))
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                            if (info == null) {
                                item { Text("No hay ningún paquete validado. Vuelve atrás y selecciona un archivo.") }
                            } else {
                                val storyNameConflicts = transferStoryNameConflictsState.value
                                if (info.stories.isNotEmpty()) {
                                    item {
                                        Text("Cuentos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text("Se importan completos, con escenas, conexiones y recursos.", fontSize = 12.sp, color = Color(0xFF667085))
                                    }
                                    item {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = info.stories.all { it.key in transferImportStoriesState.value },
                                                onCheckedChange = { checked -> transferImportStoriesState.value = if (checked) info.stories.map { it.key }.toSet() else emptySet() }
                                            )
                                            Text("Seleccionar todos los cuentos")
                                        }
                                    }
                                    info.stories.forEach { story ->
                                        item {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = story.key in transferImportStoriesState.value,
                                                    onCheckedChange = { checked ->
                                                        transferImportStoriesState.value = transferImportStoriesState.value.toMutableSet().also {
                                                            if (checked) it.add(story.key) else it.remove(story.key)
                                                        }
                                                    }
                                                )
                                                Column {
                                                    Text(story.title, fontWeight = FontWeight.SemiBold)
                                                    Text("${story.collection} · ${story.sceneCount} escenas", fontSize = 12.sp, color = Color(0xFF667085))
                                                }
                                            }
                                        }
                                    }
                                    val conflictDetails = transferObjectConflictsState.value
                                    if (storyNameConflicts.isNotEmpty()) {
                                        item {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("Cuentos con el mismo nombre", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Text("Un cuento nunca se fusiona ni se adapta. Si confirmas, el cuento local se sustituirá por completo, con todas sus escenas, conexiones, imágenes y audios. Si no quieres sustituirlo, cancela toda la importación.", fontSize = 12.sp, color = Color(0xFF667085))
                                            }
                                        }
                                        storyNameConflicts.forEach { (incomingStory, existingStory) ->
                                            item {
                                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5))) {
                                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text(incomingStory.title, fontWeight = FontWeight.Bold)
                                                        Text("Colección: ${incomingStory.collection}", fontSize = 12.sp)
                                                        Text("Local: ${existingStory.metadata.scenes.size} escenas · ZIP: ${incomingStory.sceneCount} escenas", fontSize = 12.sp, color = Color(0xFF667085))
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Checkbox(
                                                                checked = transferStoryConflictDecisionsState.value[incomingStory.key] == "overwrite",
                                                                onCheckedChange = { checked ->
                                                                    transferStoryConflictDecisionsState.value = if (checked) {
                                                                        transferStoryConflictDecisionsState.value + (incomingStory.key to "overwrite")
                                                                    } else {
                                                                        transferStoryConflictDecisionsState.value - incomingStory.key
                                                                    }
                                                                }
                                                            )
                                                            Column {
                                                                Text("Confirmo sobrescribir el cuento completo", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                                Text("Puedes desmarcarlo en cualquier momento. Sin confirmación no se permitirá importar.", fontSize = 11.sp, color = Color(0xFF667085))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    val conflictIndex = transferConflictIndexState.value.coerceIn(0, (conflictDetails.size - 1).coerceAtLeast(0))
                                    item {
                                        Text(if (conflictDetails.isEmpty()) "Revisión de objetos: sin conflictos" else "Conflicto ${conflictIndex + 1} de ${conflictDetails.size}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        if (conflictDetails.isNotEmpty()) {
                                            androidx.compose.material3.LinearProgressIndicator(progress = { (conflictIndex + 1).toFloat() / conflictDetails.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                                            Text("Resuelve las coincidencias una a una. Se comparan las imágenes reales y los datos de cada objeto.", fontSize = 12.sp, color = Color(0xFF667085))
                                        }
                                    }
                                    val localObjects = transferLocalObjectsState.value
                                    val requiredObjectKeys = transferRequiredImportObjectsState.value.keys
                                    info.objects.filter { it.key in requiredObjectKeys }.forEach { dependency ->
                                        val incoming = transferImportObjectEntriesState.value.firstOrNull { it.first.key == dependency.key }?.second
                                            ?: return@forEach
                                        val existing = localObjects.firstOrNull {
                                            it.collection == incoming.collection && NameRules.key(it.metadata.name) == NameRules.key(incoming.metadata.name)
                                        }
                                        if (existing != null) {
                                            if (conflictDetails.indexOfFirst { it.incoming.folderName == dependency.directory } == conflictIndex) {
                                                item {
                                                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Text("Conflicto de objeto: ${dependency.name}", fontWeight = FontWeight.Bold)
                                                            Text("Comparación de las dos definiciones:", fontSize = 12.sp, color = Color(0xFF667085))
                                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                    Text("En el ZIP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                                    val incomingBitmap = transferObjectConflictThumbnailsState.value[dependency.directory]?.first
                                                                    if (incomingBitmap != null) Image(incomingBitmap.asImageBitmap(), contentDescription = "Objeto del ZIP", modifier = Modifier.width(84.dp).height(84.dp), contentScale = ContentScale.Fit)
                                                                    Text(incoming.metadata.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                                    Text("Aparece en la tienda: ${if (incoming.metadata.availableInStore) "Sí" else "No"}", fontSize = 11.sp, color = Color(0xFF667085))
                                                                }
                                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                    Text("En este dispositivo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                                    val existingBitmap = transferObjectConflictThumbnailsState.value[dependency.directory]?.second
                                                                    if (existingBitmap != null) Image(existingBitmap.asImageBitmap(), contentDescription = "Objeto existente", modifier = Modifier.width(84.dp).height(84.dp), contentScale = ContentScale.Fit)
                                                                    Text(existing.metadata.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                                    Text("Aparece en la tienda: ${if (existing.metadata.availableInStore) "Sí" else "No"}", fontSize = 11.sp, color = Color(0xFF667085))
                                                                }
                                                            }
                                                            val detail = conflictDetails.firstOrNull { it.incoming.folderName == dependency.directory }
                                                            detail?.differences?.forEach { difference -> Text("• $difference", fontSize = 11.sp, color = Color(0xFF475467)) }
                                                            Text("Ya existe un objeto con la misma identidad, pero sus datos o imagen son diferentes.", fontSize = 12.sp, color = Color(0xFF667085))
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                androidx.compose.material3.RadioButton(
                                                                    selected = transferObjectConflictDecisionsState.value[dependency.directory] == "reuse",
                                                                    onClick = { transferObjectConflictDecisionsState.value = transferObjectConflictDecisionsState.value + (dependency.directory to "reuse") }
                                                                )
                                                                Text("Conservar el objeto de este dispositivo")
                                                            }
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                androidx.compose.material3.RadioButton(
                                                                    selected = transferObjectConflictDecisionsState.value[dependency.directory] == "overwrite",
                                                                    onClick = { transferObjectConflictDecisionsState.value = transferObjectConflictDecisionsState.value + (dependency.directory to "overwrite") }
                                                                )
                                                                Text("Sobrescribir el objeto existente")
                                                            }
                                                            if (transferObjectConflictDecisionsState.value[dependency.directory] == "overwrite") {
                                                                val affectedStories = detail?.referencingStories.orEmpty()
                                                                Text(
                                                                    if (affectedStories.isEmpty()) "No se han encontrado otros cuentos locales que utilicen este objeto."
                                                                    else "ATENCIÓN: ${affectedStories.size} cuento(s) local(es) también lo utilizan: ${affectedStories.joinToString(", ")}.",
                                                                    fontSize = 11.sp,
                                                                    color = Color(0xFFB3261E)
                                                                )
                                                                Text("La definición compartida cambiará para todos ellos. El inventario no se modifica.", fontSize = 11.sp, color = Color(0xFFB3261E))
                                                            }
                                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                                OutlinedButton(onClick = { transferConflictIndexState.value = (conflictIndex - 1).coerceAtLeast(0) }, enabled = conflictIndex > 0) { Text("Anterior") }
                                                                OutlinedButton(onClick = { transferConflictIndexState.value = (conflictIndex + 1).coerceAtMost(conflictDetails.lastIndex) }, enabled = conflictIndex < conflictDetails.lastIndex) { Text("Siguiente") }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (info.objects.isNotEmpty()) {
                                    val importObjectsWithData = transferImportObjectEntriesState.value
                                    val requiredImportObjects = transferRequiredImportObjectsState.value
                                    item {
                                        Text("Objetos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text("Los objetos requeridos por los cuentos seleccionados se incorporarán automáticamente. También puedes seleccionar objetos por separado. Esta operación no modifica el inventario del niño.", fontSize = 12.sp, color = Color(0xFF667085))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = info.objects.all { it.key in requiredImportObjects || it.key in transferImportObjectsState.value },
                                                onCheckedChange = { checked ->
                                                    transferImportObjectsState.value = if (checked) info.objects.map { it.key }.toSet() else emptySet()
                                                    transferObjectConflictDecisionsState.value = emptyMap()
                                                }
                                            )
                                            Text("Seleccionar todos los objetos")
                                        }
                                    }
                                    val collectibleObjects = importObjectsWithData.filter { it.second.metadata.collectible }
                                    if (collectibleObjects.isNotEmpty()) {
                                        item { Text("Coleccionables", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                                        collectibleObjects.forEach { (objectInfo, incoming) ->
                                            val requiredBy = requiredImportObjects[objectInfo.key].orEmpty()
                                            val isRequired = requiredBy.isNotEmpty()
                                            val selected = isRequired || objectInfo.key in transferImportObjectsState.value
                                            val local = transferLocalObjectsState.value.firstOrNull {
                                                it.collection == incoming.collection && NameRules.key(it.metadata.name) == NameRules.key(incoming.metadata.name)
                                            }
                                            item {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = selected,
                                                        enabled = !isRequired,
                                                        onCheckedChange = { checked ->
                                                            transferImportObjectsState.value = transferImportObjectsState.value.toMutableSet().also {
                                                                if (checked) it.add(objectInfo.key) else it.remove(objectInfo.key)
                                                            }
                                                        }
                                                    )
                                                    Column {
                                                        Text(objectInfo.name, fontWeight = FontWeight.SemiBold)
                                                        Text(
                                                            if (isRequired) "REQUERIDO" else if (incoming.metadata.collectible) "🏆 COLECCIONABLE" else "📦 OBJETO NORMAL",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = if (isRequired) Color(0xFF16803C) else if (incoming.metadata.collectible) Color(0xFF7952A5) else Color(0xFF356B91)
                                                        )
                                                        if (isRequired) Text("Requerido por: ${requiredBy.joinToString(", ")}", fontSize = 11.sp, color = Color(0xFF667085))
                                                        if (local != null) {
                                                            Text(
                                                                when (transferObjectConflictDecisionsState.value[objectInfo.directory]) {
                                                                    "reuse" -> "Coincidencia: se conservará el objeto local"
                                                                    "overwrite" -> "Coincidencia: se sobrescribirá el objeto local"
                                                                    else -> "Ya existe otro objeto con ese nombre; elige cómo resolverlo"
                                                                },
                                                                fontSize = 11.sp,
                                                                color = Color(0xFF9A6700)
                                                            )
                                                        }
                                                    }
                                                }
                                                if (selected && local != null) {
                                                    Column(Modifier.padding(start = 42.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            androidx.compose.material3.RadioButton(
                                                                selected = transferObjectConflictDecisionsState.value[objectInfo.directory] == "reuse",
                                                                onClick = { transferObjectConflictDecisionsState.value = transferObjectConflictDecisionsState.value + (objectInfo.directory to "reuse") }
                                                            )
                                                            Text("Conservar el objeto de este dispositivo", fontSize = 12.sp)
                                                        }
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            androidx.compose.material3.RadioButton(
                                                                selected = transferObjectConflictDecisionsState.value[objectInfo.directory] == "overwrite",
                                                                onClick = { transferObjectConflictDecisionsState.value = transferObjectConflictDecisionsState.value + (objectInfo.directory to "overwrite") }
                                                            )
                                                            Text("Sobrescribir el objeto existente", fontSize = 12.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    importObjectsWithData.filter { !it.second.metadata.collectible }
                                        .groupBy { it.second.collection }
                                        .toSortedMap()
                                        .forEach { (collection, objects) ->
                                            item { Text(collection, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                                            objects.forEach { (objectInfo, incoming) ->
                                                val requiredBy = requiredImportObjects[objectInfo.key].orEmpty()
                                                val isRequired = requiredBy.isNotEmpty()
                                                val selected = isRequired || objectInfo.key in transferImportObjectsState.value
                                                val local = transferLocalObjectsState.value.firstOrNull {
                                                    it.collection == incoming.collection && NameRules.key(it.metadata.name) == NameRules.key(incoming.metadata.name)
                                                }
                                                item {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = selected,
                                                            enabled = !isRequired,
                                                            onCheckedChange = { checked ->
                                                                transferImportObjectsState.value = transferImportObjectsState.value.toMutableSet().also {
                                                                    if (checked) it.add(objectInfo.key) else it.remove(objectInfo.key)
                                                                }
                                                            }
                                                        )
                                                        Column {
                                                            Text(objectInfo.name, fontWeight = FontWeight.SemiBold)
                                                            Text(
                                                                if (isRequired) "REQUERIDO" else if (incoming.metadata.collectible) "🏆 COLECCIONABLE" else "📦 OBJETO NORMAL",
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = if (isRequired) Color(0xFF16803C) else if (incoming.metadata.collectible) Color(0xFF7952A5) else Color(0xFF356B91)
                                                            )
                                                            if (isRequired) Text("Requerido por: ${requiredBy.joinToString(", ")}", fontSize = 11.sp, color = Color(0xFF667085))
                                                            if (local != null) {
                                                                Text(
                                                                    when (transferObjectConflictDecisionsState.value[objectInfo.directory]) {
                                                                        "reuse" -> "Coincidencia: se conservará el objeto local"
                                                                        "overwrite" -> "Coincidencia: se sobrescribirá el objeto local"
                                                                        else -> "Ya existe otro objeto con ese nombre; elige cómo resolverlo"
                                                                    },
                                                                    fontSize = 11.sp,
                                                                    color = Color(0xFF9A6700)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    if (selected && local != null) {
                                                        Column(Modifier.padding(start = 42.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                androidx.compose.material3.RadioButton(
                                                                    selected = transferObjectConflictDecisionsState.value[objectInfo.directory] == "reuse",
                                                                    onClick = { transferObjectConflictDecisionsState.value = transferObjectConflictDecisionsState.value + (objectInfo.directory to "reuse") }
                                                                )
                                                                Text("Conservar el objeto de este dispositivo", fontSize = 12.sp)
                                                            }
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                androidx.compose.material3.RadioButton(
                                                                    selected = transferObjectConflictDecisionsState.value[objectInfo.directory] == "overwrite",
                                                                    onClick = { transferObjectConflictDecisionsState.value = transferObjectConflictDecisionsState.value + (objectInfo.directory to "overwrite") }
                                                                )
                                                                Text("Sobrescribir el objeto existente", fontSize = 12.sp)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                }
                                info.modalities.forEach { modalityInfo ->
                                    val modality = modalityInfo.modality
                                    val label = when (modality) { ContentStorage.Modality.TARJETAS -> "Tarjetas"; ContentStorage.Modality.PALABRAS -> "Palabras"; ContentStorage.Modality.SECUENCIAS -> "Secuencias" }
                                    item {
                                        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        if (modality == ContentStorage.Modality.SECUENCIAS) {
                                            val selected = transferImportCategoriesState.value[modality].orEmpty()
                                            SequenceType.entries.forEach { type ->
                                                val exercisesOfType = modalityInfo.categories.flatMap { category ->
                                                    category.sequenceExercises.filter { it.type == type }.map { exercise ->
                                                        LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, type, exercise.directory)
                                                    }
                                                }
                                                if (exercisesOfType.isNotEmpty()) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = exercisesOfType.all { it in selected },
                                                            onCheckedChange = { checked ->
                                                                transferImportCategoriesState.value = transferImportCategoriesState.value.toMutableMap().also { map ->
                                                                    map[modality] = selected.toMutableSet().also { set ->
                                                                        if (checked) set.addAll(exercisesOfType) else set.removeAll(exercisesOfType.toSet())
                                                                    }
                                                                }
                                                            }
                                                        )
                                                        Text(type.displayName, fontWeight = FontWeight.Bold)
                                                    }
                                                    val categoriesForType = modalityInfo.categories.filter { category ->
                                                        category.sequenceExercises.any { it.type == type }
                                                    }
                                                    categoriesForType.forEach { category ->
                                                        val exercises = category.sequenceExercises.filter { it.type == type }
                                                        val categoryKey = "${type.jsonValue}::${category.directory}"
                                                        val exerciseKeys = exercises.map { exercise ->
                                                            LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, type, exercise.directory)
                                                        }
                                                        val expanded = categoryKey in expandedImportSequenceCategoriesState.value
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().clickable {
                                                                expandedImportSequenceCategoriesState.value = expandedImportSequenceCategoriesState.value.toMutableSet().also {
                                                                    if (expanded) it.remove(categoryKey) else it.add(categoryKey)
                                                                }
                                                            }.padding(start = 28.dp, top = 4.dp, bottom = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Checkbox(
                                                                checked = exerciseKeys.all { it in selected },
                                                                onCheckedChange = { checked ->
                                                                    transferImportCategoriesState.value = transferImportCategoriesState.value.toMutableMap().also { map ->
                                                                        map[modality] = selected.toMutableSet().also { set ->
                                                                            if (checked) set.addAll(exerciseKeys) else set.removeAll(exerciseKeys.toSet())
                                                                        }
                                                                    }
                                                                }
                                                            )
                                                            Text(if (expanded) "▾" else "▸", modifier = Modifier.padding(end = 8.dp), color = Color(0xFF667085))
                                                            Text(category.name, fontWeight = FontWeight.SemiBold, color = Color(0xFF475467))
                                                            Spacer(Modifier.weight(1f))
                                                            Text("${exercises.size}", fontSize = 12.sp, color = Color(0xFF667085))
                                                        }
                                                        if (expanded) exercises.forEach { exercise ->
                                                            val key = LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, type, exercise.directory)
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(start = 68.dp, top = 2.dp, bottom = 2.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Checkbox(
                                                                    checked = key in selected,
                                                                    onCheckedChange = { checked ->
                                                                        transferImportCategoriesState.value = transferImportCategoriesState.value.toMutableMap().also { map ->
                                                                            map[modality] = selected.toMutableSet().also { set ->
                                                                                if (checked) set.add(key) else set.remove(key)
                                                                            }
                                                                        }
                                                                    }
                                                                )
                                                                Text(exercise.title)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            modalityInfo.categories.forEach { category ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = category.directory in transferImportCategoriesState.value[modality].orEmpty(),
                                                        onCheckedChange = { checked -> transferImportCategoriesState.value = transferImportCategoriesState.value.toMutableMap().also { map -> map[modality] = map[modality].orEmpty().toMutableSet().also { set -> if (checked) set.add(category.directory) else set.remove(category.directory) } } }
                                                    )
                                                    Column {
                                                        Text(category.name)
                                                        Text("${category.exerciseCount} ejercicios", fontSize = 12.sp, color = Color(0xFF667085))
                                                    }
                                                }
                                            }
                                        }
                                        Text(if (modalityInfo.includesLearningStatistics || modalityInfo.includesUsageStatistics) "El paquete incluye estadísticas." else "El paquete no incluye estadísticas.", fontSize = 12.sp, color = Color(0xFF667085))
                                    }
                                }
                                if (info.modalities.any { it.includesLearningStatistics }) {
                                    item {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = transferImportStatisticsState.value, onCheckedChange = { transferImportStatisticsState.value = it })
                                            Column {
                                                Text("Importar estadísticas de aprendizaje", fontWeight = FontWeight.SemiBold)
                                                Text("Desactivado por defecto. Si no lo marcas, se conservarán las estadísticas de aprendizaje del dispositivo.", fontSize = 12.sp, color = Color(0xFF667085))
                                            }
                                        }
                                    }
                                }
                                if (info.modalities.any { it.includesUsageStatistics }) {
                                    item {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = transferImportUsageStatisticsState.value, onCheckedChange = { transferImportUsageStatisticsState.value = it })
                                            Column {
                                                Text("Importar estadísticas de uso", fontWeight = FontWeight.SemiBold)
                                                Text("Desactivado por defecto. Si no lo marcas, se conservarán las estadísticas de uso del dispositivo.", fontSize = 12.sp, color = Color(0xFF667085))
                                            }
                                        }
                                    }
                                }
                                item {
                                    val exerciseConflicts = transferExerciseConflictsState.value
                                    val groupedConflicts = ContentStorage.Modality.entries.mapNotNull { modality ->
                                        val conflicts = exerciseConflicts.filter { it.modality == modality }
                                        if (conflicts.isEmpty()) null else modality to conflicts
                                    }
                                    val unresolvedCount = exerciseConflicts.count {
                                        transferExerciseConflictDecisionsState.value[it.key] !in setOf("reuse", "overwrite")
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("Conflictos de ejercicios", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            if (exerciseConflicts.isEmpty()) "No hay ejercicios que coincidan con los que ya tienes."
                                            else "${exerciseConflicts.size - unresolvedCount} de ${exerciseConflicts.size} conflictos resueltos. Puedes decidir por modalidad o individualmente.",
                                            fontSize = 12.sp, color = Color(0xFF667085)
                                        )
                                        groupedConflicts.forEach { (modality, conflicts) ->
                                            val modalityName = when (modality) {
                                                ContentStorage.Modality.TARJETAS -> "Tarjetas"
                                                ContentStorage.Modality.SECUENCIAS -> "Secuencias"
                                                ContentStorage.Modality.PALABRAS -> "Palabras"
                                            }
                                            val groupResolved = conflicts.count {
                                                transferExerciseConflictDecisionsState.value[it.key] in setOf("reuse", "overwrite")
                                            }
                                            Card(
                                                Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9FC))
                                            ) {
                                                Column(
                                                    Modifier.fillMaxWidth().padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        "$modalityName · ${conflicts.size} conflictos",
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        "$groupResolved de ${conflicts.size} resueltos",
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF667085)
                                                    )
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        OutlinedButton(
                                                            modifier = Modifier.weight(1f),
                                                            onClick = {
                                                                transferExerciseConflictDecisionsState.value = transferExerciseConflictDecisionsState.value +
                                                                    conflicts.associate { it.key to "overwrite" }
                                                            }
                                                        ) {
                                                            Text("Sustituir todo", fontSize = 12.sp)
                                                        }
                                                        OutlinedButton(
                                                            modifier = Modifier.weight(1f),
                                                            onClick = {
                                                                transferExerciseConflictDecisionsState.value = transferExerciseConflictDecisionsState.value +
                                                                    conflicts.associate { it.key to "reuse" }
                                                            }
                                                        ) {
                                                            Text("No sustituir nada", fontSize = 12.sp)
                                                        }
                                                    }
                                                    conflicts.forEach { conflict ->
                                                        Card(
                                                            Modifier.fillMaxWidth(),
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = if (transferExerciseConflictDecisionsState.value[conflict.key] == "overwrite") Color(0xFFFFF4E5) else Color.White
                                                            )
                                                        ) {
                                                            Column(
                                                                Modifier.padding(12.dp),
                                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Text(conflict.incoming.metadata.title, fontWeight = FontWeight.Bold)
                                                                Text(conflict.categoryName, fontSize = 12.sp, color = Color(0xFF667085))
                                                                Text(
                                                                    "Ya existe un ejercicio con el mismo título o una carpeta de destino coincidente.",
                                                                    fontSize = 12.sp,
                                                                    color = Color(0xFF667085)
                                                                )
                                                                Text("En este dispositivo: ${conflict.existing.metadata.title}", fontSize = 12.sp)
                                                                Text("En el ZIP: ${conflict.incoming.metadata.title}", fontSize = 12.sp)
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    androidx.compose.material3.RadioButton(
                                                                        selected = transferExerciseConflictDecisionsState.value[conflict.key] == "reuse",
                                                                        onClick = {
                                                                            transferExerciseConflictDecisionsState.value = transferExerciseConflictDecisionsState.value +
                                                                                (conflict.key to "reuse")
                                                                        }
                                                                    )
                                                                    Text("Conservar el ejercicio del dispositivo", fontSize = 12.sp)
                                                                }
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    androidx.compose.material3.RadioButton(
                                                                        selected = transferExerciseConflictDecisionsState.value[conflict.key] == "overwrite",
                                                                        onClick = {
                                                                            transferExerciseConflictDecisionsState.value = transferExerciseConflictDecisionsState.value +
                                                                                (conflict.key to "overwrite")
                                                                        }
                                                                    )
                                                                    Text("Sustituirlo por el del ZIP", fontSize = 12.sp)
                                                                }
                                                                if (transferExerciseConflictDecisionsState.value[conflict.key] == "overwrite") {
                                                                    Text(
                                                                        if (transferImportStatisticsState.value) "También se importarán las estadísticas incluidas en el ZIP."
                                                                        else "Se conservarán las estadísticas locales existentes.",
                                                                        fontSize = 11.sp,
                                                                        color = Color(0xFF9A6700)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (unresolvedCount > 0) {
                                            Text(
                                                "Debes resolver todos los conflictos antes de importar.",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFB3261E)
                                            )
                                        }
                                    }
                                }
                                item {
                                    val finalConflictReview = transferObjectConflictsState.value
                                    val resolvedCount = finalConflictReview.count { it.incoming.folderName in transferObjectConflictDecisionsState.value }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Resumen de coincidencias", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("${resolvedCount} de ${finalConflictReview.size} conflictos resueltos.", fontSize = 12.sp, color = Color(0xFF667085))
                                        finalConflictReview.forEach { conflict ->
                                            val choice = when (transferObjectConflictDecisionsState.value[conflict.incoming.folderName]) {
                                                "reuse" -> "Se conservará el objeto local"
                                                "overwrite" -> "Se sobrescribirá el objeto local"
                                                else -> "Pendiente de decisión"
                                            }
                                            Text("• ${conflict.incoming.metadata.name}: $choice", fontSize = 12.sp)
                                        }
                                    }
                                }
                                item {
                                    Button(onClick = onImportSelection, enabled = !transferImportPreparationLoadingState.value && !transferImportPreparationErrorState.value &&
                                        (transferImportCategoriesState.value.values.any { it.isNotEmpty() } || transferImportStoriesState.value.isNotEmpty()) &&
                                        transferExerciseConflictsState.value.all { transferExerciseConflictDecisionsState.value[it.key] in setOf("reuse", "overwrite") } &&
                                        transferObjectConflictsState.value.all { it.incoming.folderName in transferObjectConflictDecisionsState.value } &&
                                        transferStoryNameConflictsState.value.all { transferStoryConflictDecisionsState.value[it.first.key] == "overwrite" },
                                        modifier = Modifier.fillMaxWidth()) { Text("Importar selección") }
                                }
                                item { OutlinedButton(onClick = onCancelImport, modifier = Modifier.fillMaxWidth()) { Text("Cancelar importación") } }
                            }
                            if (transferMessage.isNotBlank()) item { Text(transferMessage, fontSize = 14.sp, color = if (transferMessage.startsWith("No se pudo")) Color(0xFFB3261E) else Color(0xFF24733B)) }
}
