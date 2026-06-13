package com.andreas_kratzer.ghosttalk.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkScaffold
import com.andreas_kratzer.ghosttalk.core.ui.components.adaptiveCardHeight
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    onNavigateToUserMode: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToContentManagement: () -> Unit,
    onNavigateToAnalyticsDashboard: () -> Unit,
    onNavigateToBooks: () -> Unit,
    bookName: String
) {
    val dimensions = LocalDimensions.current

    BackHandler {
        onNavigateToBooks()
    }

    GhostTalkScaffold(
        title = bookName,
        onNavigateBack = onNavigateToBooks,
        actions = {
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.testTag("start_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(CoreR.string.settings_title_book)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(
                    horizontal = dimensions.screenPaddingHorizontal,
                    vertical = dimensions.screenPaddingVertical
                )
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
            ) {
                val isLandscape = maxWidth > maxHeight
                val vSpacing = dimensions.paddingLarge
                val dynamicCardHeight = adaptiveCardHeight()
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.paddingLarge, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(vSpacing),
                    maxItemsInEachRow = if (isLandscape) 2 else 1
                ) {
                    val cardModifier = if (isLandscape) Modifier.weight(1f) else Modifier.fillMaxWidth()
 
                GhostTalkCard(
                    title = stringResource(R.string.start_user_mode),
                    icon = Icons.Filled.PlayArrow,
                    onClick = onNavigateToUserMode,
                    modifier = cardModifier.testTag("start_card_user_mode"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.primary,
                    height = dynamicCardHeight
                )
                
                GhostTalkCard(
                    title = stringResource(CoreR.string.start_manage_content),
                    icon = GhostTalkIcons.Edit,
                    onClick = onNavigateToContentManagement,
                    modifier = cardModifier.testTag("start_card_manage"),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                    height = dynamicCardHeight
                )

                GhostTalkCard(
                    title = stringResource(R.string.settings_analytics_dashboard),
                    icon = GhostTalkIcons.BarChart,
                    onClick = onNavigateToAnalyticsDashboard,
                    modifier = cardModifier.testTag("start_card_analytics"),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    height = dynamicCardHeight
                )
            }
        }
    }
}
}
