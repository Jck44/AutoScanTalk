package com.andreas_kratzer.ghosttalk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.components.GhostTalkCard

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Name (Logo/Branding - usually stays same or uses resources)
        Text(
            text = stringResource(R.string.app_name),
            style = (if (isLandscape) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge).copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        
        Text(
            text = stringResource(R.string.start_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(if (isLandscape) 32.dp else 64.dp))
        
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(vSpacing),
            maxItemsInEachRow = if (isLandscape) 3 else 1
        ) {
            GhostTalkCard(
                title = stringResource(R.string.start_user_mode),
                icon = Icons.Filled.PlayArrow,
                onClick = onNavigateToUserMode,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .width(240.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.primary
            )
            
            GhostTalkCard(
                title = stringResource(R.string.start_manage_content),
                icon = Icons.Filled.Edit,
                onClick = onNavigateToContentManagement,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .width(240.dp),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconColor = MaterialTheme.colorScheme.tertiary
            )
            
            GhostTalkCard(
                title = stringResource(R.string.settings_title),
                icon = Icons.Filled.Settings,
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .width(240.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 32.dp else 48.dp))

        GhostTalkCard(
            title = stringResource(R.string.start_back_to_books),
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onNavigateToBooks,
            modifier = Modifier.width(240.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
