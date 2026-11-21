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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
@Composable internal fun AppNotificationHost(modifier:Modifier=Modifier){
 val context=LocalContext.current
 val objectFolder=NotificationQueueRepository.pendingFolderNames.firstOrNull()
 val storedObject=objectFolder?.let{folder->ObjectRepository.list(context).firstOrNull{it.folderName==folder}}
 val storyPaths=StoryUnlockNotificationRepository.pendingStoryPaths
 val interpreterActive=StoryUnlockNotificationRepository.isInterpreterActive
 val storyPath=storyPaths.firstOrNull().takeUnless{interpreterActive}
 val storedStory=storyPath?.let{path->StoryRepository.list(context).firstOrNull{it.directory.absolutePath==path}}
 var storyNotificationReady by remember(storyPath,interpreterActive){mutableStateOf(false)}
 LaunchedEffect(objectFolder,storyPath,interpreterActive){
  if(objectFolder!=null){
   if(storedObject==null){NotificationQueueRepository.complete(context,objectFolder);return@LaunchedEffect}
   val tone=runCatching{ToneGenerator(AudioManager.STREAM_MUSIC,65)}.getOrNull()
   try{tone?.startTone(ToneGenerator.TONE_PROP_ACK,180);delay(3000);NotificationQueueRepository.complete(context,objectFolder)}finally{tone?.release()}
  }else if(storyPath!=null){
   if(storedStory==null){StoryUnlockNotificationRepository.complete(context,storyPath);return@LaunchedEffect}
   delay(2000);if(StoryUnlockNotificationRepository.isInterpreterActive)return@LaunchedEffect
   storyNotificationReady=true
   if(AchievementSoundManager.isEnabled(context))runCatching{AchievementSoundManager.play(context,AchievementSoundEvent.STORY_UNLOCKED)}else delay(3000)
   StoryUnlockNotificationRepository.complete(context,storyPath)
  }
 }
 if(objectFolder!=null&&storedObject!=null){
  val owned=InventoryRepository.contains(context,storedObject.folderName)
  val alpha by animateFloatAsState(1f,animationSpec=tween(220),label="global-notification-fade")
  Box(modifier.fillMaxSize().padding(12.dp),contentAlignment=Alignment.TopEnd){Box(Modifier.graphicsLayer{this.alpha=alpha}){StoreUnlockNotification(storedObject,FontFamily.Default,owned)}}
 }else if(storyPath!=null&&storedStory!=null&&!interpreterActive&&storyNotificationReady){
  Box(modifier.fillMaxSize().padding(12.dp),contentAlignment=Alignment.TopEnd){StoryUnlockNotification(storedStory)}
 }
}
