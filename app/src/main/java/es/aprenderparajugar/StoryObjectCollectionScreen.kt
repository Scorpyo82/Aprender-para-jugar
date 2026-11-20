package es.aprenderparajugar

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File

@Composable
internal fun StoryObjectCollectionScreen(objects: List<StoredObject>, onFinished: () -> Unit) {
    val currentObject = objects.firstOrNull() ?: return
    val image = remember(currentObject.directory.absolutePath, currentObject.metadata.image) {
        BitmapFactory.decodeFile(File(currentObject.directory, currentObject.metadata.image).absolutePath)
    }
    val scale = remember(currentObject.folderName) { Animatable(0.08f) }
    val alpha = remember(currentObject.folderName) { Animatable(1f) }
    val travel = remember(currentObject.folderName) { Animatable(0f) }

    LaunchedEffect(currentObject.folderName) {
        scale.animateTo(1.08f, tween(450))
        scale.animateTo(0.94f, tween(220))
        scale.animateTo(1.06f, tween(220))
        scale.animateTo(0.96f, tween(220))
        delay(890)
        scale.animateTo(0.22f, tween(650))
        travel.animateTo(1f, tween(650))
        alpha.animateTo(0f, tween(550))
        onFinished()
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.align(Alignment.Center).graphicsLayer {
                translationX = -maxWidth.toPx() * 0.39f * travel.value
                translationY = maxHeight.toPx() * 0.34f * travel.value
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            },
            contentAlignment = Alignment.Center
        ) {
            if (image != null) Image(
                bitmap = image.asImageBitmap(),
                contentDescription = currentObject.metadata.name,
                modifier = Modifier.size(190.dp),
                contentScale = ContentScale.Fit
            ) else Text("🎁", fontSize = 100.sp)
        }
        Text(
            "¡Has conseguido ${currentObject.metadata.name}!",
            modifier = Modifier.align(Alignment.TopCenter),
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
