package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig

interface ActionHandler {
    fun canHandle(action: ButtonAction): Boolean 
    fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    )
}
