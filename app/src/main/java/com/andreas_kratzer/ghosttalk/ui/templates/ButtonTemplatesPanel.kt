package com.andreas_kratzer.ghosttalk.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.components.dragSource
import com.andreas_kratzer.ghosttalk.ui.components.dropTarget
import com.andreas_kratzer.ghosttalk.ui.components.TemplateDropTarget
import com.andreas_kratzer.ghosttalk.ui.components.CategoryHeaderDropTarget
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonTemplatesPanel(
    viewModel: PageViewModel,
    onEditTemplate: (ButtonTemplate) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val templates by viewModel.buttonTemplates.collectAsState()
    val dimensions = LocalDimensions.current
    var searchQuery by remember { mutableStateOf("") }
    var expandedCategories by rememberSaveable {
        mutableStateOf(ActionCategoryRegistry.ALL_GROUPS.toSet())
    }

    // Filtered templates
    val filteredTemplates = remember(templates, searchQuery) {
        if (searchQuery.isBlank()) {
            templates
        } else {
            templates.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.buttonConfig.label.contains(searchQuery, ignoreCase = true)
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
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = "Button-Vorlagen",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Vorlagen durchsuchen...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        // Drag/Drop hint
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = "💡 Vorlage auf Gitter ziehen zum Platzieren. Button aus Gitter hierhin ziehen, um Vorlage zu erstellen.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(8.dp)
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
                    text = "Keine Vorlagen gefunden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                flatItems.forEachIndexed { globalListIdx, item ->
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
                                        onDelete = {
                                            viewModel.deleteButtonTemplate(item)
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
    Surface(
        onClick = onToggle,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
                contentDescription = if (isExpanded) "Einklappen" else "Ausklappen",
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
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .dragSource(item = template)
            .clickable { onEditTemplate(template) }
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual Mini Button representation
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = template.buttonConfig.label.take(8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Template Name and Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (template.buttonConfig.spokenText != null) {
                    Text(
                        text = "Spricht: \"${template.buttonConfig.spokenText}\"",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            // Custom templates can be deleted
            if (!template.isBuiltIn) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Vorlage löschen",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
