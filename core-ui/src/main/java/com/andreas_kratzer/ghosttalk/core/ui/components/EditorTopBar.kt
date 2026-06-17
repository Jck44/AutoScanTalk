package com.andreas_kratzer.ghosttalk.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    titleContent: @Composable RowScope.() -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    modeSwitcher: (@Composable () -> Unit)? = null,
    actions: List<EditorAction> = emptyList(),
    isTablet: Boolean = false,
    overflowTestTag: String = "editor_overflow_menu_trigger",
    onExitEditor: (() -> Unit)? = null,
    exitTestTag: String = "page_editor_exit_button",
    exitContentDescription: String? = null
) {
    val resolvedExitContentDescription = exitContentDescription ?: stringResource(R.string.editor_exit)
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                titleContent()
                if (modeSwitcher != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    modeSwitcher()
                }
            }
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
        actions = {
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val maxActionsWidth = (configuration.screenWidthDp * 0.5f).dp
            
            AdaptiveActionBar(
                actions = actions,
                isTablet = isTablet,
                overflowTestTag = overflowTestTag,
                modifier = Modifier.widthIn(max = maxActionsWidth)
            )
            if (onExitEditor != null) {
                IconButton(
                    onClick = onExitEditor,
                    modifier = Modifier.testTag(exitTestTag)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = resolvedExitContentDescription
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

