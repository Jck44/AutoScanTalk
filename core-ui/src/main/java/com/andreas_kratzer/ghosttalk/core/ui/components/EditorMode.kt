package com.andreas_kratzer.ghosttalk.core.ui.components

enum class EditorMode(val route: String) {
    RASTER("raster"),
    STRUKTUR("struktur");

    companion object {
        fun fromRoute(s: String?): EditorMode {
            return entries.find { it.route.equals(s, ignoreCase = true) } ?: RASTER
        }
    }
}
