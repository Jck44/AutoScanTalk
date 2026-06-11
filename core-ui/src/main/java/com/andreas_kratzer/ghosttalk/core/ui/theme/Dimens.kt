package com.andreas_kratzer.ghosttalk.core.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Dimensions(
    val paddingSmall: Dp = 4.dp,
    val paddingMedium: Dp = 8.dp,
    val paddingLarge: Dp = 16.dp,
    val paddingExtraLarge: Dp = 24.dp,
    val paddingDoubleExtraLarge: Dp = 32.dp,
    
    val gridSpacing: Dp = 8.dp,
    val cardElevation: Dp = 2.dp,
    val cardHeight: Dp = 100.dp,
    
    val iconSizeSmall: Dp = 24.dp,
    val iconSizeMedium: Dp = 32.dp,
    val iconSizeLarge: Dp = 40.dp,
    val logoSizeSmall: Dp = 48.dp,
    val logoSizeMedium: Dp = 64.dp,
    
    val buttonFontSize: TextUnit = 14.sp,
    val buttonAspectRatio: Float = 1.0f,
    val baseMaxButtonWidth: Dp = 180.dp,
    val minButtonWidth: Dp = 45.dp,
    val isTablet: Boolean = false,

    val screenPaddingHorizontal: Dp = 16.dp,
    val screenPaddingVertical: Dp = 16.dp,
    val sectionSpacing: Dp = 24.dp,
    val listItemSpacing: Dp = 8.dp,
    val minTouchTarget: Dp = 48.dp,
    val dialogCornerRadius: Dp = 28.dp
)

val LocalDimensions = compositionLocalOf { Dimensions() }
