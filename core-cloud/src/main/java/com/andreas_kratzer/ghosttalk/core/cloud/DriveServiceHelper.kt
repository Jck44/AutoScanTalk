package com.andreas_kratzer.ghosttalk.core.cloud

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import com.andreas_kratzer.ghosttalk.core.model.CloudAuthType

class DriveServiceHelper(private val driveService: Drive) {

    private val TAG = "DriveServiceHelper"

    /**
     * Creates a folder in Google Drive.
     */
    suspend fun createFolder(folderName: String, parentFolderId: String? = null): String? = withContext(Dispatchers.IO) {
        val metadata = File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
            if (parentFolderId != null) {
                parents = listOf(parentFolderId)
            }
        }
        try {
            Log.d(TAG, "Creating folder: $folderName" + (if (parentFolderId != null) " inside parent $parentFolderId" else ""))
            val googleFile = driveService.files().create(metadata).setFields("id").execute()
            Log.d(TAG, "Folder created successfully: ${googleFile.id}")
            googleFile.id
        } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
            throw e
        } catch (e: GoogleJsonResponseException) {
            Log.e(TAG, "Failed to create folder. Status: ${e.statusCode}, Message: ${e.details.message}", e)
            if (e.statusCode == 403) {
                Log.e(TAG, "403 Forbidden: Check if Drive API is enabled in Google Cloud Console and if the user has given consent.")
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create folder due to unexpected exception: ${e.message}", e)
            null
        }
    }

    /**
     * Finds a folder by name.
     */
    suspend fun findFolder(folderName: String, parentFolderId: String? = null): String? = withContext(Dispatchers.IO) {
        val parentQuery = if (parentFolderId != null) "'$parentFolderId' in parents and " else ""
        val query = "${parentQuery}name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        try {
            Log.d(TAG, "Searching for folder: $folderName with query: $query")
            val result: FileList = driveService.files().list()
                .setQ(query)
                .setOrderBy("modifiedTime desc")
                .setFields("files(id, name, modifiedTime)")
                .execute()
            val files = result.files ?: emptyList()
            Log.d(TAG, "Search returned ${files.size} entries for $folderName")
            if (files.size > 1) {
                Log.w(TAG, "Multiple folders with name '$folderName' found. Using the most recently modified one.")
            }
            val id = files.firstOrNull()?.id
            Log.d(TAG, "Search result for $folderName: ${id ?: "Not found"}")
            id
        } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
            throw e
        } catch (e: GoogleJsonResponseException) {
            Log.e(TAG, "Failed to find folder. Status: ${e.statusCode}, Message: ${e.details?.message ?: e.message}", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find folder due to unexpected exception: ${e.message}", e)
            throw e
        }
    }

    /**
     * Uploads a file to a specific folder.
     */
    /**
     * Uploads a file to a specific folder.
     */
    suspend fun uploadFile(
        parentFolderId: String,
        file: java.io.File,
        mimeType: String,
        description: String? = null,
        properties: Map<String, String>? = null,
        onProgress: (Float) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        val metadata = File().apply {
            name = file.name
            parents = listOf(parentFolderId)
            this.description = description
            this.properties = properties
        }
        val mediaContent = FileContent(mimeType, file)
        try {
            Log.d(TAG, "Uploading file: ${file.name} to folder $parentFolderId")
            val request = driveService.files().create(metadata, mediaContent)
            request.mediaHttpUploader.apply {
                isDirectUploadEnabled = false
                setProgressListener { uploader ->
                    if (uploader.uploadState == com.google.api.client.googleapis.media.MediaHttpUploader.UploadState.MEDIA_IN_PROGRESS) {
                        onProgress(uploader.progress.toFloat())
                    } else if (uploader.uploadState == com.google.api.client.googleapis.media.MediaHttpUploader.UploadState.MEDIA_COMPLETE) {
                        onProgress(1.0f)
                    }
                }
            }
            val googleFile = request.setFields("id").execute()
            Log.d(TAG, "File uploaded successfully: ${googleFile.id}")
            googleFile.id
        } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
            throw e
        } catch (e: GoogleJsonResponseException) {
            Log.e(TAG, "Failed to upload file. Status: ${e.statusCode}, Message: ${e.details.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload file due to unexpected exception: ${e.message}", e)
            null
        }
    }

    /**
     * Updates an existing file.
     */
    suspend fun updateFile(
        fileId: String,
        file: java.io.File,
        mimeType: String,
        description: String? = null,
        properties: Map<String, String>? = null,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val metadata = File().apply {
            name = file.name
            this.description = description
            this.properties = properties
        }
        val mediaContent = FileContent(mimeType, file)
        try {
            Log.d(TAG, "Updating file: $fileId (${file.name})")
            val request = driveService.files().update(fileId, metadata, mediaContent)
            request.mediaHttpUploader.apply {
                isDirectUploadEnabled = false
                setProgressListener { uploader ->
                    if (uploader.uploadState == com.google.api.client.googleapis.media.MediaHttpUploader.UploadState.MEDIA_IN_PROGRESS) {
                        onProgress(uploader.progress.toFloat())
                    } else if (uploader.uploadState == com.google.api.client.googleapis.media.MediaHttpUploader.UploadState.MEDIA_COMPLETE) {
                        onProgress(1.0f)
                    }
                }
            }
            request.execute()
            Log.d(TAG, "File updated successfully: $fileId")
            true
        } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
            throw e
        } catch (e: GoogleJsonResponseException) {
            Log.e(TAG, "Failed to update file. Status: ${e.statusCode}, Message: ${e.details.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update file due to unexpected exception: ${e.message}", e)
            false
        }
    }

    /**
     * Updates ONLY properties of a file without uploading content.
     */
    suspend fun updateProperties(
        fileId: String,
        properties: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        val metadata = File().apply {
            this.properties = properties
        }
        try {
            Log.d(TAG, "Updating properties for file: $fileId")
            driveService.files().update(fileId, metadata).execute()
            Log.d(TAG, "Properties updated successfully for file: $fileId")
            true
        } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
            throw e
        } catch (e: GoogleJsonResponseException) {
            Log.e(TAG, "Failed to update properties. Status: ${e.statusCode}, Message: ${e.details.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update properties due to unexpected exception: ${e.message}", e)
            false
        }
    }


    /**
     * Downloads a file from Drive.
     */
    suspend fun downloadFile(
        fileId: String, 
        targetFile: java.io.File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val maxRetries = 3
        var attempt = 0
        var success = false
        var delayMs = 1000L

        while (attempt < maxRetries && !success) {
            attempt++
            try {
                Log.d(TAG, "Downloading file: $fileId to ${targetFile.absolutePath} (Attempt $attempt of $maxRetries)")
                val request = driveService.files().get(fileId)
                request.mediaHttpDownloader.setProgressListener { downloader ->
                    if (downloader.downloadState == com.google.api.client.googleapis.media.MediaHttpDownloader.DownloadState.MEDIA_IN_PROGRESS) {
                        onProgress(downloader.progress.toFloat())
                    } else if (downloader.downloadState == com.google.api.client.googleapis.media.MediaHttpDownloader.DownloadState.MEDIA_COMPLETE) {
                        onProgress(1.0f)
                    }
                }
                FileOutputStream(targetFile).use { outputStream ->
                    request.executeMediaAndDownloadTo(outputStream)
                }
                Log.d(TAG, "File downloaded successfully: $fileId on attempt $attempt")
                success = true
            } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                // Do not retry authorization errors, rethrow them
                throw e
            } catch (e: GoogleJsonResponseException) {
                Log.e(TAG, "Failed to download file on attempt $attempt. Status: ${e.statusCode}, Message: ${e.details?.message ?: e.message}", e)
                // Do not retry client errors (4xx), except for timeout (408) and rate limits (429)
                if (e.statusCode in 400..499 && e.statusCode != 408 && e.statusCode != 429) {
                    break
                }
                if (attempt < maxRetries) {
                    Log.w(TAG, "Retrying download in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download file on attempt $attempt due to unexpected exception: ${e.message}", e)
                if (attempt < maxRetries) {
                    Log.w(TAG, "Retrying download in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2
                }
            }
        }

        if (!success) {
            if (targetFile.exists()) {
                val deleted = targetFile.delete()
                Log.d(TAG, "Cleanup: Deleted partially downloaded or stale file: ${targetFile.absolutePath} ($deleted)")
            }
        }
        success
    }



    /**
     * Lists files in a folder.
     */
    suspend fun listFiles(folderId: String): List<File> = withContext(Dispatchers.IO) {
        val query = "'$folderId' in parents and trashed = false"
        try {
            Log.d(TAG, "Listing files in folder: $folderId with query: $query")
            val result: FileList = driveService.files().list().setQ(query).setFields("files(id, name, modifiedTime, description, properties, md5Checksum, version)").execute()
            val files = result.files ?: emptyList()
            Log.d(TAG, "Found ${files.size} files in folder $folderId")
            files
        } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
            throw e
        } catch (e: GoogleJsonResponseException) {
            Log.e(TAG, "Failed to list files. Status: ${e.statusCode}, Message: ${e.details.message}", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list files due to unexpected exception: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Searches for files by name containing the query string.
     */
    suspend fun searchFiles(queryText: String): List<File> = withContext(Dispatchers.IO) {
        val query = "name contains '$queryText' and trashed = false"
        try {
            Log.d(TAG, "Searching for files with query: $query")
            val result: FileList = driveService.files().list().setQ(query).setFields("files(id, name, modifiedTime, properties, md5Checksum, version)").execute()
            val files = result.files ?: emptyList()
            Log.d(TAG, "Found ${files.size} files matching '$queryText'")
            files
        } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
            throw e
        } catch (e: GoogleJsonResponseException) {
            Log.e(TAG, "Failed to search files. Status: ${e.statusCode}, Message: ${e.details.message}", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search files due to unexpected exception: ${e.message}", e)
            emptyList()
        }
    }
    /**
     * Fetches metadata for a specific file.
     */
    /**
     * Fetches metadata for a specific file.
     */
    suspend fun getFileMetadata(fileId: String): File? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching metadata for file: $fileId")
            driveService.files().get(fileId).setFields("id, name, modifiedTime, description, properties, md5Checksum, version").execute()
        } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file metadata for $fileId: ${e.message}", e)
            null
        }
    }

    /**
     * Uploads file content with optimistic locking based on expected file version.
     */
    suspend fun uploadWithOptimisticLock(
        fileId: String,
        localFile: java.io.File,
        mimeType: String,
        expectedVersion: Long,
        properties: Map<String, String>? = null,
        description: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val logTag = "DriveLockHandshake"
        try {
            Log.d(logTag, "[1/3] Starte API-Abfrage für File-ID: $fileId. Erwartete Version (Client-Stand): $expectedVersion")
            
            // Erzwinge frische Daten vom Server (Cache-Bypass)
            val getRequest = driveService.files().get(fileId).setFields("version")
            getRequest.requestHeaders.cacheControl = "no-cache"
            val meta = getRequest.execute()
            
            val currentServerVersion = meta.version ?: 0L
            Log.d(logTag, "[2/3] Google-Drive Antwort erhalten. Aktuelle Server-Version: $currentServerVersion (Erwartet vom Client: $expectedVersion)")
            
            if (expectedVersion != 0L && currentServerVersion != expectedVersion) {
                Log.w(logTag, "[MUTEX-CONFLICT] Sperre verletzt! Abbruch des Uploads. Server hat sich seit Sync-Beginn verändert. (Server: $currentServerVersion, Client-Erwartung: $expectedVersion)")
                return@withContext false
            }
            
            Log.d(logTag, "[3/3] Sperren-Check erfolgreich (oder Legacy-Bypass). Starte physischen Datei-Upload auf Google Drive...")
            val content = FileContent(mimeType, localFile)
            val metadata = File().apply {
                this.properties = properties
                this.description = description
            }
            val updateResponse = driveService.files().update(fileId, metadata, content).execute()
            
            Log.i(logTag, "[SUCCESS] Datei erfolgreich auf Google Drive überschrieben. Neue Server-Revision ist: ${updateResponse.version}")
            true
        } catch (e: Exception) {
            Log.e(logTag, "[ERROR] Ausnahme während des Optimistic-Lock Uploads", e)
            false
        }
    }

    /**
     * Deletes a file in Google Drive by its file ID.
     */
    suspend fun deleteFile(fileId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Deleting file: $fileId")
            driveService.files().delete(fileId).execute()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file $fileId", e)
            false
        }
    }

    /**
     * Deletes old conflict files (merged_*) in Drive, keeping only the 2 most recent.
     */
    suspend fun cleanOldConflictFiles(folderId: String) = withContext(Dispatchers.IO) {
        try {
            val result = driveService.files().list()
                .setQ("'$folderId' in parents and name contains 'merged_' and trashed = false")
                .setFields("files(id, name, createdTime)")
                .execute()

            val files = result.files ?: return@withContext
            if (files.size > 2) {
                val sortedFiles = files.sortedBy { it.createdTime?.value ?: 0L }
                for (i in 0 until sortedFiles.size - 2) {
                    try {
                        driveService.files().delete(sortedFiles[i].id).execute()
                        Log.i(TAG, "Deleted old conflict file: ${sortedFiles[i].name}")
                    } catch (ex: Exception) {
                        Log.e(TAG, "Failed to delete old conflict file ${sortedFiles[i].name}", ex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cloud conflict files cleanup", e)
        }
    }

    companion object {

        suspend fun buildDriveClient(
            authType: CloudAuthType,
            googleAuthManager: GoogleAuthManager,
            googleWebAuthManager: GoogleWebAuthManager
        ): Drive? {
            return when (authType) {
                CloudAuthType.SYSTEM -> {
                    val credential = googleAuthManager.getGoogleCredential() ?: return null
                    Drive.Builder(
                        com.google.api.client.http.javanet.NetHttpTransport(),
                        com.google.api.client.json.gson.GsonFactory.getDefaultInstance()
                    ) { request ->
                        credential.initialize(request)
                        request.connectTimeout = 3 * 60 * 1000 // 3 minutes
                        request.readTimeout = 3 * 60 * 1000    // 3 minutes
                    }.setApplicationName("GhosTTalk").build()
                }
                CloudAuthType.WEB_FLOW -> {
                    val token = googleWebAuthManager.getOrRefreshToken() ?: return null
                    val initializer = com.google.api.client.http.HttpRequestInitializer { req ->
                        req.headers.authorization = "Bearer $token"
                        req.connectTimeout = 3 * 60 * 1000 // 3 minutes
                        req.readTimeout = 3 * 60 * 1000    // 3 minutes
                    }
                    Drive.Builder(
                        com.google.api.client.http.javanet.NetHttpTransport(),
                        com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
                        initializer
                    ).setApplicationName("GhosTTalk").build()
                }
            }
        }
    }
}
