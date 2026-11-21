package es.aprenderparajugar

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class QuotaDifficulty(val id: String, val label: String)

private val quotaDifficulties = listOf(
    QuotaDifficulty("easy", "Fácil"),
    QuotaDifficulty("normal", "Normal"),
    QuotaDifficulty("hard", "Difícil")
)

@Composable
internal fun QuotaSystemSettingsScreen(preferences: SharedPreferences) {
    val resetMode = remember {
        mutableStateOf(preferences.getString("quota_reset_mode", "either") ?: "either")
    }
    var resetTime by remember {
        mutableStateOf(preferences.getString("quota_reset_time", "00:00") ?: "00:00")
    }
    var timeError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Sistemas de cuotas", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(
            "Configura cuántas veces puede utilizarse cada dificultad antes de que quede temporalmente bloqueada.",
            fontSize = 14.sp,
            color = Color(0xFF667085)
        )

        QuotaModalityCard("Tarjetas", "cards", preferences)
        QuotaModalityCard("Palabras", "words", preferences)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Secuencias", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                listOf(
                    "temporal" to "Orden temporal",
                    "procesos" to "Procesos",
                    "causa" to "Causa y consecuencia",
                    "ciclos" to "Ciclos"
                ).forEach { (typeId, typeLabel) ->
                    Text(typeLabel, fontWeight = FontWeight.SemiBold)
                    quotaDifficulties.forEach { difficulty ->
                        QuotaDifficultyRow(
                            id = "sequences_${typeId}_${difficulty.id}",
                            label = difficulty.label,
                            preferences = preferences
                        )
                    }
                    if (typeId != "ciclos") HorizontalDivider(color = Color(0xFFE8EAF0))
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF2FF))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Reinicio de cuotas", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Indica cuándo se recuperan los intentos de todas las modalidades limitadas.",
                    fontSize = 13.sp,
                    color = Color(0xFF667085)
                )
                QuotaResetOption(
                    selected = resetMode.value == "exhausted",
                    title = "Al agotar todas las cuotas",
                    description = "Cuando se hayan consumido todos los intentos limitados.",
                    onSelect = {
                        resetMode.value = "exhausted"
                        preferences.edit().putString("quota_reset_mode", "exhausted").apply()
                    }
                )
                QuotaResetOption(
                    selected = resetMode.value == "daily",
                    title = "Al comenzar una nueva jornada",
                    description = "A la hora diaria configurada.",
                    onSelect = {
                        resetMode.value = "daily"
                        preferences.edit().putString("quota_reset_mode", "daily").apply()
                    }
                )
                QuotaResetOption(
                    selected = resetMode.value == "either",
                    title = "Al ocurrir cualquiera de las dos",
                    description = "Al agotarse todas las cuotas o al comenzar una nueva jornada.",
                    onSelect = {
                        resetMode.value = "either"
                        preferences.edit().putString("quota_reset_mode", "either").apply()
                    }
                )
                HorizontalDivider(color = Color(0xFFD5DFEE))
                Text("Hora de reinicio diario", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = resetTime,
                    onValueChange = { value ->
                        val filtered = value.filter { it.isDigit() || it == ':' }.take(5)
                        resetTime = filtered
                        val valid = filtered.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))
                        timeError = !valid
                        if (valid) preferences.edit().putString("quota_reset_time", filtered).apply()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Hora (HH:mm)") },
                    placeholder = { Text("00:00") },
                    isError = timeError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(
                    "Se utiliza la hora local del dispositivo. El valor inicial es 00:00.",
                    fontSize = 12.sp,
                    color = Color(0xFF667085)
                )
            }
        }

        Text(
            "Los ajustes se guardan automáticamente en este dispositivo. La aplicación de las cuotas y sus bloqueos se conectará en una fase posterior.",
            fontSize = 13.sp,
            color = Color(0xFF667085)
        )
    }
}

@Composable
private fun QuotaModalityCard(
    title: String,
    modalityId: String,
    preferences: SharedPreferences
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            quotaDifficulties.forEach { difficulty ->
                QuotaDifficultyRow(
                    id = "${modalityId}_${difficulty.id}",
                    label = difficulty.label,
                    preferences = preferences
                )
            }
        }
    }
}

@Composable
private fun QuotaDifficultyRow(
    id: String,
    label: String,
    preferences: SharedPreferences
) {
    var unlimited by remember(id) {
        mutableStateOf(preferences.getBoolean("quota_${id}_unlimited", true))
    }
    var attempts by remember(id) {
        mutableIntStateOf(preferences.getInt("quota_${id}_attempts", 3).coerceAtLeast(1))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = attempts.toString(),
            onValueChange = { value ->
                val number = value.filter(Char::isDigit).take(3)
                if (number.isNotBlank()) {
                    attempts = number.toInt().coerceIn(1, 999)
                    preferences.edit().putInt("quota_${id}_attempts", attempts).apply()
                }
            },
            modifier = Modifier.weight(0.65f),
            enabled = !unlimited,
            singleLine = true,
            label = { Text("Intentos") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Checkbox(
                checked = unlimited,
                onCheckedChange = { checked ->
                    unlimited = checked
                    preferences.edit().putBoolean("quota_${id}_unlimited", checked).apply()
                }
            )
            Text("Ilimitado", fontSize = 10.sp, color = Color(0xFF667085))
        }
    }
}

@Composable
private fun QuotaResetOption(
    selected: Boolean,
    title: String,
    description: String,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 12.sp, color = Color(0xFF667085))
        }
    }
}
