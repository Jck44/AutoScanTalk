package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction

class FrequentActionHandler(
    private val log: (String) -> Unit
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is FrequentActionButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        log("Häufige Aktion auflösen...")
        onFinish(executionId)
    }
}
