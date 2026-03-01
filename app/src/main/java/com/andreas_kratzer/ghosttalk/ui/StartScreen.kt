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
@Composable
fun StartScreen(
    onNavigateToUserMode: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPageManager: () -> Unit,
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
        Text(
            text = "GhosTTalk",
            style = (if (isLandscape) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge).copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        
        Text(
            text = "Unterstützte Kommunikation",
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
            Button(
                onClick = onNavigateToUserMode,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(height = 80.dp, width = 240.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Start",
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Nutzer Modus", style = MaterialTheme.typography.titleMedium)
            }
            
            Button(
                onClick = onNavigateToPageManager,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(height = 80.dp, width = 240.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Seiten verwalten",
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Seiten verwalten", style = MaterialTheme.typography.titleMedium)
            }
            
            Button(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(height = 80.dp, width = 240.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Einstellungen",
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Einstellungen", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 32.dp else 48.dp))

        androidx.compose.material3.OutlinedButton(
            onClick = onNavigateToBooks,
            modifier = Modifier.size(height = 60.dp, width = 240.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zurück",
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Zurück zu den Büchern")
        }
    }
}
