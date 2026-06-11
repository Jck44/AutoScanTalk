package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveBookIdManager @Inject constructor(
    private val prefs: SharedPreferences
) {
    private val _activeBookIdFlow = MutableStateFlow(
        prefs.getString(SettingsConstants.KEY_ACTIVE_BOOK_ID, "book-default") ?: "book-default"
    )
    val activeBookIdFlow: StateFlow<String?> = _activeBookIdFlow.asStateFlow()

    var activeBookId: String
        get() = _activeBookIdFlow.value
        set(value) {
            if (_activeBookIdFlow.value != value) {
                _activeBookIdFlow.value = value
                prefs.edit().putString(SettingsConstants.KEY_ACTIVE_BOOK_ID, value).apply()
            }
        }
}
