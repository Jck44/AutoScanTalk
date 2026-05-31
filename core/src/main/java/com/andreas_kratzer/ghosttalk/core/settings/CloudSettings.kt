package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface CloudSettings {
    var isCloudSyncEnabled: Boolean
    val isCloudSyncEnabledFlow: StateFlow<Boolean>
    var syncIntervalMinutes: Long
    val syncIntervalMinutesFlow: StateFlow<Long>
    var syncMode: String
    val syncModeFlow: StateFlow<String>
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
}

