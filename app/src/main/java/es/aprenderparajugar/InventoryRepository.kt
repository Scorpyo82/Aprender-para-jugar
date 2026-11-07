package es.aprenderparajugar

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Resultado de una compra realizada desde el inventario infantil. */
data class InventoryPurchaseResult(
    val success: Boolean,
    val balanceSeconds: Int,
    val message: String
)

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

    /** Compra un objeto de tienda y descuenta su coste del saldo de juego. */
    fun purchase(context: Context, folderName: String): InventoryPurchaseResult {
        val preferences = context.getSharedPreferences("aprender_para_jugar", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val currentDeadline = preferences.getLong("play_deadline_ms", 0L)
        val currentBalance = if (currentDeadline > now) {
            ((currentDeadline - now + 999L) / 1000L).toInt().coerceAtLeast(0)
        } else {
            preferences.getInt("balance_seconds", 0).coerceAtLeast(0)
        }
        fun failure(message: String) = InventoryPurchaseResult(false, currentBalance, message)

        val stored = ObjectRepository.list(context).firstOrNull { it.folderName == folderName }
            ?: return failure("Este objeto ya no está disponible.")
        if (!stored.metadata.availableInStore) return failure("Este objeto no está disponible en la tienda.")
        if (stored.metadata.requiresUnlock && !ObjectUnlockRepository.contains(context, folderName)) return failure("Primero tienes que desbloquear este objeto.")

        val currentInventory = list(context)
        if (folderName in currentInventory) return failure("Ya has conseguido este objeto.")

        val priceMinutes = stored.metadata.storePriceMinutes
        if (priceMinutes <= 0) return failure("Este objeto no tiene un precio válido.")
        val costSeconds = priceMinutes * 60
        if (currentBalance < costSeconds) {
            val missingMinutes = (costSeconds - currentBalance + 59) / 60
            return failure("Todavía no tienes saldo suficiente. Te faltan $missingMinutes min.")
        }

        var inventoryUpdated = false
        try {
            save(context, currentInventory + folderName)
            inventoryUpdated = true
            val updatedDeadline = if (currentDeadline > now) currentDeadline - costSeconds.toLong() * 1000L else 0L
            val updatedBalance = if (currentDeadline > now) {
                if (updatedDeadline > now) ((updatedDeadline - now + 999L) / 1000L).toInt().coerceAtLeast(0) else 0
            } else {
                (currentBalance - costSeconds).coerceAtLeast(0)
            }
            val editor = preferences.edit().putInt("balance_seconds", updatedBalance)
            if (currentDeadline > now) {
                editor.putLong("play_deadline_ms", updatedDeadline.takeIf { it > now } ?: 0L)
                    .putBoolean("play_session_authorized", updatedDeadline > now)
                    .putBoolean("play_paused_for_screen_lock", false)
            }
            if (!editor.commit()) {
                save(context, currentInventory)
                inventoryUpdated = false
                return failure("No se ha podido guardar la compra. Inténtalo de nuevo.")
            }
            if (currentDeadline > now && updatedDeadline <= now) {
                context.stopService(Intent(context, PlayTimeService::class.java))
            }
            return InventoryPurchaseResult(true, updatedBalance, "¡Has conseguido ${stored.metadata.name}!")
        } catch (error: Exception) {
            if (inventoryUpdated) runCatching { save(context, currentInventory) }
            return failure(error.message ?: "No se ha podido completar la compra.")
        }
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
