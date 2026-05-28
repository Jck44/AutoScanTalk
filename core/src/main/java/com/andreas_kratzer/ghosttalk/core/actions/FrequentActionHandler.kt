package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction

import javax.inject.Inject

class FrequentActionHandler @Inject constructor(
    private val actionLogger: ActionLogger
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is FrequentActionButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        actionLogger.log("Häufige Aktion auflösen...")
        onFinish(executionId)
    }
}
