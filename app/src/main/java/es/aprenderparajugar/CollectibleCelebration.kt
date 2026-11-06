package es.aprenderparajugar

import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

@Composable
internal fun CollectibleCelebrationDialog(
    stored: StoredObject,
    fontFamily: FontFamily,
    onContinue: () -> Unit
) {
    val imageFile = File(stored.directory, stored.metadata.image)

    Dialog(
        onDismissRequest = { /* El premio se cierra únicamente con el botón visible. */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .height(440.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF30205F), Color(0xFF172D56), Color(0xFF102D43))
                    ),
                    RoundedCornerShape(28.dp)
                )
        ) {
            AndroidView(
                factory = { context -> GifObjectView(context) },
                update = { it.setGif(R.drawable.celebracion_confeti, animate = true, fill = true) },
                modifier = Modifier.fillMaxSize()
            )
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "¡Nuevo coleccionable conseguido!",
                        color = Color(0xFFFFE082),
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        fontFamily = fontFamily
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        stored.metadata.name,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontFamily = fontFamily
                    )
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageFile.extension.equals("gif", true)) {
                        AndroidView(
                            factory = { context -> GifObjectView(context) },
                            update = { it.setGif(imageFile, animate = true) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AndroidView(
                            factory = { context ->
                                ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                            },
                            update = { view ->
                                runCatching { view.setImageURI(Uri.fromFile(imageFile)) }
                                    .onFailure { view.setImageResource(android.R.drawable.ic_menu_gallery) }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("¡Continuar!", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, fontFamily = fontFamily)
                }
            }
        }
    }
}
