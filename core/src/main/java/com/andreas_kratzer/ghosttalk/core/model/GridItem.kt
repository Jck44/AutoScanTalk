package com.andreas_kratzer.ghosttalk.core.model

interface GridItem : ListableItem {
    val rows: Int
    val columns: Int
    val scanPattern: String?
    val rowNames: List<String>
    val buttonConfigs: List<ButtonConfig?>
}
