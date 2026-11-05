package es.aprenderparajugar

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private val StoryBackground = Color(0xFF101827)
private val StoryGold = Color(0xFFFFD968)
private val StoryPanel = Color(0xFF2A3548)

/**
 * El reproductor reserva siempre un marco 16:9 para las ilustraciones.
 * Las imágenes originales se muestran completas y sin deformación (Fit).
 */
@Composable
internal fun StoryCoverScreen(story: StoredStory, fontFamily: FontFamily, onBack: () -> Unit, onPlay: () -> Unit) {
    val activity = LocalContext.current as? Activity
    val portraitWarning = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose { activity?.requestedOrientation = previousOrientation }
    }
    val cover = remember(story.directory.absolutePath, story.metadata.cover) {
        story.metadata.cover?.let { File(story.directory, it) }
            ?.takeIf { it.isFile }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = fontFamily)) {
    Column(
        Modifier.fillMaxSize().background(StoryBackground).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(story.metadata.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        if (portraitWarning) Text("Gira el dispositivo para disfrutar del cuento en horizontal.", color = StoryGold, textAlign = TextAlign.Center)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val frameModifier = if (maxWidth / maxHeight > 16f / 9f) {
                Modifier.fillMaxHeight().aspectRatio(16f / 9f)
            } else {
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            }
            Box(frameModifier.background(Color(0xFF080D16), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                if (cover != null) Image(
                    cover.asImageBitmap(),
                    contentDescription = "Portada de ${story.metadata.title}",
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                ) else Text("📖", fontSize = 100.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Volver") }
            Button(onClick = onPlay, modifier = Modifier.weight(1f)) { Text("▶ Play") }
        }
    }
    }
}

@Composable
internal fun StoryPlayerScreen(
    story: StoredStory,
    balanceSeconds: Int,
    fontFamily: FontFamily,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val engine = remember(story.directory.absolutePath) { StoryPlaybackEngine(context, story) }
    var snapshot by remember(story.directory.absolutePath) { mutableStateOf(engine.start()) }
    var backpackOpen by remember { mutableStateOf(false) }
    var narrationFinished by remember { mutableStateOf(false) }
    var exitConfirmation by remember { mutableStateOf(false) }
    var resumeAmbientOnReturn by remember { mutableStateOf(false) }
    var resumeNarrationOnReturn by remember { mutableStateOf(false) }
    var ambientPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var narrationPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val portraitWarning = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE

    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    resumeAmbientOnReturn = runCatching { ambientPlayer?.isPlaying == true }.getOrDefault(false)
                    resumeNarrationOnReturn = runCatching { narrationPlayer?.isPlaying == true }.getOrDefault(false)
                    if (resumeAmbientOnReturn) runCatching { ambientPlayer?.pause() }
                    if (resumeNarrationOnReturn) runCatching { narrationPlayer?.pause() }
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (resumeAmbientOnReturn) runCatching { ambientPlayer?.start() }
                    if (resumeNarrationOnReturn) runCatching { narrationPlayer?.start() }
                    resumeAmbientOnReturn = false
                    resumeNarrationOnReturn = false
                }
                else -> Unit
            }
        }
        activity?.lifecycle?.addObserver(lifecycleObserver)
        onDispose {
            activity?.lifecycle?.removeObserver(lifecycleObserver)
            safelyStopAndRelease(ambientPlayer)
            safelyStopAndRelease(narrationPlayer)
            engine.stop()
            activity?.requestedOrientation = previousOrientation
        }
    }

    LaunchedEffect(snapshot.scene.folderName, snapshot.scene.metadata.keepPreviousAmbient, snapshot.scene.metadata.ambientAudio) {
        if (!snapshot.scene.metadata.keepPreviousAmbient) {
            safelyStopAndRelease(ambientPlayer)
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
        safelyStopAndRelease(narrationPlayer)
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

    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = fontFamily)) {
    Column(
        Modifier.fillMaxSize().background(StoryBackground).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val frameModifier = if (maxWidth / maxHeight > 16f / 9f) {
                Modifier.fillMaxHeight().aspectRatio(16f / 9f)
            } else {
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            }
            Box(frameModifier.background(Color(0xFF080D16), RoundedCornerShape(14.dp))) {
                if (image != null) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = snapshot.scene.metadata.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("📖", color = Color.White, fontSize = 54.sp)
                    }
                }

                OutlinedButton(
                    onClick = { exitConfirmation = true },
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(2.dp, Color.White)
                ) { Text("×", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold) }

                Row(
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { backpackOpen = true },
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(2.dp, Color.White)
                    ) { Text("🎒 ${snapshot.inventory.size}", color = Color.White) }

                    if (narrationFinished && snapshot.scene.metadata.narrationAudio != null) {
                        OutlinedButton(
                            onClick = {
                                narrationPlayer?.let { player -> runCatching { player.seekTo(0); player.start() } }
                                narrationFinished = false
                            },
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(2.dp, Color.White)
                        ) { Text("↻", color = Color.White, fontSize = 20.sp) }
                    } else {
                        Text(" ", color = Color.Transparent)
                    }

                    if (snapshot.isFinished) {
                        Button(
                            onClick = {
                                engine.finish()
                                safelyStopAndRelease(ambientPlayer); ambientPlayer = null
                                safelyStopAndRelease(narrationPlayer); narrationPlayer = null
                                onExit()
                            },
                            shape = RoundedCornerShape(24.dp)
                        ) { Text("Fin") }
                    } else if (snapshot.canAdvance) {
                        Button(
                            onClick = { engine.advance()?.let { snapshot = it } },
                            shape = RoundedCornerShape(24.dp)
                        ) { Text("»") }
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .background(StoryPanel, RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                snapshot.scene.metadata.text,
                color = Color.White,
                fontSize = 17.sp,
                lineHeight = 22.sp
            )
        }
    }

    if (exitConfirmation) {
        AlertDialog(
            onDismissRequest = { exitConfirmation = false },
            title = { Text("¿Quieres salir del cuento?") },
            text = { Text("Puedes volver a la cartelera y continuar aprendiendo cuando quieras.") },
            confirmButton = {
                TextButton(onClick = {
                    exitConfirmation = false
                    safelyStopAndRelease(ambientPlayer); ambientPlayer = null
                    safelyStopAndRelease(narrationPlayer); narrationPlayer = null
                    engine.stop()
                    onExit()
                }) { Text("Salir") }
            },
            dismissButton = {
                TextButton(onClick = { exitConfirmation = false }) { Text("Seguir leyendo") }
            }
        )
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
            confirmButton = { TextButton(onClick = { backpackOpen = false }) { Text("Cerrar") } }
        )
    }
    }
}

private fun safelyStopAndRelease(player: MediaPlayer?) {
    if (player == null) return
    runCatching {
        if (player.isPlaying) player.stop()
        player.release()
    }
}
