package es.aprenderparajugar

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Estadísticas globales de partidas completadas, independientes de las estadísticas
 * de aprendizaje de cada ejercicio. Se almacenan en la raíz de cada modalidad.
 */
internal object UsageStatisticsRepository {
    const val FILE_NAME = "statistics_usage.json"
    private const val SCHEMA_VERSION = 1
    private val difficulties = listOf("Fácil", "Normal", "Difícil")

    fun read(context: Context, modality: ContentStorage.Modality): JSONObject {
        val file = fileFor(context, modality)
        if (!file.isFile) return emptyStatistics(modality).also { write(file, it) }
        val statistics = JSONObject(file.readText(Charsets.UTF_8))
        validate(statistics, modality)
        return statistics
    }

    fun recordCompleted(
        context: Context,
        modality: ContentStorage.Modality,
        difficulty: String,
        sequenceType: SequenceType? = null
    ) {
        val level = difficulties.firstOrNull { it.equals(difficulty, ignoreCase = true) }
            ?: throw IllegalArgumentException("Dificultad no reconocida: $difficulty")
        require((modality == ContentStorage.Modality.SECUENCIAS) == (sequenceType != null)) {
            "El tipo de secuencia solo se admite para la modalidad Secuencias."
        }

        val statistics = read(context, modality)
        statistics.put("total", statistics.optInt("total", 0) + 1)
        if (modality == ContentStorage.Modality.SECUENCIAS) {
            val byType = statistics.getJSONObject("byType")
            val typeStats = byType.optJSONObject(sequenceType!!.jsonValue)
                ?: JSONObject().also { byType.put(sequenceType.jsonValue, it) }
            typeStats.put(level, typeStats.optInt(level, 0) + 1)
        } else {
            val byDifficulty = statistics.getJSONObject("byDifficulty")
            byDifficulty.put(level, byDifficulty.optInt(level, 0) + 1)
        }
        write(fileFor(context, modality), statistics)
    }

    fun validateFile(file: File, modality: ContentStorage.Modality) {
        require(file.isFile) { "Falta el archivo de estadísticas de uso de ${modality.directoryName}." }
        validate(JSONObject(file.readText(Charsets.UTF_8)), modality)
    }

    private fun emptyStatistics(modality: ContentStorage.Modality): JSONObject {
        val result = JSONObject().put("schemaVersion", SCHEMA_VERSION).put("total", 0)
        if (modality == ContentStorage.Modality.SECUENCIAS) {
            val byType = JSONObject()
            SequenceType.entries.forEach { type ->
                val levels = JSONObject()
                difficulties.forEach { levels.put(it, 0) }
                byType.put(type.jsonValue, levels)
            }
            result.put("byType", byType)
        } else {
            val byDifficulty = JSONObject()
            difficulties.forEach { byDifficulty.put(it, 0) }
            result.put("byDifficulty", byDifficulty)
        }
        return result
    }

    private fun validate(statistics: JSONObject, modality: ContentStorage.Modality) {
        require(statistics.optInt("schemaVersion", -1) == SCHEMA_VERSION) {
            "Las estadísticas de uso de ${modality.directoryName} no son compatibles."
        }
        require(statistics.optInt("total", -1) >= 0) { "El total de partidas no es válido." }
        if (modality == ContentStorage.Modality.SECUENCIAS) {
            val byType = statistics.optJSONObject("byType")
                ?: throw IllegalArgumentException("Faltan los tipos de secuencia en las estadísticas de uso.")
            SequenceType.entries.forEach { type ->
                val levels = byType.optJSONObject(type.jsonValue)
                    ?: throw IllegalArgumentException("Faltan estadísticas para ${type.displayName}.")
                difficulties.forEach { level ->
                    require(levels.optInt(level, -1) >= 0) { "El contador ${type.displayName} / $level no es válido." }
                }
            }
        } else {
            val byDifficulty = statistics.optJSONObject("byDifficulty")
                ?: throw IllegalArgumentException("Faltan las dificultades en las estadísticas de uso.")
            difficulties.forEach { level ->
                require(byDifficulty.optInt(level, -1) >= 0) { "El contador de $level no es válido." }
            }
        }
    }

    private fun fileFor(context: Context, modality: ContentStorage.Modality): File =
        File(ContentStorage.modalityDirectory(context, modality), FILE_NAME)

    private fun write(destination: File, statistics: JSONObject) {
        val temporary = File(destination.parentFile, ".${destination.name}.tmp")
        val backup = File(destination.parentFile, ".${destination.name}.bak")
        temporary.writeText(statistics.toString(2), Charsets.UTF_8)
        if (destination.exists() && !destination.renameTo(backup)) {
            temporary.delete()
            throw IllegalStateException("No se pudieron reservar las estadísticas de uso anteriores.")
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            if (backup.exists()) backup.renameTo(destination)
            throw IllegalStateException("No se pudieron guardar las estadísticas de uso.")
        }
        if (backup.exists()) backup.delete()
    }
}
