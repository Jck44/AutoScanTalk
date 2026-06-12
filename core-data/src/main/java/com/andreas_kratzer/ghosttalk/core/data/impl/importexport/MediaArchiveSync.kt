package com.andreas_kratzer.ghosttalk.core.data.impl.importexport

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.impl.ZipArchiver
import com.andreas_kratzer.ghosttalk.core.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaArchiveSync @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val zipArchiver: ZipArchiver,
    private val logger: Logger
) {
    private val TAG = "MediaArchiveSync"

    fun getLastModified(dirName: String, extension: String): Long {
        val dir = File(context.filesDir, dirName)
        if (!dir.exists() || !dir.isDirectory) return 0L
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(extension) }
            ?.maxOfOrNull { it.lastModified() }
            ?: 0L
    }

    suspend fun exportToZip(
        dirName: String,
        zipPrefix: String,
        extension: String,
        outputStream: OutputStream,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, dirName)
        val files = if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.filter { it.isFile && it.name.endsWith(extension) } ?: emptyList()
        } else {
            emptyList()
        }

        val fileEntries = files.map { it to "$zipPrefix/${it.name}" }
        logger.d(TAG, "Exporting $dirName: ${files.size} files")
        zipArchiver.zip(outputStream, emptyMap(), fileEntries, onProgress)
    }

    suspend fun importFromZip(
        dirName: String,
        zipPrefix: String,
        inputStream: InputStream,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, dirName)
        if (!dir.exists()) dir.mkdirs()

        val prefixWithSlash = if (zipPrefix.endsWith("/")) zipPrefix else "$zipPrefix/"
        val targetDirs = mapOf(prefixWithSlash to dir)
        var count = 0

        val handler = object : ZipArchiver.UnzipHandler {
            override fun handleStringEntry(name: String, content: String) {}

            override fun handleFileEntry(name: String, time: Long, inputStream: InputStream, targetFile: File) {
                val fileName = name.substringAfter(prefixWithSlash)
                onProgress(0.5f, "Extracting: $fileName")
                FileOutputStream(targetFile).use { out ->
                    inputStream.copyTo(out)
                }
                if (time != -1L) {
                    targetFile.setLastModified(time)
                }
                count++
            }
        }

        zipArchiver.unzip(inputStream, targetDirs, handler, onProgress)
        logger.d(TAG, "Imported $count $dirName files")
        onProgress(1f, "$dirName import complete.")
    }
}
