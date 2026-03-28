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
            val result: FileList = driveService.files().list().setQ(query).setFields("files(id, name)").execute()
            val files = result.files ?: emptyList()
            Log.d(TAG, "Search returned ${files.size} entries for $folderName")
            val id = files.firstOrNull()?.id
            Log.d(TAG, "Search result for $folderName: ${id ?: "Not found"}")
            id
        } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
            throw e
        } catch (e: GoogleJsonResponseException) {
            Log.e(TAG, "Failed to find folder. Status: ${e.statusCode}, Message: ${e.details.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find folder due to unexpected exception: ${e.message}", e)
            null
        }
    }

    /**
     * Uploads a file to a specific folder.
     */
    suspend fun uploadFile(
        parentFolderId: String,
        file: java.io.File,
        mimeType: String,
        description: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val metadata = File().apply {
            name = file.name
            parents = listOf(parentFolderId)
            this.description = description
        }
        val mediaContent = FileContent(mimeType, file)
        try {
            Log.d(TAG, "Uploading file: ${file.name} to folder $parentFolderId")
            val googleFile = driveService.files().create(metadata, mediaContent).setFields("id").execute()
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
        description: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val metadata = File().apply {
            name = file.name
            this.description = description
        }
        val mediaContent = FileContent(mimeType, file)
        try {
            Log.d(TAG, "Updating file: $fileId (${file.name})")
            driveService.files().update(fileId, metadata, mediaContent).execute()
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
    suspend fun downloadFile(fileId: String, targetFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading file: $fileId to ${targetFile.absolutePath}")
            FileOutputStream(targetFile).use { outputStream ->
                driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            }
            Log.d(TAG, "File downloaded successfully: $fileId")
            true
        } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
            throw e
        } catch (e: GoogleJsonResponseException) {
            Log.e(TAG, "Failed to download file. Status: ${e.statusCode}, Message: ${e.details.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download file due to unexpected exception: ${e.message}", e)
            false
        }
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
