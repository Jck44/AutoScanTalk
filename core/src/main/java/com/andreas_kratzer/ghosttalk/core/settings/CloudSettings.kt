package com.andreas_kratzer.ghosttalk.core.settings

import com.andreas_kratzer.ghosttalk.core.model.CloudAuthType
import kotlinx.coroutines.flow.StateFlow

interface CloudSettings {
    var isCloudSyncEnabled: Boolean
    val isCloudSyncEnabledFlow: StateFlow<Boolean>
    var syncIntervalMinutes: Long
    val syncIntervalMinutesFlow: StateFlow<Long>
    var syncModeBook: String
    val syncModeBookFlow: StateFlow<String>
    var syncModeTts: String
    val syncModeTtsFlow: StateFlow<String>
    var syncModeStats: String
    val syncModeStatsFlow: StateFlow<String>
    var syncModeSettings: String
    val syncModeSettingsFlow: StateFlow<String>
    var lastSuccessfulSyncTime: Long
    val lastSuccessfulSyncTimeFlow: StateFlow<Long>
    var activeBookId: String
    val activeBookIdFlow: StateFlow<String?>

    var elevenLabsApiKey: String?
    val elevenLabsApiKeyFlow: StateFlow<String?>
    var elevenLabsModel: String
    val elevenLabsModelFlow: StateFlow<String>

    var elevenLabsStability: Float
    val elevenLabsStabilityFlow: StateFlow<Float>
    var elevenLabsSimilarityBoost: Float
    val elevenLabsSimilarityBoostFlow: StateFlow<Float>

    var elevenLabsTtsLanguage: String?
    val elevenLabsTtsLanguageFlow: StateFlow<String?>

    var spotifyAccessToken: String?
    val spotifyAccessTokenFlow: StateFlow<String?>

    var spotifyRefreshToken: String?
    val spotifyRefreshTokenFlow: StateFlow<String?>

    var spotifyTokenExpiresAt: Long
    val spotifyTokenExpiresAtFlow: StateFlow<Long>

    var spotifyUserDisplayName: String?
    val spotifyUserDisplayNameFlow: StateFlow<String?>

    var googleDriveFolderId: String?
    val googleDriveFolderIdFlow: StateFlow<String?>

    var googleDriveFolderName: String?
    val googleDriveFolderNameFlow: StateFlow<String?>

    var syncTargetType: String
    val syncTargetTypeFlow: StateFlow<String>

    var localFolderSafUri: String?
    val localFolderSafUriFlow: StateFlow<String?>

    var localFolderSafName: String?
    val localFolderSafNameFlow: StateFlow<String?>

    var syncModeLogs: String
    val syncModeLogsFlow: StateFlow<String>

    var syncLogsIntervalHours: Long
    val syncLogsIntervalHoursFlow: StateFlow<Long>

    var lastLogsSyncTime: Long
    val lastLogsSyncTimeFlow: StateFlow<Long>

    var lastUploadedLogHash: String?
    val lastUploadedLogHashFlow: StateFlow<String?>

    var googleAuthType: CloudAuthType
    val googleAuthTypeFlow: StateFlow<CloudAuthType>

    var googleAccessToken: String?
    val googleAccessTokenFlow: StateFlow<String?>

    var googleRefreshToken: String?
    val googleRefreshTokenFlow: StateFlow<String?>

    var googleTokenExpiresAt: Long
    val googleTokenExpiresAtFlow: StateFlow<Long>

    var googleUserEmail: String?
    val googleUserEmailFlow: StateFlow<String?>
}


