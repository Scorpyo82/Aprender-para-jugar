package es.aprenderparajugar

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
internal fun StoryCoverScreen(story: StoredStory, onBack: () -> Unit, onPlay: () -> Unit) {
    val cover = remember(story.directory.absolutePath, story.metadata.cover) {
        story.metadata.cover?.let { File(story.directory, it) }
            ?.takeIf { it.isFile }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF101827)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(story.metadata.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        if (cover != null) Image(cover.asImageBitmap(), contentDescription = "Portada de ${story.metadata.title}", modifier = Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit)
        else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("📖", fontSize = 100.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Volver") }
            Button(onClick = onPlay, modifier = Modifier.weight(1f)) { Text("▶ Play") }
        }
    }
}

@Composable
internal fun StoryPlayerScreen(
    story: StoredStory,
    balanceSeconds: Int,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val engine = remember(story.directory.absolutePath) { StoryPlaybackEngine(context, story) }
    var snapshot by remember(story.directory.absolutePath) { mutableStateOf(engine.start()) }
    var backpackOpen by remember { mutableStateOf(false) }
    var narrationFinished by remember { mutableStateOf(false) }
    var ambientPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var narrationPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var portraitWarning by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            ambientPlayer?.runCatching { stop(); release() }
            narrationPlayer?.runCatching { stop(); release() }
            engine.stop()
            activity?.requestedOrientation = previousOrientation
        }
    }

    LaunchedEffect(snapshot.scene.folderName, snapshot.scene.metadata.keepPreviousAmbient, snapshot.scene.metadata.ambientAudio) {
        if (!snapshot.scene.metadata.keepPreviousAmbient) {
            ambientPlayer?.runCatching { stop(); release() }
            ambientPlayer = null
            val audio = snapshot.scene.metadata.ambientAudio?.let { File(snapshot.scene.directory, it) }?.takeIf { it.isFile }
            if (audio != null) {
                ambientPlayer = runCatching {
                    MediaPlayer().apply {
                        setDataSource(audio.absolutePath)
                        isLooping = true
                        prepare()
                        start()
                    }
                }.getOrNull()
            }
        }
    }

    LaunchedEffect(snapshot.scene.folderName, snapshot.scene.metadata.narrationAudio) {
        narrationPlayer?.runCatching { stop(); release() }
        narrationPlayer = null
        narrationFinished = false
        val audio = snapshot.scene.metadata.narrationAudio?.let { File(snapshot.scene.directory, it) }?.takeIf { it.isFile }
        if (audio != null) {
            narrationPlayer = runCatching {
                MediaPlayer().apply {
                    setDataSource(audio.absolutePath)
                    setOnCompletionListener { narrationFinished = true }
                    prepare()
                    start()
                }
            }.getOrNull()
            if (narrationPlayer == null) narrationFinished = true
        } else narrationFinished = true
    }

    val image = remember(snapshot.scene.directory.absolutePath, snapshot.scene.metadata.illustration) {
        snapshot.scene.metadata.illustration?.let { File(snapshot.scene.directory, it) }
            ?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }

    BackHandler(enabled = true) { /* La salida se realiza desde el control explícito. */ }

    Box(Modifier.fillMaxSize().background(Color(0xFF101827)).padding(14.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("⏱ ${balanceSeconds / 60} min ${balanceSeconds % 60} s", color = Color(0xFFFFD968), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(story.metadata.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (image != null) Image(image.asImageBitmap(), contentDescription = snapshot.scene.metadata.title, modifier = Modifier.weight(1.25f).fillMaxSize(), contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text(snapshot.scene.metadata.title, color = Color(0xFFFFD968), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(snapshot.scene.metadata.text, color = Color.White, fontSize = 17.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { backpackOpen = true }) { Text("🎒 Mochila (${snapshot.inventory.size})") }
                if (narrationFinished && snapshot.scene.metadata.narrationAudio != null) {
                    OutlinedButton(onClick = {
                        narrationPlayer?.runCatching { seekTo(0); start() }
                        narrationFinished = false
                    }) { Text("↻ Repetir") }
                }
                if (snapshot.isFinished) {
                    Button(onClick = {
                        engine.finish()
                        ambientPlayer?.runCatching { stop(); release() }; ambientPlayer = null
                        narrationPlayer?.runCatching { stop(); release() }; narrationPlayer = null
                        onExit()
                    }) { Text("Fin") }
                } else if (snapshot.canAdvance) {
                    Button(onClick = { engine.advance()?.let { snapshot = it } }) { Text("»") }
                }
            }
        }
        if (portraitWarning) Text("Gira el dispositivo para disfrutar del cuento en horizontal.", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (backpackOpen) {
        AlertDialog(
            onDismissRequest = { backpackOpen = false },
            title = { Text("Mochila de Zorro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (snapshot.inventory.isEmpty()) Text("La mochila está vacía.")
                    snapshot.inventory.forEach { item ->
                        OutlinedButton(
                            onClick = {
                                snapshot = engine.chooseObject(item.folderName)
                                backpackOpen = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(item.metadata.name) }
                    }
                }
            },
            confirmButton = { Text("Cerrar", modifier = Modifier.padding(12.dp)) }
        )
    }
}
