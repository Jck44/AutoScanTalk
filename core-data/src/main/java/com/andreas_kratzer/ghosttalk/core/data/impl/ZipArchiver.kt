package com.andreas_kratzer.ghosttalk.core.data.impl

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZipArchiver @Inject constructor() {

    fun zip(
        outputStream: OutputStream,
        stringEntries: Map<String, String>,
        fileEntries: List<Pair<File, String>>,
        onProgress: ((Float, String) -> Unit)? = null
    ) {
        ZipOutputStream(outputStream).use { zip ->
            // Write string/metadata entries
            stringEntries.forEach { (entryPath, content) ->
                zip.putNextEntry(ZipEntry(entryPath))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            // Write file entries
            val totalFiles = fileEntries.size
            fileEntries.forEachIndexed { index, (file, entryPath) ->
                val progress = 0.1f + (index.toFloat() / totalFiles.coerceAtLeast(1)) * 0.9f
                onProgress?.invoke(progress, "Compressing: ${file.name}")
                zip.putNextEntry(ZipEntry(entryPath))
                file.inputStream().use { input ->
                    input.copyTo(zip)
                }
                zip.closeEntry()
            }
            onProgress?.invoke(1.0f, "Backup complete.")
        }
    }

    interface UnzipHandler {
        fun handleStringEntry(name: String, content: String)
        fun handleFileEntry(name: String, time: Long, inputStream: InputStream, targetFile: File)
    }

    fun unzip(
        inputStream: InputStream,
        targetDirs: Map<String, File>, // e.g. "tts_cache/" -> ttsCacheDir
        handler: UnzipHandler,
        onProgress: ((Float, String) -> Unit)? = null
    ) {
        val zipIn = ZipInputStream(inputStream)
        var entry = zipIn.nextEntry
        while (entry != null) {
            onProgress?.invoke(0.1f, "Extracting: ${entry.name}")

            val name = entry.name
            if (name == "backup.json" || name == "vocal_profiles.json" || name == "statistics.json") {
                val bytes = zipIn.readBytes()
                val content = String(bytes, Charsets.UTF_8)
                handler.handleStringEntry(name, content)
            } else {
                // Find matching target directory by prefix
                val matchingPrefix = targetDirs.keys.firstOrNull { name.startsWith(it) }
                if (matchingPrefix != null) {
                    val targetDir = targetDirs[matchingPrefix]!!
                    val fileName = name.substringAfter(matchingPrefix)
                    if (fileName.isNotEmpty()) {
                        val targetFile = File(targetDir, fileName)
                        if (!isSafeFile(targetDir, targetFile)) {
                            throw SecurityException("Ungültiger Pfad in Zip-Eintrag (Directory Traversal Versuch): $name")
                        }
                        val shouldExtract = !targetFile.exists() || (entry.time > targetFile.lastModified())
                        if (shouldExtract) {
                            handler.handleFileEntry(name, entry.time, zipIn, targetFile)
                        }
                    }
                }
            }
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }
        onProgress?.invoke(1.0f, "Import complete.")
    }

    fun isSafeFile(parentDir: File, file: File): Boolean {
        val canonicalParent = parentDir.canonicalPath
        val canonicalTarget = file.canonicalPath
        return canonicalTarget.startsWith(canonicalParent + File.separator)
    }
}
