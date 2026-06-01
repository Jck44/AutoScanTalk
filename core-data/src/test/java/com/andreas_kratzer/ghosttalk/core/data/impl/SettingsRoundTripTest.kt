package com.andreas_kratzer.ghosttalk.core.data.impl

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsMapper
import com.andreas_kratzer.ghosttalk.core.model.Book
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRoundTripTest {

    @Test
    fun `test settings round trip`() = runTest {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val bookRepository = mockk<BookRepository>(relaxed = true)
        val pageRepository = mockk<PageRepository>(relaxed = true)
        val logger = mockk<com.andreas_kratzer.ghosttalk.core.util.Logger>(relaxed = true)
        val context = mockk<android.content.Context>(relaxed = true)
        val sharedPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        val prefsEditor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        val authManager = mockk<com.andreas_kratzer.ghosttalk.core.cloud.AuthManager>(relaxed = true)
        
        every { authManager.userEmail } returns kotlinx.coroutines.flow.MutableStateFlow("test@example.com")
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.edit() } returns prefsEditor
        every { prefsEditor.putString(any(), any()) } returns prefsEditor
        every { prefsEditor.putLong(any(), any()) } returns prefsEditor
        every { prefsEditor.putBoolean(any(), any()) } returns prefsEditor
        every { prefsEditor.putInt(any(), any()) } returns prefsEditor

        val settingsMapper = SettingsMapper(settingsRepository, authManager)
        val actionMapper = ActionMapper()
        val buttonTemplateRepository = mockk<com.andreas_kratzer.ghosttalk.core.data.ButtonTemplateRepository>(relaxed = true)
        every { buttonTemplateRepository.getTemplates() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        val buttonUsageDao = mockk<com.andreas_kratzer.ghosttalk.core.database.ButtonUsageDao>(relaxed = true)
        val manager = PageImportExportManager(
            context = context,
            pageRepository = pageRepository,
            bookRepository = bookRepository,
            settingsRepository = settingsRepository,
            settingsMapper = settingsMapper,
            actionMapper = actionMapper,
            buttonTemplateRepository = buttonTemplateRepository,
            buttonUsageDao = buttonUsageDao,
            logger = logger
        )

        val bookId = "test-book"
        val originalBook = Book(id = bookId, name = "Test Book")
        coEvery { bookRepository.getBookById(any()) } returns originalBook
        coEvery { pageRepository.getPagesForBook(any()) } returns emptyList()
        coEvery { bookRepository.updateBook(any()) } returns Unit
        coEvery { pageRepository.deletePagesForBook(any()) } returns Unit

        // 1. Setup mock with distinct values
        val testValues = mutableMapOf<String, Any>()
        
        val mappedProperties = listOf(
            "autoStartScanning", "scanDelayMillis", "resumeScanningFromStart",
            "switchActivationKey", "volumeKeysActivate", "defaultScanPattern",
            "isSmartPredictionEnabled", "geminiRedoPrediction", "geminiTimeout",
            "isGeminiEnabled", "useLocalGenerativeAi",
            // Note: isCloudSyncEnabled, syncIntervalMinutes, syncMode are intentionally
            // excluded from import (device-specific cloud sync settings)
            "ttsLanguage", "ttsVoiceName",
            "smartPredictionDelay", "keepScreenOnUserMode", "userModeScreenBehavior",
            "themeMode", "securityPinTimeoutMinutes", "isPinRequiredForDeletion",
            "isBiometricEnabled", "isSecurityRequiredForEdit", "isSecurityRequiredForSettings",
            "startupBehavior", "weatherCacheTimeout", "securityPinHash",
            "securityPinSalt", "appLanguage", "isNotificationReadingEnabled",
            "maxCallDurationSeconds", "callDurationFeedbackIntervalSeconds", "outgoingCallIntro",
            "incomingCallIntro", "incomingCallScanLimitUserModeActive", "incomingCallAutoActionUserModeActive",
            "incomingCallDelayUserModeInactive", "incomingCallAutoActionUserModeInactive", "callAnnouncementAsCue",
            "autoEnableSpeakerphone", "simulateCallsEnabled", "hueCachedDevices"
        )

        for (propertyName in mappedProperties) {
            val getter = SettingsRepository::class.java.methods.find { isGetter(it) && getPropertyName(it) == propertyName }
            if (getter == null) continue

            val value: Any = when (getter.returnType) {
                Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> true
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> 12345L
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> 42
                String::class.java -> "test_$propertyName"
                else -> continue
            }
            testValues[propertyName] = value
            every { getter.invoke(settingsRepository) } returns value
            
            // Also mock get...ForBook(bookId)
            val forBookMethodName = "get${propertyName.replaceFirstChar { it.uppercase() }}ForBook"
            val forBookMethod = SettingsRepository::class.java.methods.find { it.name == forBookMethodName && it.parameterCount == 1 }
            if (forBookMethod != null) {
                every { forBookMethod.invoke(settingsRepository, bookId) } returns value
            }
        }
        
        every { settingsRepository.getHoldingTimeMillisForBook(bookId) } returns 1000L
        every { settingsRepository.holdingTimeMillis } returns 1000L

        // 2. Export
        val jsonString = manager.exportBookToJson(bookId)

        // 3. Import
        manager.importFromJson(jsonString, bookId, restoreSyncSettings = true)

        // 4. Verify setters
        for ((propertyName, expectedValue) in testValues) {
            val setter = SettingsRepository::class.java.methods.find { isSetterFor(it, propertyName) }
            if (setter != null) {
                try {
                    verify { setter.invoke(settingsRepository, expectedValue) }
                } catch (e: Throwable) {
                    throw AssertionError("Verification failed for $propertyName: Expected $expectedValue", e)
                }
            }
        }
        
        verify { settingsRepository.holdingTimeMillis = 1000L }
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
        val withoutIs = if (capitalized.startsWith("Is") && capitalized.length > 2 && capitalized[2].isUpperCase()) capitalized.substring(2) else capitalized
        
        val expectedName1 = "set$withoutIs"
        val expectedName2 = "set$capitalized"
        return name == expectedName1 || name == expectedName2
    }

    private fun getPropertyName(method: Method): String {
        val name = method.name
        return when {
            name.startsWith("get") -> name.substring(3).replaceFirstChar { it.lowercase() }
            name.startsWith("is") -> name
            else -> name
        }
    }
}
