package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncAnchorStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val prefs get() = context.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE)
    fun getAnchorMd5(bookId: String): String? =
        prefs.getString("book_sync_anchor_md5_$bookId", null)
    fun setAnchor(bookId: String, structMd5: String) {
        if (structMd5.isNotEmpty()) {
            prefs.edit { putString("book_sync_anchor_md5_$bookId", structMd5) }
        }
    }
    fun clearAnchor(bookId: String) =
        prefs.edit { remove("book_sync_anchor_md5_$bookId") }
}
