package es.aprenderparajugar

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

fun LazyListScope.StoryLibraryScreen(
    stories: List<StoredStory>,
    collectionName: (String) -> String,
    onStorySelected: (StoredStory) -> Unit,
    onCreateStory: () -> Unit
) {
    item {
        Text("Biblioteca de cuentos", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
    item {
        Text("Cuentos guardados en este dispositivo.", fontSize = 14.sp, color = Color(0xFF667085))
    }
    items(stories) { stored ->
        StoryLibraryCard(
            story = stored,
            collectionName = collectionName(stored.collection),
            onClick = { onStorySelected(stored) }
        )
    }
    item {
        Button(onClick = onCreateStory, modifier = Modifier.fillMaxWidth()) {
            Text("＋ Crear nuevo cuento")
        }
    }
}

@Composable
private fun StoryLibraryCard(
    story: StoredStory,
    collectionName: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFE5))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val coverThumb = remember(story.directory.absolutePath, story.metadata.cover) {
                story.metadata.cover?.let { File(story.directory, it) }?.takeIf { it.isFile }
                    ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
            }
            if (coverThumb != null) {
                Image(
                    bitmap = coverThumb.asImageBitmap(),
                    contentDescription = "Portada del cuento",
                    modifier = Modifier.width(104.dp).aspectRatio(16f / 9f),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    Modifier.width(104.dp).aspectRatio(16f / 9f)
                        .background(Color(0xFFFFF3E8), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📖", fontSize = 28.sp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(story.metadata.title, fontWeight = FontWeight.Bold)
                Text(collectionName, fontSize = 12.sp, color = Color(0xFF667085))
            }
        }
    }
}
