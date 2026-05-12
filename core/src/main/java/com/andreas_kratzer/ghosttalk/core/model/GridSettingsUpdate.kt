package com.andreas_kratzer.ghosttalk.core.model

class OptionalProperty<out T>(val value: T)

data class GridSettingsUpdate(
    val name: String? = null,
    val scanPattern: OptionalProperty<String?>? = null,
    val rowNames: List<String>? = null,
    val rows: Int? = null,
    val columns: Int? = null
)
