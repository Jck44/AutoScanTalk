package com.andreas_kratzer.ghosttalk.core.data

import com.andreas_kratzer.ghosttalk.core.KeyEventSettings
import com.andreas_kratzer.ghosttalk.core.SecuritySettings
import com.andreas_kratzer.ghosttalk.core.actions.ControlDeviceSettings
import com.andreas_kratzer.ghosttalk.core.actions.SpeechSettings
import com.andreas_kratzer.ghosttalk.core.audio.AudioSettings
import com.andreas_kratzer.ghosttalk.core.settings.AdvancedSettings
import com.andreas_kratzer.ghosttalk.core.settings.CallSettings
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import com.andreas_kratzer.ghosttalk.core.settings.DatabaseSettings
import com.andreas_kratzer.ghosttalk.core.settings.FeatureSettings
import com.andreas_kratzer.ghosttalk.core.settings.GenAiSettings
import com.andreas_kratzer.ghosttalk.core.settings.GeneralSettings
import com.andreas_kratzer.ghosttalk.core.settings.ImportExportSettings
import com.andreas_kratzer.ghosttalk.core.settings.NotificationSettings
import com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings
import com.andreas_kratzer.ghosttalk.core.settings.SmartHomeSettings
import com.andreas_kratzer.ghosttalk.core.settings.TtsSettings
import com.andreas_kratzer.ghosttalk.core.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.andreas_kratzer.ghosttalk.core.model.SettingsProfile

/**
 * A facade interface that combines all specialized settings interfaces.
 * This maintains compatibility with the existing application architecture
 * while allowing for a modular implementation behind the scenes.
 */
interface SettingsRepository : SecuritySettings, KeyEventSettings, AudioSettings, 
    ControlDeviceSettings, SpeechSettings, GenAiSettings, DatabaseSettings, 
    TtsSettings, ImportExportSettings, CloudSettings, ScanningSettings, FeatureSettings,
    GeneralSettings, UserSettings, AdvancedSettings, NotificationSettings, SmartHomeSettings,
    CallSettings {
    
    override var activeBookId: String
    override val activeBookIdFlow: StateFlow<String?>
    
    // Audio Device Caching
    override fun getDeviceName(persistentId: String): String?
    override fun saveDeviceName(persistentId: String, name: String)
    override fun cleanupDeviceCache(keepPersistentIds: Set<String>)
    override fun getCachedDevices(): Map<String, String>
    
    /**
     * Resets all settings to their default values.
     */
    fun resetToDefaults()

    /**
     * Refreshes all internal state flows from the current SharedPreferences values.
     * Useful after direct manipulation of SharedPreferences (e.g., during import).
     */
    fun refresh()

    // --- Book-specific settings access (bypassing activeBookId) ---
    fun getDefaultStartPageIdForBook(bookId: String): String?
    fun getPageSortOrderForBook(bookId: String): String
    fun getTemplateSortOrderForBook(bookId: String): String
    
    // Scanning settings for specific book
    fun getAutoStartScanningForBook(bookId: String): Boolean
    fun getScanDelayMillisForBook(bookId: String): Long
    fun getResumeScanningFromStartForBook(bookId: String): Boolean
    fun getHoldingTimeMillisForBook(bookId: String): Long
    fun getSwitchActivationKeyForBook(bookId: String): String
    fun getVolumeKeysActivateForBook(bookId: String): Boolean
    fun getDefaultScanPatternForBook(bookId: String): String
    fun getLimitScanCyclesForBook(bookId: String): Boolean
    fun getScanCycleLimitForBook(bookId: String): Int
    fun getStaticRowEnabledForBook(bookId: String): Boolean
    fun getStaticRowScanPatternForBook(bookId: String): String
    fun getLateClickThresholdMillisForBook(bookId: String): Long
    
    // Advanced settings for specific book
    fun getSmartPredictionDelayForBook(bookId: String): Long
    fun getIsSmartPredictionEnabledForBook(bookId: String): Boolean
    fun getActionLogLimitForBook(bookId: String): Int
    fun getLogIgnoredActionsForBook(bookId: String): Boolean
    fun getLogStopActionsForBook(bookId: String): Boolean

    fun updateConfigLastModified(bookId: String)
    fun getConfigLastModified(bookId: String): Long

    // --- Profile Management ---
    fun getAllProfilesFlow(): Flow<List<SettingsProfile>>
    suspend fun getAllProfiles(): List<SettingsProfile>
    suspend fun getProfileById(id: String): SettingsProfile?
    suspend fun insertProfile(profile: SettingsProfile)
    suspend fun updateProfile(profile: SettingsProfile)
    suspend fun deleteProfile(profile: SettingsProfile)
    
    val activeProfileIdFlow: StateFlow<String>
    var activeProfileId: String
    
    suspend fun loadProfile(profileId: String)

    val isCaregiverDeviceFlow: StateFlow<Boolean>
    var isCaregiverDevice: Boolean
}
