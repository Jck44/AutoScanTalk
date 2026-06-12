package com.andreas_kratzer.ghosttalk.ui.main

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class ZipType {
    BOOK,
    TTS_CACHE,
    INVALID
}

sealed interface ImportResult {
    data class BookImported(val bookId: String) : ImportResult
    object TtsCacheImported : ImportResult
    data class Error(val message: String) : ImportResult
    data class ReadError(val message: String) : ImportResult
    object InvalidZip : ImportResult
}

@Singleton
class SharedZipImportHandler @Inject constructor(
    private val importExportManager: PageImportExportProvider
) {
    fun processSharedZip(
        uri: Uri,
        contentResolver: ContentResolver,
        scope: CoroutineScope,
        onResult: (ImportResult) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val zipType = contentResolver.openInputStream(uri)?.use { detectZipType(it) } ?: ZipType.INVALID
                
                when (zipType) {
                    ZipType.BOOK -> importBookZip(uri, contentResolver, onResult)
                    ZipType.TTS_CACHE -> importTtsCacheZip(uri, contentResolver, onResult)
                    ZipType.INVALID -> {
                        withContext(Dispatchers.Main) {
                            onResult(ImportResult.InvalidZip)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SharedZipImportHandler", "Error parsing shared ZIP", e)
                withContext(Dispatchers.Main) {
                    onResult(ImportResult.ReadError(e.message ?: "Unknown error"))
                }
            }
        }
    }

    internal fun detectZipType(inputStream: InputStream): ZipType {
        var isBookZip = false
        var isTtsCacheZip = false
        try {
            ZipInputStream(inputStream).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "backup.json") {
                        isBookZip = true
                        break
                    } else if (entry.name.startsWith("tts_cache/")) {
                        isTtsCacheZip = true
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e("SharedZipImportHandler", "Failed to detect zip type", e)
            return ZipType.INVALID
        }
        return when {
            isBookZip -> ZipType.BOOK
            isTtsCacheZip -> ZipType.TTS_CACHE
            else -> ZipType.INVALID
        }
    }

    private suspend fun importBookZip(
        uri: Uri,
        contentResolver: ContentResolver,
        onResult: (ImportResult) -> Unit
    ) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val result = importExportManager.importCloudBackupFromZip(inputStream, null) { progress, status ->
                    Log.d("SharedZipImportHandler", "Import Book ZIP: progress = $progress, status = $status")
                }
                
                withContext(Dispatchers.Main) {
                    result.onSuccess { bookId ->
                        onResult(ImportResult.BookImported(bookId))
                    }.onFailure { error ->
                        onResult(ImportResult.Error(error.message ?: "Unknown import error"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SharedZipImportHandler", "Error importing book ZIP", e)
            withContext(Dispatchers.Main) {
                onResult(ImportResult.ReadError(e.message ?: "Unknown error"))
            }
        }
    }

    private suspend fun importTtsCacheZip(
        uri: Uri,
        contentResolver: ContentResolver,
        onResult: (ImportResult) -> Unit
    ) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                importExportManager.importTtsCacheFromZip(inputStream) { progress, status ->
                    Log.d("SharedZipImportHandler", "Import TTS Cache ZIP: progress = $progress, status = $status")
                }
                
                withContext(Dispatchers.Main) {
                    onResult(ImportResult.TtsCacheImported)
                }
            }
        } catch (e: Exception) {
            Log.e("SharedZipImportHandler", "Error importing TTS Cache ZIP", e)
            withContext(Dispatchers.Main) {
                onResult(ImportResult.ReadError(e.message ?: "Unknown error"))
            }
        }
    }
}
