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
    var balanceMinutes by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(mapOf<String, String>()) }
    var resolved by remember { mutableStateOf(setOf<String>()) }
    var correct by remember { mutableIntStateOf(0) }
    val pairCount = 4
    val reward = when (difficulty) { "Normal" -> 5; "Difícil" -> 7; else -> 2 }

    if (screen == "exercise") {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Relaciona cada palabra con su dibujo", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Toca una palabra y después el dibujo que le corresponde.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    samplePairs.take(pairCount).forEach { item ->
                        Card(Modifier.fillMaxWidth().clickable {
                            if (item.word !in resolved) selected = selected + ("word" to item.word)
                        }) {
                            Text(item.word, Modifier.padding(16.dp), fontSize = 23.sp, fontWeight = FontWeight.Medium)
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
                                if (match.picture == item.picture) correct++
                                selected = emptyMap()
                                if (resolved.size == pairCount) {
                                    balanceMinutes += (reward * correct) / pairCount
                                    screen = "result"
                                }
                            }
                        }) {
                            Text(item.picture, Modifier.padding(12.dp).align(Alignment.CenterHorizontally), fontSize = 30.sp)
                        }
                    }
                }
            }
            Text("Parejas resueltas: ${resolved.size}/$pairCount")
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
            Text("Saldo de prueba: $balanceMinutes min", fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { screen = "home"; resolved = emptySet(); selected = emptyMap(); correct = 0 }) {
                Text("Volver al inicio")
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Aprender para jugar", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF284B63))
                Text("Cada ejercicio te ayuda a ganar tiempo de juego.", fontSize = 16.sp)
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Tiempo ganado", fontSize = 16.sp)
                        Text("$balanceMinutes min", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text("Prototipo: el saldo aún no se guarda al cerrar la app.")
                    }
                }
            }
            item { Text("¿Cómo quieres practicar?", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Al azar", "Mezcladas").forEach { option ->
                        if (mode == option) Button(onClick = { mode = option }, modifier = Modifier.weight(1f)) { Text(option) }
                        else OutlinedButton(onClick = { mode = option }, modifier = Modifier.weight(1f)) { Text(option) }
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
                Text("Recompensa máxima orientativa: $reward min")
            }
            item {
                Button(onClick = { resolved = emptySet(); selected = emptyMap(); correct = 0; screen = "exercise" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Empezar ejercicio")
                }
            }
            item {
                OutlinedButton(onClick = { screen = "parent" }, modifier = Modifier.fillMaxWidth()) { Text("Zona de familias (demo)") }
            }
            if (screen == "parent") item {
                Text("Configuración parental pendiente: PIN, categorías y biblioteca de palabras se añadirán en una siguiente fase.")
            }
        }
    }
}
