package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_CLOUD_SYNC_ENABLED
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_GOOGLE_DRIVE_FOLDER_ID
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_GOOGLE_DRIVE_FOLDER_NAME
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_LAST_SYNC_TIME
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SYNC_INTERVAL_MINUTES
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SYNC_MODE
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import kotlinx.coroutines.flow.StateFlow

class CloudSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String?>
) : BaseSettingsRepository(prefs, activeBookIdFlow), CloudSettings {

    override val activeBookIdFlow: StateFlow<String?> = super.activeBookIdFlow
    override var activeBookId: String
        get() = super.activeBookId
        set(_) { /* Handled by SettingsRepositoryImpl */ }

    private val _isCloudSyncEnabled = BooleanSetting(KEY_CLOUD_SYNC_ENABLED, false)
    private val _syncIntervalMinutes = LongSetting(KEY_SYNC_INTERVAL_MINUTES, 15L)
    private val _syncModeBook = NonNullStringSetting(SettingsConstants.KEY_SYNC_MODE_BOOK, "TWO_WAY")
    private val _syncModeTts = NonNullStringSetting(SettingsConstants.KEY_SYNC_MODE_TTS, "TWO_WAY")
    private val _syncModeStats = NonNullStringSetting(SettingsConstants.KEY_SYNC_MODE_STATS, "RESTORE_ONLY")
    private val _lastSuccessfulSyncTime = LongSetting(KEY_LAST_SYNC_TIME, 0L)
    private val _elevenLabsApiKey = StringSetting(SettingsConstants.KEY_ELEVENLABS_API_KEY)
    private val _elevenLabsModel = NonNullStringSetting(SettingsConstants.KEY_ELEVENLABS_MODEL, "eleven_multilingual_v2")
    private val _elevenLabsStability = FloatSetting(SettingsConstants.KEY_ELEVENLABS_STABILITY, 0.5f)
    private val _elevenLabsSimilarityBoost = FloatSetting(SettingsConstants.KEY_ELEVENLABS_SIMILARITY_BOOST, 0.75f)
    private val _elevenLabsTtsLanguage = StringSetting(SettingsConstants.KEY_ELEVENLABS_TTS_LANGUAGE)
    private val _spotifyAccessToken = StringSetting(SettingsConstants.KEY_SPOTIFY_ACCESS_TOKEN)
    private val _spotifyRefreshToken = StringSetting(SettingsConstants.KEY_SPOTIFY_REFRESH_TOKEN)
    private val _spotifyTokenExpiresAt = LongSetting(SettingsConstants.KEY_SPOTIFY_TOKEN_EXPIRES_AT, 0L)
    private val _spotifyUserDisplayName = StringSetting(SettingsConstants.KEY_SPOTIFY_USER_DISPLAY_NAME)
    private val _googleDriveFolderId = StringSetting(KEY_GOOGLE_DRIVE_FOLDER_ID)
    private val _googleDriveFolderName = StringSetting(KEY_GOOGLE_DRIVE_FOLDER_NAME)
    private val _syncTargetType = NonNullStringSetting(SettingsConstants.KEY_SYNC_TARGET_TYPE, "DRIVE_API")
    private val _localFolderSafUri = StringSetting(SettingsConstants.KEY_LOCAL_FOLDER_SAF_URI)
    private val _localFolderSafName = StringSetting(SettingsConstants.KEY_LOCAL_FOLDER_SAF_NAME)
    private val _syncModeLogs = NonNullStringSetting(SettingsConstants.KEY_SYNC_MODE_LOGS, "OFF")
    private val _syncLogsIntervalHours = LongSetting(SettingsConstants.KEY_SYNC_LOGS_INTERVAL_HOURS, 12L)
    private val _lastLogsSyncTime = LongSetting(SettingsConstants.KEY_LAST_LOGS_SYNC_TIME, 0L)
    private val _lastUploadedLogHash = StringSetting(SettingsConstants.KEY_LAST_UPLOADED_LOG_HASH)

    init {
        migrateOldSyncMode()
        migrateLogSyncSettings()
    }

    override val isCloudSyncEnabledFlow = _isCloudSyncEnabled.flow
    override val syncIntervalMinutesFlow = _syncIntervalMinutes.flow
    override val syncModeBookFlow = _syncModeBook.flow
    override val syncModeTtsFlow = _syncModeTts.flow
    override val syncModeStatsFlow = _syncModeStats.flow
    override val lastSuccessfulSyncTimeFlow = _lastSuccessfulSyncTime.flow
    override val elevenLabsApiKeyFlow = _elevenLabsApiKey.flow
    override val elevenLabsModelFlow = _elevenLabsModel.flow
    override val elevenLabsStabilityFlow = _elevenLabsStability.flow
    override val elevenLabsSimilarityBoostFlow = _elevenLabsSimilarityBoost.flow
    override val elevenLabsTtsLanguageFlow = _elevenLabsTtsLanguage.flow
    override val spotifyAccessTokenFlow = _spotifyAccessToken.flow
    override val spotifyRefreshTokenFlow = _spotifyRefreshToken.flow
    override val spotifyTokenExpiresAtFlow = _spotifyTokenExpiresAt.flow
    override val spotifyUserDisplayNameFlow = _spotifyUserDisplayName.flow
    override val googleDriveFolderIdFlow = _googleDriveFolderId.flow
    override val googleDriveFolderNameFlow = _googleDriveFolderName.flow
    override val syncTargetTypeFlow = _syncTargetType.flow
    override val localFolderSafUriFlow = _localFolderSafUri.flow
    override val localFolderSafNameFlow = _localFolderSafName.flow
    override val syncModeLogsFlow = _syncModeLogs.flow
    override val syncLogsIntervalHoursFlow = _syncLogsIntervalHours.flow
    override val lastLogsSyncTimeFlow = _lastLogsSyncTime.flow
    override val lastUploadedLogHashFlow = _lastUploadedLogHash.flow

    override var isCloudSyncEnabled: Boolean by _isCloudSyncEnabled
    override var syncIntervalMinutes: Long by _syncIntervalMinutes
    override var syncModeBook: String by _syncModeBook
    override var syncModeTts: String by _syncModeTts
    override var syncModeStats: String by _syncModeStats
    override var lastSuccessfulSyncTime: Long by _lastSuccessfulSyncTime
    override var elevenLabsApiKey: String? by _elevenLabsApiKey
    override var elevenLabsModel: String by _elevenLabsModel
    override var elevenLabsStability: Float by _elevenLabsStability
    override var elevenLabsSimilarityBoost: Float by _elevenLabsSimilarityBoost
    override var elevenLabsTtsLanguage: String? by _elevenLabsTtsLanguage
    override var spotifyAccessToken: String? by _spotifyAccessToken
    override var spotifyRefreshToken: String? by _spotifyRefreshToken
    override var spotifyTokenExpiresAt: Long by _spotifyTokenExpiresAt
    override var spotifyUserDisplayName: String? by _spotifyUserDisplayName
    override var googleDriveFolderId: String? by _googleDriveFolderId
    override var googleDriveFolderName: String? by _googleDriveFolderName
    override var syncTargetType: String by _syncTargetType
    override var localFolderSafUri: String? by _localFolderSafUri
    override var localFolderSafName: String? by _localFolderSafName
    override var syncModeLogs: String by _syncModeLogs
    override var syncLogsIntervalHours: Long by _syncLogsIntervalHours
    override var lastLogsSyncTime: Long by _lastLogsSyncTime
    override var lastUploadedLogHash: String? by _lastUploadedLogHash


    override fun refresh() {
        migrateOldSyncMode()
        _isCloudSyncEnabled.refresh()
        _syncIntervalMinutes.refresh()
        _syncModeBook.refresh()
        _syncModeTts.refresh()
        _syncModeStats.refresh()
        _lastSuccessfulSyncTime.refresh()
        _elevenLabsApiKey.refresh()
        _elevenLabsModel.refresh()
        _elevenLabsStability.refresh()
        _elevenLabsSimilarityBoost.refresh()
        _elevenLabsTtsLanguage.refresh()
        _spotifyAccessToken.refresh()
        _spotifyRefreshToken.refresh()
        _spotifyTokenExpiresAt.refresh()
        _spotifyUserDisplayName.refresh()
        _googleDriveFolderId.refresh()
        _googleDriveFolderName.refresh()
        _syncTargetType.refresh()
        _localFolderSafUri.refresh()
        _localFolderSafName.refresh()
        _syncModeLogs.refresh()
        _syncLogsIntervalHours.refresh()
        _lastLogsSyncTime.refresh()
        _lastUploadedLogHash.refresh()
    }

    private fun migrateOldSyncMode() {
        val scopedOldKey = getScopedKey(KEY_SYNC_MODE)
        val scopedBookKey = getScopedKey(SettingsConstants.KEY_SYNC_MODE_BOOK)
        val scopedTtsKey = getScopedKey(SettingsConstants.KEY_SYNC_MODE_TTS)
        val scopedStatsKey = getScopedKey(SettingsConstants.KEY_SYNC_MODE_STATS)

        if (prefs.contains(scopedOldKey)) {
            val oldMode = prefs.getString(scopedOldKey, null)
            prefs.edit {
                if (oldMode != null) {
                    if (!prefs.contains(scopedBookKey)) {
                        putString(scopedBookKey, oldMode)
                    }
                    if (!prefs.contains(scopedTtsKey)) {
                        putString(scopedTtsKey, oldMode)
                    }
                    if (!prefs.contains(scopedStatsKey)) {
                        putString(scopedStatsKey, "RESTORE_ONLY")
                    }
                }
                remove(scopedOldKey)
            }
            _syncModeBook.refresh()
            _syncModeTts.refresh()
            _syncModeStats.refresh()
        }
    }

    private fun migrateLogSyncSettings() {
        val hasMigratedKey = "has_migrated_logs_sync"
        if (!prefs.getBoolean(hasMigratedKey, false)) {
            val isSyncEnabled = prefs.getBoolean(KEY_CLOUD_SYNC_ENABLED, false)
            prefs.edit {
                putBoolean(hasMigratedKey, true)
                if (isSyncEnabled) {
                    val scopedLogsKey = getScopedKey(SettingsConstants.KEY_SYNC_MODE_LOGS)
                    if (!prefs.contains(scopedLogsKey)) {
                        putString(scopedLogsKey, "BACKUP_ONLY")
                    }
                }
            }
            _syncModeLogs.refresh()
        }
    }
}
