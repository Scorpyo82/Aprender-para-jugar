package es.aprenderparajugar

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

private val AccessBackground = Color(0xFF101827)
private val AccessPanel = Color(0xFF1A2638)
private val AccessMuted = Color(0xFFB8C3D3)
private val AccessGreen = Color(0xFF65D69A)

@Composable
internal fun StoryAccessRequirementsScreen(
    story: StoredStory,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val requiredObjects = remember(story.directory.absolutePath, story.metadata.requiredAccessObjects) {
        val library = ObjectRepository.list(context).associateBy { it.folderName }
        story.metadata.requiredAccessObjects.distinct().mapNotNull { folder ->
            library[folder]?.let { stored -> stored to InventoryRepository.contains(context, folder) }
        }
    }
    val missingCount = requiredObjects.count { !it.second }

    Column(
        Modifier.fillMaxSize().background(AccessBackground).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("¡Una aventura por desbloquear!", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(
            "Para comenzar «${story.metadata.title}» necesitas reunir estos objetos.",
            color = AccessMuted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AccessPanel)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                requiredObjects.forEach { (stored, obtained) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF263449)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (obtained) {
                            val imageFile = remember(stored.directory.absolutePath, stored.metadata.image) {
                                File(stored.directory, stored.metadata.image).takeIf { it.isFile }
                            }
                            if (imageFile != null) {
                                AndroidView(
                                    factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                    update = { view -> view.setImageURI(android.net.Uri.fromFile(imageFile)) },
                                    modifier = Modifier.size(76.dp)
                                )
                            } else {
                                Box(Modifier.size(76.dp).background(Color(0xFF35465D), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                    Text("✓", color = AccessGreen, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Box(
                                Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF35465D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("?", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stored.metadata.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (obtained) "¡Ya lo tienes!" else "Todavía te falta",
                                color = if (obtained) AccessGreen else AccessMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (obtained) Text("✓", color = AccessGreen, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(
            if (missingCount == 0) "¡Ya tienes todo lo necesario! Puedes comenzar la aventura."
            else "¡Consigue los objetos que faltan para comenzar esta aventura!",
            color = Color(0xFFFFD968),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a los cuentos", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
