package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 4
) {
    val defaultFontSize = MaterialTheme.typography.titleLarge.fontSize
    val fontSizeState = remember { mutableStateOf(defaultFontSize) }
    val readyToDrawState = remember { mutableStateOf(false) }

    Text(
        text = text,
        color = if (readyToDrawState.value) color else Color.Transparent,
        textAlign = textAlign,
        maxLines = maxLines,
        fontSize = fontSizeState.value,
        overflow = TextOverflow.Ellipsis,
        softWrap = true,
        modifier = modifier,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.didOverflowHeight || textLayoutResult.didOverflowWidth) {
                val nextSize = fontSizeState.value * 0.9f
                // Stoppe Verkleinerung bei 9.sp, um Lesbarkeit auf Handys zu garantieren
                if (nextSize.value > 9f) {
                    fontSizeState.value = nextSize
                } else {
                    readyToDrawState.value = true // Text is too long, we stop shrinking and let it truncate/ellipsis
                }
            } else {
                readyToDrawState.value = true
            }
        }
    )
}
