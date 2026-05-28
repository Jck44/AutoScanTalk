package com.andreas_kratzer.ghosttalk.core.data

import com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat

interface ButtonUsageProvider {
    suspend fun getTopActions(bookId: String, limit: Int): List<ButtonUsageStat>
}
