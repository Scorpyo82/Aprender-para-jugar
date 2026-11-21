package es.aprenderparajugar
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
internal object StoryUnlockNotificationRepository {
 private const val PREFERENCES="aprender_para_jugar"
 private const val QUEUE_KEY="pending_story_unlock_notifications"
 private val pending=mutableStateListOf<String>()
 private val interpreterActive=mutableStateOf(false)
 private var initialized=false
 val pendingStoryPaths:List<String> get()=pending
 val isInterpreterActive:Boolean get()=interpreterActive.value
 @Synchronized fun initialize(context:Context){
  if(initialized)return
  val raw=context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).getString(QUEUE_KEY,null)
  val restored=runCatching{val json=JSONArray(raw?:"[]");(0 until json.length()).mapNotNull{json.optString(it).takeIf(String::isNotBlank)}}.getOrDefault(emptyList())
  pending.clear();pending.addAll(restored.distinct());interpreterActive.value=false;initialized=true
 }
 @Synchronized fun setInterpreterActive(context:Context,active:Boolean){initialize(context);interpreterActive.value=active}
 @Synchronized fun enqueueNewlyUnlockedStories(context:Context,previous:Set<String>,updated:Set<String>){
  initialize(context);if(updated==previous)return
  val unlocked=StoryRepository.list(context).filter{story->val req=story.metadata.requiredAccessObjects;req.isNotEmpty()&&req.any{it !in previous}&&req.all{it in updated}}.map{it.directory.absolutePath}
  val additions=unlocked.filter{it !in pending};if(additions.isEmpty())return;pending.addAll(additions);persist(context)
 }
 @Synchronized fun complete(context:Context,path:String){initialize(context);pending.remove(path);persist(context)}
 private fun persist(context:Context){val json=JSONArray();pending.forEach(json::put);context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).edit().putString(QUEUE_KEY,json.toString()).commit()}
}
