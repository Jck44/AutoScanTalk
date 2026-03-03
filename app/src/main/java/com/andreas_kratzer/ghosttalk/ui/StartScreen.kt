package com.andreas_kratzer.ghosttalk.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.components.AppBrandHeader
import com.andreas_kratzer.ghosttalk.ui.components.GhostTalkCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    onNavigateToUserMode: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToContentManagement: () -> Unit,
    onNavigateToBooks: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val vSpacing = if (isLandscape) 16.dp else 24.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AppBrandHeader(
                        isLandscape = true, // Unified: always use the smaller title in the top bar
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top // Changed from Center to Top for unified look
        ) {
            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 32.dp))
            
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
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
                    iconColor = MaterialTheme.colorScheme.primary
                )
                
                GhostTalkCard(
                    title = stringResource(R.string.start_manage_content),
                    icon = Icons.Filled.Edit,
                    onClick = onNavigateToContentManagement,
                    modifier = cardModifier,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconColor = MaterialTheme.colorScheme.tertiary
                )
                
                GhostTalkCard(
                    title = stringResource(R.string.settings_title),
                    icon = Icons.Filled.Settings,
                    onClick = onNavigateToSettings,
                    modifier = cardModifier,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 32.dp))

            GhostTalkCard(
                title = stringResource(R.string.start_back_to_books),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onNavigateToBooks,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
