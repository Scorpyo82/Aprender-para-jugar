package es.aprenderparajugar

import android.net.Uri
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import java.io.FileInputStream
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
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
    isStarsMode: Boolean = false,
    message: String,
    fontFamily: FontFamily,
    onBack: () -> Unit,
    onPurchase: (StoredObject) -> Boolean
) {
    var selectedCollectible by remember { mutableStateOf<StoredObject?>(null) }
    var pendingPurchase by remember { mutableStateOf<StoredObject?>(null) }
    var celebrationObject by remember { mutableStateOf<StoredObject?>(null) }
    val visibleObjects = objects.filter { it.folderName in ownedFolders || it.metadata.availableInStore }.sortedBy { it.metadata.name.lowercase() }
    val ordinaryObjects = visibleObjects.filterNot { it.metadata.collectible }
    val collectibles = visibleObjects.filter { it.metadata.collectible }
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
                        formatRewardBalance(balanceSeconds, if (isStarsMode) RewardMode.STARS else RewardMode.TIME),
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
            if (ordinaryObjects.isNotEmpty()) {
                item { Text("Objetos", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF493B82), fontFamily = fontFamily) }
                items(ordinaryObjects, key = { it.folderName }) { stored ->
                    InventoryObjectCard(stored, stored.folderName in ownedFolders, balanceSeconds, isStarsMode, fontFamily) { pendingPurchase = it }
                }
            }
            if (collectibles.isNotEmpty()) {
                item {
                    Text("Coleccionables", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF493B82), fontFamily = fontFamily)
                    Text("Descubre y reúne piezas especiales.", fontSize = 13.sp, color = Color(0xFF667085), fontFamily = fontFamily)
                }
                items(collectibles, key = { it.folderName }) { stored ->
                    val owned = stored.folderName in ownedFolders
                    val cost = stored.metadata.storePriceMinutes.coerceAtLeast(0) * 60
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = owned) { selectedCollectible = stored },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = if (owned) Color(0xFFE7F4E8) else Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (owned) Color(0xFF9BCDA2) else Color(0xFFD8CDEB))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                                val imageFile = java.io.File(stored.directory, stored.metadata.image)
                                if (imageFile.extension.equals("gif", true)) {
                                    AndroidView(
                                        factory = { ctx -> GifObjectView(ctx) },
                                        update = { view -> view.setGif(imageFile, animate = false) },
                                        modifier = Modifier.size(84.dp).then(if (owned) Modifier else Modifier.blur(5.dp))
                                    )
                                } else {
                                    AndroidView(
                                        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; layoutParams = ViewGroup.LayoutParams(180, 180) } },
                                        update = { view -> runCatching { view.setImageURI(Uri.fromFile(imageFile)) }.onFailure { view.setImageResource(android.R.drawable.ic_menu_gallery) } },
                                        modifier = Modifier.size(84.dp).then(if (owned) Modifier else Modifier.blur(5.dp))
                                    )
                                }
                                if (!owned) Text("?", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(if (owned) stored.metadata.name else "Coleccionable misterioso", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF253344), fontFamily = fontFamily)
                                Text(if (owned) "✓ CONSEGUIDO · Pulsa para ampliar" else "Descubre qué se esconde", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (owned) Color(0xFF24733B) else Color(0xFF6B55A2), fontFamily = fontFamily)
                                if (!owned && stored.metadata.availableInStore) {
                                    Text(if (isStarsMode) "Precio · ${stored.metadata.storePriceMinutes} ⭐" else "Precio · ${stored.metadata.storePriceMinutes} min", fontSize = 13.sp, color = Color(0xFF667085), fontFamily = fontFamily)
                                    Button(onClick = { pendingPurchase = stored }, modifier = Modifier.fillMaxWidth()) { Text(if (balanceSeconds >= cost) "Comprar" else "Ver precio", fontFamily = fontFamily) }
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
    pendingPurchase?.let { pending ->
        val priceSeconds = pending.metadata.storePriceMinutes.coerceAtLeast(0) * 60
        val enoughBalance = balanceSeconds >= priceSeconds
        val remainingSeconds = (balanceSeconds - priceSeconds).coerceAtLeast(0)
        AlertDialog(
            onDismissRequest = { pendingPurchase = null },
            title = { Text("Confirmar compra", fontWeight = FontWeight.Bold, color = Color(0xFF493B82), fontFamily = fontFamily) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val previewFile = java.io.File(pending.directory, pending.metadata.image)
                    if (previewFile.extension.equals("gif", true)) {
                        AndroidView(factory = { ctx -> GifObjectView(ctx) }, update = { it.setGif(previewFile, animate = false) }, modifier = Modifier.fillMaxWidth().height(112.dp).blur(5.dp))
                    } else {
                        AndroidView(
                            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                            update = { view -> runCatching { view.setImageURI(Uri.fromFile(previewFile)) }.onFailure { view.setImageResource(android.R.drawable.ic_menu_gallery) } },
                            modifier = Modifier.fillMaxWidth().height(112.dp).blur(5.dp)
                        )
                    }
                    Text("¿Quieres descubrir este coleccionable?", fontFamily = fontFamily)
                    Text(if (isStarsMode) "Precio: ${pending.metadata.storePriceMinutes} estrellas" else "Precio: ${pending.metadata.storePriceMinutes} minutos", fontWeight = FontWeight.Bold, fontFamily = fontFamily)
                    Text(if (isStarsMode) "Tu saldo actual: ${formatRewardBalance(balanceSeconds, RewardMode.STARS)} estrellas" else "Tu saldo actual: ${formatInventoryTime(balanceSeconds)}", fontFamily = fontFamily)
                    Text(if (isStarsMode) "Saldo después de comprar: ${formatRewardBalance(remainingSeconds, RewardMode.STARS)} estrellas" else "Saldo después de comprar: ${formatInventoryTime(remainingSeconds)}", fontFamily = fontFamily)
                    if (!enoughBalance) {
                        Text(if (isStarsMode) "Te faltan ${formatRewardBalance(priceSeconds - balanceSeconds, RewardMode.STARS)} estrellas para poder comprarlo." else "Te faltan ${formatInventoryTime(priceSeconds - balanceSeconds)} para poder comprarlo.", color = Color(0xFFB3261E), fontWeight = FontWeight.SemiBold, fontFamily = fontFamily)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    pendingPurchase = null
                    val purchased = onPurchase(pending)
                    if (purchased && pending.metadata.collectible) celebrationObject = pending
                }, enabled = enoughBalance) { Text("Comprar", fontFamily = fontFamily) }
            },
            dismissButton = { OutlinedButton(onClick = { pendingPurchase = null }) { Text("Cancelar", fontFamily = fontFamily) } }
        )
    }
    celebrationObject?.let { awarded ->
        CollectibleCelebrationDialog(awarded, fontFamily) { celebrationObject = null }
    }
    selectedCollectible?.let { selected ->
        AlertDialog(
            onDismissRequest = { selectedCollectible = null },
            title = { Text(selected.metadata.name, fontWeight = FontWeight.Bold, color = Color(0xFF493B82), fontFamily = fontFamily) },
            text = {
                val imageFile = java.io.File(selected.directory, selected.metadata.image)
                if (imageFile.extension.equals("gif", true)) {
                    AndroidView(
                        factory = { ctx -> GifObjectView(ctx) },
                        update = { view -> view.setGif(imageFile, animate = true) },
                        modifier = Modifier.fillMaxWidth().height(320.dp)
                    )
                } else {
                    AndroidView(
                        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true } },
                        update = { view -> runCatching { view.setImageURI(Uri.fromFile(imageFile)) }.onFailure { view.setImageResource(android.R.drawable.ic_menu_gallery) } },
                        modifier = Modifier.fillMaxWidth().height(320.dp)
                    )
                }
            },
            confirmButton = { OutlinedButton(onClick = { selectedCollectible = null }) { Text("Cerrar", fontFamily = fontFamily) } }
        )
    }
}

@Composable
private fun InventoryObjectCard(stored: StoredObject, owned: Boolean, balanceSeconds: Int, isStarsMode: Boolean, fontFamily: FontFamily, onRequestPurchase: (StoredObject) -> Unit) {
    val cost = stored.metadata.storePriceMinutes.coerceAtLeast(0) * 60
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (owned) Color(0xFFE7F4E8) else Color.White)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AndroidView(factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; layoutParams = ViewGroup.LayoutParams(180, 180) } }, update = { view -> runCatching { view.setImageURI(Uri.fromFile(java.io.File(stored.directory, stored.metadata.image))) }.onFailure { view.setImageResource(android.R.drawable.ic_menu_gallery) } }, modifier = Modifier.size(84.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stored.metadata.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF253344), fontFamily = fontFamily)
                Text(stored.collection.replace('_', ' '), fontSize = 12.sp, color = Color(0xFF667085), fontFamily = fontFamily)
                if (owned) Text("✓ CONSEGUIDO", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF24733B), fontFamily = fontFamily)
                else {
                    Text(if (isStarsMode) "Disponible en tienda · ${stored.metadata.storePriceMinutes} ⭐" else "Disponible en tienda · ${stored.metadata.storePriceMinutes} min", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6B55A2), fontFamily = fontFamily)
                    Button(onClick = { onRequestPurchase(stored) }, modifier = Modifier.fillMaxWidth()) { Text(if (balanceSeconds >= cost) "Comprar" else "Ver precio", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily) }
                }
            }
        }
    }
}

private fun formatInventoryTime(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return String.format(java.util.Locale.ROOT, "%02d:%02d", safe / 60, safe % 60)
}

/** Reproduce GIF sin dependencias externas; en miniaturas dibuja únicamente el primer fotograma. */
internal class GifObjectView(context: android.content.Context) : View(context) {
    private var sourcePath: String? = null
    private var movie: Movie? = null
    private var animateMovie = false
    private var fillBounds = false
    private var startedAt = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun setGif(file: java.io.File, animate: Boolean) {
        if (sourcePath == file.absolutePath && animateMovie == animate && movie != null) return
        sourcePath = file.absolutePath
        animateMovie = animate
        fillBounds = false
        movie = runCatching { FileInputStream(file).use { Movie.decodeStream(it) } }.getOrNull()
        startedAt = android.os.SystemClock.uptimeMillis()
        invalidate()
    }

    fun setGif(resourceId: Int, animate: Boolean, fill: Boolean = false) {
        val resourceKey = "resource:$resourceId"
        if (sourcePath == resourceKey && animateMovie == animate && fillBounds == fill && movie != null) return
        sourcePath = resourceKey
        animateMovie = animate
        fillBounds = fill
        movie = runCatching {
            context.resources.openRawResource(resourceId).use { Movie.decodeStream(it) }
        }.getOrNull()
        startedAt = android.os.SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentMovie = movie ?: return
        val sourceWidth = currentMovie.width().coerceAtLeast(1)
        val sourceHeight = currentMovie.height().coerceAtLeast(1)
        val scale = if (fillBounds) maxOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight) else minOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
        val drawWidth = sourceWidth * scale
        val drawHeight = sourceHeight * scale
        val save = canvas.save()
        canvas.translate((width - drawWidth) / 2f, (height - drawHeight) / 2f)
        canvas.scale(scale, scale)
        if (animateMovie) {
            val duration = currentMovie.duration().takeIf { it > 0 } ?: 1000
            currentMovie.setTime(((android.os.SystemClock.uptimeMillis() - startedAt) % duration).toInt())
        } else {
            currentMovie.setTime(0)
        }
        currentMovie.draw(canvas, 0f, 0f, paint)
        canvas.restoreToCount(save)
        if (animateMovie) postInvalidateOnAnimation()
    }
}
