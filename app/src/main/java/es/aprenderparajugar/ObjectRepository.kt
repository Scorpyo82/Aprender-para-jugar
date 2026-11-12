package es.aprenderparajugar

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Datos persistentes de un objeto de la biblioteca común.
 *
 * La identidad del objeto es el nombre de su carpeta, no un campo del JSON.
 * La colección se obtiene de la carpeta que contiene al objeto.
 */
data class ObjectMetadata(
    val name: String,
    val image: String,
    val availableInStore: Boolean = false,
    val storePriceMinutes: Int = 0,
    val collectible: Boolean = false,
    val requiresUnlock: Boolean = false
)

data class StoredObject(
    val directory: File,
    val collection: String,
    val folderName: String,
    val metadata: ObjectMetadata
)

/** Conversión entre ObjectMetadata y el contenido de objeto.json. */
object ObjectJsonCodec {

    fun encode(metadata: ObjectMetadata): JSONObject {
        validate(metadata)
        return JSONObject()
            .put("name", metadata.name.trim())
            .put("image", metadata.image)
            .put("availableInStore", metadata.availableInStore)
            .put("storePriceMinutes", metadata.storePriceMinutes)
            .put("collectible", metadata.collectible)
            .put("requiresUnlock", metadata.requiresUnlock && metadata.availableInStore)
    }

    fun decode(json: JSONObject): ObjectMetadata {
        val metadata = ObjectMetadata(
            name = json.optString("name").trim(),
            image = json.optString("image"),
            availableInStore = json.optBoolean("availableInStore", false),
            storePriceMinutes = json.optInt("storePriceMinutes", 0),
            collectible = json.optBoolean("collectible", false),
            requiresUnlock = json.optBoolean("requiresUnlock", false) && json.optBoolean("availableInStore", false)
        )
        validate(metadata)
        return metadata
    }

    private fun validate(metadata: ObjectMetadata) {
        require(metadata.name.isNotBlank()) { "El nombre del objeto no puede estar vacío." }
        require(isSafeRelativeFileName(metadata.image)) {
            "La imagen debe ser un nombre de archivo relativo válido."
        }
        require(metadata.storePriceMinutes >= 0) { "El precio no puede ser negativo." }
        require(!metadata.availableInStore || metadata.storePriceMinutes > 0) {
            "Indica un precio en minutos para los objetos disponibles en la tienda."
        }
    }

    private fun isSafeRelativeFileName(value: String): Boolean =
        value.isNotBlank() &&
            value != "." &&
            value != ".." &&
            !value.startsWith("/") &&
            !value.contains('/') &&
            !value.contains('\\') &&
            !value.contains(':')
}

/**
 * Lectura y escritura de objetos en filesDir/objetos/coleccion/nombre_carpeta.
 *
 * El nombre de la carpeta del objeto es único en toda la biblioteca, incluso
 * aunque los objetos estén repartidos entre colecciones diferentes.
 */
object ObjectRepository {

    private const val ROOT_DIRECTORY = "objetos"
    private const val JSON_FILE = "objeto.json"

    fun collectionFolderName(value: String): String =
        safePathSegment(value, "La colección")

    fun rootDirectory(context: Context): File =
        ensureDirectory(File(context.filesDir, ROOT_DIRECTORY))

    /** Devuelve todos los objetos válidos de todas las colecciones. */
    fun list(context: Context): List<StoredObject> {
        val root = rootDirectory(context)
        return root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }
            ?.flatMap { collectionDirectory ->
                collectionDirectory.listFiles()
                    ?.filter { it.isDirectory && !it.name.startsWith(".") }
                    ?.sortedBy { it.name.lowercase() }
                    ?.mapNotNull { objectDirectory -> runCatching { read(objectDirectory) }.getOrNull() }
                    .orEmpty()
            }
            .orEmpty()
    }

    /** Lee un objeto concreto y comprueba que su imagen exista. */
    fun read(directory: File): StoredObject {
        require(directory.isDirectory) { "La carpeta del objeto no existe." }
        val root = directory.canonicalFile
        val jsonFile = File(root, JSON_FILE)
        require(jsonFile.isFile) { "La carpeta no contiene $JSON_FILE." }

        val metadata = ObjectJsonCodec.decode(
            JSONObject(jsonFile.readText(Charsets.UTF_8))
        )
        val imageFile = File(root, metadata.image).canonicalFile
        require(imageFile.toPath().startsWith(root.toPath()) && imageFile.isFile) {
            "No se encuentra la imagen del objeto: ${metadata.image}"
        }
        val collectionDirectory = root.parentFile
            ?: throw IllegalArgumentException("La carpeta del objeto no pertenece a una colección.")
        return StoredObject(
            directory = root,
            collection = collectionDirectory.name,
            folderName = root.name,
            metadata = metadata
        )
    }

    /**
     * Crea o actualiza un objeto. La imagen se guarda como archivo independiente.
     * Si el objeto ya existe en la misma colección y con el mismo nombre de carpeta,
     * se sustituye de forma segura; no se sobrescriben objetos de otras carpetas.
     */
    fun save(
        context: Context,
        collection: String,
        folderName: String,
        metadata: ObjectMetadata,
        imageBytes: ByteArray
    ): StoredObject {
        val safeCollection = safePathSegment(collection, "La colección")
        val safeFolderName = safePathSegment(folderName, "El nombre de carpeta")
        require(imageBytes.isNotEmpty()) { "La imagen del objeto está vacía." }

        val json = ObjectJsonCodec.encode(metadata)
        val root = rootDirectory(context).canonicalFile
        val collectionDirectory = ensureDirectory(File(root, safeCollection)).canonicalFile
        val target = File(collectionDirectory, safeFolderName).canonicalFile
        require(target.toPath().startsWith(root.toPath())) {
            "La ubicación del objeto no es válida."
        }

        val duplicate = findFolderName(context, safeFolderName)
        require(duplicate == null || duplicate.canonicalFile == target) {
            "Ya existe un objeto con ese nombre de carpeta en otra colección."
        }

        val staging = File(collectionDirectory, ".staging-${System.nanoTime()}")
        val backup = File(collectionDirectory, ".backup-${System.nanoTime()}")
        check(staging.mkdir()) { "No se pudo preparar el guardado del objeto." }

        try {
            File(staging, metadata.image).writeBytes(imageBytes)
            File(staging, JSON_FILE).writeText(json.toString(2), Charsets.UTF_8)

            if (target.exists()) {
                require(target.isDirectory && File(target, JSON_FILE).isFile) {
                    "La carpeta de destino existe, pero no contiene un objeto válido."
                }
                check(target.renameTo(backup)) {
                    "No se pudo reservar la carpeta anterior del objeto."
                }
            }

            if (!staging.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                throw IllegalStateException("No se pudo completar el guardado del objeto.")
            }

            if (backup.exists()) backup.deleteRecursively()
            return read(target)
        } catch (error: Exception) {
            if (staging.exists()) staging.deleteRecursively()
            if (backup.exists() && !target.exists()) backup.renameTo(target)
            throw error
        }
    }

    /** Elimina físicamente un objeto. La comprobación de referencias narrativas se realiza antes desde la interfaz. */
    fun delete(context: Context, stored: StoredObject) {
        val root = rootDirectory(context).canonicalFile
        val target = stored.directory.canonicalFile
        require(target.toPath().startsWith(root.toPath()) && target.parentFile?.parentFile == root) {
            "La ubicación del objeto no es válida."
        }
        require(target.name == stored.folderName && target.isDirectory) {
            "El objeto ya no está disponible en su ubicación original."
        }
        check(target.walkBottomUp().all { it.delete() || !it.exists() }) {
            "No se pudo eliminar todo el contenido del objeto."
        }
    }

    /** Reasigna un objeto a otra colección conservando su identidad y referencias locales. */
    fun moveToCollection(context: Context, stored: StoredObject, collection: String): StoredObject {
        val safeCollection = safePathSegment(collection, "La colección")
        val root = rootDirectory(context).canonicalFile
        val source = stored.directory.canonicalFile
        require(source.toPath().startsWith(root.toPath()) && source.isDirectory) {
            "La ubicación actual del objeto no es válida."
        }
        val targetCollection = ensureDirectory(File(root, safeCollection)).canonicalFile
        require(targetCollection.toPath().startsWith(root.toPath())) {
            "La colección de destino no es válida."
        }
        val target = File(targetCollection, stored.folderName).canonicalFile
        if (source.parentFile.canonicalFile == targetCollection) return read(source)
        require(!target.exists()) { "Ya existe un objeto con el mismo identificador en la colección de destino." }
        check(source.renameTo(target)) { "No se pudo reasignar el objeto a la colección seleccionada." }
        return read(target)
    }

    private fun findFolderName(context: Context, folderName: String): File? {
        val root = rootDirectory(context)
        root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.forEach { collectionDirectory ->
                val candidate = File(collectionDirectory, folderName)
                if (candidate.exists()) return candidate
            }
        return null
    }

    private fun safePathSegment(value: String, description: String): String {
        val normalized = value.trim()
            .replace(Regex("[\\s]+"), "_")
            .map { character ->
                if (character.isLetterOrDigit() || character == '_' || character == '-') character else '_'
            }
            .joinToString("")
            .replace(Regex("_+"), "_")
            .trim('_')

        require(normalized.isNotBlank() && normalized != "." && normalized != "..") {
            "$description no válido."
        }
        return normalized
    }

    private fun ensureDirectory(directory: File): File {
        if (directory.exists()) {
            check(directory.isDirectory) {
                "La ruta existe, pero no es una carpeta: ${directory.absolutePath}"
            }
            return directory
        }
        check(directory.mkdirs() || directory.isDirectory) {
            "No se pudo crear la carpeta: ${directory.absolutePath}"
        }
        return directory
    }
}
