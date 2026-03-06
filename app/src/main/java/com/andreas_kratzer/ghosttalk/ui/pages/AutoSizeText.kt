package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var fontSize by remember { mutableStateOf(defaultFontSize) }
    var readyToDraw by remember { mutableStateOf(false) }

    Text(
        text = text,
        color = if (readyToDraw) color else Color.Transparent,
        textAlign = textAlign,
        maxLines = maxLines,
        fontSize = fontSize,
        overflow = TextOverflow.Ellipsis,
        softWrap = true,
        modifier = modifier,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.didOverflowHeight || textLayoutResult.didOverflowWidth) {
                val nextSize = fontSize * 0.9f
                // Stoppe Verkleinerung bei 9.sp, um Lesbarkeit auf Handys zu garantieren
                if (nextSize.value > 9f) {
                    fontSize = nextSize
                } else {
                    readyToDraw = true // Text is too long, we stop shrinking and let it truncate/ellipsis
                }
            } else {
                readyToDraw = true
            }
        }
    )
}
