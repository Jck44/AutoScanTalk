package com.andreas_kratzer.ghosttalk.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal for the global "User Mode" state.
 * When true, the app is in "Use" mode (for the end user).
 * When false, the app is in "Edit" mode.
 */
val LocalIsUserModeActive = staticCompositionLocalOf { false }

/**
 * CompositionLocal for the currently active Book ID.
 */
val LocalActiveBookId = staticCompositionLocalOf<String?> { null }

/**
 * CompositionLocal for the currently active Page ID.
 */
val LocalCurrentPageId = staticCompositionLocalOf<String?> { null }
