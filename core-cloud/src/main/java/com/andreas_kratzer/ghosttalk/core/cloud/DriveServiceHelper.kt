package com.andreas_kratzer.ghosttalk.core.cloud

import android.util.Log
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

class DriveServiceHelper(private val driveService: Drive) {

    private val TAG = "DriveServiceHelper"

    /**
     * Creates a folder in Google Drive.
     */
    suspend fun createFolder(folderName: String): String? = withContext(Dispatchers.IO) {
        val metadata = File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
        }
        try {
            Log.d(TAG, "Creating folder: $folderName")
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
    suspend fun findFolder(folderName: String): String? = withContext(Dispatchers.IO) {
        val query = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
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
    suspend fun uploadFile(
        parentFolderId: String,
        file: java.io.File,
        mimeType: String,
        description: String? = null,
        onProgress: (Float) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        val metadata = File().apply {
            name = file.name
            parents = listOf(parentFolderId)
            this.description = description
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
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val metadata = File().apply {
            name = file.name
            this.description = description
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
     * Renames a file in Google Drive.
     */
    suspend fun renameFile(fileId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val metadata = File().apply {
            name = newName
        }
        try {
            Log.d(TAG, "Renaming file $fileId to $newName")
            driveService.files().update(fileId, metadata).execute()
            Log.d(TAG, "File renamed successfully: $fileId")
            true
        } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
            throw e
        } catch (e: GoogleJsonResponseException) {
            Log.e(TAG, "Failed to rename file. Status: ${e.statusCode}, Message: ${e.details.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename file due to unexpected exception: ${e.message}", e)
            false
        }
    }

    /**
     * Lists files in a folder.
     */
    suspend fun listFiles(folderId: String): List<File> = withContext(Dispatchers.IO) {
        val query = "'$folderId' in parents and trashed = false"
        try {
            Log.d(TAG, "Listing files in folder: $folderId with query: $query")
            val result: FileList = driveService.files().list().setQ(query).setFields("files(id, name, modifiedTime, description)").execute()
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
            val result: FileList = driveService.files().list().setQ(query).setFields("files(id, name, modifiedTime)").execute()
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
    suspend fun getFileMetadata(fileId: String): File? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching metadata for file: $fileId")
            driveService.files().get(fileId).setFields("id, name, modifiedTime, description").execute()
        } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file metadata for $fileId: ${e.message}", e)
            null
        }
    }
}
