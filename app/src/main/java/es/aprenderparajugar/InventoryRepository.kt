package es.aprenderparajugar
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
data class InventoryPurchaseResult(val success:Boolean,val balanceSeconds:Int,val message:String)
object InventoryRepository {
 private const val ROOT_DIRECTORY="inventario"
 private const val JSON_FILE="inventario.json"
 fun list(context:Context):Set<String>{val a=readRoot(context).optJSONArray("objects")?:JSONArray();return (0 until a.length()).mapNotNull{a.optString(it).takeIf(String::isNotBlank)}.toSet()}
 fun contains(context:Context,folderName:String)=list(context).contains(validateFolderName(folderName))
 fun add(context:Context,folderName:String):Set<String>{
  val safe=validateFolderName(folderName);require(ObjectRepository.list(context).any{it.folderName==safe}){"El objeto ya no existe en la biblioteca."}
  val before=list(context);val updated=before+safe;save(context,updated,resetFirstPresentationFolder=safe.takeIf{it !in before})
  if(safe !in before) runCatching{StoryUnlockNotificationRepository.enqueueNewlyUnlockedStories(context,before,updated)}
  return updated
 }
 fun remove(context:Context,folderName:String):Set<String>{val safe=validateFolderName(folderName);val updated=list(context)-safe;save(context,updated,removeFirstPresentationFolder=safe);return updated}
 fun isFirstPresentationShown(context:Context,folderName:String):Boolean{val s=readRoot(context).optJSONObject("collectibleFirstPresentation")?:JSONObject();return s.optBoolean(validateFolderName(folderName),false)}
 fun markFirstPresentationShown(context:Context,folderName:String){val safe=validateFolderName(folderName);if(!contains(context,safe))return;val root=readRoot(context);val states=root.optJSONObject("collectibleFirstPresentation")?:JSONObject();states.put(safe,true);root.put("collectibleFirstPresentation",states);writeRoot(context,root)}
 fun purchase(context:Context,folderName:String):InventoryPurchaseResult{
  val prefs=context.getSharedPreferences("aprender_para_jugar",Context.MODE_PRIVATE);val now=System.currentTimeMillis();val deadline=prefs.getLong("play_deadline_ms",0L)
  val balance=if(deadline>now)((deadline-now+999L)/1000L).toInt().coerceAtLeast(0)else prefs.getInt("balance_seconds",0).coerceAtLeast(0)
  fun failure(message:String)=InventoryPurchaseResult(false,balance,message)
  val stored=ObjectRepository.list(context).firstOrNull{it.folderName==folderName}?:return failure("Este objeto ya no está disponible.")
  if(!stored.metadata.availableInStore)return failure("Este objeto no está disponible en la tienda.")
  if(stored.metadata.requiresUnlock&&!ObjectUnlockRepository.contains(context,folderName))return failure("Primero tienes que desbloquear este objeto.")
  val current=list(context);if(folderName in current)return failure("Ya has conseguido este objeto.")
  val price=stored.metadata.storePriceMinutes;if(price<=0)return failure("Este objeto no tiene un precio válido.")
  val cost=price*60;if(balance<cost){val missing=(cost-balance+59)/60;return failure("Todavía no tienes saldo suficiente. Te faltan $missing min.")}
  var updated=false
  try{
   save(context,current+folderName,resetFirstPresentationFolder=folderName);updated=true
   val newDeadline=if(deadline>now)deadline-cost.toLong()*1000L else 0L
   val newBalance=if(deadline>now){if(newDeadline>now)((newDeadline-now+999L)/1000L).toInt().coerceAtLeast(0)else 0}else(balance-cost).coerceAtLeast(0)
   val editor=prefs.edit().putInt("balance_seconds",newBalance)
   if(deadline>now)editor.putLong("play_deadline_ms",newDeadline.takeIf{it>now}?:0L).putBoolean("play_session_authorized",newDeadline>now).putBoolean("play_paused_for_screen_lock",false)
   if(!editor.commit()){save(context,current,removeFirstPresentationFolder=folderName);updated=false;return failure("No se ha podido guardar la compra. Inténtalo de nuevo.")}
   if(deadline>now&&newDeadline<=now)context.stopService(Intent(context,PlayTimeService::class.java))
   runCatching{StoryUnlockNotificationRepository.enqueueNewlyUnlockedStories(context,current,current+folderName)}
   return InventoryPurchaseResult(true,newBalance,"¡Has conseguido ${stored.metadata.name}!")
  }catch(error:Exception){if(updated)runCatching{save(context,current,removeFirstPresentationFolder=folderName)};return failure(error.message?:"No se ha podido completar la compra.")}
 }
 private fun save(context:Context,folders:Set<String>,resetFirstPresentationFolder:String?=null,removeFirstPresentationFolder:String?=null){
  val root=readRoot(context);root.put("objects",JSONArray(folders.sorted()));val states=root.optJSONObject("collectibleFirstPresentation")?:JSONObject()
  removeFirstPresentationFolder?.let{states.remove(it)}
  resetFirstPresentationFolder?.let{folder->if(ObjectRepository.list(context).firstOrNull{it.folderName==folder}?.metadata?.collectible==true)states.put(folder,false)}
  root.put("collectibleFirstPresentation",states);writeRoot(context,root)
 }
 private fun readRoot(context:Context):JSONObject{val f=inventoryFile(context);return if(f.isFile)runCatching{JSONObject(f.readText(Charsets.UTF_8))}.getOrDefault(JSONObject())else JSONObject()}
 private fun writeRoot(context:Context,root:JSONObject){
  val dir=File(context.filesDir,ROOT_DIRECTORY);check(dir.exists()||dir.mkdirs()){"No se pudo crear la carpeta del inventario."};check(dir.isDirectory){"La ruta del inventario no es una carpeta."}
  val target=File(dir,JSON_FILE);val tmp=File(dir,".$JSON_FILE.tmp");tmp.writeText(root.toString(2),Charsets.UTF_8)
  if(target.exists()&&!target.delete()){tmp.delete();throw IllegalStateException("No se pudo actualizar el inventario.")}
  if(!tmp.renameTo(target)){tmp.delete();throw IllegalStateException("No se pudo guardar el inventario.")}
 }
 private fun inventoryFile(context:Context)=File(File(context.filesDir,ROOT_DIRECTORY),JSON_FILE)
 private fun validateFolderName(value:String):String{val n=value.trim();require(n.isNotBlank()&&n!="."&&n!=".."&&!n.contains('/')&&!n.contains('\\')&&!n.contains(':')){"La referencia del objeto no es válida."};return n}
}
