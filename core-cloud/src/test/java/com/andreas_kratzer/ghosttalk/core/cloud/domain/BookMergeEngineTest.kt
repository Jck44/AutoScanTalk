package com.andreas_kratzer.ghosttalk.core.cloud.domain

import com.andreas_kratzer.ghosttalk.core.util.Logger
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class BookMergeEngineTest {

    private val logger = mockk<Logger>(relaxed = true)
    private val engine = BookMergeEngine(logger)

    @Test
    fun `calculateStructuralMd5FromJson ignores app_version_code and import_version`() {
        val json1 = """
            {
                "bookId": "123",
                "bookName": "Test Book",
                "app_version_code": 100,
                "ghosttalk_import_version": "1.1"
            }
        """.trimIndent()

        val json2 = """
            {
                "bookId": "123",
                "bookName": "Test Book",
                "app_version_code": 105,
                "ghosttalk_import_version": "1.2"
            }
        """.trimIndent()

        val hash1 = engine.calculateStructuralMd5FromJson(json1)
        val hash2 = engine.calculateStructuralMd5FromJson(json2)

        org.junit.Assert.assertTrue("Hash should not be empty (indicates parse failure)", hash1.isNotEmpty())
        assertEquals(hash1, hash2)
    }

    @Test
    fun `mergeBooks preserves remote button if local page updated time is newer but there is no tombstone`() {
        val localButton = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportButton(id = "button1", label = "Local Button")
        val remoteButton = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportButton(id = "button2", label = "Remote Button", updatedAt = 1000L)
        
        val localPage = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage(
            importId = "page1",
            updatedAt = 2000L,
            buttons = listOf(localButton)
        )
        val remotePage = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage(
            importId = "page1",
            updatedAt = 1000L,
            buttons = listOf(localButton, remoteButton)
        )

        val localData = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData(
            bookId = "book1",
            bookUpdatedAt = 2000L,
            pages = listOf(localPage)
        )
        val remoteData = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData(
            bookId = "book1",
            bookUpdatedAt = 1000L,
            pages = listOf(remotePage)
        )

        val merged = engine.mergeBooks(localData, remoteData)
        val mergedPage = merged.pages.first()
        
        val buttonIds = mergedPage.buttons.map { it.id }.toSet()
        org.junit.Assert.assertTrue("Should contain both button1 and button2", buttonIds.contains("button1") && buttonIds.contains("button2"))
    }

    @Test
    fun `mergeBooks deletes remote button if local has a matching tombstone`() {
        val localButton = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportButton(id = "button1", label = "Local Button")
        val remoteButton = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportButton(id = "button2", label = "Remote Button", updatedAt = 1000L)
        
        val localPage = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage(
            importId = "page1",
            updatedAt = 2000L,
            buttons = listOf(localButton)
        )
        val remotePage = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage(
            importId = "page1",
            updatedAt = 1000L,
            buttons = listOf(localButton, remoteButton)
        )

        val tombstone = com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedTombstone(
            entityId = "button2",
            entityType = "BUTTON",
            deletedAt = 1500L
        )

        val localData = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData(
            bookId = "book1",
            bookUpdatedAt = 2000L,
            pages = listOf(localPage),
            deletedEntities = listOf(tombstone)
        )
        val remoteData = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData(
            bookId = "book1",
            bookUpdatedAt = 1000L,
            pages = listOf(remotePage)
        )

        val merged = engine.mergeBooks(localData, remoteData)
        val mergedPage = merged.pages.first()
        
        val buttonIds = mergedPage.buttons.map { it.id }.toSet()
        org.junit.Assert.assertTrue("Should only contain button1", buttonIds.contains("button1") && !buttonIds.contains("button2"))
    }

    @Test
    fun `remote edit after local delete resurrects button`() {
        val localPage = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage(
            importId = "page1",
            updatedAt = 2000L,
            buttons = emptyList()
        )
        val remoteButton = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportButton(
            id = "button1",
            label = "Edited Remote Button",
            updatedAt = 2500L
        )
        val remotePage = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage(
            importId = "page1",
            updatedAt = 2500L,
            buttons = listOf(remoteButton)
        )

        val tombstone = com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedTombstone(
            entityId = "button1",
            entityType = "BUTTON",
            deletedAt = 2000L
        )

        val localData = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData(
            bookId = "book1",
            bookUpdatedAt = 2000L,
            pages = listOf(localPage),
            deletedEntities = listOf(tombstone)
        )
        val remoteData = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData(
            bookId = "book1",
            bookUpdatedAt = 2500L,
            pages = listOf(remotePage)
        )

        val merged = engine.mergeBooks(localData, remoteData)
        val mergedPage = merged.pages.first()
        val buttonIds = mergedPage.buttons.map { it.id }.toSet()
        org.junit.Assert.assertTrue("Remote button should resurrect because it was edited after the delete", buttonIds.contains("button1"))
    }

    @Test
    fun `buttons without id keep legacy page-time heuristic`() {
        val remoteButton = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportButton(
            id = null,
            index = 1,
            label = "Legacy Button",
            updatedAt = 1000L
        )
        val localPage = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage(
            importId = "page1",
            updatedAt = 2000L,
            buttons = emptyList()
        )
        val remotePage = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage(
            importId = "page1",
            updatedAt = 1000L,
            buttons = listOf(remoteButton)
        )

        val localData = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData(
            bookId = "book1",
            bookUpdatedAt = 2000L,
            pages = listOf(localPage)
        )
        val remoteData = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData(
            bookId = "book1",
            bookUpdatedAt = 1000L,
            pages = listOf(remotePage)
        )

        val merged = engine.mergeBooks(localData, remoteData)
        val mergedPage = merged.pages.first()
        org.junit.Assert.assertTrue("Button without ID should be dropped based on legacy heuristic", mergedPage.buttons.isEmpty())
    }

    @Test
    fun `button tombstone is dropped when button exists in merged result`() {
        val button = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportButton(
            id = "button1",
            label = "Button 1",
            updatedAt = 2000L
        )
        val localPage = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage(
            importId = "page1",
            updatedAt = 2000L,
            buttons = listOf(button)
        )
        val remotePage = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage(
            importId = "page1",
            updatedAt = 2000L,
            buttons = listOf(button)
        )

        val tombstone = com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedTombstone(
            entityId = "button1",
            entityType = "BUTTON",
            deletedAt = 1000L
        )

        val localData = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData(
            bookId = "book1",
            bookUpdatedAt = 2000L,
            pages = listOf(localPage),
            deletedEntities = listOf(tombstone)
        )
        val remoteData = com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData(
            bookId = "book1",
            bookUpdatedAt = 2000L,
            pages = listOf(remotePage)
        )

        val merged = engine.mergeBooks(localData, remoteData)
        val deletedIds = merged.deletedEntities.orEmpty().map { it.entityId }.toSet()
        org.junit.Assert.assertFalse("Tombstone should be dropped because button1 exists in merged result", deletedIds.contains("button1"))
    }
}

