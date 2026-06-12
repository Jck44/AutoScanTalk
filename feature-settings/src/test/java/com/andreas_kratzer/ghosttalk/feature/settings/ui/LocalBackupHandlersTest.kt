package com.andreas_kratzer.ghosttalk.feature.settings.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBackupHandlersTest {

    @Test
    fun testIsZipUri() {
        // Zip extension
        assertTrue(isZipUri("backup.zip", null))
        assertTrue(isZipUri("BACKUP.ZIP", null))
        assertTrue(isZipUri("folder/my_backup.zip", null))

        // Zip mime type
        assertTrue(isZipUri("backup", "application/zip"))
        assertTrue(isZipUri(null, "application/zip"))

        // Non-zip cases
        assertFalse(isZipUri("backup.json", "application/json"))
        assertFalse(isZipUri(null, null))
        assertFalse(isZipUri("", ""))
    }
}
