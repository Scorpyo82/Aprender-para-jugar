package es.aprenderparajugar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Edición de una escena fuera de AprenderApp. El contenedor padre proporciona
 * el desplazamiento vertical; este componente no crea otra LazyColumn.
 */
@Composable
internal fun StorySceneEditorContent(
    state: StoryEditorState,
    storyAmbientDuringNarration: Int,
    onPickIllustration: () -> Unit,
    onPickAmbient: () -> Unit,
    onPickNarration: () -> Unit,
    onBackToMap: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedScene by state.selectedScene
    var summary by state.summary
    var sceneText by state.sceneText
    var illustrations by state.illustrations
    var ambientAudio by state.ambientAudio
    var narrationAudio by state.narrationAudio
    var keepAmbientByScene by state.keepAmbientByScene
    var awardedObjectsByScene by state.awardedObjectsByScene
    var unlockedObjectsByScene by state.unlockedObjectsByScene
    var objectTiming by state.objectTiming
    var message by state.message
    var editingFolder by state.editingFolder
    var editingCollectionFolder by state.editingCollectionFolder
    var storedStoryObjects by remember { mutableStateOf(ObjectRepository.list(context)) }
    var showAwardedObjectsDialog by remember { mutableStateOf(false) }
    var showUnlockedObjectsDialog by remember { mutableStateOf(false) }
    var storySceneTest by remember { mutableStateOf<StoredStoryScene?>(null) }
    var storySceneTestAmbientOverride by remember { mutableStateOf(false) }
    var storySceneTestAmbientFile by remember { mutableStateOf<File?>(null) }
    var storyScenePendingTest by remember { mutableStateOf<StoredStoryScene?>(null) }
    var storyAmbientSourceCandidates by remember { mutableStateOf<List<StoredStoryScene>>(emptyList()) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Column { Text(selectedScene.ifBlank { "Editar escena" }, fontSize = 23.sp, fontWeight = FontWeight.Bold) }
            Column {
                            OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text("Resumen de ilustración") }, supportingText = { Text("Descripción breve que aparecerá en la tarjeta y en el mapa conceptual.") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        }
            Column {
                            OutlinedTextField(value = sceneText, onValueChange = { sceneText = it }, label = { Text("Texto de la escena") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                            if (sceneText.length > 120) {
                                Text("⚠️ La narración puede superar las 3 líneas recomendadas. No es un límite: el texto podrá desplazarse en la lectura.", color = Color(0xFF9A5B00), fontSize = 12.sp)
                            }
                        }
            Column { StoryDecisionEditor(state, selectedScene) }
            Column {
                            OutlinedButton(onClick = {
                                onPickIllustration()
                            }, modifier = Modifier.fillMaxWidth()) { Text(if (illustrations[selectedScene] == null) "＋ Añadir ilustración" else "Cambiar ilustración") }
                            val illustrationFile = if (illustrations[selectedScene] != null && editingFolder != null && editingCollectionFolder != null)
                                File(File(File(File(StoryRepository.rootDirectory(context), editingCollectionFolder!!), editingFolder!!), "escenas"), StoryRepository.sceneFolderName(selectedScene)).let { File(it, illustrations[selectedScene]!!) }
                            else null
                            StoryImagePreview(illustrationFile, "Vista previa de ilustración")
                        }
            Column {
                            val keepPreviousAmbient = keepAmbientByScene[selectedScene] ?: false
                            val ambientFile = if (!keepPreviousAmbient && ambientAudio[selectedScene] != null && editingFolder != null && editingCollectionFolder != null)
                                File(File(File(File(StoryRepository.rootDirectory(context), editingCollectionFolder!!), editingFolder!!), "escenas"), StoryRepository.sceneFolderName(selectedScene)).let { File(it, ambientAudio[selectedScene]!!) }
                            else null
                            OutlinedButton(
                                onClick = {
                                    onPickAmbient()
                                },
                                enabled = !keepPreviousAmbient,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (keepPreviousAmbient) "Audio ambiental deshabilitado" else if (ambientFile == null) "♫ Elegir audio ambiental" else "Cambiar audio ambiental") }
                            if (!keepPreviousAmbient) {
                                StoryAudioPreview(ambientFile, "Audio ambiental")
                                if (ambientFile != null) {
                                    OutlinedButton(
                                        onClick = {
                                            ambientAudio = ambientAudio.toMutableMap().apply { put(selectedScene, null) }
                                            ambientFile.delete()
                                            state.persist()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Eliminar audio ambiental") }
                                }
                            } else {
                                Text("Se conservará el sonido de la escena anterior. El audio asignado a esta escena se ignorará.", fontSize = 12.sp, color = Color(0xFF667085))
                            }
                        }
            Column {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Mantener sonido ambiental de escena anterior", fontWeight = FontWeight.Medium)
                                    Text("Tiene prioridad sobre cualquier audio ambiental asignado a esta escena.", fontSize = 12.sp, color = Color(0xFF667085))
                                }
                                Switch(
                                    checked = keepAmbientByScene[selectedScene] ?: false,
                                    onCheckedChange = { enabled ->
                                        keepAmbientByScene = keepAmbientByScene.toMutableMap().apply {
                                            put(selectedScene, enabled)
                                        }
                                        state.persist()
                                    }
                                )
                            }
                        }
            Column {
                            val narrationFile = if (narrationAudio[selectedScene] != null && editingFolder != null && editingCollectionFolder != null)
                                File(File(File(File(StoryRepository.rootDirectory(context), editingCollectionFolder!!), editingFolder!!), "escenas"), StoryRepository.sceneFolderName(selectedScene)).let { File(it, narrationAudio[selectedScene]!!) }
                            else null
                            OutlinedButton(onClick = {
                                onPickNarration()
                            }, modifier = Modifier.fillMaxWidth()) { Text(if (narrationFile == null) "♫ Elegir audio de narración" else "Cambiar audio de narración") }
                            StoryAudioPreview(narrationFile, "Audio de narración")
                            if (narrationFile != null) {
                                OutlinedButton(
                                    onClick = {
                                        narrationAudio = narrationAudio.toMutableMap().apply { put(selectedScene, null) }
                                        narrationFile.delete()
                                        state.persist()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Eliminar audio de narración") }
                            }
                        }

            Column {
                            val awardedFolders = awardedObjectsByScene[selectedScene].orEmpty()
                            val unlockedFolders = unlockedObjectsByScene[selectedScene].orEmpty()
                            val namesByFolder = storedStoryObjects.associateBy { it.folderName }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Objetos que recibe el niño", fontWeight = FontWeight.SemiBold)
                                Text(if (awardedFolders.isEmpty()) "Ningún objeto adicional seleccionado." else awardedFolders.mapNotNull { namesByFolder[it]?.metadata?.name }.joinToString(", "), fontSize = 12.sp, color = Color(0xFF667085))
                                OutlinedButton(onClick = { storedStoryObjects = ObjectRepository.list(context); showAwardedObjectsDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Seleccionar objetos para añadir al inventario (${awardedFolders.size})") }
                                Text("Objetos que se desbloquean", fontWeight = FontWeight.SemiBold)
                                Text(if (unlockedFolders.isEmpty()) "Ningún objeto seleccionado." else unlockedFolders.mapNotNull { namesByFolder[it]?.metadata?.name }.joinToString(", "), fontSize = 12.sp, color = Color(0xFF667085))
                                OutlinedButton(onClick = { storedStoryObjects = ObjectRepository.list(context); showUnlockedObjectsDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Desbloquear objetos (${unlockedFolders.size})") }
                            }
                            if (showAwardedObjectsDialog) {
                                AlertDialog(
                                    onDismissRequest = { showAwardedObjectsDialog = false },
                                    title = { Text("Objetos que recibe el niño") },
                                    text = {
                                        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                            storedStoryObjects.forEach { obj ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(checked = obj.folderName in awardedFolders, onCheckedChange = { checked ->
                                                        awardedObjectsByScene = awardedObjectsByScene.toMutableMap().apply {
                                                            put(selectedScene, if (checked) (awardedFolders + obj.folderName).distinct() else awardedFolders - obj.folderName)
                                                        }
                                                    })
                                                    Text(obj.metadata.name + " · " + obj.collection)
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = { TextButton(onClick = { showAwardedObjectsDialog = false }) { Text("Aceptar") } },
                                    dismissButton = { TextButton(onClick = { showAwardedObjectsDialog = false }) { Text("Cerrar") } }
                                )
                            }
                            if (showUnlockedObjectsDialog) {
                                AlertDialog(
                                    onDismissRequest = { showUnlockedObjectsDialog = false },
                                    title = { Text("Desbloquear objetos") },
                                    text = {
                                        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                            storedStoryObjects.forEach { obj ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(checked = obj.metadata.availableInStore && obj.folderName in unlockedFolders, enabled = obj.metadata.availableInStore, onCheckedChange = { checked ->
                                                        unlockedObjectsByScene = unlockedObjectsByScene.toMutableMap().apply {
                                                            put(selectedScene, if (checked && obj.metadata.availableInStore) (unlockedFolders + obj.folderName).distinct() else unlockedFolders - obj.folderName)
                                                        }
                                                    })
                                                    Column {
                                                        Text(obj.metadata.name)
                                                        Text(if (obj.metadata.availableInStore) "A la venta" else "No está a la venta · No se puede seleccionar", fontSize = 11.sp, color = Color(0xFF667085))
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = { TextButton(onClick = { showUnlockedObjectsDialog = false }) { Text("Aceptar") } },
                                    dismissButton = { TextButton(onClick = { showUnlockedObjectsDialog = false }) { Text("Cerrar") } }
                                )
                            }
                        }

            Column {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val stored = state.persist() ?: error(message.ifBlank { "Guarda primero el cuento." })
                                        val folder = StoryRepository.sceneFolderName(selectedScene)
                                        val targetScene = StoryRepository.readScene(stored, folder)
                                        if (targetScene.metadata.keepPreviousAmbient) {
                                            val candidates = StoryRepository.findAmbientSourceCandidates(stored, folder)
                                            when {
                                                candidates.size > 1 -> {
                                                    storyScenePendingTest = targetScene
                                                    storyAmbientSourceCandidates = candidates
                                                }
                                                candidates.size == 1 -> {
                                                    val source = candidates.single()
                                                    storySceneTestAmbientFile = source.metadata.ambientAudio
                                                        ?.let { File(source.directory, it) }?.takeIf { it.isFile }
                                                    storySceneTestAmbientOverride = true
                                                    storySceneTest = targetScene
                                                }
                                                else -> {
                                                    storySceneTestAmbientFile = null
                                                    storySceneTestAmbientOverride = true
                                                    storySceneTest = targetScene
                                                }
                                            }
                                        } else {
                                            storySceneTestAmbientFile = null
                                            storySceneTestAmbientOverride = false
                                            storySceneTest = targetScene
                                        }
                                    }.onFailure { message = it.message ?: "No se pudo iniciar la prueba." }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("▶ Probar escena") }
                            Text("Reproduce la escena sin permitir interacciones.", fontSize = 12.sp, color = Color(0xFF667085))
                        }
            Column {
                            if (storySceneTest != null) StorySceneTestDialog(
                                scene = storySceneTest!!,
                                ambientDuringNarrationPercent = storyAmbientDuringNarration,
                                ambientAudioOverride = storySceneTestAmbientFile,
                                useAmbientAudioOverride = storySceneTestAmbientOverride,
                                onExit = {
                                    storySceneTest = null
                                    storySceneTestAmbientFile = null
                                    storySceneTestAmbientOverride = false
                                }
                            )
                            if (storyScenePendingTest != null) StoryAmbientSourceDialog(
                                candidates = storyAmbientSourceCandidates,
                                onSelect = { source ->
                                    storySceneTestAmbientFile = source.metadata.ambientAudio
                                        ?.let { File(source.directory, it) }?.takeIf { it.isFile }
                                    storySceneTestAmbientOverride = true
                                    storySceneTest = storyScenePendingTest
                                    storyScenePendingTest = null
                                    storyAmbientSourceCandidates = emptyList()
                                },
                                onDismiss = {
                                    storyScenePendingTest = null
                                    storyAmbientSourceCandidates = emptyList()
                                }
                            )
                        }
            Column {
                            Text("Momento de obtención", fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = objectTiming == "Al entrar", onClick = { objectTiming = "Al entrar" }, label = { Text("Al entrar") })
                                FilterChip(selected = objectTiming == "Al salir", onClick = { objectTiming = "Al salir" }, label = { Text("Al salir") })
                            }
                        }
            Column {
                            Button(onClick = {
                                if (state.persist() != null) onBackToMap()
                            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar escena y volver al mapa") }
                        }
                    
    }
}
