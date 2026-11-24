package es.aprenderparajugar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * Contenedor único para todas las opciones de audio de la configuración.
 * El estado expandedKey se comparte entre secciones para permitir un solo
 * panel abierto en toda la pantalla.
 */
@Composable
internal fun AudioOptionCard(
    key: String,
    title: String,
    description: String,
    enabled: Boolean,
    expanded: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onExpandChange: () -> Unit,
    checkboxEnabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentEnabled: Boolean = enabled,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (checkboxEnabled) 1f else 0.42f),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f).clickable(onClick = onExpandChange),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (expanded) "Ocultar opciones ▲" else "Configurar audio ▼",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Checkbox(checked = enabled, onCheckedChange = onEnabledChange, enabled = checkboxEnabled)
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth().alpha(if (contentEnabled) 1f else 0.42f)) {
                    content()
                }
            }
        }
    }
}
