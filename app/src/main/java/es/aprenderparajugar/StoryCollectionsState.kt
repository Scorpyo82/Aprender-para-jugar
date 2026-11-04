package es.aprenderparajugar

import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Estado y operaciones de gestión de colecciones de cuentos.
 *
 * La interfaz conserva la presentación; las reglas de creación, activación y
 * eliminación, junto con su persistencia, quedan fuera de AprenderApp.
 */
internal class StoryCollectionsState(
    private val preferences: SharedPreferences
) {
    val collections: MutableState<List<String>> = mutableStateOf(
        (preferences.getString("story_collections", "") ?: "")
            .split("|").map { it.trim() }.filter { it.isNotBlank() }
    )
    val draft: MutableState<String> = mutableStateOf("")
    val message: MutableState<String> = mutableStateOf("")
    val collectionToDelete: MutableState<String> = mutableStateOf("")
    val disabledCollections: MutableState<Set<String>> = mutableStateOf(
        preferences.getStringSet("disabled_story_collections", emptySet())?.toSet() ?: emptySet()
    )

    fun addCollection() {
        val clean = draft.value.trim()
        when {
            NameRules.validationError(clean) != null -> message.value = NameRules.validationError(clean)!!
            collections.value.any { NameRules.key(it) == NameRules.key(clean) } ->
                message.value = "Esa colección ya existe."
            else -> {
                collections.value = (collections.value + clean).sortedBy { it.lowercase() }
                preferences.edit()
                    .putString("story_collections", collections.value.joinToString("|"))
                    .apply()
                draft.value = ""
                message.value = "Colección creada."
            }
        }
    }

    fun toggleCollection(name: String) {
        disabledCollections.value = if (name in disabledCollections.value) {
            disabledCollections.value - name
        } else {
            disabledCollections.value + name
        }
        preferences.edit()
            .putStringSet("disabled_story_collections", disabledCollections.value)
            .apply()
        message.value = if (name in disabledCollections.value) {
            "Colección «$name» desactivada."
        } else {
            "Colección «$name» activada."
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
        disabledCollections.value = disabledCollections.value - name
        preferences.edit()
            .putString("story_collections", collections.value.joinToString("|"))
            .putStringSet("disabled_story_collections", disabledCollections.value)
            .apply()
        collectionToDelete.value = ""
        message.value = "Colección eliminada."
    }
}
