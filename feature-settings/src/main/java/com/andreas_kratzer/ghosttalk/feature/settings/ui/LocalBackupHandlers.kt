package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

internal fun isZipUri(fileName: String?, mimeType: String?): Boolean {
    val lowerFileName = fileName?.lowercase() ?: ""
    return lowerFileName.endsWith(".zip") || mimeType == "application/zip"
}

internal fun handleLocalImport(
    context: Context,
    uri: Uri,
    viewModel: SettingsViewModel,
    coroutineScope: CoroutineScope,
    isGlobal: Boolean
) {
    coroutineScope.launch {
        try {
            val fileName = uri.path
            val mimeType = context.contentResolver.getType(uri)
            val isZip = isZipUri(fileName, mimeType)

            if (isZip) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    if (isGlobal) {
                        viewModel.importGlobalManualBackupZip(
                            inputStream = inputStream,
                            onSuccess = { _ ->
                                Toast.makeText(context, context.getString(R.string.settings_import_success), Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    } else {
                        viewModel.importLocalBackupZip(
                            inputStream = inputStream,
                            onSuccess = {
                                Toast.makeText(context, context.getString(CoreR.string.page_import_success), Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            } else {
                // Legacy JSON import
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val jsonContent = reader.readText()
                    if (isGlobal) {
                        viewModel.importGlobalManualBackup(
                            json = jsonContent,
                            onSuccess = { _ ->
                                Toast.makeText(context, context.getString(R.string.settings_import_success), Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    } else {
                        viewModel.importLocalBackup(
                            json = jsonContent,
                            onSuccess = {
                                Toast.makeText(context, context.getString(CoreR.string.page_import_success), Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, context.getString(R.string.settings_error_import_export, e.message), Toast.LENGTH_LONG).show()
        }
    }
}

internal fun handleLocalExport(
    context: Context,
    uri: Uri,
    viewModel: SettingsViewModel,
    coroutineScope: CoroutineScope
) {
    coroutineScope.launch {
        try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    viewModel.exportLocalBackupZip(outputStream)
                }
            }
            Toast.makeText(context, context.getString(CoreR.string.page_export_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, context.getString(R.string.settings_error_import_export, e.message), Toast.LENGTH_LONG).show()
        }
    }
}
