package es.aprenderparajugar

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class RemoteLibraryPackage(
    val name: String,
    val sha: String,
    val size: Long,
    val downloadUrl: String
)

internal object RemoteLibraryRepository {
    private const val API_URL = "https://api.github.com/repos/Scorpyo82/Aprender-para-jugar/releases/latest"
    private const val PREFERENCES = "remote_library_downloads"

    fun listPackages(): List<RemoteLibraryPackage> {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Aprender-para-jugar-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("GitHub respondió HTTP $code")
            val release = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val assets = release.optJSONArray("assets") ?: return emptyList()
            return (0 until assets.length()).mapNotNull { index ->
                val asset = assets.optJSONObject(index) ?: return@mapNotNull null
                val name = asset.optString("name")
                if (!name.endsWith(".zip", true)) return@mapNotNull null
                val downloadUrl = asset.optString("browser_download_url")
                if (downloadUrl.isBlank()) return@mapNotNull null

                // GitHub Releases exposes the asset's SHA-256 digest. Keep a fallback
                // fingerprint for older assets for which the digest is not available.
                val digest = asset.optString("digest").takeIf { it.startsWith("sha256:") }
                    ?: "asset:${asset.optLong("id")}:${asset.optString("updated_at")}:${asset.optLong("size")}"
                RemoteLibraryPackage(name, digest, asset.optLong("size"), downloadUrl)
            }.sortedBy { it.name.lowercase() }
        } finally {
            connection.disconnect()
        }
    }

    fun download(
        context: Context,
        item: RemoteLibraryPackage,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File {
        val safeName = item.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val directory = File(context.cacheDir, "remote_libraries").apply { check(mkdirs() || isDirectory) }
        val temporary = File(directory, "$safeName.download")
        val destination = File(directory, safeName)
        val previous = File(directory, "$safeName.previous")
        val connection = (URL(item.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Aprender-para-jugar-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("GitHub respondió HTTP $code")
            val total = item.size.takeIf { it > 0L } ?: connection.contentLengthLong.coerceAtLeast(0L)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            connection.inputStream.buffered().use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                }
            }
            if (!temporary.isFile || temporary.length() <= 0L) throw IllegalStateException("El ZIP descargado está vacío.")
            if (item.size > 0L && temporary.length() != item.size) throw IllegalStateException("La descarga está incompleta.")
            if (item.sha.startsWith("sha256:")) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual.equals(item.sha.removePrefix("sha256:"), ignoreCase = true)) {
                    "La comprobación SHA-256 del ZIP no coincide."
                }
            }
            previous.delete()
            if (destination.exists()) check(destination.renameTo(previous)) { "No se pudo conservar la descarga anterior." }
            if (!temporary.renameTo(destination)) {
                if (previous.exists()) previous.renameTo(destination)
                throw IllegalStateException("No se pudo guardar el ZIP descargado.")
            }
            previous.delete()
            return destination
        } catch (error: Exception) {
            if (!destination.exists() && previous.exists()) previous.renameTo(destination)
            throw error
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    fun cachedFile(context: Context, name: String): File {
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(context.cacheDir, "remote_libraries"), safeName)
    }

    fun deleteCached(context: Context, name: String) {
        cachedFile(context, name).delete()
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().remove(name).apply()
    }

    fun downloadedSha(context: Context, name: String): String? =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(name, null)

    // Installed packages are tracked separately from cached ZIP downloads. The ZIP can
    // be deleted after a successful import without losing the installed state.
    fun installedSha(context: Context, name: String): String? =
        context.getSharedPreferences("remote_library_installed", Context.MODE_PRIVATE).getString(name, null)

    fun saveInstalled(context: Context, name: String, sha: String) {
        context.getSharedPreferences("remote_library_installed", Context.MODE_PRIVATE)
            .edit().putString(name, sha).apply()
    }

    fun saveDownloaded(context: Context, name: String, sha: String) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(name, sha).apply()
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(java.util.Locale.getDefault(), "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
