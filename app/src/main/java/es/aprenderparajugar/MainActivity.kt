package es.aprenderparajugar

import android.os.Bundle
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onStop() {
        val prefs = getSharedPreferences("aprender_para_jugar", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val currentDeadline = prefs.getLong("play_deadline_ms", 0L)
        val balance = prefs.getInt("balance_seconds", 0).coerceAtLeast(0)
        val parentAreaWasActive = prefs.getBoolean("parent_area_active", false)
        val manualExit = prefs.getBoolean("manual_exit_allowed", false)

        // If the app leaves the foreground with earned time, start the countdown here as
        // a reliable fallback; accessibility window events are not delivered on every tablet.
        if (!parentAreaWasActive && !manualExit && currentDeadline <= now && balance > 0) {
            prefs.edit()
                .putLong("play_deadline_ms", now + balance.toLong() * 1000L)
                .putLong("expiry_last_launch_ms", 0L)
                .apply()
            try {
                androidx.core.content.ContextCompat.startForegroundService(
                    this,
                    Intent(this, PlayTimeService::class.java).setAction(PlayTimeService.ACTION_START)
                )
            } catch (_: Exception) { }
        }
        prefs.edit().putBoolean("parent_area_active", false).apply()
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

    var packageMessage by remember { mutableStateOf("") }
    var pendingImportMode by remember { mutableStateOf("replace") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            pictureDraft = uri.toString()
            libraryMessage = "Imagen seleccionada."
        }
    }
    var screen by remember { mutableStateOf("home") }
    var musicUri by remember { mutableStateOf(preferences.getString("background_music_uri", null)) }
    var soundEnabled by remember { mutableStateOf(preferences.getBoolean("sound_enabled", true)) }
    var musicMessage by remember { mutableStateOf("") }
    var appInForeground by remember { mutableStateOf(true) }
    val lifecycleOwner = remember(context) { context as? ComponentActivity }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> appInForeground = true
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
    var difficulty by remember { mutableStateOf("Fácil") }
    var questionCount by remember { mutableIntStateOf(preferences.getInt("question_count", 4).coerceIn(1, 10)) }
    var balanceSeconds by remember { mutableIntStateOf(preferences.getInt("balance_seconds", 0)) }
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
    var parentSection by remember { mutableStateOf("menu") }
    var dailyManualLimit by remember { mutableIntStateOf(preferences.getInt("daily_manual_limit", 2)) }
    var easyRewardMinutes by remember { mutableIntStateOf(preferences.getInt("reward_easy_minutes", 2).coerceIn(0, 120)) }
    var normalRewardMinutes by remember { mutableIntStateOf(preferences.getInt("reward_normal_minutes", 5).coerceIn(0, 120)) }
    var hardRewardMinutes by remember { mutableIntStateOf(preferences.getInt("reward_hard_minutes", 7).coerceIn(0, 120)) }
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
        if (uri != null) {
            packageMessage = try {
                exportContentPackage(context, uri, pairs, categories)
                "Exportación completada: ${pairs.size} palabras y ${categories.size} categorías."
            } catch (e: Exception) { "No se pudo exportar: ${e.message ?: "error de archivo"}" }
        }
    }
    val importPackageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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

    fun addEarnedTime(seconds: Int) {
        if (seconds <= 0) return
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
            preferences.edit().putInt("balance_seconds", updated).apply()
            balanceSeconds = updated
        }
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
                screen = "home"
                break
            }
            delay(1000L)
        }
    }

    androidx.compose.runtime.LaunchedEffect(screen, appInForeground) {
        preferences.edit().putBoolean(
            "parent_area_active",
            appInForeground && (screen == "parent-pin" || screen == "parent" || screen == "exit-pin")
        ).apply()
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
                Text("⚙️ Control parental", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                Text("Configura el contenido y las recompensas de Aprender para jugar.", fontSize = 16.sp, color = Color(0xFF667085))
            }
            if (parentSection == "menu") {
                item {
                    Card(Modifier.fillMaxWidth().clickable { parentSection = "words" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🖼️", fontSize = 30.sp)
                            Column { Text("Biblioteca de palabras", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Palabras, dibujos y dificultad", color = Color(0xFF667085)) }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth().clickable { parentSection = "categories" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🗂️", fontSize = 30.sp)
                            Column { Text("Categorías", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Organiza el vocabulario por temas", color = Color(0xFF667085)) }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth().clickable { parentSection = "rewards" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("⏱️", fontSize = 30.sp)
                            Column { Text("Recompensas y límites", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Minutos por dificultad y elecciones diarias", color = Color(0xFF667085)) }
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
                            Column { Text("Seguridad y saldo", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("PIN y minutos acumulados", color = Color(0xFF667085)) }
                        }
                    }
                }
            } else {
                item { OutlinedButton(onClick = { parentSection = "menu" }) { Text("← Volver al menú de control") } }
                when (parentSection) {
                    "words" -> item {
                        if (libraryPage == "list") {
                            Text("Biblioteca de palabras", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Gestiona las palabras guardadas. Pulsa Añadir para crear una nueva.", fontSize = 14.sp, color = Color(0xFF667085))
                            Button(onClick = { editingWord = null; wordDraft = ""; pictureDraft = ""; categoryDraft = "Primeras palabras"; difficultyDraft = "Fácil"; libraryMessage = ""; libraryPage = "edit" }, modifier = Modifier.fillMaxWidth()) { Text("＋ Añadir palabra") }
                            Spacer(Modifier.height(8.dp))
                            Text("Copia de seguridad y traslado", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Exporta categorías, palabras, dificultad, estado e imágenes en un ZIP. El PIN y el saldo no se incluyen.", fontSize = 13.sp, color = Color(0xFF667085))
                            OutlinedButton(onClick = { exportPackageLauncher.launch("aprender-para-jugar-contenido.zip") }, modifier = Modifier.fillMaxWidth()) { Text("📦 Exportar contenido a ZIP") }
                            OutlinedButton(onClick = { pendingImportMode = "replace"; importPackageLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("♻️ Importar y sustituir biblioteca") }
                            OutlinedButton(onClick = { pendingImportMode = "merge"; importPackageLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("➕ Importar y combinar contenido") }
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
                                                AndroidView(factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; layoutParams = ViewGroup.LayoutParams(120, 120) } }, update = { view -> try { view.setImageURI(Uri.parse(pair.picture)) } catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) } }, modifier = Modifier.width(64.dp).height(64.dp))
                                            } else Text(pair.picture, fontSize = 28.sp)
                                            Column(Modifier.weight(1f)) { Text(pair.word, fontSize = 19.sp, fontWeight = FontWeight.SemiBold); Text("${pair.category} · ${pair.difficulty} · ${if (pair.active) "Activa" else "Inactiva"}", color = Color(0xFF667085)) }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            OutlinedButton(onClick = { editingWord = pair.word; wordDraft = pair.word; pictureDraft = pair.picture; categoryDraft = pair.category; difficultyDraft = pair.difficulty; libraryMessage = ""; libraryPage = "edit" }) { Text("Editar") }
                                            OutlinedButton(onClick = { pairs = pairs.map { if (it.word == pair.word) it.copy(active = !it.active) else it }; savePairs(preferences, pairs) }) { Text(if (pair.active) "Desactivar" else "Activar") }
                                            OutlinedButton(onClick = { pairs = pairs.filterNot { it.word == pair.word }; savePairs(preferences, pairs) }) { Text("Eliminar") }
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
                            OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Elegir imagen de la galería") }
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
                        Text("Añade categorías y, antes de borrar una, elige dónde mover sus palabras.", fontSize = 14.sp, color = Color(0xFF667085))
                        OutlinedTextField(value = categoryDraftName, onValueChange = { categoryDraftName = it }, label = { Text("Nueva categoría") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = { val clean = categoryDraftName.trim(); if (clean.isBlank()) categoryMessage = "Escribe un nombre."; else if (categories.any { it.equals(clean, ignoreCase = true) }) categoryMessage = "Esa categoría ya existe."; else { categoryNames = categoryNames + clean; preferences.edit().putString("categories", categoryNames.joinToString("|")).apply(); categoryDraftName = ""; categoryMessage = "Categoría creada." } }, modifier = Modifier.fillMaxWidth()) { Text("Añadir categoría") }
                        if (categoryMessage.isNotBlank()) Text(categoryMessage, color = Color(0xFF24733B))
                        categories.forEach { name ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${pairs.count { it.category.equals(name, ignoreCase = true) }} palabras", color = Color(0xFF667085))
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
                                preferences.edit().putInt("balance_seconds", 0).putLong("play_deadline_ms", 0L).putLong("expiry_last_launch_ms", 0L).apply()
                                context.stopService(Intent(context, PlayTimeService::class.java))
                            }, enabled = balanceSeconds > 0) { Text("Poner a cero") }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Recompensas y límites", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Minutos que se ganan al completar un ejercicio", fontSize = 17.sp)
                        Text("El premio final se calcula proporcionalmente según los aciertos.", fontSize = 14.sp, color = Color(0xFF667085))
                        listOf("Fácil", "Normal", "Difícil").forEach { level ->
                            val minutes = when (level) { "Normal" -> normalRewardMinutes; "Difícil" -> hardRewardMinutes; else -> easyRewardMinutes }
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("$level: $minutes min", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
                        Spacer(Modifier.height(8.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Preguntas por partida", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Tú decides cuántas preguntas tendrá cada partida. Izan no podrá cambiar esta cantidad desde la pantalla de inicio.", fontSize = 14.sp, color = Color(0xFF667085))
                        Text("Cantidad configurada: $questionCount", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { questionCount = (questionCount - 1).coerceAtLeast(1); preferences.edit().putInt("question_count", questionCount).apply() }, enabled = questionCount > 1) { Text("−") }
                            Text("$questionCount", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { questionCount = (questionCount + 1).coerceAtMost(10); preferences.edit().putInt("question_count", questionCount).apply() }, enabled = questionCount < 10) { Text("+") }
                        }
                        Text("Límite diario de elecciones manuales: $dailyManualLimit", fontSize = 17.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { if (dailyManualLimit > 0) { dailyManualLimit--; preferences.edit().putInt("daily_manual_limit", dailyManualLimit).apply() } }) { Text("−") }
                            OutlinedButton(onClick = { if (dailyManualLimit < 10) { dailyManualLimit++; preferences.edit().putInt("daily_manual_limit", dailyManualLimit).apply() } }) { Text("+") }
                        }
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
                    "security" -> item {
                        Text("Seguridad y saldo", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Cambiar PIN familiar", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(value = newPinDraft, onValueChange = { newPinDraft = it.filter { ch -> ch.isDigit() }.take(4) }, label = { Text("Nuevo PIN de 4 cifras") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = { if (newPinDraft.length == 4) { pinStored = newPinDraft; preferences.edit().putString("parent_pin", pinStored).apply(); newPinDraft = ""; libraryMessage = "PIN actualizado." } else libraryMessage = "El PIN debe tener 4 cifras." }, modifier = Modifier.fillMaxWidth()) { Text("Guardar PIN") }
                        if (libraryMessage.isNotBlank()) Text(libraryMessage, color = Color(0xFF24733B))
                        Text("Saldo actual: $balanceMinutes min $balanceRemainderSeconds s", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { balanceSeconds = 0; preferences.edit().putInt("balance_seconds", 0).apply() }, modifier = Modifier.fillMaxWidth()) { Text("Poner saldo a cero") }
                    }
                }
            }
            item { OutlinedButton(onClick = { screen = "home"; parentSection = "menu" }, modifier = Modifier.fillMaxWidth()) { Text("Salir de configuración") } }
        }
    } else if (screen == "exercise") {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Une las palabras con sus dibujos", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color(0xFF284B63))
            Text("1. Elige una palabra.  2. Toca su dibujo.", fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    exercisePairs.forEach { item ->
                        val isSelected = selected["word"] == item.word
                        val isResolved = item.word in resolved
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (!isResolved) {
                                    val pendingPicture = selectedPicture
                                    if (pendingPicture.isBlank()) {
                                        selected = mapOf("word" to item.word)
                                        feedback = "Ahora busca el dibujo de «" + item.word + "»."
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
                                            addEarnedTime((rewardSeconds * newCorrect) / pairCount)
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
                            Text(item.word, Modifier.padding(16.dp), fontSize = 27.sp, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, color = Color(0xFF29213B))
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    exercisePairs.reversed().forEach { item ->
                        Card(Modifier.fillMaxWidth().clickable {
                            val word = selected["word"]
                            if (word == null) {
                                selectedPicture = item.picture
                                feedback = "Has elegido un dibujo. Ahora toca la palabra que le corresponde."
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
                                    addEarnedTime((rewardSeconds * correct) / pairCount)
                                    screen = "result"
                                }
                            }
                        }, colors = CardDefaults.cardColors(containerColor = when { selectedPicture == item.picture -> Color(0xFFFFD66B); evaluatedPictures[item.picture] == true -> Color(0xFFD9F2D9); evaluatedPictures[item.picture] == false -> Color(0xFFFFDAD6); else -> Color(0xFFF0E8F5) }), border = when { selectedPicture == item.picture -> androidx.compose.foundation.BorderStroke(3.dp, Color(0xFFB56A00)); evaluatedPictures.containsKey(item.picture) -> androidx.compose.foundation.BorderStroke(3.dp, if (evaluatedPictures[item.picture] == true) Color(0xFF24733B) else Color(0xFFB3261E)); else -> null }) {
                            if (item.picture.startsWith("content://") || item.picture.startsWith("file://")) {
                                AndroidView(factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 180) } }, update = { view -> try { view.setImageURI(Uri.parse(item.picture)) } catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) } }, modifier = Modifier.fillMaxWidth().height(90.dp).padding(8.dp))
                            } else {
                                Text(item.picture, Modifier.padding(12.dp).align(Alignment.CenterHorizontally), fontSize = 34.sp)
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
                context.stopService(Intent(context, PlayTimeService::class.java))
                preferences.edit().putLong("play_deadline_ms", 0L).putInt("balance_seconds", remaining).apply()
                balanceSeconds = remaining
                screen = "home"
            }, modifier = Modifier.fillMaxWidth()) { Text("Terminar juego y volver") }
        }
    } else if (screen == "result") {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("¡Buen trabajo!", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Aciertos: $correct de $pairCount", fontSize = 22.sp)
            Text("Saldo de prueba: $balanceMinutes min $balanceRemainderSeconds s", fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { screen = "home"; resolved = emptySet(); selected = emptyMap(); selectedPicture = ""; evaluatedPictures = emptyMap(); correct = 0; feedback = "" }) {
                Text("Volver al inicio")
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("🌱 Aprender para jugar", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344), modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        soundEnabled = !soundEnabled
                        preferences.edit().putBoolean("sound_enabled", soundEnabled).apply()
                    }) { Text(if (soundEnabled) "🔊" else "🔇") }
                    Card(shape = RoundedCornerShape(50), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Text("⏱ $balanceMinutes min", Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF7)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E0D2)), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🦊", fontSize = 54.sp)
                        Text("¡Vamos a aprender!", Modifier.fillMaxWidth(), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("Lee, piensa, empareja y gana minutos para jugar.", Modifier.fillMaxWidth(), fontSize = 16.sp, color = Color(0xFF667085), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF7)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E0D2))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("¿Cómo quieres jugar?", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Al azar", "Mezcladas", "Elegir").forEach { option ->
                                Card(modifier = Modifier.weight(1f).clickable { mode = option }, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = if (mode == option) Color(0xFFEAF2FF) else Color.White), border = androidx.compose.foundation.BorderStroke(if (mode == option) 2.dp else 1.dp, if (mode == option) Color(0xFF3567A8) else Color(0xFFE8E0D2))) {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(when(option) { "Al azar" -> "🎲"; "Mezcladas" -> "🧩"; else -> "📚" }, fontSize = 22.sp)
                                        Text(option, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(when(option) { "Al azar" -> "Una categoría sorpresa"; "Mezcladas" -> "Varias categorías"; else -> "Escoge categoría" }, fontSize = 10.sp, color = Color(0xFF667085))
                                    }
                                }
                            }
                        }
                        if (mode == "Elegir") {
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = { exerciseCategoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("📚 Tema: $selectedExerciseCategory  ▾")
                            }
                            DropdownMenu(
                                expanded = exerciseCategoryMenuExpanded,
                                onDismissRequest = { exerciseCategoryMenuExpanded = false }
                            ) {
                                categories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category) },
                                        onClick = {
                                            selectedExerciseCategory = category
                                            preferences.edit().putString("selected_exercise_category", category).apply()
                                            exerciseCategoryMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Text("Preguntas por partida: $questionCount · Disponibles en ${difficulty.lowercase()}: ${availableExercisePairs.size}", fontSize = 14.sp, color = Color(0xFF667085))
                        if (availableExercisePairs.isEmpty()) {
                            Text("No hay preguntas activas para esta dificultad. Entra en Configuración parental → Biblioteca de palabras y activa o crea palabras de dificultad $difficulty.", color = Color(0xFFB3261E), fontWeight = FontWeight.SemiBold)
                        } else if (availableExercisePairs.size < questionCount) {
                            Text("La partida tendrá ${availableExercisePairs.size} preguntas: no hay más palabras activas de esta dificultad.", color = Color(0xFF8A5A00))
                        }
                        Text("Dificultad y recompensa", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Fácil", "Normal", "Difícil").forEach { option ->
                                Card(modifier = Modifier.weight(1f).clickable { difficulty = option }, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = if (difficulty == option) Color(0xFFEAF2FF) else Color.White), border = androidx.compose.foundation.BorderStroke(if (difficulty == option) 2.dp else 1.dp, if (difficulty == option) Color(0xFF3567A8) else Color(0xFFE8E0D2))) {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(option, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(when(option) { "Normal" -> "$normalRewardMinutes min"; "Difícil" -> "$hardRewardMinutes min"; else -> "$easyRewardMinutes min" }, fontSize = 12.sp, color = Color(0xFF667085))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = {
                    if (availableExercisePairs.size >= questionCount) {
                        activeExercisePairs = chooseAdaptivePairs(availableExercisePairs, questionCount, learningStats, learningTurn)
                        activeExercisePairs.forEach { pair ->
                            learningTurn += 1
                            val state = learningStats[pair.word] ?: WordLearning()
                            learningStats = learningStats + (pair.word to state.copy(lastShownTurn = learningTurn))
                        }
                        preferences.edit().putInt("learning_turn_v1", learningTurn).apply()
                        saveLearningStats(preferences, learningStats)
                        resolved = emptySet(); selected = emptyMap(); selectedPicture = ""; evaluatedPictures = emptyMap(); correct = 0; feedback = ""; screen = "exercise"
                    }
                }, enabled = availableExercisePairs.size >= questionCount, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF3567A8))) {
                    Text("📚 Empezar ejercicio", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { pinEntry = ""; pinError = false; screen = "parent-pin" }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("⚙️ Configuración parental", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { pinEntry = ""; pinError = false; screen = "exit-pin" }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("🔒 Salir de la aplicación", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(4.dp))
                Text("Completa ejercicios para acumular minutos de juego.", fontSize = 12.sp, color = Color(0xFF667085))
                Button(onClick = {
                    if (balanceSeconds > 0) {
                        val deadline = System.currentTimeMillis() + balanceSeconds.toLong() * 1000L
                        preferences.edit().putLong("play_deadline_ms", deadline).putBoolean("expiry_relaunch_attempted", false).apply()
                        androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, PlayTimeService::class.java).setAction(PlayTimeService.ACTION_START))
                        screen = "play-time"
                        (context as? ComponentActivity)?.moveTaskToBack(true)
                    }
                }, enabled = balanceSeconds > 0, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF267A55), disabledContainerColor = Color(0xFFE7E1D8), disabledContentColor = Color(0xFF667085))) {
                    Text(if (balanceSeconds > 0) "▶ Jugar con el tiempo ganado" else "🔒 Sin tiempo para jugar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (screen == "parent") item {
                Text("Configuración parental pendiente: PIN, categorías y biblioteca de palabras se añadirán en una siguiente fase.")
            }
        }
    }
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
