package com.andreas_kratzer.ghosttalk.core.cloud.domain

import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetDriveFoldersUseCase @Inject constructor() {

    /**
     * Fetches a list of folders in the specified parent folder.
     * @param drive The Google Drive service instance.
     * @param parentFolderId The ID of the parent folder to list folders from. Defaults to "root".
     * @return A list of Drive files representing folders.
     */
    suspend fun execute(drive: Drive, parentFolderId: String = "root"): List<File> = withContext(Dispatchers.IO) {
        val query = "'$parentFolderId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        
        return@withContext try {
            val result = drive.files().list()
                .setQ(query)
                .setFields("files(id, name, modifiedTime, appProperties)")
                .setOrderBy("name")
                .execute()
            result.files ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
