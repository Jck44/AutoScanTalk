package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig

interface FeatureGuardProxy {
    fun isButtonVisible(config: ButtonConfig): Boolean
    fun isActionEnabled(action: ButtonAction): Boolean
}
