package es.aprenderparajugar

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.io.File

/**
 * Estado y guardado del formulario de objetos de Cuentos.
 *
 * La interfaz conserva los selectores de imagen y la navegación; esta clase
 * centraliza los datos del formulario y la persistencia del objeto.
 */
internal class StoryObjectEditorState {
    val nameDraft: MutableState<String> = mutableStateOf("")
    val pictureDraft: MutableState<String> = mutableStateOf("")
    val editingFolder: MutableState<String?> = mutableStateOf(null)
    val imageChanged: MutableState<Boolean> = mutableStateOf(false)
    val availableInStore: MutableState<Boolean> = mutableStateOf(false)
    val storePriceMinutes: MutableState<String> = mutableStateOf("")
    val collectible: MutableState<Boolean> = mutableStateOf(false)
    val requiresUnlock: MutableState<Boolean> = mutableStateOf(false)
    val message: MutableState<String> = mutableStateOf("")
    val selectedCollection: MutableState<String> = mutableStateOf("")

    fun reset() {
        editingFolder.value = null
        nameDraft.value = ""
        pictureDraft.value = ""
        selectedCollection.value = ""
        imageChanged.value = false
        availableInStore.value = false
        storePriceMinutes.value = ""
        collectible.value = false
        requiresUnlock.value = false
        message.value = ""
    }

    fun beginEdit(stored: StoredObject) {
        editingFolder.value = stored.folderName
        nameDraft.value = stored.metadata.name
        pictureDraft.value = Uri.fromFile(File(stored.directory, stored.metadata.image)).toString()
        selectedCollection.value = stored.collection
        imageChanged.value = false
        availableInStore.value = stored.metadata.availableInStore
        storePriceMinutes.value = stored.metadata.storePriceMinutes.takeIf { it > 0 }?.toString().orEmpty()
        collectible.value = stored.metadata.collectible
        requiresUnlock.value = stored.metadata.requiresUnlock
        message.value = ""
    }

    fun markImageSelected(uri: Uri) {
        pictureDraft.value = uri.toString()
        imageChanged.value = true
    }

    fun save(context: Context): List<StoredObject>? {
        val cleanName = nameDraft.value.trim()
        val collection = selectedCollection.value.trim()
        val existing = editingFolder.value?.let { folder ->
            ObjectRepository.list(context).firstOrNull { it.folderName == folder }
        }
        if ((NameRules.validationError(cleanName) != null && cleanName != existing?.metadata?.name) ||
            (NameRules.validationError(collection) != null && collection != existing?.collection) ||
            pictureDraft.value.isBlank() ||
            (availableInStore.value && (storePriceMinutes.value.toIntOrNull() ?: 0) <= 0)) {
            message.value = NameRules.validationError(cleanName)?.takeIf { cleanName != existing?.metadata?.name }
                ?: NameRules.validationError(collection)?.takeIf { collection != existing?.collection }
                ?: if (pictureDraft.value.isBlank()) "Selecciona una imagen." else "Indica un precio válido en minutos para la tienda."
            return null
        }

        return try {
            val duplicateName = ObjectRepository.list(context).firstOrNull {
                NameRules.key(it.metadata.name) == NameRules.key(cleanName) && it.folderName != editingFolder.value
            }
            require(duplicateName == null) { "Ya existe un objeto con ese nombre. Los nombres de objeto deben ser únicos." }
            val imageBytes = if (existing != null && !imageChanged.value) {
                File(existing.directory, existing.metadata.image).readBytes()
            } else {
                context.contentResolver.openInputStream(Uri.parse(pictureDraft.value))?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("No se ha podido leer la imagen seleccionada.")
            }
            val mime = context.contentResolver.getType(Uri.parse(pictureDraft.value))
            val extension = when (mime?.lowercase()) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "jpg"
            }
            val imageName = if (existing != null && !imageChanged.value) existing.metadata.image else "imagen.$extension"
            val folderName = existing?.folderName ?: cleanName.lowercase()
                .replace(Regex("[^\\p{L}\\p{N}_-]+"), "_")
                .trim('_')
            ObjectRepository.save(context, collection, folderName, ObjectMetadata(cleanName, imageName, availableInStore.value, if (availableInStore.value) storePriceMinutes.value.toInt() else 0, collectible.value, requiresUnlock.value && availableInStore.value), imageBytes)
            if (!availableInStore.value || !requiresUnlock.value) {
                runCatching { ObjectUnlockRepository.remove(context, folderName) }
            }
            reset()
            message.value = "Objeto guardado correctamente."
            ObjectRepository.list(context)
        } catch (error: Exception) {
            message.value = error.message ?: "No se ha podido guardar el objeto."
            null
        }
    }
}
