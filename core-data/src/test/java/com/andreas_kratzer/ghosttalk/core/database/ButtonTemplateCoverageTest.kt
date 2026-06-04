package com.andreas_kratzer.ghosttalk.core.database

import com.andreas_kratzer.ghosttalk.core.data.impl.ButtonTemplateRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonTemplateCoverageTest {

    @Test
    fun testEnsureBuiltInTemplates_insertsOnlyMissing() = runTest {
        val dao = mockk<ButtonTemplateDao>(relaxed = true)
        val repository = ButtonTemplateRepositoryImpl(dao)

        val existingEntity = ButtonTemplateEntity(
            id = "builtin_speak_text",
            name = "Hallo sprechen",
            isBuiltIn = true,
            buttonConfig = mockk(relaxed = true),
            orderIndex = 0
        )
        coEvery { dao.getAllTemplates() } returns listOf(existingEntity)

        repository.ensureBuiltInTemplates()

        val slot = slot<List<ButtonTemplateEntity>>()
        coVerify { dao.insertTemplates(capture(slot)) }

        val inserted = slot.captured
        assertTrue(inserted.none { it.id == "builtin_speak_text" })
        assertTrue(inserted.any { it.id == "builtin_navigate_page" })
    }

    @Test
    fun testBuiltInTemplatesCoverage() {
        val repository = ButtonTemplateRepositoryImpl(mockk())
        val templates = repository.generateBuiltInTemplatesList()

        // 1. Assert coverages of all ButtonAction subclasses
        val sealedSubclasses = ButtonAction::class.sealedSubclasses
        assertTrue("Sealed subclasses of ButtonAction should not be empty", sealedSubclasses.isNotEmpty())

        for (subclass in sealedSubclasses) {
            if (subclass == ControlDeviceButtonAction::class) {
                // Assert that for every DeviceActionType there is a template
                for (deviceActionType in DeviceActionType.entries) {
                    val hasTemplate = templates.any { template ->
                        val action = template.buttonConfig.buttonAction
                        action is ControlDeviceButtonAction && action.actionType == deviceActionType
                    }
                    assertTrue(
                        "Missing built-in template for ControlDeviceButtonAction with type: $deviceActionType",
                        hasTemplate
                    )
                }
            } else {
                // Assert that there is at least one template for this non-device action subclass
                val hasTemplate = templates.any { template ->
                    template.buttonConfig.buttonAction::class == subclass
                }
                assertTrue(
                    "Missing built-in template for ButtonAction subclass: ${subclass.simpleName}",
                    hasTemplate
                )
            }
        }
    }
}
