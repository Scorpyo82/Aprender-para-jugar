package es.aprenderparajugar

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Presenta avisos globales por encima de la pantalla activa, sin depender
 * de AprenderApp ni del intérprete de cuentos.
 */
@Composable
internal fun AppNotificationHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pending = NotificationQueueRepository.pendingFolderNames
    val paused = NotificationQueueRepository.isPaused
    val folderName = pending.firstOrNull().takeUnless { paused }
    val stored = folderName?.let { folder ->
        ObjectRepository.list(context).firstOrNull { it.folderName == folder }
    }

    LaunchedEffect(folderName) {
        if (folderName == null) return@LaunchedEffect
        if (stored == null) {
            NotificationQueueRepository.complete(context, folderName)
            return@LaunchedEffect
        }

        val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 65) }.getOrNull()
        try {
            tone?.startTone(ToneGenerator.TONE_PROP_ACK, 260)
            delay(3200)
            NotificationQueueRepository.complete(context, folderName)
        } finally {
            tone?.release()
        }
    }

    if (stored != null) {
        val isOwned = InventoryRepository.contains(context, stored.folderName)
        val alpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(220),
            label = "global-notification-fade"
        )
        Box(
            modifier = modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(Modifier.graphicsLayer { this.alpha = alpha }) {
                StoreUnlockNotification(stored, FontFamily.Default, isOwned)
            }
        }
    }
}
