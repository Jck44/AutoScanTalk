package com.andreas_kratzer.ghosttalk.core

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val _unlockedBookIds = MutableStateFlow<Set<String>>(emptySet())
    val unlockedBookIds: StateFlow<Set<String>> = _unlockedBookIds.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * A flow that emits true if the currently active book is unlocked.
     */
    val isUnlocked: StateFlow<Boolean> = combine(
        _unlockedBookIds,
        settingsRepository.activeBookIdFlow
    ) { unlockedIds, activeId ->
        unlockedIds.contains(activeId)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private var lastActivityTime: Long = 0

    fun isBookUnlocked(bookId: String? = null): Boolean {
        val id = bookId ?: settingsRepository.activeBookId
        return _unlockedBookIds.value.contains(id)
    }

    fun unlock(pin: String, bookId: String? = null): Boolean {
        val targetId = bookId ?: settingsRepository.activeBookId
        val correctPin = settingsRepository.getSecurityPinForBook(targetId)
        
        return if (!correctPin.isNullOrEmpty() && pin == correctPin) {
            _unlockedBookIds.value += targetId
            updateActivity()
            true
        } else {
            false
        }
    }

    fun lock() {
        _unlockedBookIds.value = emptySet()
    }

    fun updateActivity() {
        lastActivityTime = System.currentTimeMillis()
    }

    fun checkTimeout() {
        if (_unlockedBookIds.value.isEmpty()) return
        
        val timeoutMinutes = settingsRepository.securityPinTimeoutMinutes
        if (timeoutMinutes <= 0) return

        val currentTime = System.currentTimeMillis()
        val elapsedMillis = currentTime - lastActivityTime
        if (elapsedMillis > timeoutMinutes * 60 * 1000) {
            lock()
        }
    }

    fun isPinSet(bookId: String? = null): Boolean {
        val pin = if (bookId != null) {
            settingsRepository.getSecurityPinForBook(bookId)
        } else {
            settingsRepository.securityPin
        }
        return !pin.isNullOrEmpty()
    }
    
    fun isPinRequiredForDeletion(bookId: String? = null): Boolean {
        // Deletion protection requires BOTH:
        // 1. The global setting is enabled
        // 2. The specific book has a PIN set (either scoped or via global fallback)
        return settingsRepository.isPinRequiredForDeletion && isPinSet(bookId)
    }
}
