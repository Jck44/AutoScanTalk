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
}
