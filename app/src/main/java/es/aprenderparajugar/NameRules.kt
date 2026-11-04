package es.aprenderparajugar

import java.text.Normalizer
import java.util.Locale

/**
 * Reglas comunes para nombres visibles de colecciones, categorías y actividades.
 * Los textos educativos y narrativos no deben pasar por este filtro.
 */
internal object NameRules {
    fun filterInput(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
        return buildString(normalized.length) {
            normalized.forEach { character ->
                if (character.isLetterOrDigit() || character == ' ' || character == '-') {
                    append(character)
                }
            }
        }
    }

    fun validationError(value: String): String? {
        val clean = Normalizer.normalize(value, Normalizer.Form.NFC).trim()
        if (clean.isBlank()) return "Escribe un nombre."
        if (clean.any { !(it.isLetterOrDigit() || it == ' ' || it == '-') }) {
            return "Usa solo letras, números, espacios y guiones (-)."
        }
        return null
    }

    fun key(value: String): String =
        Normalizer.normalize(value.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)

    fun duplicates(sourceNames: Collection<String>, destinationNames: Collection<String>): List<String> {
        val destinationKeys = destinationNames.map(::key).toSet()
        return sourceNames.distinctBy(::key).filter { key(it) in destinationKeys }
    }
}
