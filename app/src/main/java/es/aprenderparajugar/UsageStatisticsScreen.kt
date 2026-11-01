package es.aprenderparajugar

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/**
 * Pantalla independiente para consultar las partidas completadas.
 * No modifica las estadísticas de aprendizaje de cada ejercicio.
 */
@Composable
internal fun UsageStatisticsScreen(context: Context) {
    val results = remember(context) {
        ContentStorage.Modality.entries.map { modality ->
            modality to runCatching { UsageStatisticsRepository.read(context, modality) }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Resumen de partidas", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        results.forEach { (modality, result) ->
            val statistics = result.getOrNull()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        when (modality) {
                            ContentStorage.Modality.TARJETAS -> "🃏 Tarjetas"
                            ContentStorage.Modality.PALABRAS -> "🔤 Palabras"
                            ContentStorage.Modality.SECUENCIAS -> "🧩 Secuencias"
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (statistics == null) {
                        Text(
                            "No se pudieron leer las estadísticas: ${result.exceptionOrNull()?.message ?: "error desconocido"}",
                            color = Color(0xFFB3261E),
                            fontSize = 14.sp
                        )
                    } else {
                        val total = statistics.optInt("total", 0)
                        Text("$total partidas completadas", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        if (total == 0) {
                            Text("Todavía no hay partidas registradas.", color = Color(0xFF667085), fontSize = 14.sp)
                        } else if (modality == ContentStorage.Modality.SECUENCIAS) {
                            val byType = statistics.optJSONObject("byType") ?: JSONObject()
                            SequenceType.entries.forEach { type ->
                                val levels = byType.optJSONObject(type.jsonValue) ?: JSONObject()
                                val typeTotal = listOf("Fácil", "Normal", "Difícil").sumOf { levels.optInt(it, 0) }
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(type.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(
                                        "Fácil: ${levels.optInt("Fácil", 0)}  ·  Normal: ${levels.optInt("Normal", 0)}  ·  Difícil: ${levels.optInt("Difícil", 0)}  ·  Total: $typeTotal",
                                        color = Color(0xFF667085),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            val byDifficulty = statistics.optJSONObject("byDifficulty") ?: JSONObject()
                            listOf("Fácil", "Normal", "Difícil").forEach { level ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(level, color = Color(0xFF667085), fontSize = 14.sp)
                                    Text(byDifficulty.optInt(level, 0).toString(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
