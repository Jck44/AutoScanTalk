package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.pages.PageImportExportManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ExportPageUseCaseTest {

    private lateinit var importExportManager: PageImportExportManager
    private lateinit var useCase: ExportPageUseCase

    @Before
    fun setup() {
        importExportManager = mockk()
        useCase = ExportPageUseCase(importExportManager)
    }

    @Test
    fun `execute calls manager and returns json`() = runTest {
        val pages = listOf<Page>(mockk())
        val expectedJson = "{\"pages\": []}"
        coEvery { importExportManager.exportPageListToJson(pages) } returns expectedJson

        val result = useCase.execute(pages)

        assertEquals(expectedJson, result)
    }
}
