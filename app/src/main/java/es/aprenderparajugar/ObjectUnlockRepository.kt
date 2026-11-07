package es.aprenderparajugar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Progreso local de desbloqueo del único niño de esta instalación.
 * No forma parte de las bibliotecas ni se exporta/importa.
 */
object ObjectUnlockRepository {
    private const val ROOT_DIRECTORY = "desbloqueos"
    private const val JSON_FILE = "objetos_desbloqueados.json"

    fun list(context: Context): Set<String> {
        val file = File(File(context.filesDir, ROOT_DIRECTORY), JSON_FILE)
        if (!file.isFile) return emptySet()
        return runCatching {
            val values = JSONObject(file.readText(Charsets.UTF_8)).optJSONArray("objects") ?: JSONArray()
            (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun contains(context: Context, folderName: String): Boolean = folderName in list(context)

    fun unlock(context: Context, folderNames: Collection<String>): Set<String> {
        val validFolders = ObjectRepository.list(context).filter { it.metadata.availableInStore && it.metadata.requiresUnlock }.map { it.folderName }.toSet()
        val updated = list(context) + folderNames.filter { it in validFolders }
        save(context, updated)
        return updated
    }

    fun remove(context: Context, folderName: String): Set<String> {
        val updated = list(context) - folderName
        save(context, updated)
        return updated
    }

    private fun save(context: Context, folders: Set<String>) {
        val directory = File(context.filesDir, ROOT_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) { "No se pudo crear el almacenamiento de desbloqueos." }
        val target = File(directory, JSON_FILE)
        val temporary = File(directory, ".$JSON_FILE.tmp")
        temporary.writeText(JSONObject().put("objects", JSONArray(folders.sorted())).toString(2), Charsets.UTF_8)
        if (target.exists() && !target.delete()) {
            temporary.delete()
            throw IllegalStateException("No se pudo actualizar el progreso de desbloqueos.")
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IllegalStateException("No se pudo guardar el progreso de desbloqueos.")
        }
    }
}
