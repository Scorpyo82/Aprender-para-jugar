package es.aprenderparajugar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistencia del inventario del niño.
 *
 * Cada entrada referencia el nombre de carpeta único del objeto en la biblioteca
 * común. No copia sus datos ni su imagen.
 */
object InventoryRepository {

    private const val ROOT_DIRECTORY = "inventario"
    private const val JSON_FILE = "inventario.json"

    fun list(context: Context): Set<String> {
        val file = inventoryFile(context)
        if (!file.isFile) return emptySet()

        val root = JSONObject(file.readText(Charsets.UTF_8))
        val objects = root.optJSONArray("objects") ?: JSONArray()
        return (0 until objects.length())
            .mapNotNull { index -> objects.optString(index).takeIf { it.isNotBlank() } }
            .toSet()
    }

    fun contains(context: Context, folderName: String): Boolean =
        list(context).contains(validateFolderName(folderName))

    fun add(context: Context, folderName: String): Set<String> {
        val safeName = validateFolderName(folderName)
        require(ObjectRepository.list(context).any { it.folderName == safeName }) {
            "El objeto ya no existe en la biblioteca."
        }
        val updated = list(context) + safeName
        save(context, updated)
        return updated
    }

    fun remove(context: Context, folderName: String): Set<String> {
        val safeName = validateFolderName(folderName)
        val updated = list(context) - safeName
        save(context, updated)
        return updated
    }

    private fun save(context: Context, folderNames: Set<String>) {
        val directory = File(context.filesDir, ROOT_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) {
            "No se pudo crear la carpeta del inventario."
        }
        check(directory.isDirectory) { "La ruta del inventario no es una carpeta." }

        val target = File(directory, JSON_FILE)
        val temporary = File(directory, ".$JSON_FILE.tmp")
        val json = JSONObject()
            .put("objects", JSONArray(folderNames.sorted()))
        temporary.writeText(json.toString(2), Charsets.UTF_8)

        if (target.exists() && !target.delete()) {
            temporary.delete()
            throw IllegalStateException("No se pudo actualizar el inventario.")
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IllegalStateException("No se pudo guardar el inventario.")
        }
    }

    private fun inventoryFile(context: Context): File =
        File(File(context.filesDir, ROOT_DIRECTORY), JSON_FILE)

    private fun validateFolderName(value: String): String {
        val name = value.trim()
        require(
            name.isNotBlank() &&
                name != "." &&
                name != ".." &&
                !name.contains('/') &&
                !name.contains('\\') &&
                !name.contains(':')
        ) { "La referencia del objeto no es válida." }
        return name
    }
}
