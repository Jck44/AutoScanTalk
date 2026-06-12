package com.andreas_kratzer.ghosttalk.core.data.impl.clone

import com.andreas_kratzer.ghosttalk.core.database.ButtonEntity
import com.andreas_kratzer.ghosttalk.core.model.Page

data class MutablePageWithButtons(
    var page: Page,
    val buttons: MutableList<ButtonEntity>
)
