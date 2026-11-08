package es.aprenderparajugar

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
internal fun StoreUnlockNotification(
    stored: StoredObject,
    fontFamily: FontFamily
) {
    val bitmap = remember(stored.directory.absolutePath, stored.metadata.image) {
        File(stored.directory, stored.metadata.image).takeIf { it.isFile }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }
    Row(
        modifier = Modifier
            .background(Color(0xF2FFF8E8), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stored.metadata.name,
                modifier = Modifier.size(48.dp).blur(2.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Text("🎁", fontSize = 30.sp)
            }
        }
        androidx.compose.foundation.layout.Column {
            Text(
                "¡Nuevo objeto desbloqueado!",
                color = Color(0xFF49316F),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily
            )
            Text(
                "${stored.metadata.name} está disponible en la tienda",
                color = Color(0xFF344054),
                fontSize = 13.sp,
                fontFamily = fontFamily
            )
        }
    }
}
