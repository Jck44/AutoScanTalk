package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.Context
import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.database.SettingsProfileDao
import com.andreas_kratzer.ghosttalk.core.database.toDomain
import com.andreas_kratzer.ghosttalk.core.database.toEntity
import com.andreas_kratzer.ghosttalk.core.model.ProfileConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SettingsMigrationManager(
    private val context: Context,
    private val settingsProfileDao: SettingsProfileDao,
    private val prefs: SharedPreferences
) {
    private val jsonSerializer = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    suspend fun migrateIfNeeded(activeBookId: String, activeProfileId: String) {
        try {
            ProfileBootstrapper(settingsProfileDao, context, com.andreas_kratzer.ghosttalk.core.util.AppLogger(context)).bootstrapIfNeeded()
            
            // Migrate legacy AES-CBC encrypted settings to new Android Keystore AES-GCM keys
            SecuritySettingsEncryptor.migratePreferences(prefs, context)
            
            // One-time migration of book-scoped scanning settings to global settings & active profile config
            if (!prefs.getBoolean("migration_scanning_settings_to_profile_done", false)) {
                val keysToMigrate = listOf(
                    Triple(SettingsConstants.KEY_AUTO_START_SCANNING, "boolean", true),
                    Triple(SettingsConstants.KEY_SCAN_DELAY_MILLIS, "long", 3000L),
                    Triple(SettingsConstants.KEY_RESUME_SCANNING_FROM_START, "boolean", true),
                    Triple(SettingsConstants.KEY_HOLDING_TIME_MILLIS, "long", 250L),
                    Triple(SettingsConstants.KEY_DEFAULT_SCAN_PATTERN, "string", "linear"),
                    Triple(SettingsConstants.KEY_LIMIT_SCAN_CYCLES, "boolean", false),
                    Triple(SettingsConstants.KEY_SCAN_CYCLE_LIMIT, "int", 2),
                    Triple(SettingsConstants.KEY_STATIC_ROW_ENABLED, "boolean", false),
                    Triple(SettingsConstants.KEY_LATE_CLICK_THRESHOLD_MILLIS, "long", 250L),
                    Triple(SettingsConstants.KEY_VOCAL_SWITCH_ENABLED, "boolean", false),
                    Triple(SettingsConstants.KEY_SWITCH_ACTIVATION_KEY, "string", "~3")
                )
                
                val editor = prefs.edit()
                var profileConfigUpdated = false
                
                val profile = settingsProfileDao.getProfileById(activeProfileId)?.toDomain(jsonSerializer)
                var currentConfig = profile?.config ?: ProfileConfig()
                
                for ((key, type, defaultValue) in keysToMigrate) {
                    var migratedValue: Any? = null
                    val activeScopedKey = "${activeBookId}_$key"
                    
                    // 1. Check active book
                    if (prefs.contains(activeScopedKey)) {
                        migratedValue = when (type) {
                            "boolean" -> prefs.getBoolean(activeScopedKey, defaultValue as Boolean)
                            "long" -> prefs.getLong(activeScopedKey, defaultValue as Long)
                            "int" -> prefs.getInt(activeScopedKey, defaultValue as Int)
                            "string" -> prefs.getString(activeScopedKey, defaultValue as String)
                            else -> null
                        }
                    }
                    
                    // 2. Fallback: check any other book-scoped key in preferences
                    if (migratedValue == null || migratedValue == defaultValue) {
                        val allPrefs = prefs.all
                        for ((prefKey, prefValue) in allPrefs) {
                            if (prefKey.endsWith("_$key") && prefValue != null && prefValue != defaultValue) {
                                migratedValue = prefValue
                                break
                            }
                        }
                    }
                    
                    // If we found a custom key value, write it globally and update config builder
                    if (migratedValue != null && migratedValue != defaultValue) {
                        when (type) {
                            "boolean" -> editor.putBoolean(key, migratedValue as Boolean)
                            "long" -> editor.putLong(key, migratedValue as Long)
                            "int" -> editor.putInt(key, migratedValue as Int)
                            "string" -> editor.putString(key, migratedValue as String)
                        }
                        
                        // Update our local config representation
                        currentConfig = when (key) {
                            SettingsConstants.KEY_AUTO_START_SCANNING -> currentConfig.copy(autoStartScanning = migratedValue as Boolean)
                            SettingsConstants.KEY_SCAN_DELAY_MILLIS -> currentConfig.copy(scanDelayMillis = migratedValue as Long)
                            SettingsConstants.KEY_RESUME_SCANNING_FROM_START -> currentConfig.copy(resumeScanningFromStart = migratedValue as Boolean)
                            SettingsConstants.KEY_HOLDING_TIME_MILLIS -> currentConfig.copy(holdingTimeMillis = migratedValue as Long)
                            SettingsConstants.KEY_DEFAULT_SCAN_PATTERN -> currentConfig.copy(defaultScanPattern = migratedValue as String)
                            SettingsConstants.KEY_LIMIT_SCAN_CYCLES -> currentConfig.copy(limitScanCycles = migratedValue as Boolean)
                            SettingsConstants.KEY_SCAN_CYCLE_LIMIT -> currentConfig.copy(scanCycleLimit = migratedValue as Int)
                            SettingsConstants.KEY_STATIC_ROW_ENABLED -> currentConfig.copy(staticRowEnabled = migratedValue as Boolean)
                            SettingsConstants.KEY_LATE_CLICK_THRESHOLD_MILLIS -> currentConfig.copy(lateClickThresholdMillis = migratedValue as Long)
                            SettingsConstants.KEY_VOCAL_SWITCH_ENABLED -> currentConfig.copy(vocalSwitchEnabled = migratedValue as Boolean)
                            SettingsConstants.KEY_SWITCH_ACTIVATION_KEY -> currentConfig.copy(switchActivationKey = migratedValue as String)
                            else -> currentConfig
                        }
                        profileConfigUpdated = true
                    }
                }
                
                editor.putBoolean("migration_scanning_settings_to_profile_done", true)
                editor.apply()
                
                if (profileConfigUpdated && profile != null) {
                    val updatedProfile = profile.copy(config = currentConfig, updatedAt = System.currentTimeMillis())
                    settingsProfileDao.updateProfile(updatedProfile.toEntity(jsonSerializer))
                }
            }
            
            // One-time migration of secrets from existing DB profiles to profile-scoped SharedPreferences
            val allProfiles = settingsProfileDao.getAllProfiles().map { it.toDomain(jsonSerializer) }
            prefs.edit().apply {
                for (p in allProfiles) {
                    val pId = p.id
                    val rawEntity = settingsProfileDao.getProfileById(pId)
                    if (rawEntity != null) {
                        try {
                            val jsonObj = Json.parseToJsonElement(rawEntity.configJson).jsonObject
                            if (!prefs.contains("profile_${pId}_elevenlabs_api_key")) {
                                val value = jsonObj["elevenLabsApiKey"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
                                putString("profile_${pId}_elevenlabs_api_key", value)
                            }
                            if (!prefs.contains("profile_${pId}_gemini_api_key")) {
                                val value = jsonObj["geminiApiKey"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
                                putString("profile_${pId}_gemini_api_key", value)
                            }
                            if (!prefs.contains("profile_${pId}_security_pin_hash")) {
                                val value = jsonObj["securityPinHash"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
                                putString("profile_${pId}_security_pin_hash", value)
                            }
                            if (!prefs.contains("profile_${pId}_security_pin_salt")) {
                                val value = jsonObj["securityPinSalt"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
                                putString("profile_${pId}_security_pin_salt", value)
                            }
                            if (!prefs.contains("profile_${pId}_hue_username")) {
                                val value = jsonObj["hueUsername"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content } ?: ""
                                putString("profile_${pId}_hue_username", value)
                            }
                            if (!prefs.contains("profile_${pId}_hue_bridge_fingerprint")) {
                                val value = jsonObj["hueBridgeFingerprint"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content } ?: ""
                                putString("profile_${pId}_hue_bridge_fingerprint", value)
                            }
                        } catch (_: Exception) {}
                    }
                }
                apply()
            }
        } catch (_: Exception) {
            // Non-fatal bootstrapper/migration error
        }
    }
}
