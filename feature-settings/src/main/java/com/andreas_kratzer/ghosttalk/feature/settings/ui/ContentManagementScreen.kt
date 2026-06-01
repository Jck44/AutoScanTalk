package com.andreas_kratzer.ghosttalk.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentManagementScreen(
    onNavigateToPageManager: () -> Unit,
    onNavigateToTemplateManager: () -> Unit,
    onNavigateToAnalyticsDashboard: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val dimensions = LocalDimensions.current

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CoreR.string.start_manage_content)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("content_management_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreR.string.back_button_content_description)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(dimensions.paddingLarge)
        ) {
            val isLandscape = maxWidth > maxHeight
            val dynamicCardHeight = (maxHeight * if (isLandscape) 0.18f else 0.12f).coerceIn(90.dp, 140.dp)

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimensions.paddingLarge)
            ) {
            GhostTalkCard(
                title = stringResource(CoreR.string.page_list_title),
                icon = GhostTalkIcons.Description,
                onClick = onNavigateToPageManager,
                height = dynamicCardHeight,
                testTag = "content_manage_pages"
            )
            
            GhostTalkCard(
                title = stringResource(CoreR.string.template_manage_title),
                icon = GhostTalkIcons.GridView,
                onClick = onNavigateToTemplateManager,
                height = dynamicCardHeight,
                testTag = "content_manage_templates"
            )

            GhostTalkCard(
                title = "Statistiken & Empfehlungen",
                icon = GhostTalkIcons.BarChart,
                onClick = onNavigateToAnalyticsDashboard,
                height = dynamicCardHeight,
                testTag = "content_manage_analytics"
            )
        }
    }
}
}
