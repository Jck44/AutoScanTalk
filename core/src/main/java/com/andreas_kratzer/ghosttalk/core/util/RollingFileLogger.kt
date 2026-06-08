package com.andreas_kratzer.ghosttalk.core.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RollingFileLogger(private val context: Context) {
    private val TAG = "RollingFileLogger"
    private val logDir = try {
        val dir = context.filesDir
        if (dir != null) {
            File(dir, "app_logs")
        } else {
            File("/tmp", "app_logs")
        }
    } catch (e: Exception) {
        File("/tmp", "app_logs")
    }
    private val currentLogFile = File(logDir, "app_log_current.txt")
    private val backupLogFile1 = File(logDir, "app_log_backup_1.txt")
    private val backupLogFile2 = File(logDir, "app_log_backup_2.txt")
    private val maxFileSize = 2 * 1024 * 1024 // 2MB
    private val timestampFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    init {
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
    }

    @Synchronized
    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        try {
            checkRotation()
            val time = timestampFormat.format(Date())
            val logLine = if (throwable != null) {
                val sw = java.io.StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                "$time  $level/$tag: $message\n$sw\n"
            } else {
                "$time  $level/$tag: $message\n"
            }
            FileWriter(currentLogFile, true).use { writer ->
                writer.write(logLine)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to rolling log file", e)
        }
    }

    private fun checkRotation() {
        if (currentLogFile.exists() && currentLogFile.length() >= maxFileSize) {
            rotateFiles()
        }
    }

    private fun rotateFiles() {
        try {
            // Delete oldest backup
            if (backupLogFile2.exists()) {
                backupLogFile2.delete()
            }
            // Move backup 1 to backup 2
            if (backupLogFile1.exists()) {
                backupLogFile1.renameTo(backupLogFile2)
            }
            // Move current to backup 1
            if (currentLogFile.exists()) {
                currentLogFile.renameTo(backupLogFile1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating files", e)
        }
    }

    fun getLogFiles(): List<File> {
        val files = mutableListOf<File>()
        if (backupLogFile2.exists()) files.add(backupLogFile2)
        if (backupLogFile1.exists()) files.add(backupLogFile1)
        if (currentLogFile.exists()) files.add(currentLogFile)
        return files
    }

    @Synchronized
    fun clearLogs() {
        try {
            if (currentLogFile.exists()) currentLogFile.delete()
            if (backupLogFile1.exists()) backupLogFile1.delete()
            if (backupLogFile2.exists()) backupLogFile2.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing log files", e)
        }
    }
}
