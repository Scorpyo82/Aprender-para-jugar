package es.aprenderparajugar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StatisticsExportOptions(
    includeLearning: Boolean,
    includeUsage: Boolean,
    onLearningChange: (Boolean) -> Unit,
    onUsageChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeLearning, onCheckedChange = onLearningChange)
            Text("Incluir estadísticas de aprendizaje")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeUsage, onCheckedChange = onUsageChange)
            Text("Incluir estadísticas de uso")
        }
    }
}

@Composable
internal fun StatisticsImportOptions(
    showLearning: Boolean,
    showUsage: Boolean,
    importLearning: Boolean,
    importUsage: Boolean,
    onLearningChange: (Boolean) -> Unit,
    onUsageChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.sp)) {
        if (showLearning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = importLearning, onCheckedChange = onLearningChange)
                Column {
                    Text("Importar estadísticas de aprendizaje", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Desactivado por defecto. Si no lo marcas, se conservarán las estadísticas de aprendizaje del dispositivo.",
                        fontSize = 12.sp,
                        color = Color(0xFF667085)
                    )
                }
            }
        }
        if (showUsage) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = importUsage, onCheckedChange = onUsageChange)
                Column {
                    Text("Importar estadísticas de uso", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Desactivado por defecto. Si no lo marcas, se conservarán las estadísticas de uso del dispositivo.",
                        fontSize = 12.sp,
                        color = Color(0xFF667085)
                    )
                }
            }
        }
    }
}
