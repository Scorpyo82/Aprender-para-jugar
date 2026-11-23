package es.aprenderparajugar

internal fun saveTemporalSequenceExercise(
    title: String,
    editingIndex: Int,
    exercises: List<SequenceExercise>,
    category: String,
    cards: List<String>,
    alternativeDraft: String,
    pictures: List<String>,
    sequenceType: String,
    difficulty: String,
    libraryState: SequenceLibraryState,
    onValidationError: (String) -> Unit,
    onSaved: (List<SequenceExercise>) -> Unit
) {
    val cleanCards = cards.map { it.trim() }
    val alternativeLines = alternativeDraft.lines().map { it.trim() }.filter { it.isNotBlank() }
    val parsedAlternatives = alternativeLines.map { line -> line.split(",").map { it.trim().toIntOrNull() } }
    val invalidAlternative = parsedAlternatives.any { indexes ->
        indexes.size != cleanCards.size ||
            indexes.any { it == null || it !in 1..cleanCards.size } ||
            indexes.filterNotNull().distinct().size != cleanCards.size
    }
    val validationError = NameRules.validationError(title)
    when {
        validationError != null && title != exercises.getOrNull(editingIndex)?.title ->
            onValidationError(validationError)
        exercises.withIndex().any { (index, item) ->
            index != editingIndex &&
                NameRules.key(item.title) == NameRules.key(title) &&
                NameRules.key(item.category) == NameRules.key(category)
        } -> onValidationError("Ya existe un ejercicio con ese nombre en esta categoría.")
        category.isBlank() -> onValidationError("Crea o selecciona una categoría antes de guardar el ejercicio.")
        cleanCards.size < 2 || cleanCards.any { it.isBlank() } ->
            onValidationError("Completa al menos 2 tarjetas; no puede haber pasos vacíos.")
        cleanCards.size > 6 -> onValidationError("El máximo es de 6 tarjetas.")
        pictures.indices.take(cleanCards.size).any { pictures.getOrNull(it).isNullOrBlank() } ||
            pictures.size < cleanCards.size ->
            onValidationError("Selecciona una imagen para cada tarjeta. No puede quedar ninguna imagen vacía.")
        alternativeLines.isNotEmpty() && invalidAlternative ->
            onValidationError("Cada alternativa debe incluir todos los números del 1 al ${cleanCards.size}, sin repetir y separados por comas.")
        else -> {
            val previousExercise = exercises.getOrNull(editingIndex)
            val exercise = SequenceExercise(
                title.trim(),
                cleanCards,
                "Imagen y texto",
                category.trim(),
                alternativeLines,
                cleanCards.indices.map { pictures.getOrNull(it).orEmpty() },
                sequenceType,
                difficulty,
                previousExercise?.active ?: true,
                previousExercise?.directory,
                previousExercise?.createdAt.orEmpty()
            )
            val updatedExercises = exercises.toMutableList().also {
                if (editingIndex < 0) it.add(exercise) else it[editingIndex] = exercise
            }
            onSaved(libraryState.saveExercises(updatedExercises))
        }
    }
}
