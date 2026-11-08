package es.aprenderparajugar

import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
internal fun SequenceGameScreen(
    exercise: SequenceExercise?,
    selectedOrder: List<Int>,
    playOrder: List<Int>,
    contentFormat: String,
    message: String,
    exerciseFontFamily: FontFamily,
    rewardMessage: String,
    onBack: () -> Unit,
    onSelectCard: (Int) -> Unit
) {
    if (exercise == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("No hay ningún ejercicio seleccionado.")
            Button(onClick = onBack) { Text("Volver a ejercicios") }
        }
        return
    }

    val isTabletLayout = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val finished = selectedOrder.size == exercise.cards.size
    LazyColumn(
        Modifier.fillMaxSize().padding(if (isTabletLayout) 28.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedButton(onClick = onBack) { Text("← Volver a ejercicios") }
            Text(exercise.title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF245C2A))
            Text(
                if (finished) "¡Muy bien! Has completado la secuencia." else "Toca las tarjetas en el orden en que ocurren.",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (finished) Color(0xFF24733B) else Color(0xFF667085)
            )
            Text("Pasos correctos: ${selectedOrder.size}/${exercise.cards.size}", fontSize = 14.sp, color = Color(0xFF667085))
        }
        items(playOrder.size) { displayPosition ->
            val cardIndex = playOrder[displayPosition]
            val alreadySelected = cardIndex in selectedOrder
            val picture = exercise.pictures.getOrNull(cardIndex)?.takeIf { it.isNotBlank() }
                ?: if (exercise.pictures.isEmpty()) "" else ""
            Card(
                Modifier.fillMaxWidth().clickable(enabled = !finished && !alreadySelected) { onSelectCard(cardIndex) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (alreadySelected) Color(0xFFD9F2D9) else Color.White),
                border = BorderStroke(if (alreadySelected) 2.dp else 1.dp, if (alreadySelected) Color(0xFF24733B) else Color(0xFFE0E5EA))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (contentFormat != "Solo texto") {
                        if (picture.startsWith("content://") || picture.startsWith("file://")) {
                            AndroidView(
                                factory = { ctx -> ImageView(ctx).apply {
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                    layoutParams = ViewGroup.LayoutParams(160, 120)
                                } },
                                update = { view ->
                                    try { view.setImageURI(Uri.parse(picture)) }
                                    catch (_: Exception) { view.setImageResource(android.R.drawable.ic_menu_gallery) }
                                },
                                modifier = Modifier.width(if (isTabletLayout) 140.dp else 96.dp).height(if (isTabletLayout) 110.dp else 82.dp).clipToBounds()
                            )
                        } else {
                            Box(
                                Modifier.width(if (isTabletLayout) 140.dp else 96.dp).height(if (isTabletLayout) 110.dp else 82.dp)
                                    .background(if (picture.isBlank()) Color(0xFFF2F4F7) else Color(0xFFEAF5E6), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(picture.ifBlank { "▧" }, fontSize = if (picture.isBlank()) 34.sp else 42.sp, color = Color(0xFF98A2B3))
                            }
                        }
                    }
                    if (contentFormat != "Solo imágenes") {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (alreadySelected) Text("${selectedOrder.indexOf(cardIndex) + 1}.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF24733B))
                            Text(exercise.cards[cardIndex], fontSize = if (isTabletLayout) 22.sp else 18.sp, fontFamily = exerciseFontFamily, fontWeight = FontWeight.SemiBold, color = Color(0xFF253344))
                        }
                    } else if (alreadySelected) {
                        Text("${selectedOrder.indexOf(cardIndex) + 1}.", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF24733B))
                    }
                }
            }
        }
        if (message.isNotBlank()) {
            item { Text(message, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (finished) Color(0xFF24733B) else Color(0xFF72521B)) }
        }
        if (finished) {
            item { Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Elegir otro ejercicio") } }
            item { Text(rewardMessage, fontSize = 14.sp, color = Color(0xFF667085)) }
        }
    }
}
