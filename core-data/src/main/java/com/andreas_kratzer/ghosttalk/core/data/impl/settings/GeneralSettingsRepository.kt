package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_APP_LANGUAGE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_DEFAULT_START_PAGE_ID
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_FAVORITE_BOOK_ID
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_FORCE_SOFT_KEYBOARD
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_IS_SETUP_COMPLETED
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_PAGE_SORT_ORDER
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_STARTUP_BEHAVIOR
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SYNC_LOGS_STORAGE
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
    private val _forceSoftKeyboard = BooleanSetting(KEY_FORCE_SOFT_KEYBOARD, default = true, isScoped = false)
    private val _syncLogsStorage = StringSetting(KEY_SYNC_LOGS_STORAGE, isScoped = false)
    private val _isSetupCompleted = BooleanSetting(KEY_IS_SETUP_COMPLETED, default = false, isScoped = false)
    private val _isCaregiverDevice = BooleanSetting("is_caregiver_device", default = false, isScoped = false)
    private val _activeProfileId = StringSetting("local_active_profile_id", default = "profile-default", isScoped = false)

    val themeModeFlow = _themeMode.flow
    val pageSortOrderFlow = _pageSortOrder.flow
    val templateSortOrderFlow = _templateSortOrder.flow
    val syncLogsStorageFlow = _syncLogsStorage.flow
    val appLanguageFlow = _appLanguage.flow
    val defaultStartPageIdFlow = _defaultStartPageId.flow
    val favoriteBookIdFlow = _favoriteBookId.flow
    val startupBehaviorFlow = _startupBehavior.flow
    val forceSoftKeyboardFlow = _forceSoftKeyboard.flow
    val isSetupCompletedFlow = _isSetupCompleted.flow
    val isCaregiverDeviceFlow = _isCaregiverDevice.flow
    val activeProfileIdFlow = _activeProfileId.flow

    var themeMode: String by _themeMode
    var pageSortOrder: String by _pageSortOrder
    var templateSortOrder: String by _templateSortOrder
    var appLanguage: String? by _appLanguage
    var defaultStartPageId: String? by _defaultStartPageId
    var favoriteBookId: String? by _favoriteBookId
    var startupBehavior: String by _startupBehavior
    var forceSoftKeyboard: Boolean by _forceSoftKeyboard
    var syncLogsStorage: String? by _syncLogsStorage
    var isSetupCompleted: Boolean by _isSetupCompleted
    var isCaregiverDevice: Boolean by _isCaregiverDevice
    var activeProfileId: String
        get() = _activeProfileId.value ?: "profile-default"
        set(value) { _activeProfileId.value = value }


    override fun refresh() {
        _themeMode.refresh()
        _pageSortOrder.refresh()
        _templateSortOrder.refresh()
        _appLanguage.refresh()
        _defaultStartPageId.refresh()
        _favoriteBookId.refresh()
        _startupBehavior.refresh()
        _forceSoftKeyboard.refresh()
        _syncLogsStorage.refresh()
        _isSetupCompleted.refresh()
        _isCaregiverDevice.refresh()
        _activeProfileId.refresh()
    }
}
