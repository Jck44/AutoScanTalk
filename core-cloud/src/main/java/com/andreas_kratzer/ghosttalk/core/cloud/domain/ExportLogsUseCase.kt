package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class LogUploadResult {
    object Success : LogUploadResult()
    data class Skipped(val reason: String) : LogUploadResult()
    data class Error(val message: String) : LogUploadResult()
}

class ExportLogsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val bookRepository: BookRepository,
    private val cloudSyncUseCase: CloudSyncUseCase,
    private val googleAuthManager: GoogleAuthManager
) {
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    @android.annotation.SuppressLint("HardwareIds")
    suspend fun generateLogString(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        
        // 1. Device and Application Metadata Header
        sb.append("=========================================\n")
        sb.append("GhostTalk FEHLERBERICHT / LOGCAT AUSZUG\n")
        sb.append("=========================================\n")
        sb.append("Erstellungsdatum : ").append(timestampFormat.format(Date())).append("\n")
        sb.append("Geräte-Hersteller: ").append(Build.MANUFACTURER).append("\n")
        sb.append("Geräte-Modell    : ").append(Build.MODEL).append("\n")
        sb.append("Android-Version  : ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        sb.append("Geräte-ID        : ").append(deviceId).append("\n")
        
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            sb.append("App-Version      : ").append(pInfo.versionName).append(" (Code ").append(pInfo.longVersionCode).append(")\n")
        } catch (_: Exception) {
            sb.append("App-Version      : unknown\n")
        }
        
        val activeBookId = settingsRepository.activeBookId
        val activeBook = bookRepository.getBookById(activeBookId)
        sb.append("Aktives Buch (ID): ").append(activeBook?.name ?: "Unbenannt").append(" (").append(activeBookId).append(")\n")
        sb.append("Sync-Zieltyp     : ").append(settingsRepository.syncTargetType).append("\n")
        sb.append("=========================================\n\n")

        // 2. Read from RollingFileLogger files (falling back to Logcat if empty)
        try {
            val rollingFileLogger = com.andreas_kratzer.ghosttalk.core.util.RollingFileLogger(context)
            val files = rollingFileLogger.getLogFiles()
            if (files.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val twelveHoursAgo = now - 12 * 60 * 60 * 1000 // 12 hours window
                val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
                val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                
                var lastLineWasAppended = true
                for (file in files) {
                    file.forEachLine { line ->
                        // Check if the line starts with a timestamp (e.g. "06-08 15:00:00.000")
                        val hasTimestamp = line.length >= 18 && line[2] == '-' && line[5] == ' ' && line[8] == ':' && line[11] == ':' && line[14] == '.'
                        if (hasTimestamp) {
                            val timeStr = line.substring(0, 18)
                            try {
                                val date = dateFormat.parse(timeStr)
                                if (date != null) {
                                    val cal = java.util.Calendar.getInstance()
                                    cal.time = date
                                    cal.set(java.util.Calendar.YEAR, currentYear)
                                    val timestamp = cal.timeInMillis
                                    // Filter logs older than 12 hours
                                    lastLineWasAppended = timestamp >= twelveHoursAgo
                                } else {
                                    lastLineWasAppended = true
                                }
                            } catch (_: Exception) {
                                lastLineWasAppended = true
                            }
                        }
                        if (lastLineWasAppended) {
                            sb.append(line).append("\n")
                        }
                    }
                }
            } else {
                // Fallback to logcat if no files exist
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (!isNoiseLine(line!!)) {
                        sb.append(line).append("\n")
                    }
                }
            }
        } catch (e: Exception) {
            sb.append("Fehler beim Auslesen der Logs: ").append(e.message).append("\n")
        }

        sb.toString()
    }

    /**
     * Returns true for logcat lines that carry no diagnostic value and should
     * be dropped before the log is shared or uploaded.
     *
     * The filter targets well-known Android framework noise tags that appear
     * frequently even during normal operation and are never relevant for
     * debugging GhosTTalk-specific issues.
     */
    private fun isNoiseLine(line: String): Boolean {
        // Logcat format: "MM-DD HH:mm:ss.mmm PID-TID  TAG  PACKAGE  LEVEL  message"
        // We match against the TAG column (between the second and third set of spaces
        // after the timestamp) plus known message substrings.

        // ---- Tags that are always noise ----------------------------------------
        val noiseTags = arrayOf(
            "ViewRootImpl",
            "Choreographer",
            "EGL_emulation",
            "EGLCodecCommon",
            "OpenGLRenderer",
            "RenderThread",
            "RenderScript",
            "HardwareRenderer",
            "HwBinder",
            "InputMethodManager",
            "InputManager",
            "InputTransport",
            "SurfaceFlinger",
            "SurfaceView",
            "Surface",
            "BufferQueueProducer",
            "BufferQueueConsumer",
            "Gralloc",
            "gralloc",
            "mali",
            "MALI",
            "libEGL",
            "GLSurfaceView",
            "MediaPlayerNative",
            "MediaPlayer",
            "AudioFlinger",
            "AudioTrack",
            "AudioSystem",
            "AudioManager",
            "AudioPolicyManager",
            "AudioService",
            "audio_hw_primary",
            "wificond",
            "WifiStateMachine",
            "WifiManager",
            "WifiNative",
            "ConnectivityService",
            "NetworkScheduler",
            "NetworkStats",
            "TelephonyManager",
            "RIL",
            "SignalStrength",
            "PackageManager",
            "ActivityManager",
            "ActivityTaskManager",
            "WindowManager",
            "CompatibilityInfo",
            "ContentResolver",
            "ContentProvider",
            "SQLiteDatabase",
            "SQLiteLog",
            "SQLiteStatementInfo",
            "StrictMode",
            "BatteryStatsImpl",
            "PowerManager",
            "PowerManagerService",
            "BroadcastQueue",
            "ProcessStats",
            "Perf",
            "perfetto",
            "art",
            "zygote",
            "Zygote",
            "dalvikvm",
            "jdwp",
            "GC",
            "libGLESv2",
            "VsyncThread",
            "BLASTBufferQueue",
            "GraphicBuffer",
            "ComposeSurface"
        )

        // ---- Known noisy message fragments (substring match) -------------------
        val noiseFragments = arrayOf(
            "action cancel",
            "eccen:",
            "requestLayout() improperly called",
            "Skipped",
            "jank",
            "dropping",
            "vsync",
            "Vsync",
            "frame timeout",
            "Slow",
            "leaking",
            "Profile save took",
            "GcStateAssertLock",
            "GetCurrentMethod",
            "I/O exception",           // too generic; covered by specific tag matches
            "KeyEvent { action=CANCEL" // touch cancel spam
        )

        // Check if the line contains any noise tag surrounded by word boundaries.
        // Logcat lines have variable whitespace; a simple contains() is sufficient
        // because tag names are distinctive enough.
        for (tag in noiseTags) {
            if (line.contains(tag)) return true
        }
        for (fragment in noiseFragments) {
            if (line.contains(fragment)) return true
        }
        return false
    }

    suspend fun shareLogs(context: Context) = withContext(Dispatchers.IO) {
        val logContent = generateLogString()
        val tempFile = File(context.cacheDir, "ghosttalk_error_log.txt")
        tempFile.writeText(logContent)
        
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, tempFile)
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "GhostTalk Fehlerbericht")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooserIntent = Intent.createChooser(shareIntent, "Fehlerbericht teilen").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
    }

    @android.annotation.SuppressLint("HardwareIds")
    suspend fun uploadLogs(drive: Drive?, force: Boolean = false): LogUploadResult = withContext(Dispatchers.IO) {
        if (!force && settingsRepository.syncModeLogs == "OFF") {
            return@withContext LogUploadResult.Skipped("Log-Upload ist deaktiviert.")
        }
        
        val logContent = generateLogString()
        val currentHash = getSha256Hash(logContent)
        val lastHash = settingsRepository.lastUploadedLogHash
        
        if (!force && currentHash == lastHash) {
            return@withContext LogUploadResult.Skipped("Keine neuen Log-Einträge vorhanden.")
        }
        
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val logFileName = "ghosttalk_log_${deviceId}.txt"
        val tempFile = File(context.cacheDir, logFileName)
        
        try {
            tempFile.writeText(logContent)
            val success = cloudSyncUseCase.uploadLogFile(drive, tempFile)
            if (success) {
                settingsRepository.lastUploadedLogHash = currentHash
                settingsRepository.lastLogsSyncTime = System.currentTimeMillis()
                LogUploadResult.Success
            } else {
                LogUploadResult.Error("Upload fehlgeschlagen.")
            }
        } catch (e: Exception) {
            LogUploadResult.Error("Upload-Fehler: ${e.message}")
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    suspend fun performAutoUpload(force: Boolean = false): LogUploadResult = withContext(Dispatchers.IO) {
        if (!force && settingsRepository.syncModeLogs == "OFF") {
            return@withContext LogUploadResult.Skipped("Log-Upload ist deaktiviert.")
        }
        val isSaf = settingsRepository.syncTargetType == "LOCAL_FOLDER_SAF"
        val drive = if (isSaf) {
            null
        } else {
            val credential = googleAuthManager.getGoogleCredential() ?: return@withContext LogUploadResult.Error("Keine Google-Anmeldedaten vorhanden.")
            Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance()
            ) { request ->
                credential.initialize(request)
                request.connectTimeout = 3 * 60 * 1000
                request.readTimeout = 3 * 60 * 1000
            }.setApplicationName("GhostTalk").build()
        }
        uploadLogs(drive, force)
    }

    private fun getSha256Hash(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
