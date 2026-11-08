package es.aprenderparajugar

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File

/** Mochila contextual del reproductor de cuentos. */
@Composable
internal fun StoryBackpackDialog(
    objects: List<StoredObject>,
    fontFamily: FontFamily,
    onUseObject: (StoredObject) -> Boolean,
    onDismiss: () -> Unit
) {
    var showInvalidFeedback by remember { mutableStateOf(false) }
    LaunchedEffect(showInvalidFeedback) {
        if (showInvalidFeedback) {
            delay(1300)
            showInvalidFeedback = false
        }
    }
    val feedbackAlpha by animateFloatAsState(
        targetValue = if (showInvalidFeedback) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "invalid-object-fade"
    )
    val pulse = rememberInfiniteTransition(label = "invalid-object-pulse")
        .animateFloat(
            initialValue = 0.88f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(260),
                repeatMode = RepeatMode.Reverse
            ),
            label = "invalid-object-scale"
        ).value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mochila de Zorro", fontFamily = fontFamily) },
        text = {
            Box {
                Column {
                    if (objects.isEmpty()) {
                        Text(
                            "No tienes objetos que puedas utilizar en este cuento.",
                            fontFamily = fontFamily
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(objects, key = { it.folderName }) { item ->
                                val objectImage = remember(item.directory.absolutePath, item.metadata.image) {
                                    File(item.directory, item.metadata.image).takeIf { it.isFile }
                                        ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (onUseObject(item)) onDismiss()
                                        else showInvalidFeedback = true
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(64.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (objectImage != null) {
                                                Image(
                                                    bitmap = objectImage.asImageBitmap(),
                                                    contentDescription = item.metadata.name,
                                                    modifier = Modifier.size(60.dp),
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                        }
                                        Text(
                                            text = item.metadata.name,
                                            modifier = Modifier.weight(1f),
                                            fontFamily = fontFamily,
                                            textAlign = TextAlign.Start,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (feedbackAlpha > 0.01f) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🚫",
                            modifier = Modifier.graphicsLayer {
                                alpha = feedbackAlpha
                                scaleX = pulse
                                scaleY = pulse
                            },
                            fontSize = 54.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
