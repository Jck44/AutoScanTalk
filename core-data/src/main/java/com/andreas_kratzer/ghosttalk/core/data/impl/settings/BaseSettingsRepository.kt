package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BaseSettingsRepository(
    protected val prefs: SharedPreferences,
    open val activeBookIdFlow: StateFlow<String?>
) {
    var changeListener: ((String) -> Unit)? = null

    open val activeBookId: String get() = activeBookIdFlow.value ?: "book-default"

    protected fun getScopedKey(key: String): String = "${activeBookId}_$key"

    protected fun getStringScoped(key: String, defaultValue: String? = null): String? {
        val scopedKey = getScopedKey(key)
        if (prefs.contains(scopedKey)) {
            return prefs.getString(scopedKey, defaultValue)
        }
        return prefs.getString(key, defaultValue)
    }

    protected fun getBooleanScoped(key: String, defaultValue: Boolean): Boolean {
        val scopedKey = getScopedKey(key)
        if (prefs.contains(scopedKey)) {
            return prefs.getBoolean(scopedKey, defaultValue)
        }
        return prefs.getBoolean(key, defaultValue)
    }

    protected fun getLongScoped(key: String, defaultValue: Long): Long {
        val scopedKey = getScopedKey(key)
        if (prefs.contains(scopedKey)) {
            return prefs.getLong(scopedKey, defaultValue)
        }
        return prefs.getLong(key, defaultValue)
    }

    protected fun getIntScoped(key: String, defaultValue: Int): Int {
        val scopedKey = getScopedKey(key)
        if (prefs.contains(scopedKey)) {
            return prefs.getInt(scopedKey, defaultValue)
        }
        return prefs.getInt(key, defaultValue)
    }

    protected fun getFloatScoped(key: String, defaultValue: Float): Float {
        val scopedKey = getScopedKey(key)
        if (prefs.contains(scopedKey)) {
            return prefs.getFloat(scopedKey, defaultValue)
        }
        return prefs.getFloat(key, defaultValue)
    }

    protected fun getStringSetScoped(key: String, defaultValue: Set<String>? = null): Set<String>? {
        val scopedKey = getScopedKey(key)
        if (prefs.contains(scopedKey)) {
            return prefs.getStringSet(scopedKey, defaultValue)
        }
        return prefs.getStringSet(key, defaultValue)
    }

    // --- Book-specific access (explicit bookId) ---

    fun getStringForBook(bookId: String, key: String, defaultValue: String? = null): String? {
        val scopedKey = "${bookId}_$key"
        if (prefs.contains(scopedKey)) {
            return prefs.getString(scopedKey, defaultValue)
        }
        return prefs.getString(key, defaultValue)
    }

    fun getBooleanForBook(bookId: String, key: String, defaultValue: Boolean): Boolean {
        val scopedKey = "${bookId}_$key"
        if (prefs.contains(scopedKey)) {
            return prefs.getBoolean(scopedKey, defaultValue)
        }
        return prefs.getBoolean(key, defaultValue)
    }

    fun getLongForBook(bookId: String, key: String, defaultValue: Long): Long {
        val scopedKey = "${bookId}_$key"
        if (prefs.contains(scopedKey)) {
            return prefs.getLong(scopedKey, defaultValue)
        }
        return prefs.getLong(key, defaultValue)
    }

    fun getIntForBook(bookId: String, key: String, defaultValue: Int): Int {
        val scopedKey = "${bookId}_$key"
        if (prefs.contains(scopedKey)) {
            return prefs.getInt(scopedKey, defaultValue)
        }
        return prefs.getInt(key, defaultValue)
    }

    protected fun putStringScoped(key: String, value: String?) {
        prefs.edit { putString(getScopedKey(key), value) }
    }

    protected fun putBooleanScoped(key: String, value: Boolean) {
        prefs.edit { putBoolean(getScopedKey(key), value) }
    }

    protected fun putLongScoped(key: String, value: Long) {
        prefs.edit { putLong(getScopedKey(key), value) }
    }

    protected fun putIntScoped(key: String, value: Int) {
        prefs.edit { putInt(getScopedKey(key), value) }
    }

    protected fun putFloatScoped(key: String, value: Float) {
        prefs.edit { putFloat(getScopedKey(key), value) }
    }

    protected fun putStringSetScoped(key: String, value: Set<String>?) {
        prefs.edit { putStringSet(getScopedKey(key), value) }
    }

    protected inner class StringSetting(
        private val key: String,
        private val default: String? = null,
        private val isScoped: Boolean = true
    ) {
        private val _flow = MutableStateFlow(if (isScoped) getStringScoped(key, default) else prefs.getString(key, default))
        val flow: StateFlow<String?> = _flow.asStateFlow()

        var value: String?
            get() = if (isScoped) getStringScoped(key, default) else prefs.getString(key, default)
            set(v) {
                if (isScoped) putStringScoped(key, v) else prefs.edit { putString(key, v) }
                _flow.value = v
                changeListener?.invoke(key)
            }

        fun refresh() { if (isScoped) _flow.value = value }

        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): String? = value
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, v: String?) { value = v }
    }

    protected inner class NonNullStringSetting(
        private val key: String,
        private val default: String,
        private val isScoped: Boolean = true
    ) {
        private val _flow = MutableStateFlow((if (isScoped) getStringScoped(key, default) else prefs.getString(key, default)) ?: default)
        val flow: StateFlow<String> = _flow.asStateFlow()

        var value: String
            get() = (if (isScoped) getStringScoped(key, default) else prefs.getString(key, default)) ?: default
            set(v) {
                if (isScoped) putStringScoped(key, v) else prefs.edit { putString(key, v) }
                _flow.value = v
                changeListener?.invoke(key)
            }

        fun refresh() { if (isScoped) _flow.value = value }

        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): String = value
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, v: String) { value = v }
    }

    protected inner class BooleanSetting(
        private val key: String,
        private val default: Boolean,
        private val isScoped: Boolean = true
    ) {
        private val _flow = MutableStateFlow(if (isScoped) getBooleanScoped(key, default) else prefs.getBoolean(key, default))
        val flow: StateFlow<Boolean> = _flow.asStateFlow()

        var value: Boolean
            get() = if (isScoped) getBooleanScoped(key, default) else prefs.getBoolean(key, default)
            set(v) {
                if (isScoped) putBooleanScoped(key, v) else prefs.edit { putBoolean(key, v) }
                _flow.value = v
                changeListener?.invoke(key)
            }

        fun refresh() { if (isScoped) _flow.value = value }

        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Boolean = value
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, v: Boolean) { value = v }
    }

    protected inner class IntSetting(
        private val key: String,
        private val default: Int,
        private val isScoped: Boolean = true
    ) {
        private val _flow = MutableStateFlow(if (isScoped) getIntScoped(key, default) else prefs.getInt(key, default))
        val flow: StateFlow<Int> = _flow.asStateFlow()

        var value: Int
            get() = if (isScoped) getIntScoped(key, default) else prefs.getInt(key, default)
            set(v) {
                if (isScoped) putIntScoped(key, v) else prefs.edit { putInt(key, v) }
                _flow.value = v
                changeListener?.invoke(key)
            }

        fun refresh() { if (isScoped) _flow.value = value }

        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Int = value
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, v: Int) { value = v }
    }

    protected inner class LongSetting(
        private val key: String,
        private val default: Long,
        private val isScoped: Boolean = true
    ) {
        private val _flow = MutableStateFlow(if (isScoped) getLongScoped(key, default) else prefs.getLong(key, default))
        val flow: StateFlow<Long> = _flow.asStateFlow()

        var value: Long
            get() = if (isScoped) getLongScoped(key, default) else prefs.getLong(key, default)
            set(v) {
                if (isScoped) putLongScoped(key, v) else prefs.edit { putLong(key, v) }
                _flow.value = v
                changeListener?.invoke(key)
            }

        fun refresh() { if (isScoped) _flow.value = value }

        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Long = value
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, v: Long) { value = v }
    }

    protected inner class FloatSetting(
        private val key: String,
        private val default: Float,
        private val isScoped: Boolean = true,
        private val coerce: ((Float) -> Float)? = null
    ) {
        private val _flow = MutableStateFlow(if (isScoped) getFloatScoped(key, default) else prefs.getFloat(key, default))
        val flow: StateFlow<Float> = _flow.asStateFlow()

        var value: Float
            get() = if (isScoped) getFloatScoped(key, default) else prefs.getFloat(key, default)
            set(v) {
                val coerced = coerce?.invoke(v) ?: v
                if (isScoped) putFloatScoped(key, coerced) else prefs.edit {
                    putFloat(
                        key,
                        coerced
                    )
                }
                _flow.value = coerced
                changeListener?.invoke(key)
            }

        fun refresh() { if (isScoped) _flow.value = value }

        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Float = value
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, v: Float) { value = v }
    }

    protected inner class StringSetSetting(
        private val key: String,
        private val default: Set<String> = emptySet(),
        private val isScoped: Boolean = true
    ) {
        private val _flow = MutableStateFlow((if (isScoped) getStringSetScoped(key, default) else prefs.getStringSet(key, default)) ?: default)
        val flow: StateFlow<Set<String>> = _flow.asStateFlow()

        var value: Set<String>
            get() = (if (isScoped) getStringSetScoped(key, default) else prefs.getStringSet(key, default)) ?: default
            set(v) {
                if (isScoped) putStringSetScoped(key, v) else prefs.edit { putStringSet(key, v) }
                _flow.value = v
                changeListener?.invoke(key)
            }

        fun refresh() { if (isScoped) _flow.value = value }

        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Set<String> = value
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, v: Set<String>) { value = v }
    }

    abstract fun refresh()
}
