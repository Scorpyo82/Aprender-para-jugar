package es.aprenderparajugar

import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Inventario infantil unificado: mezcla objetos conseguidos y objetos comprables.
 * Los objetos que no posee y no están disponibles en tienda no se muestran.
 */
@Composable
internal fun StoryObjectInventoryScreen(
    objects: List<StoredObject>,
    ownedFolders: Set<String>,
    balanceSeconds: Int,
    message: String,
    fontFamily: FontFamily,
    onBack: () -> Unit,
    onPurchase: (StoredObject) -> Unit
) {
    val visibleObjects = objects
        .filter { it.folderName in ownedFolders || it.metadata.availableInStore }
        .sortedBy { it.metadata.name.lowercase() }
    val ownedCount = visibleObjects.count { it.folderName in ownedFolders }
    val purchasableCount = visibleObjects.count { it.folderName !in ownedFolders && it.metadata.availableInStore }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFFF8E8)).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedButton(onClick = onBack) { Text("← Volver al inicio", fontFamily = fontFamily) }
            Text("Mi inventario", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF493B82), fontFamily = fontFamily)
            Text("Aquí puedes ver los objetos que has conseguido y los que puedes comprar.", fontSize = 15.sp, color = Color(0xFF667085), fontFamily = fontFamily)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE9C7))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tu saldo", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF79501C), fontFamily = fontFamily)
                    Text(
                        String.format(java.util.Locale.ROOT, "%02d:%02d", balanceSeconds.coerceAtLeast(0) / 60, balanceSeconds.coerceAtLeast(0) % 60),
                        fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF267A55), fontFamily = fontFamily
                    )
                    Text("$ownedCount conseguidos · $purchasableCount disponibles para comprar", fontSize = 13.sp, color = Color(0xFF667085), fontFamily = fontFamily)
                }
            }
        }
        if (visibleObjects.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🎒", fontSize = 48.sp)
                        Text("Todavía no hay objetos", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF493B82), fontFamily = fontFamily)
                        Text("Cuando consigas objetos en los cuentos o haya objetos disponibles en la tienda, aparecerán aquí.", fontSize = 14.sp, color = Color(0xFF667085), textAlign = TextAlign.Center, fontFamily = fontFamily)
                    }
                }
            }
        } else {
            items(visibleObjects, key = { it.folderName }) { stored ->
                val owned = stored.folderName in ownedFolders
                val priceSeconds = stored.metadata.storePriceMinutes.coerceAtLeast(0) * 60
                val affordable = balanceSeconds >= priceSeconds
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = if (owned) Color(0xFFE7F4E8) else Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (owned) Color(0xFF9BCDA2) else Color(0xFFD8CDEB))
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AndroidView(
                            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; layoutParams = ViewGroup.LayoutParams(180, 180) } },
                            update = { view ->
                                runCatching { view.setImageURI(Uri.fromFile(java.io.File(stored.directory, stored.metadata.image))) }
                                    .onFailure { view.setImageResource(android.R.drawable.ic_menu_gallery) }
                            },
                            modifier = Modifier.size(84.dp)
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(stored.metadata.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF253344), fontFamily = fontFamily)
                            Text(stored.collection.replace('_', ' '), fontSize = 12.sp, color = Color(0xFF667085), fontFamily = fontFamily)
                            if (owned) {
                                Text("✓ CONSEGUIDO", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF24733B), fontFamily = fontFamily)
                            } else {
                                Text("Disponible en tienda · ${stored.metadata.storePriceMinutes} min", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6B55A2), fontFamily = fontFamily)
                                Button(onClick = { onPurchase(stored) }, enabled = affordable, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (affordable) "Comprar" else "Saldo insuficiente", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (message.isNotBlank()) {
            item {
                Text(message, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (message.startsWith("¡Has conseguido")) Color(0xFF24733B) else Color(0xFFB3261E),
                    fontFamily = fontFamily)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
