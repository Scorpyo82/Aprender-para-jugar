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

private fun defaultSequencePicture(index: Int): String =
    listOf("🌱", "🌿", "🌳", "🌸", "🦋", "🍎")[index.coerceIn(0, 5)]

private val sampleTemporalSequences = listOf(
    SequenceExercise("Plantar una semilla", listOf("Poner la semilla", "Sale el brote", "Crece la planta", "Aparece la flor"), "Solo imágenes", "Naturaleza", type = "Procesos", difficulty = "Normal"),
    SequenceExercise("Lavarse las manos", listOf("Mojar las manos", "Enjabonarse", "Aclarar", "Secar"), "Imagen y texto", "Rutinas", type = "Orden temporal", difficulty = "Fácil"),
    SequenceExercise("Cepillarse los dientes", listOf("Poner pasta", "Cepillarse", "Aclarar la boca", "Secar y guardar"), "Imagen y texto", "Rutinas", type = "Orden temporal", difficulty = "Normal"),
    SequenceExercise("Vestirse", listOf("Ponerse los pantalones", "Ponerse la camiseta", "Ponerse los zapatos"), "Solo imágenes", "Rutinas", listOf("Ponerse la camiseta|Ponerse los pantalones|Ponerse los zapatos"), type = "Orden temporal", difficulty = "Fácil"),
    SequenceExercise("Preparar un bocadillo", listOf("Preparar el pan", "Añadir ingredientes", "Cerrar el bocadillo", "Bocadillo listo"), "Imagen y texto", "Cocina", type = "Procesos", difficulty = "Difícil"),
    SequenceExercise("El ciclo de una mariposa", listOf("Huevos", "Oruga", "Crisálida", "Mariposa"), "Solo imágenes", "Naturaleza", type = "Ciclos", difficulty = "Normal")
)

private fun loadSequenceExercises(preferences: android.content.SharedPreferences): List<SequenceExercise> {
    val saved = preferences.getString("sequence_exercises_temporal_v1", null) ?: return sampleTemporalSequences
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
        }.ifEmpty { sampleTemporalSequences }
    }.getOrDefault(sampleTemporalSequences)
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


private fun exportContentPackage(context: Context, target: Uri, pairs: List<PairItem>, categories: List<String>) {
    val resolver = context.contentResolver
    ZipOutputStream(resolver.openOutputStream(target)?.buffered() ?: throw IllegalArgumentException("No se puede escribir en el destino.")).use { zip ->
        val root = JSONObject()
        root.put("format", "aprender-para-jugar-content")
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

private fun importContentPackage(context: Context, source: Uri): ImportedContent {
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
    require(root.optString("format") == "aprender-para-jugar-content") { "El archivo no es una exportación de Aprender para jugar." }
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

private val samplePairs = listOf(
    PairItem("mamá", "👩"), PairItem("sol", "☀️"),
    PairItem("casa", "🏠"), PairItem("gato", "🐈"),
    PairItem("luna", "🌙"), PairItem("pan", "🥖")
)

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
private fun AprenderApp() {
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val preferences = remember { context.getSharedPreferences("aprender_para_jugar", Context.MODE_PRIVATE) }
    var pairs by remember { mutableStateOf(loadPairs(preferences)) }
    var wordDraft by remember { mutableStateOf("") }
    var pictureDraft by remember { mutableStateOf("🐱") }
    var categoryDraft by remember { mutableStateOf("Primeras palabras") }
    var difficultyDraft by remember { mutableStateOf("Fácil") }
    var editingWord by remember { mutableStateOf<String?>(null) }
    var libraryMessage by remember { mutableStateOf("") }
    var libraryPage by remember { mutableStateOf("list") }
    var sequenceExercises by remember { mutableStateOf(loadSequenceExercises(preferences)) }
    var sequenceEditingIndex by remember { mutableIntStateOf(-1) }
    var sequenceTitleDraft by remember { mutableStateOf("") }
    var sequenceFormatDraft by remember { mutableStateOf("Imagen y texto") }
    var sequenceCategoryDraft by remember { mutableStateOf("Rutinas") }
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
    var sequenceAlternativeDraft by remember { mutableStateOf("") }
    var sequenceEditorMessage by remember { mutableStateOf("") }
    var sequencePlayingExercise by remember { mutableStateOf<SequenceExercise?>(null) }
    var sequencePlayOrder by remember { mutableStateOf(emptyList<Int>()) }
    var sequenceSelectedOrder by remember { mutableStateOf(emptyList<Int>()) }
    var sequencePlayMessage by remember { mutableStateOf("") }
    var sequenceChosenCategory by remember { mutableStateOf("") }
    var sequenceChosenDifficulty by remember { mutableStateOf("") }

    var packageMessage by remember { mutableStateOf("") }
    var pendingImportMode by remember { mutableStateOf("replace") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preferences.edit().putBoolean("content_picker_active", false).apply()
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            pictureDraft = uri.toString()
            libraryMessage = "Imagen seleccionada."
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
                pictureDraft = Uri.fromFile(imageFile).toString()
                libraryMessage = "Foto añadida y comprimida para ocupar poco espacio."
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
    var categoryNames by remember { mutableStateOf((preferences.getString("categories", "Primeras palabras|Casa y familia|Naturaleza") ?: "").split("|").filter { it.isNotBlank() }) }
    var categoryMessage by remember { mutableStateOf("") }
    var categoryToRemove by remember { mutableStateOf("") }
    val categories = (categoryNames + pairs.map { it.category }).distinctBy { it.trim().lowercase() }.sorted()
    var selectedExerciseCategory by remember { mutableStateOf(preferences.getString("selected_exercise_category", categories.firstOrNull() ?: "Primeras palabras") ?: "Primeras palabras") }
    var exerciseCategoryMenuExpanded by remember { mutableStateOf(false) }
    var libraryCategoryFilter by remember { mutableStateOf("Todas") }
    var libraryDifficultyFilter by remember { mutableStateOf("Todas") }
    var libraryFilterCategoryExpanded by remember { mutableStateOf(false) }

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
                "words", "categories" -> parentSection = "cards-settings"
                "cards-settings", "sequences-settings", "words-settings" -> parentSection = "game-modes"
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
            "category-select" -> { screen = "cards-options"; cardsStartMessage = "" }
            "sequences" -> { screen = "home" }
            "sequence-difficulty" -> { screen = "sequences" }
            "sequence-game" -> { screen = "sequence-difficulty"; sequenceSelectedOrder = emptyList(); sequencePlayMessage = "" }
            "parent-pin", "exit-pin" -> { screen = "home"; pinEntry = ""; pinError = false }
            "exercise" -> { screen = "home"; resolved = emptySet(); selected = emptyMap(); selectedPicture = ""; evaluatedPictures = emptyMap(); correct = 0; feedback = "" }
            "result" -> { screen = "home"; resolved = emptySet(); selected = emptyMap(); selectedPicture = ""; evaluatedPictures = emptyMap(); correct = 0; feedback = ""; lastEarnedSeconds = 0 }
            else -> { screen = "home"; parentSection = "menu" }
        }
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
                Text("Control parental  ›  ${if (parentSection == "menu") "Menú principal" else when (parentSection) { "game-modes" -> "Configurar modos de juego"; "cards-settings" -> "Tarjetas"; "sequences-settings" -> "Secuencias"; "words-settings" -> "Palabras"; "words" -> "Biblioteca de palabras"; "categories" -> "Categorías"; "rewards" -> "Recompensas y límites"; "music" -> "Música de fondo"; "security" -> "Cambiar PIN"; "device-setup" -> "Configuración del dispositivo"; "about" -> "Acerca de esta app"; else -> "Configuración" }}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF667085))
                Text(if (parentSection == "menu") "Control parental" else when (parentSection) { "game-modes" -> "Configurar modos de juego"; "cards-settings" -> "Tarjetas"; "words" -> "Biblioteca de palabras"; "categories" -> "Categorías"; "rewards" -> "Recompensas y límites"; "music" -> "Música de fondo"; "security" -> "Cambiar PIN"; "device-setup" -> "Configuración del dispositivo"; "about" -> "Acerca de esta app"; else -> "Configuración" }, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                Text(if (parentSection == "menu") "Configura el contenido y las opciones de la aplicación." else "Control parental · ${when (parentSection) { "game-modes" -> "Elige una modalidad para configurar"; "cards-settings" -> "Opciones específicas del modo Tarjetas"; "sequences-settings" -> "Opciones del modo Secuencias"; "words-settings" -> "Opciones del modo Palabras"; "words" -> "Gestiona palabras e imágenes"; "categories" -> "Organiza el vocabulario"; "rewards" -> "Configura tiempos, recompensas y límites"; "music" -> "Configura la música y el sonido"; "security" -> "Protege el acceso y administra el saldo"; "device-setup" -> "Permisos y accesos necesarios para la protección"; "about" -> "Información, propósito y créditos"; else -> "Configuración" }}", fontSize = 15.sp, color = Color(0xFF667085))
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
                item { OutlinedButton(onClick = { parentSection = when (parentSection) { "game-modes" -> "menu"; "cards-settings", "sequences-settings", "words-settings" -> "game-modes"; "sequence-type-temporal", "sequence-type-procesos", "sequence-type-causa", "sequence-type-ciclos" -> "sequences-settings"; "sequence-editor-temporal" -> "sequence-library"; "sequence-library", "sequence-categories" -> "sequences-settings"; "sequence-library-temporal", "sequence-categories-temporal" -> "sequence-type-temporal"; "sequence-library-procesos", "sequence-categories-procesos" -> "sequence-type-procesos"; "sequence-library-causa", "sequence-categories-causa" -> "sequence-type-causa"; "sequence-library-ciclos", "sequence-categories-ciclos" -> "sequence-type-ciclos"; "words", "categories" -> "cards-settings"; else -> "menu" } }) { Text(if (parentSection == "game-modes") "← Volver al menú de control" else if (parentSection == "cards-settings" || parentSection == "sequences-settings" || parentSection == "words-settings") "← Volver a los modos de juego" else if (parentSection.startsWith("sequence-type-")) "← Volver a Secuencias" else if (parentSection == "sequence-editor-temporal") "← Volver a la biblioteca" else if (parentSection == "sequence-library" || parentSection == "sequence-categories") "← Volver a Secuencias" else if (parentSection.startsWith("sequence-library-") || parentSection.startsWith("sequence-categories-")) "← Volver al tipo de secuencia" else if (parentSection == "words" || parentSection == "categories") "← Volver a Tarjetas" else "← Volver al menú de control") } }
                when (parentSection) {
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
                                        Text("🌱", fontSize = 24.sp)
                                        Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(description, fontSize = 13.sp, color = Color(0xFF667085)) }
                                        Text("›", fontSize = 26.sp, color = Color(0xFF39713A))
                                    }
                                }
                            }
                        }
                        item {
                            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Formato del contenido", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Se podrá elegir entre los tres formatos acordados. Estos controles son una vista previa; el juego aún no está implementado.", fontSize = 14.sp, color = Color(0xFF667085))
                            listOf("Imagen y texto", "Solo imágenes", "Solo texto").forEach { format ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(format, modifier = Modifier.weight(1f))
                                    Text("Próximamente", fontSize = 12.sp, color = Color(0xFF667085))
                                }
                            }
                        }
                        item {
                            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Máximo de tarjetas por secuencia", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("6 tarjetas como máximo. La distribución se adaptará a la orientación y al espacio disponible.", fontSize = 14.sp, color = Color(0xFF667085))
                        }
                        item { Text("Configuración en fase beta: todavía no modifica ejercicios reales.", fontSize = 13.sp, color = Color(0xFF667085)) }
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
                        item { Text(if (parentSection == "sequence-type-temporal") "Prototipo funcional: biblioteca y editor de Orden temporal." else "Biblioteca de ejemplo disponible inicialmente para Orden temporal.", fontSize = 13.sp, color = Color(0xFF667085)) }
                    }
                    "sequence-library", "sequence-library-temporal", "sequence-library-procesos", "sequence-library-causa", "sequence-library-ciclos" -> {
                        val sequenceTypes = listOf("Orden temporal", "Procesos", "Causa y consecuencia", "Ciclos")
                        val sequenceCategories = (sequenceExercises.map { it.category }.filter { it.isNotBlank() } + listOf("Rutinas", "Naturaleza", "Cocina")).distinct().sorted()
                        val visibleExercises = sequenceExercises.withIndex().filter { (_, exercise) ->
                            (sequenceFilterType == "Todos" || exercise.type == sequenceFilterType) &&
                            (sequenceFilterCategory == "Todas" || exercise.category == sequenceFilterCategory) &&
                            (sequenceFilterDifficulty == "Todas" || exercise.difficulty == sequenceFilterDifficulty) &&
                            (sequenceFilterStatus == "Todos" || if (sequenceFilterStatus == "Activos") exercise.active else !exercise.active)
                        }
                        item { Text("Biblioteca de secuencias", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item { Text("Biblioteca común para los cuatro tipos. Filtra los ejercicios y activa solo los que quieras ofrecer durante el juego.", fontSize = 14.sp, color = Color(0xFF667085)) }
                        item {
                            Button(onClick = {
                                sequenceEditingIndex = -1; sequenceTitleDraft = ""; sequenceFormatDraft = "Imagen y texto"
                                sequenceCategoryDraft = "Rutinas"; sequenceTypeDraft = "Orden temporal"; sequenceDifficultyDraft = "Normal"
                                sequenceCardsDraft = listOf("", "", ""); sequenceCardPicturesDraft = listOf("", "", "")
                                sequenceAlternativeDraft = ""; sequenceEditorMessage = ""; parentSection = "sequence-editor-temporal"
                            }, modifier = Modifier.fillMaxWidth()) { Text("＋ Crear ejercicio") }
                        }
                        item {
                            Text("Tipo", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                (listOf("Todos") + sequenceTypes).forEach { type -> FilterChip(selected = sequenceFilterType == type, onClick = { sequenceFilterType = type }, label = { Text(type, fontSize = 12.sp) }) }
                            }
                        }
                        item {
                            Text("Categoría", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                (listOf("Todas") + sequenceCategories).forEach { category -> FilterChip(selected = sequenceFilterCategory == category, onClick = { sequenceFilterCategory = category }, label = { Text(category, fontSize = 12.sp) }) }
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
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "sequence-categories", "sequence-categories-temporal", "sequence-categories-procesos", "sequence-categories-causa", "sequence-categories-ciclos" -> {
                        val sequenceCategories = (sequenceExercises.map { it.category }.filter { it.isNotBlank() } + listOf("Rutinas", "Naturaleza", "Cocina")).distinct().sorted()
                        item { Text("Categorías de secuencias", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item { Text("Las categorías son comunes a todos los tipos. Al desactivar una, sus ejercicios no aparecerán en las partidas, aunque sigan guardados en la biblioteca.", fontSize = 14.sp, color = Color(0xFF667085)) }
                        sequenceCategories.forEach { category ->
                            item {
                                val enabled = category !in disabledSequenceCategories
                                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (enabled) Color.White else Color(0xFFF2F4F7))) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(category, fontWeight = FontWeight.Bold)
                                            Text("${sequenceExercises.count { it.category == category }} ejercicios · ${sequenceExercises.count { it.category == category && it.active }} activos", fontSize = 12.sp, color = Color(0xFF667085))
                                        }
                                        Switch(checked = enabled, onCheckedChange = { checked ->
                                            disabledSequenceCategories = if (checked) disabledSequenceCategories - category else disabledSequenceCategories + category
                                            preferences.edit().putStringSet("disabled_sequence_categories", disabledSequenceCategories).apply()
                                        })
                                    }
                                }
                            }
                        }
                    }
                    "sequence-editor-temporal" -> {
                        item { Text(if (sequenceEditingIndex < 0) "Nuevo ejercicio" else "Editar ejercicio", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item { OutlinedTextField(value = sequenceTitleDraft, onValueChange = { sequenceTitleDraft = it }, label = { Text("Nombre del ejercicio") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                        item {
                            Text("Formato de las tarjetas", fontWeight = FontWeight.Bold)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("Imagen y texto", "Solo imágenes", "Solo texto").forEach { format ->
                                    OutlinedButton(
                                        onClick = { sequenceFormatDraft = format },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(if (sequenceFormatDraft == format) "✓ $format" else format, fontSize = 14.sp) }
                                }
                            }
                        }
                        item {
                            Text("Tipo de secuencia", fontWeight = FontWeight.Bold)
                            listOf("Orden temporal", "Procesos", "Causa y consecuencia", "Ciclos").forEach { type ->
                                OutlinedButton(onClick = { sequenceTypeDraft = type }, modifier = Modifier.fillMaxWidth()) { Text(if (sequenceTypeDraft == type) "✓ $type" else type) }
                            }
                        }
                        item {
                            Text("Dificultad", fontWeight = FontWeight.Bold)
                            listOf("Fácil", "Normal", "Difícil").forEach { level ->
                                OutlinedButton(onClick = { sequenceDifficultyDraft = level }, modifier = Modifier.fillMaxWidth()) { Text(if (sequenceDifficultyDraft == level) "✓ $level" else level) }
                            }
                        }
                        item { OutlinedTextField(value = sequenceCategoryDraft, onValueChange = { sequenceCategoryDraft = it }, label = { Text("Categoría temática") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
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
                                                    Text(picture, fontSize = 38.sp)
                                                }
                                            }
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Imagen de la tarjeta ${index + 1}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            OutlinedButton(onClick = {
                                                pendingSequencePictureIndex = index
                                                preferences.edit().putBoolean("content_picker_active", true).putBoolean("parent_area_active", true).commit()
                                                sequenceImagePicker.launch(arrayOf("image/*"))
                                            }, modifier = Modifier.fillMaxWidth()) { Text("Elegir imagen") }
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
                            }, enabled = sequenceCardsDraft.size < 6, modifier = Modifier.fillMaxWidth()) { Text("＋ Añadir tarjeta (${sequenceCardsDraft.size}/6)") }
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
                                    cleanCards.size < 2 || cleanCards.any { it.isBlank() } -> sequenceEditorMessage = "Completa al menos 2 tarjetas; no puede haber pasos vacíos."
                                    cleanCards.size > 6 -> sequenceEditorMessage = "El máximo es de 6 tarjetas."
                                     sequenceFormatDraft == "Solo imágenes" && sequenceCardPicturesDraft.take(cleanCards.size).any { it.isBlank() } -> sequenceEditorMessage = "En el formato «Solo imágenes», selecciona una imagen para cada tarjeta."
                                    alternativeLines.isNotEmpty() && invalidAlternative -> sequenceEditorMessage = "Cada alternativa debe incluir todos los números del 1 al ${cleanCards.size}, sin repetir y separados por comas."
                                    else -> {
                                        val alternatives = parsedAlternatives.map { indexes -> indexes.filterNotNull().map { cleanCards[it - 1] }.joinToString("|") }
                                        val cleanPictures = cleanCards.indices.map { sequenceCardPicturesDraft.getOrNull(it).orEmpty() }
                                        val exercise = SequenceExercise(sequenceTitleDraft.trim(), cleanCards, sequenceFormatDraft, sequenceCategoryDraft.trim().ifBlank { "Sin categoría" }, alternatives, cleanPictures, sequenceTypeDraft, sequenceDifficultyDraft, sequenceExercises.getOrNull(sequenceEditingIndex)?.active ?: true)
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
                        listOf("Rutinas", "Naturaleza", "Cocina").forEach { category ->
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
                        item { Text("En esta primera versión las categorías se muestran como ejemplo; su edición se añadirá después.", fontSize = 12.sp, color = Color(0xFF667085)) }
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
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF))) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🖼️", fontSize = 26.sp)
                                    Column { Text("Biblioteca de palabras", fontWeight = FontWeight.Bold); Text("Palabras e imágenes que servirán como pistas", fontSize = 13.sp, color = Color(0xFF667085)) }
                                }
                            }
                        }
                        item {
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF))) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🗂️", fontSize = 26.sp)
                                    Column { Text("Categorías", fontWeight = FontWeight.Bold); Text("Organización del vocabulario por temas", fontSize = 13.sp, color = Color(0xFF667085)) }
                                }
                            }
                        }
                        item {
                            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Mecánica prevista", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("El niño verá una imagen, tocará letras y las colocará en las posiciones de la palabra. La dificultad y el contenido se configurarán aquí cuando se implemente el juego.", fontSize = 14.sp, color = Color(0xFF667085))
                        }
                        item { Text("Configuración en fase beta: la biblioteca y los controles aún no están conectados.", fontSize = 13.sp, color = Color(0xFF667085)) }
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
                                    } else if (name !in listOf("Primeras palabras", "Casa y familia", "Naturaleza")) {
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
                        Button(onClick = { musicPicker.launch(arrayOf("audio/mpeg", "audio/mp3", "audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Elegir archivo MP3") }
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
            correct == 0 -> "😢"
            correct == pairCount -> "🏆"
            correct * 2 < pairCount -> "⭐"
            correct * 2 == pairCount -> "⭐⭐"
            else -> "⭐⭐⭐"
        }
        val rewardMessage = when {
            correct == 0 -> "¡Vamos a intentarlo otra vez!"
            correct == pairCount -> "¡PERFECTO! ¡Eres un campeón!"
            correct * 2 < pairCount -> "¡Buen intento! Sigue practicando"
            correct * 2 == pairCount -> "¡Bien hecho! Has conseguido la mitad"
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
            Text("$correct de $pairCount aciertos", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF49445A))
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
                        val candidates = sequenceExercises.filter { it.active && it.category !in disabledSequenceCategories && it.type.equals(sequenceChosenCategory, ignoreCase = true) && it.difficulty.equals(level, ignoreCase = true) }
                        if (candidates.isNotEmpty()) {
                            val exercise = candidates.random()
                            sequencePlayingExercise = exercise
                            val indexes = exercise.cards.indices.toList()
                            val shuffled = indexes.shuffled()
                            sequencePlayOrder = if (shuffled.size > 1 && shuffled == indexes) shuffled.drop(1) + shuffled.first() else shuffled
                            sequenceSelectedOrder = emptyList()
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
            val finished = sequenceSelectedOrder.size == exercise.cards.size
            LazyColumn(
                Modifier.fillMaxSize().padding(if (isTabletLayout) 28.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedButton(onClick = { screen = "sequences"; sequenceSelectedOrder = emptyList(); sequencePlayMessage = "" }) { Text("← Volver a ejercicios") }
                    Text(exercise.title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF245C2A))
                    Text(
                        if (finished) "¡Muy bien! Has completado la secuencia." else "Toca las tarjetas en el orden en que ocurren.",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (finished) Color(0xFF24733B) else Color(0xFF667085)
                    )
                    Text("Pasos correctos: ${sequenceSelectedOrder.size}/${exercise.cards.size}", fontSize = 14.sp, color = Color(0xFF667085))
                }
                items(sequencePlayOrder.size) { displayPosition ->
                    val cardIndex = sequencePlayOrder[displayPosition]
                    val alreadySelected = cardIndex in sequenceSelectedOrder
                    val picture = exercise.pictures.getOrNull(cardIndex)?.takeIf { it.isNotBlank() }
                        ?: if (exercise.pictures.isEmpty()) defaultSequencePicture(cardIndex) else ""
                    Card(
                        Modifier.fillMaxWidth().clickable(enabled = !finished && !alreadySelected) {
                            val expectedIndex = sequenceSelectedOrder.size
                            if (cardIndex == expectedIndex) {
                                val nextOrder = sequenceSelectedOrder + cardIndex
                                sequenceSelectedOrder = nextOrder
                                if (nextOrder.size == exercise.cards.size) {
                                    sequencePlayMessage = "¡Secuencia completada!"
                                    addEarnedTime((when (sequenceChosenDifficulty) { "Normal" -> normalRewardMinutes; "Difícil" -> hardRewardMinutes; else -> easyRewardMinutes }) * 60)
                                } else {
                                    sequencePlayMessage = "¡Correcto! ¿Qué ocurre después?"
                                }
                            } else {
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
                            if (exercise.format != "Solo texto") {
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
                            if (exercise.format != "Solo imágenes") {
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
                    Modifier.fillMaxWidth().clickable { screen = "cards-options"; cardsStartMessage = "" },
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
                            Text("EMPEZAR  ›", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF754411))
                        }
                    }
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth().clickable { screen = "sequences" },
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
                            Text("EMPEZAR  ›", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF39713A))
                        }
                    }
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth(),
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
                            Text("PRÓXIMAMENTE  🔒", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF285F94))
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
    val saved = preferences.getString("pairs_v1", null) ?: return samplePairs
    return saved.split("\n").mapNotNull { line ->
        val fields = line.split("\t")
        if (fields.size < 5) null else PairItem(fields[0], fields[1], fields[2], fields[3], fields[4].toBoolean())
    }.ifEmpty { samplePairs }
}

private fun savePairs(preferences: android.content.SharedPreferences, pairs: List<PairItem>) {
    val saved = pairs.joinToString("\n") { pair ->
        listOf(pair.word, pair.picture, pair.category, pair.difficulty, pair.active.toString()).joinToString("\t")
    }
    preferences.edit().putString("pairs_v1", saved).apply()
}