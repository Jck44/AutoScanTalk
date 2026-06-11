package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.export.ImportResult
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImportPageUseCaseTest {

    private lateinit var importExportManager: PageImportExportProvider
    private lateinit var useCase: ImportPageUseCase

    @Before
    fun setup() {
        importExportManager = mockk()
        useCase = ImportPageUseCase(importExportManager)
    }

    @Test
    fun `execute returns Success when manager succeeds`() = runTest {
        val json = "{\"test\": \"data\"}"
        val bookId = "book1"
        coEvery { importExportManager.importFromJson(json, bookId, any(), any()) } returns Result.success(ImportResult(5))

        val result = useCase.execute(json, bookId)

        assertTrue(result.isSuccess)
        assertEquals(5, result.getOrNull())
    }

    @Test
    fun `execute returns Failure when manager fails`() = runTest {
        val json = "invalid"
        val bookId = "book1"
        val exception = RuntimeException("Import failed")
        coEvery { importExportManager.importFromJson(json, bookId, any(), any()) } returns Result.failure(exception)

        val result = useCase.execute(json, bookId)

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
