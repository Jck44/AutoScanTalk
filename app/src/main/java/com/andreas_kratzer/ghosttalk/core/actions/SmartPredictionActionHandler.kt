package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction

import javax.inject.Inject

class SmartPredictionActionHandler @Inject constructor(
    private val actionLogger: ActionLogger
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is SmartPredictionButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val smartAction = action as SmartPredictionButtonAction
        actionLogger.log("Smart Prediction button clicked (Rank: ${smartAction.rank}). Actual execution handled in ViewModel.")
        onFinish(executionId)
    }
}
