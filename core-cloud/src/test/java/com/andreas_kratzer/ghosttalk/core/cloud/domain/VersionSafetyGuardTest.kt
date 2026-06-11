package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionSafetyGuardTest {

    @Test
    fun testCompareVersions() {
        assertEquals(0, VersionSafetyGuard.compareVersions("1.1", "1.1"))
        assertEquals(-1, VersionSafetyGuard.compareVersions("1.1", "1.2"))
        assertEquals(1, VersionSafetyGuard.compareVersions("1.2", "1.1"))
        assertEquals(-1, VersionSafetyGuard.compareVersions("1.1.1", "1.1.2"))
        assertEquals(1, VersionSafetyGuard.compareVersions("1.2.0", "1.1.9"))
        assertEquals(0, VersionSafetyGuard.compareVersions("1.1", "1.1.0"))
    }

    @Test
    fun testCheckCompatibility_compatible() {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        val packageInfo = mockk<PackageInfo>().apply {
            versionCode = 100
        }
        every { context.packageName } returns "com.andreas_kratzer.ghosttalk"
        every { context.packageManager } returns packageManager
        every { packageManager.getPackageInfo("com.andreas_kratzer.ghosttalk", 0) } returns packageInfo

        // Same version, same format
        val remoteProps = mapOf(
            "ghosttalk_import_version" to "1.1",
            "app_version_code" to "100"
        )
        val result = VersionSafetyGuard.checkCompatibility(context, remoteProps)
        assertTrue(result.isSuccess)
    }

    @Test
    fun testCheckCompatibility_olderRemote() {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        val packageInfo = mockk<PackageInfo>().apply {
            versionCode = 100
        }
        every { context.packageName } returns "com.andreas_kratzer.ghosttalk"
        every { context.packageManager } returns packageManager
        every { packageManager.getPackageInfo("com.andreas_kratzer.ghosttalk", 0) } returns packageInfo

        // Older remote version and format
        val remoteProps = mapOf(
            "ghosttalk_import_version" to "1.0",
            "app_version_code" to "90"
        )
        val result = VersionSafetyGuard.checkCompatibility(context, remoteProps)
        assertTrue(result.isSuccess)
    }

    @Test
    fun testCheckCompatibility_newerFormatVersion() {
        val context = mockk<Context>()
        
        // Newer format version
        val remoteProps = mapOf(
            "ghosttalk_import_version" to "1.2",
            "app_version_code" to "100"
        )
        val result = VersionSafetyGuard.checkCompatibility(context, remoteProps)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Dateiformat") == true)
    }

    @Test
    fun testCheckCompatibility_newerAppVersion() {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        val packageInfo = mockk<PackageInfo>().apply {
            versionCode = 100
        }
        every { context.packageName } returns "com.andreas_kratzer.ghosttalk"
        every { context.packageManager } returns packageManager
        every { packageManager.getPackageInfo("com.andreas_kratzer.ghosttalk", 0) } returns packageInfo

        // Newer app version code
        val remoteProps = mapOf(
            "ghosttalk_import_version" to "1.1",
            "app_version_code" to "101"
        )
        val result = VersionSafetyGuard.checkCompatibility(context, remoteProps)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("App-Version") == true)
    }
}
