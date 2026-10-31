package es.aprenderparajugar

import org.json.JSONArray
import org.json.JSONObject

/**
 * Modelo persistente común para los ejercicios de las tres bibliotecas.
 *
 * Las rutas de imagen son relativas a la carpeta del ejercicio. No deben
 * contener URIs content:// ni rutas absolutas del dispositivo.
 */
data class ExerciseMetadata(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val modality: ContentStorage.Modality,
    val title: String,
    val category: String,
    val difficulty: String,
    val active: Boolean = true,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class CardExerciseContent(
    val word: String,
    val image: String
)

data class WordExerciseContent(
    val word: String,
    val hintImage: String
)

data class SequenceStep(
    /** Identificador local utilizado por las soluciones alternativas. */
    val id: String,
    val text: String,
    /** Nombre relativo del archivo de imagen dentro de la carpeta del ejercicio. */
    val image: String
)

data class SequenceExerciseContent(
    val type: SequenceType,
    val steps: List<SequenceStep>,
    /** Cada alternativa es una ordenación completa de identificadores de pasos. */
    val alternativeSolutions: List<List<String>>
)

enum class SequenceType(val jsonValue: String, val displayName: String) {
    ORDEN_TEMPORAL("orden_temporal", "Orden temporal"),
    PROCESOS("procesos", "Procesos"),
    CAUSA_EFECTO("causa_efecto", "Causa y consecuencia"),
    CICLOS("ciclos", "Ciclos");

    companion object {
        fun fromJson(value: String): SequenceType =
            entries.firstOrNull { it.jsonValue == value }
                ?: throw IllegalArgumentException("Tipo de secuencia no reconocido: $value")

        fun fromDisplayName(value: String): SequenceType =
            entries.firstOrNull { it.displayName.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Tipo de secuencia no reconocido: $value")
    }
}

/**
 * Serialización del formato ejercicio.json.
 *
 * Esta clase no accede al disco: la escritura atómica, las imágenes y las
 * estadísticas pertenecen al repositorio de contenidos.
 */
object ExerciseJsonCodec {

    fun encode(metadata: ExerciseMetadata, content: Any): JSONObject {
        validateMetadata(metadata)
        val jsonContent = when (metadata.modality) {
            ContentStorage.Modality.TARJETAS -> encodeCards(content as? CardExerciseContent
                ?: invalidContent(metadata.modality))
            ContentStorage.Modality.PALABRAS -> encodeWords(content as? WordExerciseContent
                ?: invalidContent(metadata.modality))
            ContentStorage.Modality.SECUENCIAS -> encodeSequences(content as? SequenceExerciseContent
                ?: invalidContent(metadata.modality))
        }

        return JSONObject().apply {
            put("schemaVersion", metadata.schemaVersion)
            put("modality", metadata.modality.directoryName)
            put("title", metadata.title.trim())
            put("category", metadata.category.trim())
            put("difficulty", metadata.difficulty.trim())
            put("active", metadata.active)
            put("createdAt", metadata.createdAt)
            put("updatedAt", metadata.updatedAt)
            put("content", jsonContent)
        }
    }

    fun decode(json: JSONObject): Pair<ExerciseMetadata, Any> {
        val schemaVersion = json.optInt("schemaVersion", -1)
        require(schemaVersion == ExerciseMetadata.CURRENT_SCHEMA_VERSION) {
            "Versión de ejercicio no compatible: $schemaVersion"
        }

        val modality = ContentStorage.Modality.entries.firstOrNull {
            it.directoryName == json.optString("modality")
        } ?: throw IllegalArgumentException("Modalidad de ejercicio no reconocida.")

        val metadata = ExerciseMetadata(
            schemaVersion = schemaVersion,
            modality = modality,
            title = json.optString("title").trim(),
            category = json.optString("category").trim(),
            difficulty = json.optString("difficulty").trim(),
            active = json.optBoolean("active", true),
            createdAt = json.optString("createdAt"),
            updatedAt = json.optString("updatedAt")
        )
        validateMetadata(metadata)

        val content = json.optJSONObject("content")
            ?: throw IllegalArgumentException("El ejercicio no contiene el bloque 'content'.")

        val decodedContent: Any = when (modality) {
            ContentStorage.Modality.TARJETAS -> decodeCards(content)
            ContentStorage.Modality.PALABRAS -> decodeWords(content)
            ContentStorage.Modality.SECUENCIAS -> decodeSequences(content)
        }
        return metadata to decodedContent
    }

    private fun encodeCards(content: CardExerciseContent): JSONObject {
        require(content.word.isNotBlank()) { "La palabra de la tarjeta no puede estar vacía." }
        require(isSafeAssetName(content.image)) { "La imagen de la tarjeta debe ser un nombre de archivo relativo." }
        return JSONObject().put("word", content.word.trim()).put("image", content.image)
    }

    private fun encodeWords(content: WordExerciseContent): JSONObject {
        require(content.word.isNotBlank()) { "La palabra del ejercicio no puede estar vacía." }
        require(isSafeAssetName(content.hintImage)) { "La imagen de apoyo debe ser un nombre de archivo relativo." }
        return JSONObject().put("word", content.word.trim()).put("hintImage", content.hintImage)
    }

    private fun encodeSequences(content: SequenceExerciseContent): JSONObject {
        validateSequence(content)
        val steps = JSONArray()
        content.steps.forEach { step ->
            steps.put(JSONObject()
                .put("id", step.id)
                .put("text", step.text.trim())
                .put("image", step.image))
        }
        val alternatives = JSONArray()
        content.alternativeSolutions.forEach { solution ->
            alternatives.put(JSONArray(solution))
        }
        return JSONObject()
            .put("type", content.type.jsonValue)
            .put("steps", steps)
            .put("alternativeSolutions", alternatives)
    }

    private fun decodeCards(content: JSONObject): CardExerciseContent =
        CardExerciseContent(
            word = content.optString("word").trim(),
            image = content.optString("image")
        ).also {
            require(it.word.isNotBlank()) { "La tarjeta no contiene palabra." }
            require(isSafeAssetName(it.image)) { "La imagen de la tarjeta no es una ruta relativa válida." }
        }

    private fun decodeWords(content: JSONObject): WordExerciseContent =
        WordExerciseContent(
            word = content.optString("word").trim(),
            hintImage = content.optString("hintImage")
        ).also {
            require(it.word.isNotBlank()) { "El ejercicio de Palabras no contiene palabra." }
            require(isSafeAssetName(it.hintImage)) { "La imagen de apoyo no es una ruta relativa válida." }
        }

    private fun decodeSequences(content: JSONObject): SequenceExerciseContent {
        val stepsJson = content.optJSONArray("steps")
            ?: throw IllegalArgumentException("La secuencia no contiene pasos.")
        val steps = (0 until stepsJson.length()).map { index ->
            val item = stepsJson.optJSONObject(index)
                ?: throw IllegalArgumentException("El paso ${index + 1} no es válido.")
            SequenceStep(
                id = item.optString("id"),
                text = item.optString("text").trim(),
                image = item.optString("image")
            )
        }
        val alternativesJson = content.optJSONArray("alternativeSolutions") ?: JSONArray()
        val alternatives = (0 until alternativesJson.length()).map { index ->
            val solution = alternativesJson.optJSONArray(index)
                ?: throw IllegalArgumentException("La solución alternativa ${index + 1} no es válida.")
            (0 until solution.length()).map { solution.getString(it) }
        }
        return SequenceExerciseContent(
            type = SequenceType.fromJson(content.optString("type")),
            steps = steps,
            alternativeSolutions = alternatives
        ).also(::validateSequence)
    }

    private fun validateMetadata(metadata: ExerciseMetadata) {
        require(metadata.schemaVersion == ExerciseMetadata.CURRENT_SCHEMA_VERSION) {
            "Versión de esquema no compatible."
        }
        require(metadata.title.isNotBlank()) { "El título del ejercicio no puede estar vacío." }
        require(metadata.category.isNotBlank()) { "La categoría del ejercicio no puede estar vacía." }
        require(metadata.difficulty.isNotBlank()) { "La dificultad del ejercicio no puede estar vacía." }
        require(metadata.createdAt.isNotBlank() && metadata.updatedAt.isNotBlank()) {
            "Faltan las fechas de creación o actualización."
        }
    }

    private fun validateSequence(content: SequenceExerciseContent) {
        require(content.steps.size in 2..6) { "Una secuencia debe contener entre 2 y 6 pasos." }
        val ids = content.steps.map { it.id }
        require(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) {
            "Cada paso debe tener un identificador único."
        }
        content.steps.forEachIndexed { index, step ->
            require(step.text.isNotBlank()) { "El paso ${index + 1} no contiene texto." }
            require(isSafeAssetName(step.image)) {
                "El paso ${index + 1} no contiene un nombre de imagen relativo válido."
            }
        }
        content.alternativeSolutions.forEachIndexed { index, solution ->
            require(solution.size == ids.size && solution.toSet() == ids.toSet()) {
                "La solución alternativa ${index + 1} debe incluir todos los pasos una sola vez."
            }
        }
    }

    private fun isSafeAssetName(value: String): Boolean {
        if (value.isBlank()) return false
        if (value.startsWith("emoji:")) return value.length > "emoji:".length
        if (value.startsWith("/") || value.contains('\\') || value.contains(':')) return false
        val segments = value.split('/')
        return segments.all { it.isNotBlank() && it != "." && it != ".." }
    }

    private fun invalidContent(modality: ContentStorage.Modality): Nothing =
        throw IllegalArgumentException("El contenido no corresponde a la modalidad ${modality.directoryName}.")
}
