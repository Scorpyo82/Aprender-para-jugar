package es.aprenderparajugar

import android.os.Bundle
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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

private data class PairItem(val word: String, val picture: String)
private val samplePairs = listOf(
    PairItem("mamá", "👩"), PairItem("sol", "☀️"),
    PairItem("casa", "🏠"), PairItem("gato", "🐈"),
    PairItem("luna", "🌙"), PairItem("pan", "🥖")
)

@Composable
private fun AprenderApp() {
    var screen by remember { mutableStateOf("home") }
    var mode by remember { mutableStateOf("Al azar") }
    var difficulty by remember { mutableStateOf("Fácil") }
    var balanceSeconds by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(mapOf<String, String>()) }
    var resolved by remember { mutableStateOf(setOf<String>()) }
    var correct by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf("") }
    val pairCount = 4
    val rewardSeconds = when (difficulty) { "Normal" -> 300; "Difícil" -> 420; else -> 120 }
    val balanceMinutes = balanceSeconds / 60
    val balanceRemainderSeconds = balanceSeconds % 60

    if (screen == "exercise") {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Une las palabras con sus dibujos", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color(0xFF284B63))
            Text("1. Elige una palabra.  2. Toca su dibujo.", fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    samplePairs.take(pairCount).forEach { item ->
                        val isSelected = selected["word"] == item.word
                        val isResolved = item.word in resolved
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (!isResolved) {
                                    selected = selected + ("word" to item.word)
                                    feedback = "Busca el dibujo de " + item.word
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
                    samplePairs.take(pairCount).reversed().forEach { item ->
                        Card(Modifier.fillMaxWidth().clickable {
                            val word = selected["word"]
                            if (word != null && word !in resolved) {
                                val match = samplePairs.first { it.word == word }
                                resolved = resolved + word
                                val isCorrect = match.picture == item.picture
                                val newCorrect = correct + if (isCorrect) 1 else 0
                                correct = newCorrect
                                feedback = if (isCorrect) "Muy bien: " + word + " es correcto" else "No es esa pareja. Sigue con otra."
                                selected = emptyMap()
                                if (resolved.size == pairCount) {
                                    balanceSeconds += (rewardSeconds * newCorrect) / pairCount
                                    screen = "result"
                                }
                            }
                        }, colors = CardDefaults.cardColors(containerColor = Color(0xFFF0E8F5))) {
                            Text(item.picture, Modifier.padding(12.dp).align(Alignment.CenterHorizontally), fontSize = 34.sp)
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
            Button(onClick = { screen = "home"; resolved = emptySet(); selected = emptyMap(); correct = 0; feedback = "" }) {
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
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Tiempo ganado", fontSize = 16.sp)
                        Text("$balanceMinutes min $balanceRemainderSeconds s", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text("Prototipo: el saldo aún no se guarda al cerrar la app.")
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
                Text("Dificultad", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Fácil", "Normal", "Difícil").forEach { option ->
                        if (difficulty == option) Button(onClick = { difficulty = option }, modifier = Modifier.weight(1f)) { Text(option) }
                        else OutlinedButton(onClick = { difficulty = option }, modifier = Modifier.weight(1f)) { Text(option) }
                    }
                }
                Text("Recompensa máxima orientativa: ${rewardSeconds / 60} min")
            }
            item {
                Button(onClick = { resolved = emptySet(); selected = emptyMap(); correct = 0; feedback = ""; screen = "exercise" }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF3567A8))) {
                    Text("📚 Empezar ejercicio", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { screen = "parent" }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("⚙️ Configuración parental", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
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
