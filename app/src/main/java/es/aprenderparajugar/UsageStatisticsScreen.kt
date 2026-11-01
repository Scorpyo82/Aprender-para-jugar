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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/**
 * Pantalla independiente para consultar las partidas completadas y el estado
 * del contenido disponible en cada biblioteca.
 */
@Composable
internal fun UsageStatisticsScreen(context: Context) {
    val results = remember(context) {
        val preferences = context.getSharedPreferences("aprender_para_jugar", Context.MODE_PRIVATE)
        val disabledWordCategories = preferences.getStringSet("disabled_letter_word_categories_v1", emptySet()) ?: emptySet()
        val disabledSequenceCategories = preferences.getStringSet("disabled_sequence_categories", emptySet()) ?: emptySet()
        ContentStorage.Modality.entries.map { modality ->
            val exercises = runCatching { ExerciseRepository.list(context, modality) }
            val enabledMode = when (modality) {
                ContentStorage.Modality.TARJETAS -> preferences.getBoolean("game_active_cards", true)
                ContentStorage.Modality.PALABRAS -> preferences.getBoolean("game_active_words", true)
                ContentStorage.Modality.SECUENCIAS -> preferences.getBoolean("game_active_sequences", true)
            }
            val disabledCategories = when (modality) {
                ContentStorage.Modality.TARJETAS -> emptySet()
                ContentStorage.Modality.PALABRAS -> disabledWordCategories
                ContentStorage.Modality.SECUENCIAS -> disabledSequenceCategories
            }
            val exerciseList = exercises.getOrNull().orEmpty()
            val activeCount = exerciseList.count { it.metadata.active && enabledMode && it.metadata.category !in disabledCategories }
            val blockedCount = exerciseList.size - activeCount
            val statistics = runCatching { UsageStatisticsRepository.read(context, modality) }
            Triple(modality, exercises, Triple(activeCount, blockedCount, statistics))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Resumen de partidas", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        results.forEach { (modality, exerciseResult, countsAndStatistics) ->
            val (activeCount, blockedCount, statisticsResult) = countsAndStatistics
            val statistics = statisticsResult.getOrNull()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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

                    if (exerciseResult.isFailure) {
                        Text(
                            "No se pudo leer la biblioteca: ${exerciseResult.exceptionOrNull()?.message ?: "error desconocido"}",
                            color = Color(0xFFB3261E),
                            fontSize = 14.sp
                        )
                    } else {
                        val totalExercises = exerciseResult.getOrNull().orEmpty().size
                        Text("Actividades de la biblioteca", fontSize = 14.sp, color = Color(0xFF667085), fontWeight = FontWeight.Medium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CountColumn("$totalExercises", "Total", Modifier.weight(1f), Color(0xFF344054))
                            CountColumn("$activeCount", "Activas", Modifier.weight(1f), Color(0xFF24733B))
                            CountColumn("$blockedCount", "Bloqueadas", Modifier.weight(1f), Color(0xFFB26A22))
                        }
                    }

                    if (statistics == null) {
                        Text(
                            "No se pudieron leer las estadísticas: ${statisticsResult.exceptionOrNull()?.message ?: "error desconocido"}",
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

@Composable
private fun CountColumn(value: String, label: String, modifier: Modifier, valueColor: Color) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, fontSize = 12.sp, color = Color(0xFF667085))
    }
}