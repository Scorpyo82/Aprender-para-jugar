package es.aprenderparajugar

import android.os.Bundle
import android.content.Context
import android.net.Uri
import android.widget.ImageView
import android.view.ViewGroup
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
private val samplePairs = listOf(
    PairItem("mamá", "👩"), PairItem("sol", "☀️"),
    PairItem("casa", "🏠"), PairItem("gato", "🐈"),
    PairItem("luna", "🌙"), PairItem("pan", "🥖")
)

@Composable
private fun AprenderApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("aprender_para_jugar", Context.MODE_PRIVATE) }
    var pairs by remember { mutableStateOf(loadPairs(preferences)) }
    var wordDraft by remember { mutableStateOf("") }
    var pictureDraft by remember { mutableStateOf("🐱") }
    var categoryDraft by remember { mutableStateOf("Primeras palabras") }
    var difficultyDraft by remember { mutableStateOf("Fácil") }
    var editingWord by remember { mutableStateOf<String?>(null) }
    var libraryMessage by remember { mutableStateOf("") }
    var libraryPage by remember { mutableStateOf("list") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            pictureDraft = uri.toString()
            libraryMessage = "Imagen seleccionada."
        }
    }
    var screen by remember { mutableStateOf("home") }
    var mode by remember { mutableStateOf("Al azar") }
    var difficulty by remember { mutableStateOf("Fácil") }
    var balanceSeconds by remember { mutableIntStateOf(preferences.getInt("balance_seconds", 0)) }
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
    var pinStored by remember { mutableStateOf(preferences.getString("parent_pin", "1234") ?: "1234") }
    var newPinDraft by remember { mutableStateOf("") }
    var categoryDraftName by remember { mutableStateOf("") }
    var categoryMessage by remember { mutableStateOf("") }
    var categoryToRemove by remember { mutableStateOf("") }
    val categories = (listOf("Primeras palabras", "Casa y familia", "Naturaleza") + pairs.map { it.category }).distinctBy { it.trim().lowercase() }.sorted()
    val exercisePairs = pairs.filter { it.active && it.difficulty == difficulty }.take(4)
    val pairCount = exercisePairs.size
    val rewardSeconds = when (difficulty) { "Normal" -> 300; "Difícil" -> 420; else -> 120 }
    val balanceMinutes = balanceSeconds / 60
    val balanceRemainderSeconds = balanceSeconds % 60

    if (screen == "parent-pin") {
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
                            pairs.forEach { pair ->
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
                            OutlinedTextField(value = categoryDraft, onValueChange = { categoryDraft = it }, label = { Text("Categoría") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
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
                        Button(onClick = { val clean = categoryDraftName.trim(); if (clean.isBlank()) categoryMessage = "Escribe un nombre."; else if (categories.any { it.equals(clean, ignoreCase = true) }) categoryMessage = "Esa categoría ya existe."; else { categoryDraftName = ""; categoryMessage = "Categoría creada. Ya puedes asignarla a una palabra." ; categoryDraftName = clean } }, modifier = Modifier.fillMaxWidth()) { Text("Añadir categoría") }
                        if (categoryMessage.isNotBlank()) Text(categoryMessage, color = Color(0xFF24733B))
                        categories.forEach { name ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${pairs.count { it.category.equals(name, ignoreCase = true) }} palabras", color = Color(0xFF667085))
                                    if (name.equals(categoryToRemove, ignoreCase = true)) {
                                        Text("Mover palabras a:", fontWeight = FontWeight.SemiBold)
                                        categories.filterNot { it.equals(name, ignoreCase = true) }.forEach { destination ->
                                            OutlinedButton(onClick = { pairs = pairs.map { if (it.category.equals(name, ignoreCase = true)) it.copy(category = destination) else it }; savePairs(preferences, pairs); categoryToRemove = ""; categoryMessage = "Palabras reasignadas a $destination." }, modifier = Modifier.fillMaxWidth()) { Text(destination) }
                                        }
                                        OutlinedButton(onClick = { if (pairs.none { it.category.equals(name, ignoreCase = true) }) { categoryToRemove = ""; categoryMessage = "Categoría retirada de la lista." } else categoryMessage = "Primero mueve las palabras a otra categoría." }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                                    } else if (name !in listOf("Primeras palabras", "Casa y familia", "Naturaleza")) {
                                        OutlinedButton(onClick = { categoryToRemove = name; categoryMessage = "" }, modifier = Modifier.fillMaxWidth()) { Text("Eliminar / reasignar") }
                                    }
                                }
                            }
                        }
                    }
                    "rewards" -> item {
                        Text("Recompensas y límites", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Recompensa base: Fácil 2 min · Normal 5 min · Difícil 7 min", fontSize = 17.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Límite diario de elecciones manuales: $dailyManualLimit", fontSize = 17.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { if (dailyManualLimit > 0) { dailyManualLimit--; preferences.edit().putInt("daily_manual_limit", dailyManualLimit).apply() } }) { Text("−") }
                            OutlinedButton(onClick = { if (dailyManualLimit < 10) { dailyManualLimit++; preferences.edit().putInt("daily_manual_limit", dailyManualLimit).apply() } }) { Text("+") }
                        }
                        Text("Los cambios se guardan en este dispositivo.", fontSize = 13.sp, color = Color(0xFF667085))
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
                                        val isCorrect = samplePairs.first { it.word == item.word }.picture == pendingPicture
                                        resolved = resolved + item.word
                                        val newCorrect = correct + if (isCorrect) 1 else 0
                                        correct = newCorrect
                                        evaluatedPictures = evaluatedPictures + (pendingPicture to isCorrect)
                                        feedback = if (isCorrect) "¡Muy bien! «" + item.word + "» corresponde a ese dibujo." else "No coincide. «" + item.word + "» queda resuelta; prueba con otra palabra."
                                        selected = emptyMap()
                                        selectedPicture = ""
                                        if (resolved.size == pairCount) {
                                            balanceSeconds += (rewardSeconds * newCorrect) / pairCount
                                            preferences.edit().putInt("balance_seconds", balanceSeconds).apply()
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
                                val isCorrect = samplePairs.first { it.word == word }.picture == item.picture
                                resolved = resolved + word
                                if (isCorrect) correct += 1
                                evaluatedPictures = evaluatedPictures + (item.picture to isCorrect)
                                feedback = if (isCorrect) "¡Muy bien! «" + word + "» corresponde a ese dibujo." else "No coincide. «" + word + "» queda resuelta; prueba con otra palabra."
                                selected = emptyMap()
                                selectedPicture = ""
                                if (resolved.size == pairCount) {
                                    balanceSeconds += (rewardSeconds * correct) / pairCount
                                    preferences.edit().putInt("balance_seconds", balanceSeconds).apply()
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
                    Text("🌱 Aprender para jugar", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
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
                        Text("Dificultad y recompensa", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Fácil", "Normal", "Difícil").forEach { option ->
                                Card(modifier = Modifier.weight(1f).clickable { difficulty = option }, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = if (difficulty == option) Color(0xFFEAF2FF) else Color.White), border = androidx.compose.foundation.BorderStroke(if (difficulty == option) 2.dp else 1.dp, if (difficulty == option) Color(0xFF3567A8) else Color(0xFFE8E0D2))) {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(option, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(when(option) { "Normal" -> "5 min"; "Difícil" -> "7 min"; else -> "2 min" }, fontSize = 12.sp, color = Color(0xFF667085))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = { resolved = emptySet(); selected = emptyMap(); selectedPicture = ""; evaluatedPictures = emptyMap(); correct = 0; feedback = ""; screen = "exercise" }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF3567A8))) {
                    Text("📚 Empezar ejercicio", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { pinEntry = ""; pinError = false; screen = "parent-pin" }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("⚙️ Configuración parental", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(4.dp))
                Text("Completa ejercicios para acumular minutos de juego.", fontSize = 12.sp, color = Color(0xFF667085))
                Button(onClick = { }, enabled = balanceSeconds > 0, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF267A55), disabledContainerColor = Color(0xFFE7E1D8), disabledContentColor = Color(0xFF667085))) {
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
