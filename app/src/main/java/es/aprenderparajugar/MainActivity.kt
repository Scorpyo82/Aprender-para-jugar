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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    // Start the earned-time countdown at the first lifecycle transition away from the app.
    // onPause is earlier than onStop and closes the race with the accessibility service/Home.
    private fun persistAndStartEarnedCountdown() {
        val prefs = getSharedPreferences("aprender_para_jugar", MODE_PRIVATE)
        if (prefs.getBoolean("device_setup_in_progress", false) ||
            prefs.getBoolean("parent_area_active", false) ||
            prefs.getBoolean("manual_exit_allowed", false) ||
            prefs.getBoolean("play_paused_for_screen_lock", false)) return

        val now = System.currentTimeMillis()
        val deadline = prefs.getLong("play_deadline_ms", 0L)
        val balance = prefs.getInt("balance_seconds", 0).coerceAtLeast(0)
        if (deadline > now) {
            val remaining = ((deadline - now + 999L) / 1000L).toInt().coerceAtLeast(0)
            prefs.edit().putInt("balance_seconds", remaining).commit()
        } else if (balance > 0) {
            // Save synchronously so Home/Back cannot race a pending SharedPreferences write.
            prefs.edit()
                .putLong("play_deadline_ms", now + balance.toLong() * 1000L)
                .putLong("expiry_last_launch_ms", 0L)
                .commit()
        } else {
            return
        }

        try {
            androidx.core.content.ContextCompat.startForegroundService(
                this,
                Intent(this, PlayTimeService::class.java).setAction(PlayTimeService.ACTION_START)
            )
        } catch (_: Exception) { }
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
        // A manual launch re-arms the focus guard after a parent-authorized exit.
        getSharedPreferences("aprender_para_jugar", MODE_PRIVATE)
            .edit().putBoolean("manual_exit_allowed", false).apply()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFFF8E8)) {
                    AprenderApp()
                }
            }
        }
    }
}

private data class PairItem(val word: String, val picture: String, val category: String = "Primeras palabras", val difficulty: String = "Fácil", val active: Boolean = true)

private data class SequenceExercise(
    val title: String,
    val cards: List<String>,
    val format: String,
    val category: String,
    val alternatives: List<String> = emptyList(),
    val pictures: List<String> = emptyList(),
    val type: String = "Orden temporal",
    val difficulty: String = "Normal",
    val active: Boolean = true
)

private fun defaultSequencePicture(index: Int): String = ""

private fun loadSequenceExercises(preferences: android.content.SharedPreferences): List<SequenceExercise> {
    val saved = preferences.getString("sequence_exercises_temporal_v1", null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(saved)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val cardsJson = item.optJSONArray("cards") ?: JSONArray()
            val cards = (0 until cardsJson.length()).map { cardsJson.getString(it) }
            if (cards.size < 2) return@mapNotNull null
            val alternativesJson = item.optJSONArray("alternatives") ?: JSONArray()
            val alternatives = (0 until alternativesJson.length()).map { alternativesJson.getString(it) }
            val picturesJson = item.optJSONArray("pictures") ?: JSONArray()
            val pictures = (0 until picturesJson.length()).map { picturesJson.getString(it) }
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
    }.getOrDefault(emptyList())
}

private fun saveSequenceExercises(preferences: android.content.SharedPreferences, exercises: List<SequenceExercise>) {
    val array = JSONArray()
    exercises.forEach { exercise ->
        array.put(JSONObject().apply {
            put("title", exercise.title)
            put("cards", JSONArray(exercise.cards))
            put("format", exercise.format)
            put("category", exercise.category)
            put("alternatives", JSONArray(exercise.alternatives))
            put("pictures", JSONArray(exercise.pictures))
            put("type", exercise.type)
            put("difficulty", exercise.difficulty)
            put("active", exercise.active)
        })
    }
    preferences.edit().putString("sequence_exercises_temporal_v1", array.toString()).apply()
}

private data class WordLearning(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AprenderApp() {
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val preferences = remember { context.getSharedPreferences("aprender_para_jugar", Context.MODE_PRIVATE) }
    // No se borran datos del usuario al iniciar ni al actualizar la aplicación.
    var pairs by remember { mutableStateOf(loadPairs(preferences)) }
    var wordExercises by remember { mutableStateOf(loadWordExercises(preferences)) }
    var gameActiveCards by remember { mutableStateOf(preferences.getBoolean("game_active_cards", true)) }
    var gameActiveSequences by remember { mutableStateOf(preferences.getBoolean("game_active_sequences", true)) }
    var gameActiveWords by remember { mutableStateOf(preferences.getBoolean("game_active_words", true)) }
    var wordExerciseCategories by remember {
        mutableStateOf((preferences.getString("letter_word_categories_v1", "") ?: "").split("|").map { it.trim() }.filter { it.isNotBlank() }.distinct())
    }
    var disabledWordExerciseCategories by remember { mutableStateOf(preferences.getStringSet("disabled_letter_word_categories_v1", emptySet())?.toSet() ?: emptySet()) }
    var wordLibraryPage by remember { mutableStateOf("list") }
    var wordLibraryEditingIndex by remember { mutableIntStateOf(-1) }
    var wordLibraryWordDraft by remember { mutableStateOf("") }
    var wordLibraryPictureDraft by remember { mutableStateOf("") }
    var wordLibraryCategoryDraft by remember { mutableStateOf("") }
    var wordLibraryDifficultyDraft by remember { mutableStateOf("Fácil") }
    var wordLibraryMessage by remember { mutableStateOf("") }
    var wordLibraryCategoryMenuExpanded by remember { mutableStateOf(false) }
    var wordFilterCategory by remember { mutableStateOf("Todas") }
    var wordFilterDifficulty by remember { mutableStateOf("Todas") }
    var wordFilterStatus by remember { mutableStateOf("Todos") }
    var wordPackageMessage by remember { mutableStateOf("") }
    var wordPendingImportMode by remember { mutableStateOf("replace") }
    var wordNewCategoryDraft by remember { mutableStateOf("") }
    var wordCategoryToDelete by remember { mutableStateOf<String?>(null) }
    var wordDraft by remember { mutableStateOf("") }
    var pictureDraft by remember { mutableStateOf("") }
    var categoryDraft by remember { mutableStateOf("") }
    var difficultyDraft by remember { mutableStateOf("Fácil") }
    var editingWord by remember { mutableStateOf<String?>(null) }
    var libraryMessage by remember { mutableStateOf("") }
    var libraryPage by remember { mutableStateOf("list") }
    var sequenceExercises by remember { mutableStateOf(loadSequenceExercises(preferences)) }
    var sequenceEditingIndex by remember { mutableIntStateOf(-1) }
    var sequenceTitleDraft by remember { mutableStateOf("") }
    var sequenceFormatDraft by remember { mutableStateOf("Imagen y texto") }
    var sequenceContentFormat by remember { mutableStateOf(preferences.getString("sequence_content_format_v1", "Imagen y texto") ?: "Imagen y texto") }
    var sequenceMaxCards by remember { mutableIntStateOf(preferences.getInt("sequence_max_cards_v1", 6).coerceIn(2, 6)) }
    var sequenceCategoryDraft by remember { mutableStateOf("") }
    var sequenceNewCategoryDraft by remember { mutableStateOf("") }
    var sequenceCategoryMenuExpanded by remember { mutableStateOf(false) }
    var sequenceCategoryNames by remember {
        mutableStateOf(
            (preferences.getString("sequence_categories_v1", null)
                ?: "")
                .split("|").map { it.trim() }.filter { it.isNotBlank() }.distinct()
        )
    }
    var sequenceCategoryMessage by remember { mutableStateOf("") }
    var sequenceCategoryToDelete by remember { mutableStateOf<String?>(null) }
    var sequenceTypeDraft by remember { mutableStateOf("Orden temporal") }
    var sequenceDifficultyDraft by remember { mutableStateOf("Normal") }
    var sequenceFilterType by remember { mutableStateOf("Todos") }
    var sequenceFilterCategory by remember { mutableStateOf("Todas") }
    var sequenceFilterDifficulty by remember { mutableStateOf("Todas") }
    var sequenceFilterStatus by remember { mutableStateOf("Todos") }
    var disabledSequenceCategories by remember { mutableStateOf(preferences.getStringSet("disabled_sequence_categories", emptySet())?.toSet() ?: emptySet()) }
    var sequenceCardsDraft by remember { mutableStateOf(listOf("", "")) }
    var sequenceCardPicturesDraft by remember { mutableStateOf(listOf("🌱", "🌿")) }
    var pendingSequencePictureIndex by remember { mutableIntStateOf(-1) }
    var pendingCameraTarget by remember { mutableStateOf("pair") }
    var pendingCameraSequenceIndex by remember { mutableIntStateOf(-1) }
    var sequenceAlternativeDraft by remember { mutableStateOf("") }
    var sequenceEditorMessage by remember { mutableStateOf("") }
    var sequencePlayingExercise by remember { mutableStateOf<SequenceExercise?>(null) }
    var sequencePlayOrder by remember { mutableStateOf(emptyList<Int>()) }
    var sequenceSelectedOrder by remember { mutableStateOf(emptyList<Int>()) }
    var sequenceWrongAnswers by remember { mutableIntStateOf(0) }
    var sequencePlayMessage by remember { mutableStateOf("") }
    var sequenceChosenCategory by remember { mutableStateOf("") }
    var sequenceChosenDifficulty by remember { mutableStateOf("") }

    var packageMessage by remember { mutableStateOf("") }
    var sequencePackageMessage by remember { mutableStateOf("") }
    var sequencePendingImportMode by remember { mutableStateOf("replace") }
    var sequenceLibraryFilterCategoryExpanded by remember { mutableStateOf(false) }
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
                    else -> { pictureDraft = savedPicture; libraryMessage = "Foto añadida y comprimida para ocupar poco espacio." }
                }
            } catch (_: Exception) {
                libraryMessage = "No se ha podido guardar la foto."
            } finally {
                bitmap.recycle()
            }
        }
    }
    val firstLaunchNeedsSetup = remember { !preferences.getBoolean("first_launch_setup_seen", false) }
    if (firstLaunchNeedsSetup) {
        preferences.edit()
            .putBoolean("first_launch_setup_seen", true)
            .putBoolean("sound_enabled", false)
            .apply()
    }
    var screen by remember { mutableStateOf(if (firstLaunchNeedsSetup) "parent" else "home") }
    var musicUri by remember { mutableStateOf(preferences.getString("background_music_uri", null)) }
    var soundEnabled by remember { mutableStateOf(if (firstLaunchNeedsSetup) false else preferences.getBoolean("sound_enabled", true)) }
    androidx.compose.runtime.LaunchedEffect(screen) {
        if (screen == "home" && firstLaunchNeedsSetup && !preferences.getBoolean("first_home_sound_activation_done", false)) {
            soundEnabled = true
            preferences.edit().putBoolean("sound_enabled", true).putBoolean("first_home_sound_activation_done", true).apply()
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
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> appInForeground = false
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
    DisposableEffect(musicUri, soundEnabled, appInForeground) {
        var player: MediaPlayer? = null
        if (soundEnabled && appInForeground) {
            try {
                player = if (musicUri.isNullOrBlank()) {
                    MediaPlayer.create(context, R.raw.musica_infantil_2)
                } else {
                    MediaPlayer.create(context, Uri.parse(musicUri))
                }
                player?.apply {
                    isLooping = true
                    setVolume(0.22f, 0.22f)
                    start()
                }
                if (player == null) {
                    musicMessage = "No se ha podido cargar la música."
                }
            } catch (_: Exception) {
                musicMessage = "No se ha podido reproducir la música. Prueba con otro MP3."
            }
        }
        onDispose {
            try { player?.stop() } catch (_: Exception) { }
            player?.release()
        }
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
    var activeExercisePairs by remember { mutableStateOf<List<PairItem>>(emptyList()) }
    var selected by remember { mutableStateOf(mapOf<String, String>()) }
    var selectedPicture by remember { mutableStateOf("") }
    var evaluatedPictures by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var resolved by remember { mutableStateOf(setOf<String>()) }
    var correct by remember { mutableIntStateOf(0) }
    var resultCorrectCount by remember { mutableIntStateOf(0) }
    var resultTotalCount by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf("") }
    var pinEntry by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var parentSection by remember { mutableStateOf(if (firstLaunchNeedsSetup) "device-setup" else "menu") }
    var easyRewardMinutes by remember { mutableIntStateOf(preferences.getInt("reward_easy_minutes", 2).coerceIn(0, 120)) }
    var normalRewardMinutes by remember { mutableIntStateOf(preferences.getInt("reward_normal_minutes", 5).coerceIn(0, 120)) }
    var hardRewardMinutes by remember { mutableIntStateOf(preferences.getInt("reward_hard_minutes", 7).coerceIn(0, 120)) }
    var wrongAnswerPenalty by remember { mutableStateOf(preferences.getString("wrong_answer_penalty", "half") ?: "half") }
    var penaltyMenuExpanded by remember { mutableStateOf(false) }
    var pinStored by remember { mutableStateOf(preferences.getString("parent_pin", "1234") ?: "1234") }
    var newPinDraft by remember { mutableStateOf("") }
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
    var wordExerciseTarget by remember { mutableStateOf<PairItem?>(null) }
    var wordExerciseLetterOrder by remember { mutableStateOf(emptyList<Int>()) }
    var wordExercisePlaced by remember { mutableStateOf(emptyList<Int>()) }
    var wordExerciseWrongAnswers by remember { mutableIntStateOf(0) }
    var wordExerciseMessage by remember { mutableStateOf("") }
    var wordSelectedDifficulty by remember { mutableStateOf("Fácil") }
    var wordStartMessage by remember { mutableStateOf("") }
    var wordCategoryCandidates by remember { mutableStateOf(emptyList<String>()) }

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
                savePairs(preferences, mergedPairs)
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
                wordExercises = nextExercises
                saveWordExercises(preferences, nextExercises)
                val nextCategories = if (wordPendingImportMode == "replace") imported.categories else (wordExerciseCategories + imported.categories).distinctBy { it.lowercase() }
                wordExerciseCategories = nextCategories
                preferences.edit().putString("letter_word_categories_v1", nextCategories.joinToString("|")).apply()
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
                saveSequenceExercises(preferences, nextExercises)
                preferences.edit().putStringSet("disabled_sequence_categories", nextDisabled).apply()
                "Importación completada: ${imported.exercises.size} ejercicios. ${if (sequencePendingImportMode == "replace") "Biblioteca de secuencias sustituida." else "Contenido combinado."}"
            } catch (e: Exception) { "No se pudo importar: ${e.message ?: "archivo no válido"}" }
        }
    }

    val availableExercisePairs = pairs.filter {
        it.active && it.difficulty == difficulty &&
            (mode != "Elegir" || it.category.equals(selectedExerciseCategory, ignoreCase = true))
    }
    val exercisePairs = if (screen == "exercise" || screen == "result") activeExercisePairs else availableExercisePairs.take(questionCount)
    val pairCount = exercisePairs.size
    val rewardSeconds = when (difficulty) { "Normal" -> normalRewardMinutes * 60; "Difícil" -> hardRewardMinutes * 60; else -> easyRewardMinutes * 60 }
    val balanceMinutes = balanceSeconds / 60
    val balanceRemainderSeconds = balanceSeconds % 60

    BackHandler(enabled = screen != "home" && screen != "play-time") {
        when (screen) {
            "parent" -> when (parentSection) {
                "word-library", "word-categories" -> {
                    if (parentSection == "word-library" && wordLibraryPage == "edit") { wordLibraryPage = "list"; wordLibraryMessage = "" }
                    else parentSection = "words-settings"
                }
                "words", "categories" -> parentSection = "cards-settings"
                "cards-settings", "sequences-settings", "words-settings" -> parentSection = "game-modes"
                "active-games", "music", "rewards", "security", "device-setup", "about" -> parentSection = "menu"
                "game-modes" -> parentSection = "menu"
                "sequence-type-temporal", "sequence-type-procesos", "sequence-type-causa", "sequence-type-ciclos" -> parentSection = "sequences-settings"
                "sequence-library", "sequence-categories" -> parentSection = "sequences-settings"
                "sequence-library-temporal", "sequence-categories-temporal" -> parentSection = "sequence-type-temporal"
                "sequence-editor-temporal" -> parentSection = "sequence-library"
                "sequence-library-procesos", "sequence-categories-procesos" -> parentSection = "sequence-type-procesos"
                "sequence-library-causa", "sequence-categories-causa" -> parentSection = "sequence-type-causa"
                "sequence-library-ciclos", "sequence-categories-ciclos" -> parentSection = "sequence-type-ciclos"
                else -> { screen = "home"; parentSection = "menu" }
            }
            "cards-options" -> { screen = "home"; cardsStartMessage = "" }
            "sequences" -> { screen = "home" }
            "sequence-difficulty" -> { screen = "sequences" }
            "category-select" -> { screen = "cards-options"; cardsStartMessage = "" }
            "word-options" -> { screen = "home"; wordStartMessage = "" }
            "word-category-select" -> { screen = "word-options"; wordStartMessage = "" }
            "word-exercise" -> { screen = "word-options"; wordExerciseTarget = null; wordExercisePlaced = emptyList(); wordStartMessage = "" }
            "sequences" -> { screen = "home" }
            "sequence-difficulty" -> { screen = "sequences" }
            "sequence-game" -> { screen = "sequence-difficulty"; sequenceSelectedOrder = emptyList(); sequencePlayMessage = "" }
            "parent-pin", "exit-pin" -> { screen = "home"; pinEntry = ""; pinError = false }
            "exercise" -> { screen = "home"; resolved = emptySet(); selected = emptyMap(); selectedPicture = ""; evaluatedPictures = emptyMap(); correct = 0; feedback = "" }
            "result" -> { screen = "home"; resolved = emptySet(); selected = emptyMap(); selectedPicture = ""; evaluatedPictures = emptyMap(); correct = 0; feedback = ""; lastEarnedSeconds = 0 }
            else -> { screen = "home"; parentSection = "menu" }
        }
    }

    fun startWordExercise(category: String?) {
        val eligible = wordExercises.filter {
            it.active &&
            it.category !in disabledWordExerciseCategories &&
            it.difficulty == wordSelectedDifficulty &&
            (category == null || it.category == category)
        }
        if (eligible.isEmpty()) {
            wordExerciseTarget = null
            wordStartMessage = if (category == null) {
                "No hay ejercicios activos de Palabras con dificultad $wordSelectedDifficulty. Prueba otra dificultad o crea/activa ejercicios en Configuración > Palabras."
            } else {
                "No hay ejercicios activos de la categoría $category con dificultad $wordSelectedDifficulty. Prueba otra dificultad o elige otra categoría."
            }
            return
        }
        val chosen = eligible.random()
        wordExerciseTarget = chosen
        wordExerciseLetterOrder = chosen.word.indices.toList().shuffled()
        if (chosen.word.length > 1 && wordExerciseLetterOrder == chosen.word.indices.toList()) {
            wordExerciseLetterOrder = wordExerciseLetterOrder.drop(1) + wordExerciseLetterOrder.first()
        }
        wordExercisePlaced = emptyList()
        wordExerciseWrongAnswers = 0
        wordExerciseMessage = ""
        wordStartMessage = ""
        screen = "word-exercise"
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
        activeExercisePairs = chooseAdaptivePairs(candidates, questionCount, learningStats, learningTurn)
        activeExercisePairs.forEach { pair ->
            learningTurn += 1
            val state = learningStats[pair.word] ?: WordLearning()
            learningStats = learningStats + (pair.word to state.copy(lastShownTurn = learningTurn))
        }
        preferences.edit().putInt("learning_turn_v1", learningTurn).apply()
        saveLearningStats(preferences, learningStats)
        resolved = emptySet(); selected = emptyMap(); selectedPicture = ""; evaluatedPictures = emptyMap()
        correct = 0; feedback = ""; screen = "exercise"
    }

    androidx.compose.runtime.LaunchedEffect(screen, appInForeground) {
        while (screen == "play-time" && appInForeground) {
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
    androidx.compose.runtime.LaunchedEffect(appInForeground) {
        if (appInForeground) {
            // Fallback for devices where ACTION_USER_PRESENT is missed: resuming
            // the app proves the screen is unlocked, so restart the saved countdown.
            if (preferences.getBoolean("play_paused_for_screen_lock", false)) {
                val remaining = preferences.getInt("balance_seconds", 0).coerceAtLeast(0)
                preferences.edit()
                    .putBoolean("play_paused_for_screen_lock", false)
                    .putLong("play_deadline_ms", if (remaining > 0) System.currentTimeMillis() + remaining.toLong() * 1000L else 0L)
                    .commit()
                if (remaining > 0) {
                    androidx.core.content.ContextCompat.startForegroundService(
                        context,
                        Intent(context, PlayTimeService::class.java).setAction(PlayTimeService.ACTION_START)
                    )
                    balanceSeconds = remaining
                }
            }
            val deadline = preferences.getLong("play_deadline_ms", 0L)
            if (deadline > System.currentTimeMillis() && screen == "home") {
                screen = "play-time"
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
                appInForeground && (screen == "parent-pin" || screen == "parent" || screen == "exit-pin")
            ).apply()
        }
    }

    if (screen == "exit-pin") {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒 Salir de la aplicación", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
            Spacer(Modifier.height(8.dp))
            Text("Introduce el PIN familiar para confirmar la salida.", fontSize = 17.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = pinEntry, onValueChange = { value -> pinEntry = value.filter { it.isDigit() }.take(4); pinError = false }, label = { Text("PIN de 4 cifras") }, singleLine = true)
            if (pinError) Text("PIN incorrecto. Prueba otra vez.", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
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
            }, modifier = Modifier.fillMaxWidth()) { Text("Confirmar salida") }
            OutlinedButton(onClick = { screen = "home"; pinEntry = ""; pinError = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        }
    } else if (screen == "parent-pin") {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔐 Zona de familias", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
            Spacer(Modifier.height(8.dp))
            Text("Introduce el PIN para acceder a los controles.", fontSize = 17.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = pinEntry, onValueChange = { value -> pinEntry = value.filter { it.isDigit() }.take(4); pinError = false }, label = { Text("PIN de 4 cifras") }, singleLine = true)
            if (pinError) Text("PIN incorrecto. Prueba otra vez.", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                if (pinEntry == pinStored) { screen = "parent"; parentSection = "menu"; pinEntry = ""; pinError = false }
                else pinError = true
            }, modifier = Modifier.fillMaxWidth()) { Text("Entrar") }
            OutlinedButton(onClick = { screen = "home"; pinEntry = ""; pinError = false }, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
            Text("Introduce el PIN familiar.", fontSize = 12.sp, color = Color(0xFF667085))
        }
    } else if (screen == "parent") {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Control parental  ›  ${if (parentSection == "menu") "Menú principal" else when (parentSection) { "game-modes" -> "Configurar modos de juego"; "active-games" -> "Juegos activos"; "cards-settings" -> "Tarjetas"; "sequences-settings" -> "Secuencias"; "words-settings" -> "Palabras"; "words" -> "Biblioteca de palabras"; "categories" -> "Categorías"; "rewards" -> "Recompensas y límites"; "music" -> "Música de fondo"; "security" -> "Cambiar PIN"; "device-setup" -> "Configuración del dispositivo"; "about" -> "Acerca de esta app"; else -> "Configuración" }}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF667085))
                Text(if (parentSection == "menu") "Control parental" else when (parentSection) { "game-modes" -> "Configurar modos de juego"; "active-games" -> "Juegos activos"; "cards-settings" -> "Tarjetas"; "words" -> "Biblioteca de palabras"; "categories" -> "Categorías"; "rewards" -> "Recompensas y límites"; "music" -> "Música de fondo"; "security" -> "Cambiar PIN"; "device-setup" -> "Configuración del dispositivo"; "about" -> "Acerca de esta app"; else -> "Configuración" }, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                Text(if (parentSection == "menu") "Configura el contenido y las opciones de la aplicación." else "Control parental · ${when (parentSection) { "game-modes" -> "Elige una modalidad para configurar"; "active-games" -> "Activa o desactiva las modalidades disponibles para el periodo de pruebas"; "cards-settings" -> "Opciones específicas del modo Tarjetas"; "sequences-settings" -> "Opciones del modo Secuencias"; "words-settings" -> "Opciones del modo Palabras"; "words" -> "Gestiona palabras e imágenes"; "categories" -> "Organiza el vocabulario"; "rewards" -> "Configura tiempos, recompensas y límites"; "music" -> "Configura la música y el sonido"; "security" -> "Protege el acceso y administra el saldo"; "device-setup" -> "Permisos y accesos necesarios para la protección"; "about" -> "Información, propósito y créditos"; else -> "Configuración" }}", fontSize = 15.sp, color = Color(0xFF667085))
            }
            if (parentSection == "menu") {
                item {
                    Card(Modifier.fillMaxWidth().clickable { parentSection = "game-modes" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🎮", fontSize = 30.sp)
                            Column { Text("Configurar modos de juego", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Opciones específicas de cada modalidad", color = Color(0xFF667085)) }
                        }
                    }
                }
                item { Text("CONFIGURACIÓN GENERAL", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF667085)) }
                item {
                    Card(Modifier.fillMaxWidth().clickable { parentSection = "active-games" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🎮", fontSize = 30.sp)
                            Column { Text("Juegos activos", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Activa o bloquea modalidades durante las pruebas", color = Color(0xFF667085)) }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth().clickable { parentSection = "rewards" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("⏱️", fontSize = 30.sp)
                            Column { Text("Recompensas y límites", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Minutos de recompensa por dificultad", color = Color(0xFF667085)) }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth().clickable { parentSection = "music" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🎵", fontSize = 30.sp)
                            Column { Text("Música de fondo", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Elige un MP3 y configura el sonido", color = Color(0xFF667085)) }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth().clickable { parentSection = "security" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🔐", fontSize = 30.sp)
                            Column { Text("Cambiar PIN", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Modificar el PIN de acceso familiar", color = Color(0xFF667085)) }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth().clickable { parentSection = "about" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("ℹ️ Acerca de esta app", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF253344))
                            Text("Propósito, créditos, versión y repositorio compartido.", fontSize = 14.sp, color = Color(0xFF667085))
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth().clickable { parentSection = "device-setup" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("⚙️", fontSize = 30.sp)
                            Column { Text("Configuración del dispositivo", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Permisos y servicio de accesibilidad", color = Color(0xFF667085)) }
                        }
                    }
                }
            } else {
                item { OutlinedButton(onClick = { parentSection = when (parentSection) { "game-modes" -> "menu"; "active-games", "music", "rewards", "security", "device-setup", "about" -> "menu"; "cards-settings", "sequences-settings", "words-settings" -> "game-modes"; "sequence-type-temporal", "sequence-type-procesos", "sequence-type-causa", "sequence-type-ciclos" -> "sequences-settings"; "sequence-editor-temporal" -> "sequence-library"; "sequence-library", "sequence-categories" -> "sequences-settings"; "sequence-library-temporal", "sequence-categories-temporal" -> "sequence-type-temporal"; "sequence-library-procesos", "sequence-categories-procesos" -> "sequence-type-procesos"; "sequence-library-causa", "sequence-categories-causa" -> "sequence-type-causa"; "sequence-library-ciclos", "sequence-categories-ciclos" -> "sequence-type-ciclos"; "words", "categories" -> "cards-settings"; "word-library", "word-categories" -> "words-settings"; else -> "menu" } }) { Text(if (parentSection == "game-modes") "← Volver al menú de control" else if (parentSection == "cards-settings" || parentSection == "sequences-settings" || parentSection == "words-settings") "← Volver a los modos de juego" else if (parentSection.startsWith("sequence-type-")) "← Volver a Secuencias" else if (parentSection == "sequence-editor-temporal") "← Volver a la biblioteca" else if (parentSection == "sequence-library" || parentSection == "sequence-categories") "← Volver a Secuencias" else if (parentSection.startsWith("sequence-library-") || parentSection.startsWith("sequence-categories-")) "← Volver al tipo de secuencia" else if (parentSection == "words" || parentSection == "categories") "← Volver a Tarjetas" else if (parentSection == "word-library" || parentSection == "word-categories") "← Volver a Palabras" else "← Volver al menú de control") } }
                when (parentSection) {
                    "active-games" -> {
                        item { Text("Juegos activos", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
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
                    }
                    "game-modes" -> {
                        item { Text("Modos de juego", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
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
                            Text("Formato del contenido", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Elige cómo se mostrarán las tarjetas al niño. Cada ejercicio seguirá guardando texto e imagen.", fontSize = 14.sp, color = Color(0xFF667085))
                            listOf("Imagen y texto", "Solo imágenes", "Solo texto").forEach { format ->
                                OutlinedButton(
                                    onClick = {
                                        sequenceContentFormat = format
                                        preferences.edit().putString("sequence_content_format_v1", format).apply()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (sequenceContentFormat == format) "✓ $format" else format) }
                            }
                        }
                        item {
                            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Máximo de tarjetas por secuencia", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("El niño solo verá ejercicios que no superen el límite seleccionado.", fontSize = 14.sp, color = Color(0xFF667085))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                (2..6).forEach { limit ->
                                    OutlinedButton(
                                        onClick = {
                                            sequenceMaxCards = limit
                                            preferences.edit().putInt("sequence_max_cards_v1", limit).apply()
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                    ) { Text(if (sequenceMaxCards == limit) "✓ $limit" else "$limit") }
                                }
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
                            exercise.type != "Letras" &&
                            (sequenceFilterType == "Todos" || exercise.type == sequenceFilterType) &&
                            (sequenceFilterCategory == "Todas" || exercise.category == sequenceFilterCategory) &&
                            (sequenceFilterDifficulty == "Todas" || exercise.difficulty == sequenceFilterDifficulty) &&
                            (sequenceFilterStatus == "Todos" || if (sequenceFilterStatus == "Activos") exercise.active else !exercise.active)
                        }
                        item { Text("Biblioteca de secuencias", fontSize = 23.sp, fontWeight = FontWeight.Bold) }
                        item { Text("Control parental · Gestiona ejercicios, categorías y su disponibilidad.", fontSize = 14.sp, color = Color(0xFF667085)) }
                        item {
                            Button(onClick = {
                                sequenceEditingIndex = -1; sequenceTitleDraft = ""; sequenceFormatDraft = "Imagen y texto"
                                sequenceCategoryDraft = ""; sequenceTypeDraft = "Orden temporal"; sequenceDifficultyDraft = "Normal"
                                sequenceCardsDraft = listOf("", "", ""); sequenceCardPicturesDraft = listOf("", "", "")
                                sequenceAlternativeDraft = ""; sequenceEditorMessage = ""; parentSection = "sequence-editor-temporal"
                            }, modifier = Modifier.fillMaxWidth()) { Text("＋ Crear ejercicio") }
                        }
                        item {
                            Text("Copia de seguridad y traslado", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Exporta e importa únicamente ejercicios, categorías, dificultad, estado y las imágenes de secuencias. No modifica la biblioteca de palabras.", fontSize = 13.sp, color = Color(0xFF667085))
                            OutlinedButton(onClick = {
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                exportSequencePackageLauncher.launch("aprender-para-jugar-secuencias.zip")
                            }, modifier = Modifier.fillMaxWidth()) { Text("📦 Exportar secuencias a ZIP") }
                            OutlinedButton(onClick = {
                                sequencePendingImportMode = "replace"
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                importSequencePackageLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            }, modifier = Modifier.fillMaxWidth()) { Text("♻️ Importar y sustituir secuencias") }
                            OutlinedButton(onClick = {
                                sequencePendingImportMode = "merge"
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                importSequencePackageLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            }, modifier = Modifier.fillMaxWidth()) { Text("➕ Importar y combinar secuencias") }
                            if (sequencePackageMessage.isNotBlank()) Text(sequencePackageMessage, fontSize = 14.sp, color = if (sequencePackageMessage.startsWith("No se pudo")) Color(0xFFB3261E) else Color(0xFF24733B), fontWeight = FontWeight.SemiBold)
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
                                            sequenceEditingIndex = index; sequenceTitleDraft = exercise.title; sequenceFormatDraft = exercise.format
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
                                                saveSequenceExercises(preferences, sequenceExercises)
                                            })
                                            Text(if (exercise.active) "Activo" else "Inactivo", fontSize = 11.sp)
                                            OutlinedButton(onClick = {
                                                sequenceExercises = sequenceExercises.filterIndexed { currentIndex, _ -> currentIndex != index }
                                                saveSequenceExercises(preferences, sequenceExercises)
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
                                onValueChange = { sequenceNewCategoryDraft = it; sequenceCategoryMessage = "" },
                                label = { Text("Nueva categoría") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(onClick = {
                                val name = sequenceNewCategoryDraft.trim()
                                when {
                                    name.isBlank() -> sequenceCategoryMessage = "Escribe un nombre para la categoría."
                                    sequenceCategories.any { it.equals(name, ignoreCase = true) } -> sequenceCategoryMessage = "Ya existe una categoría con ese nombre."
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
                                                saveSequenceExercises(preferences, sequenceExercises)
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
                                                    sequenceExercises = sequenceExercises.map {
                                                        if (it.category.equals(category, ignoreCase = true)) it.copy(category = destination) else it
                                                    }
                                                    saveSequenceExercises(preferences, sequenceExercises)
                                                    sequenceCategoryNames = sequenceCategoryNames.filterNot { it.equals(category, ignoreCase = true) }
                                                    preferences.edit().putString("sequence_categories_v1", sequenceCategoryNames.joinToString("|")).apply()
                                                    disabledSequenceCategories = disabledSequenceCategories.filterNot { it.equals(category, ignoreCase = true) }.toSet()
                                                    preferences.edit().putStringSet("disabled_sequence_categories", disabledSequenceCategories).apply()
                                                    sequenceCategoryToDelete = null
                                                    sequenceCategoryMessage = "Categoría eliminada; ejercicios movidos a $destination."
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
                        item { OutlinedTextField(value = sequenceTitleDraft, onValueChange = { sequenceTitleDraft = it }, label = { Text("Nombre del ejercicio") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                        item {
                            Text("Tipo de secuencia", fontWeight = FontWeight.Bold)
                            listOf("Orden temporal", "Procesos", "Causa y consecuencia", "Ciclos").forEach { type ->
                                OutlinedButton(onClick = {
                                    sequenceTypeDraft = type
                                    if (type == "Letras") {
                                        sequenceCardsDraft = listOf(sequenceCardsDraft.firstOrNull().orEmpty())
                                        sequenceCardPicturesDraft = listOf(sequenceCardPicturesDraft.firstOrNull().orEmpty())
                                        sequenceAlternativeDraft = ""
                                        sequenceFormatDraft = "Imagen y texto"
                                    } else if (sequenceCardsDraft.size < 2) {
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
                            Text(if (sequenceTypeDraft == "Letras") "Palabra e imagen" else "Tarjetas en el orden correcto", fontWeight = FontWeight.Bold)
                            Text(if (sequenceTypeDraft == "Letras") "Escribe la palabra que el niño tendrá que formar. Se utilizará una única imagen como pista." else "Edita los pasos en su orden correcto. Máximo 6 tarjetas.", fontSize = 13.sp, color = Color(0xFF667085))
                        }
                        sequenceCardsDraft.forEachIndexed { index, card ->
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (sequenceTypeDraft != "Letras") Text("${index + 1}.", fontWeight = FontWeight.Bold)
                                        OutlinedTextField(value = card, onValueChange = { updated -> sequenceCardsDraft = sequenceCardsDraft.toMutableList().also { it[index] = updated } }, label = { Text(if (sequenceTypeDraft == "Letras") "Palabra objetivo" else "Paso ${index + 1}") }, modifier = Modifier.weight(1f), singleLine = true)
                                        if (sequenceTypeDraft != "Letras") OutlinedButton(onClick = {
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
                                if (sequenceCardsDraft.size < 6 && sequenceTypeDraft != "Letras") {
                                    sequenceCardsDraft = sequenceCardsDraft + ""
                                    sequenceCardPicturesDraft = sequenceCardPicturesDraft + ""
                                }
                            }, enabled = sequenceCardsDraft.size < 6 && sequenceTypeDraft != "Letras", modifier = Modifier.fillMaxWidth()) { Text("＋ Añadir tarjeta (${sequenceCardsDraft.size}/6)") }
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
                                    sequenceTitleDraft.isBlank() -> sequenceEditorMessage = "Escribe un nombre para el ejercicio."
                                    sequenceCategoryDraft.isBlank() -> sequenceEditorMessage = "Crea o selecciona una categoría antes de guardar el ejercicio."
                                    (if (sequenceTypeDraft == "Letras") cleanCards.size != 1 else cleanCards.size < 2) || cleanCards.any { it.isBlank() } -> sequenceEditorMessage = if (sequenceTypeDraft == "Letras") "Escribe la palabra que se formará con las letras." else "Completa al menos 2 tarjetas; no puede haber pasos vacíos."
                                    cleanCards.size > 6 -> sequenceEditorMessage = "El máximo es de 6 tarjetas."
                                     sequenceTypeDraft != "Letras" && sequenceCardPicturesDraft.take(cleanCards.size).any { it.isBlank() } -> sequenceEditorMessage = "Selecciona una imagen para cada tarjeta; todos los ejercicios deben tener texto e imagen."
                                    sequenceTypeDraft != "Letras" && alternativeLines.isNotEmpty() && invalidAlternative -> sequenceEditorMessage = "Cada alternativa debe incluir todos los números del 1 al ${cleanCards.size}, sin repetir y separados por comas."
                                    else -> {
                                        val alternatives = parsedAlternatives.map { indexes -> indexes.filterNotNull().map { cleanCards[it - 1] }.joinToString("|") }
                                        val cleanPictures = cleanCards.indices.map { sequenceCardPicturesDraft.getOrNull(it).orEmpty() }
                                        val exercise = SequenceExercise(sequenceTitleDraft.trim(), cleanCards, sequenceFormatDraft, sequenceCategoryDraft.trim(), alternatives, cleanPictures, sequenceTypeDraft, sequenceDifficultyDraft, sequenceExercises.getOrNull(sequenceEditingIndex)?.active ?: true)
                                        val updatedExercises = sequenceExercises.toMutableList().also {
                                            if (sequenceEditingIndex < 0) it.add(exercise) else it[sequenceEditingIndex] = exercise
                                        }
                                        sequenceExercises = updatedExercises
                                        saveSequenceExercises(preferences, updatedExercises)
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
                            item {
                                Text("Copia de seguridad y traslado", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit(); exportWordPackageLauncher.launch("aprender-para-jugar-palabras.zip") }, modifier = Modifier.fillMaxWidth()) { Text("📦 Exportar palabras a ZIP") }
                                OutlinedButton(onClick = { wordPendingImportMode = "replace"; preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit(); importWordPackageLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("♻️ Importar y sustituir palabras") }
                                OutlinedButton(onClick = { wordPendingImportMode = "merge"; preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit(); importWordPackageLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("➕ Importar y combinar palabras") }
                                if (wordPackageMessage.isNotBlank()) Text(wordPackageMessage, color = if (wordPackageMessage.startsWith("No se pudo")) Color(0xFFB3261E) else Color(0xFF24733B), fontSize = 13.sp)
                            }
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
                                                OutlinedButton(onClick = { wordExercises = wordExercises.toMutableList().also { it[index] = exercise.copy(active = !exercise.active) }; saveWordExercises(preferences, wordExercises) }, modifier = Modifier.weight(1f)) { Text(if (exercise.active) "Desactivar" else "Activar") }
                                                OutlinedButton(onClick = { wordExercises = wordExercises.filterIndexed { i, _ -> i != index }; saveWordExercises(preferences, wordExercises) }, modifier = Modifier.weight(1f)) { Text("Eliminar") }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            item { OutlinedTextField(value = wordLibraryWordDraft, onValueChange = { wordLibraryWordDraft = it }, label = { Text("Palabra que formará") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
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
                                            val entry = PairItem(cleanWord, wordLibraryPictureDraft.trim(), wordLibraryCategoryDraft, wordLibraryDifficultyDraft, true)
                                            wordExercises = if (wordLibraryEditingIndex < 0) wordExercises + entry else wordExercises.toMutableList().also { it[wordLibraryEditingIndex] = entry }
                                            saveWordExercises(preferences, wordExercises)
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
                        item { OutlinedTextField(value = wordNewCategoryDraft, onValueChange = { wordNewCategoryDraft = it; wordLibraryMessage = "" }, label = { Text("Nueva categoría") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                        item { Button(onClick = {
                            val name = wordNewCategoryDraft.trim()
                            when {
                                name.isBlank() -> wordLibraryMessage = "Escribe un nombre."
                                wordExerciseCategories.any { it.equals(name, true) } -> wordLibraryMessage = "Esa categoría ya existe."
                                else -> { wordExerciseCategories = wordExerciseCategories + name; preferences.edit().putString("letter_word_categories_v1", wordExerciseCategories.joinToString("|")).apply(); wordNewCategoryDraft = ""; wordLibraryMessage = "Categoría creada." }
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
                                                disabledWordExerciseCategories = if (enabled) disabledWordExerciseCategories - category else disabledWordExerciseCategories + category
                                                preferences.edit().putStringSet("disabled_letter_word_categories_v1", disabledWordExerciseCategories).apply()
                                            })
                                        }
                                        if (wordCategoryToDelete == category) {
                                            Text("Borrar categoría y contenido", fontWeight = FontWeight.Bold)
                                            Text("Se eliminarán también los ${wordExercises.count { it.category == category }} ejercicios asignados.", color = Color(0xFFB3261E))
                                            Button(onClick = { wordExercises = wordExercises.filterNot { it.category == category }; saveWordExercises(preferences, wordExercises); wordExerciseCategories = wordExerciseCategories.filterNot { it == category }; preferences.edit().putString("letter_word_categories_v1", wordExerciseCategories.joinToString("|")).apply(); wordCategoryToDelete = null }, modifier = Modifier.fillMaxWidth()) { Text("Borrar categoría y contenido") }
                                            Text("Borrar categoría pero mover contenido a:", fontWeight = FontWeight.Bold)
                                            wordExerciseCategories.filterNot { it == category }.forEach { destination ->
                                                OutlinedButton(onClick = { wordExercises = wordExercises.map { if (it.category == category) it.copy(category = destination) else it }; saveWordExercises(preferences, wordExercises); wordExerciseCategories = wordExerciseCategories.filterNot { it == category }; preferences.edit().putString("letter_word_categories_v1", wordExerciseCategories.joinToString("|")).apply(); wordCategoryToDelete = null }, modifier = Modifier.fillMaxWidth()) { Text(destination) }
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
                                    Column { Text("Biblioteca de palabras", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Palabras, imágenes, dificultad y estado", color = Color(0xFF667085)) }
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
                            Text("Biblioteca de palabras", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Gestiona las palabras guardadas. Pulsa Añadir para crear una nueva.", fontSize = 14.sp, color = Color(0xFF667085))
                            Button(onClick = { editingWord = null; wordDraft = ""; pictureDraft = ""; categoryDraft = "Primeras palabras"; difficultyDraft = "Fácil"; libraryMessage = ""; libraryPage = "edit" }, modifier = Modifier.fillMaxWidth()) { Text("＋ Añadir palabra") }
                            Spacer(Modifier.height(8.dp))
                            Text("Copia de seguridad y traslado", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Exporta categorías, palabras, dificultad, estado e imágenes en un ZIP. El PIN y el saldo no se incluyen.", fontSize = 13.sp, color = Color(0xFF667085))
                            OutlinedButton(onClick = {
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                exportPackageLauncher.launch("aprender-para-jugar-contenido.zip")
                            }, modifier = Modifier.fillMaxWidth()) { Text("📦 Exportar contenido a ZIP") }
                            OutlinedButton(onClick = {
                                pendingImportMode = "replace"
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                importPackageLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            }, modifier = Modifier.fillMaxWidth()) { Text("♻️ Importar y sustituir biblioteca") }
                            OutlinedButton(onClick = {
                                pendingImportMode = "merge"
                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                importPackageLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            }, modifier = Modifier.fillMaxWidth()) { Text("➕ Importar y combinar contenido") }
                            if (packageMessage.isNotBlank()) Text(packageMessage, fontSize = 14.sp, color = if (packageMessage.startsWith("No se pudo")) Color(0xFFB3261E) else Color(0xFF24733B), fontWeight = FontWeight.SemiBold)

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
                                            OutlinedButton(onClick = { pairs = pairs.map { if (it.word == pair.word) it.copy(active = !it.active) else it }; savePairs(preferences, pairs) }, modifier = Modifier.weight(1.2f)) { Text(if (pair.active) "Desactivar" else "Activar", maxLines = 1, fontSize = 13.sp) }
                                            OutlinedButton(onClick = { pairs = pairs.filterNot { it.word == pair.word }; savePairs(preferences, pairs) }, modifier = Modifier.weight(1f)) { Text("Eliminar", maxLines = 1, fontSize = 13.sp) }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(if (editingWord == null) "Añadir palabra" else "Editar palabra", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Completa los datos y guarda. La imagen se elige desde la galería.", fontSize = 14.sp, color = Color(0xFF667085))
                            OutlinedTextField(value = wordDraft, onValueChange = { wordDraft = it }, label = { Text("Palabra") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
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
                                if (clean.isBlank() || pictureDraft.isBlank() || categoryDraft.isBlank()) libraryMessage = "Completa la palabra, la categoría y elige una imagen o emoji."
                                else if (pairs.any { it.word.equals(clean, ignoreCase = true) && it.word != editingWord }) libraryMessage = "Esa palabra ya existe."
                                else {
                                    pairs = if (editingWord == null) pairs + PairItem(clean, pictureDraft.trim(), categoryDraft.trim(), difficultyDraft) else pairs.map { if (it.word == editingWord) it.copy(word = clean, picture = pictureDraft.trim(), category = categoryDraft.trim(), difficulty = difficultyDraft) else it }
                                    savePairs(preferences, pairs)
                                    wordDraft = ""; pictureDraft = ""; categoryDraft = "Primeras palabras"; difficultyDraft = "Fácil"; editingWord = null; libraryMessage = "Palabra guardada."; libraryPage = "list"
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar palabra") }
                            OutlinedButton(onClick = { libraryPage = "list"; editingWord = null; libraryMessage = "" }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar y volver a la biblioteca") }
                            if (libraryMessage.isNotBlank()) Text(libraryMessage, color = Color(0xFF24733B))
                        }
                    }
                    "categories" -> item {
                        Text("Categorías", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Activa o desactiva categorías completas sin borrar sus palabras. También puedes mover o eliminar categorías.", fontSize = 14.sp, color = Color(0xFF667085))
                        OutlinedTextField(value = categoryDraftName, onValueChange = { categoryDraftName = it }, label = { Text("Nueva categoría") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = { val clean = categoryDraftName.trim(); if (clean.isBlank()) categoryMessage = "Escribe un nombre."; else if (categories.any { it.equals(clean, ignoreCase = true) }) categoryMessage = "Esa categoría ya existe."; else { categoryNames = categoryNames + clean; preferences.edit().putString("categories", categoryNames.joinToString("|")).apply(); categoryDraftName = ""; categoryMessage = "Categoría creada." } }, modifier = Modifier.fillMaxWidth()) { Text("Añadir categoría") }
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
                                            savePairs(preferences, pairs)
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
                                            savePairs(preferences, pairs)
                                            categoryNames = categoryNames.filterNot { it.equals(name, ignoreCase = true) }
                                            preferences.edit().putString("categories", categoryNames.joinToString("|")).apply()
                                            categoryToRemove = ""
                                            categoryMessage = "Categoría y palabras eliminadas."
                                        }, modifier = Modifier.fillMaxWidth()) { Text("Borrar categoría y contenido") }
                                        Text("Borrar categoría pero mover contenido a:", fontWeight = FontWeight.SemiBold)
                                        categories.filterNot { it.equals(name, ignoreCase = true) }.forEach { destination ->
                                            OutlinedButton(onClick = {
                                                pairs = pairs.map { if (it.category.equals(name, ignoreCase = true)) it.copy(category = destination) else it }
                                                savePairs(preferences, pairs)
                                                categoryNames = categoryNames.filterNot { it.equals(name, ignoreCase = true) }
                                                preferences.edit().putString("categories", categoryNames.joinToString("|")).apply()
                                                categoryToRemove = ""
                                                categoryMessage = "Categoría eliminada; palabras movidas a $destination."
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
                        Text("Saldo de juego", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Saldo actual: ${balanceSeconds / 60} min ${balanceSeconds % 60} s", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = {
                                val updated = (balanceSeconds + 60).coerceAtMost(24 * 60 * 60)
                                balanceSeconds = updated
                                preferences.edit().putInt("balance_seconds", updated).apply()
                            }, enabled = balanceSeconds < 24 * 60 * 60) { Text("+1 minuto") }
                            OutlinedButton(onClick = {
                                val updated = (balanceSeconds + 5 * 60).coerceAtMost(24 * 60 * 60)
                                balanceSeconds = updated
                                preferences.edit().putInt("balance_seconds", updated).apply()
                            }, enabled = balanceSeconds < 24 * 60 * 60) { Text("+5 minutos") }
                            OutlinedButton(onClick = {
                                balanceSeconds = 0
                                preferences.edit().putInt("balance_seconds", 0).putLong("play_deadline_ms", 0L).putLong("expiry_last_launch_ms", 0L).putBoolean("play_session_authorized", false).apply()
                                context.stopService(Intent(context, PlayTimeService::class.java))
                            }, enabled = balanceSeconds > 0) { Text("Poner a cero") }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Recompensas y límites", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Cada bloque agrupa una opción concreta. Los cambios se guardan automáticamente.", fontSize = 14.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("1. Acceso al tiempo de juego", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Tiempo mínimo de saldo necesario para iniciar una nueva sesión.", fontSize = 14.sp, color = Color(0xFF667085))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = {
                                minimumPlayMinutes = (minimumPlayMinutes - 1).coerceAtLeast(0)
                                preferences.edit().putInt("minimum_play_minutes", minimumPlayMinutes).apply()
                            }, enabled = minimumPlayMinutes > 0) { Text("−") }
                            Text("$minimumPlayMinutes min", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = {
                                minimumPlayMinutes = (minimumPlayMinutes + 1).coerceAtMost(60)
                                preferences.edit().putInt("minimum_play_minutes", minimumPlayMinutes).apply()
                            }, enabled = minimumPlayMinutes < 60) { Text("+") }
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
                        Text(when (wrongAnswerPenalty) { "none" -> "Los errores no reducen la recompensa de esta partida."; "full" -> "Cada respuesta incorrecta resta el equivalente al tiempo que vale un acierto."; else -> "Cada respuesta incorrecta resta la mitad del tiempo que vale un acierto." }, fontSize = 14.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text("3. Recompensa por dificultad", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Tiempo que se añade al saldo según la dificultad del ejercicio.", fontSize = 14.sp, color = Color(0xFF667085))
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
                                        Text("$minutes min", fontWeight = FontWeight.Bold)
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
                        setupAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (setupAccessibilityEnabled) Color(0xFFE5F5E9) else Color(0xFFF0EDF7))) {
                            Text(if (setupAccessibilityEnabled) "✓ COMPLETADO · Servicio de accesibilidad activado" else "○ PENDIENTE · Activa el servicio de accesibilidad", modifier = Modifier.padding(12.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (setupAccessibilityEnabled) Color(0xFF24733B) else Color(0xFF6B5AA6))
                        }
                        Text("Para que la protección de la aplicación funcione, Android requiere que una persona autorice manualmente el servicio de accesibilidad. Esta guía te llevará a los ajustes necesarios.", fontSize = 15.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
                    "about" -> item {
                        Text("Acerca de esta app", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Aprender para jugar", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3567A8))
                        Text("Creado por Miguel Ponce Torres", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Desarrollado con ayuda de inteligencia artificial (IA), como herramienta de apoyo en el diseño, la programación y la resolución de problemas.", fontSize = 14.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Versión 0.3.22", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Fecha de versión: 29 de octubre de 2026", fontSize = 14.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Propósito", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Aplicación educativa para que los niños practiquen vocabulario y asociación de palabras e imágenes mediante ejercicios breves. El aprendizaje se refuerza con recompensas de tiempo de juego y controles configurables por la familia.", fontSize = 14.sp, color = Color(0xFF667085))
                        Text("Repositorio compartido", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("El código fuente está disponible en GitHub para consultar el desarrollo, revisar los cambios y colaborar.", fontSize = 14.sp, color = Color(0xFF667085))
                        OutlinedButton(onClick = { uriHandler.openUri("https://github.com/Scorpyo82/Aprender-para-jugar") }, modifier = Modifier.fillMaxWidth()) { Text("Ver repositorio en GitHub ↗") }
                        Text("La aplicación se encuentra en desarrollo. Las funciones y la interfaz pueden cambiar entre versiones.", fontSize = 13.sp, color = Color(0xFF667085))
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
            item { OutlinedButton(onClick = { screen = "home"; parentSection = "menu" }, modifier = Modifier.fillMaxWidth()) { Text("Salir de configuración") } }
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
                                        val isCorrect = exercisePairs.first { it.word == item.word }.picture == pendingPicture
                                        resolved = resolved + item.word
                                        learningTurn += 1
                                        val oldLearning = learningStats[item.word] ?: WordLearning()
                                        learningStats = learningStats + (item.word to WordLearning(if (isCorrect) oldLearning.streak + 1 else 0, oldLearning.lastShownTurn, isCorrect))
                                        preferences.edit().putInt("learning_turn_v1", learningTurn).apply()
                                        saveLearningStats(preferences, learningStats)
                                        val newCorrect = correct + if (isCorrect) 1 else 0
                                        correct = newCorrect
                                        evaluatedPictures = evaluatedPictures + (pendingPicture to isCorrect)
                                        feedback = if (isCorrect) "¡Muy bien! «" + item.word + "» corresponde a ese dibujo." else "No coincide. «" + item.word + "» queda resuelta; prueba con otra palabra."
                                        selected = emptyMap()
                                        selectedPicture = ""
                                        if (resolved.size == pairCount) {
                                            val wrong = pairCount - newCorrect
                                            val netCorrectUnits = when (wrongAnswerPenalty) {
                                                "none" -> newCorrect * 2
                                                "full" -> (newCorrect * 2 - wrong * 2).coerceAtLeast(0)
                                                else -> (newCorrect * 2 - wrong).coerceAtLeast(0)
                                            }
                                            addEarnedTime((rewardSeconds * netCorrectUnits) / (pairCount * 2))
                                            screen = "result"
                                        }
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
                            Text(item.word, Modifier.padding(cardPadding), fontSize = wordCardFontSize, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, color = Color(0xFF29213B))
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
                                val isCorrect = exercisePairs.first { it.word == word }.picture == item.picture
                                resolved = resolved + word
                                learningTurn += 1
                                val oldLearning = learningStats[word] ?: WordLearning()
                                learningStats = learningStats + (word to WordLearning(if (isCorrect) oldLearning.streak + 1 else 0, oldLearning.lastShownTurn, isCorrect))
                                preferences.edit().putInt("learning_turn_v1", learningTurn).apply()
                                saveLearningStats(preferences, learningStats)
                                if (isCorrect) correct += 1
                                evaluatedPictures = evaluatedPictures + (item.picture to isCorrect)
                                feedback = if (isCorrect) "¡Muy bien! «" + word + "» corresponde a ese dibujo." else "No coincide. «" + word + "» queda resuelta; prueba con otra palabra."
                                selected = emptyMap()
                                selectedPicture = ""
                                if (resolved.size == pairCount) {
                                    val wrong = pairCount - correct
                                    val netCorrectUnits = when (wrongAnswerPenalty) {
                                        "none" -> correct * 2
                                        "full" -> (correct * 2 - wrong * 2).coerceAtLeast(0)
                                        else -> (correct * 2 - wrong).coerceAtLeast(0)
                                    }
                                    addEarnedTime((rewardSeconds * netCorrectUnits) / (pairCount * 2))
                                    resultCorrectCount = correct
                                    resultTotalCount = pairCount
                                    screen = "result"
                                }
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
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = { screen = "home"; wordStartMessage = "" }) { Text("← Volver al inicio") }
            Text("Palabras", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("Elige dificultad y cómo seleccionar la categoría.", fontSize = 17.sp, color = Color(0xFF667085))
            Text("Dificultad", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Fácil" to easyRewardMinutes, "Normal" to normalRewardMinutes, "Difícil" to hardRewardMinutes).forEach { (level, minutes) ->
                    Card(
                        Modifier.weight(1f).clickable { wordSelectedDifficulty = level; wordStartMessage = "" },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (wordSelectedDifficulty == level) Color(0xFFE8F0FE) else Color.White),
                        border = androidx.compose.foundation.BorderStroke(if (wordSelectedDifficulty == level) 2.dp else 1.dp, if (wordSelectedDifficulty == level) Color(0xFF3E6FA8) else Color(0xFFE4E0D8))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(level, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("$minutes min", fontSize = 14.sp, color = Color(0xFF667085))
                        }
                    }
                }
            }
            Card(
                Modifier.fillMaxWidth().clickable { startWordExercise(null) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0D9)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4C07B))
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("🎲", fontSize = 38.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Al azar", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7B4B16))
                        Text("Elegir una categoría disponible al azar", fontSize = 15.sp, color = Color(0xFF795F43))
                    }
                    Text("›", fontSize = 30.sp, color = Color(0xFFB7791F))
                }
            }
            Card(
                Modifier.fillMaxWidth().clickable {
                    wordCategoryCandidates = wordExercises.filter { it.active && it.category !in disabledWordExerciseCategories && it.difficulty == wordSelectedDifficulty }
                        .map { it.category }.distinct().sorted()
                    wordStartMessage = ""
                    screen = "word-category-select"
                },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F4E9)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA4D3B2))
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("🗂️", fontSize = 38.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Elegir categoría", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF286344))
                        Text("Selecciona el tema que quieres practicar", fontSize = 15.sp, color = Color(0xFF52725F))
                    }
                    Text("›", fontSize = 30.sp, color = Color(0xFF27804D))
                }
            }
            if (wordStartMessage.isNotBlank()) Text(wordStartMessage, color = Color(0xFFB3261E), fontSize = 15.sp)
        }
    } else if (screen == "word-category-select") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { screen = "word-options"; wordStartMessage = "" }) { Text("← Volver a Palabras") }
            Text("Elegir categoría", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("Dificultad seleccionada: $wordSelectedDifficulty", fontSize = 16.sp, color = Color(0xFF667085))
            val availableWordCategories = wordExercises.filter { it.active && it.category !in disabledWordExerciseCategories && it.difficulty == wordSelectedDifficulty }
                .map { it.category }.distinct().sorted()
            if (availableWordCategories.isEmpty()) {
                Text("No hay categorías con ejercicios activos de dificultad $wordSelectedDifficulty. Vuelve atrás y prueba otra dificultad o crea/activa ejercicios.", color = Color(0xFFB3261E), fontSize = 16.sp)
            } else {
                availableWordCategories.forEach { category ->
                    val count = wordExercises.count { it.active && it.category == category && it.category !in disabledWordExerciseCategories && it.difficulty == wordSelectedDifficulty }
                    Card(
                        Modifier.fillMaxWidth().clickable { startWordExercise(category) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8DDE5))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(category, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text("$count ejercicios disponibles", color = Color(0xFF667085), fontSize = 14.sp)
                            }
                            Text("›", fontSize = 26.sp, color = Color(0xFF3973AC))
                        }
                    }
                }
            }
            if (wordStartMessage.isNotBlank()) Text(wordStartMessage, color = Color(0xFFB3261E), fontSize = 15.sp)
        }
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
                                val expected = targetWord.getOrNull(wordExercisePlaced.size)
                                val chosenLetter = targetWord.getOrNull(letterIndex)
                                if (expected != null && chosenLetter != null && expected.equals(chosenLetter, ignoreCase = true)) {
                                    val next = wordExercisePlaced + letterIndex
                                    wordExercisePlaced = next
                                    wordExerciseMessage = if (next.size == targetWord.length) "¡Palabra completada!" else "¡Bien! Sigue con la siguiente letra."
                                    if (next.size == targetWord.length) {
                                        val baseReward = when (target.difficulty) { "Normal" -> normalRewardMinutes * 60; "Difícil" -> hardRewardMinutes * 60; else -> easyRewardMinutes * 60 }
                                        val penaltyPercent = when (wrongAnswerPenalty) { "none" -> 0; "full" -> 100; else -> 50 }
                                        val rewardPercent = (100 - wordExerciseWrongAnswers * penaltyPercent).coerceAtLeast(20)
                                        addEarnedTime(baseReward * rewardPercent / 100)
                                        resultCorrectCount = (targetWord.length - wordExerciseWrongAnswers).coerceIn(0, targetWord.length)
                                        resultTotalCount = targetWord.length
                                        correct = if (wordExerciseWrongAnswers == 0) targetWord.length else resultCorrectCount
                                        screen = "result"
                                    }
                                } else {
                                    wordExerciseWrongAnswers += 1
                                    wordExerciseMessage = "Prueba con otra letra."
                                }
                            },
                            enabled = !complete && !alreadyPlaced,
                            modifier = Modifier.height(58.dp)
                        ) { Text(targetWord.getOrNull(letterIndex)?.uppercase() ?: "", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Text(wordExerciseMessage, fontSize = 16.sp, color = if (wordExerciseMessage.startsWith("¡")) Color(0xFF24733B) else Color(0xFF667085))
                Text("Dificultad: ${target.difficulty}", fontSize = 13.sp, color = Color(0xFF667085))
            }
        }
    } else if (screen == "play-time") {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎮 Tiempo de juego", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
            Spacer(Modifier.height(12.dp))
            Text("Este es el tiempo que has ganado al aprender.", fontSize = 16.sp, color = Color(0xFF667085), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Text(String.format(java.util.Locale.ROOT, "%02d:%02d", balanceSeconds / 60, balanceSeconds % 60), fontSize = 54.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF267A55))
            Text("El tiempo sigue descontándose aunque uses otras aplicaciones.", fontSize = 15.sp, color = Color(0xFF667085), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                val deadline = preferences.getLong("play_deadline_ms", 0L)
                val remaining = if (deadline > 0L) ((deadline - System.currentTimeMillis() + 999L) / 1000L).toInt().coerceAtLeast(0) else balanceSeconds.coerceAtLeast(0)
                // Explicitly choosing this button pauses play time and banks only the
                // actual remaining seconds. Leaving the app with Home does not pause it.
                preferences.edit()
                    .putLong("play_deadline_ms", 0L)
                    .putInt("balance_seconds", remaining)
                    .commit()
                context.stopService(Intent(context, PlayTimeService::class.java))
                balanceSeconds = remaining
                screen = "home"
            }, modifier = Modifier.fillMaxWidth()) { Text("Terminar juego y volver") }
        }
    } else if (screen == "result") {
        val rewardIcon = when {
            resultCorrectCount == 0 -> "😢"
            resultCorrectCount == resultTotalCount -> "🏆"
            resultCorrectCount * 2 < resultTotalCount -> "⭐"
            resultCorrectCount * 2 == resultTotalCount -> "⭐⭐"
            else -> "⭐⭐⭐"
        }
        val rewardMessage = when {
            resultCorrectCount == 0 -> "¡Vamos a intentarlo otra vez!"
            resultCorrectCount == resultTotalCount -> "¡PERFECTO! ¡Eres un campeón!"
            resultCorrectCount * 2 < resultTotalCount -> "¡Buen intento! Sigue practicando"
            resultCorrectCount * 2 == resultTotalCount -> "¡Bien hecho! Has conseguido la mitad"
            else -> "¡Muy bien! Cada vez lo haces mejor"
        }
        val celebration = rememberInfiniteTransition(label = "celebracion")
        val iconScale by celebration.animateFloat(initialValue = 0.88f, targetValue = 1.12f, animationSpec = infiniteRepeatable(animation = tween(650), repeatMode = RepeatMode.Reverse), label = "rebote")
        val timeScale by celebration.animateFloat(initialValue = 1f, targetValue = 1.045f, animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse), label = "pulsoTiempo")
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(rewardIcon, fontSize = if (correct == pairCount) 104.sp else 76.sp, modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale })
            Spacer(Modifier.height(8.dp))
            Text(rewardMessage, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Text("+${lastEarnedSeconds / 60}:${(lastEarnedSeconds % 60).toString().padStart(2, '0')}", fontSize = 58.sp, fontWeight = FontWeight.Black, color = Color(0xFF267A55), modifier = Modifier.graphicsLayer { scaleX = timeScale; scaleY = timeScale })
            Text("MINUTOS GANADOS", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF267A55))
            Spacer(Modifier.height(14.dp))
            Text("$resultCorrectCount de $resultTotalCount aciertos", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF49445A))
            Spacer(Modifier.height(24.dp))
            Button(onClick = { screen = "home"; resolved = emptySet(); selected = emptyMap(); selectedPicture = ""; evaluatedPictures = emptyMap(); correct = 0; feedback = ""; lastEarnedSeconds = 0 }, modifier = Modifier.fillMaxWidth()) {
                Text("🏠 Volver al inicio", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else if (screen == "sequences") {
        val sequenceTypes = listOf("Orden temporal", "Procesos", "Causa y consecuencia", "Ciclos")
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedButton(onClick = { screen = "home" }) { Text("← Volver al inicio") }
                Text("Secuencias", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                Text("Elige el tipo de actividad que quieres practicar.", fontSize = 16.sp, color = Color(0xFF667085))
            }
            items(sequenceTypes) { type ->
                Card(
                    Modifier.fillMaxWidth().clickable {
                        sequenceChosenCategory = type
                        sequenceChosenDifficulty = ""
                        screen = "sequence-difficulty"
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8EC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA9D79A))
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(when(type) { "Orden temporal" -> "⏳"; "Procesos" -> "⚙️"; "Causa y consecuencia" -> "🔗"; else -> "🔄" }, fontSize = 30.sp)
                        Column(Modifier.weight(1f)) {
                            Text(type, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF245C2A))
                            Text("Practicar este tipo de secuencia", fontSize = 13.sp, color = Color(0xFF667085))
                        }
                        Text("›", fontSize = 26.sp, color = Color(0xFF39713A))
                    }
                }
            }
        }
    } else if (screen == "sequence-difficulty") {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedButton(onClick = { screen = "sequences" }) { Text("← Volver a las actividades") }
                Text(sequenceChosenCategory, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                Text("Elige la dificultad. El premio dependerá del nivel seleccionado.", fontSize = 16.sp, color = Color(0xFF667085))
            }
            items(listOf("Fácil", "Normal", "Difícil")) { level ->
                val reward = when(level) { "Normal" -> normalRewardMinutes; "Difícil" -> hardRewardMinutes; else -> easyRewardMinutes }
                Card(
                    Modifier.fillMaxWidth().clickable {
                        sequenceChosenDifficulty = level
                        val candidates = sequenceExercises.filter { it.active && it.cards.size <= sequenceMaxCards && it.category !in disabledSequenceCategories && it.type.equals(sequenceChosenCategory, ignoreCase = true) && it.difficulty.equals(level, ignoreCase = true) }
                        if (candidates.isNotEmpty()) {
                            val exercise = candidates.random()
                            sequencePlayingExercise = exercise
                            val indexes = if (exercise.type == "Letras") exercise.cards.firstOrNull().orEmpty().indices.toList() else exercise.cards.indices.toList()
                            val shuffled = indexes.shuffled()
                            sequencePlayOrder = if (shuffled.size > 1 && shuffled == indexes) shuffled.drop(1) + shuffled.first() else shuffled
                            sequenceSelectedOrder = emptyList()
                            sequenceWrongAnswers = 0
                            sequencePlayMessage = "Busca la primera tarjeta de la secuencia."
                            screen = "sequence-game"
                        } else {
                            sequencePlayMessage = "Todavía no hay ejercicios de $sequenceChosenCategory con dificultad $level. Prueba otra combinación o crea uno desde la configuración."
                            android.widget.Toast.makeText(context, sequencePlayMessage, android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E5EA))
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(level, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("Premio: $reward min de juego", fontSize = 14.sp, color = Color(0xFF667085))
                        }
                        Text("EMPEZAR  ›", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF39713A))
                    }
                }
            }
        }
    } else if (screen == "sequence-game") {
        val exercise = sequencePlayingExercise
        if (exercise == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No hay ningún ejercicio seleccionado.")
                Button(onClick = { screen = "sequences" }) { Text("Volver a ejercicios") }
            }
        } else {
            val isTabletLayout = LocalConfiguration.current.screenWidthDp >= 600
            val targetLetterWord = exercise.cards.firstOrNull().orEmpty()
            val isLettersExercise = exercise.type == "Letras"
            val finished = if (isLettersExercise) sequenceSelectedOrder.size == targetLetterWord.length else sequenceSelectedOrder.size == exercise.cards.size
            LazyColumn(
                Modifier.fillMaxSize().padding(if (isTabletLayout) 28.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedButton(onClick = { screen = "sequences"; sequenceSelectedOrder = emptyList(); sequencePlayMessage = "" }) { Text("← Volver a ejercicios") }
                    Text(exercise.title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF245C2A))
                    if (isLettersExercise) {
                        val wordPicture = exercise.pictures.firstOrNull().orEmpty()
                        if (wordPicture.startsWith("content://") || wordPicture.startsWith("file://")) {
                            AndroidView(
                                factory = { ctx -> ImageView(ctx).apply {
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 280)
                                } },
                                update = { view ->
                                    try { view.setImageURI(Uri.parse(wordPicture)) }
                                    catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) }
                                },
                                modifier = Modifier.fillMaxWidth().height(180.dp)
                            )
                        } else if (wordPicture.isNotBlank()) {
                            Text(wordPicture, fontSize = 64.sp)
                        }
                        Text("Forma la palabra tocando sus letras en orden.", fontSize = 14.sp, color = Color(0xFF667085))
                    }
                    Text(
                        if (finished) { if (isLettersExercise) "¡Has formado la palabra!" else "¡Muy bien! Has completado la secuencia." } else if (isLettersExercise) "Toca las letras en el orden correcto para formar la palabra." else "Toca las tarjetas en el orden en que ocurren.",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (finished) Color(0xFF24733B) else Color(0xFF667085)
                    )
                    Text(if (isLettersExercise) "Letras colocadas: ${sequenceSelectedOrder.size}/${targetLetterWord.length}" else "Pasos correctos: ${sequenceSelectedOrder.size}/${exercise.cards.size}", fontSize = 14.sp, color = Color(0xFF667085))
                }
                items(sequencePlayOrder.size) { displayPosition ->
                    val cardIndex = sequencePlayOrder[displayPosition]
                    val alreadySelected = cardIndex in sequenceSelectedOrder
                    val picture = if (isLettersExercise) exercise.pictures.firstOrNull()?.takeIf { it.isNotBlank() }.orEmpty() else exercise.pictures.getOrNull(cardIndex)?.takeIf { it.isNotBlank() }
                        ?: if (exercise.pictures.isEmpty()) defaultSequencePicture(cardIndex) else ""
                    Card(
                        Modifier.fillMaxWidth().clickable(enabled = !finished && !alreadySelected) {
                            val expectedIndex = sequenceSelectedOrder.size
                            val isCorrectChoice = if (isLettersExercise) targetLetterWord.getOrNull(expectedIndex) != null && targetLetterWord.getOrNull(expectedIndex)?.uppercaseChar() == targetLetterWord.getOrNull(cardIndex)?.uppercaseChar() else cardIndex == expectedIndex
                            if (isCorrectChoice) {
                                val nextOrder = sequenceSelectedOrder + cardIndex
                                sequenceSelectedOrder = nextOrder
                                val targetSize = if (isLettersExercise) targetLetterWord.length else exercise.cards.size
                                if (nextOrder.size == targetSize) {
                                    sequencePlayMessage = "¡Secuencia completada!"
                                    val baseRewardSeconds = (when (sequenceChosenDifficulty) { "Normal" -> normalRewardMinutes; "Difícil" -> hardRewardMinutes; else -> easyRewardMinutes }) * 60
                                    val penaltyPercent = when (wrongAnswerPenalty) { "none" -> 0; "full" -> 100; else -> 50 }
                                    val rewardPercent = (100 - sequenceWrongAnswers * penaltyPercent).coerceAtLeast(20)
                                    addEarnedTime(baseRewardSeconds * rewardPercent / 100)
                                    val resultSize = if (isLettersExercise) targetLetterWord.length else exercise.cards.size
                                    resultCorrectCount = (resultSize - sequenceWrongAnswers).coerceIn(0, resultSize)
                                    resultTotalCount = resultSize
                                    screen = "result"
                                } else {
                                    sequencePlayMessage = "¡Correcto! ¿Qué ocurre después?"
                                }
                            } else {
                                sequenceWrongAnswers += 1
                                sequencePlayMessage = "No es esa todavía. Busca el paso que ocurre antes."
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                alreadySelected -> Color(0xFFD9F2D9)
                                else -> Color.White
                            }
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            if (alreadySelected) 2.dp else 1.dp,
                            if (alreadySelected) Color(0xFF24733B) else Color(0xFFE0E5EA)
                        )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (isLettersExercise) {
                                Text(targetLetterWord.getOrNull(cardIndex)?.uppercase() ?: "", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                            } else if (sequenceContentFormat != "Solo texto") {
                                if (picture.startsWith("content://") || picture.startsWith("file://")) {
                                    AndroidView(
                                        factory = { ctx -> ImageView(ctx).apply {
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                            layoutParams = ViewGroup.LayoutParams(160, 120)
                                        } },
                                        update = { view ->
                                            try { view.setImageURI(Uri.parse(picture)) }
                                            catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) }
                                        },
                                        modifier = Modifier.width(if (isTabletLayout) 140.dp else 96.dp).height(if (isTabletLayout) 110.dp else 82.dp).clipToBounds()
                                    )
                                } else {
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.width(if (isTabletLayout) 140.dp else 96.dp).height(if (isTabletLayout) 110.dp else 82.dp)
                                            .background(if (picture.isBlank()) Color(0xFFF2F4F7) else Color(0xFFEAF5E6), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(picture.ifBlank { "▧" }, fontSize = if (picture.isBlank()) 34.sp else 42.sp, color = Color(0xFF98A2B3))
                                    }
                                }
                            }
                            if (isLettersExercise) {
                                Spacer(Modifier.weight(1f))
                            } else if (sequenceContentFormat != "Solo imágenes") {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (alreadySelected) Text("${sequenceSelectedOrder.indexOf(cardIndex) + 1}.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF24733B))
                                    Text(exercise.cards[cardIndex], fontSize = if (isTabletLayout) 22.sp else 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF253344))
                                }
                            } else if (alreadySelected) {
                                Text("${sequenceSelectedOrder.indexOf(cardIndex) + 1}.", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF24733B))
                            }
                        }
                    }
                }
                if (sequencePlayMessage.isNotBlank()) {
                    item { Text(sequencePlayMessage, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (finished) Color(0xFF24733B) else Color(0xFF72521B)) }
                }
                if (finished) {
                    item {
                        Button(
                            onClick = {
                                screen = "sequences"
                                sequenceSelectedOrder = emptyList()
                                sequencePlayMessage = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Elegir otro ejercicio") }
                    }
                    item { Text("Has ganado tiempo para jugar por completar el ejercicio.", fontSize = 14.sp, color = Color(0xFF667085)) }
                }
            }
        }
    } else if (screen == "cards-options") {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedButton(onClick = { screen = "home"; cardsStartMessage = "" }) { Text("← Volver al inicio") }
                Text("Tarjetas", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                Text("Elige cómo quieres practicar.", fontSize = 16.sp, color = Color(0xFF667085))
            }
            item {
                Text("Dificultad", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF253344))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Fácil", "Normal", "Difícil").forEach { option ->
                        Card(Modifier.weight(1f).clickable { difficulty = option }, shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (difficulty == option) Color(0xFFEAF2FF) else Color.White),
                            border = androidx.compose.foundation.BorderStroke(if (difficulty == option) 2.dp else 1.dp, if (difficulty == option) Color(0xFF3567A8) else Color(0xFFE8E0D2))) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(option, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(when(option) { "Normal" -> "$normalRewardMinutes min"; "Difícil" -> "$hardRewardMinutes min"; else -> "$easyRewardMinutes min" }, fontSize = 12.sp, color = Color(0xFF667085))
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().clickable { startCardsGame("Al azar") }, shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0D9)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8B66D))) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        androidx.compose.foundation.layout.Box(Modifier.width(56.dp).height(56.dp).background(Color(0xFFFFD9A3), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("🎲", fontSize = 30.sp) }
                        Column(Modifier.weight(1f)) { Text("Al azar", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7A4618)); Text("Una categoría sorpresa", fontSize = 14.sp, color = Color(0xFF795A3C)) }
                        Text("›", fontSize = 30.sp, color = Color(0xFFB96B20))
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().clickable { startCardsGame("Mezcladas") }, shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE2F4E8)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9BD4AC))) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        androidx.compose.foundation.layout.Box(Modifier.width(56.dp).height(56.dp).background(Color(0xFFC6EAD0), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("🗂️", fontSize = 30.sp) }
                        Column(Modifier.weight(1f)) { Text("Mezcladas", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF205F37)); Text("Tarjetas de distintas categorías", fontSize = 14.sp, color = Color(0xFF476C51)) }
                        Text("›", fontSize = 30.sp, color = Color(0xFF25834A))
                    }
                }
            }
            item {
                val locked = manualCategoryLimitReached
                Card(Modifier.fillMaxWidth().clickable(enabled = !locked) { cardsStartMessage = ""; screen = "category-select" }, shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = if (locked) Color(0xFFF0F0F0) else Color(0xFFE4EDFF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (locked) Color(0xFFD7D7D7) else Color(0xFFA9C2F3))) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        androidx.compose.foundation.layout.Box(Modifier.width(56.dp).height(56.dp).background(if (locked) Color(0xFFE0E0E0) else Color(0xFFC9DAFF), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(if (locked) "🔒" else "▦", fontSize = 30.sp, color = Color(0xFF356BC5)) }
                        Column(Modifier.weight(1f)) { Text("Elegir categoría", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = if (locked) Color(0xFF777777) else Color(0xFF244D91)); Text(if (locked) "Límite diario alcanzado" else "Escoge qué quieres practicar", fontSize = 14.sp, color = if (locked) Color(0xFF777777) else Color(0xFF4D6794)) }
                        Text("›", fontSize = 30.sp, color = if (locked) Color(0xFF777777) else Color(0xFF356BC5))
                    }
                }
            }
            if (cardsStartMessage.isNotBlank()) item { Text(cardsStartMessage, color = Color(0xFF9A4E00), fontWeight = FontWeight.SemiBold) }
            if (manualCategoryLimitReached) item { Text("Has usado las $dailyManualLimit elecciones manuales de hoy ($manualCategoryUsesToday/$dailyManualLimit). Podrás volver a elegir categoría mañana.", fontSize = 13.sp, color = Color(0xFF8A4B08)) }
        }
    } else if (screen == "category-select") {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedButton(onClick = { screen = "cards-options"; cardsStartMessage = "" }) { Text("← Volver a las opciones") }
                Text("Elegir categoría", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                Text("Toca una categoría para empezar la partida.", fontSize = 16.sp, color = Color(0xFF667085))
            }
            categories.forEach { name ->
                item {
                    val count = pairs.count { it.active && it.difficulty == difficulty && it.category.equals(name, ignoreCase = true) }
                    Card(Modifier.fillMaxWidth().clickable(enabled = count >= questionCount && !manualCategoryLimitReached) { startCardsGame("Elegir", name) },
                        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (count >= questionCount) Color.White else Color(0xFFF0F0F0)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E0D2))) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("📚", fontSize = 30.sp)
                            Column(Modifier.weight(1f)) { Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF253344)); Text("$count tarjetas disponibles", fontSize = 13.sp, color = Color(0xFF667085)) }
                            Text(if (count >= questionCount) "›" else "—", fontSize = 28.sp, color = Color(0xFF3567A8))
                        }
                    }
                }
            }
            if (manualCategoryLimitReached) item { Text("Has alcanzado el límite diario de elecciones manuales.", color = Color(0xFF8A4B08), fontWeight = FontWeight.SemiBold) }
            if (cardsStartMessage.isNotBlank()) item { Text(cardsStartMessage, color = Color(0xFF9A4E00), fontWeight = FontWeight.SemiBold) }
        }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Aprender para jugar", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253A5A))
                        Text("¡Aprende, descubre y diviértete!", fontSize = 14.sp, color = Color(0xFF42647D))
                    }
                    OutlinedButton(onClick = { pinEntry = ""; pinError = false; screen = "parent-pin" }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp)) { Text("⚙️", fontSize = 18.sp) }
                    OutlinedButton(onClick = { soundEnabled = !soundEnabled; preferences.edit().putBoolean("sound_enabled", soundEnabled).apply() }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp)) { Text(if (soundEnabled) "🔊" else "🔇", fontSize = 18.sp) }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD968)), border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE8B95D)), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("⏱️", fontSize = 38.sp)
                        Column(Modifier.weight(1f)) {
                            Text("Tiempo acumulado", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF79501C))
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("$balanceMinutes", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color(0xFF59357D))
                                Text("minutos", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF59357D), modifier = Modifier.padding(bottom = 5.dp))
                            }
                        }
                        
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF73C9F5)), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("🦊", fontSize = 64.sp)
                        Column(Modifier.weight(1f)) {
                            Text("¡Aprender para jugar!", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF143B66))
                            Text("Aprende, descubre y diviértete", fontSize = 15.sp, color = Color(0xFF174B70))
                        }
                    }
                }
            }
            item { Text("Elige cómo aprender", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344)) }
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
}


@Composable
private fun ModeCardImage(index: Int, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image = remember(context, index) {
        // The sprite is stored as one complete Base64 resource. Do not append the
        // old temporary chunks: doing so corrupts the JPEG and crashes on the home screen.
        val bitmap = runCatching {
            val encoded = context.resources.openRawResource(R.raw.cards_sprite_b64)
                .bufferedReader().use { it.readText().filterNot(Char::isWhitespace) }
            val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
        val sheet = bitmap ?: Bitmap.createBitmap(1, 3, Bitmap.Config.ARGB_8888)
        val tileHeight = (sheet.height / 3).coerceAtLeast(1)
        val tile = Bitmap.createBitmap(
            sheet,
            0,
            (index.coerceIn(0, 2) * tileHeight).coerceAtMost(sheet.height - tileHeight),
            sheet.width,
            tileHeight
        )
        tile.asImageBitmap()
    }
    Image(bitmap = image, contentDescription = description, modifier = modifier, contentScale = ContentScale.Fit)
}

private fun loadPairs(preferences: android.content.SharedPreferences): List<PairItem> {
    val saved = preferences.getString("pairs_v1", null) ?: return emptyList()
    return saved.split("\n").mapNotNull { line ->
        val fields = line.split("\t")
        if (fields.size < 5) null else PairItem(fields[0], fields[1], fields[2], fields[3], fields[4].toBoolean())
    }
}

private fun savePairs(preferences: android.content.SharedPreferences, pairs: List<PairItem>) {
    val saved = pairs.joinToString("\n") { pair ->
        listOf(pair.word, pair.picture, pair.category, pair.difficulty, pair.active.toString()).joinToString("\t")
    }
    preferences.edit().putString("pairs_v1", saved).apply()
}

private fun loadWordExercises(preferences: android.content.SharedPreferences): List<PairItem> {
    val saved = preferences.getString("letter_word_exercises_v1", null) ?: return emptyList()
    return saved.split("\n").mapNotNull { line ->
        val fields = line.split("\t")
        if (fields.size < 5 || fields[0].isBlank()) null
        else PairItem(fields[0], fields[1], fields[2], fields[3], fields[4].toBoolean())
    }
}

private fun saveWordExercises(preferences: android.content.SharedPreferences, exercises: List<PairItem>) {
    val saved = exercises.joinToString("\n") { exercise ->
        listOf(exercise.word, exercise.picture, exercise.category, exercise.difficulty, exercise.active.toString()).joinToString("\t")
    }
    preferences.edit().putString("letter_word_exercises_v1", saved).apply()
}