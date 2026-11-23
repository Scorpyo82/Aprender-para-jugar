package es.aprenderparajugar

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
internal fun DeviceSetupContent(
    context: Context,
    preferences: SharedPreferences,
    isStarsMode: Boolean,
    setupAccessibilityEnabled: MutableState<Boolean>,
    restrictedSettingsDone: MutableState<Boolean>,
    batterySettingsDone: MutableState<Boolean>,
    xiaomiWindowPermissionDone: MutableState<Boolean>,
    readAccessibilityEnabled: () -> Boolean
) {
                        Text("Asistente de configuración", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        if (isStarsMode) Text("Puedes dejar estos permisos preparados para cuando quieras utilizar el modo Tiempo. En modo Estrellas no se utilizarán para controlar la salida ni el saldo.", fontSize = 14.sp, color = Color(0xFF667085))
                        setupAccessibilityEnabled.value = readAccessibilityEnabled()
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (setupAccessibilityEnabled.value) Color(0xFFE5F5E9) else Color(0xFFF0EDF7))) {
                            Text(if (setupAccessibilityEnabled.value) "✓ COMPLETADO · Servicio de accesibilidad activado" else "○ PENDIENTE · Activa el servicio de accesibilidad", modifier = Modifier.padding(12.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (setupAccessibilityEnabled.value) Color(0xFF24733B) else Color(0xFF6B5AA6))
                        }
                        Text("Para que la protección de la aplicación funcione, Android requiere que una persona autorice manualmente el servicio de accesibilidad. Esta guía te llevará a los ajustes necesarios.", fontSize = 15.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        if (!restrictedSettingsDone.value) {
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (restrictedSettingsDone.value) "✓" else "1", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (restrictedSettingsDone.value) Color(0xFF24733B) else Color(0xFF667085))
                            Text("Permitir ajustes restringidos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Al instalar la aplicación desde un APK, Android puede bloquear la activación del servicio. Abre la información de la aplicación y busca el menú de tres puntos (⋮). Si aparece «Permitir ajustes restringidos», selecciónalo y confirma.", fontSize = 14.sp, color = Color(0xFF667085))
                        Button(onClick = {
                            preferences.edit().putBoolean("device_setup_in_progress", true).putBoolean("parent_area_active", true).commit()
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "No se pudieron abrir los ajustes de la aplicación.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("1. Abrir información de la aplicación") }
                        OutlinedButton(onClick = { restrictedSettingsDone.value = !restrictedSettingsDone.value; preferences.edit().putBoolean("restricted_settings_done", restrictedSettingsDone.value).apply() }, modifier = Modifier.fillMaxWidth()) { Text(if (restrictedSettingsDone.value) "✓ Paso 1 completado" else "Marcar paso 1 como completado") }
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
}
if (!setupAccessibilityEnabled.value) {
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (setupAccessibilityEnabled.value) "✓" else "2", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (setupAccessibilityEnabled.value) Color(0xFF24733B) else Color(0xFF667085))
                            Text("Activar accesibilidad", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Después, abre Accesibilidad, busca «Aprender para jugar» dentro de las aplicaciones o servicios instalados y activa el interruptor. Android puede mostrar una advertencia que tendrás que aceptar. Al terminar, vuelve a la aplicación.", fontSize = 14.sp, color = Color(0xFF667085))
                        Button(onClick = {
                            preferences.edit().putBoolean("device_setup_in_progress", true).putBoolean("parent_area_active", true).commit()
                            try {
                                context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "No se pudieron abrir los ajustes de accesibilidad.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("2. Abrir ajustes de accesibilidad") }
                        Text("Nota: la aplicación no puede activar estos permisos por sí sola. Si «Permitir ajustes restringidos» no aparece, intenta primero activar el servicio desde Accesibilidad y vuelve después a la información de la aplicación.", fontSize = 13.sp, color = Color(0xFF667085))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
}
if (!batterySettingsDone.value) {
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (batterySettingsDone.value) "✓" else "3", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (batterySettingsDone.value) Color(0xFF24733B) else Color(0xFF667085))
                            Text("Evitar que Xiaomi cierre la aplicación", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Para que la protección siga funcionando en segundo plano, abre la información de la aplicación. Entra en «Ahorro de batería» o «Batería» y selecciona «Sin restricciones» si aparece. En esa misma ficha, desactiva «Pausar la actividad de la aplicación si no se utiliza». Los nombres pueden variar según MIUI/HyperOS.", fontSize = 14.sp, color = Color(0xFF667085))
                        Button(onClick = {
                            preferences.edit().putBoolean("device_setup_in_progress", true).putBoolean("parent_area_active", true).commit()
                            try {
                                context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context, "Abre manualmente Ajustes > Aplicaciones > Aprender para jugar > Batería.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("3. Revisar ajustes de batería") }
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        OutlinedButton(onClick = { batterySettingsDone.value = !batterySettingsDone.value; preferences.edit().putBoolean("battery_setup_done", batterySettingsDone.value).apply() }, modifier = Modifier.fillMaxWidth()) { Text(if (batterySettingsDone.value) "✓ Ajustes de batería completados" else "Marcar ajustes de batería como completados") }
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
}
if (!xiaomiWindowPermissionDone.value) {
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (xiaomiWindowPermissionDone.value) "✓" else "4", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (xiaomiWindowPermissionDone.value) Color(0xFF24733B) else Color(0xFF667085))
                            Text("Permitir ventanas en segundo plano (Xiaomi)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("En tu móvil, esta autorización es necesaria para que la aplicación pueda volver a mostrarse cuando el servicio de accesibilidad tenga que intervenir. Pulsa el botón, entra en «Otros permisos» y activa «Abrir nuevas ventanas mientras se ejecuta en segundo plano». En la misma pantalla puedes comprobar los otros permisos, pero no actives «Accesos directos de la pantalla de inicio» ni «Mostrar en la pantalla de bloqueo» salvo que detectemos que también hacen falta.", fontSize = 14.sp, color = Color(0xFF667085))
                        Button(onClick = {
                            preferences.edit().putBoolean("device_setup_in_progress", true).putBoolean("parent_area_active", true).commit()
                            try {
                                context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "Abre Ajustes > Aplicaciones > Aprender para jugar > Otros permisos.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("4. Revisar otros permisos de Xiaomi") }
                        OutlinedButton(onClick = { xiaomiWindowPermissionDone.value = !xiaomiWindowPermissionDone.value; preferences.edit().putBoolean("xiaomi_window_permission_done", xiaomiWindowPermissionDone.value).apply() }, modifier = Modifier.fillMaxWidth()) { Text(if (xiaomiWindowPermissionDone.value) "✓ Permiso de ventanas completado" else "Marcar permiso de ventanas como completado") }

                    }
}
