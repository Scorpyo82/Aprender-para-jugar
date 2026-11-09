package es.aprenderparajugar

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray

/**
 * Cola persistente de avisos dirigidos al niño.
 * Se conserva entre pantallas y reinicios; la UI solo confirma un aviso
 * cuando ha terminado de mostrarse.
 */
internal object NotificationQueueRepository {
    private const val PREFERENCES = "aprender_para_jugar"
    private const val QUEUE_KEY = "pending_app_notifications"
    private val pending = mutableStateListOf<String>()
    private val paused = mutableStateOf(false)
    private var initialized = false

    val isPaused: Boolean
        get() = paused.value

    @Synchronized
    fun setPaused(value: Boolean) {
        paused.value = value
    }

    val pendingFolderNames: List<String>
        get() = pending

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val raw = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(QUEUE_KEY, null)
        val restored = runCatching {
            val json = JSONArray(raw ?: "[]")
            (0 until json.length()).mapNotNull { index ->
                json.optString(index).takeIf(String::isNotBlank)
            }
        }.getOrDefault(emptyList())
        pending.clear()
        pending.addAll(restored.distinct())
        initialized = true
    }

    @Synchronized
    fun enqueueStoreUnlocks(context: Context, folderNames: Collection<String>) {
        initialize(context)
        if (folderNames.isEmpty()) return
        val eligible = ObjectRepository.list(context)
            .filter { it.folderName in folderNames && it.metadata.availableInStore &&
                it.metadata.requiresUnlock }
            .map { it.folderName }
        val additions = eligible.filter { it !in pending }
        if (additions.isEmpty()) return
        pending.addAll(additions)
        persist(context)
    }

    @Synchronized
    fun complete(context: Context, folderName: String) {
        initialize(context)
        if (pending.firstOrNull() == folderName) pending.removeAt(0)
        else pending.remove(folderName)
        persist(context)
    }

    private fun persist(context: Context) {
        val json = JSONArray()
        pending.forEach(json::put)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(QUEUE_KEY, json.toString()).commit()
    }
}
