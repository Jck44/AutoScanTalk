package com.andreas_kratzer.ghosttalk.ui.pages.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class TtsFieldPlaybackState(
    private val fieldName: String,
    private val isElevenLabs: Boolean,
    private val isTextCached: ((String) -> Boolean)?,
    private val onPrefetchText: ((String, () -> Unit) -> Unit)?,
    private val onStopTts: (() -> Unit)?,
    private val onPlayTts: ((String, () -> Unit) -> Unit)?,
    private val getPlayingField: () -> String?,
    private val setPlayingField: (String?) -> Unit
) {
    var isPrefetching by mutableStateOf(false)
    var isCached by mutableStateOf(false)

    fun updateCachedState(text: String) {
        isCached = if (isElevenLabs && isTextCached != null && text.isNotBlank()) {
            isTextCached.invoke(text)
        } else {
            false
        }
    }

    fun handlePlayClick(text: String) {
        if (getPlayingField() == fieldName) {
            onStopTts?.invoke()
            setPlayingField(null)
        } else {
            onStopTts?.invoke()
            if (isElevenLabs && !isCached && text.isNotBlank() && onPrefetchText != null) {
                isPrefetching = true
                onPrefetchText.invoke(text) {
                    isPrefetching = false
                    isCached = isTextCached?.invoke(text) ?: false
                    setPlayingField(fieldName)
                    onPlayTts?.invoke(text) {
                        if (getPlayingField() == fieldName) {
                            setPlayingField(null)
                        }
                    }
                }
            } else {
                setPlayingField(fieldName)
                onPlayTts?.invoke(text) {
                    if (getPlayingField() == fieldName) {
                        setPlayingField(null)
                    }
                }
            }
        }
    }

    fun handleFocusLost(text: String, onAutoSave: () -> Unit) {
        onAutoSave()
        if (isElevenLabs && text.isNotBlank() && isTextCached?.invoke(text) == false && onPrefetchText != null) {
            isPrefetching = true
            onPrefetchText.invoke(text) {
                isPrefetching = false
                isCached = isTextCached(text)
            }
        }
    }
}

@Composable
fun rememberTtsFieldPlayback(
    fieldName: String,
    isElevenLabs: Boolean,
    isTextCached: ((String) -> Boolean)?,
    onPrefetchText: ((String, () -> Unit) -> Unit)?,
    onStopTts: (() -> Unit)?,
    onPlayTts: ((String, () -> Unit) -> Unit)?,
    getPlayingField: () -> String?,
    setPlayingField: (String?) -> Unit
): TtsFieldPlaybackState {
    return remember(fieldName) {
        TtsFieldPlaybackState(
            fieldName = fieldName,
            isElevenLabs = isElevenLabs,
            isTextCached = isTextCached,
            onPrefetchText = onPrefetchText,
            onStopTts = onStopTts,
            onPlayTts = onPlayTts,
            getPlayingField = getPlayingField,
            setPlayingField = setPlayingField
        )
    }
}
