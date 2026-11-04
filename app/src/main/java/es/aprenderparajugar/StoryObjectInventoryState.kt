package es.aprenderparajugar

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Estado y operaciones del inventario de objetos de Cuentos.
 *
 * Centraliza la lectura y las altas/bajas del inventario sin intervenir
 * en la biblioteca de objetos ni en su presentación.
 */
internal class StoryObjectInventoryState(context: Context) {
    val folders: MutableState<Set<String>> = mutableStateOf(
        InventoryRepository.list(context).toSet()
    )

    fun refresh(context: Context) {
        folders.value = InventoryRepository.list(context).toSet()
    }

    /** Alterna la pertenencia de un objeto y devuelve un error legible si falla. */
    fun toggle(context: Context, folderName: String): String? {
        return runCatching {
            folders.value = if (folders.value.contains(folderName)) {
                InventoryRepository.remove(context, folderName).toSet()
            } else {
                InventoryRepository.add(context, folderName).toSet()
            }
        }.exceptionOrNull()?.message
    }

    fun remove(context: Context, folderName: String): String? {
        return runCatching {
            folders.value = InventoryRepository.remove(context, folderName).toSet()
        }.exceptionOrNull()?.message
    }
}
