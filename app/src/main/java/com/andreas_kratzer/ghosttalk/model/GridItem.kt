package com.andreas_kratzer.ghosttalk.model

import com.andreas_kratzer.ghosttalk.ui.util.ListableItem

interface GridItem : ListableItem {
    val rows: Int
    val columns: Int
    val scanPattern: String?
    val rowNames: List<String>
    val buttonConfigs: List<ButtonConfig?>
}
