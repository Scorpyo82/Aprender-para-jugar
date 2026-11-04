package es.aprenderparajugar

import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Estado y operaciones de las colecciones de objetos de Cuentos.
 *
 * Mantiene fuera de AprenderApp la creación, eliminación y persistencia
 * de colecciones. La interfaz conserva su presentación.
 */
internal class StoryObjectCollectionsState(
    private val preferences: SharedPreferences
) {
    val collections: MutableState<List<String>> = mutableStateOf(
        (preferences.getString("story_object_collections", "") ?: "")
            .split("|").map { it.trim() }.filter { it.isNotBlank() }
    )
    val draft: MutableState<String> = mutableStateOf("")
    val message: MutableState<String> = mutableStateOf("")
    val collectionToDelete: MutableState<String> = mutableStateOf("")

    fun addCollection() {
        val clean = draft.value.trim()
        when {
            clean.isBlank() -> message.value = "Escribe un nombre."
            collections.value.any { it.equals(clean, ignoreCase = true) } ->
                message.value = "Esa colección ya existe."
            else -> {
                collections.value = (collections.value + clean).sortedBy { it.lowercase() }
                preferences.edit()
                    .putString("story_object_collections", collections.value.joinToString("|"))
                    .apply()
                draft.value = ""
                message.value = "Colección creada."
            }
        }
    }

    fun requestDelete(name: String) {
        collectionToDelete.value = name
        message.value = ""
    }

    fun cancelDelete() {
        collectionToDelete.value = ""
        message.value = "Operación cancelada."
    }

    fun deleteCollection(name: String) {
        collections.value = collections.value.filterNot { it == name }
        preferences.edit()
            .putString("story_object_collections", collections.value.joinToString("|"))
            .apply()
        collectionToDelete.value = ""
        message.value = "Colección eliminada."
    }
}
