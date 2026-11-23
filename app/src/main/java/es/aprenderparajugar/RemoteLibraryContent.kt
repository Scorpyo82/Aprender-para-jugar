package es.aprenderparajugar

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Contenido del catálogo remoto de bibliotecas; mantiene fuera de AprenderApp sus lambdas de consulta y descarga. */
internal fun LazyListScope.remoteLibraryItems(
    context: Context,
    remoteLibraryScope: CoroutineScope,
    remoteLibraryPackagesState: MutableState<List<RemoteLibraryPackage>>,
    remoteLibraryLoadingState: MutableState<Boolean>,
    remoteLibraryMessageState: MutableState<String>,
    remoteLibraryDownloadingNameState: MutableState<String?>,
    remoteLibraryDownloadedBytesState: MutableState<Long>,
    remoteLibraryTotalBytesState: MutableState<Long>,
    remoteLibraryPendingImportState: MutableState<RemoteLibraryPackage?>
) {
    var remoteLibraryPackages by remoteLibraryPackagesState
    var remoteLibraryLoading by remoteLibraryLoadingState
    var remoteLibraryMessage by remoteLibraryMessageState
    var remoteLibraryDownloadingName by remoteLibraryDownloadingNameState
    var remoteLibraryDownloadedBytes by remoteLibraryDownloadedBytesState
    var remoteLibraryTotalBytes by remoteLibraryTotalBytesState
    var remoteLibraryPendingImport by remoteLibraryPendingImportState

                        item {
                            Text("Bibliotecas disponibles", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Paquetes ZIP publicados en GitHub. Puedes descargarlos e importar solo el contenido que necesites.", fontSize = 14.sp, color = Color(0xFF667085))
                        }
                        item {
                            OutlinedButton(
                                onClick = {
                                    remoteLibraryScope.launch {
                                        remoteLibraryLoading = true
                                        remoteLibraryMessage = ""
                                        try {
                                            remoteLibraryPackages = withContext(Dispatchers.IO) { RemoteLibraryRepository.listPackages() }
                                            if (remoteLibraryPackages.isEmpty()) remoteLibraryMessage = "Todavía no hay paquetes ZIP publicados."
                                        } catch (error: Exception) {
                                            remoteLibraryMessage = "No se ha podido consultar GitHub: ${error.message ?: "error de conexión"}"
                                        } finally {
                                            remoteLibraryLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !remoteLibraryLoading
                            ) { Text(if (remoteLibraryLoading) "Consultando GitHub…" else "↻ Actualizar lista") }
                        }
                        if (remoteLibraryMessage.isNotBlank()) {
                            item { Text(remoteLibraryMessage, fontSize = 13.sp, color = if (remoteLibraryMessage.startsWith("No se ha podido")) Color(0xFFB3261E) else Color(0xFF667085)) }
                        }
                        if (remoteLibraryPackages.isEmpty() && !remoteLibraryLoading && remoteLibraryMessage.isBlank()) {
                            item { Text("Pulsa «Actualizar lista» para consultar los paquetes disponibles.", fontSize = 14.sp, color = Color(0xFF667085)) }
                        }
                        remoteLibraryPackages.forEach { remotePackage ->
                            val savedSha = RemoteLibraryRepository.downloadedSha(context, remotePackage.name)
                            val installedSha = RemoteLibraryRepository.installedSha(context, remotePackage.name)
                            val cachedFile = RemoteLibraryRepository.cachedFile(context, remotePackage.name)
                            val isInstalled = installedSha == remotePackage.sha
                            val isUpdate = (installedSha != null && installedSha != remotePackage.sha) ||
                                (savedSha != null && savedSha != remotePackage.sha)
                            val isDownloaded = !isInstalled && savedSha == remotePackage.sha && cachedFile.isFile && cachedFile.length() > 0L
                            val isDownloading = remoteLibraryDownloadingName == remotePackage.name
                            item {
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(remotePackage.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        Text(RemoteLibraryRepository.formatSize(remotePackage.size), fontSize = 12.sp, color = Color(0xFF667085))
                                        Text(
                                            when {
                                                isDownloading -> "Descargando biblioteca…"
                                                isInstalled -> "Instalada"
                                                isUpdate -> "Actualización disponible"
                                                isDownloaded -> "Descargado, pendiente de importar"
                                                else -> "Disponible"
                                            },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when {
                                                isDownloading -> Color(0xFF3567A8)
                                                isInstalled -> Color(0xFF24733B)
                                                isUpdate -> Color(0xFFB26A00)
                                                isDownloaded -> Color(0xFF24733B)
                                                else -> Color(0xFF667085)
                                            }
                                        )
                                        if (isDownloading) {
                                            val total = remoteLibraryTotalBytes
                                            val downloaded = remoteLibraryDownloadedBytes
                                            if (total > 0L) {
                                                LinearProgressIndicator(
                                                    progress = { (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            } else {
                                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                            }
                                            val percentage = if (total > 0L) (downloaded * 100L / total).coerceIn(0L, 100L) else null
                                            Text(
                                                "${percentage?.let { "$it%" } ?: "Calculando…"} · ${RemoteLibraryRepository.formatSize(downloaded)} / ${RemoteLibraryRepository.formatSize(if (total > 0L) total else remotePackage.size)}",
                                                fontSize = 12.sp,
                                                color = Color(0xFF667085)
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                if (isInstalled) {
                                                    // The current release is already installed; no ZIP is needed.
                                                } else if (isDownloaded) {
                                                    remoteLibraryPendingImport = remotePackage
                                                } else {
                                                    remoteLibraryScope.launch {
                                                        remoteLibraryDownloadingName = remotePackage.name
                                                        remoteLibraryDownloadedBytes = 0L
                                                        remoteLibraryTotalBytes = remotePackage.size
                                                        remoteLibraryMessage = ""
                                                        try {
                                                            val file = withContext(Dispatchers.IO) {
                                                                var lastProgressReported = 0L
                                                                RemoteLibraryRepository.download(context, remotePackage) { downloaded, total ->
                                                                    if (downloaded - lastProgressReported >= 512L * 1024L || (total > 0L && downloaded == total)) {
                                                                        lastProgressReported = downloaded
                                                                        remoteLibraryScope.launch {
                                                                            remoteLibraryDownloadedBytes = downloaded
                                                                            if (total > 0L) remoteLibraryTotalBytes = total
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            RemoteLibraryRepository.saveDownloaded(context, remotePackage.name, remotePackage.sha)
                                                            remoteLibraryDownloadedBytes = file.length()
                                                            remoteLibraryTotalBytes = remotePackage.size
                                                            remoteLibraryPendingImport = remotePackage
                                                        } catch (error: Exception) {
                                                            remoteLibraryMessage = "No se ha podido descargar el paquete: ${error.message ?: "error de conexión"}"
                                                        } finally {
                                                            remoteLibraryDownloadingName = null
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = remoteLibraryDownloadingName == null && !remoteLibraryLoading && !isInstalled,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(when {
                                                isDownloading -> "Descargando…"
                                                isInstalled -> "Instalada"
                                                isDownloaded -> "Importar ahora"
                                                isUpdate -> "⬇️ Descargar actualización"
                                                else -> "⬇️ Descargar"
                                            })
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Text("Las descargas se realizan en segundo plano. Puedes seguir consultando el catálogo; solo se permite una descarga simultánea.", fontSize = 12.sp, color = Color(0xFF667085))
                        }

}
