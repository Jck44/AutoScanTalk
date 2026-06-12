package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface GenAiSettings {
    var isGeminiEnabled: Boolean
    val isGeminiEnabledFlow: StateFlow<Boolean>

    var isSmartPredictionEnabled: Boolean
    val isSmartPredictionEnabledFlow: StateFlow<Boolean>
    

    var showPageIdInLog: Boolean
    val showPageIdInLogFlow: StateFlow<Boolean>
    
    var geminiTimeout: Long
    val geminiTimeoutFlow: StateFlow<Long>
    
    var geminiRedoPrediction: Boolean
    val geminiRedoPredictionFlow: StateFlow<Boolean>

    var geminiApiKey: String?
    val geminiApiKeyFlow: StateFlow<String?>

    var useGeminiApiKey: Boolean
    val useGeminiApiKeyFlow: StateFlow<Boolean>

    var isGeminiVerified: Boolean
    val isGeminiVerifiedFlow: StateFlow<Boolean>

    var hasAcceptedPageSplitOptIn: Boolean
    val hasAcceptedPageSplitOptInFlow: StateFlow<Boolean>
}

