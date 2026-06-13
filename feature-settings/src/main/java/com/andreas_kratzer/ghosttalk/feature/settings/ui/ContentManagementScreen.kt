package com.andreas_kratzer.ghosttalk.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkScaffold
import com.andreas_kratzer.ghosttalk.core.ui.components.adaptiveCardHeight
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentManagementScreen(
    onNavigateToPageManager: () -> Unit,
    onNavigateToTemplateManager: () -> Unit,
    onNavigateToStaticRowEditor: () -> Unit,
    onNavigateToStructureEditor: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val dimensions = LocalDimensions.current

    BackHandler {
        onNavigateBack()
    }

    GhostTalkScaffold(
        title = stringResource(CoreR.string.start_manage_content),
        onNavigateBack = onNavigateBack
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(dimensions.screenPaddingHorizontal)
        ) {
            val dynamicCardHeight = adaptiveCardHeight()

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimensions.listItemSpacing)
            ) {
                GhostTalkCard(
                    title = stringResource(CoreR.string.page_list_title),
                    icon = GhostTalkIcons.Description,
                    onClick = onNavigateToPageManager,
                    height = dynamicCardHeight,
                    testTag = "content_manage_pages"
                )

                GhostTalkCard(
                    title = stringResource(CoreR.string.structure_editor_title),
                    icon = GhostTalkIcons.Link,
                    onClick = onNavigateToStructureEditor,
                    height = dynamicCardHeight,
                    testTag = "content_manage_structure"
                )
            
                GhostTalkCard(
                    title = stringResource(CoreR.string.template_manage_title),
                    icon = GhostTalkIcons.GridView,
                    onClick = onNavigateToTemplateManager,
                    height = dynamicCardHeight,
                    testTag = "content_manage_templates"
                )
            
                GhostTalkCard(
                    title = stringResource(R.string.content_manage_configure_static_row),
                    icon = GhostTalkIcons.GridView,
                    onClick = onNavigateToStaticRowEditor,
                    height = dynamicCardHeight,
                    testTag = "content_manage_static_row"
                )
            }
    }
}
}
