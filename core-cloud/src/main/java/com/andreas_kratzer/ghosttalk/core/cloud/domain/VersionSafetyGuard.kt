package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

object VersionSafetyGuard {
    const val CURRENT_FORMAT_VERSION = "1.1"

    fun getLocalVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(pInfo)
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Checks if a remote file (via its metadata properties) is compatible with the local app.
     * Returns a [Result] which is Success(Unit) if compatible, or Failure with an informative Exception if not.
     */
    fun checkCompatibility(
        context: Context,
        remoteProperties: Map<String, String>?
    ): Result<Unit> {
        if (remoteProperties == null) return Result.success(Unit)

        // 1. Check Format Version
        val remoteFormatVersion = remoteProperties["ghosttalk_import_version"]
        if (remoteFormatVersion != null) {
            if (compareVersions(remoteFormatVersion, CURRENT_FORMAT_VERSION) > 0) {
                return Result.failure(
                    IllegalStateException("Dateiformat in der Cloud ($remoteFormatVersion) ist neuer als das lokal unterstützte Format ($CURRENT_FORMAT_VERSION). Bitte aktualisiere die App.")
                )
            }
        }

        // 2. Check App Version Code
        val remoteAppVersionCode = remoteProperties["app_version_code"]?.toLongOrNull()
        if (remoteAppVersionCode != null) {
            val localAppVersionCode = getLocalVersionCode(context)
            if (remoteAppVersionCode > localAppVersionCode) {
                val remoteAppName = remoteProperties["app_version"] ?: "neuere Version"
                return Result.failure(
                    IllegalStateException("Die Daten in der Cloud wurden von einer neueren App-Version ($remoteAppName, Code: $remoteAppVersionCode) hochgeladen. Bitte aktualisiere die App.")
                )
            }
        }

        return Result.success(Unit)
    }

    /**
     * Checks if parsed JSON values (from a file content) are compatible with the local app.
     */
    fun checkJsonCompatibility(
        context: Context,
        ghosttalkImportVersion: String?,
        appVersionCode: Long?
    ): Result<Unit> {
        if (ghosttalkImportVersion != null) {
            if (compareVersions(ghosttalkImportVersion, CURRENT_FORMAT_VERSION) > 0) {
                return Result.failure(
                    IllegalStateException("Dateiformat ($ghosttalkImportVersion) ist neuer als das lokal unterstützte Format ($CURRENT_FORMAT_VERSION). Bitte aktualisiere die App.")
                )
            }
        }
        if (appVersionCode != null) {
            val localAppVersionCode = getLocalVersionCode(context)
            if (appVersionCode > localAppVersionCode) {
                return Result.failure(
                    IllegalStateException("Die Modelldaten wurden mit einer neueren App-Version (Code: $appVersionCode) erstellt. Bitte aktualisiere die App.")
                )
            }
        }
        return Result.success(Unit)
    }

    /**
     * Helper to compare semantic version strings (like "1.1", "1.1.2", "1.2")
     */
    fun compareVersions(v1: String, v2: String): Int {
        val vals1 = v1.split(".").mapNotNull { it.trim().toIntOrNull() }
        val vals2 = v2.split(".").mapNotNull { it.trim().toIntOrNull() }
        val maxLen = maxOf(vals1.size, vals2.size)
        for (i in 0 until maxLen) {
            val num1 = vals1.getOrElse(i) { 0 }
            val num2 = vals2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }
        return 0
    }
}
