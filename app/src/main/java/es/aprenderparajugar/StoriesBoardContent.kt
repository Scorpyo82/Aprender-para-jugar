package es.aprenderparajugar

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun StoriesBoardContent(
    context: Context,
    storedStories: List<StoredStory>,
    disabledStoryCollections: Set<String>,
    storyCollections: List<String>,
    selectedStoriesCollection: String?,
    onSelectedStoriesCollectionChange: (String?) -> Unit,
    selectedStoryPath: String?,
    storiesBoardTab: String,
    onStoriesBoardTabChange: (String) -> Unit,
    exerciseFontFamily: FontFamily,
    onHome: () -> Unit,
    onSelectStory: (StoredStory) -> Unit
) {
        val availableStories = storedStories.filter { it.collection !in disabledStoryCollections }
        val availableCollections = availableStories.map { it.collection }.distinct().sortedBy { folder ->
            storyCollections.firstOrNull { StoryRepository.collectionFolderName(it) == folder }?.lowercase() ?: folder.lowercase()
        }
        val selectedCollection = selectedStoriesCollection
        val collectionStories = selectedCollection?.let { folder -> availableStories.filter { it.collection == folder }.sortedBy { it.metadata.title.lowercase() } }.orEmpty()
        val boardWidth = LocalConfiguration.current.screenWidthDp
        val boardColumns = when {
            boardWidth >= 1100 -> 5
            boardWidth >= 820 -> 4
            boardWidth >= 560 -> 3
            else -> 2
        }
        val boardGap = 12.dp
        val boardCardWidth = ((boardWidth - 40 - (boardColumns - 1) * 12) / boardColumns).coerceAtLeast(132).dp
        LazyColumn(
            Modifier.fillMaxSize().background(Color(0xFF101827)).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedButton(onClick = { onHome() }) { Text("← Volver al inicio", color = Color.White) }
                Text(
                    if (selectedCollection == null) "Cuentos" else "Colección",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    if (selectedCollection == null) "Elige una historia para comenzar tu aventura." else "Explora los cuentos de esta colección.",
                    fontSize = 15.sp,
                    color = Color(0xFFB8C3D3)
                )
            }
            if (selectedCollection == null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("all" to "Todos los cuentos", "collections" to "Colecciones").forEach { (key, label) ->
                            val selected = storiesBoardTab == key
                            Card(
                                Modifier.weight(1f).clickable { onStoriesBoardTabChange(key) },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF263B59) else Color(0xFF1A2638)),
                                border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF8DB8F5) else Color(0xFF34445A))
                            ) {
                                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else Color(0xFFB8C3D3), textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                if (storiesBoardTab == "all") {
                    if (availableStories.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Image(painter = painterResource(id = R.drawable.home_header_fox), contentDescription = "El zorro leyendo un cuento", modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp), contentScale = ContentScale.Fit)
                                Text("Todavía no hay cuentos disponibles.", color = Color(0xFFD4DCE7), fontSize = 15.sp, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        item {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(boardGap),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                availableStories.sortedBy { it.metadata.title.lowercase() }.forEach { story ->
                                    val coverFile = story.metadata.cover?.let { File(story.directory, it) }?.takeIf { it.isFile }
                                    Card(
                                        Modifier.width(boardCardWidth).clickable { onSelectStory(story) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2638)),
                                        border = androidx.compose.foundation.BorderStroke(if (selectedStoryPath == story.directory.absolutePath) 2.dp else 1.dp, if (selectedStoryPath == story.directory.absolutePath) Color(0xFF8DB8F5) else Color(0xFF34445A))
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(
                                                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clipToBounds().background(Color(0xFF29384D), RoundedCornerShape(11.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (coverFile != null) {
                                                    AndroidView(
                                                        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                                                        update = { view -> view.setImageURI(Uri.fromFile(coverFile)) },
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Text("📖", fontSize = 48.sp)
                                                }
                                                if (story.metadata.requiredAccessObjects.any { !InventoryRepository.contains(context, it) }) {
                                                    Box(
                                                        Modifier.align(Alignment.TopEnd).padding(8.dp)
                                                            .background(Color(0xDD101827), RoundedCornerShape(50))
                                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                                    ) { Text("🔒", fontSize = 18.sp) }
                                                }
                                            }
                                            Text(
                                                story.metadata.title,
                                                fontSize = 15.sp,
                                                fontFamily = exerciseFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                softWrap = true,
                                                minLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (availableCollections.isEmpty()) {
                        item { Text("Todavía no hay colecciones con cuentos disponibles.", color = Color(0xFFD4DCE7), fontSize = 15.sp) }
                    } else {
                        item {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(boardGap),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                availableCollections.forEach { folder ->
                                    val displayName = storyCollections.firstOrNull { StoryRepository.collectionFolderName(it) == folder } ?: folder.replace('_', ' ')
                                    val coverFile = StoryRepository.collectionCoverFile(context, folder)?.takeIf { it.isFile }
                                    Card(
                                        Modifier.width(boardCardWidth).clickable { onSelectedStoriesCollectionChange(folder) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2638)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34445A))
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(
                                                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clipToBounds().background(Color(0xFF29384D), RoundedCornerShape(11.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (coverFile != null) {
                                                    AndroidView(
                                                        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                                        update = { view -> view.setImageURI(Uri.fromFile(coverFile)) },
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Text("📚", fontSize = 48.sp)
                                                }
                                            }
                                            Text(displayName, fontSize = 15.sp, fontFamily = exerciseFontFamily, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                                            Text("${availableStories.count { it.collection == folder }} cuentos", fontSize = 12.sp, color = Color(0xFFB8C3D3))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    val displayName = storyCollections.firstOrNull { StoryRepository.collectionFolderName(it) == selectedCollection } ?: selectedCollection.replace('_', ' ')
                    val coverFile = StoryRepository.collectionCoverFile(context, selectedCollection)?.takeIf { it.isFile }
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelectedStoriesCollectionChange(null) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clipToBounds().background(Color(0xFF29384D), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            if (coverFile != null) {
                                AndroidView(
                                    factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                    update = { view -> view.setImageURI(Uri.fromFile(coverFile)) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text("📚", fontSize = 30.sp)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(displayName, color = Color.White, fontSize = 19.sp, fontFamily = exerciseFontFamily, fontWeight = FontWeight.Bold)
                            Text("← Volver a colecciones", color = Color(0xFF9FC5F8), fontSize = 13.sp)
                        }
                    }
                }
                if (collectionStories.isEmpty()) {
                    item { Text("Esta colección todavía no contiene cuentos.", color = Color(0xFFD4DCE7), fontSize = 15.sp) }
                } else {
                    item {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(boardGap),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            collectionStories.forEach { story ->
                                val coverFile = story.metadata.cover?.let { File(story.directory, it) }?.takeIf { it.isFile }
                                Card(
                                    Modifier.width(boardCardWidth).clickable { onSelectStory(story) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2638)),
                                    border = androidx.compose.foundation.BorderStroke(if (selectedStoryPath == story.directory.absolutePath) 2.dp else 1.dp, if (selectedStoryPath == story.directory.absolutePath) Color(0xFF8DB8F5) else Color(0xFF34445A))
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clipToBounds().background(Color(0xFF29384D), RoundedCornerShape(11.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (coverFile != null) {
                                                AndroidView(
                                                    factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                                                    update = { view -> view.setImageURI(Uri.fromFile(coverFile)) },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Text("📖", fontSize = 48.sp)
                                            }
                                            if (story.metadata.requiredAccessObjects.any { !InventoryRepository.contains(context, it) }) {
                                                Box(
                                                    Modifier.align(Alignment.TopEnd).padding(8.dp)
                                                        .background(Color(0xDD101827), RoundedCornerShape(50))
                                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) { Text("🔒", fontSize = 18.sp) }
                                            }
                                        }
                                        Text(story.metadata.title, fontSize = 15.sp, fontFamily = exerciseFontFamily, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

}
