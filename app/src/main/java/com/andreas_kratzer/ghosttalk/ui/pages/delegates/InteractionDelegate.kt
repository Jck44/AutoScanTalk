package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.ui.pages.ScanCoordinator
import javax.inject.Inject

class InteractionDelegate @Inject constructor(
    private val actionExecutor: ActionExecutor,
    private val ttsHelper: TextToSpeechHelper
) {
    lateinit var scanCoordinator: ScanCoordinator
    lateinit var pageManagementDelegate: PageManagementDelegate

    fun activateButtonAtIndex(index: Int, currentPage: Page?) {
        // Prevent interaction during execution (non-interruptible audio policy)
        if (actionExecutor.isExecuting.value) {
            Log.d("InteractionDelegate", "Ignoring button click at index $index as ActionExecutor is currently executing.")
            return
        }

        // Every valid switch press interrupts any currently playing notification TTS
        ttsHelper.stopNotificationTTS()
        
        val page = currentPage ?: return
        val buttonConfig = page.buttonConfigs.getOrNull(index) ?: return
        
        scanCoordinator.setFocusedIndex(index)
        
        actionExecutor.executeButtonAction(buttonConfig, bookId = pageManagementDelegate.activeBookId.value)
    }
}
