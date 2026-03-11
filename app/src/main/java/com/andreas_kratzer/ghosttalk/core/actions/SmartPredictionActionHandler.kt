package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction

class SmartPredictionActionHandler(
    private val log: (String) -> Unit
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is SmartPredictionButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val smartAction = action as SmartPredictionButtonAction
        log("Smart Prediction button clicked (Rank: ${smartAction.rank}). Actual execution handled in ViewModel.")
        onFinish(executionId)
    }
}
