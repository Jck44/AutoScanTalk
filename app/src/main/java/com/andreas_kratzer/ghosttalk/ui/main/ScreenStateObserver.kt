package com.andreas_kratzer.ghosttalk.ui.main

import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.andreas_kratzer.ghosttalk.core.call.CallState
import com.andreas_kratzer.ghosttalk.ui.pages.CallViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import kotlinx.coroutines.launch

class ScreenStateObserver(
    private val activity: AppCompatActivity,
    private val pageViewModel: PageViewModel,
    private val callViewModel: CallViewModel
) {
    fun startObserving() {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                pageViewModel.screenState.collect { state ->
                    if (state.keepScreenOn) {
                        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }

                    val params = activity.window.attributes
                    params.screenBrightness = state.dimAmount ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    activity.window.attributes = params
                }
            }
        }

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                callViewModel.callState.collect { callState ->
                    val isInCall = callState != CallState.NONE
                    activity.setShowWhenLocked(isInCall)
                    activity.setTurnScreenOn(isInCall)
                }
            }
        }
    }
}
