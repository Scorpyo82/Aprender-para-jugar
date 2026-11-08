package es.aprenderparajugar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
    storedStoryObjects: List<StoredStoryObject>,
    onPickCover: () -> Unit,
    onOpenScene: (String) -> Unit,
    onOpenStructure: () -> Unit,
    onBackToLibrary: () -> Unit
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
                                        storyCollections.forEach { name ->
                                            DropdownMenuItem(text = { Text(name) }, onClick = { collection = name; collectionMenuExpanded = false })
                                        }
                                    }
                                }
                                if (storyCollections.isEmpty()) Text("Todavía no hay colecciones de cuentos.", fontSize = 12.sp, color = Color(0xFF667085))
                            }
                        }
            Column {
                            OutlinedButton(onClick = {
                                onPickCover()
                            }, modifier = Modifier.fillMaxWidth()) { Text(if (cover == null) "＋ Añadir portada" else "Cambiar portada") }
                            val coverFile = if (cover != null && editingFolder != null && editingCollectionFolder != null)
                                File(File(File(StoryRepository.rootDirectory(context), editingCollectionFolder!!), editingFolder!!), cover!!)
                            else null
                            StoryImagePreview(coverFile, "Vista previa de portada")
                        }
            Column { Spacer(Modifier.height(8.dp)); Text("Estructura del cuento", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Column { Text("Pulsa una escena para editar su contenido. El resumen de ilustración ayudará a identificarla en este mapa.", fontSize = 13.sp, color = Color(0xFF667085)) }
                        items(scenes.size) { index ->
                            val sceneName = scenes[index]
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
                                storyEditorState.loadDecisionForScene(sceneName, savedScene)
                                illustrations = illustrations.toMutableMap().apply { put(sceneName, savedScene?.illustration) }
                                ambientAudio = ambientAudio.toMutableMap().apply { put(sceneName, savedScene?.ambientAudio) }
                                narrationAudio = narrationAudio.toMutableMap().apply { put(sceneName, savedScene?.narrationAudio) }
                                objectTiming = if (savedScene?.awardTiming == "onEnter") "Al entrar" else "Al salir"
                                awardedObjectByScene = awardedObjectByScene.toMutableMap().apply { put(sceneName, savedScene?.awardedObject) }
                                storedStoryObjects = ObjectRepository.list(context)
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
                            item {
                                val sceneToDelete = storyScenePendingDeletion!!
                                AlertDialog(
                                    onDismissRequest = { storyScenePendingDeletion = null },
                                    title = { Text("Eliminar escena") },
                                    text = { Text("¿Quieres eliminar definitivamente «$sceneToDelete»? Se borrarán su ilustración, sus audios y el resto de archivos propios. Las escenas que apuntaban a ella conservarán una salida HUÉRFANA para que puedas repararla en la estructura narrativa.") },
                                    confirmButton = {
                                        Button(onClick = {
                                            if (storyEditorState.removeScene(sceneToDelete)) {
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
                        if (message.isNotBlank()) item {
                            Text(message, color = if (message == "Cuento guardado.") Color(0xFF24733B) else Color(0xFFB3261E))
                        }
            Column {
                            OutlinedButton(onClick = { onBackToLibrary() }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                        }
                    
    }
}
