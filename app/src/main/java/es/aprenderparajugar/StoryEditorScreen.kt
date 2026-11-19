package es.aprenderparajugar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Pantalla de metadatos y mapa de escenas del editor de cuentos. */
@Composable
internal fun StoryEditorScreen(
    state: StoryEditorState,
    collections: List<String>,
    onPickCover: () -> Unit,
    onOpenScene: (String) -> Unit,
    onOpenStructure: () -> Unit,
    onBackToLibrary: () -> Unit,
    objects: List<StoredObject> = emptyList(),
    coverRevision: Int = 0
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var title by state.title
    var collection by state.collection
    var collectionMenuExpanded by state.collectionMenuExpanded
    var cover by state.cover
    var scenes by state.scenes
    var selectedScene by state.selectedScene
    var summary by state.summary
    var sceneText by state.sceneText
    var illustrations by state.illustrations
    var ambientAudio by state.ambientAudio
    var narrationAudio by state.narrationAudio
    var objectTiming by state.objectTiming
    var awardedObjectByScene by state.awardedObjectByScene
    var keepAmbientByScene by state.keepAmbientByScene
    var message by state.message
    var editingFolder by state.editingFolder
    var editingCollectionFolder by state.editingCollectionFolder
    var storedStories by state.storedStories
    var requiredAccessObjects by state.requiredAccessObjects
    var showAccessObjectsDialog by remember { mutableStateOf(false) }
    var storyScenePendingDeletion by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Column { Text("Crear cuento", fontSize = 23.sp, fontWeight = FontWeight.Bold) }
            Column {
                            OutlinedTextField(value = title, onValueChange = { title = if (editingFolder == null) NameRules.filterInput(it) else it }, label = { Text("Título del cuento") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        }
            Column {
                            Column {
                                Text("Colección", fontWeight = FontWeight.SemiBold)
                                Box {
                                    OutlinedButton(onClick = { collectionMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text(collection.ifBlank { "Seleccionar colección" })
                                    }
                                    DropdownMenu(expanded = collectionMenuExpanded, onDismissRequest = { collectionMenuExpanded = false }) {
                                        collections.forEach { name ->
                                            DropdownMenuItem(text = { Text(name) }, onClick = { collection = name; collectionMenuExpanded = false })
                                        }
                                    }
                                }
                                if (collections.isEmpty()) Text("Todavía no hay colecciones de cuentos.", fontSize = 12.sp, color = Color(0xFF667085))
                            }
                        }
            Column {
                            Text("Requisitos para iniciar", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("El niño deberá tener todos los objetos seleccionados en su inventario para poder comenzar este cuento. No se consumen al iniciar.", fontSize = 13.sp, color = Color(0xFF667085))
                            if (objects.isEmpty()) {
                                Text("No hay objetos disponibles en la biblioteca.", fontSize = 12.sp, color = Color(0xFF667085))
                            } else {
                                val sortedObjects = objects.sortedBy { it.metadata.name.lowercase() }
                                val selectedNames = sortedObjects
                                    .filter { it.folderName in requiredAccessObjects }
                                    .map { it.metadata.name }
                                OutlinedButton(
                                    onClick = { showAccessObjectsDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Seleccionar objetos (${requiredAccessObjects.size})")
                                }
                                Text(
                                    if (selectedNames.isEmpty()) "No se requieren objetos para iniciar."
                                    else selectedNames.joinToString(", "),
                                    fontSize = 12.sp,
                                    color = Color(0xFF667085)
                                )
                                if (showAccessObjectsDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showAccessObjectsDialog = false },
                                        title = { Text("Requisitos para iniciar") },
                                        text = {
                                            Column(
                                                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                                            ) {
                                                sortedObjects.forEach { storedObject ->
                                                    Row(
                                                        Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = storedObject.folderName in requiredAccessObjects,
                                                            onCheckedChange = { checked ->
                                                                requiredAccessObjects = if (checked) {
                                                                    (requiredAccessObjects + storedObject.folderName).distinct()
                                                                } else {
                                                                    requiredAccessObjects - storedObject.folderName
                                                                }
                                                            }
                                                        )
                                                        Text(storedObject.metadata.name)
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { showAccessObjectsDialog = false }) {
                                                Text("Aceptar")
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showAccessObjectsDialog = false }) {
                                                Text("Cerrar")
                                            }
                                        }
                                    )
                                }
                            }
                        }
            Column {
                            OutlinedButton(onClick = {
                                onPickCover()
                            }, modifier = Modifier.fillMaxWidth()) { Text(if (cover == null) "＋ Añadir portada" else "Cambiar portada") }
                            val coverFile = if (cover != null && editingFolder != null && editingCollectionFolder != null)
                                File(File(File(StoryRepository.rootDirectory(context), editingCollectionFolder!!), editingFolder!!), cover!!)
                            else null
                            StoryImagePreview(coverFile, "Vista previa de portada", coverRevision)
                        }
            Column { Spacer(Modifier.height(8.dp)); Text("Estructura del cuento", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Column { Text("Pulsa una escena para editar su contenido. El resumen de ilustración ayudará a identificarla en este mapa.", fontSize = 13.sp, color = Color(0xFF667085)) }
                        scenes.forEachIndexed { index, sceneName ->
                            val sceneFolder = runCatching { StoryRepository.sceneFolderName(sceneName) }.getOrNull()
                            val currentStoredStory = storedStories.firstOrNull {
                                it.folderName == editingFolder && it.collection == editingCollectionFolder
                            }
                            val sceneSummary = if (sceneName == selectedScene) {
                                summary
                            } else if (sceneFolder != null && currentStoredStory != null) {
                                runCatching { StoryRepository.readScene(currentStoredStory, sceneFolder).metadata.illustrationSummary }.getOrNull().orEmpty()
                            } else {
                                ""
                            }
                            Card(Modifier.fillMaxWidth().clickable {
                                selectedScene = sceneName
                                val folder = sceneFolder
                                val stored = currentStoredStory
                                val savedScene = if (folder != null && stored != null) runCatching { StoryRepository.readScene(stored, folder).metadata }.getOrNull() else null
                                summary = savedScene?.illustrationSummary.orEmpty()
                                sceneText = savedScene?.text.orEmpty()
                                state.loadDecisionForScene(sceneName, savedScene)
                                illustrations = illustrations.toMutableMap().apply { put(sceneName, savedScene?.illustration) }
                                ambientAudio = ambientAudio.toMutableMap().apply { put(sceneName, savedScene?.ambientAudio) }
                                narrationAudio = narrationAudio.toMutableMap().apply { put(sceneName, savedScene?.narrationAudio) }
                                objectTiming = if (savedScene?.awardTiming == "onEnter") "Al entrar" else "Al salir"
                                awardedObjectByScene = awardedObjectByScene.toMutableMap().apply { put(sceneName, savedScene?.awardedObject) }
                                state.awardedObjectsByScene.value = state.awardedObjectsByScene.value.toMutableMap().apply {
                                    put(
                                        sceneName,
                                        (listOfNotNull(savedScene?.awardedObject) + savedScene?.awardedObjects.orEmpty()).distinct()
                                    )
                                }
                                state.unlockedObjectsByScene.value = state.unlockedObjectsByScene.value.toMutableMap().apply {
                                    put(sceneName, savedScene?.unlockedObjects.orEmpty())
                                }
                                keepAmbientByScene = keepAmbientByScene.toMutableMap().apply {
                                    put(sceneName, savedScene?.keepPreviousAmbient ?: false)
                                }
                                onOpenScene(sceneName)
                            }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE5))) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("📖", fontSize = 22.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(sceneName, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            sceneSummary.ifBlank { "Sin resumen de ilustración" },
                                            fontSize = 12.sp,
                                            color = if (sceneSummary.isBlank()) Color(0xFF98A2B3) else Color(0xFF667085),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedButton(onClick = { storyScenePendingDeletion = sceneName }) { Text("Eliminar") }
                                    Text("›", fontSize = 24.sp, color = Color(0xFF87451F))
                                }
                            }
                        }
            Column {
                            OutlinedButton(onClick = { scenes = scenes + "Escena ${scenes.size + 1}" }, modifier = Modifier.fillMaxWidth()) { Text("＋ Añadir escena") }
                        }
                        if (storyScenePendingDeletion != null) {
                            Column {
                                val sceneToDelete = storyScenePendingDeletion!!
                                AlertDialog(
                                    onDismissRequest = { storyScenePendingDeletion = null },
                                    title = { Text("Eliminar escena") },
                                    text = { Text("¿Quieres eliminar definitivamente «$sceneToDelete»? Se borrarán su ilustración, sus audios y el resto de archivos propios. Las escenas que apuntaban a ella conservarán una salida HUÉRFANA para que puedas repararla en la estructura narrativa.") },
                                    confirmButton = {
                                        Button(onClick = {
                                            if (state.removeScene(sceneToDelete)) {
                                                message = "Escena eliminada del borrador. Guarda el cuento para borrar definitivamente sus archivos."
                                            }
                                            storyScenePendingDeletion = null
                                        }) { Text("Eliminar definitivamente") }
                                    },
                                    dismissButton = { OutlinedButton(onClick = { storyScenePendingDeletion = null }) { Text("Cancelar") } }
                                )
                            }
                        }
            Column {
                            OutlinedButton(
                                onClick = { onOpenStructure() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("⑂ Editar estructura narrativa") }
                            Text("Configura las conexiones entre escenas, las bifurcaciones y sus condiciones por objeto.", fontSize = 12.sp, color = Color(0xFF667085))
                        }
            Column {
                            Button(onClick = {
                                if (state.persist() != null) onBackToLibrary()
                            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar cuento") }
                        }
                        if (message.isNotBlank()) Column {
                            Text(message, color = if (message == "Cuento guardado.") Color(0xFF24733B) else Color(0xFFB3261E))
                        }
            Column {
                            OutlinedButton(onClick = { onBackToLibrary() }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                        }
                    
    }
}
