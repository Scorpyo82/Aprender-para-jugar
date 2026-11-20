package es.aprenderparajugar

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

private val ObjectCollectionBackground = Color(0xFF101827)
private val ObjectCollectionGold = Color(0xFFFFD968)

@Composable
internal fun StoryObjectCollectionScreen(
    objects: List<StoredObject>,
    onFinished: () -> Unit
) {
    var currentIndex by remember(objects) { mutableStateOf(0) }
    var animating by remember(objects) { mutableStateOf(false) }
    val currentObject = objects.getOrNull(currentIndex) ?: return
    val image = remember(currentObject.directory.absolutePath, currentObject.metadata.image) {
        BitmapFactory.decodeFile(File(currentObject.directory, currentObject.metadata.image).absolutePath)
    }
    val progress by animateFloatAsState(
        targetValue = if (animating) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        finishedListener = { value ->
            if (value == 1f) {
                if (currentIndex + 1 < objects.size) currentIndex++ else onFinished()
                animating = false
            }
        },
        label = "object-enter-backpack"
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(ObjectCollectionBackground).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                "¡Has conseguido un objeto!",
                color = ObjectCollectionGold,
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(12.dp)
                        .clip(RoundedCornerShape(24.dp)).background(Color(0xFF26364F)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎒", fontSize = 100.sp)
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (image != null) {
                            Image(
                                bitmap = image.asImageBitmap(),
                                contentDescription = currentObject.metadata.name,
                                modifier = Modifier.size(190.dp).graphicsLayer {
                                    translationX = -progress * 360f
                                    scaleX = 1f - progress * 0.65f
                                    scaleY = 1f - progress * 0.65f
                                    alpha = 1f - progress
                                },
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                "🎁",
                                modifier = Modifier.graphicsLayer {
                                    translationX = -progress * 360f
                                    scaleX = 1f - progress * 0.65f
                                    scaleY = 1f - progress * 0.65f
                                    alpha = 1f - progress
                                },
                                fontSize = 90.sp
                            )
                        }
                        Text(
                            currentObject.metadata.name,
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Text(
                "${currentIndex + 1} de ${objects.size}",
                color = Color.LightGray,
                fontSize = 14.sp
            )
            Button(
                onClick = { if (!animating) animating = true },
                enabled = !animating,
                modifier = Modifier.fillMaxWidth(0.65f)
            ) {
                Text("Guardar", fontSize = 20.sp)
            }
        }
    }
}
