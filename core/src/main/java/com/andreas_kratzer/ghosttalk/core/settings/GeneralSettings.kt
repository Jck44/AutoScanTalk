package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface GeneralSettings {
    val themeModeFlow: StateFlow<String>
    val pageSortOrderFlow: StateFlow<String>
    val templateSortOrderFlow: StateFlow<String>
    val appLanguageFlow: StateFlow<String?>
    val defaultStartPageIdFlow: StateFlow<String?>
    val favoriteBookIdFlow: StateFlow<String?>
    val startupBehaviorFlow: StateFlow<String>

    var themeMode: String
    var pageSortOrder: String
    var templateSortOrder: String
    var appLanguage: String?
    var defaultStartPageId: String?
    var favoriteBookId: String?
    var startupBehavior: String
}
