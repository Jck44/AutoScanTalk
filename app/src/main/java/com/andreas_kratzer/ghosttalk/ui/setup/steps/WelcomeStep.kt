package com.andreas_kratzer.ghosttalk.ui.setup.steps

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@Composable
fun WelcomeStepContent(
    viewModel: SettingsViewModel,
    onRestoreSuccess: () -> Unit,
    onLocalImportClick: () -> Unit
) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("setup_ui_prefs", Context.MODE_PRIVATE) }
    val localDimensions = LocalDimensions.current
    var showRestoreDialog by remember { 
        mutableStateOf(uiPrefs.getBoolean("show_restore_dialog", false)) 
    }

    val setShowRestoreDialog = { value: Boolean ->
        showRestoreDialog = value
        uiPrefs.edit { putBoolean("show_restore_dialog", value) }
    }

    Image(
        painter = painterResource(id = CoreR.drawable.ic_app_logo),
        contentDescription = null,
        modifier = Modifier.size(96.dp)
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))
    Text(
        text = stringResource(R.string.setup_welcome_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingMedium))
    Text(
        text = stringResource(R.string.setup_welcome_desc),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = localDimensions.paddingMedium)
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingDoubleExtraLarge))

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(localDimensions.paddingLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Bereits GhostTalk genutzt?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(localDimensions.paddingSmall))
            Text(
                text = "Stelle dein bestehendes Profil über ein lokales Backup oder Cloud Sync wieder her.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(localDimensions.paddingMedium))
            Button(
                onClick = { setShowRestoreDialog(true) },
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(GhostTalkIcons.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Profil wiederherstellen")
            }
        }
    }

    if (showRestoreDialog) {
        RestoreProfileDialog(
            viewModel = viewModel,
            onDismiss = { setShowRestoreDialog(false) },
            onLocalImportClick = {
                setShowRestoreDialog(false)
                onLocalImportClick()
            },
            onRestoreSuccess = onRestoreSuccess
        )
    }
}
