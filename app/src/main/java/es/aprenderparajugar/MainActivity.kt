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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
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

private data class LibraryImportPreparation(
    val exerciseConflicts: List<LibraryPackageManager.ExerciseConflictInfo>,
    val objectConflicts: List<LibraryPackageManager.ObjectConflictInfo>,
    val storyNameConflicts: List<Pair<LibraryPackageManager.StoryInfo, StoredStory>>,
    val incomingObjects: List<Pair<LibraryPackageManager.ObjectDependencyInfo, StoredObject>>,
    val requiredObjects: Map<String, List<String>>,
    val localObjects: List<StoredObject>,
    val conflictThumbnails: Map<String, Pair<Bitmap?, Bitmap?>>
)

private fun decodeLibraryImportThumbnail(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 256 || bounds.outHeight / sampleSize > 256) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
    )
}

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
        NotificationQueueRepository.initialize(this)
        StoryUnlockNotificationRepository.initialize(this)
        // A manual launch re-arms the focus guard after a parent-authorized exit.
        getSharedPreferences("aprender_para_jugar", MODE_PRIVATE)
            .edit().putBoolean("manual_exit_allowed", false).apply()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFFF8E8)) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AprenderApp()
                        AppNotificationHost(Modifier.align(Alignment.TopEnd))
                    }
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

private fun isAccessibilityServiceEnabled(context: Context): Boolean =
    isPlayTimeAccessibilityEnabled(context)

private fun currentDayKey(): String {
    val calendar = Calendar.getInstance()
    return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
}

@Composable
internal fun StoryImagePreview(file: File?, label: String, revision: Int = 0) {
    val bitmap = remember(file?.absolutePath, file?.lastModified(), revision) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AprenderApp() {
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val preferences = remember { context.getSharedPreferences("aprender_para_jugar", Context.MODE_PRIVATE) }
    var rewardMode by remember { mutableStateOf(RewardMode.fromPreferences(preferences)) }
    var pendingTimeModeActivation by remember { mutableStateOf(preferences.getBoolean("pending_time_mode_activation", !preferences.getBoolean("first_launch_setup_seen", false) && !RewardMode.fromPreferences(preferences).isStars)) }
    var permissionRecoveryNotice by remember { mutableStateOf(false) }
    var showPermissionRecoveryDialog by remember { mutableStateOf(false) }
    var showTimeModeNotActivatedDialog by remember { mutableStateOf(false) }
    var expandedLearningModalities by remember { mutableStateOf(setOf<ContentStorage.Modality>()) }
    // No se borran datos del usuario al iniciar ni al actualizar la aplicación.
    val pairsState = remember { mutableStateOf(ExerciseUiStorage.loadPairs(context, ContentStorage.Modality.TARJETAS)) }
    var pairs by pairsState
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
    val wordDraftState = remember { mutableStateOf("") }
    var wordDraft by wordDraftState
    val pictureDraftState = remember { mutableStateOf("") }
    var pictureDraft by pictureDraftState
    val categoryDraftState = remember { mutableStateOf("") }
    var categoryDraft by categoryDraftState
    val difficultyDraftState = remember { mutableStateOf("Fácil") }
    var difficultyDraft by difficultyDraftState
    val editingWordState = remember { mutableStateOf<String?>(null) }
    var editingWord by editingWordState
    val libraryMessageState = remember { mutableStateOf("") }
    var libraryMessage by libraryMessageState
    val libraryPageState = remember { mutableStateOf("list") }
    var libraryPage by libraryPageState
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
    val storedStoryObjectsState = remember { mutableStateOf(emptyList<StoredObject>()) }
    var storedStoryObjects by storedStoryObjectsState
    val storyObjectInventoryState = remember(context) { StoryObjectInventoryState(context) }
    var storedInventoryFolders by storyObjectInventoryState.folders
    var storyInventoryMessage by remember { mutableStateOf("") }
    val storyObjectCollectionMenuExpandedState = remember { mutableStateOf(false) }
    var storyObjectCollectionMenuExpanded by storyObjectCollectionMenuExpandedState
    val storyObjectCollectionsState = remember(preferences) { StoryObjectCollectionsState(preferences) }
    var storyObjectCollections by storyObjectCollectionsState.collections
    var storyObjectCollectionDraft by storyObjectCollectionsState.draft
    var storyObjectSelectedCollection by storyObjectEditorState.selectedCollection
    var storyObjectCollectionMessage by storyObjectCollectionsState.message
    var storyObjectCollectionToDelete by storyObjectCollectionsState.collectionToDelete
    val objectLibraryCollectionFilterState = remember { mutableStateOf(ALL_OBJECT_COLLECTIONS) }
    var objectLibraryCollectionFilter by objectLibraryCollectionFilterState
    val objectStoreMessageState = remember { mutableStateOf("") }
    var objectStoreMessage by objectStoreMessageState
    val objectEditorReturnSectionState = remember { mutableStateOf("stories-object-library") }
    var objectEditorReturnSection by objectEditorReturnSectionState
    val storyCollectionsState = remember(preferences) { StoryCollectionsState(context, preferences) }
    var storyCollections by storyCollectionsState.collections
    var storyCollectionDraft by storyCollectionsState.draft
    var storyCollectionMessage by storyCollectionsState.message
    val storyEditorState = remember(context) { StoryEditorState(context) }
    val storyEditorAudioActions = rememberStoryEditorAudioActions(storyEditorState, preferences)
    var storyEditorTitle by storyEditorState.title
    var showStoryAccessRequirements by remember { mutableStateOf(false) }
    var storyEditorCover by storyEditorState.cover
    var storyEditorIllustrations by storyEditorState.illustrations
    var editingStoryFolder by storyEditorState.editingFolder
    var editingStoryCollectionFolder by storyEditorState.editingCollectionFolder
    var storedStories by storyEditorState.storedStories
    var storyEditorMessage by storyEditorState.message
    var storyEditorCollection by storyEditorState.collection
    var storyEditorSummary by storyEditorState.summary
    var storyEditorScenes by storyEditorState.scenes
    var storyEditorSelectedScene by storyEditorState.selectedScene
    var storyScenePendingDeletion by remember { mutableStateOf<String?>(null) }
    val storyAudioSettingsState = remember(context) { StoryAudioSettingsState(context) }
    var showPendingStoryObjectImages by remember { mutableStateOf(preferences.getBoolean("stories_show_pending_object_images", false)) }
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
    val storyCollectionCoverRevisionState = remember { mutableIntStateOf(0) }
    var storyCollectionCoverRevision by storyCollectionCoverRevisionState
    val storyEditorCoverRevisionState = remember { mutableIntStateOf(0) }
    var storyEditorCoverRevision by storyEditorCoverRevisionState
    val storyEditorIllustrationRevisionState = remember { mutableIntStateOf(0) }
    var storyEditorIllustrationRevision by storyEditorIllustrationRevisionState
    val editingStoryCollectionState = remember { mutableStateOf<String?>(null) }
    var editingStoryCollection by editingStoryCollectionState
    var storiesBoardTab by remember { mutableStateOf("all") }
    val selectedStoriesCollectionState = remember { mutableStateOf<String?>(null) }
    var selectedStoriesCollection by selectedStoriesCollectionState
    var selectedStoryPath by remember { mutableStateOf<String?>(null) }

    fun persistStoryDraft(): StoredStory? = storyEditorState.persist()
    val sequenceLibraryState = remember(context, preferences) { SequenceLibraryState(context, preferences) }
    var sequenceExercises by sequenceLibraryState.exercises
    var sequenceEditingIndex by remember { mutableIntStateOf(-1) }
    var sequenceTitleDraft by remember { mutableStateOf("") }
    val sequenceMaxCardsState = remember { mutableIntStateOf(preferences.getInt("sequence_max_cards_v1", 6).coerceIn(2, 6)) }
    var sequenceMaxCards by sequenceMaxCardsState
    val sequenceContentFormatState = remember { mutableStateOf(preferences.getString("sequence_content_format_v1", "Imagen y texto") ?: "Imagen y texto") }
    var sequenceContentFormat by sequenceContentFormatState
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
    val pendingCameraTargetState = remember { mutableStateOf("pair") }
    var pendingCameraTarget by pendingCameraTargetState
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
            storyEditorCoverRevision++
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
            storyEditorIllustrationRevision++
        }.onFailure { storyEditorMessage = it.message ?: "No se pudo importar la ilustración." }
    }
    val onPickStoryCover: () -> Unit = {
        preferences.edit().putBoolean("content_picker_active", true).apply()
        storyCoverPicker.launch(arrayOf("image/*"))
    }
    val onPickStoryIllustration: () -> Unit = {
        preferences.edit().putBoolean("content_picker_active", true).apply()
        storyIllustrationPicker.launch(arrayOf("image/*"))
    }
    val onChooseStoryCollectionCover: (String) -> Unit = { name ->
        pendingStoryCollectionCover = name
        preferences.edit().putBoolean("content_picker_active", true).apply()
        storyCollectionCoverPicker.launch(arrayOf("image/*"))
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
    var activeExerciseMode by remember { mutableStateOf("cards") }
    // Pantallas padre conservadas para que el gesto Atrás respete el origen real.
    var cardsExerciseReturnScreen by remember { mutableStateOf("cards-options") }
    var wordExerciseReturnScreen by remember { mutableStateOf("word-options") }
    var sequenceGameReturnScreen by remember { mutableStateOf("sequence-difficulty") }
    var storyAccessReturnScreen by remember { mutableStateOf("stories") }
    val appAudioState = remember(context, firstLaunchNeedsSetup) {
        AppAudioState(context, firstLaunchNeedsSetup)
    }
    var appInForeground by remember { mutableStateOf(true) }
    val setupAccessibilityEnabledState = remember { mutableStateOf(false) }
    var setupAccessibilityEnabled by setupAccessibilityEnabledState
    val restrictedSettingsDoneState = remember { mutableStateOf(preferences.getBoolean("restricted_settings_done", false)) }
    var restrictedSettingsDone by restrictedSettingsDoneState
    val batterySettingsDoneState = remember { mutableStateOf(preferences.getBoolean("battery_setup_done", false)) }
    var batterySettingsDone by batterySettingsDoneState
    val xiaomiWindowPermissionDoneState = remember { mutableStateOf(preferences.getBoolean("xiaomi_window_permission_done", false)) }
    var xiaomiWindowPermissionDone by xiaomiWindowPermissionDoneState
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
    val balanceSecondsState = remember { mutableIntStateOf(preferences.getInt("balance_seconds", 0)) }
    var balanceSeconds by balanceSecondsState
    val minimumPlayMinutesState = remember { mutableIntStateOf(preferences.getInt("minimum_play_minutes", 5).coerceIn(0, 60)) }
    var minimumPlayMinutes by minimumPlayMinutesState
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
    var parentSessionAuthenticated by remember { mutableStateOf(false) }
    var permissionRecoveryEntryPending by remember { mutableStateOf(false) }
    val parentSectionState = remember { mutableStateOf("menu") }
    var parentSection by parentSectionState
    val onCreateSequenceExercise = {
        sequenceEditingIndex = -1; sequenceTitleDraft = ""
        sequenceCategoryDraft = ""; sequenceTypeDraft = "Orden temporal"; sequenceDifficultyDraft = "Normal"
        sequenceCardsDraft = listOf("", "", ""); sequenceCardPicturesDraft = listOf("", "", "")
        sequenceAlternativeDraft = ""; sequenceEditorMessage = ""; parentSection = "sequence-editor-temporal"
    }
    val onEditSequenceExercise: (Int, SequenceExercise) -> Unit = { index, exercise ->
        sequenceEditingIndex = index; sequenceTitleDraft = exercise.title
        sequenceCategoryDraft = exercise.category; sequenceTypeDraft = exercise.type; sequenceDifficultyDraft = exercise.difficulty
        sequenceCardsDraft = exercise.cards
        sequenceCardPicturesDraft = exercise.cards.indices.map { cardIndex -> exercise.pictures.getOrNull(cardIndex).orEmpty().ifBlank { defaultSequencePicture(cardIndex) } }
        sequenceAlternativeDraft = exercise.alternatives.joinToString("\n")
        sequenceEditorMessage = ""; parentSection = "sequence-editor-temporal"
    }
    androidx.compose.runtime.LaunchedEffect(appInForeground, screen, rewardMode) {
        // Detect revoked prerequisites, pause Time mode and inform the parent.
        // Never open the permission wizard automatically: the parent chooses when to continue.
        if (appInForeground && screen == "home" && !rewardMode.isStars &&
            !areMandatoryDeviceSetupPermissionsReady(context)
        ) {
            pendingTimeModeActivation = false
            permissionRecoveryNotice = true
            showPermissionRecoveryDialog = true
            rewardMode = RewardMode.STARS
            preferences.edit()
                .putString(RewardMode.PREFERENCE_KEY, "stars")
                .putBoolean("pending_time_mode_activation", false)
                .putBoolean("device_setup_in_progress", false)
                .putBoolean("parent_area_active", false)
                .apply()
        }
    }
    androidx.compose.runtime.LaunchedEffect(pendingTimeModeActivation, screen) {
        // A pending request is not an active Time mode. Keep Stars as the persisted
        // safe mode until the wizard verifies every mandatory permission.
        if (pendingTimeModeActivation && screen != "tour" && screen != "pin-setup") {
            rewardMode = RewardMode.STARS
            preferences.edit().putString(RewardMode.PREFERENCE_KEY, "stars").apply()
        }
    }
    androidx.compose.runtime.LaunchedEffect(screen) {
        if (screen == "home") {
            parentSessionAuthenticated = false
            preferences.edit().putBoolean("parent_session_authenticated", false).putBoolean("parent_area_active", false).apply()
        } else if (screen == "parent" && !parentSessionAuthenticated && !firstLaunchNeedsSetup) {
            screen = "parent-pin"
        }
    }
    androidx.compose.runtime.LaunchedEffect(screen, parentSection) {
        if (screen == "parent" && parentSection == "device-setup-wizard") {
            preferences.edit()
                .putBoolean("device_setup_in_progress", true)
                .putBoolean("parent_area_active", true)
                .apply()
        }
    }
    var linkedExerciseFont by remember { mutableStateOf(preferences.getBoolean("linked_exercise_font_v1", true)) }
    val exerciseFontFamily = if (linkedExerciseFont) FontFamily(Font(R.font.playwrite_es)) else FontFamily.Default
    val transferExportCategoriesState = remember { mutableStateOf<Map<ContentStorage.Modality, Set<String>>>(emptyMap()) }
    var transferExportCategories by transferExportCategoriesState
    val expandedExportSequenceCategoriesState = remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedExportSequenceCategories by expandedExportSequenceCategoriesState
    val expandedImportSequenceCategoriesState = remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedImportSequenceCategories by expandedImportSequenceCategoriesState
    val transferExportStatisticsState = remember { mutableStateOf<Set<ContentStorage.Modality>>(emptySet()) }
    var transferExportStatistics by transferExportStatisticsState
    val transferExportUsageStatisticsState = remember { mutableStateOf<Set<ContentStorage.Modality>>(emptySet()) }
    var transferExportUsageStatistics by transferExportUsageStatisticsState
    val transferExportStoriesState = remember { mutableStateOf<Set<String>>(emptySet()) }
    var transferExportStories by transferExportStoriesState
    val transferExportObjectsState = remember { mutableStateOf<Set<String>>(emptySet()) }
    var transferExportObjects by transferExportObjectsState
    val transferPackageState = remember { mutableStateOf<LibraryPackageManager.PackageInfo?>(null) }
    var transferPackage by transferPackageState
    val transferImportCategoriesState = remember { mutableStateOf<Map<ContentStorage.Modality, Set<String>>>(emptyMap()) }
    var transferImportCategories by transferImportCategoriesState
    val transferImportStoriesState = remember { mutableStateOf<Set<String>>(emptySet()) }
    var transferImportStories by transferImportStoriesState
    val transferImportObjectsState = remember { mutableStateOf<Set<String>>(emptySet()) }
    var transferImportObjects by transferImportObjectsState
    val transferObjectConflictDecisionsState = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var transferObjectConflictDecisions by transferObjectConflictDecisionsState
    val transferStoryConflictDecisionsState = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var transferStoryConflictDecisions by transferStoryConflictDecisionsState
    val transferExerciseConflictDecisionsState = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var transferExerciseConflictDecisions by transferExerciseConflictDecisionsState
    val transferConflictIndexState = remember { mutableIntStateOf(0) }
    var transferConflictIndex by transferConflictIndexState
    var transferReplaceDuplicates by remember { mutableStateOf(false) }
    val transferImportStatisticsState = remember { mutableStateOf(false) }
    var transferImportStatistics by transferImportStatisticsState
    val transferImportUsageStatisticsState = remember { mutableStateOf(false) }
    var transferImportUsageStatistics by transferImportUsageStatisticsState
    val transferMessageState = remember { mutableStateOf("") }
    var transferMessage by transferMessageState
    val remoteLibraryPackagesState = remember { mutableStateOf<List<RemoteLibraryPackage>>(emptyList()) }
    var remoteLibraryPackages by remoteLibraryPackagesState
    val remoteLibraryLoadingState = remember { mutableStateOf(false) }
    var remoteLibraryLoading by remoteLibraryLoadingState
    val remoteLibraryMessageState = remember { mutableStateOf("") }
    var remoteLibraryMessage by remoteLibraryMessageState
    val remoteLibraryDownloadingNameState = remember { mutableStateOf<String?>(null) }
    var remoteLibraryDownloadingName by remoteLibraryDownloadingNameState
    val remoteLibraryDownloadedBytesState = remember { mutableStateOf(0L) }
    var remoteLibraryDownloadedBytes by remoteLibraryDownloadedBytesState
    val remoteLibraryTotalBytesState = remember { mutableStateOf(0L) }
    var remoteLibraryTotalBytes by remoteLibraryTotalBytesState
    val remoteLibraryPendingImportState = remember { mutableStateOf<RemoteLibraryPackage?>(null) }
    var remoteLibraryPendingImport by remoteLibraryPendingImportState
    val transferRemoteLibraryNameState = remember { mutableStateOf<String?>(null) }
    var transferRemoteLibraryName by transferRemoteLibraryNameState
    val libraryTaskRunningState = remember { mutableStateOf(false) }
    var libraryTaskRunning by libraryTaskRunningState
    var libraryTaskMessage by remember { mutableStateOf("") }
    var libraryTaskProgress by remember { mutableStateOf(0f) }
    var libraryTaskIndeterminate by remember { mutableStateOf(true) }
    val transferImportPreparationLoadingState = remember { mutableStateOf(false) }
    var transferImportPreparationLoading by transferImportPreparationLoadingState
    val transferImportPreparationErrorState = remember { mutableStateOf(false) }
    var transferImportPreparationError by transferImportPreparationErrorState
    val transferExerciseConflictsState = remember { mutableStateOf<List<LibraryPackageManager.ExerciseConflictInfo>>(emptyList()) }
    var transferExerciseConflicts by transferExerciseConflictsState
    val transferObjectConflictsState = remember { mutableStateOf<List<LibraryPackageManager.ObjectConflictInfo>>(emptyList()) }
    var transferObjectConflicts by transferObjectConflictsState
    val transferStoryNameConflictsState = remember { mutableStateOf<List<Pair<LibraryPackageManager.StoryInfo, StoredStory>>>(emptyList()) }
    var transferStoryNameConflicts by transferStoryNameConflictsState
    val transferImportObjectEntriesState = remember { mutableStateOf<List<Pair<LibraryPackageManager.ObjectDependencyInfo, StoredObject>>>(emptyList()) }
    var transferImportObjectEntries by transferImportObjectEntriesState
    val transferRequiredImportObjectsState = remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var transferRequiredImportObjects by transferRequiredImportObjectsState
    val transferLocalObjectsState = remember { mutableStateOf<List<StoredObject>>(emptyList()) }
    var transferLocalObjects by transferLocalObjectsState
    val transferObjectConflictThumbnailsState = remember { mutableStateOf<Map<String, Pair<Bitmap?, Bitmap?>>>(emptyMap()) }
    var transferObjectConflictThumbnails by transferObjectConflictThumbnailsState
    val remoteLibraryScope = rememberCoroutineScope()
    val updateLibraryTaskProgress: (String, Int, Int) -> Unit = { message, current, total ->
        libraryTaskMessage = message
        libraryTaskIndeterminate = total <= 0
        libraryTaskProgress = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    }
    BackHandler(enabled = libraryTaskRunning) { }
    androidx.compose.runtime.LaunchedEffect(transferPackage, transferImportCategories, transferImportStories, transferImportObjects) {
        val packageInfo = transferPackage
        if (packageInfo == null) {
            transferExerciseConflicts = emptyList()
            transferObjectConflicts = emptyList()
            transferStoryNameConflicts = emptyList()
            transferImportObjectEntries = emptyList()
            transferRequiredImportObjects = emptyMap()
            transferLocalObjects = emptyList()
            transferObjectConflictThumbnails = emptyMap()
            transferImportPreparationError = false
            transferImportPreparationLoading = false
            return@LaunchedEffect
        }
        transferImportPreparationError = false
        transferImportPreparationLoading = true
        try {
            val prepared = withContext(Dispatchers.IO) {
                val localStories = StoryRepository.list(context)
                val localObjects = ObjectRepository.list(context)
                val storyConflicts = packageInfo.stories.filter { it.key in transferImportStories }.mapNotNull { incomingStory ->
                    localStories.firstOrNull {
                        it.collection == incomingStory.collection && NameRules.key(it.metadata.title) == NameRules.key(incomingStory.title)
                    }?.let { existing -> incomingStory to existing }
                }
                val objectConflicts = LibraryPackageManager.objectConflicts(context, packageInfo, transferImportStories, transferImportObjects, localObjects)
                val conflictThumbnails = objectConflicts.associate { conflict ->
                    conflict.incoming.folderName to (
                        decodeLibraryImportThumbnail(File(conflict.incoming.directory, conflict.incoming.metadata.image)) to
                            decodeLibraryImportThumbnail(File(conflict.existing.directory, conflict.existing.metadata.image))
                    )
                }
                LibraryImportPreparation(
                    exerciseConflicts = LibraryPackageManager.exerciseConflicts(context, packageInfo, transferImportCategories),
                    objectConflicts = objectConflicts,
                    storyNameConflicts = storyConflicts,
                    incomingObjects = packageInfo.objects.map { objectInfo ->
                        objectInfo to ObjectRepository.read(File(packageInfo.staging, "objetos/${objectInfo.key}"))
                    },
                    requiredObjects = LibraryPackageManager.requiredImportObjects(packageInfo, transferImportStories),
                    localObjects = localObjects,
                    conflictThumbnails = conflictThumbnails
                )
            }
            transferExerciseConflicts = prepared.exerciseConflicts
            transferObjectConflicts = prepared.objectConflicts
            transferStoryNameConflicts = prepared.storyNameConflicts
            transferImportPreparationError = false
            transferImportObjectEntries = prepared.incomingObjects
            transferRequiredImportObjects = prepared.requiredObjects
            transferLocalObjects = prepared.localObjects
            transferObjectConflictThumbnails = prepared.conflictThumbnails
        } catch (error: Exception) {
            transferMessage = "No se pudieron comprobar las coincidencias: ${error.message ?: "error"}"
            transferExerciseConflicts = emptyList()
            transferObjectConflicts = emptyList()
            transferStoryNameConflicts = emptyList()
            transferImportObjectEntries = emptyList()
            transferRequiredImportObjects = emptyMap()
            transferLocalObjects = emptyList()
            transferObjectConflictThumbnails = emptyMap()
            transferImportPreparationError = true
        } finally {
            transferImportPreparationLoading = false
        }
    }
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
    var deleteDependencySnapshot by remember { mutableStateOf<StoryObjectDependencySnapshot?>(null) }

    LaunchedEffect(parentSection) {
        if (parentSection == "library-delete") {
            deleteDependencySnapshot = null
            val loaded = withContext(Dispatchers.IO) {
                Triple(
                    StoryRepository.list(context),
                    ObjectRepository.list(context),
                    StoryObjectDependencyRepository.scan(context)
                )
            }
            storedStories = loaded.first
            storedStoryObjects = loaded.second
            deleteDependencySnapshot = loaded.third
        }
    }

    val easyRewardMinutesState = remember { mutableIntStateOf(preferences.getInt("reward_easy_minutes", 2).coerceIn(0, 120)) }
    var easyRewardMinutes by easyRewardMinutesState
    val normalRewardMinutesState = remember { mutableIntStateOf(preferences.getInt("reward_normal_minutes", 5).coerceIn(0, 120)) }
    var normalRewardMinutes by normalRewardMinutesState
    val hardRewardMinutesState = remember { mutableIntStateOf(preferences.getInt("reward_hard_minutes", 7).coerceIn(0, 120)) }
    var hardRewardMinutes by hardRewardMinutesState
    val wrongAnswerPenaltyState = remember { mutableStateOf(preferences.getString("wrong_answer_penalty", "half") ?: "half") }
    var wrongAnswerPenalty by wrongAnswerPenaltyState
    val penaltyMenuExpandedState = remember { mutableStateOf(false) }
    var penaltyMenuExpanded by penaltyMenuExpandedState
    var pinStored by remember {
        mutableStateOf(
            preferences.getString("parent_pin", "") ?: ""
        )
    }
    var newPinDraft by remember { mutableStateOf("") }
    var initialPinConfirmDraft by remember { mutableStateOf("") }
    var initialPinError by remember { mutableStateOf("") }
    var tourReturnTarget by remember { mutableStateOf("home") }
    val categoryDraftNameState = remember { mutableStateOf("") }
    var categoryDraftName by categoryDraftNameState
    val categoryMenuExpandedState = remember { mutableStateOf(false) }
    var categoryMenuExpanded by categoryMenuExpandedState
    val categoryNamesState = remember { mutableStateOf((preferences.getString("categories", "") ?: "").split("|").filter { it.isNotBlank() }) }
    var categoryNames by categoryNamesState
    val categoryMessageState = remember { mutableStateOf("") }
    var categoryMessage by categoryMessageState
    val categoryToRemoveState = remember { mutableStateOf("") }
    var categoryToRemove by categoryToRemoveState
    val categories = (categoryNames + pairs.map { it.category }).distinctBy { it.trim().lowercase() }.sorted()
    var selectedExerciseCategory by remember { mutableStateOf(preferences.getString("selected_exercise_category", categories.firstOrNull() ?: "") ?: "") }
    var exerciseCategoryMenuExpanded by remember { mutableStateOf(false) }
    val libraryCategoryFilterState = remember { mutableStateOf("Todas") }
    var libraryCategoryFilter by libraryCategoryFilterState
    val libraryDifficultyFilterState = remember { mutableStateOf("Todas") }
    var libraryDifficultyFilter by libraryDifficultyFilterState
    val libraryFilterCategoryExpandedState = remember { mutableStateOf(false) }
    var libraryFilterCategoryExpanded by libraryFilterCategoryExpandedState
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
    var wordSelectionMode by remember { mutableStateOf("Sorpresa") }
    var sequenceSelectionMode by remember { mutableStateOf("Sorpresa") }

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
            libraryTaskRunning = true
            updateLibraryTaskProgress("Preparando la inspección del paquete…", 0, 0)
            remoteLibraryScope.launch {
                try {
                    transferPackage?.let { previous ->
                        withContext(Dispatchers.IO) { LibraryPackageManager.discard(previous) }
                        transferPackage = null
                    }
                    val inspected = withContext(Dispatchers.IO) {
                        LibraryPackageManager.inspect(context, uri, updateLibraryTaskProgress)
                    }
                    transferPackage = inspected
                    transferImportCategories = inspected.modalities.associate { info ->
                        info.modality to info.categories.flatMap { category ->
                            if (info.modality == ContentStorage.Modality.SECUENCIAS) category.sequenceExercises.map { exercise ->
                                LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, exercise.type, exercise.directory)
                            } else listOf(category.directory)
                        }.toSet()
                    }
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
                } finally {
                    libraryTaskRunning = false
                }
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
                setupAccessibilityEnabled = isPlayTimeAccessibilityEnabled(context)
                screen = "parent"
                if (!rewardMode.isStars && !areMandatoryDeviceSetupPermissionsReady(context)) {
                    pendingTimeModeActivation = true
                    rewardMode = RewardMode.STARS
                    preferences.edit()
                        .putString(RewardMode.PREFERENCE_KEY, "stars")
                        .putBoolean("pending_time_mode_activation", true)
                        .putBoolean("device_setup_in_progress", true)
                        .putBoolean("parent_area_active", true)
                        .apply()
                    parentSection = "device-setup-wizard"
                } else {
                    pendingTimeModeActivation = false
                    preferences.edit().putBoolean("pending_time_mode_activation", false).apply()
                    parentSection = "menu"
                }
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
                if (parentSection == "device-setup-wizard") {
                    // Leaving the wizard must never leave Time mode active without
                    // every mandatory prerequisite, even if the pending flag was lost.
                    val setupWasRequested = pendingTimeModeActivation
                    if (setupWasRequested || !areMandatoryDeviceSetupPermissionsReady(context)) {
                        rewardMode = RewardMode.STARS
                        preferences.edit().putString(RewardMode.PREFERENCE_KEY, "stars").apply()
                    }
                    if (setupWasRequested) showTimeModeNotActivatedDialog = true
                    pendingTimeModeActivation = false
                    permissionRecoveryNotice = false
                    preferences.edit()
                        .putBoolean("pending_time_mode_activation", false)
                        .putBoolean("device_setup_in_progress", false)
                        .apply()
                    parentSection = "menu"
                } else when (parentSection) {
                    "stories-object-create" -> {
                        storyObjectEditorState.reset()
                        parentSection = objectEditorReturnSection
                    }
                    "word-library" -> {
                        if (wordLibraryPage == "edit") {
                            wordLibraryPage = "list"
                            wordLibraryMessage = ""
                        } else {
                            parentSection = parentBackSection(parentSection, transferRemoteLibraryName != null) ?: "words-settings"
                        }
                    }
                    "library-export", "library-import", "library-delete" -> {
                        transferPackage?.let { packageToDiscard ->
                            remoteLibraryScope.launch {
                                withContext(Dispatchers.IO) { LibraryPackageManager.discard(packageToDiscard) }
                            }
                        }
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
            "stories" -> { screen = "home"; selectedStoriesCollection = null; selectedStoryPath = null }
            "story-cover" -> {
                if (showStoryAccessRequirements) {
                    showStoryAccessRequirements = false
                } else {
                    screen = "stories"
                    selectedStoryPath = null
                }
            }
            "story-access" -> { screen = storyAccessReturnScreen; if (screen == "stories") selectedStoryPath = null; showStoryAccessRequirements = false }
            "story-player" -> { screen = "story-cover" }
            "story-inventory" -> { screen = "home"; storyInventoryMessage = "" }
            "sequences" -> { screen = "home" }
            "sequence-difficulty" -> { screen = "sequences" }
            "category-select" -> { screen = "cards-options"; cardsStartMessage = "" }
            "word-options" -> { screen = "home"; wordStartMessage = "" }
            "word-category-select" -> { screen = "word-options"; wordStartMessage = "" }
            "sequence-category-select" -> { screen = "sequence-difficulty"; sequencePlayMessage = "" }
            "word-exercise" -> { screen = wordExerciseReturnScreen; wordExerciseTarget = null; wordExercisePlaced = emptyList(); wordStartMessage = "" }
            "sequences" -> { screen = "home" }
            "sequence-difficulty" -> { screen = "sequences" }
            "sequence-game" -> { screen = sequenceGameReturnScreen; sequenceSelectedOrder = emptyList(); sequencePlayMessage = "" }
            "sequence-result" -> { screen = "sequence-difficulty"; sequenceSelectedOrder = emptyList(); sequencePlayMessage = "" }
            "pin-setup" -> Unit
            "parent-pin", "exit-pin" -> { screen = "home"; permissionRecoveryEntryPending = false; pinEntry = ""; pinError = false }
            "exercise" -> { screen = cardsExerciseReturnScreen; resolved = emptySet(); selected = emptyMap(); selectedPicture = ""; evaluatedPictures = emptyMap(); correct = 0; feedback = "" }
            "result" -> { screen = "home"; resolved = emptySet(); selected = emptyMap(); selectedPicture = ""; evaluatedPictures = emptyMap(); correct = 0; feedback = ""; lastEarnedSeconds = 0 }
            else -> { screen = "home"; parentSection = "menu" }
        }
    }

    var quotaRevision by remember { mutableIntStateOf(0) }

    fun eligibleSequenceExercises(type: String, level: String? = null): List<SequenceExercise> =
        sequenceGameState.eligibleExercises(
            sequenceExercises, type, sequenceMaxCards, disabledSequenceCategories, level
        )

    val quotaAvailability = buildQuotaAvailabilitySnapshot(
        pairs = pairs,
        wordExercises = wordExercises,
        disabledWordExerciseCategories = disabledWordExerciseCategories,
        questionCount = questionCount,
        sequenceGameState = sequenceGameState,
        sequenceExercises = sequenceExercises,
        sequenceMaxCards = sequenceMaxCards,
        disabledSequenceCategories = disabledSequenceCategories
    )
    LaunchedEffect(quotaAvailability) {
        ExerciseQuotaRepository.allQuotaIds().forEach { id ->
            ExerciseQuotaRepository.status(preferences, id, quotaAvailability[id] == true)
        }
        ExerciseQuotaRepository.refreshExhaustedCycle(preferences, quotaAvailability)
        quotaRevision++
    }
    val observedQuotaRevision = quotaRevision

    LaunchedEffect(appInForeground, quotaAvailability) {
        if (appInForeground) while (true) { delay(15_000L); quotaRevision++ }
    }

    fun quotaMessage(status: ExerciseQuotaRepository.QuotaStatus): String =
        when {
            status.limit == null -> "Sin límite de intentos."
            status.available -> "Intentos restantes: ${status.remaining}/${status.limit}."
            else -> status.reason ?: "Dificultad bloqueada."
        }

    fun startWordExercise(category: String? = null) {
        if (screen == "word-options" || screen == "word-category-select") wordExerciseReturnScreen = screen
        val previousCategory = wordCurrentCategory
        val avoidCategory = if (wordSelectionMode == "Mezclar temas" && category == null) previousCategory else null
        val quotaId = ExerciseQuotaRepository.id("words", wordSelectedDifficulty)
        val hasExercises = wordExercises.any { it.active && it.category !in disabledWordExerciseCategories && it.difficulty.equals(wordSelectedDifficulty, true) }
        val quota = ExerciseQuotaRepository.status(preferences, quotaId, hasExercises)
        if (!quota.available) {
            wordStartMessage = quota.reason ?: "Has agotado los intentos de esta dificultad."
            screen = "word-options"
            return
        }
        if (wordGameState.start(wordExercises, disabledWordExerciseCategories, category, avoidCategory)) {
            ExerciseQuotaRepository.consume(preferences, quotaId, hasExercises)
            ExerciseQuotaRepository.refreshExhaustedCycle(preferences, quotaAvailability)
            quotaRevision++
            activeExerciseMode = "words"
            sequencePlayingExercise = null
            sequenceSelectedOrder = emptyList()
            screen = "word-exercise"
        }
    }

    fun startSequenceExercise(type: String, level: String, category: String? = null): Boolean {
        if (screen == "sequence-difficulty" || screen == "sequence-category-select") sequenceGameReturnScreen = screen
        val previousCategory = sequencePlayingExercise?.category
        val avoidCategory = if (sequenceSelectionMode == "Mezclar temas" && category == null) previousCategory else null
        val quotaId = ExerciseQuotaRepository.id("sequences", level, type)
        val hasExercises = eligibleSequenceExercises(type, level).isNotEmpty()
        val quota = ExerciseQuotaRepository.status(preferences, quotaId, hasExercises)
        if (!quota.available) {
            sequencePlayMessage = quota.reason ?: "Has agotado los intentos de esta dificultad."
            screen = "sequence-difficulty"
            return false
        }
        val candidates = eligibleSequenceExercises(type, level).filter { category == null || it.category.equals(category, true) }
        if (candidates.isEmpty()) { sequencePlayMessage = "No hay ejercicios disponibles para esta selección."; return false }
        val started = sequenceGameState.startExercise(candidates, type, level, sequenceMaxCards, disabledSequenceCategories, avoidCategory)
        if (started) {
            ExerciseQuotaRepository.consume(preferences, quotaId, hasExercises)
            ExerciseQuotaRepository.refreshExhaustedCycle(preferences, quotaAvailability)
            quotaRevision++
            activeExerciseMode = "sequences"
            wordExerciseTarget = null
            wordExercisePlaced = emptyList()
            screen = "sequence-game"
        }
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
        if (screen == "cards-options" || screen == "category-select") cardsExerciseReturnScreen = screen
        cardsStartMessage = ""
        if (requestedMode == "Elegir" && manualCategoryLimitReached) {
            cardsStartMessage = "Has alcanzado el límite diario de elecciones manuales."
            return
        }
        val chosenCategory = if (requestedMode == "Elegir") requestedCategory ?: selectedExerciseCategory else ""
        val candidates = pairs.filter { it.active && it.difficulty == difficulty && (requestedMode != "Elegir" || it.category.equals(chosenCategory, true)) }
        if (candidates.size < questionCount) { cardsStartMessage = "No hay suficientes tarjetas activas para esta dificultad."; return }
        if (requestedMode == "Mezcladas" && candidates.map { it.category.lowercase() }.distinct().size < 2) { cardsStartMessage = "Para mezclar temas necesitas tarjetas activas de al menos dos categorías."; return }
        val quotaId = ExerciseQuotaRepository.id("cards", difficulty)
        val hasExercises = pairs.count { it.active && it.difficulty.equals(difficulty, true) } >= questionCount
        val quota = ExerciseQuotaRepository.status(preferences, quotaId, hasExercises)
        if (!quota.available) { cardsStartMessage = quota.reason ?: "Has agotado los intentos de esta dificultad."; screen = "cards-options"; return }
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
        val rankedCandidates = chooseAdaptivePairs(candidates, candidates.size, learningStats, learningTurn)
        val chosenPairs = if (requestedMode == "Mezcladas") {
            val groups = rankedCandidates.groupBy { it.category.lowercase() }.values.map { it.toMutableList() }.toMutableList()
            val mixed = mutableListOf<PairItem>()
            while (mixed.size < questionCount && groups.any { it.isNotEmpty() }) groups.shuffled().forEach { group -> if (group.isNotEmpty() && mixed.size < questionCount) mixed += group.removeAt(0) }
            mixed
        } else rankedCandidates.take(questionCount)
        activeExerciseMode = "cards"
        wordExerciseTarget = null
        wordExercisePlaced = emptyList()
        sequencePlayingExercise = null
        sequenceSelectedOrder = emptyList()
        activeExercisePairs = chosenPairs
        chosenPairs.forEach { pair ->
            learningTurn += 1
            val state = learningStats[pair.word] ?: WordLearning()
            learningStats = learningStats + (pair.word to state.copy(lastShownTurn = learningTurn))
        }
        preferences.edit().putInt("learning_turn_v1", learningTurn).apply()
        saveLearningStats(preferences, learningStats)
        cardsGameState.begin(activeExercisePairs)
        ExerciseQuotaRepository.consume(preferences, quotaId, hasExercises)
        ExerciseQuotaRepository.refreshExhaustedCycle(preferences, quotaAvailability)
        quotaRevision++
        feedback = ""; screen = "exercise"
    }


    AppAudioCoordinator(
        state = appAudioState,
        preferences = preferences,
        screen = screen,
        firstLaunchNeedsSetup = firstLaunchNeedsSetup,
        onFirstLaunchSetupComplete = { firstLaunchNeedsSetup = false },
        appInForeground = appInForeground,
        context = context,
        difficulty = difficulty,
        wordSelectedDifficulty = wordSelectedDifficulty,
        sequenceChosenCategory = sequenceChosenCategory,
        sequenceChosenDifficulty = sequenceChosenDifficulty,
        sequenceType = sequencePlayingExercise?.type
    )

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


    val parentAreaContent: @Composable () -> Unit = {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (parentSection != "menu") {
                item { TextButton(onClick = { if (parentSection == "device-setup-wizard") { if (pendingTimeModeActivation) { rewardMode = RewardMode.STARS; preferences.edit().putString(RewardMode.PREFERENCE_KEY, "stars").apply() }; pendingTimeModeActivation = false; preferences.edit().putBoolean("pending_time_mode_activation", false).putBoolean("device_setup_in_progress", false).apply(); parentSection = "menu" } else { parentSection = when (parentSection) { "game-modes" -> "menu"; "active-games", "rewards", "security", "device-setup", "device-setup-wizard", "about", "library-transfer", "typography" -> "menu"; "quota-system" -> "rewards"; "music", "stories-audio", "achievement-sounds" -> "sound-settings"; "library-export", "library-delete" -> "library-transfer"; "library-import" -> if (transferRemoteLibraryName != null) "basic-library" else "library-transfer"; "statistics-menu" -> "menu"; "learning-stats", "usage-stats" -> "statistics-menu"; "cards-settings", "sequences-settings", "words-settings", "stories-settings" -> "game-modes"; "stories-content-settings", "stories-objects" -> "stories-settings"; "stories-library", "stories-collections" -> "stories-content-settings"; "stories-editor" -> "stories-library"; "stories-structure" -> "stories-editor"; "stories-scene-editor" -> "stories-editor"; "stories-object-create", "stories-object-collection", "stories-object-inventory" -> "stories-objects"; "sequence-type-temporal", "sequence-type-procesos", "sequence-type-causa", "sequence-type-ciclos" -> "sequences-settings"; "sequence-editor-temporal" -> "sequence-library"; "sequence-library", "sequence-categories" -> "sequences-settings"; "sequence-library-temporal", "sequence-categories-temporal" -> "sequence-type-temporal"; "sequence-library-procesos", "sequence-categories-procesos" -> "sequence-type-procesos"; "sequence-library-causa", "sequence-categories-causa" -> "sequence-type-causa"; "sequence-library-ciclos", "sequence-categories-ciclos" -> "sequence-type-ciclos"; "words", "categories" -> "cards-settings"; "word-library", "word-categories" -> "words-settings"; else -> "menu" } } }) { Text(if (parentSection == "quota-system") "← Recompensas y límites" else if (parentSection == "game-modes") "← Control parental" else if (parentSection == "music" || parentSection == "stories-audio" || parentSection == "achievement-sounds") "← Ajustes de sonido" else if (parentSection == "cards-settings" || parentSection == "sequences-settings" || parentSection == "words-settings") "← Modos de juego" else if (parentSection == "stories-settings") "← Modos de juego" else if (parentSection == "stories-content-settings" || parentSection == "stories-objects") "← Cuentos" else if (parentSection == "stories-library" || parentSection == "stories-collections") "← Gestión de cuentos" else if (parentSection == "stories-object-create" || parentSection == "stories-object-collection" || parentSection == "stories-object-inventory") "← Objetos" else if (parentSection.startsWith("sequence-type-")) "← Secuencias" else if (parentSection == "sequence-editor-temporal") "← Biblioteca" else if (parentSection == "sequence-library" || parentSection == "sequence-categories") "← Secuencias" else if (parentSection.startsWith("sequence-library-") || parentSection.startsWith("sequence-categories-")) "← Tipo de secuencia" else if (parentSection == "words" || parentSection == "categories") "← Tarjetas" else if (parentSection == "word-library" || parentSection == "word-categories") "← Palabras" else if (parentSection == "library-export" || parentSection == "library-import" || parentSection == "library-delete") if (parentSection == "library-import" && transferRemoteLibraryName != null) "← Catálogo" else "← Bibliotecas" else if (parentSection == "learning-stats" || parentSection == "usage-stats") "← Estadísticas" else "← Control parental") } }
            }
            if (parentSection == "menu") {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Control parental", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                        Text("Configura el contenido y las opciones de la aplicación.", fontSize = 15.sp, color = Color(0xFF667085))
                    }
                }
            }
            if (parentSection == "menu") {
                parentMenuItems(
                    rewardMode = rewardMode,
                    linkedExerciseFont = linkedExerciseFont,
                    onSelectTime = {
                        setupAccessibilityEnabled = isPlayTimeAccessibilityEnabled(context)
                        if (areMandatoryDeviceSetupPermissionsReady(context)) {
                            pendingTimeModeActivation = false
                            permissionRecoveryNotice = false
                            preferences.edit()
                                .putBoolean("pending_time_mode_activation", false)
                                .putString(RewardMode.PREFERENCE_KEY, "time")
                                .apply()
                            rewardMode = RewardMode.TIME
                            parentSection = "menu"
                        } else {
                            pendingTimeModeActivation = true
                            preferences.edit()
                                .putBoolean("pending_time_mode_activation", true)
                                .putBoolean("device_setup_in_progress", true)
                                .putBoolean("parent_area_active", true)
                                .apply()
                            parentSection = "device-setup-wizard"
                        }
                    },
                    onSelectStars = {
                        pendingTimeModeActivation = false
                        preferences.edit().putBoolean("pending_time_mode_activation", false).apply()
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
                    },
                    onOpenTour = {
                        tourReturnTarget = "parent-menu"
                        screen = "tour"
                    },
                    onNavigate = { parentSection = it },
                    onOpenLibraryTransfer = {
                        transferExportCategories = ContentStorage.Modality.entries.associateWith { modality ->
                            // Al abrir la exportación se selecciona cada categoría completa.
                            // Los selectores de tipo y ejercicio concretan después esa selección
                            // sin dejar las secuencias inicialmente desmarcadas.
                            LibraryPackageManager.categories(context, modality).map { category ->
                                category.directory
                            }.toSet()
                        }
                        expandedExportSequenceCategories = emptySet()
                        transferExportStatistics = emptySet()
                        transferMessage = ""
                        parentSection = "library-transfer"
                    },
                    onOpenDeviceSetup = {
                        pendingTimeModeActivation = false
                        preferences.edit()
                            .putBoolean("pending_time_mode_activation", false)
                            .putBoolean("device_setup_in_progress", true)
                            .putBoolean("parent_area_active", true)
                            .apply()
                        parentSection = "device-setup-wizard"
                    }
                )
            } else {
                when (parentSection) {
                    "sound-settings" -> {
                        item { SoundSettingsMenu(onNavigate = { parentSection = it }) }
                    }
                    "achievement-sounds" -> {
                        item { AchievementSoundSettingsScreen() }
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
                        remoteLibraryItems(
                            context = context,
                            remoteLibraryScope = remoteLibraryScope,
                            remoteLibraryPackagesState = remoteLibraryPackagesState,
                            remoteLibraryLoadingState = remoteLibraryLoadingState,
                            remoteLibraryMessageState = remoteLibraryMessageState,
                            remoteLibraryDownloadingNameState = remoteLibraryDownloadingNameState,
                            remoteLibraryDownloadedBytesState = remoteLibraryDownloadedBytesState,
                            remoteLibraryTotalBytesState = remoteLibraryTotalBytesState,
                            remoteLibraryPendingImportState = remoteLibraryPendingImportState
                        )
                    }
                    "library-transfer" -> {
                        libraryTransferItems(
                            transferMessage = transferMessage,
                            onExport = {
                                transferExportCategories = ContentStorage.Modality.entries.associateWith { modality ->
                                    LibraryPackageManager.categories(context, modality).map { it.directory }.toSet()
                                }
                                transferExportStatistics = emptySet()
                                transferExportStories = StoryRepository.list(context).map { "${it.collection}/${it.folderName}" }.toSet()
                                transferMessage = ""
                                parentSection = "library-export"
                            },
                            onImport = {
                                transferImportUsageStatistics = false
                                transferMessage = ""
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                unifiedImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            },
                            onDelete = {
                                deleteSelectedItems = emptySet()
                                deleteExpandedCategories = emptySet()
                                deleteConfirmArmed = false
                                deleteDependencySnapshot = null
                                transferMessage = ""
                                parentSection = "library-delete"
                            }
                        )
                    }
                    "library-export" -> {
                        libraryExportContent(
                            context = context,
                            transferExportCategoriesState = transferExportCategoriesState,
                            transferExportStatisticsState = transferExportStatisticsState,
                            transferExportUsageStatisticsState = transferExportUsageStatisticsState,
                            transferExportStoriesState = transferExportStoriesState,
                            transferExportObjectsState = transferExportObjectsState,
                            expandedExportSequenceCategoriesState = expandedExportSequenceCategoriesState,
                            transferMessage = transferMessage,
                            onTransferMessageChange = { transferMessage = it },
                            onExportSelection = {
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                unifiedExportLauncher.launch("biblioteca.zip")
                            }
                        )
                    }
                    "library-delete" -> {
                        libraryDeleteItems(
                            context = context,
                            preferences = preferences,
                            stories = storedStories,
                            objects = storedStoryObjects,
                            dependencySnapshot = deleteDependencySnapshot,
                            selectedItems = deleteSelectedItems,
                            expandedCategories = deleteExpandedCategories,
                            confirmArmed = deleteConfirmArmed,
                            onSelectionChanged = { deleteSelectedItems = it; deleteConfirmArmed = false },
                            onExpandedCategoriesChanged = { deleteExpandedCategories = it },
                            onConfirmArmedChanged = { deleteConfirmArmed = it },
                            onDeleteConfirmed = {
                            launchLibraryDeletion(
                                scope = remoteLibraryScope,
                                context = context,
                                preferences = preferences,
                                selectedItems = deleteSelectedItems,
                                onConfirmArmedChanged = { deleteConfirmArmed = it },
                                onMessageChanged = { transferMessage = it },
                                onParentSectionChanged = { parentSection = it },
                                onCategoryNamesChanged = { categoryNames = it },
                                onSaveWordCategories = { wordLibraryState.saveCategories(it) },
                                onSequenceCategoriesChanged = { sequenceCategoryNames = it },
                                onPairsChanged = { pairs = it },
                                onReloadWordExercises = { wordExercises = wordLibraryState.reloadExercises() },
                                onReloadSequenceExercises = { sequenceExercises = sequenceLibraryState.reloadExercises() },
                                onStoriesChanged = { storedStories = it },
                                onObjectsChanged = { storedStoryObjects = it },
                                onResetSelection = {
                                    deleteDependencySnapshot = null
                                    deleteSelectedItems = emptySet()
                                    deleteExpandedCategories = emptySet()
                                    deleteConfirmArmed = false
                                }
                            )
                        }
                        )
                    }
                    "learning-stats" -> {
                        item {
                            LearningStatisticsScreen(
                                context = context,
                                expandedModalities = expandedLearningModalities,
                                onToggleModality = { modality ->
                                    expandedLearningModalities = if (modality in expandedLearningModalities) {
                                        expandedLearningModalities - modality
                                    } else {
                                        expandedLearningModalities + modality
                                    }
                                }
                            )
                        }
                    }
                    "library-import" -> {
                        libraryImportContent(
                            context = context,
                            transferPackage = transferPackage,
                            expandedImportSequenceCategoriesState = expandedImportSequenceCategoriesState,
                            transferImportCategoriesState = transferImportCategoriesState,
                            transferImportStoriesState = transferImportStoriesState,
                            transferImportObjectsState = transferImportObjectsState,
                            transferObjectConflictDecisionsState = transferObjectConflictDecisionsState,
                            transferStoryConflictDecisionsState = transferStoryConflictDecisionsState,
                            transferExerciseConflictDecisionsState = transferExerciseConflictDecisionsState,
                            transferConflictIndexState = transferConflictIndexState,
                            transferImportStatisticsState = transferImportStatisticsState,
                            transferImportUsageStatisticsState = transferImportUsageStatisticsState,
                            transferImportPreparationLoadingState = transferImportPreparationLoadingState,
                            transferImportPreparationErrorState = transferImportPreparationErrorState,
                            transferExerciseConflictsState = transferExerciseConflictsState,
                            transferObjectConflictsState = transferObjectConflictsState,
                            transferStoryNameConflictsState = transferStoryNameConflictsState,
                            transferImportObjectEntriesState = transferImportObjectEntriesState,
                            transferRequiredImportObjectsState = transferRequiredImportObjectsState,
                            transferLocalObjectsState = transferLocalObjectsState,
                            transferObjectConflictThumbnailsState = transferObjectConflictThumbnailsState,
                            transferMessage = transferMessage,
                            onImportSelection = {
                                launchLibraryImportSelection(
                                    context = context,
                                    scope = remoteLibraryScope,
                                    preferences = preferences,
                                    transferPackageState = transferPackageState,
                                    transferImportCategoriesState = transferImportCategoriesState,
                                    transferImportStatisticsState = transferImportStatisticsState,
                                    transferImportUsageStatisticsState = transferImportUsageStatisticsState,
                                    transferImportStoriesState = transferImportStoriesState,
                                    transferObjectConflictDecisionsState = transferObjectConflictDecisionsState,
                                    transferStoryConflictDecisionsState = transferStoryConflictDecisionsState,
                                    transferImportObjectsState = transferImportObjectsState,
                                    transferExerciseConflictDecisionsState = transferExerciseConflictDecisionsState,
                                    transferRemoteLibraryNameState = transferRemoteLibraryNameState,
                                    remoteLibraryPackagesState = remoteLibraryPackagesState,
                                    storedStoriesState = storyEditorState.storedStories,
                                    storedStoryObjectsState = storedStoryObjectsState,
                                    storyObjectCollectionsState = storyObjectCollectionsState.collections,
                                    storyCollectionsState = storyCollectionsState.collections,
                                    pairsState = pairsState,
                                    categoryNamesState = categoryNamesState,
                                    transferMessageState = transferMessageState,
                                    parentSectionState = parentSectionState,
                                    libraryTaskRunningState = libraryTaskRunningState,
                                    wordLibraryState = wordLibraryState,
                                    sequenceLibraryState = sequenceLibraryState,
                                    updateProgress = updateLibraryTaskProgress
                                )
                            },
                            onCancelImport = {
                                val info = transferPackage
                                if (info != null) {
                                transferPackage = null
                                remoteLibraryScope.launch {
                                    withContext(Dispatchers.IO) { LibraryPackageManager.discard(info) }
                                }
                                parentSection = if (transferRemoteLibraryName != null) "basic-library" else "library-transfer"
                                transferRemoteLibraryName = null
                            
                                }
                            }
                        )
                    }
                    "active-games" -> {
                        activeGamesItems(
                            gameActiveCards = gameActiveCards,
                            gameActiveSequences = gameActiveSequences,
                            gameActiveWords = gameActiveWords,
                            gameActiveStories = gameActiveStories,
                            onCardsChange = { gameActiveCards = it; preferences.edit().putBoolean("game_active_cards", it).apply() },
                            onSequencesChange = { gameActiveSequences = it; preferences.edit().putBoolean("game_active_sequences", it).apply() },
                            onWordsChange = { gameActiveWords = it; preferences.edit().putBoolean("game_active_words", it).apply() },
                            onStoriesChange = { gameActiveStories = it; preferences.edit().putBoolean("game_active_stories", it).apply() }
                        )
                    }
"game-modes" -> gameModesItems(
                        onOpenCards = { parentSection = "cards-settings" },
                        onOpenSequences = { parentSection = "sequences-settings" },
                        onOpenWords = { parentSection = "words-settings" },
                        onOpenStories = { parentSection = "stories-settings" }
                    )
                    "stories-settings" -> {
                        StorySettingsScreen(
                            showPendingObjectImages = showPendingStoryObjectImages,
                            onShowPendingObjectImagesChange = { enabled ->
                                showPendingStoryObjectImages = enabled
                                preferences.edit().putBoolean("stories_show_pending_object_images", enabled).apply()
                            },
                            onOpenStoryManagement = { parentSection = "stories-content-settings" },
                            onOpenObjectManagement = { parentSection = "stories-objects" }
                        )
                    }
                    "stories-content-settings" -> {
                        StoryContentSettingsScreen(
                            onOpenLibrary = { parentSection = "stories-library" },
                            onOpenCollections = { parentSection = "stories-collections" }
                        )
                    }
                    "stories-library", "stories-editor", "stories-structure", "stories-scene-editor", "stories-collections" -> {
                        storyManagementItems(
                            section = parentSection,
                            context = context,
                            preferences = preferences,
                            parentSectionState = parentSectionState,
                            storedStoryObjectsState = storedStoryObjectsState,
                            storyCollectionsState = storyCollectionsState,
                            storyEditorState = storyEditorState,
                            storyEditorAudioActions = storyEditorAudioActions,
                            storyAudioSettingsState = storyAudioSettingsState,
                            storyEditorCoverRevisionState = storyEditorCoverRevisionState,
                            storyEditorIllustrationRevisionState = storyEditorIllustrationRevisionState,
                            storyCollectionCoverRevisionState = storyCollectionCoverRevisionState,
                            editingStoryCollectionState = editingStoryCollectionState,
                            selectedStoriesCollectionState = selectedStoriesCollectionState,
                            onPickStoryCover = onPickStoryCover,
                            onPickStoryIllustration = onPickStoryIllustration,
                            onChooseStoryCollectionCover = onChooseStoryCollectionCover,
                            persistStoryDraft = { persistStoryDraft() }
                        )
                    }
                    "stories-objects", "stories-object-library", "stories-object-store",
                    "stories-object-create", "stories-object-collection", "stories-object-inventory" -> {
                        storyObjectManagementItems(
                            section = parentSection,
                            context = context,
                            rewardMode = rewardMode,
                            storedStoryObjectsState = storedStoryObjectsState,
                            storyObjectEditorState = storyObjectEditorState,
                            storyObjectInventoryState = storyObjectInventoryState,
                            storyObjectCollectionsState = storyObjectCollectionsState,
                            objectLibraryCollectionFilterState = objectLibraryCollectionFilterState,
                            objectStoreMessageState = objectStoreMessageState,
                            objectEditorReturnSectionState = objectEditorReturnSectionState,
                            storyObjectCollectionMenuExpandedState = storyObjectCollectionMenuExpandedState,
                            parentSectionState = parentSectionState,
                            onChooseGallery = {
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                storyObjectImagePicker.launch(arrayOf("image/*"))
                            },
                            onTakePhoto = {
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                pendingCameraTarget = "story-object"
                                cameraLauncher.launch(null)
                            }
                        )
                    }
                    "sequences-settings", "sequence-type-temporal", "sequence-type-procesos", "sequence-type-causa", "sequence-type-ciclos",
                    "sequence-library", "sequence-library-temporal", "sequence-library-procesos", "sequence-library-causa", "sequence-library-ciclos",
                    "sequence-categories", "sequence-categories-temporal", "sequence-categories-procesos", "sequence-categories-causa", "sequence-categories-ciclos" -> {
                        sequenceSettingsItems(
                            parentSectionState = parentSectionState,
                            preferences = preferences,
                            sequenceLibraryState = sequenceLibraryState,
                            sequenceContentFormatState = sequenceContentFormatState,
                            sequenceMaxCardsState = sequenceMaxCardsState,
                            onCreateExercise = onCreateSequenceExercise,
                            onEditExercise = onEditSequenceExercise
                        )
                    }
                    "sequence-editor-temporal" -> {
                        SequenceEditorTemporalScreen(
                            editing = sequenceEditingIndex >= 0,
                            title = sequenceTitleDraft,
                            onTitleChange = { sequenceTitleDraft = if (sequenceEditingIndex < 0) NameRules.filterInput(it) else it },
                            sequenceType = sequenceTypeDraft,
                            onSequenceTypeChange = { type ->
                                sequenceTypeDraft = type
                                if (sequenceCardsDraft.size < 2) {
                                    sequenceCardsDraft = listOf(sequenceCardsDraft.firstOrNull().orEmpty(), "", "")
                                    sequenceCardPicturesDraft = listOf(sequenceCardPicturesDraft.firstOrNull().orEmpty(), "", "")
                                }
                            },
                            difficulty = sequenceDifficultyDraft,
                            onDifficultyChange = { sequenceDifficultyDraft = it },
                            category = sequenceCategoryDraft,
                            categories = sequenceCategoryNames + sequenceExercises.map { it.category }.filter { it.isNotBlank() },
                            categoryMenuExpanded = sequenceCategoryMenuExpanded,
                            onCategoryMenuExpandedChange = { sequenceCategoryMenuExpanded = it },
                            onCategorySelected = { sequenceCategoryDraft = it },
                            cards = sequenceCardsDraft,
                            onCardChange = { index, value ->
                                sequenceCardsDraft = sequenceCardsDraft.toMutableList().also { it[index] = value }
                            },
                            pictures = sequenceCardPicturesDraft,
                            onRemoveCard = { index ->
                                if (sequenceCardsDraft.size > 2) {
                                    sequenceCardsDraft = sequenceCardsDraft.toMutableList().also { it.removeAt(index) }
                                    sequenceCardPicturesDraft = sequenceCardPicturesDraft.toMutableList().also { if (index < it.size) it.removeAt(index) }
                                }
                            },
                            onPickGallery = { index ->
                                pendingSequencePictureIndex = index
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                sequenceImagePicker.launch(arrayOf("image/*"))
                            },
                            onTakePhoto = { index ->
                                pendingCameraTarget = "sequence"
                                pendingCameraSequenceIndex = index
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                cameraLauncher.launch(null)
                            },
                            onRemovePicture = { index ->
                                sequenceCardPicturesDraft = sequenceCardPicturesDraft.toMutableList().also {
                                    while (it.size <= index) it.add("")
                                    it[index] = ""
                                }
                            },
                            onAddCard = {
                                if (sequenceCardsDraft.size < 6) {
                                    sequenceCardsDraft = sequenceCardsDraft + ""
                                    sequenceCardPicturesDraft = sequenceCardPicturesDraft + ""
                                }
                            },
                            alternativeDraft = sequenceAlternativeDraft,
                            onAlternativeDraftChange = { sequenceAlternativeDraft = it },
                            message = sequenceEditorMessage,
                            onSave = {
                                saveTemporalSequenceExercise(
                                    title = sequenceTitleDraft,
                                    editingIndex = sequenceEditingIndex,
                                    exercises = sequenceExercises,
                                    category = sequenceCategoryDraft,
                                    cards = sequenceCardsDraft,
                                    alternativeDraft = sequenceAlternativeDraft,
                                    pictures = sequenceCardPicturesDraft,
                                    sequenceType = sequenceTypeDraft,
                                    difficulty = sequenceDifficultyDraft,
                                    libraryState = sequenceLibraryState,
                                    onValidationError = { sequenceEditorMessage = it },
                                    onSaved = {
                                        sequenceExercises = it
                                        parentSection = "sequence-library"
                                    }
                                )
                            },
                            onCancel = { parentSection = "sequence-library-temporal" }
                        )
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
                    "word-library", "word-categories" -> {
                        wordLibraryItems(
                            state = wordLibraryState,
                            section = parentSection,
                            onPickImage = {
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                wordImagePicker.launch(arrayOf("image/*"))
                            },
                            onTakePhoto = {
                                pendingCameraTarget = "word"
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                cameraLauncher.launch(null)
                            }
                        )
                    }
                    "cards-settings" -> {
                        CardsSettingsScreen(
                            questionCount = questionCount,
                            dailyManualLimit = dailyManualLimit,
                            onOpenLibrary = { parentSection = "words" },
                            onOpenCategories = { parentSection = "categories" },
                            onQuestionCountChange = { delta ->
                                questionCount = (questionCount + delta).coerceIn(1, 10)
                                preferences.edit().putInt("question_count", questionCount).apply()
                            },
                            onDailyManualLimitChange = { delta ->
                                dailyManualLimit = (dailyManualLimit + delta).coerceIn(0, 10)
                                preferences.edit().putInt("daily_manual_limit", dailyManualLimit).apply()
                            }
                        )
                    }
                    "words", "categories" -> {
                        cardLibraryItems(
                            state = CardLibraryState(
                                context = context,
                                preferences = preferences,
                                pairs = pairsState,
                                wordDraft = wordDraftState,
                                pictureDraft = pictureDraftState,
                                categoryDraft = categoryDraftState,
                                difficultyDraft = difficultyDraftState,
                                editingWord = editingWordState,
                                libraryMessage = libraryMessageState,
                                libraryPage = libraryPageState,
                                categoryDraftName = categoryDraftNameState,
                                categoryNames = categoryNamesState,
                                categoryMessage = categoryMessageState,
                                categoryToRemove = categoryToRemoveState,
                                categoryMenuExpanded = categoryMenuExpandedState,
                                libraryCategoryFilter = libraryCategoryFilterState,
                                libraryDifficultyFilter = libraryDifficultyFilterState,
                                libraryFilterCategoryExpanded = libraryFilterCategoryExpandedState
                            ),
                            section = parentSection,
                            onPickImage = {
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                imagePicker.launch(arrayOf("image/*"))
                            },
                            onTakePhoto = {
                                pendingCameraTarget = "pair"
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                cameraLauncher.launch(null)
                            }
                        )
                    }
                    "rewards" -> item {
                        RewardsSettingsContent(
                            context = context,
                            preferences = preferences,
                            rewardMode = rewardMode,
                            balanceSeconds = balanceSecondsState,
                            minimumPlayMinutes = minimumPlayMinutesState,
                            easyRewardMinutes = easyRewardMinutesState,
                            normalRewardMinutes = normalRewardMinutesState,
                            hardRewardMinutes = hardRewardMinutesState,
                            wrongAnswerPenalty = wrongAnswerPenaltyState,
                            penaltyMenuExpanded = penaltyMenuExpandedState,
                            onOpenQuotaSystem = { parentSection = "quota-system" }
                        )
                    }
                    "quota-system" -> item {
                        QuotaSystemSettingsScreen(preferences = preferences)
                    }
                    "stories-audio" -> item {
                        StoryAudioSettingsScreen(state = storyAudioSettingsState)
                    }
                    "music" -> item {
                        MusicSettingsScreen(
                            musicUri = appAudioState.musicUri,
                            onMusicUriChange = { appAudioState.musicUri = it },
                            soundEnabled = appAudioState.soundEnabled,
                            onSoundEnabledChange = { appAudioState.soundEnabled = it },
                            message = appAudioState.musicMessage,
                            onMessageChange = { appAudioState.musicMessage = it }
                        )
                    }
                    "device-setup-wizard" -> item {
                        DeviceSetupWizardScreen(
                            accessibilityEnabled = setupAccessibilityEnabled || isPlayTimeAccessibilityEnabled(context),
                            showPermissionRecoveryNotice = permissionRecoveryNotice,
                            showTimeActivationButton = pendingTimeModeActivation,
                            showExitWithoutActivation = pendingTimeModeActivation,
                            onComplete = {
                                val ready = areMandatoryDeviceSetupPermissionsReady(context)
                                if (ready) {
                                    rewardMode = RewardMode.TIME
                                    preferences.edit().putString(RewardMode.PREFERENCE_KEY, "time").apply()
                                    pendingTimeModeActivation = false
                                    permissionRecoveryNotice = false
                                    preferences.edit()
                                        .putBoolean("pending_time_mode_activation", false)
                                        .putBoolean("device_setup_in_progress", false)
                                        .apply()
                                    parentSection = "menu"
                                } else {
                                    setupAccessibilityEnabled = isPlayTimeAccessibilityEnabled(context)
                                }
                            },
                            onCancel = { permissionsComplete ->
                                // Exiting from the final confirmation screen is an intentional
                                // choice not to activate Time; permissions are already ready.
                                val setupWasRequested = pendingTimeModeActivation
                                if (!permissionsComplete &&
                                    (setupWasRequested || !areMandatoryDeviceSetupPermissionsReady(context))
                                ) {
                                    rewardMode = RewardMode.STARS
                                    preferences.edit().putString(RewardMode.PREFERENCE_KEY, "stars").apply()
                                }
                                if (setupWasRequested && !permissionsComplete) {
                                    showTimeModeNotActivatedDialog = true
                                }
                                pendingTimeModeActivation = false
                                permissionRecoveryNotice = false
                                preferences.edit()
                                    .putBoolean("pending_time_mode_activation", false)
                                    .putBoolean("device_setup_in_progress", false)
                                    .apply()
                                parentSection = "menu"
                            }
                        )
                    }
                    "device-setup" -> item {
                        DeviceSetupContent(
                            context = context,
                            preferences = preferences,
                            isStarsMode = rewardMode.isStars,
                            setupAccessibilityEnabled = setupAccessibilityEnabledState,
                            restrictedSettingsDone = restrictedSettingsDoneState,
                            batterySettingsDone = batterySettingsDoneState,
                            xiaomiWindowPermissionDone = xiaomiWindowPermissionDoneState,
                            readAccessibilityEnabled = { isAccessibilityServiceEnabled(context) }
                        )
                    }
"about" -> item {
                        AboutScreen(
                            versionName = BuildConfig.VERSION_NAME,
                            versionCode = BuildConfig.VERSION_CODE,
                            buildDate = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
                            onOpenUri = { uriHandler.openUri(it) }
                        )
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
            if (parentSection != "device-setup-wizard") item { OutlinedButton(onClick = {
                tourReturnTarget = "home"
                screen = if (!preferences.getBoolean("welcome_tour_seen", false)) "tour" else "home"
                parentSection = "menu"
            }, modifier = Modifier.fillMaxWidth()) { Text("Salir de configuración") } }
        }
    }

    val parentalTypographyScreen = screen == "pin-setup" || screen == "parent-pin" || screen == "exit-pin" || screen == "parent"
    val inheritedExerciseTextStyle = LocalTextStyle.current.copy(
        fontFamily = if (parentalTypographyScreen) LocalTextStyle.current.fontFamily else exerciseFontFamily
    )
    CompositionLocalProvider(LocalTextStyle provides inheritedExerciseTextStyle) {
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
                if (selected.isStars) {
                    pendingTimeModeActivation = false
                    preferences.edit()
                        .putBoolean("pending_time_mode_activation", false)
                        .putString(RewardMode.PREFERENCE_KEY, "stars")
                        .apply()
                } else if (areMandatoryDeviceSetupPermissionsReady(context)) {
                    pendingTimeModeActivation = false
                    preferences.edit()
                        .putBoolean("pending_time_mode_activation", false)
                        .putString(RewardMode.PREFERENCE_KEY, "time")
                        .apply()
                } else {
                    pendingTimeModeActivation = true
                    preferences.edit()
                        .putBoolean("pending_time_mode_activation", true)
                        .putString(RewardMode.PREFERENCE_KEY, "stars")
                        .apply()
                }
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
                    parentSessionAuthenticated = true
                    preferences.edit().putBoolean("parent_session_authenticated", true).putBoolean("parent_area_active", true).apply()
                    screen = "parent"
                    if (permissionRecoveryEntryPending) {
                        pendingTimeModeActivation = false
                        permissionRecoveryNotice = true
                        preferences.edit().putBoolean("pending_time_mode_activation", false).putBoolean("device_setup_in_progress", true).apply()
                        parentSection = "device-setup-wizard"
                        permissionRecoveryEntryPending = false
                    } else {
                        parentSection = "menu"
                    }
                    pinEntry = ""
                    pinError = false
                } else {
                    pinError = true
                }
            },
            onCancel = { screen = "home"; permissionRecoveryEntryPending = false; pinEntry = ""; pinError = false },
            cancelLabel = "Volver al inicio"
        )
    } else if (screen == "parent") {
        parentAreaContent()
    } else if (screen == "exercise") {
        val cardsQuotaStatus = ExerciseQuotaRepository.status(
            preferences,
            ExerciseQuotaRepository.id("cards", difficulty),
            pairs.count { it.active && it.difficulty.equals(difficulty, true) } >= questionCount
        )
        CardsExerciseScreen(
            activeExercisePairs = activeExercisePairs,
            exercisePairs = exercisePairs,
            rewardMode = rewardMode,
            balanceSeconds = balanceSeconds,
            roundsUsed = cardsQuotaStatus.used,
            roundsLimit = cardsQuotaStatus.limit,
            difficulty = difficulty,
            selected = selected,
            selectedPicture = selectedPicture,
            resolved = resolved,
            evaluatedPictures = evaluatedPictures,
            feedback = feedback,
            pairCount = pairCount,
            exerciseFontFamily = exerciseFontFamily,
            onSelectedChange = { selected = it },
            onSelectedPictureChange = { selectedPicture = it },
            onFeedbackChange = { feedback = it },
            onResolveCardsPair = { word, picture -> resolveCardsPair(word, picture) },
            onExit = { screen = "home" }
        )
    } else if (screen == "word-options") {
        WordOptionsRoute(
            wordExercises = wordExercises,
            disabledCategories = disabledWordExerciseCategories,
            difficulty = wordSelectedDifficulty,
            preferences = preferences,
            easyReward = easyRewardMinutes,
            normalReward = normalRewardMinutes,
            hardReward = hardRewardMinutes,
            isStarsMode = rewardMode.isStars,
            startMessage = wordStartMessage,
            onBack = { screen = "home"; wordStartMessage = "" },
            onDifficultyChange = { wordSelectedDifficulty = it; wordStartMessage = "" },
            onSurprise = { wordSelectionMode = "Sorpresa"; startWordExercise(null) },
            onChooseTheme = { categories -> wordSelectionMode = "Elegir tema"; wordCategoryCandidates = categories; wordStartMessage = ""; screen = "word-category-select" },
            onMixThemes = { wordSelectionMode = "Mezclar temas"; startWordExercise(null) }
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
        val wordQuotaStatus = target?.let {
            ExerciseQuotaRepository.status(
                preferences,
                ExerciseQuotaRepository.id("words", it.difficulty),
                wordExercises.any { exercise -> exercise.active && exercise.category !in disabledWordExerciseCategories && exercise.difficulty.equals(it.difficulty, true) }
            )
        }
        WordExerciseScreen(
            hasTarget = target != null,
            targetWord = target?.word.orEmpty(),
            targetPicture = target?.picture.orEmpty(),
            difficulty = target?.difficulty.orEmpty(),
            message = wordExerciseMessage,
            placedLetters = wordExercisePlaced,
            letterOrder = wordExerciseLetterOrder,
            balanceText = if (rewardMode.isStars) "${formatRewardBalance(balanceSeconds, rewardMode)} ⭐" else "${balanceSeconds / 60} min ${balanceSeconds % 60} s",
            roundsUsed = wordQuotaStatus?.used ?: 0,
            roundsLimit = wordQuotaStatus?.limit,
            exerciseFontFamily = exerciseFontFamily,
            onPlayInstruction = {
                ExerciseVoiceManager.items.firstOrNull { it.key == "instruction_words" }?.let {
                    ExerciseVoiceManager.playLatest(context, it, preview = true)
                }
            },
            onExit = { wordExerciseTarget = null; screen = "home" },
            onBackHome = { screen = "home" },
            onChooseLetter = { letterIndex ->
                if (target != null) {
                    val targetWord = target.word
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
                }
            }
        )
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
        ExerciseResultRoute(
            resultCorrectCount = resultCorrectCount,
            resultTotalCount = resultTotalCount,
            isPerfectCardsResult = correct == pairCount,
            rewardMode = rewardMode,
            lastEarnedSeconds = lastEarnedSeconds,
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
                        val category = if (sequenceSelectionMode == "Elegir tema") sequencePlayingExercise?.category else null
                        if (!startSequenceExercise(sequenceChosenCategory, sequenceChosenDifficulty, category)) screen = "sequence-difficulty"
                    }
                    else -> {
                        when (activeExerciseMode) {
                            "words" -> startWordExercise(if (wordSelectionMode == "Elegir tema") wordCurrentCategory else null)
                            "sequences" -> {
                                val category = if (sequenceSelectionMode == "Elegir tema") sequencePlayingExercise?.category else null
                                if (!startSequenceExercise(sequenceChosenCategory, sequenceChosenDifficulty, category)) screen = "sequence-difficulty"
                            }
                            else -> startCardsGame(mode, if (mode == "Elegir") selectedExerciseCategory else null)
                        }
                    }
                }
            }
        )
    } else if (screen == "sequences") {
        SequenceSelectionRoute(
            chosenCategory = sequenceChosenCategory,
            message = sequencePlayMessage,
            maxCards = sequenceMaxCards,
            preferences = preferences,
            sequenceGameState = sequenceGameState,
            sequenceExercises = sequenceExercises,
            disabledCategories = disabledSequenceCategories,
            onBack = { screen = "home" },
            onChooseType = { type, available ->
                sequenceChosenCategory = type
                sequenceChosenDifficulty = "Fácil"
                sequencePlayMessage = if (available) "" else "No hay ejercicios activos de $type que respeten el máximo de $sequenceMaxCards tarjetas configurado."
                if (available) screen = "sequence-difficulty"
            }
        )
    } else if (screen == "sequence-difficulty") {
        SequenceDifficultyRoute(
            chosenCategory = sequenceChosenCategory,
            chosenDifficulty = sequenceChosenDifficulty,
            message = sequencePlayMessage,
            preferences = preferences,
            sequenceGameState = sequenceGameState,
            sequenceExercises = sequenceExercises,
            maxCards = sequenceMaxCards,
            disabledCategories = disabledSequenceCategories,
            easyReward = easyRewardMinutes,
            normalReward = normalRewardMinutes,
            hardReward = hardRewardMinutes,
            isStarsMode = rewardMode.isStars,
            onBack = { screen = "sequences"; sequencePlayMessage = "" },
            onDifficultyChange = { sequenceChosenDifficulty = it; sequencePlayMessage = "" },
            onSurprise = { sequenceSelectionMode = "Sorpresa"; startSequenceExercise(sequenceChosenCategory, sequenceChosenDifficulty) },
            onChooseTheme = { sequenceSelectionMode = "Elegir tema"; screen = "sequence-category-select" },
            onMixThemes = { sequenceSelectionMode = "Mezclar temas"; startSequenceExercise(sequenceChosenCategory, sequenceChosenDifficulty) }
        )
    } else if (screen == "sequence-category-select") {
        SequenceCategorySelectionRoute(
            difficulty = sequenceChosenDifficulty,
            chosenType = sequenceChosenCategory,
            message = sequencePlayMessage,
            sequenceGameState = sequenceGameState,
            sequenceExercises = sequenceExercises,
            maxCards = sequenceMaxCards,
            disabledCategories = disabledSequenceCategories,
            onBack = { screen = "sequence-difficulty"; sequencePlayMessage = "" },
            onSelectCategory = { startSequenceExercise(sequenceChosenCategory, sequenceChosenDifficulty, it) }
        )
    } else if (screen == "sequence-game") {
        SequenceGameRoute(
            balance = if (rewardMode.isStars) "${formatRewardBalance(balanceSeconds, rewardMode)} ⭐" else "${balanceSeconds / 60} min ${balanceSeconds % 60} s",
            roundsStatus = sequencePlayingExercise?.let { exercise ->
                ExerciseQuotaRepository.status(
                    preferences,
                    ExerciseQuotaRepository.id("sequences", exercise.difficulty, exercise.type),
                    eligibleSequenceExercises(exercise.type, exercise.difficulty).isNotEmpty()
                )
            },
            exercise = sequencePlayingExercise,
            selectedOrder = sequenceSelectedOrder,
            playOrder = sequencePlayOrder,
            contentFormat = sequenceContentFormat,
            message = sequencePlayMessage,
            exerciseFontFamily = exerciseFontFamily,
            rewardMessage = if (rewardMode.isStars) "Has ganado estrellas por completar el ejercicio." else "Has ganado tiempo para jugar por completar el ejercicio.",
            onBack = {
                screen = "sequence-difficulty"
                sequenceSelectedOrder = emptyList()
                sequencePlayMessage = ""
            },
            onReplayInstruction = {
                sequencePlayingExercise?.type?.toVoiceKey()?.let { typeKey ->
                    ExerciseVoiceManager.items.firstOrNull { it.key == "instruction_sequences_$typeKey" }?.let {
                        ExerciseVoiceManager.playLatest(context, it, preview = true)
                    }
                }
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
        StoriesBoardContent(
            context = context,
            storedStories = storedStories,
            disabledStoryCollections = disabledStoryCollections,
            storyCollections = storyCollections,
            selectedStoriesCollection = selectedStoriesCollection,
            onSelectedStoriesCollectionChange = { selectedStoriesCollection = it },
            selectedStoryPath = selectedStoryPath,
            storiesBoardTab = storiesBoardTab,
            onStoriesBoardTabChange = { storiesBoardTab = it },
            exerciseFontFamily = exerciseFontFamily,
            onHome = { screen = "home"; selectedStoriesCollection = null; selectedStoryPath = null },
            onSelectStory = { story ->
                selectedStoryPath = story.directory.absolutePath
                val needsAccess = story.metadata.requiredAccessObjects.any { !InventoryRepository.contains(context, it) }
                if (needsAccess) storyAccessReturnScreen = "stories"
                screen = if (needsAccess) "story-access" else "story-cover"
            }
        )
    } else if (screen == "story-cover") {
        val selectedStory = selectedStoryPath?.let { path ->
            storedStories.firstOrNull { it.directory.absolutePath == path }
                ?: runCatching { StoryRepository.read(File(path)) }.getOrNull()
        }
        StoryCoverRoute(
            story = selectedStory,
            showAccessRequirements = showStoryAccessRequirements,
            fontFamily = exerciseFontFamily,
            onMissingStoryBack = { screen = "stories"; selectedStoryPath = null },
            onAccessRequirementsBack = { showStoryAccessRequirements = false },
            onBack = { screen = "stories"; selectedStoryPath = null; showStoryAccessRequirements = false },
            onPlay = {
                val missingObjects = selectedStory?.metadata?.requiredAccessObjects.orEmpty().filterNot {
                    InventoryRepository.contains(context, it)
                }
                showStoryAccessRequirements = false
                if (missingObjects.isEmpty()) {
                    screen = "story-player"
                } else {
                    storyAccessReturnScreen = "story-cover"
                    screen = "story-access"
                }
            }
        )
    } else if (screen == "story-access") {
        val selectedStory = selectedStoryPath?.let { path ->
            storedStories.firstOrNull { it.directory.absolutePath == path }
                ?: runCatching { StoryRepository.read(File(path)) }.getOrNull()
        }
        StoryAccessRoute(
            story = selectedStory,
            hasMissingAccessObjects = selectedStory?.metadata?.requiredAccessObjects?.any {
                !InventoryRepository.contains(context, it)
            } == true,
            onMissingStory = { screen = "stories" },
            onAccessAlreadyGranted = { screen = "story-cover" },
            onBack = { screen = "stories"; selectedStoryPath = null; showStoryAccessRequirements = false }
        )
    } else if (screen == "story-player") {
        val selectedStory = selectedStoryPath?.let { path ->
            storedStories.firstOrNull { it.directory.absolutePath == path }
                ?: runCatching { StoryRepository.read(File(path)) }.getOrNull()
        }
        StoryPlayerRoute(
            story = selectedStory,
            balanceSeconds = balanceSeconds,
            fontFamily = exerciseFontFamily,
            onMissingStory = { screen = "stories" },
            onAccessRequirementsBack = { screen = "stories"; selectedStoryPath = null; showStoryAccessRequirements = false },
            onExit = { screen = "stories"; selectedStoryPath = null }
        )
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
                if (result.success && !stored.metadata.collectible) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        try { AchievementSoundManager.play(context, AchievementSoundEvent.ITEM_PURCHASED) } catch (_: Exception) { }
                    }
                }
                result.success
            }
        )
    } else if (screen == "cards-options") {
        CardsOptionsRoute(
            pairs = pairs,
            difficulty = difficulty,
            preferences = preferences,
            questionCount = questionCount,
            easyReward = easyRewardMinutes,
            normalReward = normalRewardMinutes,
            hardReward = hardRewardMinutes,
            isStarsMode = rewardMode.isStars,
            startMessage = cardsStartMessage,
            manualChoiceLocked = manualCategoryLimitReached,
            manualCategoryUsesToday = manualCategoryUsesToday,
            dailyManualLimit = dailyManualLimit,
            onBack = { screen = "home"; cardsStartMessage = "" },
            onDifficultyChange = { difficulty = it; cardsStartMessage = "" },
            onSurprise = { startCardsGame("Al azar") },
            onChooseTheme = { cardsStartMessage = ""; screen = "category-select" },
            onMixThemes = { startCardsGame("Mezcladas") }
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
        val cardsQuotaStates = listOf("Fácil", "Normal", "Difícil").map { level ->
            val hasExercises = pairs.count { it.active && it.difficulty.equals(level, true) } >= questionCount
            !ExerciseQuotaRepository.status(preferences, ExerciseQuotaRepository.id("cards", level), hasExercises).available
        }
        val wordsQuotaStates = listOf("Fácil", "Normal", "Difícil").map { level ->
            val hasExercises = wordExercises.any { it.active && it.category !in disabledWordExerciseCategories && it.difficulty.equals(level, true) }
            !ExerciseQuotaRepository.status(preferences, ExerciseQuotaRepository.id("words", level), hasExercises).available
        }
        val sequencesQuotaStates = listOf("Orden temporal", "Procesos", "Causa y consecuencia", "Ciclos").flatMap { type ->
            listOf("Fácil", "Normal", "Difícil").map { level ->
                val hasExercises = eligibleSequenceExercises(type, level).isNotEmpty()
                !ExerciseQuotaRepository.status(preferences, ExerciseQuotaRepository.id("sequences", level, type), hasExercises).available
            }
        }
        HomeDashboardScreen(
            cardsAllQuotaLocked = cardsQuotaStates.all { it },
            wordsAllQuotaLocked = wordsQuotaStates.all { it },
            sequencesAllQuotaLocked = sequencesQuotaStates.all { it },
            isStarsMode = rewardMode.isStars,
            formattedBalance = formatRewardBalance(balanceSeconds, rewardMode),
            balanceSeconds = balanceSeconds,
            balanceMinutes = balanceMinutes,
            balanceRemainderSeconds = balanceRemainderSeconds,
            soundEnabled = appAudioState.soundEnabled,
            gameActiveCards = gameActiveCards,
            gameActiveSequences = gameActiveSequences,
            gameActiveWords = gameActiveWords,
            gameActiveStories = gameActiveStories,
            minimumPlayMinutes = minimumPlayMinutes,
            canStartPlaySession = balanceSeconds > 0 && (
                preferences.getBoolean("play_session_authorized", false) ||
                    balanceSeconds >= minimumPlayMinutes * 60
            ),
            showParentPlaceholder = screen == "parent",
            onOpenSettings = { pinEntry = ""; pinError = false; screen = "parent-pin" },
            onToggleSound = {
                appAudioState.soundEnabled = !appAudioState.soundEnabled
                preferences.edit().putBoolean("sound_enabled", appAudioState.soundEnabled).apply()
            },
            onOpenHelp = { screen = "help" },
            onOpenCards = { screen = "cards-options"; cardsStartMessage = "" },
            onOpenSequences = { if (gameActiveSequences && !sequencesQuotaStates.all { it }) screen = "sequences" },
            onOpenWords = {
                wordSelectedDifficulty = "Fácil"
                wordStartMessage = ""
                screen = "word-options"
            },
            onOpenStories = { if (gameActiveStories) screen = "stories" },
            onOpenInventory = {
                storedStoryObjects = ObjectRepository.list(context)
                storyObjectInventoryState.refresh(context)
                storyInventoryMessage = ""
                screen = "story-inventory"
            },
            onExit = { pinEntry = ""; pinError = false; screen = "exit-pin" },
            onStartPlaySession = {
                val now = System.currentTimeMillis()
                val existingDeadline = preferences.getLong("play_deadline_ms", 0L)
                val savedBalance = preferences.getInt("balance_seconds", balanceSeconds).coerceAtLeast(0)
                val sessionAuthorized = preferences.getBoolean("play_session_authorized", false)
                if (!sessionAuthorized && savedBalance < minimumPlayMinutes * 60) {
                    balanceSeconds = savedBalance
                } else {
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
                        androidx.core.content.ContextCompat.startForegroundService(
                            context,
                            Intent(context, PlayTimeService::class.java).setAction(PlayTimeService.ACTION_START)
                        )
                        screen = "play-time"
                        (context as? ComponentActivity)?.moveTaskToBack(true)
                    } else {
                        balanceSeconds = 0
                    }
                }
            }
        )
    }
    }
    if (showPermissionRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRecoveryDialog = false },
            title = { Text("Se ha detectado un cambio en los permisos") },
            text = {
                val missingPermissions = buildList {
                    if (!isPlayTimeAccessibilityEnabled(context)) add("Accesibilidad")
                    if (!isNotificationPermissionReady(context)) add("Notificaciones")
                }
                val missingText = missingPermissions.joinToString(" y ")
                Text("«Aprender para jugar» ha pasado a modo Estrellas de forma automática. Esto puede deberse a la pérdida o renovación de permisos ($missingText). Puedes intentar corregir los permisos o continuar en modo Estrellas, sin control de tiempo fuera de la aplicación.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRecoveryDialog = false
                    permissionRecoveryEntryPending = true
                    pinEntry = ""
                    pinError = false
                    parentSection = "device-setup-wizard"
                    screen = "parent-pin"
                }) { Text("Configurar permisos") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRecoveryDialog = false }) { Text("Continuar en modo Estrellas") }
            }
        )
    }
    if (showTimeModeNotActivatedDialog) {
        AlertDialog(
            onDismissRequest = { showTimeModeNotActivatedDialog = false },
            title = { Text("Modo Tiempo no activado") },
            text = {
                Text("No se ha activado el modo Tiempo porque has cancelado el asistente antes de completar la configuración. Puedes volver a intentarlo cuando quieras desde la configuración parental.")
            },
            confirmButton = {
                TextButton(onClick = { showTimeModeNotActivatedDialog = false }) { Text("Entendido") }
            }
        )
    }
    if (libraryTaskRunning) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Procesando biblioteca") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Reservar siempre el mismo espacio para el mensaje evita que
                    // el diálogo cambie de altura cuando cambia el texto de progreso.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            libraryTaskMessage.ifBlank { "Preparando operación…" },
                            fontSize = 14.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // El indicador conserva una altura fija tanto en modo
                    // indeterminado como cuando ya se conoce el porcentaje.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (libraryTaskIndeterminate) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                progress = { libraryTaskProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            if (libraryTaskIndeterminate) {
                                "La operación puede tardar. No cierres la aplicación."
                            } else {
                                "${(libraryTaskProgress * 100).toInt()}% completado. No cierres la aplicación."
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF667085),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = { }
        )
    }
    if (remoteLibraryPendingImport != null) {
        val pendingPackage = remoteLibraryPendingImport!!
        AlertDialog(
            onDismissRequest = { remoteLibraryPendingImport = null },
            title = { Text("Biblioteca descargada") },
            text = { Text("«${pendingPackage.name}» se ha descargado correctamente y está lista para instalar. Puedes importarla ahora o dejarla para más tarde.") },
            confirmButton = {
                TextButton(onClick = {
                    remoteLibraryPendingImport = null
                    libraryTaskRunning = true
                    updateLibraryTaskProgress("Validando el paquete descargado…", 0, 0)
                    remoteLibraryScope.launch {
                        try {
                            val file = withContext(Dispatchers.IO) {
                                RemoteLibraryRepository.cachedFile(context, pendingPackage.name)
                            }
                            check(file.isFile && file.length() > 0L) { "El archivo descargado ya no está disponible. Descárgalo de nuevo." }
                            val inspected = withContext(Dispatchers.IO) {
                                LibraryPackageManager.inspect(context, Uri.fromFile(file), updateLibraryTaskProgress)
                            }
                            transferPackage?.let { previous ->
                                withContext(Dispatchers.IO) { LibraryPackageManager.discard(previous) }
                                transferPackage = null
                            }
                            transferPackage = inspected
                            transferImportCategories = inspected.modalities.associate { info ->
                    info.modality to info.categories.flatMap { category ->
                        if (info.modality == ContentStorage.Modality.SECUENCIAS) category.sequenceExercises.map { exercise ->
                            LibraryPackageManager.sequenceExerciseSelectionKey(category.directory, exercise.type, exercise.directory)
                        } else listOf(category.directory)
                    }.toSet()
                }
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
                        } finally {
                            libraryTaskRunning = false
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



@Composable
private fun StoryAccessRoute(
    story: StoredStory?,
    hasMissingAccessObjects: Boolean,
    onMissingStory: () -> Unit,
    onAccessAlreadyGranted: () -> Unit,
    onBack: () -> Unit
) {
    if (story == null) {
        onMissingStory()
    } else if (!hasMissingAccessObjects) {
        onAccessAlreadyGranted()
    } else {
        StoryAccessRequirementsScreen(
            story = story,
            onBack = onBack
        )
    }
}

@Composable
private fun StoryCoverRoute(
    story: StoredStory?,
    showAccessRequirements: Boolean,
    fontFamily: FontFamily,
    onMissingStoryBack: () -> Unit,
    onAccessRequirementsBack: () -> Unit,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    if (story == null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No se ha podido abrir este cuento.")
            OutlinedButton(onClick = onMissingStoryBack) { Text("Volver a la cartelera") }
        }
    } else if (showAccessRequirements) {
        StoryAccessRequirementsScreen(
            story = story,
            onBack = onAccessRequirementsBack
        )
    } else {
        StoryCoverScreen(
            story = story,
            fontFamily = fontFamily,
            onBack = onBack,
            onPlay = onPlay
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordExerciseScreen(
    hasTarget: Boolean,
    targetWord: String,
    targetPicture: String,
    difficulty: String,
    message: String,
    placedLetters: List<Int>,
    letterOrder: List<Int>,
    balanceText: String,
    roundsUsed: Int,
    roundsLimit: Int?,
    exerciseFontFamily: FontFamily,
    onPlayInstruction: () -> Unit,
    onExit: () -> Unit,
    onBackHome: () -> Unit,
    onChooseLetter: (Int) -> Unit
) {
    if (!hasTarget) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Palabras", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF24537A))
            Spacer(Modifier.height(16.dp))
            Text(message, fontSize = 17.sp, color = Color(0xFF667085), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onBackHome, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
        }
        return
    }

    val complete = placedLetters.size == targetWord.length
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ExerciseStatusBar(
            balance = balanceText,
            roundsUsed = roundsUsed,
            roundsLimit = roundsLimit,
            difficulty = difficulty
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Palabras", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF24537A))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onPlayInstruction) { Text("🔊") }
                OutlinedButton(onClick = onExit) { Text("Salir") }
            }
        }
        Text("Forma la palabra", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (targetPicture.startsWith("content://") || targetPicture.startsWith("file://")) {
            AndroidView(
                factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 220) } },
                update = { view -> try { view.setImageURI(Uri.parse(targetPicture)) } catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) } },
                modifier = Modifier.fillMaxWidth().height(170.dp)
            )
        } else {
            Text(targetPicture, fontSize = 76.sp)
        }
        Text("Toca las letras en el orden correcto.", fontSize = 15.sp, color = Color(0xFF667085))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            targetWord.indices.forEach { position ->
                Card(
                    modifier = Modifier.width(42.dp).height(54.dp),
                    colors = CardDefaults.cardColors(containerColor = if (position < placedLetters.size) Color(0xFFE2F4DF) else Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB7C8B2))
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(placedLetters.getOrNull(position)?.let { targetWord[it].uppercase() } ?: "·", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            letterOrder.forEach { letterIndex ->
                val alreadyPlaced = letterIndex in placedLetters
                OutlinedButton(
                    onClick = { onChooseLetter(letterIndex) },
                    enabled = !complete && !alreadyPlaced,
                    modifier = Modifier.height(58.dp)
                ) {
                    Text(targetWord.getOrNull(letterIndex)?.uppercase() ?: "", fontSize = 24.sp, fontFamily = exerciseFontFamily, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(message, fontSize = 16.sp, color = if (message.startsWith("¡")) Color(0xFF24733B) else Color(0xFF667085))
        Text("Dificultad: $difficulty", fontSize = 13.sp, color = Color(0xFF667085))
    }
}

@Composable
private fun CardsExerciseScreen(
    activeExercisePairs: List<PairItem>,
    exercisePairs: List<PairItem>,
    rewardMode: RewardMode,
    balanceSeconds: Int,
    roundsUsed: Int,
    roundsLimit: Int?,
    difficulty: String,
    selected: Map<String, String>,
    selectedPicture: String,
    resolved: Set<String>,
    evaluatedPictures: Map<String, Boolean>,
    feedback: String,
    pairCount: Int,
    exerciseFontFamily: FontFamily,
    onSelectedChange: (Map<String, String>) -> Unit,
    onSelectedPictureChange: (String) -> Unit,
    onFeedbackChange: (String) -> Unit,
    onResolveCardsPair: (String, String) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
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
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Column(
        modifier = Modifier.fillMaxSize().clipToBounds().verticalScroll(rememberScrollState()).padding(if (isTabletLayout) 28.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExerciseStatusBar(
            balance = if (rewardMode.isStars) "${formatRewardBalance(balanceSeconds, rewardMode)} ⭐" else "${balanceSeconds / 60} min ${balanceSeconds % 60} s",
            roundsUsed = roundsUsed,
            roundsLimit = roundsLimit,
            difficulty = difficulty
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Une las palabras con sus dibujos", fontSize = 25.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, color = Color(0xFF284B63), modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { ExerciseVoiceManager.items.firstOrNull { it.key == "instruction_cards" }?.let { ExerciseVoiceManager.playLatest(context, it, preview = true) } }) { Text("🔊") }
        }
        Text("1. Elige una palabra.  2. Toca su dibujo.", fontSize = 18.sp, lineHeight = 26.sp)
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
                                        onSelectedChange(emptyMap())
                                        onFeedbackChange("Selección cancelada.")
                                    } else {
                                        onSelectedChange(mapOf("word" to item.word))
                                        onFeedbackChange("Ahora busca el dibujo de «" + item.word + "».")
                                    }
                                } else {
                                    onResolveCardsPair(item.word, pendingPicture)
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
                        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = cardPadding, vertical = cardPadding)) {
                            val availableWidthPx = with(density) { maxWidth.toPx() }
                            val baseStyle = TextStyle(
                                fontSize = wordCardFontSize,
                                fontFamily = exerciseFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            // Keep each whitespace-delimited word indivisible: size the whole
                            // phrase against its widest word, not against the full phrase.
                            val widestWordPx = item.word.split(Regex("\\s+"))
                                .filter { it.isNotBlank() }
                                .maxOfOrNull { word ->
                                    textMeasurer.measure(
                                        text = AnnotatedString(word),
                                        style = baseStyle,
                                        maxLines = 1,
                                        softWrap = false
                                    ).size.width.toFloat()
                                } ?: 0f
                            val fittedFontSize = if (widestWordPx > availableWidthPx && widestWordPx > 0f) {
                                (wordCardFontSize.value * (availableWidthPx * 0.96f) / widestWordPx).sp
                            } else wordCardFontSize
                            Text(
                                item.word,
                                modifier = Modifier.fillMaxWidth(),
                                style = TextStyle(
                                    fontSize = fittedFontSize,
                                    lineHeight = fittedFontSize * 1.3f,
                                    fontFamily = exerciseFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF29213B),
                                    textAlign = TextAlign.Center
                                ),
                                softWrap = true,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pictureCardOrder.forEach { item ->
                    Card(Modifier.fillMaxWidth().clickable {
                        val word = selected["word"]
                        if (word == null) {
                            if (selectedPicture == item.picture) {
                                onSelectedPictureChange("")
                                onFeedbackChange("Selección cancelada.")
                            } else {
                                onSelectedPictureChange(item.picture)
                                onFeedbackChange("Has elegido un dibujo. Ahora toca la palabra que le corresponde.")
                            }
                        } else if (word !in resolved) {
                            onResolveCardsPair(word, item.picture)
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
        Text("Parejas resueltas: ${resolved.size}/$pairCount", fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
        if (feedback.isNotBlank()) Text(feedback, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = if (feedback.startsWith("Muy bien")) Color(0xFF24733B) else Color(0xFF72521B))
        OutlinedButton(onClick = onExit) { Text("Salir del ejercicio") }
    }
}



private fun androidx.compose.foundation.lazy.LazyListScope.gameModesItems(
    onOpenCards: () -> Unit,
    onOpenSequences: () -> Unit,
    onOpenWords: () -> Unit,
    onOpenStories: () -> Unit
) {
    item { Text("Actividades disponibles", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    item {
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onOpenCards),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🖼️", fontSize = 30.sp)
                Column { Text("Tarjetas", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Biblioteca, categorías y parejas por partida", color = Color(0xFF667085)) }
            }
        }
    }
    item {
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onOpenSequences),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8EC))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🌱", fontSize = 30.sp)
                Column { Text("Secuencias", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Configurar tipo de contenido y secuencias", color = Color(0xFF667085)) }
            }
        }
    }
    item {
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onOpenWords),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🔤", fontSize = 30.sp)
                Column { Text("Palabras", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Configurar vocabulario y dificultad", color = Color(0xFF667085)) }
            }
        }
    }
    item {
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onOpenStories),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE3))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📖", fontSize = 30.sp)
                Column { Text("Cuentos", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Configurar y crear cuentos ilustrados", color = Color(0xFF667085)) }
            }
        }
    }
}


private fun androidx.compose.foundation.lazy.LazyListScope.libraryTransferItems(
    transferMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit
) {
    item { Text("Gestionar bibliotecas", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    item { Text("Elige qué operación quieres realizar. Cada proceso tiene su propia pantalla para mantener separadas la importación y la exportación.", fontSize = 14.sp, color = Color(0xFF667085)) }
    item {
        Card(Modifier.fillMaxWidth().clickable(onClick = onExport), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
        Card(Modifier.fillMaxWidth().clickable(onClick = onImport), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
        Card(Modifier.fillMaxWidth().clickable(onClick = onDelete), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🗑️", fontSize = 30.sp)
                Column {
                    Text("Eliminar biblioteca", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB3261E))
                    Text("Borrar Tarjetas, Palabras o Secuencias", color = Color(0xFF667085))
                }
            }
        }
    }
    if (transferMessage.isNotBlank()) item {
        Text(transferMessage, color = if (transferMessage.startsWith("No se puede") || transferMessage.startsWith("No se pudo")) Color(0xFFB3261E) else Color(0xFF24733B), fontSize = 14.sp)
    }
}


@Composable
private fun StoryPlayerRoute(
    story: StoredStory?,
    balanceSeconds: Int,
    fontFamily: FontFamily,
    onMissingStory: () -> Unit,
    onAccessRequirementsBack: () -> Unit,
    onExit: () -> Unit
) {
    if (story == null) {
        onMissingStory()
    } else if (story.metadata.requiredAccessObjects.any { !InventoryRepository.contains(LocalContext.current, it) }) {
        StoryAccessRequirementsScreen(
            story = story,
            onBack = onAccessRequirementsBack
        )
    } else if (story.metadata.connections.values.any { STORY_LOST_DESTINATION in it }) {
        AlertDialog(
            onDismissRequest = onExit,
            title = { Text("Estructura narrativa incompleta") },
            text = { Text("Este cuento contiene conexiones huérfanas. Entra en su editor, abre «Editar estructura narrativa» y reasigna o elimina las salidas marcadas en rojo antes de reproducirlo.") },
            confirmButton = { Button(onClick = onExit) { Text("Volver") } }
        )
    } else {
        StoryPlayerScreen(
            story = story,
            balanceSeconds = balanceSeconds,
            fontFamily = fontFamily,
            onExit = onExit
        )
    }
}


@Composable
private fun ExerciseResultRoute(
    resultCorrectCount: Int,
    resultTotalCount: Int,
    isPerfectCardsResult: Boolean,
    rewardMode: RewardMode,
    lastEarnedSeconds: Int,
    onHome: () -> Unit,
    onNextExercise: () -> Unit
) {
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
        isPerfectCardsResult = isPerfectCardsResult,
        rewardAmountText = if (rewardMode.isStars) "+${formatRewardBalance(lastEarnedSeconds, rewardMode)} ⭐" else "+${lastEarnedSeconds / 60}:${(lastEarnedSeconds % 60).toString().padStart(2, '0')}",
        rewardUnitText = if (rewardMode.isStars) "ESTRELLAS GANADAS" else "MINUTOS GANADOS",
        correctCount = resultCorrectCount,
        totalCount = resultTotalCount,
        onHome = onHome,
        onNextExercise = onNextExercise
    )
}

@Composable
private fun SequenceGameRoute(
    balance: String,
    roundsStatus: ExerciseQuotaRepository.QuotaStatus?,
    exercise: SequenceExercise?,
    selectedOrder: List<Int>,
    playOrder: List<Int>,
    contentFormat: String,
    message: String,
    exerciseFontFamily: FontFamily,
    rewardMessage: String,
    onBack: () -> Unit,
    onReplayInstruction: () -> Unit,
    onSelectCard: (Int) -> Unit
) {
    SequenceGameScreen(
        balance = balance,
        roundsStatus = roundsStatus,
        exercise = exercise,
        selectedOrder = selectedOrder,
        playOrder = playOrder,
        contentFormat = contentFormat,
        message = message,
        exerciseFontFamily = exerciseFontFamily,
        rewardMessage = rewardMessage,
        onBack = onBack,
        onReplayInstruction = onReplayInstruction,
        onSelectCard = onSelectCard
    )
}

private fun buildQuotaAvailabilitySnapshot(
    pairs: List<PairItem>,
    wordExercises: List<PairItem>,
    disabledWordExerciseCategories: Set<String>,
    questionCount: Int,
    sequenceGameState: SequenceGameState,
    sequenceExercises: List<SequenceExercise>,
    sequenceMaxCards: Int,
    disabledSequenceCategories: Set<String>
): Map<String, Boolean> {
    val result = mutableMapOf<String, Boolean>()
    listOf("Fácil", "Normal", "Difícil").forEach { level ->
        result[ExerciseQuotaRepository.id("cards", level)] =
            pairs.count { it.active && it.difficulty.equals(level, true) } >= questionCount
        result[ExerciseQuotaRepository.id("words", level)] =
            wordExercises.any { it.active && it.category !in disabledWordExerciseCategories && it.difficulty.equals(level, true) }
        listOf("Orden temporal", "Procesos", "Causa y consecuencia", "Ciclos").forEach { type ->
            result[ExerciseQuotaRepository.id("sequences", level, type)] =
                sequenceGameState.eligibleExercises(
                    sequenceExercises, type, sequenceMaxCards, disabledSequenceCategories, level
                ).isNotEmpty()
        }
    }
    return result
}

@Composable
private fun SequenceDifficultyRoute(
    chosenCategory: String,
    chosenDifficulty: String,
    message: String,
    preferences: android.content.SharedPreferences,
    sequenceGameState: SequenceGameState,
    sequenceExercises: List<SequenceExercise>,
    maxCards: Int,
    disabledCategories: Set<String>,
    easyReward: Int,
    normalReward: Int,
    hardReward: Int,
    isStarsMode: Boolean,
    onBack: () -> Unit,
    onDifficultyChange: (String) -> Unit,
    onSurprise: () -> Unit,
    onChooseTheme: () -> Unit,
    onMixThemes: () -> Unit
) {
    val sequenceCandidates = sequenceGameState.eligibleExercises(
        sequenceExercises, chosenCategory, maxCards, disabledCategories, chosenDifficulty
    )
    val sequenceCategories = sequenceCandidates.map { it.category }.distinct()
    val sequenceQuota = ExerciseQuotaRepository.status(
        preferences,
        ExerciseQuotaRepository.id("sequences", chosenDifficulty, chosenCategory),
        sequenceCandidates.isNotEmpty()
    )
    val lockedSequenceDifficulties = listOf("Fácil", "Normal", "Difícil").filter { level ->
        val hasExercises = sequenceGameState.eligibleExercises(
            sequenceExercises, chosenCategory, maxCards, disabledCategories, level
        ).isNotEmpty()
        val status = ExerciseQuotaRepository.status(
            preferences, ExerciseQuotaRepository.id("sequences", level, chosenCategory), hasExercises
        )
        hasExercises && status.limit != null && !status.available
    }.toSet()
    val quotaText = when {
        sequenceQuota.limit == null -> "Sin límite de intentos."
        sequenceQuota.available -> "Intentos restantes: ${sequenceQuota.remaining}/${sequenceQuota.limit}."
        else -> sequenceQuota.reason ?: "Dificultad bloqueada."
    }
    ExerciseOptionsScreen(
        title = chosenCategory,
        difficulty = chosenDifficulty,
        easyReward = easyReward,
        normalReward = normalReward,
        hardReward = hardReward,
        isStarsMode = isStarsMode,
        message = message.ifBlank { quotaText },
        lockedDifficulties = lockedSequenceDifficulties,
        surpriseDescription = "La app elige una secuencia entre todos los temas disponibles.",
        mixDescription = "La app busca cambiar de tema entre una secuencia y la siguiente.",
        surpriseAvailable = sequenceCandidates.isNotEmpty() && sequenceQuota.available,
        chooseAvailable = sequenceCandidates.isNotEmpty() && sequenceQuota.available,
        mixAvailable = sequenceCategories.size >= 2 && sequenceQuota.available,
        onBack = onBack,
        onDifficultyChange = onDifficultyChange,
        onSurprise = onSurprise,
        onChooseTheme = onChooseTheme,
        onMixThemes = onMixThemes
    )
}

@Composable
private fun CardsOptionsRoute(
    pairs: List<PairItem>,
    difficulty: String,
    preferences: android.content.SharedPreferences,
    questionCount: Int,
    easyReward: Int,
    normalReward: Int,
    hardReward: Int,
    isStarsMode: Boolean,
    startMessage: String,
    manualChoiceLocked: Boolean,
    manualCategoryUsesToday: Int,
    dailyManualLimit: Int,
    onBack: () -> Unit,
    onDifficultyChange: (String) -> Unit,
    onSurprise: () -> Unit,
    onChooseTheme: () -> Unit,
    onMixThemes: () -> Unit
) {
    val activeCards = pairs.filter { it.active && it.difficulty == difficulty }
    val categoryCounts = activeCards.groupingBy { it.category.lowercase() }.eachCount()
    val cardsQuota = ExerciseQuotaRepository.status(
        preferences, ExerciseQuotaRepository.id("cards", difficulty), activeCards.size >= questionCount
    )
    val lockedCardDifficulties = listOf("Fácil", "Normal", "Difícil").filter { level ->
        val hasExercises = pairs.count { it.active && it.difficulty.equals(level, true) } >= questionCount
        val status = ExerciseQuotaRepository.status(preferences, ExerciseQuotaRepository.id("cards", level), hasExercises)
        hasExercises && status.limit != null && !status.available
    }.toSet()
    val quotaText = when {
        cardsQuota.limit == null -> "Sin límite de intentos."
        cardsQuota.available -> "Intentos restantes: ${cardsQuota.remaining}/${cardsQuota.limit}."
        else -> cardsQuota.reason ?: "Dificultad bloqueada."
    }
    ExerciseOptionsScreen(
        title = "Tarjetas",
        difficulty = difficulty,
        easyReward = easyReward,
        normalReward = normalReward,
        hardReward = hardReward,
        isStarsMode = isStarsMode,
        message = startMessage.ifBlank { quotaText },
        lockedDifficulties = lockedCardDifficulties,
        surpriseDescription = "Tarjetas de cualquier tema de esta dificultad.",
        mixDescription = "Combina tarjetas de diferentes temas en la misma partida.",
        surpriseAvailable = activeCards.size >= questionCount && cardsQuota.available,
        chooseAvailable = categoryCounts.values.any { it >= questionCount } && cardsQuota.available,
        mixAvailable = questionCount >= 2 && activeCards.size >= questionCount && categoryCounts.size >= 2 && cardsQuota.available,
        manualChoiceLocked = manualChoiceLocked,
        manualLimitMessage = if (manualChoiceLocked) "Has usado las $dailyManualLimit elecciones manuales de hoy ($manualCategoryUsesToday/$dailyManualLimit)." else "",
        onBack = onBack,
        onDifficultyChange = onDifficultyChange,
        onSurprise = onSurprise,
        onChooseTheme = onChooseTheme,
        onMixThemes = onMixThemes
    )
}

@Composable
private fun SequenceSelectionRoute(
    chosenCategory: String,
    message: String,
    maxCards: Int,
    preferences: android.content.SharedPreferences,
    sequenceGameState: SequenceGameState,
    sequenceExercises: List<SequenceExercise>,
    disabledCategories: Set<String>,
    onBack: () -> Unit,
    onChooseType: (String, Boolean) -> Unit
) {
    val types = listOf("Orden temporal", "Procesos", "Causa y consecuencia", "Ciclos")
    fun eligible(type: String, level: String? = null): List<SequenceExercise> =
        sequenceGameState.eligibleExercises(sequenceExercises, type, maxCards, disabledCategories, level)
    val availableCounts = types.associateWith { type -> eligible(type).size }
    val lockedTypes = types.filter { type ->
        listOf("Fácil", "Normal", "Difícil").none { level ->
            val hasExercises = eligible(type, level).isNotEmpty()
            ExerciseQuotaRepository.status(
                preferences, ExerciseQuotaRepository.id("sequences", level, type), hasExercises
            ).available
        }
    }.toSet()
    SequenceSelectionScreen(
        isDifficultyScreen = false,
        chosenCategory = chosenCategory,
        message = message,
        maxCards = maxCards,
        availableCounts = availableCounts,
        lockedTypes = lockedTypes,
        rewardLabels = emptyMap(),
        onBack = onBack,
        onChooseType = onChooseType,
        onChooseDifficulty = {}
    )
}

@Composable
private fun SequenceCategorySelectionRoute(
    difficulty: String,
    chosenType: String,
    message: String,
    sequenceGameState: SequenceGameState,
    sequenceExercises: List<SequenceExercise>,
    maxCards: Int,
    disabledCategories: Set<String>,
    onBack: () -> Unit,
    onSelectCategory: (String) -> Unit
) {
    val availableCounts = sequenceGameState.eligibleExercises(
        sequenceExercises, chosenType, maxCards, disabledCategories, difficulty
    ).groupingBy { it.category }.eachCount().toSortedMap()
    WordCategorySelectionScreen(
        difficulty = difficulty,
        availableCounts = availableCounts,
        message = message,
        onBack = onBack,
        onSelectCategory = onSelectCategory
    )
}

@Composable
private fun WordOptionsRoute(
    wordExercises: List<PairItem>,
    disabledCategories: Set<String>,
    difficulty: String,
    preferences: android.content.SharedPreferences,
    easyReward: Int,
    normalReward: Int,
    hardReward: Int,
    isStarsMode: Boolean,
    startMessage: String,
    onBack: () -> Unit,
    onDifficultyChange: (String) -> Unit,
    onSurprise: () -> Unit,
    onChooseTheme: (List<String>) -> Unit,
    onMixThemes: () -> Unit
) {
    val availableWords = wordExercises.filter {
        it.active && it.category !in disabledCategories && it.difficulty == difficulty
    }
    val wordCategories = availableWords.map { it.category }.distinct()
    val wordQuota = ExerciseQuotaRepository.status(
        preferences, ExerciseQuotaRepository.id("words", difficulty), availableWords.isNotEmpty()
    )
    val lockedWordDifficulties = listOf("Fácil", "Normal", "Difícil").filter { level ->
        val hasExercises = wordExercises.any {
            it.active && it.category !in disabledCategories && it.difficulty.equals(level, true)
        }
        val status = ExerciseQuotaRepository.status(
            preferences, ExerciseQuotaRepository.id("words", level), hasExercises
        )
        hasExercises && status.limit != null && !status.available
    }.toSet()
    val quotaText = when {
        wordQuota.limit == null -> "Sin límite de intentos."
        wordQuota.available -> "Intentos restantes: ${wordQuota.remaining}/${wordQuota.limit}."
        else -> wordQuota.reason ?: "Dificultad bloqueada."
    }
    ExerciseOptionsScreen(
        title = "Palabras",
        difficulty = difficulty,
        easyReward = easyReward,
        normalReward = normalReward,
        hardReward = hardReward,
        isStarsMode = isStarsMode,
        message = startMessage.ifBlank { quotaText },
        lockedDifficulties = lockedWordDifficulties,
        surpriseDescription = "La app elige una palabra entre todos los temas disponibles.",
        mixDescription = "La app busca cambiar de tema entre una palabra y la siguiente.",
        surpriseAvailable = availableWords.isNotEmpty() && wordQuota.available,
        chooseAvailable = availableWords.isNotEmpty() && wordQuota.available,
        mixAvailable = wordCategories.size >= 2 && wordQuota.available,
        onBack = onBack,
        onDifficultyChange = onDifficultyChange,
        onSurprise = onSurprise,
        onChooseTheme = { onChooseTheme(wordCategories.sorted()) },
        onMixThemes = onMixThemes
    )
}
