package es.aprenderparajugar

/**
 * Resolves the previous parental-settings section without performing navigation side effects.
 * A null result means that the parent flow should leave the parental area.
 */
internal fun parentBackSection(
    currentSection: String,
    hasRemoteImportOrigin: Boolean
): String? = when (currentSection) {
    "word-library", "word-categories" -> "words-settings"
    "words", "categories" -> "cards-settings"
    "cards-settings", "sequences-settings", "words-settings", "stories-settings" -> "game-modes"
    "stories-content-settings", "stories-objects" -> "stories-settings"
    "stories-library", "stories-collections" -> "stories-content-settings"
    "stories-editor" -> "stories-library"
    "stories-structure", "stories-scene-editor" -> "stories-editor"
    "stories-object-create", "stories-object-library", "stories-object-store", "stories-object-collection", "stories-object-inventory" -> "stories-objects"
    "active-games", "sound-settings", "rewards", "security", "device-setup", "about",
    "basic-library", "library-transfer", "statistics-menu", "typography", "game-modes" -> "menu"
    "music", "stories-audio" -> "sound-settings"
    "library-export", "library-delete" -> "library-transfer"
    "library-import" -> if (hasRemoteImportOrigin) "basic-library" else "library-transfer"
    "learning-stats", "usage-stats" -> "statistics-menu"
    "sequence-type-temporal", "sequence-type-procesos", "sequence-type-causa", "sequence-type-ciclos",
    "sequence-library", "sequence-categories" -> "sequences-settings"
    "sequence-library-temporal", "sequence-categories-temporal" -> "sequence-type-temporal"
    "sequence-editor-temporal" -> "sequence-library"
    "sequence-library-procesos", "sequence-categories-procesos" -> "sequence-type-procesos"
    "sequence-library-causa", "sequence-categories-causa" -> "sequence-type-causa"
    "sequence-library-ciclos", "sequence-categories-ciclos" -> "sequence-type-ciclos"
    else -> null
}
