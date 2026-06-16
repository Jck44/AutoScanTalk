package com.andreas_kratzer.ghosttalk.ui.templates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.components.CategoryHeaderDropTarget
import com.andreas_kratzer.ghosttalk.ui.components.TemplateDropTarget
import com.andreas_kratzer.ghosttalk.ui.components.dragSource
import com.andreas_kratzer.ghosttalk.ui.components.dropTarget
import com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonTemplatesPanel(
    actions: GridEditorActions,
    modifier: Modifier = Modifier,
    onEditTemplate: (ButtonTemplate) -> Unit = {},
    onTemplateClick: ((ButtonTemplate) -> Unit)? = null
) {
    val templates by actions.buttonTemplates.collectAsState()
    val dimensions = LocalDimensions.current
    var searchQuery by remember { mutableStateOf("") }
    var expandedCategories by rememberSaveable {
        mutableStateOf(ActionCategoryRegistry.ALL_GROUPS.toSet())
    }

    // Filtered templates
    val filteredTemplates = remember(templates, searchQuery) {
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.isBlank()) {
            templates
        } else {
            templates.filter {
                it.name.contains(trimmedQuery, ignoreCase = true) ||
                it.buttonConfig.label.contains(trimmedQuery, ignoreCase = true)
            }
        }
    }

    // Grouped by Action Category Header
    val groupedTemplates = remember(filteredTemplates) {
        filteredTemplates.groupBy {
            ActionCategoryRegistry.getGroupForAction(it.buttonConfig.buttonAction)
        }
    }

    // Build the flat list of items for index mapping
    val flatItems = remember(groupedTemplates, expandedCategories) {
        val list = mutableListOf<Any>()
        ActionCategoryRegistry.ALL_GROUPS.forEach { groupName ->
            val groupItems = groupedTemplates[groupName] ?: emptyList()
            if (groupItems.isNotEmpty()) {
                list.add(groupName) // Header
                if (expandedCategories.contains(groupName)) {
                    list.addAll(groupItems.sortedBy { it.orderIndex })
                }
            }
        }
        list
    }

    val lazyListState = rememberLazyListState()

    Column(
        modifier = modifier.padding(dimensions.paddingMedium)
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.template_search_placeholder), fontSize = 14.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Suche löschen")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimensions.paddingMedium),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            )
        )

        // Drag/Drop hint
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimensions.paddingMedium)
        ) {
            Text(
                text = stringResource(R.string.template_drag_drop_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(dimensions.paddingMedium)
            )
        }

        if (flatItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.template_none_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
                contentPadding = PaddingValues(bottom = dimensions.paddingExtraLarge)
            ) {
                flatItems.forEach { item ->
                    when (item) {
                        is String -> {
                            item(key = "header_$item") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .dropTarget(key = CategoryHeaderDropTarget(item))
                                ) {
                                    val isExpanded = expandedCategories.contains(item)
                                    CategoryHeader(
                                        title = item,
                                        isExpanded = isExpanded,
                                        onToggle = {
                                            expandedCategories = if (isExpanded) {
                                                expandedCategories - item
                                            } else {
                                                expandedCategories + item
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        is ButtonTemplate -> {
                            item(key = item.id) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .dropTarget(key = TemplateDropTarget(item))
                                ) {
                                    TemplateItemCard(
                                        template = item,
                                        onEditTemplate = onEditTemplate,
                                        onTemplateClick = onTemplateClick,
                                        onDelete = {
                                             actions.deleteButtonTemplate(item)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryHeader(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val dimensions = LocalDimensions.current
    Surface(
        onClick = onToggle,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.paddingSmall)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(if (isExpanded) R.string.content_desc_collapse else R.string.content_desc_expand),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun TemplateItemCard(
    template: ButtonTemplate,
    onEditTemplate: (ButtonTemplate) -> Unit = {},
    onTemplateClick: ((ButtonTemplate) -> Unit)? = null,
    onDelete: () -> Unit
) {
    val dimensions = LocalDimensions.current
    Card(
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .dragSource(item = template)
            .clickable {
                if (onTemplateClick != null) {
                    onTemplateClick(template)
                } else {
                    onEditTemplate(template)
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.paddingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual Mini Button representation
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            val (badgeBgColor, badgeTxtColor) = remember(template.buttonConfig.buttonAction, isDark) {
                com.andreas_kratzer.ghosttalk.ui.pages.GridButtonColors.getBadgeColors(template.buttonConfig.buttonAction, isDark)
            }
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(badgeBgColor)
                    .border(
                        width = 1.dp,
                        color = badgeTxtColor.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = template.buttonConfig.label.take(8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeTxtColor,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(dimensions.paddingMedium))

            // Template Name and Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                val spokenText = template.buttonConfig.spokenText
                if (spokenText != null) {
                    Text(
                        text = stringResource(R.string.template_speaks_format, spokenText),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            // Pencil edit icon
            IconButton(
                onClick = { onEditTemplate(template) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.action_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Custom templates can be deleted
            if (!template.isBuiltIn) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.template_delete_title),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
