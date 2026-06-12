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
    fun `both sides changed since anchor returns MERGE_CONFLICT even if local seq is higher`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 8L,
            localLastModified = 1000L,
            localStructMd5 = "L",
            remoteFileMd5 = "different_md5",
            remoteSeq = 6L,
            remoteStructMd5 = "R",
            remoteLastModified = 500L,
            anchorMd5 = "A",
            resolvedBookMode = SyncMode.TWO_WAY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.MERGE_CONFLICT, action)
    }

    @Test
    fun `both sides changed since anchor returns MERGE_CONFLICT even if remote seq is higher`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 6L,
            localLastModified = 1000L,
            localStructMd5 = "L",
            remoteFileMd5 = "different_md5",
            remoteSeq = 8L,
            remoteStructMd5 = "R",
            remoteLastModified = 2000L,
            anchorMd5 = "A",
            resolvedBookMode = SyncMode.TWO_WAY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.MERGE_CONFLICT, action)
    }

    @Test
    fun `only local changed since anchor returns UPLOAD`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 5L,
            localLastModified = 1000L,
            localStructMd5 = "L",
            remoteFileMd5 = "different_md5",
            remoteSeq = 3L,
            remoteStructMd5 = "A",
            remoteLastModified = 500L,
            anchorMd5 = "A",
            resolvedBookMode = SyncMode.TWO_WAY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.UPLOAD, action)
    }

    @Test
    fun `only remote changed since anchor returns DOWNLOAD`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 2L,
            localLastModified = 1000L,
            localStructMd5 = "A",
            remoteFileMd5 = "different_md5",
            remoteSeq = 5L,
            remoteStructMd5 = "R",
            remoteLastModified = 2000L,
            anchorMd5 = "A",
            resolvedBookMode = SyncMode.TWO_WAY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.DOWNLOAD, action)
    }

    @Test
    fun `nothing changed since anchor returns NO_OP`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 2L,
            localLastModified = 1000L,
            localStructMd5 = "A",
            remoteFileMd5 = "different_md5", // differs e.g. due to whitespace / minor format differences
            remoteSeq = 2L,
            remoteStructMd5 = "A",
            remoteLastModified = 1000L,
            anchorMd5 = "A",
            resolvedBookMode = SyncMode.TWO_WAY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.NO_OP, action)
    }

    @Test
    fun `missing anchor with differing content returns MERGE_CONFLICT in TWO_WAY`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 8L,
            localLastModified = 1000L,
            localStructMd5 = "L",
            remoteFileMd5 = "different_md5",
            remoteSeq = 6L,
            remoteStructMd5 = "R",
            remoteLastModified = 500L,
            anchorMd5 = null,
            resolvedBookMode = SyncMode.TWO_WAY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.MERGE_CONFLICT, action)
    }

    @Test
    fun `missing anchor keeps BACKUP_ONLY upload semantics`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 5L,
            localLastModified = 2000L,
            localStructMd5 = "L",
            remoteFileMd5 = "different_md5",
            remoteSeq = 2L,
            remoteStructMd5 = "R",
            remoteLastModified = 1000L,
            anchorMd5 = null,
            resolvedBookMode = SyncMode.BACKUP_ONLY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.UPLOAD, action)
    }

    @Test
    fun `anchor path respects RESTORE_ONLY by translating upload to DOWNLOAD`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 5L,
            localLastModified = 2000L,
            localStructMd5 = "L",
            remoteFileMd5 = "different_md5",
            remoteSeq = 2L,
            remoteStructMd5 = "A",
            remoteLastModified = 1000L,
            anchorMd5 = "A",
            resolvedBookMode = SyncMode.RESTORE_ONLY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.DOWNLOAD, action)
    }

    @Test
    fun `BACKUP_ONLY uploads when both sides changed`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 2L,
            localLastModified = 1000L,
            localStructMd5 = "L",
            remoteFileMd5 = "different_md5",
            remoteSeq = 2L,
            remoteStructMd5 = "R",
            remoteLastModified = 1000L,
            anchorMd5 = "A",
            resolvedBookMode = SyncMode.BACKUP_ONLY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.UPLOAD, action)
    }

    @Test
    fun `TWO_WAY mode with conflict files returns MERGE_CONFLICT`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 2L,
            localLastModified = 1000L,
            localStructMd5 = "L",
            remoteFileMd5 = "different_md5",
            remoteSeq = 2L,
            remoteStructMd5 = "R",
            remoteLastModified = 1000L,
            anchorMd5 = "A",
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
            localStructMd5 = "L",
            remoteFileMd5 = md5,
            remoteSeq = 5L,
            remoteStructMd5 = "R",
            remoteLastModified = 2000L,
            anchorMd5 = "A",
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
            localStructMd5 = "L",
            remoteFileMd5 = "different_md5",
            remoteSeq = 5L,
            remoteStructMd5 = "R",
            remoteLastModified = 2000L,
            anchorMd5 = null,
            resolvedBookMode = SyncMode.BACKUP_ONLY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.NO_OP, action)
    }

    @Test
    fun `RESTORE_ONLY mode allows download when remote is newer`() {
        val file = createTempFileWithContent("local_content")
        val action = engine.determineSyncAction(
            localFile = file,
            localSeq = 2L,
            localLastModified = 1000L,
            localStructMd5 = "L",
            remoteFileMd5 = "different_md5",
            remoteSeq = 5L,
            remoteStructMd5 = "R",
            remoteLastModified = 2000L,
            anchorMd5 = null,
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
            localStructMd5 = "L",
            remoteFileMd5 = "different_md5",
            remoteSeq = 0L,
            remoteLastModified = 1000L,
            remoteStructMd5 = "R",
            anchorMd5 = null,
            resolvedBookMode = SyncMode.BACKUP_ONLY,
            remoteConflictFilesNotEmpty = false
        )
        assertEquals(SyncAction.UPLOAD, action)
    }
}
