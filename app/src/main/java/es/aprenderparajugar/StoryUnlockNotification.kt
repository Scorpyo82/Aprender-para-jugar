package es.aprenderparajugar
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
@Composable internal fun StoryUnlockNotification(story:StoredStory){
 val bitmap=remember(story.directory.absolutePath,story.metadata.cover){story.metadata.cover?.let{File(story.directory,it)}?.takeIf{it.isFile}?.let{BitmapFactory.decodeFile(it.absolutePath)}}
 Row(Modifier.background(Color(0xF2FFF8E8),RoundedCornerShape(18.dp)).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
  if(bitmap!=null)Image(bitmap.asImageBitmap(),"Portada de ${story.metadata.title}",Modifier.size(58.dp))else Text("📖",fontSize=38.sp)
  Column{Text("¡Nuevo cuento desbloqueado!",color=Color(0xFF49316F),fontSize=15.sp,fontWeight=FontWeight.Bold);Text("Ya puedes leer «${story.metadata.title}».",color=Color(0xFF344054),fontSize=13.sp)}
 }
}
