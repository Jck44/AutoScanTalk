package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction

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
        // action as FrequentActionButtonAction
        log("Häufigste Aktion (unaufgelöst) ignoriert")
        onFinish(executionId)
    }
}
