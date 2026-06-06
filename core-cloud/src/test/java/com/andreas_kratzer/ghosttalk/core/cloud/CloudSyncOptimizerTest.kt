package com.andreas_kratzer.ghosttalk.core.cloud

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CloudSyncOptimizerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var localFile: File
    private lateinit var optimizer: CloudSyncOptimizer

    @Before
    fun setUp() {
        optimizer = CloudSyncOptimizer()
        localFile = tempFolder.newFile("local_book.zip")
        localFile.writeText("hello world")
    }

    @Test
    fun testEvaluateSync_IdenticalContent_ReturnsNoOp() {
        // Calculate MD5 of localFile
        val localMd5 = optimizer.calculateMD5(localFile)

        // When content is identical, it should be NO_OP regardless of sequence numbers
        assertEquals(SyncAction.NO_OP, optimizer.evaluateSync(localFile, localMd5, 5L, 2L))
        assertEquals(SyncAction.NO_OP, optimizer.evaluateSync(localFile, localMd5, 0L, 0L))
        assertEquals(SyncAction.NO_OP, optimizer.evaluateSync(localFile, localMd5, 2L, 5L))
    }

    @Test
    fun testEvaluateSync_NewerLocalSequence_ReturnsUpload() {
        val differentMd5 = "different_md5_hash"

        // Local has newer sequence, should upload
        assertEquals(SyncAction.UPLOAD, optimizer.evaluateSync(localFile, differentMd5, 5L, 2L))
    }

    @Test
    fun testEvaluateSync_NewerRemoteSequence_ReturnsDownload() {
        val differentMd5 = "different_md5_hash"

        // Remote has newer sequence, should download
        assertEquals(SyncAction.DOWNLOAD, optimizer.evaluateSync(localFile, differentMd5, 2L, 5L))
    }

    @Test
    fun testEvaluateSync_EqualSequencesDifferentContent_ReturnsMergeConflict() {
        val differentMd5 = "different_md5_hash"

        // Equal sequence numbers with different content should trigger conflict
        assertEquals(SyncAction.MERGE_CONFLICT, optimizer.evaluateSync(localFile, differentMd5, 5L, 5L))
    }

    @Test
    fun testEvaluateSync_LegacyRemote_ReturnsMergeConflict() {
        val differentMd5 = "different_md5_hash"

        // Even if local sequence is greater, if remote is legacy (0L), force conflict to merge
        assertEquals(SyncAction.MERGE_CONFLICT, optimizer.evaluateSync(localFile, differentMd5, 5L, 0L))
    }

    @Test
    fun testEvaluateSync_LegacyLocal_ReturnsMergeConflict() {
        val differentMd5 = "different_md5_hash"

        // Even if remote sequence is greater, if local is legacy (0L), force conflict to merge
        assertEquals(SyncAction.MERGE_CONFLICT, optimizer.evaluateSync(localFile, differentMd5, 0L, 5L))
    }

    @Test
    fun testEvaluateSync_BothLegacy_ReturnsMergeConflict() {
        val differentMd5 = "different_md5_hash"

        // Both legacy with different content triggers conflict
        assertEquals(SyncAction.MERGE_CONFLICT, optimizer.evaluateSync(localFile, differentMd5, 0L, 0L))
    }
}
