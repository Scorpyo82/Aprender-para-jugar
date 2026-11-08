package es.aprenderparajugar

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
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
        BoxWithConstraints(Modifier.weight(0.68f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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
    var backpackPromptDismissedScene by remember { mutableStateOf<String?>(null) }
    var decisionDialogOpen by remember { mutableStateOf(false) }
    var decisionSelectionInProgress by remember { mutableStateOf(false) }
    var celebrationObject by remember { mutableStateOf<StoredObject?>(null) }
    var narrationFinished by remember { mutableStateOf(false) }
    var narrationIsPlaying by remember { mutableStateOf(false) }
    var ambientVolume by remember { mutableStateOf(1f) }
    var exitConfirmation by remember { mutableStateOf(false) }
    var resumeAmbientOnReturn by remember { mutableStateOf(false) }
    var resumeNarrationOnReturn by remember { mutableStateOf(false) }
    var ambientPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var narrationPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val portraitWarning = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE

    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val window = activity?.window
        val insetsController = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        val previousSystemBarsVisible = window?.let {
            WindowInsetsCompat.toWindowInsetsCompat(it.decorView.rootWindowInsets, it.decorView)
                .isVisible(WindowInsetsCompat.Type.systemBars())
        } ?: true
        val previousBehavior = insetsController?.systemBarsBehavior
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    if (runCatching { ambientPlayer?.isPlaying == true }.getOrDefault(false)) {
                        resumeAmbientOnReturn = true
                        runCatching { ambientPlayer?.pause() }
                    }
                    if (runCatching { narrationPlayer?.isPlaying == true }.getOrDefault(false)) {
                        resumeNarrationOnReturn = true
                        runCatching { narrationPlayer?.pause() }
                        narrationIsPlaying = false
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (resumeAmbientOnReturn) runCatching { ambientPlayer?.start() }
                    if (resumeNarrationOnReturn) {
                        runCatching { narrationPlayer?.start() }
                        narrationIsPlaying = true
                    }
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
            if (previousSystemBarsVisible) insetsController?.show(WindowInsetsCompat.Type.systemBars())
            else insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            previousBehavior?.let { insetsController?.systemBarsBehavior = it }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(snapshot.scene.folderName, snapshot.scene.metadata.keepPreviousAmbient, snapshot.scene.metadata.ambientAudio) {
        if (!snapshot.scene.metadata.keepPreviousAmbient) {
            safelyStopAndRelease(ambientPlayer)
            ambientPlayer = null
            val audio = snapshot.scene.metadata.ambientAudio?.let { File(snapshot.scene.directory, it) }?.takeIf { it.isFile }
            if (audio != null) {
                ambientVolume = 1f
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
        narrationIsPlaying = false
        narrationFinished = false
        val audio = snapshot.scene.metadata.narrationAudio?.let { File(snapshot.scene.directory, it) }?.takeIf { it.isFile }
        if (audio != null) {
            narrationPlayer = runCatching {
                MediaPlayer().apply {
                    setDataSource(audio.absolutePath)
                    setOnCompletionListener {
                        narrationIsPlaying = false
                        narrationFinished = true
                    }
                    prepare()
                    start()
                }
            }.getOrNull()
            if (narrationPlayer == null) narrationFinished = true
            else narrationIsPlaying = true
        } else narrationFinished = true
    }

    val audioPreferences = remember(context) { context.getSharedPreferences("aprender_para_jugar", android.content.Context.MODE_PRIVATE) }
    val ambientDuringNarration = remember(audioPreferences) {
        audioPreferences.getInt("story_ambient_during_narration_percent", 20).coerceIn(0, 30) / 100f
    }

    LaunchedEffect(ambientPlayer, narrationIsPlaying) {
        val player = ambientPlayer ?: return@LaunchedEffect
        val targetVolume = if (narrationIsPlaying) ambientDuringNarration else 1f
        val startVolume = ambientVolume
        val steps = 10
        for (step in 1..steps) {
            val fraction = step / steps.toFloat()
            val volume = startVolume + (targetVolume - startVolume) * fraction
            runCatching { player.setVolume(volume, volume) }
            ambientVolume = volume
            kotlinx.coroutines.delay(25)
        }
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
            Modifier.fillMaxWidth().weight(0.68f),
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
                    val hasUsableObject = snapshot.inventory.any { item ->
                        !item.metadata.collectible && engine.canUseObject(item.folderName)
                    }
                    val backpackAttention = hasUsableObject && narrationFinished &&
                        backpackPromptDismissedScene != snapshot.scene.folderName && !backpackOpen
                    val backpackPulse = rememberInfiniteTransition(label = "story-backpack-pulse")
                        .animateFloat(
                            initialValue = 1f,
                            targetValue = if (backpackAttention) 1.12f else 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 650),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "story-backpack-scale"
                        ).value
                    OutlinedButton(
                        onClick = {
                            backpackPromptDismissedScene = snapshot.scene.folderName
                            backpackOpen = true
                        },
                        modifier = Modifier.graphicsLayer {
                            scaleX = backpackPulse
                            scaleY = backpackPulse
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xEE17243A),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(
                            if (backpackAttention) 3.dp else 2.dp,
                            if (backpackAttention) StoryGold else Color.White
                        )
                    ) {
                        Text("🎒 ${snapshot.inventory.size}", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }

                    if (narrationFinished && snapshot.scene.metadata.narrationAudio != null) {
                        OutlinedButton(
                            onClick = {
                                narrationPlayer?.let { player ->
                                    runCatching { player.seekTo(0); player.start() }
                                    narrationIsPlaying = true
                                }
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
                                val collectible = engine.claimCurrentSceneExitCollectible()
                                if (collectible != null) {
                                    celebrationObject = collectible
                                } else {
                                    engine.finish()
                                    safelyStopAndRelease(ambientPlayer); ambientPlayer = null
                                    safelyStopAndRelease(narrationPlayer); narrationPlayer = null
                                    onExit()
                                }
                            },
                            shape = RoundedCornerShape(24.dp)
                        ) { Text("Fin") }
                    } else if (snapshot.canAdvance || snapshot.decisionOptions.isNotEmpty()) {
                        Button(
                            onClick = {
                                val collectible = engine.claimCurrentSceneExitCollectible()
                                if (collectible != null) celebrationObject = collectible
                                else if (snapshot.decisionOptions.isNotEmpty()) decisionDialogOpen = true
                                else engine.advance()?.let { snapshot = it }
                            },
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("»")
                                if (snapshot.decisionOptions.isNotEmpty()) Text("☷", fontSize = 14.sp)
                            }
                        }
                    }
                }

                if (decisionDialogOpen && snapshot.decisionOptions.isNotEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.42f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(0.86f).fillMaxHeight(0.94f)
                                .background(Color(0xEAF9FAFC), RoundedCornerShape(20.dp))
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    snapshot.decisionPrompt.ifBlank { "Elige una opción" },
                                    modifier = Modifier.weight(1f),
                                    color = Color(0xFF253047),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                TextButton(onClick = { if (!decisionSelectionInProgress) decisionDialogOpen = false }, enabled = !decisionSelectionInProgress) {
                                    Text("✕", fontSize = 20.sp, color = Color(0xFF475467))
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                items(snapshot.decisionOptions, key = { it.key }) { option ->
                                    Button(
                                        onClick = {
                                            if (!decisionSelectionInProgress) {
                                                decisionSelectionInProgress = true
                                                val next = runCatching { engine.chooseDecisionOption(option.key) }.getOrNull()
                                                if (next != null) {
                                                    snapshot = next
                                                    decisionDialogOpen = false
                                                }
                                                decisionSelectionInProgress = false
                                            }
                                        },
                                        enabled = !decisionSelectionInProgress,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                        shape = RoundedCornerShape(13.dp)
                                    ) {
                                        Text(option.text, modifier = Modifier.fillMaxWidth(), fontSize = 17.sp, textAlign = TextAlign.Center)
                                    }
                                }
                                if (snapshot.decisionOptions.size > 3) {
                                    Column(
                                        modifier = Modifier.padding(start = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("⌃", color = Color(0xFF59677D), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                        Text("⌄", color = Color(0xFF59677D), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth()
                 .weight(0.32f)
                .background(StoryPanel, RoundedCornerShape(14.dp))
                .verticalScroll(remember(snapshot.scene.folderName) { ScrollState(0) })
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                snapshot.scene.metadata.text,
                color = Color.White,
                fontSize = 17.sp,
                lineHeight = 25.sp
            )
        }
    }

    celebrationObject?.let { awarded ->
        CollectibleCelebrationDialog(awarded, fontFamily) { celebrationObject = null }
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
        val configuredOrigins = story.metadata.connectionAlternatives.map { it.fromScene }.toSet()
        val requiredObjectFolders = (
            story.metadata.connectionAlternatives
                .filter { it.kind == "object" }
                .mapNotNull { it.requiredObject } +
                story.metadata.connectionConditions
                    .filterNot { it.fromScene in configuredOrigins }
                    .mapNotNull { it.requiredObject }
            ).toSet()
        val contextualInventory = snapshot.inventory.filter {
            !it.metadata.collectible && it.folderName in requiredObjectFolders
        }
        StoryBackpackDialog(
            objects = contextualInventory,
            fontFamily = fontFamily,
            onUseObject = { item ->
                if (engine.canUseObject(item.folderName)) {
                    snapshot = engine.chooseObject(item.folderName)
                    true
                } else {
                    false
                }
            },
            onDismiss = { backpackOpen = false }
        )
    }
   }
}


@Composable
internal fun StoryAmbientSourceDialog(
    candidates: List<StoredStoryScene>,
    onSelect: (StoredStoryScene) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar sonido ambiental") },
        text = {
            Column {
                Text(
                    "Esta escena puede heredar el ambiente de varias escenas anteriores. Elige cuál quieres escuchar en la prueba.",
                    color = Color(0xFF667085)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(candidates, key = { it.folderName }) { candidate ->
                        OutlinedButton(
                            onClick = { onSelect(candidate) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(candidate.metadata.title, fontWeight = FontWeight.Bold, color = Color(0xFF493B82))
                                Text(
                                    candidate.metadata.illustrationSummary.ifBlank { "Sin resumen de ilustración" },
                                    color = Color(0xFF667085),
                                    textAlign = TextAlign.Start
                                )
                                Text(
                                    if (candidate.metadata.keepPreviousAmbient || candidate.metadata.ambientAudio == null)
                                        "Esta ruta llega sin sonido ambiental propio"
                                    else "Esta escena aporta el sonido ambiental",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8A6A35)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
internal fun StorySceneTestDialog(
    scene: StoredStoryScene,
    ambientDuringNarrationPercent: Int,
    ambientAudioOverride: File? = null,
    useAmbientAudioOverride: Boolean = false,
    onExit: () -> Unit
) {
    var ambientPlayer by remember(scene.directory.absolutePath) { mutableStateOf<MediaPlayer?>(null) }
    var narrationPlayer by remember(scene.directory.absolutePath) { mutableStateOf<MediaPlayer?>(null) }
    var narrationIsPlaying by remember(scene.directory.absolutePath) { mutableStateOf(false) }
    var ambientCurrentVolume by remember(scene.directory.absolutePath) { mutableStateOf(1f) }
    val image = remember(scene.directory.absolutePath, scene.metadata.illustration) {
        scene.metadata.illustration?.let { File(scene.directory, it) }
            ?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }

    DisposableEffect(scene.directory.absolutePath, scene.metadata.ambientAudio, scene.metadata.narrationAudio, useAmbientAudioOverride, ambientAudioOverride?.absolutePath) {
        val ambientFile = if (useAmbientAudioOverride) {
            ambientAudioOverride?.takeIf { it.isFile }
        } else {
            scene.metadata.ambientAudio?.let { File(scene.directory, it) }?.takeIf { it.isFile }
        }
        val narrationFile = scene.metadata.narrationAudio?.let { File(scene.directory, it) }?.takeIf { it.isFile }
        ambientPlayer = ambientFile?.let { file -> runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull() }
        narrationPlayer = narrationFile?.let { file -> runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { narrationIsPlaying = false }
                prepare()
                start()
                narrationIsPlaying = true
            }
        }.getOrNull() }
        onDispose {
            safelyStopAndRelease(ambientPlayer)
            safelyStopAndRelease(narrationPlayer)
            ambientPlayer = null
            narrationPlayer = null
        }
    }

    LaunchedEffect(ambientPlayer, narrationIsPlaying, ambientDuringNarrationPercent) {
        val player = ambientPlayer ?: return@LaunchedEffect
        val target = if (narrationIsPlaying) ambientDuringNarrationPercent.coerceIn(0, 30) / 100f else 1f
        val start = ambientCurrentVolume
        for (step in 1..10) {
            val volume = start + (target - start) * (step / 10f)
            runCatching { player.setVolume(volume, volume) }
            ambientCurrentVolume = volume
            kotlinx.coroutines.delay(25)
        }
    }

    Dialog(onDismissRequest = onExit, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(StoryBackground).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Prueba de escena", color = StoryGold, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onExit, border = BorderStroke(1.dp, Color.White)) {
                    Text("Salir del test", color = Color.White)
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val frameModifier = if (maxWidth / maxHeight > 16f / 9f) {
                    Modifier.fillMaxHeight().aspectRatio(16f / 9f)
                } else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                Box(frameModifier.background(Color(0xFF080D16), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    if (image != null) Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = scene.metadata.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    ) else Text("📖", color = Color.White, fontSize = 54.sp)
                }
            }
            Column(Modifier.fillMaxWidth().weight(0.27f).background(StoryPanel, RoundedCornerShape(14.dp)).verticalScroll(remember(scene.directory.absolutePath) { ScrollState(0) }).padding(16.dp)) {
                Text(scene.metadata.text, color = Color.White, fontSize = 17.sp, lineHeight = 22.sp)
            }
            Text(
                if (narrationPlayer != null) "Narración en reproducción · Ambiente al " + ambientDuringNarrationPercent.coerceIn(0, 30) + " %"
                else "Ambiente al 100 % · Sin narración asignada",
                color = Color.LightGray,
                fontSize = 12.sp
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
