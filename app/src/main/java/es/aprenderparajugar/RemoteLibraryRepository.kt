package es.aprenderparajugar

import android.content.Context
import org.json.JSONArray
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
    private const val API_URL = "https://api.github.com/repos/Scorpyo82/Aprender-para-jugar/contents/bibliotecas?ref=main"
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
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val entries = JSONArray(body)
            return (0 until entries.length()).mapNotNull { index ->
                val item = entries.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name")
                if (item.optString("type") != "file" || !name.endsWith(".zip", true)) return@mapNotNull null
                val sha = item.optString("sha")
                val downloadUrl = item.optString("download_url")
                if (sha.isBlank() || downloadUrl.isBlank()) return@mapNotNull null
                RemoteLibraryPackage(name, sha, item.optLong("size"), downloadUrl)
            }.sortedBy { it.name.lowercase() }
        } finally {
            connection.disconnect()
        }
    }

    fun download(context: Context, item: RemoteLibraryPackage): File {
        val safeName = item.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val directory = File(context.cacheDir, "remote_libraries").apply { check(mkdirs() || isDirectory) }
        val temporary = File(directory, "$safeName.download")
        val destination = File(directory, safeName)
        val connection = (URL(item.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "Aprender-para-jugar-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("GitHub respondió HTTP $code")
            connection.inputStream.buffered().use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            if (!temporary.isFile || temporary.length() <= 0L) throw IllegalStateException("El ZIP descargado está vacío.")
            if (item.size > 0L && temporary.length() != item.size) throw IllegalStateException("La descarga está incompleta.")
            if (destination.exists()) destination.delete()
            check(temporary.renameTo(destination)) { "No se pudo guardar el ZIP descargado." }
            return destination
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    fun downloadedSha(context: Context, name: String): String? =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(name, null)

    fun saveDownloaded(context: Context, name: String, sha: String) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(name, sha).apply()
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(java.util.Locale.getDefault(), "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
