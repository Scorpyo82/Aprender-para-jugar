package es.aprenderparajugar

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

/** Mochila contextual del reproductor de cuentos. */
@Composable
internal fun StoryBackpackDialog(
    objects: List<StoredObject>,
    fontFamily: FontFamily,
    onUseObject: (StoredObject) -> Boolean,
    onDismiss: () -> Unit
) {
    var selectionError by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mochila de Zorro", fontFamily = fontFamily) },
        text = {
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
                                else selectionError = "Este objeto no se puede utilizar en esta escena. No se ha consumido."
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp,
                                vertical = 8.dp
                            )
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
            if (selectionError.isNotBlank()) {
                Text(selectionError, fontFamily = fontFamily, color = androidx.compose.ui.graphics.Color(0xFFB3261E))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
