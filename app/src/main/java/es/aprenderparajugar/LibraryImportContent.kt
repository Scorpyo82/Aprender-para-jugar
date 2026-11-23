package es.aprenderparajugar

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
    expandedImportSequenceCategoriesState: androidx.compose.runtime.MutableState<Set<String>>,
    transferImportCategoriesState: androidx.compose.runtime.MutableState<Map<ContentStorage.Modality, Set<String>>>,
    transferImportStoriesState: androidx.compose.runtime.MutableState<Set<String>>,
    transferImportObjectsState: androidx.compose.runtime.MutableState<Set<String>>,
    transferObjectConflictDecisionsState: androidx.compose.runtime.MutableState<Map<String, String>>,
    transferStoryConflictDecisionsState: androidx.compose.runtime.MutableState<Map<String, String>>,
    transferExerciseConflictDecisionsState: androidx.compose.runtime.MutableState<Map<String, String>>,
    transferConflictIndexState: androidx.compose.runtime.MutableState<Int>,
    transferImportStatisticsState: androidx.compose.runtime.MutableState<Boolean>,
    transferImportUsageStatisticsState: androidx.compose.runtime.MutableState<Boolean>,
    transferImportPreparationLoadingState: androidx.compose.runtime.MutableState<Boolean>,
    transferImportPreparationErrorState: androidx.compose.runtime.MutableState<Boolean>,
    transferExerciseConflictsState: androidx.compose.runtime.MutableState<List<LibraryPackageManager.ExerciseConflictInfo>>,
    transferObjectConflictsState: androidx.compose.runtime.MutableState<List<LibraryPackageManager.ObjectConflictInfo>>,
    transferStoryNameConflictsState: androidx.compose.runtime.MutableState<List<Pair<LibraryPackageManager.StoryInfo, StoredStory>>>,
    transferImportObjectEntriesState: androidx.compose.runtime.MutableState<List<Pair<LibraryPackageManager.ObjectDependencyInfo, StoredObject>>>,
    transferRequiredImportObjectsState: androidx.compose.runtime.MutableState<Map<String, List<String>>>,
    transferLocalObjectsState: androidx.compose.runtime.MutableState<List<StoredObject>>,
    transferObjectConflictThumbnailsState: androidx.compose.runtime.MutableState<Map<String, Pair<Bitmap?, Bitmap?>>>,
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
                                                                OutlinedButton(onClick = onImportSelection}, enabled = !transferImportPreparationLoadingState.value && !transferImportPreparationErrorState.value &&
                                        (transferImportCategoriesState.value.values.any { it.isNotEmpty() } || transferImportStoriesState.value.isNotEmpty()) &&
                                        transferExerciseConflictsState.value.all { transferExerciseConflictDecisionsState.value[it.key] in setOf("reuse", "overwrite") } &&
                                        transferObjectConflictsState.value.all { it.incoming.folderName in transferObjectConflictDecisionsState.value } &&
                                        transferStoryNameConflictsState.value.all { transferStoryConflictDecisionsState.value[it.first.key] == "overwrite" },
                                        modifier = Modifier.fillMaxWidth()) { Text("Importar selección") }
                                }
                                item { OutlinedButton(onClick = onCancelImport}, modifier = Modifier.fillMaxWidth()) { Text("Cancelar importación") } }
                            }
                            if (transferMessage.isNotBlank()) item { Text(transferMessage, fontSize = 14.sp, color = if (transferMessage.startsWith("No se pudo")) Color(0xFFB3261E) else Color(0xFF24733B)) }
                        }
}
