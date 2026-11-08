package es.aprenderparajugar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PinSetupScreen(
    pinDraft: String,
    confirmationDraft: String,
    error: String,
    onPinDraftChange: (String) -> Unit,
    onConfirmationDraftChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔐 Protege el control parental", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF253344))
        Spacer(Modifier.height(10.dp))
        Text(
            "Antes de continuar, crea un PIN personal de 4 cifras. Lo necesitarás para acceder a la configuración parental.",
            fontSize = 16.sp,
            color = Color(0xFF667085)
        )
        Text(
            "Guárdalo en un lugar seguro. Si lo olvidas, tendrás que restablecer la aplicación borrando sus datos desde los ajustes de Android. Esto elimina el contenido guardado localmente; conserva copias de tus bibliotecas.",
            fontSize = 13.sp,
            color = Color(0xFF8A4B08)
        )
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = pinDraft,
            onValueChange = { onPinDraftChange(it.filter(Char::isDigit).take(4)) },
            label = { Text("Nuevo PIN de 4 cifras") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = confirmationDraft,
            onValueChange = { onConfirmationDraftChange(it.filter(Char::isDigit).take(4)) },
            label = { Text("Confirmar PIN") },
            singleLine = true
        )
        if (error.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = Color(0xFFB3261E), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("Guardar PIN y continuar")
        }
    }
}
