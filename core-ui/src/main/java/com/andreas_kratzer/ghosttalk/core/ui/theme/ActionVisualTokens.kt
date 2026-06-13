package com.andreas_kratzer.ghosttalk.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.MarkAccidentalButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateBackButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction

/**
 * Shared visual tokens for different button action categories,
 * providing unified representation across grid buttons, graph nodes, tree items, and drag chips.
 */
object ActionVisualTokens {

    /**
     * Resolves the badge background and text color for a given [ButtonAction].
     */
    fun getColors(action: ButtonAction?, isDark: Boolean): Pair<Color, Color> {
        if (action == null) {
            return if (isDark) FrequentActionBadgeBgDark to FrequentActionBadgeTextDark
            else FrequentActionBadgeBgLight to FrequentActionBadgeTextLight
        }
        return when (action) {
            is SpeakTextButtonAction -> {
                if (isDark) SpeakTextBadgeBgDark to SpeakTextBadgeTextDark
                else SpeakTextBadgeBgLight to SpeakTextBadgeTextLight
            }
            is NavigateToPageButtonAction, is NavigateBackButtonAction, is NavigateToStartPageButtonAction -> {
                if (isDark) NavigateBadgeBgDark to NavigateBadgeTextDark
                else NavigateBadgeBgLight to NavigateBadgeTextLight
            }
            is SmartHomeButtonAction -> {
                if (isDark) SmartHomeBadgeBgDark to SmartHomeBadgeTextDark
                else SmartHomeBadgeBgLight to SmartHomeBadgeTextLight
            }
            is GeminiButtonAction, is GeminiSearchButtonAction, is GeminiNanoButtonAction, is GeminiVisionButtonAction -> {
                if (isDark) GeminiBadgeBgDark to GeminiBadgeTextDark
                else GeminiBadgeBgLight to GeminiBadgeTextLight
            }
            is ControlDeviceButtonAction -> {
                if (isDark) ControlDeviceBadgeBgDark to ControlDeviceBadgeTextDark
                else ControlDeviceBadgeBgLight to ControlDeviceBadgeTextLight
            }
            is WeatherButtonAction -> {
                if (isDark) WeatherBadgeBgDark to WeatherBadgeTextDark
                else WeatherBadgeBgLight to WeatherBadgeTextLight
            }
            is FrequentActionButtonAction, is SmartPredictionButtonAction, is PreviousActionButtonAction, is MarkAccidentalButtonAction -> {
                if (isDark) FrequentActionBadgeBgDark to FrequentActionBadgeTextDark
                else FrequentActionBadgeBgLight to FrequentActionBadgeTextLight
            }
            is PlayMediaButtonAction -> {
                if (isDark) PlayMediaBadgeBgDark to PlayMediaBadgeTextDark
                else PlayMediaBadgeBgLight to PlayMediaBadgeTextLight
            }
        }
    }
}
