package com.andreas_kratzer.ghosttalk.core.call

enum class CallAutoAction {
    NONE,
    ANSWER,
    REJECT
}

object CallAutoActionPolicy {

    fun determineAutoAction(actionStr: String?): CallAutoAction {
        if (actionStr == null) return CallAutoAction.NONE
        return try {
            CallAutoAction.valueOf(actionStr)
        } catch (e: IllegalArgumentException) {
            CallAutoAction.NONE
        }
    }

    fun shouldTriggerActiveScanLimit(cyclesCompleted: Int, limit: Int): Boolean {
        return limit > 0 && cyclesCompleted >= limit
    }
}
