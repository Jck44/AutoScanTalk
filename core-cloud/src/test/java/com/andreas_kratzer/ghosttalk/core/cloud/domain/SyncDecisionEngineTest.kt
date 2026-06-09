package com.andreas_kratzer.ghosttalk.core.cloud.domain

import com.andreas_kratzer.ghosttalk.core.cloud.SyncAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SyncDecisionEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val engine = SyncDecisionEngine()

    private fun createTempFileWithContent(content: String): File {
        val file = tempFolder.newFile()
        file.writeText(content)
        return file
    }

    @Test
    fun `TWO_WAY mode and local sequence newer returns UPLOAD`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 5L,
            localLastModified = 1000L,
            remoteFileMd5 = "different_md5",
            remoteSeq = 3L,
            remoteLastModified = 500L,
            resolvedBookMode = SyncMode.TWO_WAY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.UPLOAD, action)
    }

    @Test
    fun `TWO_WAY mode and remote sequence newer returns DOWNLOAD`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 2L,
            localLastModified = 1000L,
            remoteFileMd5 = "different_md5",
            remoteSeq = 5L,
            remoteLastModified = 2000L,
            resolvedBookMode = SyncMode.TWO_WAY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.DOWNLOAD, action)
    }

    @Test
    fun `TWO_WAY mode with conflict files returns MERGE_CONFLICT`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 2L,
            localLastModified = 1000L,
            remoteFileMd5 = "different_md5",
            remoteSeq = 2L,
            remoteLastModified = 1000L,
            resolvedBookMode = SyncMode.TWO_WAY,
            remoteConflictFilesNotEmpty = true
        )
        assertEquals(SyncAction.MERGE_CONFLICT, action)
    }

    @Test
    fun `identical MD5 check returns NO_OP even if sequences differ`() {
        val file = createTempFileWithContent("same_content")
        val md5 = com.andreas_kratzer.ghosttalk.core.cloud.CloudSyncOptimizer().calculateMD5(file)
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 2L,
            localLastModified = 1000L,
            remoteFileMd5 = md5,
            remoteSeq = 5L,
            remoteLastModified = 2000L,
            resolvedBookMode = SyncMode.TWO_WAY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.NO_OP, action)
    }

    @Test
    fun `BACKUP_ONLY mode skips download when remote is newer`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 2L,
            localLastModified = 1000L,
            remoteFileMd5 = "different_md5",
            remoteSeq = 5L,
            remoteLastModified = 2000L,
            resolvedBookMode = SyncMode.BACKUP_ONLY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.NO_OP, action)
    }

    @Test
    fun `BACKUP_ONLY mode allows upload when local is newer`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 5L,
            localLastModified = 2000L,
            remoteFileMd5 = "different_md5",
            remoteSeq = 2L,
            remoteLastModified = 1000L,
            resolvedBookMode = SyncMode.BACKUP_ONLY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.UPLOAD, action)
    }

    @Test
    fun `RESTORE_ONLY mode allows download when remote is newer`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 2L,
            localLastModified = 1000L,
            remoteFileMd5 = "different_md5",
            remoteSeq = 5L,
            remoteLastModified = 2000L,
            resolvedBookMode = SyncMode.RESTORE_ONLY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.DOWNLOAD, action)
    }

    @Test
    fun `RESTORE_ONLY mode downloads when local is newer but not in sync`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 5L,
            localLastModified = 2000L,
            remoteFileMd5 = "different_md5",
            remoteSeq = 2L,
            remoteLastModified = 1000L,
            resolvedBookMode = SyncMode.RESTORE_ONLY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.DOWNLOAD, action)
    }

    @Test
    fun `legacy mode with different content and local newer timestamp uploads in BACKUP_ONLY`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 0L,
            localLastModified = 5000L,
            remoteFileMd5 = "different_md5",
            remoteSeq = 0L,
            remoteLastModified = 1000L,
            resolvedBookMode = SyncMode.BACKUP_ONLY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.UPLOAD, action)
    }
}
