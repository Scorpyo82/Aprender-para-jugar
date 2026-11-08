package es.aprenderparajugar

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.net.Uri
import android.widget.ImageView
import android.media.MediaPlayer
import android.view.ViewGroup
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.Calendar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    // Start the earned-time countdown at the first lifecycle transition away from the app.
    // onPause is earlier than onStop and closes the race with the accessibility service/Home.
    private fun persistAndStartEarnedCountdown() {
        val prefs = getSharedPreferences("aprender_para_jugar", MODE_PRIVATE)
        if (prefs.getString(RewardMode.PREFERENCE_KEY, "time") == "stars") return
        if (prefs.getBoolean("device_setup_in_progress", false) ||
            prefs.getBoolean("parent_area_active", false) ||
            prefs.getBoolean("manual_exit_allowed", false) ||
            prefs.getBoolean("play_paused_for_screen_lock", false)) return

        val now = System.currentTimeMillis()
        val deadline = prefs.getLong("play_deadline_ms", 0L)
        val remaining = if (deadline > now) {
            ((deadline - now + 999L) / 1000L).toInt().coerceAtLeast(0)
        } else {
            prefs.getInt("balance_seconds", 0).coerceAtLeast(0)
        }
        val sessionAuthorized = prefs.getBoolean("play_session_authorized", false)
        val minimumSeconds = prefs.getInt("minimum_play_minutes", 5).coerceIn(0, 60) * 60

        // The minimum is checked only when creating a new session. Once authorized,
        // returning to the app does not revoke permission to leave again with the remainder.
        if (remaining <= 0) {
            prefs.edit()
                .putInt("balance_seconds", 0)
                .putLong("play_deadline_ms", 0L)
                .putBoolean("play_session_authorized", false)
                .putBoolean("play_paused_for_screen_lock", false)
                .commit()
            return
        }
        if (!sessionAuthorized && remaining < minimumSeconds) return

        val nextDeadline = if (deadline > now) deadline else now + remaining.toLong() * 1000L
        // Persist authorization and deadline together so lifecycle and Accessibility
        // cannot observe a running session without its authorization flag.
        prefs.edit()
            .putInt("balance_seconds", remaining)
            .putLong("play_deadline_ms", nextDeadline)
            .putLong("expiry_last_launch_ms", 0L)
            .putBoolean("play_session_authorized", true)
            .putBoolean("play_paused_for_screen_lock", false)
            .commit()

        try {
            androidx.core.content.ContextCompat.startForegroundService(
                this,
                Intent(this, PlayTimeService::class.java).setAction(PlayTimeService.ACTION_START)
            )
        } catch (_: Exception) { }
    }

    // Home/gesto de inicio: asegura que el saldo ganado empiece a consumirse
    // aunque el usuario abandone la pantalla de resultados o selección sin pulsar
    // ningún botón interno de la aplicación. La operación es idempotente.
    override fun onUserLeaveHint() {
        persistAndStartEarnedCountdown()
        super.onUserLeaveHint()
    }

    override fun onPause() {
        persistAndStartEarnedCountdown()
        super.onPause()
    }

    override fun onStop() {
        persistAndStartEarnedCountdown()
        val prefs = getSharedPreferences("aprender_para_jugar", MODE_PRIVATE)
        // Android's document picker temporarily backgrounds this Activity; do not
        // interpret that expected system UI as leaving the app's parental area.
        if (!prefs.getBoolean("content_picker_active", false) && !prefs.getBoolean("device_setup_in_progress", false)) {
            prefs.edit().putBoolean("parent_area_active", false).apply()
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inicializa las raíces privadas para las bibliotecas de contenido.
        ContentStorage.initialize(this)
        // A manual launch re-arms the focus guard after a parent-authorized exit.
        getSharedPreferences("aprender_para_jugar", MODE_PRIVATE)
            .edit().putBoolean("manual_exit_allowed", false).apply()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFFF8E8)) {
                    AprenderApp()
                }
            }
        }
    }
}

internal data class PairItem(val word: String, val picture: String, val category: String = "Primeras palabras", val difficulty: String = "Fácil", val active: Boolean = true, val directory: File? = null, val createdAt: String = "")

internal data class SequenceExercise(
    val title: String,
    val cards: List<String>,
    val format: String,
    val category: String,
    val alternatives: List<String> = emptyList(),
    val pictures: List<String> = emptyList(),
    val type: String = "Orden temporal",
    val difficulty: String = "Normal",
    val active: Boolean = true,
    val directory: File? = null,
    val createdAt: String = ""
)

private fun defaultSequencePicture(index: Int): String = ""

private fun loadSequenceExercises(context: Context): List<SequenceExercise> =
    ExerciseUiStorage.loadSequences(context)

private fun saveSequenceExercises(context: Context, exercises: List<SequenceExercise>): List<SequenceExercise> =
    ExerciseUiStorage.saveSequences(context, exercises)
internal data class WordLearning(
    val streak: Int = 0,
    val lastShownTurn: Int = 0,
    val lastCorrect: Boolean = true
)

private fun loadLearningStats(preferences: android.content.SharedPreferences): Map<String, WordLearning> {
    val saved = preferences.getString("word_learning_v1", null) ?: return emptyMap()
    return saved.split("\n").mapNotNull { line ->
        val fields = line.split("\t")
        if (fields.size < 4) null else try {
            fields[0] to WordLearning(fields[1].toInt(), fields[2].toInt(), fields[3].toBoolean())
        } catch (_: Exception) { null }
    }.toMap()
}

private fun saveLearningStats(preferences: android.content.SharedPreferences, stats: Map<String, WordLearning>) {
    val saved = stats.entries.joinToString("\n") { (word, state) ->
        listOf(word, state.streak.toString(), state.lastShownTurn.toString(), state.lastCorrect.toString()).joinToString("\t")
    }
    preferences.edit().putString("word_learning_v1", saved).apply()
}

private fun chooseAdaptivePairs(
    candidates: List<PairItem>,
    count: Int,
    stats: Map<String, WordLearning>,
    turn: Int
): List<PairItem> {
    return candidates.shuffled().sortedBy { pair ->
        val state = stats[pair.word] ?: WordLearning()
        val elapsed = turn - state.lastShownTurn
        when {
            state.lastShownTurn == 0 -> -1000
            !state.lastCorrect && elapsed >= 3 -> -500
            !state.lastCorrect -> -200 + elapsed
            elapsed >= 5 + state.streak * 5 -> -100
            else -> elapsed
        }
    }.take(count)
}


private fun exportContentPackage(context: Context, target: Uri, pairs: List<PairItem>, categories: List<String>, format: String = "aprender-para-jugar-content") {
    val resolver = context.contentResolver
    ZipOutputStream(resolver.openOutputStream(target)?.buffered() ?: throw IllegalArgumentException("No se puede escribir en el destino.")).use { zip ->
        val root = JSONObject()
        root.put("format", format)
        root.put("version", 1)
        root.put("categories", JSONArray(categories))
        val words = JSONArray()
        pairs.forEachIndexed { index, pair ->
            val item = JSONObject()
            item.put("word", pair.word)
            item.put("category", pair.category)
            item.put("difficulty", pair.difficulty)
            item.put("active", pair.active)
            val uri = Uri.parse(pair.picture)
            val imageName = if (pair.picture.startsWith("content://") || pair.picture.startsWith("file://")) {
                val extension = when (resolver.getType(uri)?.lowercase()) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    else -> "jpg"
                }
                val name = "images/${index}_image.$extension"
                val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                if (bytes != null) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                    name
                } else null
            } else null
            if (imageName != null) item.put("image", imageName) else item.put("picture", pair.picture)
            words.put(item)
        }
        root.put("words", words)
        zip.putNextEntry(ZipEntry("content.json"))
        zip.write(root.toString(2).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}

private data class ImportedContent(val categories: List<String>, val pairs: List<PairItem>)

private fun importContentPackage(context: Context, source: Uri, expectedFormat: String = "aprender-para-jugar-content"): ImportedContent {
    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(context.contentResolver.openInputStream(source)?.buffered() ?: throw IllegalArgumentException("No se puede leer el archivo seleccionado.")).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory) {
                val out = ByteArrayOutputStream()
                zip.copyTo(out)
                entries[entry.name] = out.toByteArray()
            }
            zip.closeEntry()
        }
    }
    val jsonBytes = entries["content.json"] ?: throw IllegalArgumentException("El ZIP no contiene content.json.")
    val root = JSONObject(String(jsonBytes, Charsets.UTF_8))
    require(root.optString("format") == expectedFormat) { "El archivo no corresponde a esta biblioteca de ejercicios." }
    val categoriesJson = root.optJSONArray("categories") ?: JSONArray()
    val categories = (0 until categoriesJson.length()).map { categoriesJson.getString(it) }.filter { it.isNotBlank() }
    val words = root.optJSONArray("words") ?: JSONArray()
    val importedPairs = (0 until words.length()).mapNotNull { index ->
        val item = words.optJSONObject(index) ?: return@mapNotNull null
        val word = item.optString("word").trim()
        if (word.isBlank()) return@mapNotNull null
        val imageName = item.optString("image")
        val picture = if (imageName.isNotBlank()) {
            val bytes = entries[imageName] ?: throw IllegalArgumentException("Falta la imagen $imageName.")
            val extension = imageName.substringAfterLast('.', "jpg").filter { it.isLetterOrDigit() }.ifBlank { "jpg" }
            val file = java.io.File(context.filesDir, "imported_${word.hashCode()}.$extension")
            file.writeBytes(bytes)
            Uri.fromFile(file).toString()
        } else item.optString("picture", "❔")
        PairItem(word, picture, item.optString("category", "Primeras palabras"), item.optString("difficulty", "Fácil"), item.optBoolean("active", true))
    }
    require(importedPairs.isNotEmpty()) { "El archivo no contiene palabras válidas." }
    return ImportedContent(categories, importedPairs)
}

private data class ImportedSequenceContent(
    val categories: List<String>,
    val disabledCategories: Set<String>,
    val exercises: List<SequenceExercise>
)

private fun exportSequencePackage(
    context: Context,
    target: Uri,
    exercises: List<SequenceExercise>,
    categories: List<String>,
    disabledCategories: Set<String>
) {
    val resolver = context.contentResolver
    ZipOutputStream(resolver.openOutputStream(target)?.buffered() ?: throw IllegalArgumentException("No se puede escribir en el destino.")).use { zip ->
        val root = JSONObject().apply {
            put("format", "aprender-para-jugar-sequences")
            put("version", 1)
            put("categories", JSONArray(categories))
            put("disabledCategories", JSONArray(disabledCategories.toList()))
        }
        val exerciseArray = JSONArray()
        exercises.forEachIndexed { exerciseIndex, exercise ->
            val item = JSONObject().apply {
                put("title", exercise.title)
                put("format", exercise.format)
                put("category", exercise.category)
                put("type", exercise.type)
                put("difficulty", exercise.difficulty)
                put("active", exercise.active)
                put("cards", JSONArray(exercise.cards))
                put("alternatives", JSONArray(exercise.alternatives))
            }
            val pictures = JSONArray()
            exercise.pictures.forEachIndexed { pictureIndex, picture ->
                val uri = Uri.parse(picture)
                val imageName = if (picture.startsWith("content://") || picture.startsWith("file://")) {
                    val extension = when (resolver.getType(uri)?.lowercase()) {
                        "image/png" -> "png"
                        "image/webp" -> "webp"
                        "image/gif" -> "gif"
                        else -> "jpg"
                    }
                    val name = "images/exercise_${exerciseIndex}_picture_${pictureIndex}.$extension"
                    val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    if (bytes != null) {
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(bytes)
                        zip.closeEntry()
                        name
                    } else null
                } else null
                pictures.put(if (imageName != null) JSONObject().put("image", imageName) else JSONObject().put("picture", picture))
            }
            item.put("pictures", pictures)
            exerciseArray.put(item)
        }
        root.put("exercises", exerciseArray)
        zip.putNextEntry(ZipEntry("sequences.json"))
        zip.write(root.toString(2).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}

private fun importSequencePackage(context: Context, source: Uri): ImportedSequenceContent {
    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(context.contentResolver.openInputStream(source)?.buffered() ?: throw IllegalArgumentException("No se puede leer el archivo seleccionado.")).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory) {
                val out = ByteArrayOutputStream()
                zip.copyTo(out)
                entries[entry.name] = out.toByteArray()
            }
            zip.closeEntry()
        }
    }
    val jsonBytes = entries["sequences.json"] ?: throw IllegalArgumentException("El ZIP no contiene sequences.json.")
    val root = JSONObject(String(jsonBytes, Charsets.UTF_8))
    require(root.optString("format") == "aprender-para-jugar-sequences") { "El archivo no es una copia de seguridad de secuencias." }
    val categoryJson = root.optJSONArray("categories") ?: JSONArray()
    val categories = (0 until categoryJson.length()).map { categoryJson.getString(it) }.filter { it.isNotBlank() }
    val disabledJson = root.optJSONArray("disabledCategories") ?: JSONArray()
    val disabled = (0 until disabledJson.length()).map { disabledJson.getString(it) }.toSet()
    val array = root.optJSONArray("exercises") ?: JSONArray()
    val exercises = (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val cardsJson = item.optJSONArray("cards") ?: JSONArray()
        val cards = (0 until cardsJson.length()).map { cardsJson.getString(it) }
        if (cards.size < 2) return@mapNotNull null
        val alternativesJson = item.optJSONArray("alternatives") ?: JSONArray()
        val alternatives = (0 until alternativesJson.length()).map { alternativesJson.getString(it) }
        val picturesJson = item.optJSONArray("pictures") ?: JSONArray()
        val pictures = (0 until picturesJson.length()).map { pictureIndex ->
            val picture = picturesJson.optJSONObject(pictureIndex) ?: return@map (picturesJson.optString(pictureIndex))
            val imageName = picture.optString("image")
            if (imageName.isNotBlank()) {
                val bytes = entries[imageName] ?: throw IllegalArgumentException("Falta una imagen de secuencia: $imageName.")
                val extension = imageName.substringAfterLast('.', "jpg").filter { it.isLetterOrDigit() }.ifBlank { "jpg" }
                val file = File(context.filesDir, "imported_sequence_${index}_${pictureIndex}.$extension")
                file.writeBytes(bytes)
                Uri.fromFile(file).toString()
            } else picture.optString("picture", "❔")
        }
        SequenceExercise(
            item.optString("title", "Ejercicio"),
            cards,
            item.optString("format", "Imagen y texto"),
            item.optString("category", "Sin categoría"),
            alternatives,
            pictures,
            item.optString("type", "Orden temporal"),
            item.optString("difficulty", "Normal"),
            item.optBoolean("active", true)
        )
    }
    require(exercises.isNotEmpty()) { "El archivo no contiene ejercicios de secuencias válidos." }
    return ImportedSequenceContent(categories, disabled, exercises)
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = android.content.ComponentName(context, PlayTimeAccessibilityService::class.java)
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager ?: return false
    return manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { info -> info.resolveInfo.serviceInfo.packageName == expected.packageName && info.resolveInfo.serviceInfo.name == expected.className }
}

private fun currentDayKey(): String {
    val calendar = Calendar.getInstance()
    return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
}

@Composable
internal fun StoryImagePreview(file: File?, label: String) {
    val bitmap = remember(file?.absolutePath, file?.lastModified()) {
        file?.takeIf { it.isFile && it.length() > 0L }?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, Color(0xFFD8D0C7), RoundedCornerShape(12.dp))
            .background(Color(0xFFFFFDF8), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF667085))
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = label,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Fit)
        } else {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (file == null) "Aquí aparecerá la vista previa de la imagen." else "No se ha podido cargar la imagen seleccionada.",
                    color = if (file == null) Color(0xFF667085) else Color(0xFFB3261E),
                    textAlign = TextAlign.Center
                )
            }
        }
        if (file != null) Text(file.name, fontSize = 11.sp, color = Color(0xFF667085))
    }
}

@Composable
internal fun StoryAudioPreview(file: File?, label: String) {
    val context = LocalContext.current
    val player = remember(file?.absolutePath, file?.lastModified()) {
        file?.takeIf { it.isFile && it.length() > 0L }?.let { runCatching { MediaPlayer.create(context, Uri.fromFile(it)) }.getOrNull() }
    }
    var playing by remember(file?.absolutePath) { mutableStateOf(false) }
    DisposableEffect(player) {
        player?.setOnCompletionListener { playing = false }
        onDispose {
            runCatching { if (player?.isPlaying == true) player.pause() }
            player?.release()
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, Color(0xFFD8D0C7), RoundedCornerShape(12.dp))
            .background(Color(0xFFFFFDF8), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF667085))
        Text(file?.name ?: "Todavía no hay ningún audio seleccionado.", fontSize = 12.sp, color = Color(0xFF667085))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                player?.let {
                    if (it.isPlaying) { it.pause(); playing = false }
                    else { if (it.currentPosition >= it.duration) it.seekTo(0); it.start(); playing = true }
                }
            }, enabled = player != null) { Text(if (playing) "Pausar" else "Reproducir") }
            OutlinedButton(onClick = {
                player?.let {
                    if (it.isPlaying) it.pause()
                    it.seekTo(0)
                    playing = false
                }
            }, enabled = player != null) { Text("Detener") }
        }
        if (file != null && player == null) Text("No se pudo cargar este audio.", color = Color(0xFFB3261E), fontSize = 12.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AprenderApp() {
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val preferences = remember { context.getSharedPreferences("aprender_para_jugar", Context.MODE_PRIVATE) }
    var rewardMode by remember { mutableStateOf(RewardMode.fromPreferences(preferences)) }
    var expandedLearningModalities by remember { mutableStateOf(setOf<ContentStorage.Modality>()) }
    // No se borran datos del usuario al iniciar ni al actualizar la aplicación.
    var pairs by remember { mutableStateOf(ExerciseUiStorage.loadPairs(context, ContentStorage.Modality.TARJETAS)) }
    val wordLibraryState = remember(context, preferences) { WordLibraryState(context, preferences) }
    var wordExercises by wordLibraryState.exercises
    var gameActiveCards by remember { mutableStateOf(preferences.getBoolean("game_active_cards", true)) }
    var gameActiveSequences by remember { mutableStateOf(preferences.getBoolean("game_active_sequences", true)) }
    var gameActiveWords by remember { mutableStateOf(preferences.getBoolean("game_active_words", true)) }
    var gameActiveStories by remember { mutableStateOf(preferences.getBoolean("game_active_stories", true)) }
    var wordExerciseCategories by wordLibraryState.categories
    var disabledWordExerciseCategories by wordLibraryState.disabledCategories
    var wordLibraryPage by wordLibraryState.page
    var wordLibraryEditingIndex by wordLibraryState.editingIndex
    var wordLibraryWordDraft by wordLibraryState.wordDraft
    var wordLibraryPictureDraft by wordLibraryState.pictureDraft
    var wordLibraryCategoryDraft by wordLibraryState.categoryDraft
    var wordLibraryDifficultyDraft by wordLibraryState.difficultyDraft
    var wordLibraryMessage by wordLibraryState.message
    var wordLibraryCategoryMenuExpanded by wordLibraryState.categoryMenuExpanded
    var wordFilterCategory by wordLibraryState.filterCategory
    var wordFilterDifficulty by wordLibraryState.filterDifficulty
    var wordFilterStatus by wordLibraryState.filterStatus
    var wordPackageMessage by remember { mutableStateOf("") }
    var wordPendingImportMode by remember { mutableStateOf("replace") }
    var wordNewCategoryDraft by wordLibraryState.newCategoryDraft
    var wordCategoryToDelete by wordLibraryState.categoryToDelete
    var wordDraft by remember { mutableStateOf("") }
    var pictureDraft by remember { mutableStateOf("") }
    var categoryDraft by remember { mutableStateOf("") }
    var difficultyDraft by remember { mutableStateOf("Fácil") }
    var editingWord by remember { mutableStateOf<String?>(null) }
    var libraryMessage by remember { mutableStateOf("") }
    var libraryPage by remember { mutableStateOf("list") }
        val storyObjectEditorState = remember { StoryObjectEditorState() }
    var storyObjectNameDraft by storyObjectEditorState.nameDraft
    var storyObjectPictureDraft by storyObjectEditorState.pictureDraft
    var storyObjectEditingFolder by storyObjectEditorState.editingFolder
    var storyObjectImageChanged by storyObjectEditorState.imageChanged
    var storyObjectAvailableInStore by storyObjectEditorState.availableInStore
    var storyObjectRequiresUnlock by storyObjectEditorState.requiresUnlock
    var storyObjectStorePriceMinutes by storyObjectEditorState.storePriceMinutes
    var storyObjectCollectible by storyObjectEditorState.collectible
    var storyObjectMessage by storyObjectEditorState.message
    var storedStoryObjects by remember { mutableStateOf(emptyList<StoredObject>()) }
    val storyObjectInventoryState = remember(context) { StoryObjectInventoryState(context) }
    var storedInventoryFolders by storyObjectInventoryState.folders
    var storyInventoryMessage by remember { mutableStateOf("") }
    var storyObjectCollectionMenuExpanded by remember { mutableStateOf(false) }
    val storyObjectCollectionsState = remember(preferences) { StoryObjectCollectionsState(preferences) }
    var storyObjectCollections by storyObjectCollectionsState.collections
    var storyObjectCollectionDraft by storyObjectCollectionsState.draft
    var storyObjectSelectedCollection by storyObjectEditorState.selectedCollection
    var storyObjectCollectionMessage by storyObjectCollectionsState.message
    var storyObjectCollectionToDelete by storyObjectCollectionsState.collectionToDelete
    val storyCollectionsState = remember(preferences) { StoryCollectionsState(context, preferences) }
    var storyCollections by storyCollectionsState.collections
    var storyCollectionDraft by storyCollectionsState.draft
    var storyCollectionMessage by storyCollectionsState.message
    val storyEditorState = remember(context) { StoryEditorState(context) }
    var storyEditorTitle by storyEditorState.title
    var storyEditorCover by storyEditorState.cover
    var storyEditorIllustrations by storyEditorState.illustrations
    var storyEditorAmbientAudio by storyEditorState.ambientAudio
    var storyEditorNarrationAudio by storyEditorState.narrationAudio
    var editingStoryFolder by storyEditorState.editingFolder
    var editingStoryCollectionFolder by storyEditorState.editingCollectionFolder
    var storedStories by storyEditorState.storedStories
    var storyEditorMessage by storyEditorState.message
    var storyEditorCollection by storyEditorState.collection
    var storyEditorSummary by storyEditorState.summary
    var storyEditorScenes by storyEditorState.scenes
    var storyEditorSelectedScene by storyEditorState.selectedScene
    var storyScenePendingDeletion by remember { mutableStateOf<String?>(null) }
    var storyAmbientDuringNarration by remember { mutableIntStateOf(preferences.getInt("story_ambient_during_narration_percent", 20).coerceIn(0, 30)) }
    var storySceneTest by remember { mutableStateOf<StoredStoryScene?>(null) }
    var storySceneTestAmbientOverride by remember { mutableStateOf(false) }
    var storySceneTestAmbientFile by remember { mutableStateOf<File?>(null) }
    var storyScenePendingTest by remember { mutableStateOf<StoredStoryScene?>(null) }
    var storyAmbientSourceCandidates by remember { mutableStateOf<List<StoredStoryScene>>(emptyList()) }
    var storyEditorSceneText by storyEditorState.sceneText
    var storyEditorObjectTiming by storyEditorState.objectTiming
    var storyEditorAwardedObjectByScene by storyEditorState.awardedObjectByScene
    var storyEditorAwardedObjectsByScene by storyEditorState.awardedObjectsByScene
    var storyEditorUnlockedObjectsByScene by storyEditorState.unlockedObjectsByScene
    var showAwardedObjectsDialog by remember { mutableStateOf(false) }
    var showUnlockedObjectsDialog by remember { mutableStateOf(false) }
    var storyEditorObjectMenuExpanded by storyEditorState.objectMenuExpanded
    var storyEditorKeepAmbientByScene by storyEditorState.keepAmbientByScene
    var storyEditorCollectionMenuExpanded by storyEditorState.collectionMenuExpanded
    var storyCollectionToDelete by storyCollectionsState.collectionToDelete
    var disabledStoryCollections by storyCollectionsState.disabledCollections
    var pendingStoryCollectionCover by remember { mutableStateOf<String?>(null) }
    var storyCollectionCoverRevision by remember { mutableIntStateOf(0) }
    var editingStoryCollection by remember { mutableStateOf<String?>(null) }
    var storiesBoardTab by remember { mutableStateOf("all") }
    var selectedStoriesCollection by remember { mutableStateOf<String?>(null) }
    var selectedStoryPath by remember { mutableStateOf<String?>(null) }

    fun persistStoryDraft(): StoredStory? = storyEditorState.persist()
    val sequenceLibraryState = remember(context, preferences) { SequenceLibraryState(context, preferences) }
    var sequenceExercises by sequenceLibraryState.exercises
    var sequenceEditingIndex by remember { mutableIntStateOf(-1) }
    var sequenceTitleDraft by remember { mutableStateOf("") }
    var sequenceMaxCards by remember { mutableIntStateOf(preferences.getInt("sequence_max_cards_v1", 6).coerceIn(2, 6)) }
    var sequenceContentFormat by remember { mutableStateOf(preferences.getString("sequence_content_format_v1", "Imagen y texto") ?: "Imagen y texto") }
    var sequenceCategoryDraft by remember { mutableStateOf("") }
    var sequenceNewCategoryDraft by sequenceLibraryState.newCategoryDraft
    var sequenceCategoryMenuExpanded by remember { mutableStateOf(false) }
    var sequenceCategoryNames by sequenceLibraryState.categoryNames
    var sequenceCategoryMessage by sequenceLibraryState.categoryMessage
    var sequenceCategoryToDelete by sequenceLibraryState.categoryToDelete
    var sequenceTypeDraft by remember { mutableStateOf("Orden temporal") }
    var sequenceDifficultyDraft by remember { mutableStateOf("Normal") }
    var sequenceFilterType by sequenceLibraryState.filterType
    var sequenceFilterCategory by sequenceLibraryState.filterCategory
    var sequenceFilterDifficulty by sequenceLibraryState.filterDifficulty
    var sequenceFilterStatus by sequenceLibraryState.filterStatus
    var disabledSequenceCategories by sequenceLibraryState.disabledCategories
    var sequenceCardsDraft by remember { mutableStateOf(listOf("", "")) }
    var sequenceCardPicturesDraft by remember { mutableStateOf(listOf("", "")) }
    var pendingSequencePictureIndex by remember { mutableIntStateOf(-1) }
    var pendingCameraTarget by remember { mutableStateOf("pair") }
    var pendingCameraSequenceIndex by remember { mutableIntStateOf(-1) }
    var sequenceAlternativeDraft by remember { mutableStateOf("") }
    var sequenceEditorMessage by remember { mutableStateOf("") }
    val sequenceGameState = remember(preferences) { SequenceGameState(preferences) }
    var sequencePlayingExercise by sequenceGameState.playingExercise
    var sequencePlayOrder by sequenceGameState.playOrder
    var sequenceSelectedOrder by sequenceGameState.selectedOrder
    var sequenceWrongAnswers by sequenceGameState.wrongAnswers
    var sequencePlayMessage by sequenceGameState.playMessage
    var sequenceChosenCategory by sequenceGameState.chosenCategory
    var sequenceChosenDifficulty by sequenceGameState.chosenDifficulty

    var packageMessage by remember { mutableStateOf("") }
    var sequencePackageMessage by remember { mutableStateOf("") }
    var sequencePendingImportMode by remember { mutableStateOf("replace") }
    var sequenceLibraryFilterCategoryExpanded by sequenceLibraryState.filterCategoryExpanded
    var pendingImportMode by remember { mutableStateOf("replace") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            pictureDraft = uri.toString()
            libraryMessage = "Imagen seleccionada."
        }
    }
    val wordImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            wordLibraryPictureDraft = uri.toString()
            wordLibraryMessage = "Imagen seleccionada."
        }
    }
        val storyObjectImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            storyObjectEditorState.markImageSelected(uri)
        }
    }
    val storyCollectionCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        val collection = pendingStoryCollectionCover
        pendingStoryCollectionCover = null
        if (uri != null && collection != null) runCatching {
            StoryRepository.saveCollectionCover(context, StoryRepository.collectionFolderName(collection), uri)
            storyCollectionCoverRevision++
            storyCollectionMessage = "Portada de «$collection» guardada."
        }.onFailure {
            storyCollectionMessage = it.message ?: "No se pudo guardar la portada de la colección."
        }
    }
val storyCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) runCatching {
            val stored = persistStoryDraft() ?: error(storyEditorMessage)
            storyEditorCover = StoryRepository.importMedia(context, uri, stored.directory, "portada")
            persistStoryDraft()
        }.onFailure { storyEditorMessage = it.message ?: "No se pudo importar la portada." }
    }
    val storyIllustrationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) runCatching {
            val stored = persistStoryDraft() ?: error(storyEditorMessage)
            val scene = StoryRepository.sceneFolderName(storyEditorSelectedScene)
            val fileName = StoryRepository.importMedia(context, uri, File(File(stored.directory, "escenas"), scene), "ilustracion")
            storyEditorIllustrations = storyEditorIllustrations.toMutableMap().apply { put(storyEditorSelectedScene, fileName) }
            persistStoryDraft()
        }.onFailure { storyEditorMessage = it.message ?: "No se pudo importar la ilustración." }
    }
    val storyAmbientPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) runCatching {
            val stored = persistStoryDraft() ?: error(storyEditorMessage)
            val scene = StoryRepository.sceneFolderName(storyEditorSelectedScene)
            val fileName = StoryRepository.importMedia(context, uri, File(File(stored.directory, "escenas"), scene), "ambiente")
            storyEditorAmbientAudio = storyEditorAmbientAudio.toMutableMap().apply { put(storyEditorSelectedScene, fileName) }
            persistStoryDraft()
        }.onFailure { storyEditorMessage = it.message ?: "No se pudo importar el audio ambiental." }
    }
    val storyNarrationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) runCatching {
            val stored = persistStoryDraft() ?: error(storyEditorMessage)
            val scene = StoryRepository.sceneFolderName(storyEditorSelectedScene)
            val fileName = StoryRepository.importMedia(context, uri, File(File(stored.directory, "escenas"), scene), "narracion")
            storyEditorNarrationAudio = storyEditorNarrationAudio.toMutableMap().apply { put(storyEditorSelectedScene, fileName) }
            persistStoryDraft()
        }.onFailure { storyEditorMessage = it.message ?: "No se pudo importar la narración." }
    }
    val sequenceImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        val index = pendingSequencePictureIndex
        if (uri != null && index >= 0) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            sequenceCardPicturesDraft = sequenceCardPicturesDraft.toMutableList().also {
                while (it.size <= index) it.add(defaultSequencePicture(it.size))
                it[index] = uri.toString()
            }
        }
        pendingSequencePictureIndex = -1
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (bitmap != null) {
            try {
                val imagesDir = File(context.filesDir, "word_images").apply { mkdirs() }
                val imageFile = File(imagesDir, "captured_${System.currentTimeMillis()}.jpg")
                FileOutputStream(imageFile).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 60, output)
                }
                val savedPicture = Uri.fromFile(imageFile).toString()
                when (pendingCameraTarget) {
                    "word" -> { wordLibraryPictureDraft = savedPicture; wordLibraryMessage = "Foto añadida." }
                    "sequence" -> {
                        val index = pendingCameraSequenceIndex
                        if (index >= 0) sequenceCardPicturesDraft = sequenceCardPicturesDraft.toMutableList().also {
                            while (it.size <= index) it.add("")
                            it[index] = savedPicture
                        }
                    }
                    "story-object" -> storyObjectEditorState.markImageSelected(Uri.parse(savedPicture))
                    else -> { pictureDraft = savedPicture; libraryMessage = "Foto añadida y comprimida para ocupar poco espacio." }
                }
            } catch (_: Exception) {
                libraryMessage = "No se ha podido guardar la foto."
            } finally {
                bitmap.recycle()
            }
        }
    }
    var firstLaunchNeedsSetup by remember { mutableStateOf(!preferences.getBoolean("first_launch_setup_seen", false)) }
    val initialPinSetupPending = remember {
        !preferences.getBoolean("parent_pin_configured", false) ||
            preferences.getString("parent_pin", null).isNullOrBlank()
    }
    if (firstLaunchNeedsSetup) {
        preferences.edit().putBoolean("sound_enabled", false).apply()
    }
    var screen by remember {
        mutableStateOf(
            when {
                initialPinSetupPending -> "pin-setup"
                firstLaunchNeedsSetup -> "parent"
                else -> if (!preferences.getBoolean("welcome_tour_seen", false)) "tour" else "home"
            }
        )
    }
    androidx.compose.runtime.LaunchedEffect(rewardMode, screen) {
        // Do not request notification permission before the family has chosen a mode.
        // Stars never needs notifications for device-time protection.
        if (!rewardMode.isStars && (screen == "parent" || screen == "home") &&
            preferences.getBoolean("welcome_tour_seen", false) &&
            Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            (context as? ComponentActivity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
        }
    }
    var musicUri by remember { mutableStateOf(preferences.getString("background_music_uri", null)) }
    var soundEnabled by remember { mutableStateOf(if (firstLaunchNeedsSetup) false else preferences.getBoolean("sound_enabled", true)) }
    androidx.compose.runtime.LaunchedEffect(screen) {
        if (screen == "home" && firstLaunchNeedsSetup) {
            soundEnabled = true
            preferences.edit()
                .putBoolean("sound_enabled", true)
                .putBoolean("first_home_sound_activation_done", true)
                .putBoolean("first_launch_setup_seen", true)
                .apply()
            firstLaunchNeedsSetup = false
        }
    }
    var musicMessage by remember { mutableStateOf("") }
    var appInForeground by remember { mutableStateOf(true) }
    var setupAccessibilityEnabled by remember { mutableStateOf(false) }
    var restrictedSettingsDone by remember { mutableStateOf(preferences.getBoolean("restricted_settings_done", false)) }
    var batterySettingsDone by remember { mutableStateOf(preferences.getBoolean("battery_setup_done", false)) }
    var xiaomiWindowPermissionDone by remember { mutableStateOf(preferences.getBoolean("xiaomi_window_permission_done", false)) }
    val lifecycleOwner = remember(context) { context as? ComponentActivity }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    appInForeground = true
                    preferences.edit().putBoolean("device_setup_in_progress", false).apply()
                    setupAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    appInForeground = false
                    // Remember the exact activity screen before Android Home/backgrounding.
                    // Ignore temporary document pickers, setup flows and the timer gate itself.
                    if (screen != "play-time" &&
                        !preferences.getBoolean("content_picker_active", false) &&
                        !preferences.getBoolean("device_setup_in_progress", false) &&
                        !preferences.getBoolean("parent_area_active", false)) {
                        preferences.edit().putString("play_time_return_screen", screen).commit()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            musicUri = uri.toString()
            preferences.edit().putString("background_music_uri", musicUri).apply()
            musicMessage = "Música seleccionada. Se reproducirá en bucle cuando el sonido esté activado."
        }
    }
    var backgroundMusicPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // El reproductor general solo se recrea si cambia la pista seleccionada.
    // Cambiar de actividad nunca destruye ni reinicia la música.
    DisposableEffect(musicUri) {
        var player: MediaPlayer? = null
        try {
            player = if (musicUri.isNullOrBlank()) {
                MediaPlayer.create(context, R.raw.musica_infantil_2)
            } else {
                MediaPlayer.create(context, Uri.parse(musicUri))
            }
            player?.apply {
                isLooping = true
                setVolume(0.22f, 0.22f)
            }
            if (player == null) {
                musicMessage = "No se ha podido cargar la música."
            }
            backgroundMusicPlayer = player
        } catch (_: Exception) {
            backgroundMusicPlayer = null
            musicMessage = "No se ha podido reproducir la música. Prueba con otro MP3."
        }
        onDispose {
            if (backgroundMusicPlayer === player) backgroundMusicPlayer = null
            try { player?.stop() } catch (_: Exception) { }
            player?.release()
        }
    }

    // Al entrar en Cuentos se pausa la música general; al salir se reanuda
    // desde la posición conservada. El resto de la navegación no la afecta.
    DisposableEffect(backgroundMusicPlayer, soundEnabled, appInForeground, screen) {
        val player = backgroundMusicPlayer
        if (player != null) {
            try {
                if (soundEnabled && appInForeground && screen != "story-player") {
                    if (!player.isPlaying) player.start()
                } else {
                    if (player.isPlaying) player.pause()
                }
            } catch (_: Exception) { }
        }
        onDispose { }
    }
    var mode by remember { mutableStateOf("Al azar") }
    var cardsStartMessage by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("Fácil") }
    var dailyManualLimit by remember { mutableIntStateOf(preferences.getInt("daily_manual_limit", 2).coerceIn(0, 10)) }
    val manualSelectionDayKey = currentDayKey()
    var manualCategoryUsesToday by remember {
        mutableIntStateOf(
            if (preferences.getString("manual_category_day", "") == manualSelectionDayKey)
                preferences.getInt("manual_category_uses", 0).coerceAtLeast(0)
            else 0
        )
    }
    androidx.compose.runtime.LaunchedEffect(manualSelectionDayKey) {
        if (preferences.getString("manual_category_day", "") != manualSelectionDayKey) {
            preferences.edit()
                .putString("manual_category_day", manualSelectionDayKey)
                .putInt("manual_category_uses", 0)
                .apply()
            manualCategoryUsesToday = 0
        }
    }
    val manualCategoryLimitReached = manualCategoryUsesToday >= dailyManualLimit
    androidx.compose.runtime.LaunchedEffect(manualCategoryLimitReached) {
        if (manualCategoryLimitReached && mode == "Elegir") {
            mode = "Al azar"
        }
    }
    var questionCount by remember { mutableIntStateOf(preferences.getInt("question_count", 4).coerceIn(1, 10)) }
    var balanceSeconds by remember { mutableIntStateOf(preferences.getInt("balance_seconds", 0)) }
    var minimumPlayMinutes by remember { mutableIntStateOf(preferences.getInt("minimum_play_minutes", 5).coerceIn(0, 60)) }
    var lastEarnedSeconds by remember { mutableIntStateOf(0) }
    var learningStats by remember { mutableStateOf(loadLearningStats(preferences)) }
    var learningTurn by remember { mutableIntStateOf(preferences.getInt("learning_turn_v1", 0)) }
    val cardsGameState = remember { CardsGameState() }
    var activeExercisePairs by cardsGameState.activePairs
    var selected by cardsGameState.selectedWord
    var selectedPicture by cardsGameState.selectedPicture
    var evaluatedPictures by cardsGameState.evaluatedPictures
    var resolved by cardsGameState.resolvedWords
    var correct by cardsGameState.correctCount
    var resultCorrectCount by remember { mutableIntStateOf(0) }
    var resultTotalCount by remember { mutableIntStateOf(0) }
    var feedback by cardsGameState.feedback
    var pinEntry by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var parentSection by remember { mutableStateOf(if (firstLaunchNeedsSetup && !rewardMode.isStars) "device-setup" else "menu") }
    var linkedExerciseFont by remember { mutableStateOf(preferences.getBoolean("linked_exercise_font_v1", true)) }
    val exerciseFontFamily = if (linkedExerciseFont) FontFamily(Font(R.font.playwrite_es)) else FontFamily.Default
    var transferExportCategories by remember { mutableStateOf<Map<ContentStorage.Modality, Set<String>>>(emptyMap()) }
    var transferExportStatistics by remember { mutableStateOf<Set<ContentStorage.Modality>>(emptySet()) }
    var transferExportUsageStatistics by remember { mutableStateOf<Set<ContentStorage.Modality>>(emptySet()) }
    var transferExportStories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var transferExportObjects by remember { mutableStateOf<Set<String>>(emptySet()) }
    var transferPackage by remember { mutableStateOf<LibraryPackageManager.PackageInfo?>(null) }
    var transferImportCategories by remember { mutableStateOf<Map<ContentStorage.Modality, Set<String>>>(emptyMap()) }
    var transferImportStories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var transferImportObjects by remember { mutableStateOf<Set<String>>(emptySet()) }
    var transferObjectConflictDecisions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var transferStoryConflictDecisions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var transferExerciseConflictDecisions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var transferConflictIndex by remember { mutableIntStateOf(0) }
    var transferReplaceDuplicates by remember { mutableStateOf(false) }
    var transferImportStatistics by remember { mutableStateOf(false) }
    var transferImportUsageStatistics by remember { mutableStateOf(false) }
    var transferMessage by remember { mutableStateOf("") }
    var remoteLibraryPackages by remember { mutableStateOf<List<RemoteLibraryPackage>>(emptyList()) }
    var remoteLibraryLoading by remember { mutableStateOf(false) }
    var remoteLibraryMessage by remember { mutableStateOf("") }
    var remoteLibraryDownloadingName by remember { mutableStateOf<String?>(null) }
    var remoteLibraryDownloadedBytes by remember { mutableStateOf(0L) }
    var remoteLibraryTotalBytes by remember { mutableStateOf(0L) }
    var remoteLibraryPendingImport by remember { mutableStateOf<RemoteLibraryPackage?>(null) }
    var transferRemoteLibraryName by remember { mutableStateOf<String?>(null) }
    val remoteLibraryScope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(parentSection) {
        if (parentSection == "basic-library" && remoteLibraryPackages.isEmpty() && !remoteLibraryLoading) {
            remoteLibraryLoading = true
            try {
                remoteLibraryPackages = withContext(Dispatchers.IO) { RemoteLibraryRepository.listPackages() }
                if (remoteLibraryPackages.isEmpty()) remoteLibraryMessage = "Todavía no hay paquetes ZIP publicados."
            } catch (error: Exception) {
                remoteLibraryMessage = "No se ha podido consultar GitHub: ${error.message ?: "error de conexión"}"
            } finally {
                remoteLibraryLoading = false
            }
        }
    }
    var deleteSelectedItems by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteExpandedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteConfirmArmed by remember { mutableStateOf(false) }

    var easyRewardMinutes by remember { mutableIntStateOf(preferences.getInt("reward_easy_minutes", 2).coerceIn(0, 120)) }
    var normalRewardMinutes by remember { mutableIntStateOf(preferences.getInt("reward_normal_minutes", 5).coerceIn(0, 120)) }
    var hardRewardMinutes by remember { mutableIntStateOf(preferences.getInt("reward_hard_minutes", 7).coerceIn(0, 120)) }
    var wrongAnswerPenalty by remember { mutableStateOf(preferences.getString("wrong_answer_penalty", "half") ?: "half") }
    var penaltyMenuExpanded by remember { mutableStateOf(false) }
    var pinStored by remember {
        mutableStateOf(
            preferences.getString("parent_pin", "") ?: ""
        )
    }
    var newPinDraft by remember { mutableStateOf("") }
    var initialPinConfirmDraft by remember { mutableStateOf("") }
    var initialPinError by remember { mutableStateOf("") }
    var tourReturnTarget by remember { mutableStateOf("home") }
    var categoryDraftName by remember { mutableStateOf("") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var categoryNames by remember { mutableStateOf((preferences.getString("categories", "") ?: "").split("|").filter { it.isNotBlank() }) }
    var categoryMessage by remember { mutableStateOf("") }
    var categoryToRemove by remember { mutableStateOf("") }
    val categories = (categoryNames + pairs.map { it.category }).distinctBy { it.trim().lowercase() }.sorted()
    var selectedExerciseCategory by remember { mutableStateOf(preferences.getString("selected_exercise_category", categories.firstOrNull() ?: "") ?: "") }
    var exerciseCategoryMenuExpanded by remember { mutableStateOf(false) }
    var libraryCategoryFilter by remember { mutableStateOf("Todas") }
    var libraryDifficultyFilter by remember { mutableStateOf("Todas") }
    var libraryFilterCategoryExpanded by remember { mutableStateOf(false) }
    val wordGameState = remember { WordGameState() }
    var wordExerciseTarget by wordGameState.target
    var wordExerciseLetterOrder by wordGameState.letterOrder
    var wordExercisePlaced by wordGameState.placedLetters
    var wordExerciseWrongAnswers by wordGameState.wrongAnswers
    var wordExerciseMessage by wordGameState.message
    var wordSelectedDifficulty by wordGameState.selectedDifficulty
    var wordCurrentCategory by wordGameState.currentCategory
    var wordStartMessage by wordGameState.startMessage
    var wordCategoryCandidates by wordGameState.categoryCandidates

    val exportPackageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) {
            packageMessage = try {
                exportContentPackage(context, uri, pairs, categories)
                "Exportación completada: ${pairs.size} palabras y ${categories.size} categorías."
            } catch (e: Exception) { "No se pudo exportar: ${e.message ?: "error de archivo"}" }
        }
    }
    val importPackageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) {
            packageMessage = try {
                val imported = importContentPackage(context, uri)
                val mergedPairs = if (pendingImportMode == "replace") imported.pairs else {
                    val merged = pairs.toMutableList()
                    imported.pairs.forEach { incoming ->
                        val existingIndex = merged.indexOfFirst { it.word.equals(incoming.word, ignoreCase = true) }
                        if (existingIndex >= 0) merged[existingIndex] = incoming else merged.add(incoming)
                    }
                    merged
                }
                pairs = mergedPairs
                pairs = ExerciseUiStorage.savePairs(context, ContentStorage.Modality.TARJETAS, mergedPairs)
                val mergedCategories = if (pendingImportMode == "replace") imported.categories else (categoryNames + imported.categories).distinctBy { it.lowercase() }
                categoryNames = mergedCategories
                preferences.edit().putString("categories", mergedCategories.joinToString("|")).apply()
                "Importación completada: ${imported.pairs.size} palabras procesadas. ${if (pendingImportMode == "replace") "Biblioteca sustituida." else "Contenido combinado; las palabras con el mismo nombre se actualizaron."}"
            } catch (e: Exception) { "No se pudo importar: ${e.message ?: "archivo no válido"}" }
        }
    }

    val exportWordPackageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) {
            wordPackageMessage = try {
                exportContentPackage(context, uri, wordExercises, wordExerciseCategories, "aprender-para-jugar-words")
                "Copia de Palabras creada: " + wordExercises.size + " ejercicios."
            } catch (e: Exception) { "No se pudo exportar: " + (e.message ?: "error de archivo") }
        }
    }
    val importWordPackageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) {
            wordPackageMessage = try {
                val imported = importContentPackage(context, uri, "aprender-para-jugar-words")
                val nextExercises = if (wordPendingImportMode == "replace") imported.pairs else {
                    val merged = wordExercises.toMutableList()
                    imported.pairs.forEach { incoming ->
                        val existingIndex = merged.indexOfFirst { it.word.equals(incoming.word, ignoreCase = true) }
                        if (existingIndex >= 0) merged[existingIndex] = incoming else merged.add(incoming)
                    }
                    merged
                }
                wordExercises = wordLibraryState.saveExercises(nextExercises)
                wordLibraryState.mergeImportedCategories(imported.categories, wordPendingImportMode == "replace")
                "Importación de Palabras completada: " + imported.pairs.size + " ejercicios. " + if (wordPendingImportMode == "replace") "Biblioteca sustituida." else "Contenido combinado."
            } catch (e: Exception) { "No se pudo importar: " + (e.message ?: "archivo no válido") }
        }
    }
    val exportSequencePackageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) {
            sequencePackageMessage = try {
                exportSequencePackage(context, uri, sequenceExercises, (sequenceExercises.map { it.category } + emptyList()).filter { it.isNotBlank() }.distinct().sorted(), disabledSequenceCategories)
                "Copia de seguridad creada: ${sequenceExercises.size} ejercicios."
            } catch (e: Exception) { "No se pudo exportar: ${e.message ?: "error de archivo"}" }
        }
    }
    val importSequencePackageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) {
            sequencePackageMessage = try {
                val imported = importSequencePackage(context, uri)
                val nextExercises = if (sequencePendingImportMode == "replace") imported.exercises else {
                    val merged = sequenceExercises.toMutableList()
                    imported.exercises.forEach { incoming ->
                        val existingIndex = merged.indexOfFirst { it.title.equals(incoming.title, ignoreCase = true) && it.type == incoming.type && it.category.equals(incoming.category, ignoreCase = true) }
                        if (existingIndex >= 0) merged[existingIndex] = incoming else merged.add(incoming)
                    }
                    merged
                }
                val nextDisabled = if (sequencePendingImportMode == "replace") imported.disabledCategories else disabledSequenceCategories + imported.disabledCategories
                sequenceExercises = nextExercises
                disabledSequenceCategories = nextDisabled
                sequenceExercises = sequenceLibraryState.saveExercises(nextExercises)
                preferences.edit().putStringSet("disabled_sequence_categories", nextDisabled).apply()
                "Importación completada: ${imported.exercises.size} ejercicios. ${if (sequencePendingImportMode == "replace") "Biblioteca de secuencias sustituida." else "Contenido combinado."}"
            } catch (e: Exception) { "No se pudo importar: ${e.message ?: "archivo no válido"}" }
        }
    }

    val unifiedExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) {
            transferMessage = try {
                LibraryPackageManager.export(context, uri, transferExportCategories, transferExportStatistics, transferExportUsageStatistics, transferExportStories, transferExportObjects)
                "Exportación completada correctamente.".also { parentSection = "library-transfer" }
            } catch (e: Exception) { "No se pudo exportar: ${e.message ?: "error de archivo"}" }
        }
    }
    val unifiedImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) {
            try {
                transferPackage?.let { LibraryPackageManager.discard(it) }
                val inspected = LibraryPackageManager.inspect(context, uri)
                transferPackage = inspected
                transferImportCategories = inspected.modalities.associate { info -> info.modality to info.categories.map { it.directory }.toSet() }
                transferImportStories = inspected.stories.map { it.key }.toSet()
                transferImportObjects = emptySet()
                transferObjectConflictDecisions = emptyMap()
                transferStoryConflictDecisions = emptyMap()
                transferExerciseConflictDecisions = emptyMap()
                transferConflictIndex = 0
                transferReplaceDuplicates = false
                transferImportStatistics = false
                transferMessage = "Archivo válido. Selecciona las categorías que deseas importar."
                parentSection = "library-import"
            } catch (e: Exception) {
                transferMessage = "No se puede importar: ${e.message ?: "archivo no válido"}"
            }
        }
    }

    val availableExercisePairs = pairs.filter {
        it.active && it.difficulty == difficulty &&
            (mode != "Elegir" || it.category.equals(selectedExerciseCategory, ignoreCase = true))
    }
    val exercisePairs = if (screen == "exercise" || screen == "result" || screen == "sequence-result") activeExercisePairs else availableExercisePairs.take(questionCount)
    val pairCount = exercisePairs.size
    val rewardSeconds = when (difficulty) { "Normal" -> normalRewardMinutes * 60; "Difícil" -> hardRewardMinutes * 60; else -> easyRewardMinutes * 60 }
    val balanceMinutes = balanceSeconds / 60
    val balanceRemainderSeconds = balanceSeconds % 60

    fun finishWelcomeTour() {
        preferences.edit().putBoolean("welcome_tour_seen", true).apply()
        when (tourReturnTarget) {
            "parent-setup" -> {
                setupAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                // The accessibility service is the permission Android exposes reliably
                // and the essential prerequisite for Time mode. The other setup steps are
                // OEM-specific/manual recommendations and must not block mode selection.
                val deviceSetupComplete = setupAccessibilityEnabled
                screen = "parent"
                parentSection = if (rewardMode.isStars || deviceSetupComplete) "menu" else "device-setup"
            }
            "parent-menu" -> { screen = "parent"; parentSection = "menu" }
            else -> screen = "home"
        }
    }

    BackHandler(enabled = screen != "home" && screen != "play-time") {
        when (screen) {
            "help" -> { screen = "home" }
            "tour" -> finishWelcomeTour()
            "parent" -> {
                when (parentSection) {
                    "word-library" -> {
                        if (wordLibraryPage == "edit") {
                            wordLibraryPage = "list"
                            wordLibraryMessage = ""
                        } else {
                            parentSection = parentBackSection(parentSection, transferRemoteLibraryName != null) ?: "words-settings"
                        }
                    }
                    "library-export", "library-import", "library-delete" -> {
                        transferPackage?.let { LibraryPackageManager.discard(it) }
                        transferPackage = null
                        deleteConfirmArmed = false
                        parentSection = parentBackSection(parentSection, transferRemoteLibraryName != null) ?: "menu"
                        transferRemoteLibraryName = null
                    }
                    else -> {
                        val targetSection = parentBackSection(parentSection, transferRemoteLibraryName != null)
                        if (targetSection == null) {
                            screen = "home"
                            parentSection = "menu"
                        } else {
                            parentSection = targetSection
                        }
                    }
                }
            }
            "cards-options" -> { screen = "home"; cardsStartMessage = "" }
            "stories" -> { screen = "home" }
            "story-inventory" -> { screen = "home"; storyInventoryMessage = "" }
            "sequences" -> { screen = "home" }
            "sequence-difficulty" -> { screen = "sequences" }
            "category-select" -> { screen = "cards-options"; cardsStartMessage = "" }
            "word-options" -> { screen = "home"; wordStartMessage = "" }
            "word-category-select" -> { screen = "word-options"; wordStartMessage = "" }
            "word-exercise" -> { screen = "word-options"; wordExerciseTarget = null; wordExercisePlaced = emptyList(); wordStartMessage = "" }
            "sequences" -> { screen = "home" }
            "sequence-difficulty" -> { screen = "sequences" }
            "sequence-game" -> { screen = "sequence-difficulty"; sequenceSelectedOrder = emptyList(); sequencePlayMessage = "" }
            "sequence-result" -> { screen = "home" }
            "pin-setup" -> Unit
            "parent-pin", "exit-pin" -> { screen = "home"; pinEntry = ""; pinError = false }
            "exercise" -> { screen = "home"; resolved = emptySet(); selected = emptyMap(); selectedPicture = ""; evaluatedPictures = emptyMap(); correct = 0; feedback = "" }
            "result" -> { screen = "home"; resolved = emptySet(); selected = emptyMap(); selectedPicture = ""; evaluatedPictures = emptyMap(); correct = 0; feedback = ""; lastEarnedSeconds = 0 }
            else -> { screen = "home"; parentSection = "menu" }
        }
    }

    fun startWordExercise(category: String?) {
        if (wordGameState.start(wordExercises, disabledWordExerciseCategories, category)) {
            screen = "word-exercise"
        }
    }

    fun eligibleSequenceExercises(type: String, level: String? = null): List<SequenceExercise> =
        sequenceGameState.eligibleExercises(
            sequenceExercises, type, sequenceMaxCards, disabledSequenceCategories, level
        )

    fun startSequenceExercise(type: String, level: String): Boolean {
        val started = sequenceGameState.startExercise(
            sequenceExercises, type, level, sequenceMaxCards, disabledSequenceCategories
        )
        if (started) screen = "sequence-game"
        return started
    }

    fun addEarnedTime(seconds: Int) {
        if (seconds <= 0) return
        lastEarnedSeconds = seconds
        val now = System.currentTimeMillis()
        val currentDeadline = preferences.getLong("play_deadline_ms", 0L)
        if (currentDeadline > now) {
            val newDeadline = currentDeadline + seconds.toLong() * 1000L
            val remaining = ((newDeadline - now + 999L) / 1000L).toInt().coerceAtLeast(0)
            preferences.edit().putLong("play_deadline_ms", newDeadline).putInt("balance_seconds", remaining).apply()
            balanceSeconds = remaining
            androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, PlayTimeService::class.java).setAction(PlayTimeService.ACTION_START))
        } else {
            val updated = preferences.getInt("balance_seconds", balanceSeconds).coerceAtLeast(0) + seconds
            preferences.edit().putInt("balance_seconds", updated).commit()
            balanceSeconds = updated
        }
    }


    fun startCardsGame(requestedMode: String, requestedCategory: String? = null) {
        cardsStartMessage = ""
        if (requestedMode == "Elegir" && manualCategoryLimitReached) {
            cardsStartMessage = "Has alcanzado el límite diario de elecciones manuales."
            return
        }
        val chosenCategory = when (requestedMode) {
            "Al azar" -> {
                val eligible = categories.filter { category ->
                    pairs.count { it.active && it.difficulty == difficulty && it.category.equals(category, true) } >= questionCount
                }
                eligible.randomOrNull() ?: run {
                    cardsStartMessage = "No hay ninguna categoría con suficientes tarjetas activas para esta dificultad."
                    return
                }
            }
            "Elegir" -> requestedCategory ?: selectedExerciseCategory
            else -> ""
        }
        val candidates = pairs.filter { it.active && it.difficulty == difficulty &&
            (requestedMode == "Mezcladas" || it.category.equals(chosenCategory, true)) }
        if (candidates.size < questionCount) {
            cardsStartMessage = "No hay suficientes tarjetas activas para esta dificultad. Prueba otra dificultad o mezcla categorías."
            return
        }
        if (requestedMode == "Elegir") {
            val uses = manualCategoryUsesToday + 1
            manualCategoryUsesToday = uses
            preferences.edit().putString("manual_category_day", manualSelectionDayKey).putInt("manual_category_uses", uses).apply()
            if (uses >= dailyManualLimit) mode = "Al azar"
        }
        mode = requestedMode
        if (chosenCategory.isNotBlank()) {
            selectedExerciseCategory = chosenCategory
            preferences.edit().putString("selected_exercise_category", chosenCategory).apply()
        }
        val chosenPairs = chooseAdaptivePairs(candidates, questionCount, learningStats, learningTurn)
        activeExercisePairs = chosenPairs
        chosenPairs.forEach { pair ->
            learningTurn += 1
            val state = learningStats[pair.word] ?: WordLearning()
            learningStats = learningStats + (pair.word to state.copy(lastShownTurn = learningTurn))
        }
        preferences.edit().putInt("learning_turn_v1", learningTurn).apply()
        saveLearningStats(preferences, learningStats)
        cardsGameState.begin(activeExercisePairs)
        feedback = ""; screen = "exercise"
    }

    fun resolveCardsPair(word: String, picture: String) {
        val resolution = cardsGameState.resolve(word, picture, exercisePairs, learningStats, learningTurn)
        learningTurn = resolution.learningTurn
        learningStats = resolution.learningStats
        preferences.edit().putInt("learning_turn_v1", learningTurn).apply()
        saveLearningStats(preferences, learningStats)
        if (resolution.resolvedCount == pairCount) {
            val wrong = pairCount - resolution.correctCount
            val netCorrectUnits = when (wrongAnswerPenalty) {
                "none" -> resolution.correctCount * 2
                "full" -> (resolution.correctCount * 2 - wrong * 2).coerceAtLeast(0)
                else -> (resolution.correctCount * 2 - wrong).coerceAtLeast(0)
            }
            addEarnedTime((rewardSeconds * netCorrectUnits) / (pairCount * 2))
            runCatching { UsageStatisticsRepository.recordCompleted(context, ContentStorage.Modality.TARJETAS, difficulty) }
            resultCorrectCount = resolution.correctCount
            resultTotalCount = pairCount
            exercisePairs.forEach { pair ->
                pair.directory?.let { directory ->
                    val wasCorrect = evaluatedPictures[pair.picture] == true
                    runCatching { ExerciseRepository.recordResult(directory, if (wasCorrect) 1 else 0, if (wasCorrect) 0 else 1) }
                }
            }
            screen = "result"
        }
    }

    androidx.compose.runtime.LaunchedEffect(screen, appInForeground) {
        while (!rewardMode.isStars && screen == "play-time" && appInForeground) {
            val deadline = preferences.getLong("play_deadline_ms", 0L)
            val remaining = if (deadline > 0L) ((deadline - System.currentTimeMillis() + 999L) / 1000L).toInt().coerceAtLeast(0) else preferences.getInt("balance_seconds", 0).coerceAtLeast(0)
            if (remaining != balanceSeconds) {
                balanceSeconds = remaining
                preferences.edit().putInt("balance_seconds", remaining).apply()
            }
            if (remaining <= 0) {
                preferences.edit()
                    .putInt("balance_seconds", 0)
                    .putLong("play_deadline_ms", 0L)
                    .putBoolean("play_session_authorized", false)
                    .putBoolean("play_paused_for_screen_lock", false)
                    .commit()
                balanceSeconds = 0
                screen = "home"
                break
            }
            delay(1000L)
        }
    }

    // When the app regains focus while play time is running, show the live countdown
    // first. From there the parent/child can explicitly end the session, bank only the
    // remaining seconds, and return to learning to earn more time.
    androidx.compose.runtime.LaunchedEffect(appInForeground, rewardMode) {
        if (appInForeground && !rewardMode.isStars) {
            // Fallback for devices where ACTION_USER_PRESENT is missed: resuming
            // the app proves the screen is unlocked, so restart the saved countdown.
            if (preferences.getBoolean("play_paused_for_screen_lock", false)) {
                val remaining = preferences.getInt("balance_seconds", 0).coerceAtLeast(0)
                val resumeDeadline = if (remaining > 0) System.currentTimeMillis() + remaining.toLong() * 1000L else 0L
                preferences.edit()
                    .putBoolean("play_paused_for_screen_lock", false)
                    .putLong("play_deadline_ms", resumeDeadline)
                    .putBoolean("play_session_authorized", remaining > 0)
                    .putInt("balance_seconds", remaining)
                    .commit()
                balanceSeconds = remaining
                if (remaining > 0) {
                    androidx.core.content.ContextCompat.startForegroundService(
                        context,
                        Intent(context, PlayTimeService::class.java).setAction(PlayTimeService.ACTION_START)
                    )
                } else {
                    screen = "home"
                }
            }
            val now = System.currentTimeMillis()
            val deadline = preferences.getLong("play_deadline_ms", 0L)
            if (deadline > now) {
                val remaining = ((deadline - now + 999L) / 1000L).toInt().coerceAtLeast(0)
                balanceSeconds = remaining
                preferences.edit().putInt("balance_seconds", remaining).commit()
                if (remaining <= 0) {
                    preferences.edit()
                        .putLong("play_deadline_ms", 0L)
                        .putInt("balance_seconds", 0)
                        .putBoolean("play_session_authorized", false)
                        .putBoolean("play_paused_for_screen_lock", false)
                        .commit()
                    screen = "home"
                } else if (
                    !preferences.getBoolean("content_picker_active", false) &&
                    !preferences.getBoolean("device_setup_in_progress", false) &&
                    !preferences.getBoolean("parent_area_active", false)
                ) {
                    // Every return from Android Home passes through the countdown gate,
                    // regardless of which game, story, board or menu was left open.
                    screen = "play-time"
                }
            } else if (deadline > 0L) {
                // A positive deadline that has already passed is authoritative: the
                // persisted balance may still contain the previous tick's stale value.
                preferences.edit()
                    .putLong("play_deadline_ms", 0L)
                    .putInt("balance_seconds", 0)
                    .putBoolean("play_session_authorized", false)
                    .putBoolean("play_paused_for_screen_lock", false)
                    .commit()
                balanceSeconds = 0
                if (screen == "play-time") screen = "home"
            } else {
                // Refresh Compose state from the persisted balance whenever the app returns.
                val savedBalance = preferences.getInt("balance_seconds", 0).coerceAtLeast(0)
                balanceSeconds = savedBalance
                if (screen == "play-time" && savedBalance <= 0) screen = "home"
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(screen, appInForeground) {
        // External camera/document picker temporarily backgrounds the app; preserve
        // the parental guard until its activity-result callback completes.
        if (!preferences.getBoolean("device_setup_in_progress", false) &&
            !preferences.getBoolean("content_picker_active", false)) {
            preferences.edit().putBoolean(
                "parent_area_active",
                appInForeground && (screen == "parent-pin" || screen == "parent" || screen == "exit-pin" || screen == "tour")
            ).apply()
        }
    }

    if (screen == "pin-setup") {
        BackHandler(enabled = true) { }
        PinSetupScreen(
            pinDraft = newPinDraft,
            confirmationDraft = initialPinConfirmDraft,
            error = initialPinError,
            onPinDraftChange = { newPinDraft = it; initialPinError = "" },
            onConfirmationDraftChange = { initialPinConfirmDraft = it; initialPinError = "" },
            onSave = {
                when {
                    newPinDraft.length != 4 -> initialPinError = "El PIN debe tener exactamente 4 cifras."
                    initialPinConfirmDraft.length != 4 -> initialPinError = "Confirma el PIN de 4 cifras."
                    newPinDraft != initialPinConfirmDraft -> initialPinError = "Los PIN no coinciden."
                    else -> {
                        pinStored = newPinDraft
                        preferences.edit()
                            .putString("parent_pin", pinStored)
                            .putBoolean("parent_pin_configured", true)
                            .commit()
                        newPinDraft = ""
                        initialPinConfirmDraft = ""
                        initialPinError = ""
                        tourReturnTarget = if (firstLaunchNeedsSetup) "parent-setup" else "home"
                        screen = "tour"
                    }
                }
            }
        )
    } else if (screen == "tour") {
        WelcomeTourScreen(
            selectedMode = rewardMode,
            onModeSelected = { selected ->
                rewardMode = selected
                preferences.edit().putString(RewardMode.PREFERENCE_KEY, if (selected.isStars) "stars" else "time").apply()
                if (selected.isStars) {
                    val activeDeadline = preferences.getLong("play_deadline_ms", 0L)
                    if (activeDeadline > 0L) {
                        balanceSeconds = ((activeDeadline - System.currentTimeMillis() + 999L) / 1000L).toInt().coerceAtLeast(0)
                    }
                    preferences.edit()
                        .putInt("balance_seconds", balanceSeconds)
                        .putLong("play_deadline_ms", 0L)
                        .putBoolean("play_session_authorized", false)
                        .putBoolean("play_paused_for_screen_lock", false)
                        .apply()
                    context.stopService(Intent(context, PlayTimeService::class.java))
                }
            },
            onFinish = { finishWelcomeTour() }
        )
    } else if (screen == "exit-pin" && !rewardMode.isStars) {
        PinGateScreen(
            title = "🔒 Salir de la aplicación",
            instruction = "Introduce el PIN familiar para confirmar la salida.",
            pin = pinEntry,
            error = pinError,
            confirmLabel = "Confirmar salida",
            onPinChange = { pinEntry = it.filter(Char::isDigit).take(4); pinError = false },
            onConfirm = {
                if (pinEntry == pinStored) {
                    preferences.edit()
                        .putBoolean("manual_exit_allowed", true)
                        .putBoolean("parent_area_active", false)
                        .putLong("play_deadline_ms", 0L)
                        .apply()
                    context.stopService(Intent(context, PlayTimeService::class.java))
                    (context as? ComponentActivity)?.finishAndRemoveTask()
                } else {
                    pinError = true
                }
            },
            onCancel = { screen = "home"; pinEntry = ""; pinError = false }
        )
    } else if (screen == "parent-pin") {
        PinGateScreen(
            title = "🔐 Zona de familias",
            instruction = "Introduce el PIN para acceder a los controles.",
            pin = pinEntry,
            error = pinError,
            confirmLabel = "Entrar",
            warning = "Si olvidas el PIN, tendrás que restablecer la aplicación borrando sus datos desde los ajustes de Android. Esta operación elimina el contenido guardado localmente; conserva copias de tus bibliotecas.",
            onPinChange = { pinEntry = it.filter(Char::isDigit).take(4); pinError = false },
            onConfirm = {
                if (pinEntry == pinStored) {
                    screen = "parent"
                    parentSection = "menu"
                    pinEntry = ""
                    pinError = false
                } else {
                    pinError = true
                }
            },
            onCancel = { screen = "home"; pinEntry = ""; pinError = false },
            cancelLabel = "Volver al inicio"
        )
    } else if (screen == "parent") {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Control parental  ›  ${if (parentSection == "menu") "Menú principal" else when (parentSection) { "game-modes" -> "Configurar actividades"; "active-games" -> "Actividades activas"; "cards-settings" -> "Tarjetas"; "sequences-settings" -> "Secuencias"; "words-settings" -> "Palabras"; "stories-settings" -> "Cuentos"; "stories-content-settings" -> "Gestión de cuentos"; "stories-library" -> "Biblioteca de cuentos"; "stories-collections" -> "Colecciones de cuentos"; "stories-objects" -> "Gestión de objetos"; "stories-object-create" -> "Creación de objetos"; "stories-object-collection" -> "Colección de objetos"; "stories-object-inventory" -> "Inventario actual"; "words" -> "Biblioteca de tarjetas"; "categories" -> "Categorías"; "rewards" -> "Recompensas y límites"; "sound-settings" -> "Ajustes de sonido"; "music" -> "Música de fondo"; "stories-audio" -> "Audio de cuentos"; "typography" -> "Tipografía de los ejercicios"; "security" -> "Cambiar PIN"; "device-setup" -> "Configuración del dispositivo"; "about" -> "Acerca de esta app"; "basic-library" -> "Descargar contenido online"; "library-transfer" -> "Importar y exportar bibliotecas"; "library-export" -> "Exportar bibliotecas"; "library-import" -> "Importar bibliotecas"; "library-delete" -> "Eliminar biblioteca"; "statistics-menu" -> "Estadísticas"; "learning-stats" -> "Estadísticas de aprendizaje"; "usage-stats" -> "Estadísticas de uso"; else -> "Configuración" }}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF667085))
                Text(if (parentSection == "menu") "Control parental" else when (parentSection) { "typography" -> "Tipografía de los ejercicios"; "game-modes" -> "Configurar actividades"; "active-games" -> "Actividades activas"; "cards-settings" -> "Tarjetas"; "words" -> "Biblioteca de tarjetas"; "categories" -> "Categorías"; "rewards" -> "Recompensas y límites"; "music" -> "Música de fondo"; "security" -> "Cambiar PIN"; "device-setup" -> "Configuración del dispositivo"; "about" -> "Acerca de esta app"; "library-transfer" -> "Importar y exportar bibliotecas"; "library-export" -> "Exportar bibliotecas"; "library-import" -> "Importar bibliotecas"; "library-delete" -> "Eliminar biblioteca"; "statistics-menu" -> "Estadísticas"; "learning-stats" -> "Estadísticas de aprendizaje"; "usage-stats" -> "Estadísticas de uso"; else -> "Configuración" }, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                Text(if (parentSection == "menu") "Configura el contenido y las opciones de la aplicación." else "Control parental · ${when (parentSection) { "game-modes" -> "Elige una modalidad para configurar"; "active-games" -> "Activa o desactiva las modalidades disponibles para el periodo de pruebas"; "cards-settings" -> "Opciones específicas del modo Tarjetas"; "sequences-settings" -> "Opciones del modo Secuencias"; "words-settings" -> "Opciones del modo Palabras"; "stories-settings" -> "Organiza los cuentos y sus recursos"; "stories-content-settings" -> "Administra las historias y sus colecciones"; "stories-library" -> "Crea, edita y organiza cuentos ilustrados"; "stories-collections" -> "Organiza cuentos en colecciones, sagas y universos narrativos"; "stories-objects" -> "Administra los objetos y el inventario"; "stories-object-create" -> "Define nuevos objetos para reutilizarlos en los cuentos"; "stories-object-collection" -> "Consulta y administra todos los objetos de la biblioteca común"; "stories-object-inventory" -> "Consulta y gestiona los objetos que posee el niño"; "words" -> "Gestiona las tarjetas y sus imágenes"; "categories" -> "Organiza el vocabulario"; "rewards" -> "Configura Minutos/Estrellas, recompensas y límites"; "sound-settings" -> "Configura el sonido general y el audio de los cuentos"; "music" -> "Configura la música de fondo"; "stories-audio" -> "Ajusta el volumen ambiental durante las narraciones"; "typography" -> "Elige la letra que se utilizará en los ejercicios"; "security" -> "Protege el acceso y administra el saldo"; "device-setup" -> "Permisos y accesos necesarios para la protección"; "about" -> "Información, propósito y créditos"; "basic-library" -> "Explora, descarga y actualiza paquetes de contenido desde Internet"; "library-transfer" -> "Elige si quieres importar o exportar bibliotecas"; "library-export" -> "Selecciona qué categorías y estadísticas quieres guardar"; "library-import" -> "Revisa el contenido del paquete antes de importarlo"; "library-delete" -> "Elimina de forma permanente una o varias bibliotecas"; "statistics-menu" -> "Consulta el aprendizaje y el uso de las modalidades"; "learning-stats" -> "Observaciones orientativas basadas en los intentos registrados"; "usage-stats" -> "Partidas completadas por modalidad, tipo y dificultad"; else -> "Configuración" }}", fontSize = 15.sp, color = Color(0xFF667085))
            }
            if (parentSection == "menu") {
                item {
                    RewardModeSelectorCard(
                        rewardMode = rewardMode,
                        onSelectTime = {
                            rewardMode = RewardMode.TIME
                            preferences.edit().putString(RewardMode.PREFERENCE_KEY, "time").apply()
                            setupAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                            parentSection = if (setupAccessibilityEnabled) "menu" else "device-setup"
                        },
                        onSelectStars = {
                            rewardMode = RewardMode.STARS
                            val activeDeadline = preferences.getLong("play_deadline_ms", 0L)
                            if (activeDeadline > 0L) {
                                balanceSeconds = ((activeDeadline - System.currentTimeMillis() + 999L) / 1000L).toInt().coerceAtLeast(0)
                            }
                            preferences.edit().putString(RewardMode.PREFERENCE_KEY, "stars")
                                .putInt("balance_seconds", balanceSeconds)
                                .putLong("play_deadline_ms", 0L)
                                .putBoolean("play_session_authorized", false)
                                .putBoolean("play_paused_for_screen_lock", false)
                                .apply()
                            context.stopService(Intent(context, PlayTimeService::class.java))
                        }
                    )
                }
                item {
                    ParentMenuTourCard(
                        onOpenTour = {
                            tourReturnTarget = "parent-menu"
                            screen = "tour"
                        }
                    )
                }
                item {
                    ParentMenuNavigationCard("🎮", "Configurar actividades", "Opciones específicas de cada modalidad") {
                        parentSection = "game-modes"
                    }
                }
                item { ParentMenuSectionLabel("CONFIGURACIÓN GENERAL") }
                item {
                    ParentMenuNavigationCard(
                        "🔤",
                        "Tipografía de los ejercicios",
                        if (linkedExerciseFont) "Playwrite ES · Letra escolar ligada" else "Fuente del teléfono"
                    ) {
                        parentSection = "typography"
                    }
                }
                item {
                    ParentMenuActiveGamesCard(onClick = { parentSection = "active-games" })
                }
                item {
                    ParentMenuNavigationCard("⏱️", "Recompensas y límites", "Minutos/Estrellas por dificultad") {
                        parentSection = "rewards"
                    }
                }
                item {
                    ParentMenuNavigationCard("🔊", "Ajustes de sonido", "Música general y audio de cuentos") {
                        parentSection = "sound-settings"
                    }
                }
                item {
                    ParentMenuNavigationCard("☁️⬇️", "Descargar contenido online", "Explorar y descargar bibliotecas y paquetes de contenido desde Internet") {
                        parentSection = "basic-library"
                    }
                }
                item {
                    ParentMenuNavigationCard("📦", "Importar y exportar bibliotecas", "Copias selectivas de Tarjetas, Palabras y Secuencias") {
                        transferExportCategories = ContentStorage.Modality.entries.associateWith { modality -> LibraryPackageManager.categories(context, modality).map { it.directory }.toSet() }
                        transferExportStatistics = emptySet()
                        transferMessage = ""
                        parentSection = "library-transfer"
                    }
                }

                item {
                    ParentMenuNavigationCard("📊", "Estadísticas", "Consulta el aprendizaje y el uso de la aplicación") {
                        parentSection = "statistics-menu"
                    }
                }
                item {
                    ParentMenuAboutCard(onClick = { parentSection = "about" })
                }
                item {
                    ParentMenuNavigationCard("🔐", "Cambiar PIN", "Modificar el PIN de acceso familiar") {
                        parentSection = "security"
                    }
                }
                item { ParentMenuSectionDivider() }
                item {
                    ParentMenuNavigationCard(
                        "⚙️",
                        "Configuración de permisos",
                        if (rewardMode.isStars) "Puedes prepararlos para utilizar el modo Tiempo" else "Permisos y servicio de accesibilidad"
                    ) {
                        parentSection = "device-setup"
                    }
                }
            } else {
                item { OutlinedButton(onClick = { parentSection = when (parentSection) { "game-modes" -> "menu"; "active-games", "music", "rewards", "security", "device-setup", "about", "library-transfer", "typography" -> "menu"; "music", "stories-audio" -> "sound-settings"; "library-export", "library-delete" -> "library-transfer"; "library-import" -> if (transferRemoteLibraryName != null) "basic-library" else "library-transfer"; "statistics-menu" -> "menu"; "learning-stats", "usage-stats" -> "statistics-menu"; "cards-settings", "sequences-settings", "words-settings", "stories-settings" -> "game-modes"; "stories-content-settings", "stories-objects" -> "stories-settings"; "stories-library", "stories-collections" -> "stories-content-settings"; "stories-editor" -> "stories-library"; "stories-structure" -> "stories-editor"; "stories-scene-editor" -> "stories-editor"; "stories-object-create", "stories-object-collection", "stories-object-inventory" -> "stories-objects"; "sequence-type-temporal", "sequence-type-procesos", "sequence-type-causa", "sequence-type-ciclos" -> "sequences-settings"; "sequence-editor-temporal" -> "sequence-library"; "sequence-library", "sequence-categories" -> "sequences-settings"; "sequence-library-temporal", "sequence-categories-temporal" -> "sequence-type-temporal"; "sequence-library-procesos", "sequence-categories-procesos" -> "sequence-type-procesos"; "sequence-library-causa", "sequence-categories-causa" -> "sequence-type-causa"; "sequence-library-ciclos", "sequence-categories-ciclos" -> "sequence-type-ciclos"; "words", "categories" -> "cards-settings"; "word-library", "word-categories" -> "words-settings"; else -> "menu" } }) { Text(if (parentSection == "game-modes") "← Volver al menú de control" else if (parentSection == "music" || parentSection == "stories-audio") "← Volver a Ajustes de sonido" else if (parentSection == "cards-settings" || parentSection == "sequences-settings" || parentSection == "words-settings") "← Volver a los modos de juego" else if (parentSection == "stories-settings") "← Volver a los modos de juego" else if (parentSection == "stories-content-settings" || parentSection == "stories-objects") "← Volver a Cuentos" else if (parentSection == "stories-library" || parentSection == "stories-collections") "← Volver a Gestión de cuentos" else if (parentSection == "stories-object-create" || parentSection == "stories-object-collection" || parentSection == "stories-object-inventory") "← Volver a Objetos" else if (parentSection.startsWith("sequence-type-")) "← Volver a Secuencias" else if (parentSection == "sequence-editor-temporal") "← Volver a la biblioteca" else if (parentSection == "sequence-library" || parentSection == "sequence-categories") "← Volver a Secuencias" else if (parentSection.startsWith("sequence-library-") || parentSection.startsWith("sequence-categories-")) "← Volver al tipo de secuencia" else if (parentSection == "words" || parentSection == "categories") "← Volver a Tarjetas" else if (parentSection == "word-library" || parentSection == "word-categories") "← Volver a Palabras" else if (parentSection == "library-export" || parentSection == "library-import" || parentSection == "library-delete") if (parentSection == "library-import" && transferRemoteLibraryName != null) "← Volver al catálogo" else "← Volver a gestionar bibliotecas" else if (parentSection == "learning-stats" || parentSection == "usage-stats") "← Volver a estadísticas" else "← Volver al menú de control") } }
                when (parentSection) {
                    "sound-settings" -> {
                        item { Text("Ajustes de sonido", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "music" }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("🎵 Música de fondo", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("Elige la música que acompaña el uso general de la aplicación.", fontSize = 14.sp, color = Color(0xFF667085))
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "stories-audio" }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("📖 Audio de cuentos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("Configura el volumen del ambiente durante la narración.", fontSize = 14.sp, color = Color(0xFF667085))
                                }
                            }
                        }
                    }
                    "statistics-menu" -> {
                        item { Text("Estadísticas disponibles", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "learning-stats" }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("📚 Estadísticas de aprendizaje", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("Resultados, aciertos, errores y ejercicios completados.", fontSize = 14.sp, color = Color(0xFF667085))
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "usage-stats" }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("🎮 Estadísticas de uso", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("Partidas completadas por modalidad, tipo y dificultad.", fontSize = 14.sp, color = Color(0xFF667085))
                                }
                            }
                        }
                    }
                    "usage-stats" -> {
                        item { UsageStatisticsScreen(context) }
                    }
                    "basic-library" -> {
                        item {
                            Text("Bibliotecas disponibles", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Paquetes ZIP publicados en GitHub. Puedes descargarlos e importar solo el contenido que necesites.", fontSize = 14.sp, color = Color(0xFF667085))
                        }
                        item {
                            OutlinedButton(
                                onClick = {
                                    remoteLibraryScope.launch {
                                        remoteLibraryLoading = true
                                        remoteLibraryMessage = ""
                                        try {
                                            remoteLibraryPackages = withContext(Dispatchers.IO) { RemoteLibraryRepository.listPackages() }
                                            if (remoteLibraryPackages.isEmpty()) remoteLibraryMessage = "Todavía no hay paquetes ZIP publicados."
                                        } catch (error: Exception) {
                                            remoteLibraryMessage = "No se ha podido consultar GitHub: ${error.message ?: "error de conexión"}"
                                        } finally {
                                            remoteLibraryLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !remoteLibraryLoading
                            ) { Text(if (remoteLibraryLoading) "Consultando GitHub…" else "↻ Actualizar lista") }
                        }
                        if (remoteLibraryMessage.isNotBlank()) {
                            item { Text(remoteLibraryMessage, fontSize = 13.sp, color = if (remoteLibraryMessage.startsWith("No se ha podido")) Color(0xFFB3261E) else Color(0xFF667085)) }
                        }
                        if (remoteLibraryPackages.isEmpty() && !remoteLibraryLoading && remoteLibraryMessage.isBlank()) {
                            item { Text("Pulsa «Actualizar lista» para consultar los paquetes disponibles.", fontSize = 14.sp, color = Color(0xFF667085)) }
                        }
                        remoteLibraryPackages.forEach { remotePackage ->
                            val savedSha = RemoteLibraryRepository.downloadedSha(context, remotePackage.name)
                            val installedSha = RemoteLibraryRepository.installedSha(context, remotePackage.name)
                            val cachedFile = RemoteLibraryRepository.cachedFile(context, remotePackage.name)
                            val isInstalled = installedSha == remotePackage.sha
                            val isUpdate = (installedSha != null && installedSha != remotePackage.sha) ||
                                (savedSha != null && savedSha != remotePackage.sha)
                            val isDownloaded = !isInstalled && savedSha == remotePackage.sha && cachedFile.isFile && cachedFile.length() > 0L
                            val isDownloading = remoteLibraryDownloadingName == remotePackage.name
                            item {
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(remotePackage.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        Text(RemoteLibraryRepository.formatSize(remotePackage.size), fontSize = 12.sp, color = Color(0xFF667085))
                                        Text(
                                            when {
                                                isDownloading -> "Descargando biblioteca…"
                                                isInstalled -> "Instalada"
                                                isUpdate -> "Actualización disponible"
                                                isDownloaded -> "Descargado, pendiente de importar"
                                                else -> "Disponible"
                                            },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when {
                                                isDownloading -> Color(0xFF3567A8)
                                                isInstalled -> Color(0xFF24733B)
                                                isUpdate -> Color(0xFFB26A00)
                                                isDownloaded -> Color(0xFF24733B)
                                                else -> Color(0xFF667085)
                                            }
                                        )
                                        if (isDownloading) {
                                            val total = remoteLibraryTotalBytes
                                            val downloaded = remoteLibraryDownloadedBytes
                                            if (total > 0L) {
                                                LinearProgressIndicator(
                                                    progress = { (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            } else {
                                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                            }
                                            val percentage = if (total > 0L) (downloaded * 100L / total).coerceIn(0L, 100L) else null
                                            Text(
                                                "${percentage?.let { "$it%" } ?: "Calculando…"} · ${RemoteLibraryRepository.formatSize(downloaded)} / ${RemoteLibraryRepository.formatSize(if (total > 0L) total else remotePackage.size)}",
                                                fontSize = 12.sp,
                                                color = Color(0xFF667085)
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                if (isInstalled) {
                                                    // The current release is already installed; no ZIP is needed.
                                                } else if (isDownloaded) {
                                                    remoteLibraryPendingImport = remotePackage
                                                } else {
                                                    remoteLibraryScope.launch {
                                                        remoteLibraryDownloadingName = remotePackage.name
                                                        remoteLibraryDownloadedBytes = 0L
                                                        remoteLibraryTotalBytes = remotePackage.size
                                                        remoteLibraryMessage = ""
                                                        try {
                                                            val file = withContext(Dispatchers.IO) {
                                                                var lastProgressReported = 0L
                                                                RemoteLibraryRepository.download(context, remotePackage) { downloaded, total ->
                                                                    if (downloaded - lastProgressReported >= 512L * 1024L || (total > 0L && downloaded == total)) {
                                                                        lastProgressReported = downloaded
                                                                        remoteLibraryScope.launch {
                                                                            remoteLibraryDownloadedBytes = downloaded
                                                                            if (total > 0L) remoteLibraryTotalBytes = total
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            RemoteLibraryRepository.saveDownloaded(context, remotePackage.name, remotePackage.sha)
                                                            remoteLibraryDownloadedBytes = file.length()
                                                            remoteLibraryTotalBytes = remotePackage.size
                                                            remoteLibraryPendingImport = remotePackage
                                                        } catch (error: Exception) {
                                                            remoteLibraryMessage = "No se ha podido descargar el paquete: ${error.message ?: "error de conexión"}"
                                                        } finally {
                                                            remoteLibraryDownloadingName = null
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = remoteLibraryDownloadingName == null && !remoteLibraryLoading && !isInstalled,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(when {
                                                isDownloading -> "Descargando…"
                                                isInstalled -> "Instalada"
                                                isDownloaded -> "Importar ahora"
                                                isUpdate -> "⬇️ Descargar actualización"
                                                else -> "⬇️ Descargar"
                                            })
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Text("Las descargas se realizan en segundo plano. Puedes seguir consultando el catálogo; solo se permite una descarga simultánea.", fontSize = 12.sp, color = Color(0xFF667085))
                        }
                    }
                    "library-transfer" -> {
                        item { Text("Gestionar bibliotecas", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item { Text("Elige qué operación quieres realizar. Cada proceso tiene su propia pantalla para mantener separadas la importación y la exportación.", fontSize = 14.sp, color = Color(0xFF667085)) }
                        item {
                            Card(Modifier.fillMaxWidth().clickable {
                                transferExportCategories = ContentStorage.Modality.entries.associateWith { modality ->
                                    LibraryPackageManager.categories(context, modality).map { it.directory }.toSet()
                                }
                                transferExportStatistics = emptySet()
                                transferExportStories = StoryRepository.list(context).map { "${it.collection}/${it.folderName}" }.toSet()
                                transferMessage = ""
                                parentSection = "library-export"
                            }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("📤", fontSize = 30.sp)
                                    Column {
                                        Text("Exportar bibliotecas", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text("Crear un ZIP con las categorías y modalidades que elijas", color = Color(0xFF667085))
                                    }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable {
                                transferImportUsageStatistics = false
                                transferMessage = ""
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                unifiedImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("📥", fontSize = 30.sp)
                                    Column {
                                        Text("Importar bibliotecas", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text("Seleccionar un ZIP y revisar su contenido antes de incorporarlo", color = Color(0xFF667085))
                                    }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable {
                                deleteSelectedItems = emptySet()
                                deleteExpandedCategories = emptySet()
                                deleteConfirmArmed = false
                                transferMessage = ""
                                parentSection = "library-delete"
                            }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🗑️", fontSize = 30.sp)
                                    Column {
                                        Text("Eliminar biblioteca", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB3261E))
                                        Text("Borrar Tarjetas, Palabras o Secuencias", color = Color(0xFF667085))
                                    }
                                }
                            }
                        }
                        if (transferMessage.isNotBlank()) item { Text(transferMessage, color = if (transferMessage.startsWith("No se puede") || transferMessage.startsWith("No se pudo")) Color(0xFFB3261E) else Color(0xFF24733B), fontSize = 14.sp) }
                    }
                    "library-export" -> {
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
                                        checked = availableStories.all { "${it.collection}/${it.folderName}" in transferExportStories },
                                        onCheckedChange = { checked ->
                                            transferExportStories = if (checked) availableStories.map { "${it.collection}/${it.folderName}" }.toSet() else emptySet()
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
                                                checked = key in transferExportStories,
                                                onCheckedChange = { checked ->
                                                    transferExportStories = transferExportStories.toMutableSet().also {
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
                        val requiredExportObjects = LibraryPackageManager.requiredExportObjects(context, transferExportStories)
                        item {
                            Text("Objetos y coleccionables", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Los objetos requeridos por los cuentos seleccionados se incluirán automáticamente. También puedes seleccionar objetos por separado.", fontSize = 12.sp, color = Color(0xFF667085))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = availableObjectsForExport.isNotEmpty() && availableObjectsForExport.all {
                                        val key = "${it.collection}/${it.folderName}"
                                        key in requiredExportObjects || key in transferExportObjects
                                    },
                                    onCheckedChange = { checked ->
                                        transferExportObjects = if (checked) availableObjectsForExport
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
                                            checked = isRequired || key in transferExportObjects,
                                            enabled = !isRequired,
                                            onCheckedChange = { checked ->
                                                transferExportObjects = transferExportObjects.toMutableSet().also { if (checked) it.add(key) else it.remove(key) }
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = categoriesForModality.all { it.directory in transferExportCategories[modality].orEmpty() },
                                            onCheckedChange = { checked -> transferExportCategories = transferExportCategories.toMutableMap().also { it[modality] = if (checked) categoriesForModality.map { category -> category.directory }.toSet() else emptySet() } }
                                        )
                                        Text("Seleccionar todas las categorías")
                                    }
                                    categoriesForModality.forEach { category ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = category.directory in transferExportCategories[modality].orEmpty(),
                                                onCheckedChange = { checked -> transferExportCategories = transferExportCategories.toMutableMap().also { map -> map[modality] = map[modality].orEmpty().toMutableSet().also { set -> if (checked) set.add(category.directory) else set.remove(category.directory) } } }
                                            )
                                            Column {
                                                Text(category.name)
                                                Text("${category.exerciseCount} ejercicios", fontSize = 12.sp, color = Color(0xFF667085))
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = modality in transferExportStatistics,
                                            onCheckedChange = { checked -> transferExportStatistics = transferExportStatistics.toMutableSet().also { if (checked) it.add(modality) else it.remove(modality) } }
                                        )
                                        Text("Incluir estadísticas de aprendizaje")
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = modality in transferExportUsageStatistics,
                                            onCheckedChange = { checked -> transferExportUsageStatistics = transferExportUsageStatistics.toMutableSet().also { if (checked) it.add(modality) else it.remove(modality) } }
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
                                    val selected = transferExportCategories.filterValues { it.isNotEmpty() }
                                    if (selected.isEmpty() && transferExportStories.isEmpty() && transferExportObjects.isEmpty()) transferMessage = "Selecciona al menos una categoría, cuento u objeto."
                                    else {
                                        preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                        unifiedExportLauncher.launch("biblioteca.zip")
                                    }
                                },
                                enabled = transferExportCategories.values.any { it.isNotEmpty() } || transferExportStories.isNotEmpty() || transferExportObjects.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Exportar selección") }
                        }
                        if (transferMessage.isNotBlank()) item { Text(transferMessage, color = if (transferMessage.startsWith("No se puede") || transferMessage.startsWith("No se pudo")) Color(0xFFB3261E) else Color(0xFF24733B), fontSize = 14.sp) }
                    }
                    "library-delete" -> {
                        val labels = mapOf(ContentStorage.Modality.TARJETAS to "Tarjetas", ContentStorage.Modality.PALABRAS to "Palabras", ContentStorage.Modality.SECUENCIAS to "Secuencias")
                        val modalityCategories = ContentStorage.Modality.entries.associateWith { modality ->
                            val exercises = ExerciseRepository.list(context, modality)
                            val prefKey = when (modality) {
                                ContentStorage.Modality.TARJETAS -> "categories"
                                ContentStorage.Modality.PALABRAS -> "letter_word_categories_v1"
                                ContentStorage.Modality.SECUENCIAS -> "sequence_categories_v1"
                            }
                            val registered = (preferences.getString(prefKey, "") ?: "").split("|").filter { it.isNotBlank() }
                            (registered + exercises.map { it.metadata.category }).distinctBy { it.trim().lowercase() }.sortedBy { it.lowercase() }
                                .map { category -> category to exercises.filter { it.metadata.category.equals(category, true) } }
                        }
                        val allExercises = modalityCategories.values.flatten().flatMap { it.second }
                        val selectedCategories = deleteSelectedItems.filter { it.startsWith("C|") }
                        val selectedCategoryCount = selectedCategories.size
                        val selectedExerciseCount = allExercises.count { stored ->
                            ("C|" + stored.metadata.modality.name + "|" + stored.metadata.category) in selectedCategories ||
                                ("E|" + stored.directory.absolutePath) in deleteSelectedItems
                        }
                        val selectedStatsCount = allExercises.count { stored ->
                            val path = stored.directory.absolutePath
                            ("S|" + path) in deleteSelectedItems && ("E|" + path) !in deleteSelectedItems &&
                                ("C|" + stored.metadata.modality.name + "|" + stored.metadata.category) !in selectedCategories
                        }
                        item {
                            Text("Eliminar contenido", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB3261E))
                            Text("Selecciona categorías completas, ejercicios individuales o solamente las estadísticas de cada ejercicio. Las imágenes se eliminan junto con su ejercicio.", fontSize = 14.sp, color = Color(0xFF667085))
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
                                                key in deleteSelectedItems || (pair.second.isNotEmpty() && pair.second.all { ("E|" + it.directory.absolutePath) in deleteSelectedItems })
                                            },
                                            onCheckedChange = { checked ->
                                                val next = deleteSelectedItems.toMutableSet()
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
                                                deleteSelectedItems = next
                                                deleteConfirmArmed = false
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
                                val selectedCount = if (categoryKey in deleteSelectedItems) exercises.size else exercises.count { ("E|" + it.directory.absolutePath) in deleteSelectedItems }
                                val expanded = categoryKey in deleteExpandedCategories
                                item {
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                        Column(Modifier.padding(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = categoryKey in deleteSelectedItems || (exercises.isNotEmpty() && selectedCount == exercises.size),
                                                    onCheckedChange = { checked ->
                                                        val next = deleteSelectedItems.toMutableSet()
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
                                                        deleteSelectedItems = next
                                                        deleteConfirmArmed = false
                                                    }
                                                )
                                                Column(Modifier.weight(1f)) {
                                                    Text(category, fontWeight = FontWeight.Bold)
                                                    Text(exercises.size.toString() + " ejercicios" + if (selectedCount in 1 until exercises.size) " · selección parcial" else "", fontSize = 12.sp, color = Color(0xFF667085))
                                                }
                                                Text(if (expanded) "Ocultar" else "Ver", color = Color(0xFF6750A4),
                                                    modifier = Modifier.clickable {
                                                        deleteExpandedCategories = if (expanded) deleteExpandedCategories - categoryKey else deleteExpandedCategories + categoryKey
                                                    }.padding(8.dp))
                                            }
                                            if (expanded) {
                                                if (exercises.isEmpty()) Text("Categoría vacía: selecciónala para eliminarla.", fontSize = 13.sp, color = Color(0xFF667085))
                                                exercises.forEach { stored ->
                                                    val path = stored.directory.absolutePath
                                                    val exerciseKey = "E|" + path
                                                    val statsKey = "S|" + path
                                                    val exerciseChecked = categoryKey in deleteSelectedItems || exerciseKey in deleteSelectedItems
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(checked = exerciseChecked, onCheckedChange = { checked ->
                                                            val next = deleteSelectedItems.toMutableSet()
                                                            next.remove(categoryKey)
                                                            next.remove("S|" + path)
                                                            if (checked) next.add(exerciseKey) else next.remove(exerciseKey)
                                                            deleteSelectedItems = next
                                                            deleteConfirmArmed = false
                                                        })
                                                        Column(Modifier.weight(1f)) {
                                                            Text(stored.metadata.title, fontWeight = FontWeight.Medium)
                                                            Text(stored.metadata.difficulty, fontSize = 12.sp, color = Color(0xFF667085))
                                                        }
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 42.dp)) {
                                                        Checkbox(checked = statsKey in deleteSelectedItems, enabled = !exerciseChecked, onCheckedChange = { checked ->
                                                            val next = deleteSelectedItems.toMutableSet()
                                                            if (checked) next.add(statsKey) else next.remove(statsKey)
                                                            deleteSelectedItems = next
                                                            deleteConfirmArmed = false
                                                        })
                                                        Text("Eliminar solo las estadísticas", fontSize = 12.sp, color = Color(0xFF667085))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Text("Selección: " + selectedCategoryCount + " categorías, " + selectedExerciseCount + " ejercicios y " + selectedStatsCount + " conjuntos de estadísticas. El PIN, los límites y las preferencias generales se conservarán.", fontSize = 13.sp, color = Color(0xFF667085))
                        }
                        item {
                            if (!deleteConfirmArmed) {
                                Button(onClick = { deleteConfirmArmed = true }, enabled = deleteSelectedItems.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) {
                                    Text("Continuar con la eliminación")
                                }
                            } else {
                                Text("CONFIRMACIÓN: se eliminarán " + selectedCategoryCount + " categorías, " + selectedExerciseCount + " ejercicios y " + selectedStatsCount + " conjuntos de estadísticas. Esta acción no se puede deshacer.", fontWeight = FontWeight.Bold, color = Color(0xFFB3261E))
                                Button(onClick = {
                                    try {
                                        var removedExercises = 0
                                        var removedStatistics = 0
                                        ContentStorage.Modality.entries.forEach { modality ->
                                            val exercises = ExerciseRepository.list(context, modality)
                                            val categoriesToRemove = deleteSelectedItems.filter { it.startsWith("C|" + modality.name + "|") }
                                                .map { it.removePrefix("C|" + modality.name + "|") }.toSet()
                                            exercises.forEach { stored ->
                                                val path = stored.directory.absolutePath
                                                if (stored.metadata.category in categoriesToRemove || ("E|" + path) in deleteSelectedItems) {
                                                    ExerciseRepository.delete(context, modality, stored.directory)
                                                    removedExercises++
                                                } else if (("S|" + path) in deleteSelectedItems) {
                                                    if (ExerciseRepository.deleteStatistics(stored.directory)) removedStatistics++
                                                }
                                            }
                                            if (categoriesToRemove.isNotEmpty()) {
                                                categoriesToRemove.forEach { category ->
                                                    val categoryDirectory = ContentStorage.categoryDirectory(context, modality, category)
                                                    if (categoryDirectory.list()?.isEmpty() == true) categoryDirectory.delete()
                                                }
                                                val prefKey = when (modality) {
                                                    ContentStorage.Modality.TARJETAS -> "categories"
                                                    ContentStorage.Modality.PALABRAS -> "letter_word_categories_v1"
                                                    ContentStorage.Modality.SECUENCIAS -> "sequence_categories_v1"
                                                }
                                                val current = (preferences.getString(prefKey, "") ?: "").split("|").filter { it.isNotBlank() }
                                                preferences.edit().putString(prefKey, current.filterNot { name -> categoriesToRemove.any { it.equals(name, true) } }.joinToString("|")).apply()
                                            }
                                        }
                                        categoryNames = (preferences.getString("categories", "") ?: "").split("|").filter { it.isNotBlank() }
                                        wordLibraryState.saveCategories((preferences.getString("letter_word_categories_v1", "") ?: "").split("|").map { it.trim() }.filter { it.isNotBlank() })
                                        sequenceCategoryNames = (preferences.getString("sequence_categories_v1", "") ?: "").split("|").filter { it.isNotBlank() }
                                        pairs = ExerciseUiStorage.loadPairs(context, ContentStorage.Modality.TARJETAS)
                                        wordExercises = wordLibraryState.reloadExercises()
                                        sequenceExercises = sequenceLibraryState.reloadExercises()
                                        deleteSelectedItems = emptySet()
                                        deleteExpandedCategories = emptySet()
                                        deleteConfirmArmed = false
                                        transferMessage = "Eliminación completada: " + removedExercises + " ejercicios y " + removedStatistics + " estadísticas eliminados."
                                        parentSection = "library-transfer"
                                    } catch (e: Exception) {
                                        deleteConfirmArmed = false
                                        transferMessage = "No se pudo completar la eliminación: " + (e.message ?: "error")
                                    }
                                }, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) {
                                    Text("Sí, eliminar definitivamente")
                                }
                                OutlinedButton(onClick = { deleteConfirmArmed = false }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Cancelar y conservar los datos")
                                }
                            }
                        }
                    }
                    "learning-stats" -> {
                        val records = ContentStorage.Modality.entries.flatMap { modality ->
                            ExerciseRepository.list(context, modality).mapNotNull { stored ->
                                runCatching { Triple(stored, ExerciseRepository.readStatistics(stored.directory), modality) }.getOrNull()
                            }
                        }
                        val totalAttempts = records.sumOf { it.second.optInt("attempts", 0) }
                        val totalCorrect = records.sumOf { it.second.optInt("correct", 0) }
                        val totalIncorrect = records.sumOf { it.second.optInt("incorrect", 0) }
                        val totalCompleted = records.sumOf { it.second.optInt("completed", 0) }
                        fun percentage(correct: Int, attempts: Int): String =
                            if (attempts > 0) "${(correct * 100.0 / attempts).toInt()}%" else "Sin datos"


                        item { Text("Resumen de actividad", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("$totalCompleted ejercicios completados", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("$totalAttempts intentos registrados · $totalCorrect aciertos · $totalIncorrect errores", fontSize = 14.sp)
                                    Text("Acierto global: ${percentage(totalCorrect, totalAttempts)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        val modalityAreas = listOf(
                            Triple(ContentStorage.Modality.TARJETAS, "Tarjetas", "🃏"),
                            Triple(ContentStorage.Modality.PALABRAS, "Palabras", "🔤"),
                            Triple(ContentStorage.Modality.SECUENCIAS, "Secuencias", "🧩")
                        )
                        item { Text("Estadísticas por modalidad", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                        modalityAreas.forEach { (modality, title, icon) ->
                            val subset = records.filter { it.third == modality }
                            val attempts = subset.sumOf { it.second.optInt("attempts", 0) }
                            val correct = subset.sumOf { it.second.optInt("correct", 0) }
                            val incorrect = subset.sumOf { it.second.optInt("incorrect", 0) }
                            val completed = subset.sumOf { it.second.optInt("completed", 0) }
                            val expanded = modality in expandedLearningModalities
                            val categoryGroups = subset.groupBy { stored ->
                                val category = stored.first.metadata.category
                                if (modality == ContentStorage.Modality.SECUENCIAS) {
                                    val type = (stored.first.content as? SequenceExerciseContent)?.type?.displayName
                                    if (type.isNullOrBlank()) category else "$category · $type"
                                } else category
                            }.toSortedMap()
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        expandedLearningModalities = if (expanded) expandedLearningModalities - modality else expandedLearningModalities + modality
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(icon, fontSize = 22.sp)
                                            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.weight(1f))
                                            Text(if (attempts == 0) "Sin actividad" else "Ver detalle", fontSize = 12.sp, color = Color(0xFF667085))
                                            Text(if (expanded) "⌃" else "⌄", fontSize = 18.sp, color = Color(0xFF667085))
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("Aciertos: $correct", fontSize = 13.sp, color = Color(0xFF475467))
                                            Text("Errores: $incorrect", fontSize = 13.sp, color = Color(0xFF475467))
                                        }
                                        Text("Intentos: $attempts · Completados: $completed", fontSize = 13.sp, color = Color(0xFF475467))
                                        if (expanded) {
                                            Spacer(Modifier.height(2.dp))
                                            if (categoryGroups.isEmpty()) {
                                                Text("Todavía no hay actividad registrada en esta modalidad.", fontSize = 13.sp, color = Color(0xFF667085))
                                            } else {
                                                categoryGroups.forEach { (category, categoryRecords) ->
                                                    val categoryAttempts = categoryRecords.sumOf { it.second.optInt("attempts", 0) }
                                                    val categoryCorrect = categoryRecords.sumOf { it.second.optInt("correct", 0) }
                                                    val categoryIncorrect = categoryRecords.sumOf { it.second.optInt("incorrect", 0) }
                                                    val categoryCompleted = categoryRecords.sumOf { it.second.optInt("completed", 0) }
                                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                        Text(category, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                                        Text("${percentage(categoryCorrect, categoryAttempts)} de acierto · $categoryAttempts intentos · $categoryCompleted completados", fontSize = 12.sp, color = Color(0xFF667085))
                                                        Text("$categoryCorrect aciertos · $categoryIncorrect errores", fontSize = 12.sp, color = Color(0xFF667085))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F4F8))) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Cómo interpretar estos datos", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    Text("Esta aplicación no diagnostica, no realiza evaluaciones clínicas ni determina lo que un niño es o no es capaz de hacer. Solo ofrece actividades y recoge cómo interactúa con ellas. Los resultados dependen de los ejercicios disponibles, de su diseño, de la experiencia previa, de cómo el niño interpreta la actividad y de factores como la atención, la motivación, la memoria o la familiaridad con los patrones y símbolos.", fontSize = 13.sp, color = Color(0xFF667085))
                                    Text("Un acierto o un error aislado no demuestra que una capacidad esté adquirida o ausente. El niño puede encontrar estrategias, asociaciones o soluciones que el ejercicio no había previsto, e incluso descubrir relaciones que a un adulto podrían pasarle inadvertidas. Conviene observar el proceso, escuchar sus explicaciones y no limitar sus posibilidades a lo que reflejan estas cifras.", fontSize = 13.sp, color = Color(0xFF667085))
                                    Text("Estas estadísticas son únicamente orientativas y descriptivas. No son pruebas estandarizadas, diagnósticos ni conclusiones pedagógicas definitivas, y no sustituyen la valoración de profesionales cuando sea necesaria.", fontSize = 13.sp, color = Color(0xFF667085))
                                }
                            }
                        }
                        item { Text("Ejercicios con estadísticas legibles: ${records.size}. Si no aparecen datos, completa ejercicios para generar registros.", fontSize = 12.sp, color = Color(0xFF667085)) }
                    }
                    "library-import" -> {
                        val info = transferPackage
                        item { Text("Contenido del archivo", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        if (info == null) {
                            item { Text("No hay ningún paquete validado. Vuelve atrás y selecciona un archivo.") }
                        } else {
                            val localStoriesForImport = StoryRepository.list(context)
                            val storyNameConflicts = info.stories.filter { it.key in transferImportStories }.mapNotNull { incomingStory ->
                                val existingStory = localStoriesForImport.firstOrNull {
                                    it.collection == incomingStory.collection && NameRules.key(it.metadata.title) == NameRules.key(incomingStory.title)
                                }
                                existingStory?.let { incomingStory to it }
                            }
                            if (info.stories.isNotEmpty()) {
                                item {
                                    Text("Cuentos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("Se importan completos, con escenas, conexiones y recursos.", fontSize = 12.sp, color = Color(0xFF667085))
                                }
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = info.stories.all { it.key in transferImportStories },
                                            onCheckedChange = { checked -> transferImportStories = if (checked) info.stories.map { it.key }.toSet() else emptySet() }
                                        )
                                        Text("Seleccionar todos los cuentos")
                                    }
                                }
                                info.stories.forEach { story ->
                                    item {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = story.key in transferImportStories,
                                                onCheckedChange = { checked ->
                                                    transferImportStories = transferImportStories.toMutableSet().also {
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
                                val conflictDetails = LibraryPackageManager.objectConflicts(context, info, transferImportStories, transferImportObjects)
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
                                                            checked = transferStoryConflictDecisions[incomingStory.key] == "overwrite",
                                                            onCheckedChange = { checked ->
                                                                transferStoryConflictDecisions = if (checked) {
                                                                    transferStoryConflictDecisions + (incomingStory.key to "overwrite")
                                                                } else {
                                                                    transferStoryConflictDecisions - incomingStory.key
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
                                val conflictIndex = transferConflictIndex.coerceIn(0, (conflictDetails.size - 1).coerceAtLeast(0))
                                item {
                                    Text(if (conflictDetails.isEmpty()) "Revisión de objetos: sin conflictos" else "Conflicto ${conflictIndex + 1} de ${conflictDetails.size}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    if (conflictDetails.isNotEmpty()) {
                                        androidx.compose.material3.LinearProgressIndicator(progress = { (conflictIndex + 1).toFloat() / conflictDetails.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                                        Text("Resuelve las coincidencias una a una. Se comparan las imágenes reales y los datos de cada objeto.", fontSize = 12.sp, color = Color(0xFF667085))
                                    }
                                }
                                val localObjects = ObjectRepository.list(context)
                                val neededObjectNames = linkedSetOf<String>()
                                info.stories.filter { it.key in transferImportStories }.forEach { storyInfo ->
                                    val story = StoryRepository.read(File(info.staging, "cuentos/${storyInfo.key}"))
                                    story.metadata.connectionConditions.mapNotNullTo(neededObjectNames) { it.requiredObject }
                                    story.metadata.scenes.forEach { sceneName ->
                                        val sceneFile = File(File(File(story.directory, "escenas"), sceneName), "escena.json")
                                        if (sceneFile.isFile) {
                                            val scene = StorySceneJsonCodec.decode(JSONObject(sceneFile.readText(Charsets.UTF_8)))
                                            listOfNotNull(scene.awardedObject, scene.requiredObject).forEach { neededObjectNames.add(it) }
                                        }
                                    }
                                }
                                info.objects.filter { it.directory in neededObjectNames }.forEach { dependency ->
                                    val incoming = ObjectRepository.read(File(info.staging, "objetos/${dependency.key}"))
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
                                                                val incomingBitmap = BitmapFactory.decodeFile(File(incoming.directory, incoming.metadata.image).absolutePath)
                                                                if (incomingBitmap != null) Image(incomingBitmap.asImageBitmap(), contentDescription = "Objeto del ZIP", modifier = Modifier.width(84.dp).height(84.dp), contentScale = ContentScale.Fit)
                                                                Text(incoming.metadata.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                                Text("Aparece en la tienda: ${if (incoming.metadata.availableInStore) "Sí" else "No"}", fontSize = 11.sp, color = Color(0xFF667085))
                                                            }
                                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                Text("En este dispositivo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                                val existingBitmap = BitmapFactory.decodeFile(File(existing.directory, existing.metadata.image).absolutePath)
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
                                                                selected = transferObjectConflictDecisions[dependency.directory] == "reuse",
                                                                onClick = { transferObjectConflictDecisions = transferObjectConflictDecisions + (dependency.directory to "reuse") }
                                                            )
                                                            Text("Conservar el objeto de este dispositivo")
                                                        }
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            androidx.compose.material3.RadioButton(
                                                                selected = transferObjectConflictDecisions[dependency.directory] == "overwrite",
                                                                onClick = { transferObjectConflictDecisions = transferObjectConflictDecisions + (dependency.directory to "overwrite") }
                                                            )
                                                            Text("Sobrescribir el objeto existente")
                                                        }
                                                        if (transferObjectConflictDecisions[dependency.directory] == "overwrite") {
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
                                                            OutlinedButton(onClick = { transferConflictIndex = (conflictIndex - 1).coerceAtLeast(0) }, enabled = conflictIndex > 0) { Text("Anterior") }
                                                            OutlinedButton(onClick = { transferConflictIndex = (conflictIndex + 1).coerceAtMost(conflictDetails.lastIndex) }, enabled = conflictIndex < conflictDetails.lastIndex) { Text("Siguiente") }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (info.objects.isNotEmpty()) {
                                val importObjectsWithData = info.objects.map { objectInfo ->
                                    objectInfo to ObjectRepository.read(File(info.staging, "objetos/${objectInfo.key}"))
                                }
                                val requiredImportObjects = LibraryPackageManager.requiredImportObjects(info, transferImportStories)
                                item {
                                    Text("Objetos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("Los objetos requeridos por los cuentos seleccionados se incorporarán automáticamente. También puedes seleccionar objetos por separado. Esta operación no modifica el inventario del niño.", fontSize = 12.sp, color = Color(0xFF667085))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = info.objects.all { it.key in requiredImportObjects || it.key in transferImportObjects },
                                            onCheckedChange = { checked ->
                                                transferImportObjects = if (checked) info.objects.map { it.key }.toSet() else emptySet()
                                                transferObjectConflictDecisions = emptyMap()
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
                                        val selected = isRequired || objectInfo.key in transferImportObjects
                                        val local = ObjectRepository.list(context).firstOrNull {
                                            it.collection == incoming.collection && NameRules.key(it.metadata.name) == NameRules.key(incoming.metadata.name)
                                        }
                                        item {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = selected,
                                                    enabled = !isRequired,
                                                    onCheckedChange = { checked ->
                                                        transferImportObjects = transferImportObjects.toMutableSet().also {
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
                                                            when (transferObjectConflictDecisions[objectInfo.directory]) {
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
                                                            selected = transferObjectConflictDecisions[objectInfo.directory] == "reuse",
                                                            onClick = { transferObjectConflictDecisions = transferObjectConflictDecisions + (objectInfo.directory to "reuse") }
                                                        )
                                                        Text("Conservar el objeto de este dispositivo", fontSize = 12.sp)
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        androidx.compose.material3.RadioButton(
                                                            selected = transferObjectConflictDecisions[objectInfo.directory] == "overwrite",
                                                            onClick = { transferObjectConflictDecisions = transferObjectConflictDecisions + (objectInfo.directory to "overwrite") }
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
                                            val selected = isRequired || objectInfo.key in transferImportObjects
                                            val local = ObjectRepository.list(context).firstOrNull {
                                                it.collection == incoming.collection && NameRules.key(it.metadata.name) == NameRules.key(incoming.metadata.name)
                                            }
                                            item {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = selected,
                                                        enabled = !isRequired,
                                                        onCheckedChange = { checked ->
                                                            transferImportObjects = transferImportObjects.toMutableSet().also {
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
                                                                when (transferObjectConflictDecisions[objectInfo.directory]) {
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
                                                                selected = transferObjectConflictDecisions[objectInfo.directory] == "reuse",
                                                                onClick = { transferObjectConflictDecisions = transferObjectConflictDecisions + (objectInfo.directory to "reuse") }
                                                            )
                                                            Text("Conservar el objeto de este dispositivo", fontSize = 12.sp)
                                                        }
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            androidx.compose.material3.RadioButton(
                                                                selected = transferObjectConflictDecisions[objectInfo.directory] == "overwrite",
                                                                onClick = { transferObjectConflictDecisions = transferObjectConflictDecisions + (objectInfo.directory to "overwrite") }
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
                                    modalityInfo.categories.forEach { category ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = category.directory in transferImportCategories[modality].orEmpty(),
                                                onCheckedChange = { checked -> transferImportCategories = transferImportCategories.toMutableMap().also { map -> map[modality] = map[modality].orEmpty().toMutableSet().also { set -> if (checked) set.add(category.directory) else set.remove(category.directory) } } }
                                            )
                                            Column {
                                                Text(category.name)
                                                Text("${category.exerciseCount} ejercicios", fontSize = 12.sp, color = Color(0xFF667085))
                                            }
                                        }
                                    }
                                    Text(if (modalityInfo.includesLearningStatistics || modalityInfo.includesUsageStatistics) "El paquete incluye estadísticas." else "El paquete no incluye estadísticas.", fontSize = 12.sp, color = Color(0xFF667085))
                                }
                            }
                            if (info.modalities.any { it.includesLearningStatistics }) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = transferImportStatistics, onCheckedChange = { transferImportStatistics = it })
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
                                        Checkbox(checked = transferImportUsageStatistics, onCheckedChange = { transferImportUsageStatistics = it })
                                        Column {
                                            Text("Importar estadísticas de uso", fontWeight = FontWeight.SemiBold)
                                            Text("Desactivado por defecto. Si no lo marcas, se conservarán las estadísticas de uso del dispositivo.", fontSize = 12.sp, color = Color(0xFF667085))
                                        }
                                    }
                                }
                            }
                            item {
                                val exerciseConflicts = LibraryPackageManager.exerciseConflicts(context, info, transferImportCategories)
                                val groupedConflicts = ContentStorage.Modality.entries.mapNotNull { modality ->
                                    val conflicts = exerciseConflicts.filter { it.modality == modality }
                                    if (conflicts.isEmpty()) null else modality to conflicts
                                }
                                val unresolvedCount = exerciseConflicts.count {
                                    transferExerciseConflictDecisions[it.key] !in setOf("reuse", "overwrite")
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
                                            transferExerciseConflictDecisions[it.key] in setOf("reuse", "overwrite")
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
                                                            transferExerciseConflictDecisions = transferExerciseConflictDecisions +
                                                                conflicts.associate { it.key to "overwrite" }
                                                        }
                                                    ) {
                                                        Text("Sustituir todo", fontSize = 12.sp)
                                                    }
                                                    OutlinedButton(
                                                        modifier = Modifier.weight(1f),
                                                        onClick = {
                                                            transferExerciseConflictDecisions = transferExerciseConflictDecisions +
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
                                                            containerColor = if (transferExerciseConflictDecisions[conflict.key] == "overwrite") Color(0xFFFFF4E5) else Color.White
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
                                                                    selected = transferExerciseConflictDecisions[conflict.key] == "reuse",
                                                                    onClick = {
                                                                        transferExerciseConflictDecisions = transferExerciseConflictDecisions +
                                                                            (conflict.key to "reuse")
                                                                    }
                                                                )
                                                                Text("Conservar el ejercicio del dispositivo", fontSize = 12.sp)
                                                            }
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                androidx.compose.material3.RadioButton(
                                                                    selected = transferExerciseConflictDecisions[conflict.key] == "overwrite",
                                                                    onClick = {
                                                                        transferExerciseConflictDecisions = transferExerciseConflictDecisions +
                                                                            (conflict.key to "overwrite")
                                                                    }
                                                                )
                                                                Text("Sustituirlo por el del ZIP", fontSize = 12.sp)
                                                            }
                                                            if (transferExerciseConflictDecisions[conflict.key] == "overwrite") {
                                                                Text(
                                                                    if (transferImportStatistics) "También se importarán las estadísticas incluidas en el ZIP."
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
                                val finalConflictReview = LibraryPackageManager.objectConflicts(context, info, transferImportStories, transferImportObjects)
                                val resolvedCount = finalConflictReview.count { it.incoming.folderName in transferObjectConflictDecisions }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Resumen de coincidencias", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${resolvedCount} de ${finalConflictReview.size} conflictos resueltos.", fontSize = 12.sp, color = Color(0xFF667085))
                                    finalConflictReview.forEach { conflict ->
                                        val choice = when (transferObjectConflictDecisions[conflict.incoming.folderName]) {
                                            "reuse" -> "Se conservará el objeto local"
                                            "overwrite" -> "Se sobrescribirá el objeto local"
                                            else -> "Pendiente de decisión"
                                        }
                                        Text("• ${conflict.incoming.metadata.name}: $choice", fontSize = 12.sp)
                                    }
                                }
                            }
                            item {
                                Button(onClick = {
                                    try {
                                        val count = LibraryPackageManager.import(context, info, transferImportCategories, false, transferImportStatistics, transferImportUsageStatistics, transferImportStories, transferObjectConflictDecisions, transferStoryConflictDecisions, transferImportObjects, transferExerciseConflictDecisions)
                                        // Mark the exact release as installed only after the import completed
                                        // successfully. This state survives deletion of the downloaded ZIP.
                                        transferRemoteLibraryName?.let { packageName ->
                                            remoteLibraryPackages.firstOrNull { it.name == packageName }?.let { remotePackage ->
                                                RemoteLibraryRepository.saveInstalled(context, packageName, remotePackage.sha)
                                            }
                                        }
                                        transferPackage = null
                                        LibraryPackageManager.discard(info)
                                        transferRemoteLibraryName?.let { RemoteLibraryRepository.deleteCached(context, it) }
                                        transferRemoteLibraryName = null
                                        // La biblioteca de cuentos mantiene una lista en estado Compose.
                                        // Tras importar, hay que recargarla explícitamente para que los
                                        // cuentos recién copiados aparezcan sin reiniciar la aplicación.
                                        storedStories = StoryRepository.list(context)
                                        storedStoryObjects = ObjectRepository.list(context)
                                        // Registrar también las colecciones de objetos que llegan con
                                        // las dependencias de los cuentos importados. La biblioteca de
                                        // objetos se copia correctamente, pero la pantalla de colecciones
                                        // solo muestra las carpetas presentes en estas preferencias.
                                        val knownObjectCollectionFolders = storyObjectCollections.map {
                                            ObjectRepository.collectionFolderName(it)
                                        }.toSet()
                                        val importedObjectCollectionNames = storedStoryObjects
                                            .map { it.collection }
                                            .distinct()
                                            .filter { it !in knownObjectCollectionFolders }
                                            .map { it.replace('_', ' ') }
                                        if (importedObjectCollectionNames.isNotEmpty()) {
                                            storyObjectCollections = (storyObjectCollections + importedObjectCollectionNames)
                                                .distinctBy { ObjectRepository.collectionFolderName(it).lowercase() }
                                                .sortedBy { it.lowercase() }
                                            preferences.edit()
                                                .putString("story_object_collections", storyObjectCollections.joinToString("|"))
                                                .apply()
                                        }
                                        // Las colecciones importadas pueden no existir todavía en las
                                        // preferencias de este dispositivo. Registrar un nombre visible
                                        // derivado del identificador de carpeta, sin modificar la ruta.
                                        val knownCollectionFolders = storyCollections.map {
                                            StoryRepository.collectionFolderName(it)
                                        }.toSet()
                                        val importedCollectionNames = storedStories.map { it.collection }.distinct()
                                            .filter { it !in knownCollectionFolders }
                                            .map { it.replace('_', ' ') }
                                        if (importedCollectionNames.isNotEmpty()) {
                                            storyCollections = (storyCollections + importedCollectionNames)
                                                .distinctBy { it.lowercase() }
                                                .sortedBy { it.lowercase() }
                                            preferences.edit()
                                                .putString("story_collections", storyCollections.joinToString("|"))
                                                .apply()
                                        }
                                        pairs = ExerciseUiStorage.loadPairs(context, ContentStorage.Modality.TARJETAS)
                                        wordExercises = wordLibraryState.reloadExercises()
                                        sequenceExercises = sequenceLibraryState.reloadExercises()
                                        categoryNames = (categoryNames + ExerciseRepository.list(context, ContentStorage.Modality.TARJETAS).map { it.metadata.category }).distinct()
                                        wordLibraryState.mergeImportedCategories(ExerciseRepository.list(context, ContentStorage.Modality.PALABRAS).map { it.metadata.category }, false)
                                        sequenceCategoryNames = (sequenceCategoryNames + ExerciseRepository.list(context, ContentStorage.Modality.SECUENCIAS).map { it.metadata.category }).distinct()
                                        preferences.edit()
                                            .putString("categories", categoryNames.joinToString("|"))
                                            .putString("letter_word_categories_v1", wordExerciseCategories.joinToString("|"))
                                            .putString("sequence_categories_v1", sequenceCategoryNames.joinToString("|"))
                                            .apply()
                                        transferMessage = "Importación completada: $count ejercicios y ${transferImportStories.size} cuentos incorporados. El inventario del niño no se ha modificado."
                                        parentSection = "library-transfer"
                                    } catch (e: Exception) {
                                        transferMessage = "No se pudo completar la importación: ${e.message ?: "error"}"
                                    }
                                }, enabled = (transferImportCategories.values.any { it.isNotEmpty() } || transferImportStories.isNotEmpty()) &&
                                    LibraryPackageManager.exerciseConflicts(context, info, transferImportCategories).all { transferExerciseConflictDecisions[it.key] in setOf("reuse", "overwrite") } &&
                                    LibraryPackageManager.objectConflicts(context, info, transferImportStories, transferImportObjects).all { it.incoming.folderName in transferObjectConflictDecisions } &&
                                    info.stories.filter { it.key in transferImportStories }.all { incomingStory ->
                                        val duplicate = StoryRepository.list(context).any {
                                            it.collection == incomingStory.collection && NameRules.key(it.metadata.title) == NameRules.key(incomingStory.title)
                                        }
                                        !duplicate || transferStoryConflictDecisions[incomingStory.key] == "overwrite"
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Importar selección") }
                            }
                            item { OutlinedButton(onClick = {
                                LibraryPackageManager.discard(info)
                                transferPackage = null
                                parentSection = if (transferRemoteLibraryName != null) "basic-library" else "library-transfer"
                                transferRemoteLibraryName = null
                            }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar importación") } }
                        }
                        if (transferMessage.isNotBlank()) item { Text(transferMessage, fontSize = 14.sp, color = if (transferMessage.startsWith("No se pudo")) Color(0xFFB3261E) else Color(0xFF24733B)) }
                    }
                    "active-games" -> {
                        item { Text("Actividades activas", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item { Text("Desactiva los modos que no quieras incluir en la versión de pruebas. Los modos bloqueados mostrarán un candado y no se podrán abrir.", fontSize = 14.sp, color = Color(0xFF667085)) }
                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Checkbox(checked = gameActiveCards, onCheckedChange = { gameActiveCards = it; preferences.edit().putBoolean("game_active_cards", it).apply() })
                                    Column(Modifier.weight(1f)) { Text("Tarjetas", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Parejas de imágenes y palabras", fontSize = 14.sp, color = Color(0xFF667085)) }
                                    Text(if (gameActiveCards) "Activo" else "🔒", color = if (gameActiveCards) Color(0xFF24733B) else Color(0xFFB3261E))
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Checkbox(checked = gameActiveSequences, onCheckedChange = { gameActiveSequences = it; preferences.edit().putBoolean("game_active_sequences", it).apply() })
                                    Column(Modifier.weight(1f)) { Text("Secuencias", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Ordenar secuencias y procesos", fontSize = 14.sp, color = Color(0xFF667085)) }
                                    Text(if (gameActiveSequences) "Activo" else "🔒", color = if (gameActiveSequences) Color(0xFF24733B) else Color(0xFFB3261E))
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Checkbox(checked = gameActiveWords, onCheckedChange = { gameActiveWords = it; preferences.edit().putBoolean("game_active_words", it).apply() })
                                    Column(Modifier.weight(1f)) { Text("Palabras", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Formar palabras con letras", fontSize = 14.sp, color = Color(0xFF667085)) }
                                    Text(if (gameActiveWords) "Activo" else "🔒", color = if (gameActiveWords) Color(0xFF24733B) else Color(0xFFB3261E))
                                }
                            }
                        }
                                            item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Checkbox(checked = gameActiveStories, onCheckedChange = { gameActiveStories = it; preferences.edit().putBoolean("game_active_stories", it).apply() })
                                    Column(Modifier.weight(1f)) { Text("Cuentos", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Historias ilustradas e interactivas", fontSize = 14.sp, color = Color(0xFF667085)) }
                                    Text(if (gameActiveStories) "Activo" else "🔒", color = if (gameActiveStories) Color(0xFF24733B) else Color(0xFFB3261E))
                                }
                            }
                        }
                    }
"game-modes" -> {
                        item { Text("Actividades disponibles", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "cards-settings" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🖼️", fontSize = 30.sp)
                                    Column { Text("Tarjetas", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Biblioteca, categorías y parejas por partida", color = Color(0xFF667085)) }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "sequences-settings" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8EC))) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🌱", fontSize = 30.sp)
                                    Column { Text("Secuencias", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Configurar tipo de contenido y secuencias", color = Color(0xFF667085)) }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "words-settings" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF))) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🔤", fontSize = 30.sp)
                                    Column { Text("Palabras", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Configurar vocabulario y dificultad", color = Color(0xFF667085)) }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "stories-settings" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE3))) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("📖", fontSize = 30.sp)
                                    Column { Text("Cuentos", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Configurar y crear cuentos ilustrados", color = Color(0xFF667085)) }
                                }
                            }
                        }
                    }
                    "stories-settings" -> {
                        item { Text("Configurar Cuentos", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "stories-content-settings" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE5))) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("📚", fontSize = 30.sp)
                                    Column { Text("Gestión de cuentos", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Biblioteca de cuentos y colecciones", color = Color(0xFF667085)) }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "stories-objects" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE5))) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🎒", fontSize = 30.sp)
                                    Column { Text("Gestión de objetos", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Crear y administrar objetos reutilizables", color = Color(0xFF667085)) }
                                }
                            }
                        }
                    }
                    "stories-content-settings" -> {
                        item { Text("Gestión de cuentos", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "stories-library" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE5))) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("📖", fontSize = 30.sp)
                                    Column { Text("Biblioteca de cuentos", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Crear, editar y organizar cuentos ilustrados", color = Color(0xFF667085)) }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "stories-collections" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE5))) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🗂️", fontSize = 30.sp)
                                    Column { Text("Colecciones de cuentos", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Organizar cuentos por colecciones, sagas y universos", color = Color(0xFF667085)) }
                                }
                            }
                        }
                    }
                    "stories-library" -> {
                        item { Text("Biblioteca de cuentos", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item { Text("Cuentos guardados en este dispositivo.", fontSize = 14.sp, color = Color(0xFF667085)) }
                        items(storedStories) { stored ->
                            Card(Modifier.fillMaxWidth().clickable {
                                storyEditorState.loadStructure(stored.metadata)
                                storyEditorTitle = stored.metadata.title
                                storyEditorCover = stored.metadata.cover
                                storyEditorIllustrations = stored.metadata.scenes.associate { scene -> scene to runCatching { StoryRepository.readScene(stored, scene).metadata.illustration }.getOrNull() }
                                storyEditorAmbientAudio = stored.metadata.scenes.associate { scene -> scene to runCatching { StoryRepository.readScene(stored, scene).metadata.ambientAudio }.getOrNull() }
                                storyEditorNarrationAudio = stored.metadata.scenes.associate { scene -> scene to runCatching { StoryRepository.readScene(stored, scene).metadata.narrationAudio }.getOrNull() }
                                storyEditorState.loadDecisions(stored)
                                storyEditorAwardedObjectByScene = stored.metadata.scenes.associate { scene -> scene to runCatching { StoryRepository.readScene(stored, scene).metadata.awardedObject }.getOrNull() }
                                storyEditorAwardedObjectsByScene = stored.metadata.scenes.associate { scene ->
                                    scene to runCatching {
                                        val metadata = StoryRepository.readScene(stored, scene).metadata
                                        (listOfNotNull(metadata.awardedObject) + metadata.awardedObjects).distinct()
                                    }.getOrDefault(emptyList())
                                }
                                storyEditorUnlockedObjectsByScene = stored.metadata.scenes.associate { scene -> scene to runCatching { StoryRepository.readScene(stored, scene).metadata.unlockedObjects }.getOrDefault(emptyList()) }
                                storedStoryObjects = ObjectRepository.list(context)
                                storyEditorCollection = storyCollections.firstOrNull { StoryRepository.collectionFolderName(it) == stored.collection } ?: stored.collection
                                storyEditorScenes = stored.metadata.scenes.map { it.replace('_', ' ') }
                                editingStoryFolder = stored.folderName
                                editingStoryCollectionFolder = stored.collection
                                storyEditorSelectedScene = ""
                                storyEditorMessage = ""
                                parentSection = "stories-editor"
                            }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE5))) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val coverThumb = remember(stored.directory.absolutePath, stored.metadata.cover) {
                                        stored.metadata.cover?.let { File(stored.directory, it) }?.takeIf { it.isFile }
                                            ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
                                    }
                                    if (coverThumb != null) {
                                        Image(bitmap = coverThumb.asImageBitmap(), contentDescription = "Portada del cuento",
                                            modifier = Modifier.width(104.dp).aspectRatio(16f / 9f), contentScale = ContentScale.Fit)
                                    } else {
                                        Box(Modifier.width(104.dp).aspectRatio(16f / 9f).background(Color(0xFFFFF3E8), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                            Text("📖", fontSize = 28.sp)
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(stored.metadata.title, fontWeight = FontWeight.Bold)
                                        Text(storyCollections.firstOrNull { StoryRepository.collectionFolderName(it) == stored.collection } ?: stored.collection.replace('_', ' '), fontSize = 12.sp, color = Color(0xFF667085))
                                    }
                                }
                            }
                        }
                        item {
                            Button(onClick = {
                                storyEditorState.resetStructure(listOf("Escena 1"))
                                storyEditorTitle = ""
                                storyEditorCover = null
                                storyEditorIllustrations = emptyMap()
                                storyEditorAmbientAudio = emptyMap()
                                storyEditorNarrationAudio = emptyMap()
                                storyEditorState.resetDecisions()
                                storyEditorAwardedObjectByScene = emptyMap()
                                storyEditorAwardedObjectsByScene = emptyMap()
                                storyEditorUnlockedObjectsByScene = emptyMap()
                                storedStoryObjects = ObjectRepository.list(context)
                                storyEditorCollection = ""
                                storyEditorSummary = ""
                                storyEditorSceneText = ""
                                storyEditorScenes = listOf("Escena 1")
                                storyEditorSelectedScene = ""
                                editingStoryFolder = null
                                editingStoryCollectionFolder = null
                                storyEditorMessage = ""
                                parentSection = "stories-editor"
                            }, modifier = Modifier.fillMaxWidth()) { Text("＋ Crear nuevo cuento") }
                        }
                    }
                    "stories-editor" -> {
                        item {
                            StoryEditorScreen(
                                state = storyEditorState,
                                collections = storyCollections,
                                onPickCover = {
                                    preferences.edit().putBoolean("content_picker_active", true).apply()
                                    storyCoverPicker.launch(arrayOf("image/*"))
                                },
                                onOpenScene = { scene ->
                                    storedStoryObjects = ObjectRepository.list(context)
                                    parentSection = "stories-scene-editor"
                                },
                                onOpenStructure = { parentSection = "stories-structure" },
                                onBackToLibrary = { parentSection = "stories-library" }
                            )
                        }
                    }
                    "stories-structure" -> {
                        item {
                            StoryStructureScreen(
                                scenes = storyEditorScenes,
                                sceneSummaries = storyEditorScenes.associateWith { scene ->
                                    if (scene == storyEditorSelectedScene) {
                                        storyEditorSummary
                                    } else {
                                        val folder = runCatching { StoryRepository.sceneFolderName(scene) }.getOrNull()
                                        val stored = storedStories.firstOrNull {
                                            it.folderName == editingStoryFolder && it.collection == editingStoryCollectionFolder
                                        }
                                        if (folder != null && stored != null) {
                                            runCatching { StoryRepository.readScene(stored, folder).metadata.illustrationSummary }.getOrNull().orEmpty()
                                        } else ""
                                    }
                                },
                                initialConnections = storyEditorState.connections.value,
                                initialConditions = storyEditorState.connectionConditions.value,
                                initialAlternatives = storyEditorState.connectionAlternatives.value,
                                decisionOptionsByScene = storyEditorState.decisionOptionsByScene.value,
                                decisionPromptsByScene = storyEditorState.decisionPromptByScene.value,
                                initialStartScene = storyEditorState.startScene.value,
                                availableObjects = storedStoryObjects,
                                message = storyEditorMessage,
                                onSave = { connections, conditions, alternatives, startScene ->
                                    storyEditorState.connections.value = connections
                                    storyEditorState.connectionConditions.value = conditions
                                    storyEditorState.connectionAlternatives.value = alternatives
                                    storyEditorState.startScene.value = startScene
                                    if (persistStoryDraft() != null) {
                                        parentSection = "stories-editor"
                                        true
                                    } else false
                                },
                                onCancel = { parentSection = "stories-editor" }
                            )
                        }
                    }
                    "stories-scene-editor" -> {
                        item {
                            StorySceneEditorContent(
                                state = storyEditorState,
                                storyAmbientDuringNarration = storyAmbientDuringNarration,
                                onPickIllustration = {
                                    preferences.edit().putBoolean("content_picker_active", true).apply()
                                    storyIllustrationPicker.launch(arrayOf("image/*"))
                                },
                                onPickAmbient = {
                                    preferences.edit().putBoolean("content_picker_active", true).apply()
                                    storyAmbientPicker.launch(arrayOf("audio/*"))
                                },
                                onPickNarration = {
                                    preferences.edit().putBoolean("content_picker_active", true).apply()
                                    storyNarrationPicker.launch(arrayOf("audio/*"))
                                },
                                onBackToMap = { parentSection = "stories-editor" }
                            )
                        }
                    }
                    "stories-collections" -> {
                        item {
                            StoryCollectionsScreen(
                                context = context,
                                collections = storyCollections,
                                draft = storyCollectionDraft,
                                message = storyCollectionMessage,
                                editingCollection = editingStoryCollection,
                                disabledCollections = disabledStoryCollections,
                                coverRevision = storyCollectionCoverRevision,
                                collectionToDelete = storyCollectionToDelete,
                                onDraftChange = { storyCollectionDraft = NameRules.filterInput(it) },
                                onAddCollection = { storyCollectionsState.addCollection() },
                                onToggleEditing = { name ->
                                    editingStoryCollection = if (editingStoryCollection == name) null else name
                                },
                                onToggleCollection = { name -> storyCollectionsState.toggleCollection(name) },
                                onChooseCover = { name ->
                                    pendingStoryCollectionCover = name
                                    preferences.edit().putBoolean("content_picker_active", true).apply()
                                    storyCollectionCoverPicker.launch(arrayOf("image/*"))
                                },
                                onRemoveCover = { name ->
                                    StoryRepository.removeCollectionCover(context, StoryRepository.collectionFolderName(name))
                                    storyCollectionCoverRevision++
                                    storyCollectionMessage = "Portada de «$name» eliminada."
                                },
                                onDeleteAndStories = { name ->
                                    runCatching {
                                        storyCollectionsState.deleteCollection(name)
                                        storedStories = StoryRepository.list(context)
                                        if (selectedStoriesCollection == StoryRepository.collectionFolderName(name)) {
                                            selectedStoriesCollection = null
                                        }
                                        editingStoryCollection = null
                                    }.onFailure {
                                        storyCollectionMessage = it.message ?: "No se pudo eliminar la colección."
                                    }
                                },
                                onRequestDelete = { name -> storyCollectionsState.requestDelete(name) },
                                onCancelDelete = { storyCollectionsState.cancelDelete() }
                            )
                        }
                    }
                    "stories-objects" -> {
                        item { Text("Objetos", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { storyObjectEditorState.reset(); parentSection = "stories-object-create" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE5))) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("＋", fontSize = 30.sp, color = Color(0xFF87451F))
                                    Column(Modifier.weight(1f)) {
                                        Text("Creación de objetos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text("Crear y editar objetos reutilizables en los cuentos", fontSize = 14.sp, color = Color(0xFF667085))
                                    }
                                    Text("›", fontSize = 24.sp, color = Color(0xFF87451F))
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { storedStoryObjects = ObjectRepository.list(context); storyObjectInventoryState.refresh(context); storyObjectCollectionMessage = ""; parentSection = "stories-object-collection" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE5))) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("📚", fontSize = 30.sp)
                                    Column(Modifier.weight(1f)) {
                                        Text("Colección de objetos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text("Consultar y administrar la biblioteca común de objetos", fontSize = 14.sp, color = Color(0xFF667085))
                                    }
                                    Text("›", fontSize = 24.sp, color = Color(0xFF87451F))
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { storedStoryObjects = ObjectRepository.list(context); storyObjectInventoryState.refresh(context); parentSection = "stories-object-inventory" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE5))) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🎒", fontSize = 30.sp)
                                    Column(Modifier.weight(1f)) {
                                        Text("Inventario actual", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text("Consultar y gestionar los objetos que posee el niño", fontSize = 14.sp, color = Color(0xFF667085))
                                    }
                                    Text("›", fontSize = 24.sp, color = Color(0xFF87451F))
                                }
                            }
                        }
                    }
                    "stories-object-create" -> {
                        item {
                            StoryObjectEditorScreen(
                                editingFolder = storyObjectEditingFolder,
                                inInventory = storedInventoryFolders.contains(storyObjectEditingFolder),
                                name = storyObjectNameDraft,
                                picture = storyObjectPictureDraft,
                                collections = storyObjectCollections,
                                selectedCollection = storyObjectSelectedCollection,
                                collectionMenuExpanded = storyObjectCollectionMenuExpanded,
                                availableInStore = storyObjectAvailableInStore,
                                requiresUnlock = storyObjectRequiresUnlock,
                                collectible = storyObjectCollectible,
                                storePrice = storyObjectStorePriceMinutes,
                                isStarsMode = rewardMode.isStars,
                                message = storyObjectMessage,
                                onToggleInventory = {
                                    storyObjectInventoryState.toggle(context, storyObjectEditingFolder!!)?.let {
                                        storyObjectMessage = it
                                    }
                                },
                                onNameChange = {
                                    storyObjectNameDraft = if (storyObjectEditingFolder == null) NameRules.filterInput(it) else it
                                },
                                onCollectionMenuChange = { storyObjectCollectionMenuExpanded = it },
                                onCollectionSelected = {
                                    storyObjectSelectedCollection = it
                                    storyObjectCollectionMenuExpanded = false
                                },
                                onAvailableInStoreChange = {
                                    storyObjectAvailableInStore = it
                                    if (!it) storyObjectRequiresUnlock = false
                                },
                                onRequiresUnlockChange = { storyObjectRequiresUnlock = it },
                                onCollectibleChange = { storyObjectCollectible = it },
                                onStorePriceChange = { storyObjectStorePriceMinutes = it.filter(Char::isDigit).take(5) },
                                onChooseGallery = {
                                    preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                    storyObjectImagePicker.launch(arrayOf("image/*"))
                                },
                                onTakePhoto = {
                                    preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                    pendingCameraTarget = "story-object"
                                    cameraLauncher.launch(null)
                                },
                                onSave = {
                                    storyObjectEditorState.save(context)?.let { savedObjects ->
                                        storedStoryObjects = savedObjects
                                        parentSection = "stories-object-collection"
                                    }
                                },
                                canSave = storyObjectNameDraft.isNotBlank() &&
                                    storyObjectSelectedCollection.isNotBlank() &&
                                    storyObjectPictureDraft.isNotBlank() &&
                                    (!storyObjectAvailableInStore || (storyObjectStorePriceMinutes.toIntOrNull() ?: 0) > 0),
                                onCancel = {
                                    storyObjectEditorState.reset()
                                    parentSection = "stories-objects"
                                }
                            )
                        }
                    }
                    "stories-object-collection" -> {
                        item {
                            StoryObjectCollectionManagementScreen(
                                collections = storyObjectCollections,
                                draft = storyObjectCollectionDraft,
                                message = storyObjectCollectionMessage,
                                collectionToDelete = storyObjectCollectionToDelete,
                                objects = storedStoryObjects,
                                inventoryFolders = storedInventoryFolders,
                                onDraftChange = { storyObjectCollectionDraft = NameRules.filterInput(it) },
                                onAddCollection = { storyObjectCollectionsState.addCollection() },
                                onRequestDelete = { storyObjectCollectionsState.requestDelete(it) },
                                onDeleteCollection = { storyObjectCollectionsState.deleteCollection(it) },
                                onCancelDelete = { storyObjectCollectionsState.cancelDelete() },
                                onEditObject = { stored ->
                                    storyObjectEditorState.beginEdit(stored)
                                    storyObjectInventoryState.refresh(context)
                                    parentSection = "stories-object-create"
                                },
                                onToggleInventory = { stored, isInInventory ->
                                    val error = storyObjectInventoryState.toggle(context, stored.folderName)
                                    storyObjectCollectionMessage = error ?: if (isInInventory) {
                                        "${stored.metadata.name} retirado del inventario."
                                    } else {
                                        "${stored.metadata.name} añadido al inventario."
                                    }
                                }
                            )
                        }
                    }
                    "stories-object-inventory" -> {
                        item {
                            StoryObjectInventoryManagementScreen(
                                objects = storedStoryObjects,
                                inventoryFolders = storedInventoryFolders,
                                onRemove = { stored ->
                                    storyObjectInventoryState.remove(context, stored.folderName)?.let { storyObjectMessage = it }
                                }
                            )
                        }
                    }
                    "sequences-settings" -> {
                        item { Text("Configurar Secuencias", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "sequence-library" }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5E6))) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("📚", fontSize = 26.sp)
                                    Column(Modifier.weight(1f)) { Text("Biblioteca común de ejercicios", fontWeight = FontWeight.Bold); Text("Crear, editar, filtrar y activar ejercicios de los cuatro tipos", fontSize = 13.sp, color = Color(0xFF667085)) }
                                    Text("›", fontSize = 24.sp)
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "sequence-categories" }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5E6))) {
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
                                Card(Modifier.fillMaxWidth().clickable { parentSection = "sequence-type-$key" }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8EC))) {
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
                                        sequenceContentFormat = format
                                        preferences.edit().putString("sequence_content_format_v1", format).apply()
                                    }.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(label, modifier = Modifier.weight(1f))
                                    androidx.compose.material3.RadioButton(
                                        selected = sequenceContentFormat == format,
                                        onClick = {
                                            sequenceContentFormat = format
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
                                OutlinedButton(onClick = { if (sequenceMaxCards > 2) { sequenceMaxCards--; preferences.edit().putInt("sequence_max_cards_v1", sequenceMaxCards).apply() } }, enabled = sequenceMaxCards > 2, modifier = Modifier.weight(1f)) { Text("−") }
                                Text("$sequenceMaxCards", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { if (sequenceMaxCards < 6) { sequenceMaxCards++; preferences.edit().putInt("sequence_max_cards_v1", sequenceMaxCards).apply() } }, enabled = sequenceMaxCards < 6, modifier = Modifier.weight(1f)) { Text("+") }
                            }
                        }
                    }
                    "sequence-type-temporal", "sequence-type-procesos", "sequence-type-causa", "sequence-type-ciclos" -> {
                        val sequenceType = when (parentSection) {
                            "sequence-type-temporal" -> "Orden temporal"
                            "sequence-type-procesos" -> "Procesos"
                            "sequence-type-causa" -> "Causa y consecuencia"
                            else -> "Ciclos"
                        }
                        item { Text(sequenceType, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item { Text("Configura el contenido específico de esta modalidad.", fontSize = 14.sp, color = Color(0xFF667085)) }
                        item {
                            Card(Modifier.fillMaxWidth().clickable {
                                val key = when (parentSection) {
                                    "sequence-type-temporal" -> "temporal"
                                    "sequence-type-procesos" -> "procesos"
                                    "sequence-type-causa" -> "causa"
                                    else -> "ciclos"
                                }
                                parentSection = "sequence-library"
                            }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8EC))) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🖼️", fontSize = 26.sp)
                                    Column { Text("Biblioteca de secuencias", fontWeight = FontWeight.Bold); Text("Ver, crear y editar ejercicios de $sequenceType", fontSize = 13.sp, color = Color(0xFF667085)) }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable {
                                val key = when (parentSection) {
                                    "sequence-type-temporal" -> "temporal"
                                    "sequence-type-procesos" -> "procesos"
                                    "sequence-type-causa" -> "causa"
                                    else -> "ciclos"
                                }
                                parentSection = "sequence-categories"
                            }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8EC))) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🗂️", fontSize = 26.sp)
                                    Column { Text("Categorías", fontWeight = FontWeight.Bold); Text("Organizar por temas los ejercicios de $sequenceType", fontSize = 13.sp, color = Color(0xFF667085)) }
                                }
                            }
                        }
                        item { Text(if (parentSection == "sequence-type-temporal") "Prototipo funcional: biblioteca y editor de Orden temporal." else "Cada modalidad tendrá su propia biblioteca de ejercicios.", fontSize = 13.sp, color = Color(0xFF667085)) }
                    }
                    "sequence-library", "sequence-library-temporal", "sequence-library-procesos", "sequence-library-causa", "sequence-library-ciclos" -> {
                        val sequenceTypes = listOf("Orden temporal", "Procesos", "Causa y consecuencia", "Ciclos")
                        val sequenceCategories = (sequenceCategoryNames + sequenceExercises.map { it.category }.filter { it.isNotBlank() }).distinct().sorted()
                        val visibleExercises = sequenceExercises.withIndex().filter { (_, exercise) ->
                            (sequenceFilterType == "Todos" || exercise.type == sequenceFilterType) &&
                            (sequenceFilterCategory == "Todas" || exercise.category == sequenceFilterCategory) &&
                            (sequenceFilterDifficulty == "Todas" || exercise.difficulty == sequenceFilterDifficulty) &&
                            (sequenceFilterStatus == "Todos" || if (sequenceFilterStatus == "Activos") exercise.active else !exercise.active)
                        }
                        item { Text("Biblioteca de secuencias", fontSize = 23.sp, fontWeight = FontWeight.Bold) }
                        item { Text("Control parental · Gestiona ejercicios, categorías y su disponibilidad.", fontSize = 14.sp, color = Color(0xFF667085)) }
                        item {
                            Button(onClick = {
                                sequenceEditingIndex = -1; sequenceTitleDraft = ""
                                sequenceCategoryDraft = ""; sequenceTypeDraft = "Orden temporal"; sequenceDifficultyDraft = "Normal"
                                sequenceCardsDraft = listOf("", "", ""); sequenceCardPicturesDraft = listOf("", "", "")
                                sequenceAlternativeDraft = ""; sequenceEditorMessage = ""; parentSection = "sequence-editor-temporal"
                            }, modifier = Modifier.fillMaxWidth()) { Text("＋ Crear ejercicio") }
                        }
                        item {
                            Text("Filtrar contenido", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        item {
                            Text("Tipo", fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                (listOf("Todos") + sequenceTypes).forEach { type ->
                                    FilterChip(selected = sequenceFilterType == type, onClick = { sequenceFilterType = type }, label = { Text(type, softWrap = false) })
                                }
                            }
                        }
                        item {
                            Text("Categoría", fontWeight = FontWeight.Bold)
                            androidx.compose.foundation.layout.Box {
                                OutlinedButton(onClick = { sequenceLibraryFilterCategoryExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("🗂️ Categoría: $sequenceFilterCategory  ▾", maxLines = 1)
                                }
                                DropdownMenu(expanded = sequenceLibraryFilterCategoryExpanded, onDismissRequest = { sequenceLibraryFilterCategoryExpanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                                    (listOf("Todas") + sequenceCategories).forEach { category ->
                                        DropdownMenuItem(text = { Text(category) }, onClick = { sequenceFilterCategory = category; sequenceLibraryFilterCategoryExpanded = false })
                                    }
                                }
                            }
                        }
                        item {
                            Text("Dificultad", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("Todas", "Fácil", "Normal", "Difícil").forEach { level -> FilterChip(selected = sequenceFilterDifficulty == level, onClick = { sequenceFilterDifficulty = level }, label = { Text(level) }) }
                            }
                        }
                        item {
                            Text("Estado", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("Todos", "Activos", "Inactivos").forEach { status -> FilterChip(selected = sequenceFilterStatus == status, onClick = { sequenceFilterStatus = status }, label = { Text(status) }) }
                            }
                        }
                        if (visibleExercises.isEmpty()) item { Text("No hay ejercicios que coincidan con estos filtros.", color = Color(0xFF667085)) }
                        visibleExercises.forEach { (index, exercise) ->
                            item {
                                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (exercise.active) Color.White else Color(0xFFF2F4F7))) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).clickable {
                                            sequenceEditingIndex = index; sequenceTitleDraft = exercise.title
                                            sequenceCategoryDraft = exercise.category; sequenceTypeDraft = exercise.type; sequenceDifficultyDraft = exercise.difficulty
                                            sequenceCardsDraft = exercise.cards
                                            sequenceCardPicturesDraft = exercise.cards.indices.map { cardIndex -> exercise.pictures.getOrNull(cardIndex).orEmpty().ifBlank { defaultSequencePicture(cardIndex) } }
                                            sequenceAlternativeDraft = exercise.alternatives.joinToString("\n") { alternative -> alternative.split("|").mapNotNull { step -> exercise.cards.indexOf(step).takeIf { it >= 0 }?.plus(1) }.joinToString(",") }
                                            sequenceEditorMessage = ""; parentSection = "sequence-editor-temporal"
                                        }) {
                                            Text(exercise.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                            Text("${exercise.type} · ${exercise.category} · ${exercise.difficulty}", fontSize = 12.sp, color = Color(0xFF667085))
                                            Text("${exercise.cards.size} tarjetas · ${exercise.format}", fontSize = 12.sp, color = Color(0xFF667085))
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Switch(checked = exercise.active, onCheckedChange = { active ->
                                                sequenceExercises = sequenceExercises.toMutableList().also { it[index] = exercise.copy(active = active) }
                                                sequenceExercises = sequenceLibraryState.saveExercises(sequenceExercises)
                                            })
                                            Text(if (exercise.active) "Activo" else "Inactivo", fontSize = 11.sp)
                                            OutlinedButton(onClick = {
                                                sequenceExercises = sequenceExercises.filterIndexed { currentIndex, _ -> currentIndex != index }
                                                sequenceExercises = sequenceLibraryState.saveExercises(sequenceExercises)
                                                sequenceCategoryMessage = "Ejercicio eliminado."
                                            }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("Eliminar", fontSize = 12.sp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "sequence-categories", "sequence-categories-temporal", "sequence-categories-procesos", "sequence-categories-causa", "sequence-categories-ciclos" -> {
                        val sequenceCategories = (sequenceCategoryNames + sequenceExercises.map { it.category }.filter { it.isNotBlank() }).distinct().sorted()
                        item { Text("Categorías de secuencias", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item { Text("Crea y organiza categorías temáticas compartidas por todos los tipos de secuencia. Desactivar una categoría mantiene sus ejercicios guardados, pero evita que aparezcan en las partidas.", fontSize = 14.sp, color = Color(0xFF667085)) }
                        item {
                            OutlinedTextField(
                                value = sequenceNewCategoryDraft,
                                onValueChange = { sequenceNewCategoryDraft = NameRules.filterInput(it); sequenceCategoryMessage = "" },
                                label = { Text("Nueva categoría") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(onClick = {
                                val name = sequenceNewCategoryDraft.trim()
                                when {
                                    NameRules.validationError(name) != null -> sequenceCategoryMessage = NameRules.validationError(name)!!
                                    sequenceCategories.any { NameRules.key(it) == NameRules.key(name) } -> sequenceCategoryMessage = "Ya existe una categoría con ese nombre."
                                    else -> {
                                        sequenceCategoryNames = (sequenceCategoryNames + name).distinct()
                                        preferences.edit().putString("sequence_categories_v1", sequenceCategoryNames.joinToString("|")).apply()
                                        sequenceNewCategoryDraft = ""
                                        sequenceCategoryMessage = "Categoría creada."
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("＋ Crear categoría") }
                            if (sequenceCategoryMessage.isNotBlank()) Text(sequenceCategoryMessage, color = if (sequenceCategoryMessage == "Categoría creada.") Color(0xFF24733B) else Color(0xFFB3261E), fontSize = 13.sp)
                        }
                        sequenceCategories.forEach { category ->
                            item {
                                val enabled = category !in disabledSequenceCategories
                                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (enabled) Color.White else Color(0xFFF2F4F7))) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(category, fontWeight = FontWeight.Bold)
                                                Text("${sequenceExercises.count { it.category == category }} ejercicios · ${sequenceExercises.count { it.category == category && it.active }} activos", fontSize = 12.sp, color = Color(0xFF667085))
                                            }
                                            Switch(checked = enabled, onCheckedChange = { checked ->
                                                disabledSequenceCategories = if (checked) disabledSequenceCategories - category else disabledSequenceCategories + category
                                                preferences.edit().putStringSet("disabled_sequence_categories", disabledSequenceCategories).apply()
                                            })
                                        }
                                        if (sequenceCategoryToDelete.equals(category, ignoreCase = true)) {
                                            val assignedCount = sequenceExercises.count { it.category.equals(category, ignoreCase = true) }
                                            Text("Borrar categoría y contenido", fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "Esta opción eliminará también los $assignedCount ejercicios de esta categoría.",
                                                fontSize = 13.sp,
                                                color = Color(0xFFB3261E)
                                            )
                                            Button(onClick = {
                                                sequenceExercises = sequenceExercises.filterNot { it.category.equals(category, ignoreCase = true) }
                                                sequenceExercises = sequenceLibraryState.saveExercises(sequenceExercises)
                                                sequenceCategoryNames = sequenceCategoryNames.filterNot { it.equals(category, ignoreCase = true) }
                                                preferences.edit().putString("sequence_categories_v1", sequenceCategoryNames.joinToString("|")).apply()
                                                disabledSequenceCategories = disabledSequenceCategories.filterNot { it.equals(category, ignoreCase = true) }.toSet()
                                                preferences.edit().putStringSet("disabled_sequence_categories", disabledSequenceCategories).apply()
                                                sequenceCategoryToDelete = null
                                                sequenceCategoryMessage = "Categoría y ejercicios eliminados."
                                            }, modifier = Modifier.fillMaxWidth()) { Text("Borrar categoría y contenido") }
                                            Text("Borrar categoría pero mover contenido a:", fontWeight = FontWeight.SemiBold)
                                            val destinations = sequenceCategories.filterNot { it.equals(category, ignoreCase = true) }
                                            destinations.forEach { destination ->
                                                OutlinedButton(onClick = {
                                                    val moving = sequenceExercises.filter { it.category.equals(category, ignoreCase = true) }
                                                    val destinationItems = sequenceExercises.filter { it.category.equals(destination, ignoreCase = true) }
                                                    val conflicts = NameRules.duplicates(moving.map { it.title }, destinationItems.map { it.title })
                                                    if (conflicts.isNotEmpty()) {
                                                        sequenceCategoryMessage = "No se han movido los ejercicios. Ya existen en «$destination»: ${conflicts.joinToString()}. Renombra o resuelve esos duplicados antes de reasignar."
                                                    } else {
                                                        sequenceExercises = sequenceExercises.map {
                                                            if (it.category.equals(category, ignoreCase = true)) it.copy(category = destination) else it
                                                        }
                                                        sequenceExercises = sequenceLibraryState.saveExercises(sequenceExercises)
                                                        sequenceCategoryNames = sequenceCategoryNames.filterNot { it.equals(category, ignoreCase = true) }
                                                        preferences.edit().putString("sequence_categories_v1", sequenceCategoryNames.joinToString("|")).apply()
                                                        disabledSequenceCategories = disabledSequenceCategories.filterNot { it.equals(category, ignoreCase = true) }.toSet()
                                                        preferences.edit().putStringSet("disabled_sequence_categories", disabledSequenceCategories).apply()
                                                        sequenceCategoryToDelete = null
                                                        sequenceCategoryMessage = "Categoría eliminada; ejercicios movidos a $destination."
                                                    }
                                                }, modifier = Modifier.fillMaxWidth()) { Text(destination) }
                                            }
                                            OutlinedButton(onClick = {
                                                sequenceCategoryToDelete = null
                                                sequenceCategoryMessage = "Operación cancelada. No se ha borrado nada."
                                            }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                                        } else {
                                            OutlinedButton(onClick = { sequenceCategoryToDelete = category; sequenceCategoryMessage = "" }, modifier = Modifier.fillMaxWidth()) { Text("Eliminar / reasignar") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "sequence-editor-temporal" -> {
                        item { Text(if (sequenceEditingIndex < 0) "Nuevo ejercicio" else "Editar ejercicio", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item { OutlinedTextField(value = sequenceTitleDraft, onValueChange = { sequenceTitleDraft = if (sequenceEditingIndex < 0) NameRules.filterInput(it) else it }, label = { Text("Nombre del ejercicio") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                        item {
                            Text("Tipo de secuencia", fontWeight = FontWeight.Bold)
                            listOf("Orden temporal", "Procesos", "Causa y consecuencia", "Ciclos").forEach { type ->
                                OutlinedButton(onClick = {
                                    sequenceTypeDraft = type
                                    if (sequenceCardsDraft.size < 2) {
                                        sequenceCardsDraft = listOf(sequenceCardsDraft.firstOrNull().orEmpty(), "", "")
                                        sequenceCardPicturesDraft = listOf(sequenceCardPicturesDraft.firstOrNull().orEmpty(), "", "")
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text(if (sequenceTypeDraft == type) "✓ $type" else type) }
                            }
                        }
                        item {
                            Text("Dificultad", fontWeight = FontWeight.Bold)
                            listOf("Fácil", "Normal", "Difícil").forEach { level ->
                                OutlinedButton(onClick = { sequenceDifficultyDraft = level }, modifier = Modifier.fillMaxWidth()) { Text(if (sequenceDifficultyDraft == level) "✓ $level" else level) }
                            }
                            Text("Cada niño aprende a su ritmo. La dificultad indica la complejidad de la actividad y es orientativa. Elige el nivel que mejor se adapte a sus necesidades y experiencia.", fontSize = 13.sp, color = Color(0xFF667085))
                        }
                        item {
                            Text("Categoría temática", fontWeight = FontWeight.Bold)
                            androidx.compose.foundation.layout.Box {
                                OutlinedButton(onClick = { sequenceCategoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("🗂️ $sequenceCategoryDraft  ▾", maxLines = 1)
                                }
                                DropdownMenu(expanded = sequenceCategoryMenuExpanded, onDismissRequest = { sequenceCategoryMenuExpanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                                    (sequenceCategoryNames + sequenceExercises.map { it.category }.filter { it.isNotBlank() }).distinct().sorted().forEach { category ->
                                        DropdownMenuItem(text = { Text(category) }, onClick = { sequenceCategoryDraft = category; sequenceCategoryMenuExpanded = false })
                                    }
                                }
                            }
                            Text("Las categorías se crean y gestionan desde el menú Categorías de la biblioteca.", fontSize = 12.sp, color = Color(0xFF667085))
                        }
                        item {
                            Text("Tarjetas en el orden correcto", fontWeight = FontWeight.Bold)
                            Text("Edita los pasos en su orden correcto. Máximo 6 tarjetas.", fontSize = 13.sp, color = Color(0xFF667085))
                        }
                        sequenceCardsDraft.forEachIndexed { index, card ->
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("${index + 1}.", fontWeight = FontWeight.Bold)
                                        OutlinedTextField(value = card, onValueChange = { updated -> sequenceCardsDraft = sequenceCardsDraft.toMutableList().also { it[index] = updated } }, label = { Text("Paso ${index + 1}") }, modifier = Modifier.weight(1f), singleLine = true)
                                        OutlinedButton(onClick = {
                                            if (sequenceCardsDraft.size > 2) {
                                                sequenceCardsDraft = sequenceCardsDraft.toMutableList().also { it.removeAt(index) }
                                                sequenceCardPicturesDraft = sequenceCardPicturesDraft.toMutableList().also { if (index < it.size) it.removeAt(index) }
                                            }
                                        }, enabled = sequenceCardsDraft.size > 2) { Text("−") }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        val picture = sequenceCardPicturesDraft.getOrNull(index).orEmpty()
                                        if (picture.startsWith("content://") || picture.startsWith("file://")) {
                                            AndroidView(
                                                factory = { ctx -> ImageView(ctx).apply {
                                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                                    layoutParams = ViewGroup.LayoutParams(120, 120)
                                                } },
                                                update = { view ->
                                                    try { view.setImageURI(Uri.parse(picture)) }
                                                    catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) }
                                                },
                                                modifier = Modifier.width(88.dp).height(88.dp).clipToBounds()
                                            )
                                        } else {
                                            androidx.compose.foundation.layout.Box(
                                                Modifier.width(88.dp).height(88.dp)
                                                    .background(Color(0xFFF2F4F7), RoundedCornerShape(12.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (picture.isBlank()) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        Text("▧", fontSize = 30.sp, color = Color(0xFF98A2B3))
                                                        Text("Sin imagen", fontSize = 10.sp, color = Color(0xFF667085))
                                                    }
                                                } else {
                                                    Text("Imagen no válida", fontSize = 10.sp, color = Color(0xFF667085))
                                                }
                                            }
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Imagen de la tarjeta ${index + 1}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            OutlinedButton(onClick = {
                                                pendingSequencePictureIndex = index
                                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                                sequenceImagePicker.launch(arrayOf("image/*"))
                                            }, modifier = Modifier.fillMaxWidth()) { Text("Elegir imagen de la galería") }
                                            OutlinedButton(onClick = {
                                                pendingCameraTarget = "sequence"
                                                pendingCameraSequenceIndex = index
                                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                                cameraLauncher.launch(null)
                                            }, modifier = Modifier.fillMaxWidth()) { Text("📷 Hacer foto con la cámara") }
                                            if (picture.isNotBlank()) {
                                                OutlinedButton(onClick = {
                                                    sequenceCardPicturesDraft = sequenceCardPicturesDraft.toMutableList().also {
                                                        while (it.size <= index) it.add("")
                                                        it[index] = ""
                                                    }
                                                }, modifier = Modifier.fillMaxWidth()) { Text("Quitar imagen") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            OutlinedButton(onClick = {
                                if (sequenceCardsDraft.size < 6) {
                                    sequenceCardsDraft = sequenceCardsDraft + ""
                                    sequenceCardPicturesDraft = sequenceCardPicturesDraft + ""
                                }
                            }, enabled = sequenceCardsDraft.size < 6 && true, modifier = Modifier.fillMaxWidth()) { Text("＋ Añadir tarjeta (${sequenceCardsDraft.size}/6)") }
                        }
                        item {
                            Text("Soluciones alternativas (opcional)", fontWeight = FontWeight.Bold)
                            Text("Una alternativa por línea. Escribe los números de las tarjetas en el orden correcto, separados por comas.", fontSize = 13.sp, color = Color(0xFF667085))
                            OutlinedTextField(value = sequenceAlternativeDraft, onValueChange = { sequenceAlternativeDraft = it }, label = { Text("Ej.: 1,3,4,2") }, placeholder = { Text("1,3,4,2\n1,2,3,4") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        }
                        item {
                            if (sequenceEditorMessage.isNotBlank()) Text(sequenceEditorMessage, color = Color(0xFFB3261E), fontSize = 13.sp)
                            Button(onClick = {
                                val cleanCards = sequenceCardsDraft.map { it.trim() }
                                val alternativeLines = sequenceAlternativeDraft.lines().map { it.trim() }.filter { it.isNotBlank() }
                                val parsedAlternatives = alternativeLines.map { line -> line.split(",").map { it.trim().toIntOrNull() } }
                                val invalidAlternative = parsedAlternatives.any { indexes ->
                                    indexes.size != cleanCards.size ||
                                        indexes.any { it == null || it !in 1..cleanCards.size } ||
                                        indexes.filterNotNull().distinct().size != cleanCards.size
                                }
                                when {
                                    NameRules.validationError(sequenceTitleDraft) != null && sequenceTitleDraft != sequenceExercises.getOrNull(sequenceEditingIndex)?.title -> sequenceEditorMessage = NameRules.validationError(sequenceTitleDraft)!!
                                    sequenceExercises.withIndex().any { (index, item) -> index != sequenceEditingIndex && NameRules.key(item.title) == NameRules.key(sequenceTitleDraft) && NameRules.key(item.category) == NameRules.key(sequenceCategoryDraft) } -> sequenceEditorMessage = "Ya existe un ejercicio con ese nombre en esta categoría."
                                    sequenceCategoryDraft.isBlank() -> sequenceEditorMessage = "Crea o selecciona una categoría antes de guardar el ejercicio."
                                    cleanCards.size < 2 || cleanCards.any { it.isBlank() } -> sequenceEditorMessage = "Completa al menos 2 tarjetas; no puede haber pasos vacíos."
                                    cleanCards.size > 6 -> sequenceEditorMessage = "El máximo es de 6 tarjetas."
                                     sequenceCardPicturesDraft.indices.take(cleanCards.size).any { sequenceCardPicturesDraft.getOrNull(it).isNullOrBlank() } || sequenceCardPicturesDraft.size < cleanCards.size -> sequenceEditorMessage = "Selecciona una imagen para cada tarjeta. No puede quedar ninguna imagen vacía."
                                    alternativeLines.isNotEmpty() && invalidAlternative -> sequenceEditorMessage = "Cada alternativa debe incluir todos los números del 1 al ${cleanCards.size}, sin repetir y separados por comas."
                                    else -> {
                                        val alternatives = alternativeLines
                                        val cleanPictures = cleanCards.indices.map { sequenceCardPicturesDraft.getOrNull(it).orEmpty() }
                                        val previousExercise = sequenceExercises.getOrNull(sequenceEditingIndex)
                                        val exercise = SequenceExercise(sequenceTitleDraft.trim(), cleanCards, "Imagen y texto", sequenceCategoryDraft.trim(), alternatives, cleanPictures, sequenceTypeDraft, sequenceDifficultyDraft, previousExercise?.active ?: true, previousExercise?.directory, previousExercise?.createdAt.orEmpty())
                                        val updatedExercises = sequenceExercises.toMutableList().also {
                                            if (sequenceEditingIndex < 0) it.add(exercise) else it[sequenceEditingIndex] = exercise
                                        }
                                        sequenceExercises = updatedExercises
                                        sequenceExercises = sequenceLibraryState.saveExercises(updatedExercises)
                                        parentSection = "sequence-library"
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar ejercicio") }
                        }
                        item { OutlinedButton(onClick = { parentSection = "sequence-library-temporal" }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") } }
                    }
                    "sequence-categories-temporal" -> {
                        item { Text("Categorías · Orden temporal", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item { Text("Las categorías organizan los ejercicios de esta modalidad y no se comparten con Procesos, Causa y consecuencia ni Ciclos.", fontSize = 14.sp, color = Color(0xFF667085)) }
                        sequenceCategoryNames.forEach { category ->
                            item {
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("🗂️", fontSize = 24.sp)
                                        Text(category, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                        Text("${sequenceExercises.count { it.category == category }} ejercicios", fontSize = 12.sp, color = Color(0xFF667085))
                                    }
                                }
                            }
                        }
                        item { Text("Crea tus propias categorías para organizar los ejercicios.", fontSize = 12.sp, color = Color(0xFF667085)) }
                    }
                    "sequence-library-procesos", "sequence-library-causa", "sequence-library-ciclos" -> {
                        val typeName = when (parentSection) {
                            "sequence-library-procesos" -> "Procesos"
                            "sequence-library-causa" -> "Causa y consecuencia"
                            else -> "Ciclos"
                        }
                        item { Text("Biblioteca · $typeName", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item { Text("La biblioteca de esta modalidad tendrá sus propios ejercicios, formatos y soluciones. El editor se implementará después de validar el de Orden temporal.", fontSize = 14.sp, color = Color(0xFF667085)) }
                    }
                    "sequence-categories-procesos", "sequence-categories-causa", "sequence-categories-ciclos" -> {
                        val typeName = when (parentSection) {
                            "sequence-categories-procesos" -> "Procesos"
                            "sequence-categories-causa" -> "Causa y consecuencia"
                            else -> "Ciclos"
                        }
                        item { Text("Categorías · $typeName", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item { Text("Estas categorías son independientes de las demás modalidades. Su gestión se habilitará cuando implementemos la biblioteca de $typeName.", fontSize = 14.sp, color = Color(0xFF667085)) }
                    }
                    "words-settings" -> {
                        item { Text("Configurar Palabras", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "word-library"; wordLibraryPage = "list" }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF))) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🖼️", fontSize = 26.sp)
                                    Column { Text("Biblioteca de palabras", fontWeight = FontWeight.Bold); Text("Palabras e imágenes que servirán como pistas", fontSize = 13.sp, color = Color(0xFF667085)) }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "word-categories" }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF))) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🗂️", fontSize = 26.sp)
                                    Column { Text("Categorías", fontWeight = FontWeight.Bold); Text("Organización del vocabulario por temas", fontSize = 13.sp, color = Color(0xFF667085)) }
                                }
                            }
                        }
                        item {
                            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Dificultad", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("La dificultad de cada ejercicio se configura en la ficha de cada palabra.", fontSize = 14.sp, color = Color(0xFF667085))
                            Text("La biblioteca y las categorías de Palabras son independientes de Tarjetas y Secuencias.", fontSize = 13.sp, color = Color(0xFF667085))
                        }
                    }
                    "word-library" -> {
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
                                                OutlinedButton(onClick = { wordExercises = wordExercises.toMutableList().also { it[index] = exercise.copy(active = !exercise.active) }; wordExercises = wordLibraryState.saveExercises(wordExercises) }, modifier = Modifier.weight(1f)) { Text(if (exercise.active) "Desactivar" else "Activar") }
                                                OutlinedButton(onClick = { wordExercises = wordLibraryState.saveExercises(wordExercises.filterIndexed { i, _ -> i != index }) }, modifier = Modifier.weight(1f)) { Text("Eliminar") }
                                            }
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
                                OutlinedButton(onClick = { preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit(); wordImagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("🖼️ Elegir imagen de la galería") }
                                OutlinedButton(onClick = {
                                    pendingCameraTarget = "word"
                                    preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                    cameraLauncher.launch(null)
                                }, modifier = Modifier.fillMaxWidth()) { Text("📷 Hacer foto con la cámara") }
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
                                        cleanWord.any { it.isWhitespace() } -> wordLibraryMessage = "Para empezar, introduce una sola palabra sin espacios."
                                        wordLibraryPictureDraft.isBlank() -> wordLibraryMessage = "Selecciona una imagen de pista."
                                        wordLibraryCategoryDraft.isBlank() || wordLibraryCategoryDraft !in wordExerciseCategories -> wordLibraryMessage = "Selecciona una categoría creada en el menú Categorías."
                                        wordExercises.withIndex().any { (i, e) -> i != wordLibraryEditingIndex && e.word.equals(cleanWord, true) } -> wordLibraryMessage = "Ya existe un ejercicio con esa palabra."
                                        else -> {
                                            val entry = wordExercises.getOrNull(wordLibraryEditingIndex)?.copy(word = cleanWord, picture = wordLibraryPictureDraft.trim(), category = wordLibraryCategoryDraft, difficulty = wordLibraryDifficultyDraft, active = true) ?: PairItem(cleanWord, wordLibraryPictureDraft.trim(), wordLibraryCategoryDraft, wordLibraryDifficultyDraft, true)
                                            wordExercises = if (wordLibraryEditingIndex < 0) wordExercises + entry else wordExercises.toMutableList().also { it[wordLibraryEditingIndex] = entry }
                                            wordExercises = wordLibraryState.saveExercises(wordExercises)
                                            wordLibraryPage = "list"; wordLibraryMessage = ""
                                        }
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Guardar ejercicio") }
                            }
                            item { OutlinedButton(onClick = { wordLibraryPage = "list"; wordLibraryMessage = "" }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") } }
                        }
                    }
                    "word-categories" -> {
                        item { Text("Categorías de Palabras", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item { Text("Estas categorías solo afectan a los ejercicios de Palabras; no modifican Tarjetas ni Secuencias.", fontSize = 14.sp, color = Color(0xFF667085)) }
                        item { OutlinedTextField(value = wordNewCategoryDraft, onValueChange = { wordNewCategoryDraft = NameRules.filterInput(it); wordLibraryMessage = "" }, label = { Text("Nueva categoría") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                        item { Button(onClick = {
                            val name = wordNewCategoryDraft.trim()
                            when {
                                name.isBlank() -> wordLibraryMessage = "Escribe un nombre."
                                wordExerciseCategories.any { it.equals(name, true) } -> wordLibraryMessage = "Esa categoría ya existe."
                                else -> { wordLibraryState.createCategory(name); wordLibraryMessage = "Categoría creada." }
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
                                                wordLibraryState.setCategoryEnabled(category, enabled)
                                            })
                                        }
                                        if (wordCategoryToDelete == category) {
                                            Text("Borrar categoría y contenido", fontWeight = FontWeight.Bold)
                                            Text("Se eliminarán también los ${wordExercises.count { it.category == category }} ejercicios asignados.", color = Color(0xFFB3261E))
                                            Button(onClick = { wordLibraryState.deleteCategoryAndExercises(category) }, modifier = Modifier.fillMaxWidth()) { Text("Borrar categoría y contenido") }
                                            Text("Borrar categoría pero mover contenido a:", fontWeight = FontWeight.Bold)
                                            wordExerciseCategories.filterNot { it == category }.forEach { destination ->
                                                OutlinedButton(onClick = { wordLibraryState.reassignCategory(category, destination) }, modifier = Modifier.fillMaxWidth()) { Text(destination) }
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
                    "cards-settings" -> {
                        item { Text("Configurar Tarjetas", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "words" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🖼️", fontSize = 28.sp)
                                    Column { Text("Biblioteca de tarjetas", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Palabras, imágenes, dificultad y estado de cada tarjeta", color = Color(0xFF667085)) }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth().clickable { parentSection = "categories" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🗂️", fontSize = 28.sp)
                                    Column { Text("Categorías", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Organiza el vocabulario por temas", color = Color(0xFF667085)) }
                                }
                            }
                        }
                        item {
                            Text("Parejas por partida", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Número de parejas de palabras e imágenes que tendrá cada ejercicio.", fontSize = 14.sp, color = Color(0xFF667085))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { questionCount = (questionCount - 1).coerceAtLeast(1); preferences.edit().putInt("question_count", questionCount).apply() }, enabled = questionCount > 1) { Text("−") }
                                Text("$questionCount", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { questionCount = (questionCount + 1).coerceAtMost(10); preferences.edit().putInt("question_count", questionCount).apply() }, enabled = questionCount < 10) { Text("+") }
                            }
                        }
                        item {
                            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Límite diario para elegir categoría", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Número máximo de veces al día que el menor puede elegir la categoría. Al alcanzar el límite, podrá seguir practicando con las demás opciones.", fontSize = 14.sp, color = Color(0xFF667085))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { if (dailyManualLimit > 0) { dailyManualLimit--; preferences.edit().putInt("daily_manual_limit", dailyManualLimit).apply() } }) { Text("−") }
                                Text("$dailyManualLimit", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { if (dailyManualLimit < 10) { dailyManualLimit++; preferences.edit().putInt("daily_manual_limit", dailyManualLimit).apply() } }) { Text("+") }
                            }
                        }
                        item { Text("Los cambios se guardan en este dispositivo.", fontSize = 13.sp, color = Color(0xFF667085)) }
                    }
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
                                    OutlinedButton(
                                        onClick = { libraryDifficultyFilter = level },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(if (libraryDifficultyFilter == level) "✓ $level" else level, fontSize = 11.sp) }
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
                                preferences.edit()
                                    .putBoolean("content_picker_active", true)
                                    .putBoolean("parent_area_active", true)
                                    .commit()
                                imagePicker.launch(arrayOf("image/*"))
                            }, modifier = Modifier.fillMaxWidth()) { Text("Elegir imagen de la galería") }
                            OutlinedButton(onClick = {
                                // The external camera app temporarily backgrounds this Activity.
                                preferences.edit()
                                    .putBoolean("content_picker_active", true)
                                    .putBoolean("parent_area_active", true)
                                    .commit()
                                pendingCameraTarget = "pair"
                                cameraLauncher.launch(null)
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
                    "rewards" -> item {
                        Text("Saldo de recompensas", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text(if (rewardMode.isStars) "Saldo actual: ${formatRewardBalance(balanceSeconds, rewardMode)} estrellas" else "Saldo actual: ${balanceSeconds / 60} min ${balanceSeconds % 60} s", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("Unidad: Minutos/Estrellas (equivalencia 1:1)", fontSize = 14.sp, color = Color(0xFF667085))
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = {
                                val updated = (balanceSeconds + 60).coerceAtMost(24 * 60 * 60)
                                balanceSeconds = updated
                                preferences.edit().putInt("balance_seconds", updated).apply()
                            }, modifier = Modifier.fillMaxWidth(), enabled = balanceSeconds < 24 * 60 * 60) { Text("Sumar 1 unidad al saldo") }
                            OutlinedButton(onClick = {
                                val updated = (balanceSeconds + 5 * 60).coerceAtMost(24 * 60 * 60)
                                balanceSeconds = updated
                                preferences.edit().putInt("balance_seconds", updated).apply()
                            }, modifier = Modifier.fillMaxWidth(), enabled = balanceSeconds < 24 * 60 * 60) { Text("Sumar 5 unidades al saldo") }
                            OutlinedButton(onClick = {
                                balanceSeconds = 0
                                preferences.edit().putInt("balance_seconds", 0).putLong("play_deadline_ms", 0L).putLong("expiry_last_launch_ms", 0L).putBoolean("play_session_authorized", false).apply()
                                context.stopService(Intent(context, PlayTimeService::class.java))
                            }, modifier = Modifier.fillMaxWidth(), enabled = balanceSeconds > 0) { Text("Poner a cero el saldo") }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        Text("Recompensas y límites", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Cada bloque agrupa una opción concreta. Los cambios se guardan automáticamente.", fontSize = 14.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("1. Acceso al tiempo de juego", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (rewardMode.isStars) Color.Gray else Color.Unspecified)
                        Text(if (rewardMode.isStars) "Solo disponible en modo Tiempo: saldo mínimo para salir a jugar fuera de la aplicación." else "Tiempo mínimo de saldo necesario para iniciar una nueva sesión.", fontSize = 14.sp, color = Color(0xFF667085))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = {
                                minimumPlayMinutes = (minimumPlayMinutes - 1).coerceAtLeast(0)
                                preferences.edit().putInt("minimum_play_minutes", minimumPlayMinutes).apply()
                            }, enabled = !rewardMode.isStars && minimumPlayMinutes > 0) { Text("−") }
                            Text("$minimumPlayMinutes min", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (rewardMode.isStars) Color.Gray else Color.Unspecified)
                            OutlinedButton(onClick = {
                                minimumPlayMinutes = (minimumPlayMinutes + 1).coerceAtMost(60)
                                preferences.edit().putInt("minimum_play_minutes", minimumPlayMinutes).apply()
                            }, enabled = !rewardMode.isStars && minimumPlayMinutes < 60) { Text("+") }
                        }
                        Text("Entre 0 y 60 minutos. Con 0, cualquier saldo positivo permite empezar.", fontSize = 13.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text("2. Penalización por respuestas falladas", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Elige cuánto descuentan los errores de la recompensa de esta partida. El saldo acumulado nunca se reduce.", fontSize = 14.sp, color = Color(0xFF667085))
                        androidx.compose.foundation.layout.Box {
                            OutlinedButton(onClick = { penaltyMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(when (wrongAnswerPenalty) { "none" -> "Sin penalización ▾"; "full" -> "Cada fallo resta el 100 % de un acierto ▾"; else -> "Cada fallo resta el 50 % de un acierto ▾" })
                            }
                            DropdownMenu(expanded = penaltyMenuExpanded, onDismissRequest = { penaltyMenuExpanded = false }) {
                                DropdownMenuItem(text = { Text("Sin penalización") }, onClick = { wrongAnswerPenalty = "none"; preferences.edit().putString("wrong_answer_penalty", wrongAnswerPenalty).apply(); penaltyMenuExpanded = false })
                                DropdownMenuItem(text = { Text("Cada fallo resta el 50 % del valor de un acierto") }, onClick = { wrongAnswerPenalty = "half"; preferences.edit().putString("wrong_answer_penalty", wrongAnswerPenalty).apply(); penaltyMenuExpanded = false })
                                DropdownMenuItem(text = { Text("Cada fallo resta el 100 % del valor de un acierto") }, onClick = { wrongAnswerPenalty = "full"; preferences.edit().putString("wrong_answer_penalty", wrongAnswerPenalty).apply(); penaltyMenuExpanded = false })
                            }
                        }
                        Text(when (wrongAnswerPenalty) { "none" -> "Los errores no reducen la recompensa de esta partida."; "full" -> "Cada respuesta incorrecta resta el equivalente a un acierto."; else -> "Cada respuesta incorrecta resta la mitad de la recompensa de un acierto." }, fontSize = 14.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text("3. Recompensa por dificultad", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Minutos/Estrellas que se añaden al saldo según la dificultad del ejercicio.", fontSize = 14.sp, color = Color(0xFF667085))
                        listOf("Fácil", "Normal", "Difícil").forEach { level ->
                            val minutes = when (level) { "Normal" -> normalRewardMinutes; "Difícil" -> hardRewardMinutes; else -> easyRewardMinutes }
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(level, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(onClick = {
                                            when (level) {
                                                "Normal" -> { normalRewardMinutes = (normalRewardMinutes - 1).coerceAtLeast(0); preferences.edit().putInt("reward_normal_minutes", normalRewardMinutes).apply() }
                                                "Difícil" -> { hardRewardMinutes = (hardRewardMinutes - 1).coerceAtLeast(0); preferences.edit().putInt("reward_hard_minutes", hardRewardMinutes).apply() }
                                                else -> { easyRewardMinutes = (easyRewardMinutes - 1).coerceAtLeast(0); preferences.edit().putInt("reward_easy_minutes", easyRewardMinutes).apply() }
                                            }
                                        }, enabled = minutes > 0) { Text("−") }
                                        Text("$minutes Minutos/Estrellas", fontWeight = FontWeight.Bold)
                                        OutlinedButton(onClick = {
                                            when (level) {
                                                "Normal" -> { normalRewardMinutes = (normalRewardMinutes + 1).coerceAtMost(120); preferences.edit().putInt("reward_normal_minutes", normalRewardMinutes).apply() }
                                                "Difícil" -> { hardRewardMinutes = (hardRewardMinutes + 1).coerceAtMost(120); preferences.edit().putInt("reward_hard_minutes", hardRewardMinutes).apply() }
                                                else -> { easyRewardMinutes = (easyRewardMinutes + 1).coerceAtMost(120); preferences.edit().putInt("reward_easy_minutes", easyRewardMinutes).apply() }
                                            }
                                        }, enabled = minutes < 120) { Text("+") }
                                    }
                                }
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text("Los cambios se guardan en este dispositivo.", fontSize = 13.sp, color = Color(0xFF667085))
                    }
                    "stories-audio" -> item {
                        Text("Audio de cuentos", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Cuando se reproduce una narración, el sonido ambiental se atenúa para que la voz se escuche con claridad. Al terminar, recupera progresivamente su volumen original.", fontSize = 14.sp, color = Color(0xFF667085))
                        Text("Volumen ambiental durante la narración", fontWeight = FontWeight.SemiBold)
                        Text("$storyAmbientDuringNarration % del volumen original (máximo 30 %)", fontSize = 13.sp, color = Color(0xFF667085))
                        Slider(
                            value = storyAmbientDuringNarration.toFloat(),
                            onValueChange = { value ->
                                storyAmbientDuringNarration = value.toInt().coerceIn(0, 30)
                                preferences.edit().putInt("story_ambient_during_narration_percent", storyAmbientDuringNarration).apply()
                            },
                            valueRange = 0f..30f,
                            steps = 29
                        )
                        Text("Este ajuste es global: se aplica a todos los cuentos y se guarda automáticamente en este dispositivo.", fontSize = 12.sp, color = Color(0xFF667085))
                    }
                    "music" -> item {
                        Text("Música de fondo", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("La música comienza al abrir la aplicación y se repite en bucle. Usa el altavoz de la pantalla principal para silenciar o activar todo el sonido.", fontSize = 14.sp, color = Color(0xFF667085))
                        Text(if (musicUri.isNullOrBlank()) "Música predeterminada: Música Infantil 2." else "Archivo de música personalizado seleccionado.", fontWeight = FontWeight.SemiBold)
                        Button(onClick = { preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit(); musicPicker.launch(arrayOf("audio/mpeg", "audio/mp3", "audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Elegir archivo MP3") }
                        OutlinedButton(onClick = {
                            musicUri = null
                            preferences.edit().remove("background_music_uri").apply()
                            musicMessage = "Se ha restaurado la música predeterminada."
                        }, enabled = !musicUri.isNullOrBlank(), modifier = Modifier.fillMaxWidth()) { Text("Quitar música") }
                        OutlinedButton(onClick = {
                            soundEnabled = !soundEnabled
                            preferences.edit().putBoolean("sound_enabled", soundEnabled).apply()
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (soundEnabled) "Silenciar sonido" else "Activar sonido") }
                        if (musicMessage.isNotBlank()) Text(musicMessage, color = Color(0xFF24733B))
                        Text("Consejo: elige una melodía instrumental tranquila, sin cambios bruscos de volumen y con un final que encaje bien al repetirse.", fontSize = 13.sp, color = Color(0xFF667085))
                        Spacer(Modifier.height(8.dp))
                        Text("Agradecimientos musicales", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Música Infantil 2 — Ezequiel David Urbano", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Licencia declarada por el autor: Creative Commons Zero (CC0 1.0).", fontSize = 13.sp, color = Color(0xFF667085))
                        OutlinedButton(onClick = { uriHandler.openUri("https://open.spotify.com/artist/3FnwImaOicMeRPERcj6ueb?si=LBbyDbPYRyyO1Z6ium2P0w") }, modifier = Modifier.fillMaxWidth()) { Text("Escuchar al autor en Spotify") }
                        OutlinedButton(onClick = { uriHandler.openUri("https://www.instagram.com/ezequieldavidurbano/") }, modifier = Modifier.fillMaxWidth()) { Text("Instagram del autor") }
                    }
                    "device-setup" -> item {
                        Text("Asistente de configuración", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        if (rewardMode.isStars) Text("Puedes dejar estos permisos preparados para cuando quieras utilizar el modo Tiempo. En modo Estrellas no se utilizarán para controlar la salida ni el saldo.", fontSize = 14.sp, color = Color(0xFF667085))
                        setupAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (setupAccessibilityEnabled) Color(0xFFE5F5E9) else Color(0xFFF0EDF7))) {
                            Text(if (setupAccessibilityEnabled) "✓ COMPLETADO · Servicio de accesibilidad activado" else "○ PENDIENTE · Activa el servicio de accesibilidad", modifier = Modifier.padding(12.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (setupAccessibilityEnabled) Color(0xFF24733B) else Color(0xFF6B5AA6))
                        }
                        Text("Para que la protección de la aplicación funcione, Android requiere que una persona autorice manualmente el servicio de accesibilidad. Esta guía te llevará a los ajustes necesarios.", fontSize = 15.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        if (!restrictedSettingsDone) {
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (restrictedSettingsDone) "✓" else "1", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (restrictedSettingsDone) Color(0xFF24733B) else Color(0xFF667085))
                            Text("Permitir ajustes restringidos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Al instalar la aplicación desde un APK, Android puede bloquear la activación del servicio. Abre la información de la aplicación y busca el menú de tres puntos (⋮). Si aparece «Permitir ajustes restringidos», selecciónalo y confirma.", fontSize = 14.sp, color = Color(0xFF667085))
                        Button(onClick = {
                            preferences.edit().putBoolean("device_setup_in_progress", true).putBoolean("parent_area_active", true).commit()
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "No se pudieron abrir los ajustes de la aplicación.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("1. Abrir información de la aplicación") }
                        OutlinedButton(onClick = { restrictedSettingsDone = !restrictedSettingsDone; preferences.edit().putBoolean("restricted_settings_done", restrictedSettingsDone).apply() }, modifier = Modifier.fillMaxWidth()) { Text(if (restrictedSettingsDone) "✓ Paso 1 completado" else "Marcar paso 1 como completado") }
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
}
if (!setupAccessibilityEnabled) {
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (setupAccessibilityEnabled) "✓" else "2", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (setupAccessibilityEnabled) Color(0xFF24733B) else Color(0xFF667085))
                            Text("Activar accesibilidad", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Después, abre Accesibilidad, busca «Aprender para jugar» dentro de las aplicaciones o servicios instalados y activa el interruptor. Android puede mostrar una advertencia que tendrás que aceptar. Al terminar, vuelve a la aplicación.", fontSize = 14.sp, color = Color(0xFF667085))
                        Button(onClick = {
                            preferences.edit().putBoolean("device_setup_in_progress", true).putBoolean("parent_area_active", true).commit()
                            try {
                                context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "No se pudieron abrir los ajustes de accesibilidad.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("2. Abrir ajustes de accesibilidad") }
                        Text("Nota: la aplicación no puede activar estos permisos por sí sola. Si «Permitir ajustes restringidos» no aparece, intenta primero activar el servicio desde Accesibilidad y vuelve después a la información de la aplicación.", fontSize = 13.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
}
if (!batterySettingsDone) {
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (batterySettingsDone) "✓" else "3", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (batterySettingsDone) Color(0xFF24733B) else Color(0xFF667085))
                            Text("Evitar que Xiaomi cierre la aplicación", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Para que la protección siga funcionando en segundo plano, abre la información de la aplicación. Entra en «Ahorro de batería» o «Batería» y selecciona «Sin restricciones» si aparece. En esa misma ficha, desactiva «Pausar la actividad de la aplicación si no se utiliza». Los nombres pueden variar según MIUI/HyperOS.", fontSize = 14.sp, color = Color(0xFF667085))
                        Button(onClick = {
                            preferences.edit().putBoolean("device_setup_in_progress", true).putBoolean("parent_area_active", true).commit()
                            try {
                                context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context, "Abre manualmente Ajustes > Aplicaciones > Aprender para jugar > Batería.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("3. Revisar ajustes de batería") }
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        OutlinedButton(onClick = { batterySettingsDone = !batterySettingsDone; preferences.edit().putBoolean("battery_setup_done", batterySettingsDone).apply() }, modifier = Modifier.fillMaxWidth()) { Text(if (batterySettingsDone) "✓ Ajustes de batería completados" else "Marcar ajustes de batería como completados") }
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
}
if (!xiaomiWindowPermissionDone) {
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (xiaomiWindowPermissionDone) "✓" else "4", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (xiaomiWindowPermissionDone) Color(0xFF24733B) else Color(0xFF667085))
                            Text("Permitir ventanas en segundo plano (Xiaomi)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("En tu móvil, esta autorización es necesaria para que la aplicación pueda volver a mostrarse cuando el servicio de accesibilidad tenga que intervenir. Pulsa el botón, entra en «Otros permisos» y activa «Abrir nuevas ventanas mientras se ejecuta en segundo plano». En la misma pantalla puedes comprobar los otros permisos, pero no actives «Accesos directos de la pantalla de inicio» ni «Mostrar en la pantalla de bloqueo» salvo que detectemos que también hacen falta.", fontSize = 14.sp, color = Color(0xFF667085))
                        Button(onClick = {
                            preferences.edit().putBoolean("device_setup_in_progress", true).putBoolean("parent_area_active", true).commit()
                            try {
                                context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "Abre Ajustes > Aplicaciones > Aprender para jugar > Otros permisos.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("4. Revisar otros permisos de Xiaomi") }
                        OutlinedButton(onClick = { xiaomiWindowPermissionDone = !xiaomiWindowPermissionDone; preferences.edit().putBoolean("xiaomi_window_permission_done", xiaomiWindowPermissionDone).apply() }, modifier = Modifier.fillMaxWidth()) { Text(if (xiaomiWindowPermissionDone) "✓ Permiso de ventanas completado" else "Marcar permiso de ventanas como completado") }

                    }
                    
}
"about" -> item {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Acerca de esta app", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                Text("Aprender para jugar", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3567A8))
                                Text("Una herramienta para aprender jugando.", fontSize = 14.sp, color = Color(0xFF667085))
                            }

                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Sobre el proyecto", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("Aplicación educativa para practicar vocabulario, asociación de palabras e imágenes y secuencias. El aprendizaje se refuerza mediante recompensas y controles configurables por la familia.", fontSize = 14.sp, color = Color(0xFF667085))
                                    Text("Creada por Miguel Ponce Torres, con ayuda de inteligencia artificial como herramienta de apoyo en el diseño, la programación y la resolución de problemas.", fontSize = 13.sp, color = Color(0xFF667085))
                                }
                            }

                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Colabora con otras causas", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("La aplicación es gratuita. Si te apetece apoyar alguna iniciativa relacionada con el autismo, puedes conocer estas organizaciones y colaborar directamente con ellas.", fontSize = 14.sp, color = Color(0xFF667085))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Fundación Miradas", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Investigación, conocimiento y defensa de los derechos de las personas autistas.", fontSize = 13.sp, color = Color(0xFF667085))
                                        OutlinedButton(onClick = { uriHandler.openUri("https://fundacionmiradas.org/") }, modifier = Modifier.fillMaxWidth()) { Text("Conocer Fundación Miradas ↗") }
                                    }
                                    androidx.compose.material3.HorizontalDivider()
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Autismo España", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Representación, investigación y apoyo a las personas autistas y sus familias.", fontSize = 13.sp, color = Color(0xFF667085))
                                        OutlinedButton(onClick = { uriHandler.openUri("https://autismo.org.es/") }, modifier = Modifier.fillMaxWidth()) { Text("Conocer Autismo España ↗") }
                                    }
                                    androidx.compose.material3.HorizontalDivider()
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Fundación ConecTEA", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Orientación, intervención y acompañamiento a personas autistas y sus familias.", fontSize = 13.sp, color = Color(0xFF667085))
                                        OutlinedButton(onClick = { uriHandler.openUri("https://www.fundacionconectea.org/") }, modifier = Modifier.fillMaxWidth()) { Text("Conocer Fundación ConecTEA ↗") }
                                    }
                                    Text("Las donaciones son voluntarias y, si decides realizarlas, se hacen directamente a cada entidad. Aprender para jugar no recibe dinero. No es necesario donar para utilizar la aplicación.", fontSize = 12.sp, color = Color(0xFF667085))
                                }
                            }

                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Código abierto y colaboración", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("El código fuente está disponible públicamente para consultar el desarrollo, revisar los cambios y colaborar.", fontSize = 14.sp, color = Color(0xFF667085))
                                    OutlinedButton(onClick = { uriHandler.openUri("https://github.com/Scorpyo82/Aprender-para-jugar") }, modifier = Modifier.fillMaxWidth()) { Text("Ver proyecto en GitHub ↗") }
                                }
                            }

                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Información de versión", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("Versión ${BuildConfig.VERSION_NAME} (código ${BuildConfig.VERSION_CODE})", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Fecha de compilación: ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())}", fontSize = 13.sp, color = Color(0xFF667085))
                                }
                            }

                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Estado del proyecto", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("La aplicación continúa en desarrollo. Las funciones y la interfaz pueden cambiar entre versiones. Las mejoras y correcciones se realizarán según la disponibilidad del desarrollador.", fontSize = 13.sp, color = Color(0xFF667085))
                                }
                            }
                        }
                    }
                    "typography" -> item {
                        Text("Tipografía de los ejercicios", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Esta preferencia se aplica de forma global al contenido que debe leer el niño en Tarjetas, Secuencias y Palabras. No modifica los ejercicios ni las bibliotecas exportadas.", fontSize = 14.sp, color = Color(0xFF667085))
                        Row(Modifier.fillMaxWidth().clickable { linkedExerciseFont = false; preferences.edit().putBoolean("linked_exercise_font_v1", false).apply() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(selected = !linkedExerciseFont, onClick = { linkedExerciseFont = false; preferences.edit().putBoolean("linked_exercise_font_v1", false).apply() })
                            Column(Modifier.weight(1f)) { Text("Fuente del teléfono", fontWeight = FontWeight.SemiBold); Text("Utiliza la tipografía predeterminada del dispositivo.", fontSize = 13.sp, color = Color(0xFF667085)) }
                        }
                        Row(Modifier.fillMaxWidth().clickable { linkedExerciseFont = true; preferences.edit().putBoolean("linked_exercise_font_v1", true).apply() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(selected = linkedExerciseFont, onClick = { linkedExerciseFont = true; preferences.edit().putBoolean("linked_exercise_font_v1", true).apply() })
                            Column(Modifier.weight(1f)) {
                                Text("Playwrite ES", fontWeight = FontWeight.SemiBold)
                                Text("Tipografía escolar ligada · Diseñada por TypeTogether, Veronika Burian y José Scaglione.", fontSize = 13.sp, color = Color(0xFF667085))
                                Text("Licencia SIL Open Font License 1.1 · Consultar licencia", modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openfontlicense.org/"))) } }, fontSize = 12.sp, color = Color(0xFF356BC5))
                            }
                        }
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F4EC))) {
                            Text("mamá · pelota · mariposa", Modifier.padding(16.dp), fontSize = 28.sp, fontFamily = exerciseFontFamily, color = Color(0xFF253344))
                        }
                    }
                    "security" -> item {
                        Text("Cambiar PIN", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Actualiza el PIN de acceso familiar", fontSize = 14.sp, color = Color(0xFF667085))
                        OutlinedTextField(value = newPinDraft, onValueChange = { newPinDraft = it.filter { ch -> ch.isDigit() }.take(4) }, label = { Text("Nuevo PIN de 4 cifras") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = { if (newPinDraft.length == 4) { pinStored = newPinDraft; preferences.edit().putString("parent_pin", pinStored).apply(); newPinDraft = ""; libraryMessage = "PIN actualizado." } else libraryMessage = "El PIN debe tener 4 cifras." }, modifier = Modifier.fillMaxWidth()) { Text("Guardar PIN") }
                        if (libraryMessage.isNotBlank()) Text(libraryMessage, color = Color(0xFF24733B))
                    }
                }
            }
            item { OutlinedButton(onClick = {
                tourReturnTarget = "home"
                screen = if (!preferences.getBoolean("welcome_tour_seen", false)) "tour" else "home"
                parentSection = "menu"
            }, modifier = Modifier.fillMaxWidth()) { Text("Salir de configuración") } }
        }
    } else if (screen == "exercise") {
        val isTabletLayout = LocalConfiguration.current.screenWidthDp >= 600
        val wordCardFontSize = if (isTabletLayout) 44.sp else 32.sp
        val pictureEmojiFontSize = if (isTabletLayout) 58.sp else 42.sp
        val pictureImageHeight = if (isTabletLayout) 150.dp else 110.dp
        val cardPadding = if (isTabletLayout) 24.dp else 18.dp
        val wordCardOrder = remember(activeExercisePairs) { exercisePairs.shuffled() }
        val pictureCardOrder = remember(activeExercisePairs) {
            val shuffled = exercisePairs.shuffled()
            if (shuffled.size > 1 && shuffled.map { it.word } == wordCardOrder.map { it.word }) shuffled.drop(1) + shuffled.first() else shuffled
        }
        Column(
            modifier = Modifier.fillMaxSize().clipToBounds().verticalScroll(rememberScrollState()).padding(if (isTabletLayout) 28.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Une las palabras con sus dibujos", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color(0xFF284B63))
            Text("1. Elige una palabra.  2. Toca su dibujo.", fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    wordCardOrder.forEach { item ->
                        val isSelected = selected["word"] == item.word
                        val isResolved = item.word in resolved
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (!isResolved) {
                                    val pendingPicture = selectedPicture
                                    if (pendingPicture.isBlank()) {
                                        if (selected["word"] == item.word) {
                                            selected = emptyMap()
                                            feedback = "Selección cancelada."
                                        } else {
                                            selected = mapOf("word" to item.word)
                                            feedback = "Ahora busca el dibujo de «" + item.word + "»."
                                        }
                                    } else {
                                        resolveCardsPair(item.word, pendingPicture)
                                    }
                                }
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isResolved -> Color(0xFFD9F2D9)
                                    isSelected -> Color(0xFFFFD66B)
                                    else -> Color(0xFFF0E8F5)
                                }
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(3.dp, Color(0xFFB56A00)) else null
                        ) {
                            Text(item.word, Modifier.padding(cardPadding), fontSize = wordCardFontSize, fontFamily = exerciseFontFamily, fontWeight = FontWeight.Bold, color = Color(0xFF29213B))
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pictureCardOrder.forEach { item ->
                        Card(Modifier.fillMaxWidth().clickable {
                            val word = selected["word"]
                            if (word == null) {
                                if (selectedPicture == item.picture) {
                                    selectedPicture = ""
                                    feedback = "Selección cancelada."
                                } else {
                                    selectedPicture = item.picture
                                    feedback = "Has elegido un dibujo. Ahora toca la palabra que le corresponde."
                                }
                            } else if (word !in resolved) {
                                resolveCardsPair(word, item.picture)
                            }
                        }, colors = CardDefaults.cardColors(containerColor = when { selectedPicture == item.picture -> Color(0xFFFFD66B); evaluatedPictures[item.picture] == true -> Color(0xFFD9F2D9); evaluatedPictures[item.picture] == false -> Color(0xFFFFDAD6); else -> Color(0xFFF0E8F5) }), border = when { selectedPicture == item.picture -> androidx.compose.foundation.BorderStroke(3.dp, Color(0xFFB56A00)); evaluatedPictures.containsKey(item.picture) -> androidx.compose.foundation.BorderStroke(3.dp, if (evaluatedPictures[item.picture] == true) Color(0xFF24733B) else Color(0xFFB3261E)); else -> null }) {
                            if (item.picture.startsWith("content://") || item.picture.startsWith("file://")) {
                                AndroidView(factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 180) } }, update = { view -> try { view.setImageURI(Uri.parse(item.picture)) } catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) } }, modifier = Modifier.fillMaxWidth().height(pictureImageHeight).padding(8.dp))
                            } else {
                                Text(item.picture, Modifier.padding(12.dp).align(Alignment.CenterHorizontally), fontSize = pictureEmojiFontSize)
                            }
                        }
                    }
                }
            }
            Text("Parejas resueltas: ${resolved.size}/$pairCount", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (feedback.isNotBlank()) Text(feedback, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (feedback.startsWith("Muy bien")) Color(0xFF24733B) else Color(0xFF72521B))
            OutlinedButton(onClick = { screen = "home" }) { Text("Salir del ejercicio") }
        }
    } else if (screen == "word-options") {
        WordOptionsScreen(
            selectedDifficulty = wordSelectedDifficulty,
            easyReward = easyRewardMinutes,
            normalReward = normalRewardMinutes,
            hardReward = hardRewardMinutes,
            isStarsMode = rewardMode.isStars,
            message = wordStartMessage,
            onBack = { screen = "home"; wordStartMessage = "" },
            onDifficultyChange = { level -> wordSelectedDifficulty = level; wordStartMessage = "" },
            onRandom = { startWordExercise(null) },
            onChooseCategory = {
                wordCategoryCandidates = wordExercises.filter { it.active && it.category !in disabledWordExerciseCategories && it.difficulty == wordSelectedDifficulty }
                    .map { it.category }.distinct().sorted()
                wordStartMessage = ""
                screen = "word-category-select"
            }
        )
    } else if (screen == "word-category-select") {
        WordCategorySelectionScreen(
            difficulty = wordSelectedDifficulty,
            availableCounts = wordExercises
                .filter { it.active && it.category !in disabledWordExerciseCategories && it.difficulty == wordSelectedDifficulty }
                .groupingBy { it.category }.eachCount().toSortedMap(),
            message = wordStartMessage,
            onBack = { screen = "word-options"; wordStartMessage = "" },
            onSelectCategory = { category -> startWordExercise(category) }
        )
    } else if (screen == "word-exercise") {
        val target = wordExerciseTarget
        if (target == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Palabras", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF24537A))
                Spacer(Modifier.height(16.dp))
                Text(wordExerciseMessage, fontSize = 17.sp, color = Color(0xFF667085), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = { screen = "home" }, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
            }
        } else {
            val targetWord = target.word
            val complete = wordExercisePlaced.size == targetWord.length
            Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Palabras", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF24537A))
                    OutlinedButton(onClick = { wordExerciseTarget = null; screen = "home" }) { Text("Salir") }
                }
                Text("Forma la palabra", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (target.picture.startsWith("content://") || target.picture.startsWith("file://")) {
                    AndroidView(
                        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 220) } },
                        update = { view -> try { view.setImageURI(Uri.parse(target.picture)) } catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) } },
                        modifier = Modifier.fillMaxWidth().height(170.dp)
                    )
                } else {
                    Text(target.picture, fontSize = 76.sp)
                }
                Text("Toca las letras en el orden correcto.", fontSize = 15.sp, color = Color(0xFF667085))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    targetWord.indices.forEach { position ->
                        Card(
                            modifier = Modifier.weight(1f).height(54.dp),
                            colors = CardDefaults.cardColors(containerColor = if (position < wordExercisePlaced.size) Color(0xFFE2F4DF) else Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB7C8B2))
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(wordExercisePlaced.getOrNull(position)?.let { targetWord[it].uppercase() } ?: "·", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), maxItemsInEachRow = 5) {
                    wordExerciseLetterOrder.forEach { letterIndex ->
                        val alreadyPlaced = letterIndex in wordExercisePlaced
                        OutlinedButton(
                            onClick = {
                                val letterResult = wordGameState.chooseLetter(letterIndex)
                                if (letterResult?.completed == true) {
                                    val baseReward = when (target.difficulty) { "Normal" -> normalRewardMinutes * 60; "Difícil" -> hardRewardMinutes * 60; else -> easyRewardMinutes * 60 }
                                    val penaltyPercent = when (wrongAnswerPenalty) { "none" -> 0; "full" -> 100; else -> 50 }
                                    val rewardPercent = (100 - letterResult.wrongAnswers * penaltyPercent).coerceAtLeast(20)
                                    addEarnedTime(baseReward * rewardPercent / 100)
                                    resultCorrectCount = (targetWord.length - letterResult.wrongAnswers).coerceIn(0, targetWord.length)
                                    resultTotalCount = targetWord.length
                                    correct = if (letterResult.wrongAnswers == 0) targetWord.length else resultCorrectCount
                                    target.directory?.let { directory ->
                                        runCatching { ExerciseRepository.recordResult(directory, resultCorrectCount, letterResult.wrongAnswers) }
                                    }
                                    runCatching { UsageStatisticsRepository.recordCompleted(context, ContentStorage.Modality.PALABRAS, target.difficulty) }
                                    screen = "result"
                                }
                            },
                            enabled = !complete && !alreadyPlaced,
                            modifier = Modifier.height(58.dp)
                        ) { Text(targetWord.getOrNull(letterIndex)?.uppercase() ?: "", fontSize = 24.sp, fontFamily = exerciseFontFamily, fontWeight = FontWeight.Bold) }
                    }
                }
                Text(wordExerciseMessage, fontSize = 16.sp, color = if (wordExerciseMessage.startsWith("¡")) Color(0xFF24733B) else Color(0xFF667085))
                Text("Dificultad: ${target.difficulty}", fontSize = 13.sp, color = Color(0xFF667085))
            }
        }
    } else if (screen == "play-time" && !rewardMode.isStars) {
        PlayTimeScreen(
            balanceText = formatRewardBalance(balanceSeconds, rewardMode),
            exerciseFontFamily = exerciseFontFamily,
            onResumeLearning = {
                val deadline = preferences.getLong("play_deadline_ms", 0L)
                val remaining = if (deadline > 0L) ((deadline - System.currentTimeMillis() + 999L) / 1000L).toInt().coerceAtLeast(0) else balanceSeconds.coerceAtLeast(0)
                // Returning to learning pauses the countdown but keeps this session
                // authorized while any earned time remains. A later exit resumes it.
                preferences.edit()
                    .putLong("play_deadline_ms", 0L)
                    .putInt("balance_seconds", remaining)
                    .putBoolean("play_session_authorized", remaining > 0)
                    .putBoolean("play_paused_for_screen_lock", false)
                    .commit()
                context.stopService(Intent(context, PlayTimeService::class.java))
                balanceSeconds = remaining
                val returnScreen = preferences.getString("play_time_return_screen", "home")
                    ?.takeIf { it.isNotBlank() && it != "play-time" } ?: "home"
                preferences.edit().remove("play_time_return_screen").apply()
                screen = returnScreen
            }
        )
    } else if (screen == "result" || screen == "sequence-result") {
        val rewardStars = when {
            resultCorrectCount * 2 == resultTotalCount -> 2
            resultCorrectCount * 2 > resultTotalCount && resultCorrectCount != resultTotalCount -> 3
            else -> 0
        }
        val rewardIcon = when {
            resultCorrectCount == 0 -> "😢"
            resultCorrectCount == resultTotalCount -> "🏆"
            else -> "⭐"
        }
        val rewardMessage = when {
            resultCorrectCount == 0 -> "¡Vamos a intentarlo otra vez!"
            resultCorrectCount == resultTotalCount -> "¡PERFECTO! ¡Eres un campeón!"
            resultCorrectCount * 2 < resultTotalCount -> "¡Buen intento! Sigue practicando"
            resultCorrectCount * 2 == resultTotalCount -> "¡Bien hecho! Has conseguido la mitad"
            else -> "¡Muy bien! Cada vez lo haces mejor"
        }
        ExerciseResultScreen(
            rewardStars = rewardStars,
            rewardIcon = rewardIcon,
            rewardMessage = rewardMessage,
            isPerfectCardsResult = correct == pairCount,
            rewardAmountText = if (rewardMode.isStars) "+${formatRewardBalance(lastEarnedSeconds, rewardMode)} ⭐" else "+${lastEarnedSeconds / 60}:${(lastEarnedSeconds % 60).toString().padStart(2, '0')}",
            rewardUnitText = if (rewardMode.isStars) "ESTRELLAS GANADAS" else "MINUTOS GANADOS",
            correctCount = resultCorrectCount,
            totalCount = resultTotalCount,
            onHome = {
                screen = "home"
                resolved = emptySet()
                selected = emptyMap()
                selectedPicture = ""
                evaluatedPictures = emptyMap()
                correct = 0
                feedback = ""
                lastEarnedSeconds = 0
                wordExerciseTarget = null
                wordExercisePlaced = emptyList()
                wordExerciseMessage = ""
                sequenceSelectedOrder = emptyList()
                sequencePlayMessage = ""
            },
            onNextExercise = {
                when (screen) {
                    "sequence-result" -> {
                        if (!startSequenceExercise(sequenceChosenCategory, sequenceChosenDifficulty)) screen = "sequence-difficulty"
                    }
                    else -> {
                        if (wordExerciseTarget != null) {
                            startWordExercise(wordCurrentCategory)
                        } else {
                            startCardsGame(mode, if (mode == "Elegir") selectedExerciseCategory else null)
                        }
                    }
                }
            }
        )
    } else if (screen == "sequences" || screen == "sequence-difficulty") {
        SequenceSelectionScreen(
            isDifficultyScreen = screen == "sequence-difficulty",
            chosenCategory = sequenceChosenCategory,
            message = sequencePlayMessage,
            maxCards = sequenceMaxCards,
            availableCounts = if (screen == "sequences") {
                listOf("Orden temporal", "Procesos", "Causa y consecuencia", "Ciclos")
                    .associateWith { eligibleSequenceExercises(it).size }
            } else {
                listOf("Fácil", "Normal", "Difícil")
                    .associateWith { eligibleSequenceExercises(sequenceChosenCategory, it).size }
            },
            rewardLabels = mapOf(
                "Fácil" to "${easyRewardMinutes} ${if (rewardMode.isStars) "estrellas" else "min de juego"}",
                "Normal" to "${normalRewardMinutes} ${if (rewardMode.isStars) "estrellas" else "min de juego"}",
                "Difícil" to "${hardRewardMinutes} ${if (rewardMode.isStars) "estrellas" else "min de juego"}"
            ),
            onBack = { screen = if (screen == "sequence-difficulty") "sequences" else "home" },
            onChooseType = { type, available ->
                sequenceChosenCategory = type
                sequenceChosenDifficulty = ""
                if (available) {
                    sequencePlayMessage = ""
                    screen = "sequence-difficulty"
                } else {
                    sequencePlayMessage = "No hay ejercicios activos de $type que respeten el máximo de $sequenceMaxCards tarjetas configurado."
                }
            },
            onChooseDifficulty = { level ->
                sequenceChosenDifficulty = level
                startSequenceExercise(sequenceChosenCategory, level)
            }
        )
    } else if (screen == "sequence-game") {
        SequenceGameScreen(
            exercise = sequencePlayingExercise,
            selectedOrder = sequenceSelectedOrder,
            playOrder = sequencePlayOrder,
            contentFormat = sequenceContentFormat,
            message = sequencePlayMessage,
            exerciseFontFamily = exerciseFontFamily,
            rewardMessage = if (rewardMode.isStars) "Has ganado estrellas por completar el ejercicio." else "Has ganado tiempo para jugar por completar el ejercicio.",
            onBack = {
                screen = "sequences"
                sequenceSelectedOrder = emptyList()
                sequencePlayMessage = ""
            },
            onSelectCard = { cardIndex ->
                val exercise = sequencePlayingExercise
                if (exercise != null && sequenceGameState.selectCard(exercise, cardIndex)) {
                    sequencePlayMessage = "¡Secuencia completada!"
                    val baseRewardSeconds = (when (sequenceChosenDifficulty) { "Normal" -> normalRewardMinutes; "Difícil" -> hardRewardMinutes; else -> easyRewardMinutes }) * 60
                    val penaltyPercent = when (wrongAnswerPenalty) { "none" -> 0; "full" -> 100; else -> 50 }
                    val rewardPercent = (100 - sequenceWrongAnswers * penaltyPercent).coerceAtLeast(20)
                    addEarnedTime(baseRewardSeconds * rewardPercent / 100)
                    val resultSize = exercise.cards.size
                    resultCorrectCount = (resultSize - sequenceWrongAnswers).coerceIn(0, resultSize)
                    resultTotalCount = resultSize
                    val usageKey = sequenceGameState.dailyUsagePreferenceKey(exercise)
                    preferences.edit().putInt(usageKey, preferences.getInt(usageKey, 0) + 1).apply()
                    exercise.directory?.let { directory ->
                        runCatching { ExerciseRepository.recordResult(directory, resultCorrectCount, sequenceWrongAnswers) }
                    }
                    runCatching {
                        UsageStatisticsRepository.recordCompleted(
                            context,
                            ContentStorage.Modality.SECUENCIAS,
                            sequenceChosenDifficulty,
                            SequenceType.fromDisplayName(exercise.type)
                        )
                    }
                    screen = "sequence-result"
                }
            }
        )
    } else if (screen == "stories") {
        val availableStories = storedStories.filter { it.collection !in disabledStoryCollections }
        val availableCollections = availableStories.map { it.collection }.distinct().sortedBy { folder ->
            storyCollections.firstOrNull { StoryRepository.collectionFolderName(it) == folder }?.lowercase() ?: folder.lowercase()
        }
        val selectedCollection = selectedStoriesCollection
        val collectionStories = selectedCollection?.let { folder -> availableStories.filter { it.collection == folder }.sortedBy { it.metadata.title.lowercase() } }.orEmpty()
        val boardWidth = LocalConfiguration.current.screenWidthDp
        val boardColumns = when {
            boardWidth >= 1100 -> 5
            boardWidth >= 820 -> 4
            boardWidth >= 560 -> 3
            else -> 2
        }
        val boardGap = 12.dp
        val boardCardWidth = ((boardWidth - 40 - (boardColumns - 1) * 12) / boardColumns).coerceAtLeast(132).dp
        LazyColumn(
            Modifier.fillMaxSize().background(Color(0xFF101827)).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedButton(onClick = { screen = "home"; selectedStoriesCollection = null; selectedStoryPath = null }) { Text("← Volver al inicio", color = Color.White) }
                Text(
                    if (selectedCollection == null) "Cuentos" else "Colección",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    if (selectedCollection == null) "Elige una historia para comenzar tu aventura." else "Explora los cuentos de esta colección.",
                    fontSize = 15.sp,
                    color = Color(0xFFB8C3D3)
                )
            }
            if (selectedCollection == null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("all" to "Todos los cuentos", "collections" to "Colecciones").forEach { (key, label) ->
                            val selected = storiesBoardTab == key
                            Card(
                                Modifier.weight(1f).clickable { storiesBoardTab = key },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF263B59) else Color(0xFF1A2638)),
                                border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF8DB8F5) else Color(0xFF34445A))
                            ) {
                                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else Color(0xFFB8C3D3), textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                if (storiesBoardTab == "all") {
                    if (availableStories.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Image(painter = painterResource(id = R.drawable.home_header_fox), contentDescription = "El zorro leyendo un cuento", modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp), contentScale = ContentScale.Fit)
                                Text("Todavía no hay cuentos disponibles.", color = Color(0xFFD4DCE7), fontSize = 15.sp, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        item {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(boardGap),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                availableStories.sortedBy { it.metadata.title.lowercase() }.forEach { story ->
                                    val coverFile = story.metadata.cover?.let { File(story.directory, it) }?.takeIf { it.isFile }
                                    Card(
                                        Modifier.width(boardCardWidth).clickable { selectedStoryPath = story.directory.absolutePath; screen = "story-cover" },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2638)),
                                        border = androidx.compose.foundation.BorderStroke(if (selectedStoryPath == story.directory.absolutePath) 2.dp else 1.dp, if (selectedStoryPath == story.directory.absolutePath) Color(0xFF8DB8F5) else Color(0xFF34445A))
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(
                                                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clipToBounds().background(Color(0xFF29384D), RoundedCornerShape(11.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (coverFile != null) {
                                                    AndroidView(
                                                        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                                                        update = { view -> view.setImageURI(Uri.fromFile(coverFile)) },
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Text("📖", fontSize = 48.sp)
                                                }
                                            }
                                            Text(
                                                story.metadata.title,
                                                fontSize = 15.sp,
                                                fontFamily = exerciseFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                softWrap = true,
                                                minLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (availableCollections.isEmpty()) {
                        item { Text("Todavía no hay colecciones con cuentos disponibles.", color = Color(0xFFD4DCE7), fontSize = 15.sp) }
                    } else {
                        item {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(boardGap),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                availableCollections.forEach { folder ->
                                    val displayName = storyCollections.firstOrNull { StoryRepository.collectionFolderName(it) == folder } ?: folder.replace('_', ' ')
                                    val coverFile = StoryRepository.collectionCoverFile(context, folder)?.takeIf { it.isFile }
                                    Card(
                                        Modifier.width(boardCardWidth).clickable { selectedStoriesCollection = folder },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2638)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34445A))
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(
                                                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clipToBounds().background(Color(0xFF29384D), RoundedCornerShape(11.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (coverFile != null) {
                                                    AndroidView(
                                                        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                                        update = { view -> view.setImageURI(Uri.fromFile(coverFile)) },
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Text("📚", fontSize = 48.sp)
                                                }
                                            }
                                            Text(displayName, fontSize = 15.sp, fontFamily = exerciseFontFamily, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                                            Text("${availableStories.count { it.collection == folder }} cuentos", fontSize = 12.sp, color = Color(0xFFB8C3D3))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    val displayName = storyCollections.firstOrNull { StoryRepository.collectionFolderName(it) == selectedCollection } ?: selectedCollection.replace('_', ' ')
                    val coverFile = StoryRepository.collectionCoverFile(context, selectedCollection)?.takeIf { it.isFile }
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedStoriesCollection = null },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clipToBounds().background(Color(0xFF29384D), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            if (coverFile != null) {
                                AndroidView(
                                    factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                    update = { view -> view.setImageURI(Uri.fromFile(coverFile)) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text("📚", fontSize = 30.sp)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(displayName, color = Color.White, fontSize = 19.sp, fontFamily = exerciseFontFamily, fontWeight = FontWeight.Bold)
                            Text("← Volver a colecciones", color = Color(0xFF9FC5F8), fontSize = 13.sp)
                        }
                    }
                }
                if (collectionStories.isEmpty()) {
                    item { Text("Esta colección todavía no contiene cuentos.", color = Color(0xFFD4DCE7), fontSize = 15.sp) }
                } else {
                    item {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(boardGap),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            collectionStories.forEach { story ->
                                val coverFile = story.metadata.cover?.let { File(story.directory, it) }?.takeIf { it.isFile }
                                Card(
                                    Modifier.width(boardCardWidth).clickable { selectedStoryPath = story.directory.absolutePath; screen = "story-cover" },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2638)),
                                    border = androidx.compose.foundation.BorderStroke(if (selectedStoryPath == story.directory.absolutePath) 2.dp else 1.dp, if (selectedStoryPath == story.directory.absolutePath) Color(0xFF8DB8F5) else Color(0xFF34445A))
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clipToBounds().background(Color(0xFF29384D), RoundedCornerShape(11.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (coverFile != null) {
                                                AndroidView(
                                                    factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                                                    update = { view -> view.setImageURI(Uri.fromFile(coverFile)) },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Text("📖", fontSize = 48.sp)
                                            }
                                        }
                                        Text(story.metadata.title, fontSize = 15.sp, fontFamily = exerciseFontFamily, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else if (screen == "story-cover") {
        val selectedStory = selectedStoryPath?.let { path ->
            storedStories.firstOrNull { it.directory.absolutePath == path }
                ?: runCatching { StoryRepository.read(File(path)) }.getOrNull()
        }
        if (selectedStory == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No se ha podido abrir este cuento.")
                OutlinedButton(onClick = { screen = "stories"; selectedStoryPath = null }) { Text("Volver a la cartelera") }
            }
        } else {
            StoryCoverScreen(
                story = selectedStory,
                fontFamily = exerciseFontFamily,
                onBack = { screen = "stories"; selectedStoryPath = null },
                onPlay = { screen = "story-player" }
            )
        }
    } else if (screen == "story-player") {
        val selectedStory = selectedStoryPath?.let { path ->
            storedStories.firstOrNull { it.directory.absolutePath == path }
                ?: runCatching { StoryRepository.read(File(path)) }.getOrNull()
        }
        if (selectedStory == null) {
            screen = "stories"
        } else if (selectedStory.metadata.connections.values.any { STORY_LOST_DESTINATION in it }) {
            AlertDialog(
                onDismissRequest = { screen = "stories"; selectedStoryPath = null },
                title = { Text("Estructura narrativa incompleta") },
                text = { Text("Este cuento contiene conexiones huérfanas. Entra en su editor, abre «Editar estructura narrativa» y reasigna o elimina las salidas marcadas en rojo antes de reproducirlo.") },
                confirmButton = { Button(onClick = { screen = "stories"; selectedStoryPath = null }) { Text("Volver") } }
            )
        } else {
            StoryPlayerScreen(
                story = selectedStory,
                balanceSeconds = balanceSeconds,
                fontFamily = exerciseFontFamily,
                onExit = { screen = "stories"; selectedStoryPath = null }
            )
        }
    } else if (screen == "story-inventory") {
        StoryObjectInventoryScreen(
            objects = storedStoryObjects,
            ownedFolders = storedInventoryFolders,
            unlockedFolders = ObjectUnlockRepository.list(context),
            balanceSeconds = balanceSeconds,
            isStarsMode = rewardMode.isStars,
            message = storyInventoryMessage,
            fontFamily = exerciseFontFamily,
            onBack = { screen = "home"; storyInventoryMessage = "" },
            onPurchase = { stored ->
                val result = InventoryRepository.purchase(context, stored.folderName)
                balanceSeconds = result.balanceSeconds
                storyObjectInventoryState.refresh(context)
                storyInventoryMessage = result.message
                result.success
            }
        )
    } else if (screen == "cards-options") {
        CardsOptionsScreen(
            difficulty = difficulty,
            easyReward = easyRewardMinutes,
            normalReward = normalRewardMinutes,
            hardReward = hardRewardMinutes,
            isStarsMode = rewardMode.isStars,
            manualCategoryLimitReached = manualCategoryLimitReached,
            manualCategoryUsesToday = manualCategoryUsesToday,
            dailyManualLimit = dailyManualLimit,
            message = cardsStartMessage,
            onBack = { screen = "home"; cardsStartMessage = "" },
            onDifficultyChange = { difficulty = it },
            onRandom = { startCardsGame("Al azar") },
            onMixed = { startCardsGame("Mezcladas") },
            onChooseCategory = { cardsStartMessage = ""; screen = "category-select" }
        )
    } else if (screen == "help") {
        HelpScreen(isStarsMode = rewardMode.isStars, onBack = { screen = "home" })
    } else if (screen == "category-select") {
        CardsCategorySelectionScreen(
            categories = categories,
            availableCounts = categories.associateWith { name ->
                pairs.count { it.active && it.difficulty == difficulty && it.category.equals(name, ignoreCase = true) }
            },
            questionCount = questionCount,
            manualCategoryLimitReached = manualCategoryLimitReached,
            message = cardsStartMessage,
            onBack = { screen = "cards-options"; cardsStartMessage = "" },
            onSelectCategory = { name -> startCardsGame("Elegir", name) }
        )
    } else {
        val homeWidth = LocalConfiguration.current.screenWidthDp
        val modeImageSize = if (homeWidth >= 700) 210.dp else if (homeWidth >= 500) 165.dp else 118.dp
        val modeCardPadding = if (homeWidth >= 700) 20.dp else 12.dp
        val modeTitleSize = if (homeWidth >= 700) 27.sp else 21.sp
        val modeDescriptionSize = if (homeWidth >= 700) 17.sp else 14.sp
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(if (homeWidth >= 700) 28.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HomeTopActions(
                    soundEnabled = soundEnabled,
                    onOpenSettings = { pinEntry = ""; pinError = false; screen = "parent-pin" },
                    onToggleSound = {
                        soundEnabled = !soundEnabled
                        preferences.edit().putBoolean("sound_enabled", soundEnabled).apply()
                    },
                    onOpenHelp = { screen = "help" }
                )
            }
            item {
                HomeRewardBalanceCard(
                    isStars = rewardMode.isStars,
                    formattedBalance = formatRewardBalance(balanceSeconds, rewardMode),
                    minutes = balanceMinutes,
                    remainderSeconds = balanceRemainderSeconds
                )
            }
            item {
                HomeFoxHeader()
            }
            item { HomeSectionTitle("Elige cómo aprender") }
            item {
                Card(
                    Modifier.fillMaxWidth().clickable(enabled = gameActiveCards) { screen = "cards-options"; cardsStartMessage = "" },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0B8)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8B95D)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(modeCardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ModeCardImage(0, "Ilustración de Tarjetas", Modifier.width(modeImageSize).height(modeImageSize))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Tarjetas", fontSize = modeTitleSize, fontWeight = FontWeight.ExtraBold, color = Color(0xFF754411))
                            Text("Aprender con imágenes", fontSize = modeDescriptionSize, color = Color(0xFF795A3C))
                            Text(if (gameActiveCards) "EMPEZAR  ›" else "🔒 BLOQUEADO", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (gameActiveCards) Color(0xFF754411) else Color(0xFFB3261E))
                        }
                    }
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth().clickable(enabled = gameActiveSequences) { if (gameActiveSequences) screen = "sequences" },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDDF3D3)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA9D79A))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(modeCardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ModeCardImage(1, "Ilustración de Secuencias", Modifier.width(modeImageSize).height(modeImageSize))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Secuencias", fontSize = modeTitleSize, fontWeight = FontWeight.ExtraBold, color = Color(0xFF245C2A))
                            Text("Ordenar y razonar", fontSize = modeDescriptionSize, color = Color(0xFF476C51))
                            Text(if (gameActiveSequences) "EMPEZAR  ›" else "🔒 BLOQUEADO", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (gameActiveSequences) Color(0xFF39713A) else Color(0xFFB3261E))
                        }
                    }
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth().clickable(enabled = gameActiveWords) {
                        wordSelectedDifficulty = "Fácil"
                        wordStartMessage = ""
                        screen = "word-options"
                    },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD8EEFF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9CCCF4))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(modeCardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ModeCardImage(2, "Ilustración de Palabras", Modifier.width(modeImageSize).height(modeImageSize))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Palabras", fontSize = modeTitleSize, fontWeight = FontWeight.ExtraBold, color = Color(0xFF24537A))
                            Text("Ordenar letras y completar palabras", fontSize = modeDescriptionSize, color = Color(0xFF42647D))
                            Text(if (gameActiveWords) "¡JUGAR!" else "🔒 BLOQUEADO", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (gameActiveWords) Color(0xFF285F94) else Color(0xFFB3261E))
                        }
                    }
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth().clickable(enabled = gameActiveStories) { if (gameActiveStories) screen = "stories" },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE5D2)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8B28D))
                ) {
                    Row(Modifier.fillMaxWidth().padding(modeCardPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Image(painter = painterResource(id = R.drawable.activity_stories), contentDescription = "El zorro leyendo un cuento", modifier = Modifier.width(modeImageSize).height(modeImageSize), contentScale = ContentScale.Fit)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Cuentos", fontSize = modeTitleSize, fontWeight = FontWeight.ExtraBold, color = Color(0xFF87451F))
                            Text("Leer, escuchar y descubrir historias", fontSize = modeDescriptionSize, color = Color(0xFF795A3C))
                            Text(if (gameActiveStories) "EMPEZAR  ›" else "🔒 BLOQUEADO", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (gameActiveStories) Color(0xFF87451F) else Color(0xFFB3261E))
                        }
                    }
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth().clickable {
                        storedStoryObjects = ObjectRepository.list(context)
                        storyObjectInventoryState.refresh(context)
                        storyInventoryMessage = ""
                        screen = "story-inventory"
                    },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8E5FA)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB9AFE8))
                ) {
                    Row(Modifier.fillMaxWidth().padding(modeCardPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            Modifier.width(modeImageSize).height(modeImageSize).background(Color(0xFFD8D0F4), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text("🎒", fontSize = if (homeWidth >= 700) 72.sp else 48.sp) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Inventario", fontSize = modeTitleSize, fontWeight = FontWeight.ExtraBold, color = Color(0xFF493B82))
                            Text("Tus objetos conseguidos y los que puedes comprar", fontSize = modeDescriptionSize, color = Color(0xFF62578D))
                            Text("VER OBJETOS  ›", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF493B82))
                        }
                    }
                }
            }
            if (!rewardMode.isStars) item {
                OutlinedButton(onClick = { pinEntry = ""; pinError = false; screen = "exit-pin" }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("🔒 Salir de la aplicación", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(4.dp))
                Text("Completa ejercicios para acumular minutos de juego.", fontSize = 12.sp, color = Color(0xFF667085))
                val sessionAlreadyAuthorized = preferences.getBoolean("play_session_authorized", false)
                val canStartPlaySession = balanceSeconds > 0 &&
                    (sessionAlreadyAuthorized || balanceSeconds >= minimumPlayMinutes * 60)
                Button(onClick = {
                    val now = System.currentTimeMillis()
                    val existingDeadline = preferences.getLong("play_deadline_ms", 0L)
                    val savedBalance = preferences.getInt("balance_seconds", balanceSeconds).coerceAtLeast(0)
                    val sessionAuthorized = preferences.getBoolean("play_session_authorized", false)
                    // A new session requires the configured minimum; an already-authorized
                    // session can continue after leaving the app or returning with less time.
                    if (!sessionAuthorized && savedBalance < minimumPlayMinutes * 60) {
                        balanceSeconds = savedBalance
                        return@Button
                    }
                    // If a countdown already exists, never replace it with stale Compose state.
                    // Otherwise returning from Home could reset the deadline and spend the same
                    // earned time twice.
                    val deadline = if (existingDeadline > now) existingDeadline
                        else if (savedBalance > 0) now + savedBalance.toLong() * 1000L
                        else 0L
                    if (deadline > now) {
                        val remaining = ((deadline - now + 999L) / 1000L).toInt().coerceAtLeast(0)
                        preferences.edit()
                            .putLong("play_deadline_ms", deadline)
                            .putInt("balance_seconds", remaining)
                            .putBoolean("play_session_authorized", true)
                            .putBoolean("expiry_relaunch_attempted", false)
                            .commit()
                        balanceSeconds = remaining
                        androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, PlayTimeService::class.java).setAction(PlayTimeService.ACTION_START))
                        screen = "play-time"
                        (context as? ComponentActivity)?.moveTaskToBack(true)
                    } else {
                        balanceSeconds = 0
                    }
                }, enabled = canStartPlaySession, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF267A55), disabledContainerColor = Color(0xFFE7E1D8), disabledContentColor = Color(0xFF667085))) {
                    Text(
                        when {
                            balanceSeconds <= 0 -> "🔒 Sin tiempo para jugar"
                            !canStartPlaySession -> "🔒 Necesitas $minimumPlayMinutes min para empezar"
                            else -> "▶ Jugar con el tiempo ganado"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (screen == "parent") item {
                Text("Configuración parental pendiente: PIN, categorías y biblioteca de palabras se añadirán en una siguiente fase.")
            }
        }
    }
    if (remoteLibraryPendingImport != null) {
        val pendingPackage = remoteLibraryPendingImport!!
        AlertDialog(
            onDismissRequest = { remoteLibraryPendingImport = null },
            title = { Text("Biblioteca descargada") },
            text = { Text("«${pendingPackage.name}» se ha descargado correctamente y está lista para instalar. Puedes importarla ahora o dejarla para más tarde.") },
            confirmButton = {
                TextButton(onClick = {
                    remoteLibraryScope.launch {
                        try {
                            val file = RemoteLibraryRepository.cachedFile(context, pendingPackage.name)
                            check(file.isFile && file.length() > 0L) { "El archivo descargado ya no está disponible. Descárgalo de nuevo." }
                            val inspected = withContext(Dispatchers.IO) {
                                LibraryPackageManager.inspect(context, Uri.fromFile(file))
                            }
                            transferPackage?.let { LibraryPackageManager.discard(it) }
                            transferPackage = inspected
                            transferImportCategories = inspected.modalities.associate { info -> info.modality to info.categories.map { it.directory }.toSet() }
                            transferImportStories = inspected.stories.map { it.key }.toSet()
                            transferImportObjects = emptySet()
                            transferObjectConflictDecisions = emptyMap()
                            transferStoryConflictDecisions = emptyMap()
                            transferExerciseConflictDecisions = emptyMap()
                            transferConflictIndex = 0
                            transferReplaceDuplicates = false
                            transferImportStatistics = false
                            transferImportUsageStatistics = false
                            transferRemoteLibraryName = pendingPackage.name
                            transferMessage = "Paquete descargado y validado. Selecciona el contenido que deseas importar."
                            remoteLibraryPendingImport = null
                            parentSection = "library-import"
                        } catch (error: Exception) {
                            remoteLibraryMessage = "No se ha podido validar el paquete: ${error.message ?: "archivo no válido"}"
                            remoteLibraryPendingImport = null
                        }
                    }
                }) { Text("Importar ahora") }
            },
            dismissButton = {
                TextButton(onClick = { remoteLibraryPendingImport = null }) { Text("Más tarde") }
            }
        )
    }
}







