package com.andreas_kratzer.ghosttalk.ui.main

import android.content.res.Configuration
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    onNavigateToUserMode: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToContentManagement: () -> Unit,
    onNavigateToBooks: () -> Unit,
    bookName: String
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val dimensions = LocalDimensions.current
    val vSpacing = if (isLandscape) dimensions.paddingLarge else dimensions.paddingExtraLarge

    BackHandler {
        onNavigateToBooks()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = bookName,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToBooks) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.start_back_to_books))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(dimensions.paddingExtraLarge)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
            ) {
                val isLandscape = maxWidth > maxHeight
                val vSpacing = dimensions.paddingLarge
                val dynamicCardHeight = (maxHeight * if (isLandscape) 0.2f else 0.12f).coerceIn(90.dp, 140.dp)
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.paddingLarge, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(vSpacing),
                    maxItemsInEachRow = if (isLandscape) 3 else 1
                ) {
                    val cardModifier = if (isLandscape) Modifier.weight(1f) else Modifier.fillMaxWidth()

                GhostTalkCard(
                    title = stringResource(R.string.start_user_mode),
                    icon = Icons.Filled.PlayArrow,
                    onClick = onNavigateToUserMode,
                    modifier = cardModifier,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.primary,
                    height = dynamicCardHeight
                )
                
                GhostTalkCard(
                    title = stringResource(R.string.start_manage_content),
                    icon = Icons.Filled.Edit,
                    onClick = onNavigateToContentManagement,
                    modifier = cardModifier,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                    height = dynamicCardHeight
                )
                
                GhostTalkCard(
                    title = stringResource(R.string.settings_title_book),
                    icon = Icons.Filled.Settings,
                    onClick = onNavigateToSettings,
                    modifier = cardModifier,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    height = dynamicCardHeight
                )
            }
        }
    }
}
}
