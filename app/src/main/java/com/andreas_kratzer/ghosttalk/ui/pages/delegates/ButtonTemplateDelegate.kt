package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import com.andreas_kratzer.ghosttalk.core.data.ButtonTemplateRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

class ButtonTemplateDelegate @Inject constructor(
    private val buttonTemplateRepository: ButtonTemplateRepository
) {
    private lateinit var scope: CoroutineScope

    lateinit var buttonTemplates: StateFlow<List<ButtonTemplate>>

    fun init(coroutineScope: CoroutineScope) {
        this.scope = coroutineScope
        buttonTemplates = buttonTemplateRepository.getTemplates()
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
        scope.launch {
            buttonTemplateRepository.ensureBuiltInTemplates()
        }
    }

    fun saveButtonAsTemplate(name: String, config: ButtonConfig) {
        scope.launch {
            buttonTemplateRepository.saveTemplate(
                ButtonTemplate(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    buttonConfig = config.copy(id = java.util.UUID.randomUUID().toString()),
                    isBuiltIn = false
                )
            )
        }
    }

    fun deleteButtonTemplate(template: ButtonTemplate) {
        scope.launch {
            buttonTemplateRepository.deleteTemplate(template)
        }
    }

    fun updateButtonTemplate(template: ButtonTemplate) {
        scope.launch {
            buttonTemplateRepository.saveTemplate(template)
        }
    }

    fun updateButtonTemplatesOrder(templates: List<ButtonTemplate>) {
        scope.launch {
            buttonTemplateRepository.updateTemplateOrder(templates)
        }
    }
}
