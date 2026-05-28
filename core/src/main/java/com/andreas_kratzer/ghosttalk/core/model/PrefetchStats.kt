package com.andreas_kratzer.ghosttalk.core.model

data class PrefetchStats(
    val totalButtons: Int,
    val uniqueStrings: Int,
    val duplicateStrings: Int,
    val totalWords: Int,
    val totalCharacters: Int,
    val alreadyCached: Int
)
