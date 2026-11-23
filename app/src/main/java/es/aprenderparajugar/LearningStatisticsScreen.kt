package es.aprenderparajugar

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LearningStatisticsScreen(
    context: Context,
    expandedModalities: Set<ContentStorage.Modality>,
    onToggleModality: (ContentStorage.Modality) -> Unit
) {
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Resumen de actividad", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$totalCompleted ejercicios completados", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("$totalAttempts intentos registrados · $totalCorrect aciertos · $totalIncorrect errores", fontSize = 14.sp)
                Text("Acierto global: ${percentage(totalCorrect, totalAttempts)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        val modalityAreas = listOf(
            Triple(ContentStorage.Modality.TARJETAS, "Tarjetas", "🃏"),
            Triple(ContentStorage.Modality.PALABRAS, "Palabras", "🔤"),
            Triple(ContentStorage.Modality.SECUENCIAS, "Secuencias", "🧩")
        )
        Text("Estadísticas por modalidad", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        modalityAreas.forEach { (modality, title, icon) ->
            val subset = records.filter { it.third == modality }
            val attempts = subset.sumOf { it.second.optInt("attempts", 0) }
            val correct = subset.sumOf { it.second.optInt("correct", 0) }
            val incorrect = subset.sumOf { it.second.optInt("incorrect", 0) }
            val completed = subset.sumOf { it.second.optInt("completed", 0) }
            val expanded = modality in expandedModalities
            val categoryGroups = subset.groupBy { stored ->
                val category = stored.first.metadata.category
                if (modality == ContentStorage.Modality.SECUENCIAS) {
                    val type = (stored.first.content as? SequenceExerciseContent)?.type?.displayName
                    if (type.isNullOrBlank()) category else "$category · $type"
                } else category
            }.toSortedMap()
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onToggleModality(modality) },
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
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F4F8))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Cómo interpretar estos datos", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Esta aplicación no diagnostica, no realiza evaluaciones clínicas ni determina lo que un niño es o no es capaz de hacer. Solo ofrece actividades y recoge cómo interactúa con ellas. Los resultados dependen de los ejercicios disponibles, de su diseño, de la experiencia previa, de cómo el niño interpreta la actividad y de factores como la atención, la motivación, la memoria o la familiaridad con los patrones y símbolos.", fontSize = 13.sp, color = Color(0xFF667085))
                Text("Un acierto o un error aislado no demuestra que una capacidad esté adquirida o ausente. El niño puede encontrar estrategias, asociaciones o soluciones que el ejercicio no había previsto, e incluso descubrir relaciones que a un adulto podrían pasarle inadvertidas. Conviene observar el proceso, escuchar sus explicaciones y no limitar sus posibilidades a lo que reflejan estas cifras.", fontSize = 13.sp, color = Color(0xFF667085))
                Text("Estas estadísticas son únicamente orientativas y descriptivas. No son pruebas estandarizadas, diagnósticos ni conclusiones pedagógicas definitivas, y no sustituyen la valoración de profesionales cuando sea necesaria.", fontSize = 13.sp, color = Color(0xFF667085))
            }
        }
        Text("Ejercicios con estadísticas legibles: ${records.size}. Si no aparecen datos, completa ejercicios para generar registros.", fontSize = 12.sp, color = Color(0xFF667085))
    }
}
