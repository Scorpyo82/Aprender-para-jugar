package es.aprenderparajugar

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun RewardsSettingsContent(
    context: Context,
    preferences: SharedPreferences,
    rewardMode: RewardMode,
    balanceSeconds: MutableState<Int>,
    minimumPlayMinutes: MutableState<Int>,
    easyRewardMinutes: MutableState<Int>,
    normalRewardMinutes: MutableState<Int>,
    hardRewardMinutes: MutableState<Int>,
    wrongAnswerPenalty: MutableState<String>,
    penaltyMenuExpanded: MutableState<Boolean>,
    onOpenQuotaSystem: () -> Unit
) {
                        Text("Saldo de recompensas", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text(if (rewardMode.isStars) "Saldo actual: ${formatRewardBalance(balanceSeconds.value, rewardMode)} estrellas" else "Saldo actual: ${balanceSeconds.value / 60} min ${balanceSeconds.value % 60} s", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("Unidad: Minutos/Estrellas (equivalencia 1:1)", fontSize = 14.sp, color = Color(0xFF667085))
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = {
                                val updated = (balanceSeconds.value + 60).coerceAtMost(24 * 60 * 60)
                                balanceSeconds.value = updated
                                preferences.edit().putInt("balance_seconds", updated).apply()
                            }, modifier = Modifier.fillMaxWidth(), enabled = balanceSeconds.value < 24 * 60 * 60) { Text("Sumar 1 unidad al saldo") }
                            OutlinedButton(onClick = {
                                val updated = (balanceSeconds.value + 5 * 60).coerceAtMost(24 * 60 * 60)
                                balanceSeconds.value = updated
                                preferences.edit().putInt("balance_seconds", updated).apply()
                            }, modifier = Modifier.fillMaxWidth(), enabled = balanceSeconds.value < 24 * 60 * 60) { Text("Sumar 5 unidades al saldo") }
                            OutlinedButton(onClick = {
                                balanceSeconds.value = 0
                                preferences.edit().putInt("balance_seconds", 0).putLong("play_deadline_ms", 0L).putLong("expiry_last_launch_ms", 0L).putBoolean("play_session_authorized", false).apply()
                                context.stopService(Intent(context, PlayTimeService::class.java))
                            }, modifier = Modifier.fillMaxWidth(), enabled = balanceSeconds.value > 0) { Text("Poner a cero el saldo") }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        Text("Recompensas y límites", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Cada bloque agrupa una opción concreta. Los cambios se guardan automáticamente.", fontSize = 14.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Acceso al tiempo de juego", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (rewardMode.isStars) Color.Gray else Color.Unspecified)
                        Text(if (rewardMode.isStars) "Solo disponible en modo Tiempo: saldo mínimo para salir a jugar fuera de la aplicación." else "Tiempo mínimo de saldo necesario para iniciar una nueva sesión.", fontSize = 14.sp, color = Color(0xFF667085))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = {
                                minimumPlayMinutes.value = (minimumPlayMinutes.value - 1).coerceAtLeast(0)
                                preferences.edit().putInt("minimum_play_minutes", minimumPlayMinutes.value).apply()
                            }, enabled = !rewardMode.isStars && minimumPlayMinutes.value > 0) { Text("−") }
                            Text("$minimumPlayMinutes.value min", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (rewardMode.isStars) Color.Gray else Color.Unspecified)
                            OutlinedButton(onClick = {
                                minimumPlayMinutes.value = (minimumPlayMinutes.value + 1).coerceAtMost(60)
                                preferences.edit().putInt("minimum_play_minutes", minimumPlayMinutes.value).apply()
                            }, enabled = !rewardMode.isStars && minimumPlayMinutes.value < 60) { Text("+") }
                        }
                        Text("Entre 0 y 60 minutos. Con 0, cualquier saldo positivo permite empezar.", fontSize = 13.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text("Penalización por respuestas falladas", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Elige cuánto descuentan los errores de la recompensa de esta partida. El saldo acumulado nunca se reduce.", fontSize = 14.sp, color = Color(0xFF667085))
                        androidx.compose.foundation.layout.Box {
                            OutlinedButton(onClick = { penaltyMenuExpanded.value = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(when (wrongAnswerPenalty.value) { "none" -> "Sin penalización ▾"; "full" -> "Cada fallo resta el 100 % de un acierto ▾"; else -> "Cada fallo resta el 50 % de un acierto ▾" })
                            }
                            DropdownMenu(expanded = penaltyMenuExpanded.value, onDismissRequest = { penaltyMenuExpanded.value = false }) {
                                DropdownMenuItem(text = { Text("Sin penalización") }, onClick = { wrongAnswerPenalty.value = "none"; preferences.edit().putString("wrong_answer_penalty", wrongAnswerPenalty.value).apply(); penaltyMenuExpanded.value = false })
                                DropdownMenuItem(text = { Text("Cada fallo resta el 50 % del valor de un acierto") }, onClick = { wrongAnswerPenalty.value = "half"; preferences.edit().putString("wrong_answer_penalty", wrongAnswerPenalty.value).apply(); penaltyMenuExpanded.value = false })
                                DropdownMenuItem(text = { Text("Cada fallo resta el 100 % del valor de un acierto") }, onClick = { wrongAnswerPenalty.value = "full"; preferences.edit().putString("wrong_answer_penalty", wrongAnswerPenalty.value).apply(); penaltyMenuExpanded.value = false })
                            }
                        }
                        Text(when (wrongAnswerPenalty.value) { "none" -> "Los errores no reducen la recompensa de esta partida."; "full" -> "Cada respuesta incorrecta resta el equivalente a un acierto."; else -> "Cada respuesta incorrecta resta la mitad de la recompensa de un acierto." }, fontSize = 14.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text("Recompensa por dificultad", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Minutos/Estrellas que se añaden al saldo según la dificultad del ejercicio.", fontSize = 14.sp, color = Color(0xFF667085))
                        listOf("Fácil", "Normal", "Difícil").forEach { level ->
                            val minutes = when (level) { "Normal" -> normalRewardMinutes.value; "Difícil" -> hardRewardMinutes.value; else -> easyRewardMinutes.value }
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(level, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(onClick = {
                                            when (level) {
                                                "Normal" -> { normalRewardMinutes.value = (normalRewardMinutes.value - 1).coerceAtLeast(0); preferences.edit().putInt("reward_normal_minutes", normalRewardMinutes.value).apply() }
                                                "Difícil" -> { hardRewardMinutes.value = (hardRewardMinutes.value - 1).coerceAtLeast(0); preferences.edit().putInt("reward_hard_minutes", hardRewardMinutes.value).apply() }
                                                else -> { easyRewardMinutes.value = (easyRewardMinutes.value - 1).coerceAtLeast(0); preferences.edit().putInt("reward_easy_minutes", easyRewardMinutes.value).apply() }
                                            }
                                        }, enabled = minutes > 0) { Text("−") }
                                        Text("$minutes Minutos/Estrellas", fontWeight = FontWeight.Bold)
                                        OutlinedButton(onClick = {
                                            when (level) {
                                                "Normal" -> { normalRewardMinutes.value = (normalRewardMinutes.value + 1).coerceAtMost(120); preferences.edit().putInt("reward_normal_minutes", normalRewardMinutes.value).apply() }
                                                "Difícil" -> { hardRewardMinutes.value = (hardRewardMinutes.value + 1).coerceAtMost(120); preferences.edit().putInt("reward_hard_minutes", hardRewardMinutes.value).apply() }
                                                else -> { easyRewardMinutes.value = (easyRewardMinutes.value + 1).coerceAtMost(120); preferences.edit().putInt("reward_easy_minutes", easyRewardMinutes.value).apply() }
                                            }
                                        }, enabled = minutes < 120) { Text("+") }
                                    }
                                }
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text("Los cambios se guardan en este dispositivo.", fontSize = 13.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        Text("Control de intentos", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Configura cuántas veces se puede jugar cada dificultad y cuándo se reinician los intentos.", fontSize = 14.sp, color = Color(0xFF667085))
                        ParentMenuNavigationCard("🔄", "Sistemas de cuotas", "Configurar intentos y reinicios") { parentSection = "quota-system" }
}
