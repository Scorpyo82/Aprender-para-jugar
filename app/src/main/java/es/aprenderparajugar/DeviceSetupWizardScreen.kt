package es.aprenderparajugar

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private enum class DeviceSetupStep {
    INTRO,
    ACCESSIBILITY,
    RESTRICTED_SETTINGS,
    NOTIFICATIONS,
    COMPLETE
}

@Composable
internal fun DeviceSetupWizardScreen(
    accessibilityEnabled: Boolean,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var step by remember { mutableStateOf(DeviceSetupStep.INTRO) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var notificationPermissionGranted by remember {
        mutableStateOf(isNotificationPermissionReady(context))
    }

    fun refreshPermissions() {
        refreshKey++
        notificationPermissionGranted = isNotificationPermissionReady(context)
    }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissions()
    }

    // Accessibility is the first prerequisite because Android may hide the
    // restricted-settings exception until the service activation is attempted.
    androidx.compose.runtime.LaunchedEffect(accessibilityEnabled, notificationPermissionGranted, refreshKey) {
        if (accessibilityEnabled && step in setOf(
                DeviceSetupStep.INTRO,
                DeviceSetupStep.ACCESSIBILITY,
                DeviceSetupStep.RESTRICTED_SETTINGS
            )
        ) {
            step = if (notificationPermissionGranted) DeviceSetupStep.COMPLETE else DeviceSetupStep.NOTIFICATIONS
        }
        if (!accessibilityEnabled && step == DeviceSetupStep.NOTIFICATIONS) {
            step = DeviceSetupStep.ACCESSIBILITY
        }
        if (accessibilityEnabled && step == DeviceSetupStep.NOTIFICATIONS && notificationPermissionGranted) {
            step = DeviceSetupStep.COMPLETE
        }
    }

    fun openAccessibilitySettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            // Keep the guide visible; the parent can open Android settings manually.
        }
    }

    fun openAppDetails() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
        } catch (_: Exception) {
            // Keep the guide visible if this device does not resolve the settings intent.
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Preparar la protección", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Te guiaremos paso a paso. La configuración solo terminará cuando podamos comprobar los permisos obligatorios.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val currentStep = when (step) {
            DeviceSetupStep.INTRO -> 1
            DeviceSetupStep.ACCESSIBILITY, DeviceSetupStep.RESTRICTED_SETTINGS -> 1
            DeviceSetupStep.NOTIFICATIONS -> 2
            DeviceSetupStep.COMPLETE -> 3
        }
        Text("PASO $currentStep DE 3", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    DeviceSetupStep.INTRO -> {
                        Text("Vamos a activar la protección del tiempo", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Aprender para jugar necesita Accesibilidad para detectar cuándo se abandona la aplicación y recuperar la pantalla de aprendizaje cuando se agote el tiempo.")
                        Text("También necesita permiso para mostrar las notificaciones de la cuenta atrás.")
                        Button(onClick = { step = DeviceSetupStep.ACCESSIBILITY }, modifier = Modifier.fillMaxWidth()) {
                            Text("Comenzar")
                        }
                    }
                    DeviceSetupStep.ACCESSIBILITY -> {
                        Text("1. Activar Accesibilidad", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Busca «Aprender para jugar» en la lista de servicios instalados e intenta activar el interruptor. Android puede mostrar una advertencia: léela y confirma si estás de acuerdo.")
                        Text(
                            if (accessibilityEnabled) "Permiso verificado." else "Cuando vuelvas, comprobaremos automáticamente si se ha activado."
                        )
                        Button(onClick = { openAccessibilitySettings() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Abrir ajustes de Accesibilidad")
                        }
                        if (!accessibilityEnabled) {
                            OutlinedButton(onClick = { step = DeviceSetupStep.RESTRICTED_SETTINGS }, modifier = Modifier.fillMaxWidth()) {
                                Text("Android no me deja activarlo")
                            }
                        }
                    }
                    DeviceSetupStep.RESTRICTED_SETTINGS -> {
                        Text("Permitir ajustes restringidos", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("En algunas instalaciones mediante APK, Android bloquea la activación del servicio. No has hecho nada mal.")
                        Text("Abre la información de Aprender para jugar, pulsa el menú de tres puntos (⋮) y selecciona «Permitir ajustes restringidos», si aparece.")
                        Button(onClick = { openAppDetails() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Abrir información de la aplicación")
                        }
                        OutlinedButton(onClick = { step = DeviceSetupStep.ACCESSIBILITY; openAccessibilitySettings() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Volver a intentar Accesibilidad")
                        }
                    }
                    DeviceSetupStep.NOTIFICATIONS -> {
                        Text("2. Permitir notificaciones", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Las notificaciones muestran el tiempo restante, los avisos de los últimos minutos y cuándo se ha agotado el tiempo.")
                        Text(
                            if (notificationPermissionGranted) "Notificaciones verificadas."
                            else "Todavía no podemos mostrar las notificaciones necesarias para el modo Tiempo."
                        )
                        if (Build.VERSION.SDK_INT >= 33 &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            Button(
                                onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Permitir notificaciones") }
                        } else {
                            Button(onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    )
                                } catch (_: Exception) { }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("Abrir ajustes de notificaciones")
                            }
                        }
                        OutlinedButton(onClick = { refreshPermissions() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Comprobar de nuevo")
                        }
                    }
                    DeviceSetupStep.COMPLETE -> {
                        Text("¡Protección preparada!", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Accesibilidad y notificaciones están activadas. Ya puedes finalizar la configuración.")
                        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                            Text("Finalizar")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Salir")
            }
            if (step != DeviceSetupStep.INTRO && step != DeviceSetupStep.COMPLETE) {
                OutlinedButton(
                    onClick = {
                        step = when (step) {
                            DeviceSetupStep.RESTRICTED_SETTINGS -> DeviceSetupStep.ACCESSIBILITY
                            DeviceSetupStep.NOTIFICATIONS -> DeviceSetupStep.ACCESSIBILITY
                            else -> DeviceSetupStep.INTRO
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Volver") }
            }
        }
    }
}

internal fun isNotificationPermissionReady(context: android.content.Context): Boolean {
    val runtimeGranted = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}
