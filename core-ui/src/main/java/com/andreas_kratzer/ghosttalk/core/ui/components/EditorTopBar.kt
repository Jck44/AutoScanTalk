package com.andreas_kratzer.ghosttalk.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.core.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    titleContent: @Composable RowScope.() -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    modeSwitcher: (@Composable () -> Unit)? = null,
    actions: List<EditorAction> = emptyList(),
    overflowTestTag: String = "editor_overflow_menu_trigger",
    onExitEditor: (() -> Unit)? = null,
    exitTestTag: String = "page_editor_exit_button",
    exitContentDescription: String? = null
) {
    val resolvedExitContentDescription = exitContentDescription ?: stringResource(R.string.editor_exit)
    TopAppBar(
        title = {
            AdaptiveEditorBar(
                titleContent = titleContent,
                modeSwitcher = modeSwitcher,
                actions = actions,
                onExitEditor = onExitEditor,
                overflowTestTag = overflowTestTag,
                exitTestTag = exitTestTag,
                exitContentDescription = resolvedExitContentDescription,
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back)
                    )
                }
            }
        },
        actions = {},
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

