package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.pages.actions.NavigationActionFields
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@Composable
fun TargetPageSelectionDialog(
    availablePages: List<Page>,
    templates: List<PageTemplate>,
    onPageSelected: (Page) -> Unit,
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)?,
    onDismiss: () -> Unit,
    currentPageId: String? = null
) {
    val dimensions = LocalDimensions.current
    var selectedPageId by remember { mutableStateOf("") }
    
    GhostTalkDialog(
        title = stringResource(R.string.button_move_target_title),
        onDismiss = onDismiss,
        confirmText = stringResource(CoreR.string.action_cancel),
        onConfirm = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)
        ) {
            if (currentPageId != null) {
                val currentPage = availablePages.find { it.id == currentPageId }
                if (currentPage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPageSelected(currentPage) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(dimensions.paddingMedium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Auf aktueller Seite duplizieren",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Erstellt ein Duplikat auf dieser Seite (${currentPage.name})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            NavigationActionFields(
                navigateToPageId = selectedPageId,
                onPageSelected = { pageId ->
                    selectedPageId = pageId
                    val page = availablePages.find { it.id == pageId }
                    if (page != null) {
                        onPageSelected(page)
                    }
                },
                availablePages = availablePages,
                templates = templates,
                onNavigateToPage = null,
                onCreatePage = onCreatePage,
                onDismissDialog = onDismiss
            )
        }
    }
}

@Composable
fun MoveHiddenPromptDialog(
    requiredRows: Int,
    requiredCols: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    
    val info = when {
        requiredRows > 0 && requiredCols > 0 -> stringResource(R.string.button_move_hidden_prompt_add_both)
        requiredRows > 0 -> stringResource(R.string.button_move_hidden_prompt_add_row)
        else -> stringResource(R.string.button_move_hidden_prompt_add_col)
    }

    GhostTalkDialog(
        title = stringResource(R.string.button_move_hidden_prompt_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_create),
        onConfirm = onConfirm,
        dismissText = stringResource(R.string.action_cancel)
    ) {
        Text(stringResource(R.string.button_move_hidden_prompt_message, info))
    }
}
