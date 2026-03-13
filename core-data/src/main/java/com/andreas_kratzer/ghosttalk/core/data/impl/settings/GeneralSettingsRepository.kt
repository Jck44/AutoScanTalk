package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_APP_LANGUAGE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_DEFAULT_START_PAGE_ID
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_FAVORITE_BOOK_ID
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_PAGE_SORT_ORDER
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_STARTUP_BEHAVIOR
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_TEMPLATE_SORT_ORDER
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_THEME_MODE
import kotlinx.coroutines.flow.StateFlow

class GeneralSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String?>
) : BaseSettingsRepository(prefs, activeBookIdFlow) {

    private val _themeMode = NonNullStringSetting(KEY_THEME_MODE, "LIGHT", isScoped = false)
    private val _pageSortOrder = NonNullStringSetting(KEY_PAGE_SORT_ORDER, "MANUAL")
    private val _templateSortOrder = NonNullStringSetting(KEY_TEMPLATE_SORT_ORDER, "MANUAL")
    private val _appLanguage = StringSetting(KEY_APP_LANGUAGE, isScoped = false)
    private val _defaultStartPageId = StringSetting(KEY_DEFAULT_START_PAGE_ID)
    private val _favoriteBookId = StringSetting(KEY_FAVORITE_BOOK_ID, isScoped = false)
    private val _startupBehavior = NonNullStringSetting(KEY_STARTUP_BEHAVIOR, "BOOK_SELECTION", isScoped = false)

    val themeModeFlow = _themeMode.flow
    val pageSortOrderFlow = _pageSortOrder.flow
    val templateSortOrderFlow = _templateSortOrder.flow
    val appLanguageFlow = _appLanguage.flow
    val defaultStartPageIdFlow = _defaultStartPageId.flow
    val favoriteBookIdFlow = _favoriteBookId.flow
    val startupBehaviorFlow = _startupBehavior.flow

    var themeMode: String
        get() = _themeMode.value
        set(value) { _themeMode.value = value }

    var pageSortOrder: String
        get() = _pageSortOrder.value
        set(value) { _pageSortOrder.value = value }

    var templateSortOrder: String
        get() = _templateSortOrder.value
        set(value) { _templateSortOrder.value = value }

    var appLanguage: String?
        get() = _appLanguage.value
        set(value) { _appLanguage.value = value }

    var defaultStartPageId: String?
        get() = _defaultStartPageId.value
        set(value) { _defaultStartPageId.value = value }

    var favoriteBookId: String?
        get() = _favoriteBookId.value
        set(value) { _favoriteBookId.value = value }

    var startupBehavior: String
        get() = _startupBehavior.value
        set(value) { _startupBehavior.value = value }

    override fun refresh() {
        _themeMode.refresh()
        _pageSortOrder.refresh()
        _templateSortOrder.refresh()
        _appLanguage.refresh()
        _defaultStartPageId.refresh()
        _favoriteBookId.refresh()
        _startupBehavior.refresh()
    }
}
