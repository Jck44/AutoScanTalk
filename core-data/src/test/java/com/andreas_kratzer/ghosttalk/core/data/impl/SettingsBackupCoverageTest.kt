package com.andreas_kratzer.ghosttalk.core.data.impl

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class SettingsBackupCoverageTest {

    private val ignoredProperties = setOf(
        "activeBookId",            // Handled separately as part of the book itself
        "activeBookIdFlow",        // Flow
        "lastSuccessfulSyncTime",  // Local state
        "lastSuccessfulSyncTimeFlow",
        "initialTemplatesCreated", // Internal state
        "isSetupCompleted",        // Onboarding state
        "showTestButtons",         // Dev setting
        "persistActionLogs",       // File path (local)
        "actionLogsStorage",       // File path (local)
        "syncLogsStorage",         // Local state (sync logs)
        "ttsAudioDeviceAddress",   // Hardware-specific
        "ttsAudioDeviceAddressFlow",
        "cuesAudioDeviceAddress",  // Hardware-specific
        "cuesAudioDeviceAddressFlow",
        "recordingAudioSource",     // Hardware-specific
        "recordingAudioSourceFlow",
        "hueBridgeIp",             // Security / Secret
        "hueBridgeIpFlow",
        "hueUsername",             // Security / Secret
        "hueUsernameFlow",
        "hueBridgeFingerprint",     // Security / Secret
        "hueBridgeFingerprintFlow",
        "securityPin",             // Deprecated (use Hash/Salt)
        "securityPinFlow",
        "forceSoftKeyboard",       // UI specific
        "forceSoftKeyboardFlow",
        "elevenLabsApiKey",         // Security / Secret
        "elevenLabsApiKeyFlow",
        "spotifyAccessToken",       // Security / Secret
        "spotifyAccessTokenFlow",
        "spotifyRefreshToken",      // Security / Secret
        "spotifyRefreshTokenFlow",
        "spotifyTokenExpiresAt",    // Local session state
        "spotifyTokenExpiresAtFlow",
        "spotifyUserDisplayName",   // Local session state
        "spotifyUserDisplayNameFlow",
        "geminiApiKey",             // Security / Secret
        "geminiApiKeyFlow",
        "useGeminiApiKey",          // Local auth setting
        "useGeminiApiKeyFlow",
        "backgroundLocationEnabled",
        "backgroundLocationInterval",
        "backgroundWeatherEnabled",
        "backgroundWeatherInterval",
        "syncModeBook",             // Device-specific sync setting
        "syncModeTts",              // Device-specific sync setting
        "syncModeStats",            // Device-specific sync setting
        "googleDriveFolderId",      // Device-specific folder target
        "googleDriveFolderName",    // Device-specific folder target name
        "statsRetentionDays",       // Local device maintenance preference
        "statsAggregationHours",    // Local device maintenance preference
        "syncTargetType",           // Device-specific sync setting (Drive vs SAF)
        "localFolderSafUri",        // Device-specific SAF folder target URI
        "localFolderSafName"        // Device-specific SAF folder target name
    )

    private val propertyMappings = mapOf(
        "holdingTimeMillis" to "holdingTimeSeconds"
    )

    @Test
    fun verifyAllSettingsAreCovered() {
        val settingsClass = SettingsRepository::class.java
        val importExportClass = ImportExportData::class.java
        
        val methods = settingsClass.methods
        val getters = methods.filter { isGetter(it) }
        
        val missingProperties = mutableListOf<String>()
        
        for (getter in getters) {
            val propertyName = getPropertyName(getter)
            
            // Skip flows and ignored properties
            if (propertyName.endsWith("Flow") || ignoredProperties.contains(propertyName)) {
                continue
            }
            
            // Check if there's a corresponding setter (to ensure it's a 'var')
            val hasSetter = methods.any { isSetterFor(it, propertyName) }
            if (!hasSetter) continue 
            
            // Check if ImportExportData has a field with this name (or a mapped name)
            val searchName = propertyMappings[propertyName] ?: propertyName
            val hasField = try {
                importExportClass.getDeclaredField(searchName)
                true
            } catch (_: NoSuchFieldException) {
                // Also check with 'is' prefix if boolean
                if (searchName.startsWith("is")) {
                    try {
                        importExportClass.getDeclaredField(searchName.replaceFirstChar { it.lowercase() })
                        true
                    } catch (_: NoSuchFieldException) {
                        false
                    }
                } else {
                    false
                }
            }
            
            if (!hasField) {
                missingProperties.add(propertyName)
            }
        }
        
        if (missingProperties.isNotEmpty()) {
            fail("The following settings are in SettingsRepository but missing from ImportExportData:\n" +
                 missingProperties.joinToString("\n") + 
                 "\n\nPlease add them to ImportExportData and update PageImportExportManager mapping.")
        }
    }

    private fun isGetter(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.parameterCount != 0) return false
        if (method.returnType == Void.TYPE) return false
        val name = method.name
        return name.startsWith("get") || name.startsWith("is")
    }

    private fun isSetterFor(method: Method, propertyName: String): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.parameterCount != 1) return false
        val name = method.name
        val capitalized = propertyName.replaceFirstChar { it.uppercase() }
        val withoutIs = capitalized.removePrefix("Is")
        val expectedName1 = "set$withoutIs"
        val expectedName2 = "set$capitalized"
        return name == expectedName1 || name == expectedName2
    }

    private fun getPropertyName(method: Method): String {
        val name = method.name
        return when {
            name.startsWith("get") -> name.substring(3).replaceFirstChar { it.lowercase() }
            name.startsWith("is") -> name // Keep 'is' for booleans
            else -> name
        }
    }
}
