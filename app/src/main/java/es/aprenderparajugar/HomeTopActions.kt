package es.aprenderparajugar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomeTopActions(
    soundEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onToggleSound: () -> Unit,
    onOpenHelp: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onOpenSettings,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("⚙️", fontSize = 18.sp) }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onToggleSound,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text(if (soundEnabled) "🔊" else "🔇", fontSize = 18.sp) }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onOpenHelp,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("❔ Ayuda", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
