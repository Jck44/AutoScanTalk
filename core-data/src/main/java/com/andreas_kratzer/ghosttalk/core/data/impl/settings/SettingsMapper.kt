package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.cloud.AuthManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData
import com.andreas_kratzer.ghosttalk.core.util.EncryptionUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles the mapping between [SettingsRepository] and [ImportExportData].
 * This decouples the import/export format from the internal settings storage logic.
 */
@Singleton
class SettingsMapper @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authManager: AuthManager
) {
    private val TAG = "SettingsMapper"

    /**
     * Populates an [ImportExportData] object with settings for a specific book.
     */
    fun exportSettings(bookId: String, data: ImportExportData): ImportExportData {
        val apiKey = settingsRepository.elevenLabsApiKey
        val encryptedKey = if (!apiKey.isNullOrEmpty()) {
            val userEmail = authManager.userEmail.value
            if (!userEmail.isNullOrEmpty()) {
                try {
                    EncryptionUtils.encrypt(apiKey, userEmail)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to encrypt ElevenLabs API Key", e)
                    null
                }
            } else {
                Log.w(TAG, "No user logged in, API key will not be included in backup (plain text avoided)")
                null
            }
        } else null

        return data.copy(
            actionLogLimit = settingsRepository.getActionLogLimitForBook(bookId),
            limitScanCycles = settingsRepository.getLimitScanCyclesForBook(bookId),
            scanCycleLimit = settingsRepository.getScanCycleLimitForBook(bookId),
            logIgnoredActions = settingsRepository.getLogIgnoredActionsForBook(bookId),
            logStopActions = settingsRepository.getLogStopActionsForBook(bookId),
            holdingTimeSeconds = settingsRepository.getHoldingTimeMillisForBook(bookId) / 1000f,
            autoStartScanning = settingsRepository.getAutoStartScanningForBook(bookId),
            scanDelayMillis = settingsRepository.getScanDelayMillisForBook(bookId),
            resumeScanningFromStart = settingsRepository.getResumeScanningFromStartForBook(bookId),
            switchActivationKey = settingsRepository.getSwitchActivationKeyForBook(bookId),
            volumeKeysActivate = settingsRepository.getVolumeKeysActivateForBook(bookId),
            defaultScanPattern = settingsRepository.getDefaultScanPatternForBook(bookId),
            staticRowEnabled = settingsRepository.getStaticRowEnabledForBook(bookId),
            staticRowScanPattern = settingsRepository.getStaticRowScanPatternForBook(bookId),
            isSmartPredictionEnabled = settingsRepository.getIsSmartPredictionEnabledForBook(bookId),
            geminiRedoPrediction = settingsRepository.geminiRedoPrediction,
            geminiTimeout = settingsRepository.geminiTimeout,
            isGeminiEnabled = settingsRepository.isGeminiEnabled,
            useLocalGenerativeAi = settingsRepository.useLocalGenerativeAi,
            isCloudSyncEnabled = settingsRepository.isCloudSyncEnabled,
            syncIntervalMinutes = settingsRepository.syncIntervalMinutes,
            syncModeBook = settingsRepository.syncModeBook,
            syncModeTts = settingsRepository.syncModeTts,
            syncModeStats = settingsRepository.syncModeStats,
            ttsLanguage = settingsRepository.ttsLanguage,
            ttsVoiceName = settingsRepository.ttsVoiceName,
            pageSortOrder = settingsRepository.getPageSortOrderForBook(bookId),
            templateSortOrder = settingsRepository.getTemplateSortOrderForBook(bookId),
            smartPredictionDelay = settingsRepository.getSmartPredictionDelayForBook(bookId),
            keepScreenOnUserMode = settingsRepository.keepScreenOnUserMode,
            userModeScreenBehavior = settingsRepository.userModeScreenBehavior,
            themeMode = settingsRepository.themeMode,
            securityPinTimeoutMinutes = settingsRepository.securityPinTimeoutMinutes,
            isPinRequiredForDeletion = settingsRepository.isPinRequiredForDeletion,
            isBiometricEnabled = settingsRepository.isBiometricEnabled,
            isSecurityRequiredForEdit = settingsRepository.isSecurityRequiredForEdit,
            isSecurityRequiredForSettings = settingsRepository.isSecurityRequiredForSettings,
            isSecurityRequiredForAnalytics = settingsRepository.isSecurityRequiredForAnalytics,
            startupBehavior = settingsRepository.startupBehavior,
            favoriteBookId = settingsRepository.favoriteBookId,
            weatherCacheTimeout = settingsRepository.weatherCacheTimeout,
            defaultStartPageId = settingsRepository.getDefaultStartPageIdForBook(bookId),
            securityPinHash = settingsRepository.securityPinHash,
            securityPinSalt = settingsRepository.securityPinSalt,
            appLanguage = settingsRepository.appLanguage,
            isNotificationReadingEnabled = settingsRepository.isNotificationReadingEnabled,
            monitoredNotificationApps = settingsRepository.monitoredNotificationApps.toList(),
            showPageIdInLog = settingsRepository.showPageIdInLog,
            bluetoothDelay = settingsRepository.bluetoothDelay,
            ttsEngine = settingsRepository.ttsEngine,
            elevenLabsModel = settingsRepository.elevenLabsModel,
            elevenLabsApiKey = encryptedKey,
            googleTtsLanguage = settingsRepository.googleTtsLanguage,
            googleTtsVoiceName = settingsRepository.googleTtsVoiceName,
            elevenLabsTtsLanguage = settingsRepository.elevenLabsTtsLanguage,
            elevenLabsTtsVoiceName = settingsRepository.elevenLabsTtsVoiceName,
            elevenLabsStability = settingsRepository.elevenLabsStability,
            elevenLabsSimilarityBoost = settingsRepository.elevenLabsSimilarityBoost,
            maxCallDurationSeconds = settingsRepository.maxCallDurationSeconds,
            callDurationFeedbackIntervalSeconds = settingsRepository.callDurationFeedbackIntervalSeconds,
            outgoingCallIntro = settingsRepository.outgoingCallIntro,
            incomingCallIntro = settingsRepository.incomingCallIntro,
            incomingCallScanLimitUserModeActive = settingsRepository.incomingCallScanLimitUserModeActive,
            incomingCallAutoActionUserModeActive = settingsRepository.incomingCallAutoActionUserModeActive,
            incomingCallDelayUserModeInactive = settingsRepository.incomingCallDelayUserModeInactive,
            incomingCallAutoActionUserModeInactive = settingsRepository.incomingCallAutoActionUserModeInactive,
            callAnnouncementAsCue = settingsRepository.callAnnouncementAsCue,
            autoEnableSpeakerphone = settingsRepository.autoEnableSpeakerphone,
            simulateCallsEnabled = settingsRepository.simulateCallsEnabled,
            hueCachedDevices = settingsRepository.hueCachedDevices
        )
    }

    /**
     * Applies settings from an [ImportExportData] object to the [SettingsRepository].
     */
    fun importSettings(data: ImportExportData) {
        data.holdingTimeSeconds?.let { 
            settingsRepository.holdingTimeMillis = (it * 1000).toLong()
        }
        data.autoStartScanning?.let { settingsRepository.autoStartScanning = it }
        data.scanDelayMillis?.let { settingsRepository.scanDelayMillis = it }
        data.resumeScanningFromStart?.let { settingsRepository.resumeScanningFromStart = it }
        data.switchActivationKey?.let { settingsRepository.switchActivationKey = it }
        data.volumeKeysActivate?.let { settingsRepository.volumeKeysActivate = it }
        data.defaultScanPattern?.let { settingsRepository.defaultScanPattern = it }
        data.staticRowEnabled?.let { settingsRepository.staticRowEnabled = it }
        data.staticRowScanPattern?.let { settingsRepository.staticRowScanPattern = it }
        data.isSmartPredictionEnabled?.let { settingsRepository.isSmartPredictionEnabled = it }
        data.geminiRedoPrediction?.let { settingsRepository.geminiRedoPrediction = it }
        data.geminiTimeout?.let { settingsRepository.geminiTimeout = it }
        data.isGeminiEnabled?.let { settingsRepository.isGeminiEnabled = it }
        data.useLocalGenerativeAi?.let { settingsRepository.useLocalGenerativeAi = it }
        // Cloud sync settings are device-specific and should never be overwritten by an import.
        // They are still exported for diagnostic/backup visibility but intentionally skipped here.
        // data.isCloudSyncEnabled?.let { settingsRepository.isCloudSyncEnabled = it }
        // data.syncIntervalMinutes?.let { settingsRepository.syncIntervalMinutes = it }
        // data.syncModeBook?.let { settingsRepository.syncModeBook = it }
        // data.syncModeTts?.let { settingsRepository.syncModeTts = it }
        // data.syncModeStats?.let { settingsRepository.syncModeStats = it }
        data.ttsLanguage?.let { settingsRepository.ttsLanguage = it }
        data.ttsVoiceName?.let { settingsRepository.ttsVoiceName = it }
        data.smartPredictionDelay?.let { settingsRepository.smartPredictionDelay = it }
        data.keepScreenOnUserMode?.let { settingsRepository.keepScreenOnUserMode = it }
        data.userModeScreenBehavior?.let { settingsRepository.userModeScreenBehavior = it }
        data.themeMode?.let { settingsRepository.themeMode = it }
        data.securityPinTimeoutMinutes?.let { settingsRepository.securityPinTimeoutMinutes = it }
        data.isPinRequiredForDeletion?.let { settingsRepository.isPinRequiredForDeletion = it }
        data.isBiometricEnabled?.let { settingsRepository.isBiometricEnabled = it }
        data.isSecurityRequiredForEdit?.let { settingsRepository.isSecurityRequiredForEdit = it }
        data.isSecurityRequiredForSettings?.let { settingsRepository.isSecurityRequiredForSettings = it }
        data.isSecurityRequiredForAnalytics?.let { settingsRepository.isSecurityRequiredForAnalytics = it }
        data.startupBehavior?.let { settingsRepository.startupBehavior = it }
        data.weatherCacheTimeout?.let { settingsRepository.weatherCacheTimeout = it }
        data.securityPinHash?.let { settingsRepository.securityPinHash = it }
        data.securityPinSalt?.let { settingsRepository.securityPinSalt = it }
        data.appLanguage?.let { settingsRepository.appLanguage = it }
        data.isNotificationReadingEnabled?.let { settingsRepository.isNotificationReadingEnabled = it }
        data.monitoredNotificationApps?.let { settingsRepository.monitoredNotificationApps = it.toSet() }
        data.showPageIdInLog?.let { settingsRepository.showPageIdInLog = it }
        data.bluetoothDelay?.let { settingsRepository.bluetoothDelay = it }
        data.ttsEngine?.let { settingsRepository.ttsEngine = it }
        data.elevenLabsModel?.let { settingsRepository.elevenLabsModel = it }
        data.googleTtsLanguage?.let { settingsRepository.googleTtsLanguage = it }
        data.googleTtsVoiceName?.let { settingsRepository.googleTtsVoiceName = it }
        data.elevenLabsTtsLanguage?.let { settingsRepository.elevenLabsTtsLanguage = it }
        data.elevenLabsTtsVoiceName?.let { settingsRepository.elevenLabsTtsVoiceName = it }
        data.elevenLabsStability?.let { settingsRepository.elevenLabsStability = it }
        data.elevenLabsSimilarityBoost?.let { settingsRepository.elevenLabsSimilarityBoost = it }
        
        data.maxCallDurationSeconds?.let { settingsRepository.maxCallDurationSeconds = it }
        data.callDurationFeedbackIntervalSeconds?.let { settingsRepository.callDurationFeedbackIntervalSeconds = it }
        data.outgoingCallIntro?.let { settingsRepository.outgoingCallIntro = it }
        data.incomingCallIntro?.let { settingsRepository.incomingCallIntro = it }
        data.incomingCallScanLimitUserModeActive?.let { settingsRepository.incomingCallScanLimitUserModeActive = it }
        data.incomingCallAutoActionUserModeActive?.let { settingsRepository.incomingCallAutoActionUserModeActive = it }
        data.incomingCallDelayUserModeInactive?.let { settingsRepository.incomingCallDelayUserModeInactive = it }
        data.incomingCallAutoActionUserModeInactive?.let { settingsRepository.incomingCallAutoActionUserModeInactive = it }
        data.callAnnouncementAsCue?.let { settingsRepository.callAnnouncementAsCue = it }
        data.autoEnableSpeakerphone?.let { settingsRepository.autoEnableSpeakerphone = it }
        data.simulateCallsEnabled?.let { settingsRepository.simulateCallsEnabled = it }
        data.hueCachedDevices?.let { settingsRepository.hueCachedDevices = it }
        
        data.elevenLabsApiKey?.let { encryptedKey ->
            val userEmail = authManager.userEmail.value
            if (!userEmail.isNullOrEmpty()) {
                try {
                    val decryptedKey = EncryptionUtils.decrypt(encryptedKey, userEmail)
                    settingsRepository.elevenLabsApiKey = decryptedKey
                    Log.i(TAG, "Successfully decrypted and restored ElevenLabs API Key from backup")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decrypt ElevenLabs API Key. Account mismatch or corrupted data.", e)
                }
            } else {
                Log.w(TAG, "Found encrypted API key in backup but no user is logged in. Restore skipped.")
            }
        }
    }
}
