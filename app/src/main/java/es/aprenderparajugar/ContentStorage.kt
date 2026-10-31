package es.aprenderparajugar

import android.content.Context
import java.io.File

/**
 * Acceso inicial a la estructura privada de almacenamiento de los contenidos.
 *
 * La clasificación física es modalidad/categoría/ejercicio. El tipo de Secuencias
 * permanece en los metadatos del ejercicio y no crea directorios adicionales.
 */
object ContentStorage {

    enum class Modality(val directoryName: String) {
        TARJETAS("tarjetas"),
        PALABRAS("palabras"),
        SECUENCIAS("secuencias")
    }

    data class Roots(
        val tarjetas: File,
        val palabras: File,
        val secuencias: File
    )

    /**
     * Crea las tres carpetas raíz si todavía no existen.
     * Se ejecuta al iniciar la aplicación y no altera ni elimina contenido existente.
     */
    fun initialize(context: Context): Roots {
        // filesDir ya es la carpeta privada "files/" de esta aplicación.
        val root = ensureDirectory(context.filesDir)

        return Roots(
            tarjetas = ensureDirectory(File(root, Modality.TARJETAS.directoryName)),
            palabras = ensureDirectory(File(root, Modality.PALABRAS.directoryName)),
            secuencias = ensureDirectory(File(root, Modality.SECUENCIAS.directoryName))
        )
    }

    /** Devuelve la carpeta de una modalidad, creándola si fuera necesario. */
    fun modalityDirectory(context: Context, modality: Modality): File {
        val root = ensureDirectory(context.filesDir)
        return ensureDirectory(File(root, modality.directoryName))
    }

    /**
     * Devuelve la carpeta de una categoría y la crea si no existe.
     * El nombre mostrado al usuario se conservará en el JSON; este nombre solo
     * se utiliza para construir una ruta segura en el sistema de archivos.
     */
    fun categoryDirectory(context: Context, modality: Modality, category: String): File {
        val safeCategory = safePathSegment(category)
        return ensureDirectory(File(modalityDirectory(context, modality), safeCategory))
    }

    /**
     * Devuelve la ubicación prevista de un ejercicio sin crearla.
     * La detección de duplicados y la política de sufijos se resolverán en el gestor.
     */
    fun exerciseDirectory(
        context: Context,
        modality: Modality,
        category: String,
        exerciseName: String
    ): File {
        val safeExerciseName = safePathSegment(exerciseName)
        return File(File(modalityDirectory(context, modality), safePathSegment(category)), safeExerciseName)
    }

    private fun safePathSegment(value: String): String {
        val normalized = value.trim()
            .replace(Regex("[\\s]+"), "_")
            .map { character ->
                if (character.isLetterOrDigit() || character == '_' || character == '-') {
                    character
                } else {
                    '_'
                }
            }
            .joinToString("")
            .replace(Regex("_+"), "_")
            .trim('_')

        require(normalized.isNotBlank() && normalized != "." && normalized != "..") {
            "El nombre de categoría o ejercicio no es válido."
        }
        return normalized
    }

    private fun ensureDirectory(directory: File): File {
        if (directory.exists()) {
            check(directory.isDirectory) {
                "La ruta de contenidos existe, pero no es una carpeta: ${directory.absolutePath}"
            }
            return directory
        }

        check(directory.mkdirs() || directory.isDirectory) {
            "No se pudo crear la carpeta de contenidos: ${directory.absolutePath}"
        }
        return directory
    }
}
