package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.VocalProfile
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocalTrainingScreen(
    onNavigateBack: () -> Unit,
    viewModel: VocalTrainingViewModel = hiltViewModel()
) {
    val dimensions = LocalDimensions.current
    val allProfiles by viewModel.allProfiles.collectAsState()
    val allPages by viewModel.allPages.collectAsState()
    val slotStatusList by viewModel.recordingSlotStatus.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val activeSlot by viewModel.activeRecordingSlot.collectAsState()
    val isRecordingBackgroundNoise by viewModel.isRecordingBackgroundNoise.collectAsState()
    val backgroundNoiseStatus by viewModel.backgroundNoiseStatus.collectAsState()

    val profileName by viewModel.profileName.collectAsState()
    val actionType by viewModel.actionType.collectAsState()
    val spokenText by viewModel.spokenText.collectAsState()
    val selectedPageId by viewModel.selectedPageId.collectAsState()

    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var showPermissionDeniedHint by remember { mutableStateOf(false) }

    // Remember which slot was clicked while waiting for the permission dialog.
    var pendingSlotIndex by remember { mutableIntStateOf(-1) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingSlotIndex >= 0) {
            viewModel.startRecordingSlot(pendingSlotIndex)
        } else if (!granted) {
            showPermissionDeniedHint = true
        }
        pendingSlotIndex = -1
    }

    /** Checks RECORD_AUDIO and starts the slot, requesting permission if needed. */
    fun startSlotWithPermission(slotIndex: Int) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.startRecordingSlot(slotIndex)
        } else {
            pendingSlotIndex = slotIndex
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vocal Switch Training (Mini-Euphonia)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = dimensions.paddingLarge),
            verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)
        ) {
            item {
                Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                Text(
                    text = "Lerne GhostTalk deine Laute an! Du nimmst denselben Laut 5-mal auf. GhostTalk berechnet daraus einen akustischen Fingerabdruck und vergleicht ihn im Hintergrund. Wenn du den Laut machst, wird die Aktion sofort ausgelöst.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                PreferenceCategory("1. Laut aufnehmen (5 Proben)") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimensions.paddingMedium),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 5) {
                            TrainingSlot(
                                index = i + 1,
                                status = slotStatusList[i],
                                isAnyRecording = isRecording,
                                isActive = activeSlot == i,
                                onClick = { startSlotWithPermission(i) }
                            )
                        }
                    }
                }
            }

            item {
                PreferenceCategory("2. Aktion zuweisen") {
                    SettingsEditTextItem(
                        label = "Name des Lautes (z.B. Clicks, Ähh)",
                        value = profileName,
                        onValueChange = { viewModel.profileName.value = it }
                    )

                    Spacer(modifier = Modifier.height(dimensions.paddingSmall))

                    val selectedActionLabel = when (actionType) {
                        "taster_click" -> "Simuliere Taster-Klick"
                        "speak_text" -> "Text sprechen & ausgeben"
                        "navigate_page" -> "Direkt zu Seite springen"
                        else -> actionType
                    }

                    SettingsDropdownItem(
                        label = "Aktionstyp",
                        selectedOption = selectedActionLabel,
                        options = listOf(
                            "Simuliere Taster-Klick" to { viewModel.actionType.value = "taster_click" },
                            "Text sprechen & ausgeben" to { viewModel.actionType.value = "speak_text" },
                            "Direkt zu Seite springen" to { viewModel.actionType.value = "navigate_page" }
                        )
                    )

                    if (actionType == "speak_text") {
                        SettingsEditTextItem(
                            label = "Was soll gesprochen werden?",
                            value = spokenText,
                            onValueChange = { viewModel.spokenText.value = it }
                        )
                    }

                    if (actionType == "navigate_page" && allPages.isNotEmpty()) {
                        val selectedPage = allPages.find { it.id == selectedPageId }
                        val selectedPageLabel = selectedPage?.name ?: "Auswählen"
                        
                        SettingsDropdownItem(
                            label = "Zielseite",
                            selectedOption = selectedPageLabel,
                            options = allPages.map { page ->
                                page.name to { viewModel.selectedPageId.value = page.id }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(dimensions.paddingMedium))

                    Button(
                        onClick = { viewModel.saveProfile() },
                        enabled = profileName.isNotBlank() && slotStatusList.all { it == VocalTrainingViewModel.SlotStatus.DONE },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Vocal-Profil speichern")
                    }
                }
            }

            item {
                val testState by viewModel.testState.collectAsState()
                PreferenceCategory("Live-Test & Kalibrierung") {
                    VocalSwitchTestDashboard(
                        testState = testState,
                        allProfiles = allProfiles,
                        isRecordingBackgroundNoise = isRecordingBackgroundNoise,
                        backgroundNoiseStatus = backgroundNoiseStatus,
                        onStartTest = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                viewModel.startLiveTest()
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onRecordBackgroundNoise = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                viewModel.recordGlobalBackgroundNoise()
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onRegisterFalsePositive = { profile, features ->
                            viewModel.addLastSampleAsFalsePositive(profile, features)
                        }
                    )
                }
            }

            item {
                PreferenceCategory("Trainierte Vocal-Profile") {
                    if (allProfiles.isEmpty()) {
                        Text(
                            "Keine trainierten Vocal-Profile vorhanden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
                            allProfiles.forEach { profile ->
                                ProfileRow(profile = profile, onDelete = { viewModel.deleteProfile(profile) })
                            }
                        }
                    }
                }
            }

            item {
                PreferenceCategory("Modell-Einstellungen") {
                    Button(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Vocal Switch Modell zurücksetzen", color = MaterialTheme.colorScheme.onError)
                    }
                    Spacer(modifier = Modifier.height(dimensions.paddingLarge))
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Modell zurücksetzen?") },
            text = { Text("Möchtest du das gesamte Vocal Switch Modell zurücksetzen? Dadurch werden alle trainierten Laute unwiderruflich gelöscht.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetModel()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Zurücksetzen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showPermissionDeniedHint) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedHint = false },
            title = { Text("Mikrofon-Zugriff benötigt") },
            text = {
                Text(
                    "GhostTalk benötigt Zugriff auf das Mikrofon, um deine Laute aufzunehmen. " +
                    "Bitte erlaube den Zugriff unter Einstellungen → Apps → GhostTalk → Berechtigungen → Mikrofon."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPermissionDeniedHint = false }) {
                    Text("Verstanden")
                }
            }
        )
    }
}


@Composable
fun TrainingSlot(
    index: Int,
    status: VocalTrainingViewModel.SlotStatus,
    isAnyRecording: Boolean,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val dimensions = LocalDimensions.current

    rememberInfiniteTransition(label = "pulse")

    val backgroundColor by animateColorAsState(
        targetValue = when (status) {
            VocalTrainingViewModel.SlotStatus.EMPTY -> MaterialTheme.colorScheme.surfaceContainer
            VocalTrainingViewModel.SlotStatus.RECORDING -> MaterialTheme.colorScheme.errorContainer
            VocalTrainingViewModel.SlotStatus.PROCESSING -> MaterialTheme.colorScheme.secondaryContainer
            VocalTrainingViewModel.SlotStatus.DONE -> MaterialTheme.colorScheme.primaryContainer
            VocalTrainingViewModel.SlotStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        },
        label = "bgColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when (status) {
            VocalTrainingViewModel.SlotStatus.RECORDING -> MaterialTheme.colorScheme.error
            VocalTrainingViewModel.SlotStatus.DONE -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        label = "borderColor"
    )

    val textColor = when (status) {
        VocalTrainingViewModel.SlotStatus.RECORDING -> MaterialTheme.colorScheme.onErrorContainer
        VocalTrainingViewModel.SlotStatus.DONE -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .border(2.dp, borderColor, CircleShape)
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(enabled = !isAnyRecording || isActive) { onClick() }
                .padding(dimensions.paddingSmall)
        ) {
            Text(
                text = index.toString(),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = when (status) {
                VocalTrainingViewModel.SlotStatus.EMPTY -> "Leer"
                VocalTrainingViewModel.SlotStatus.RECORDING -> "Aufnahme"
                VocalTrainingViewModel.SlotStatus.PROCESSING -> "AI Anal."
                VocalTrainingViewModel.SlotStatus.DONE -> "Bereit"
                VocalTrainingViewModel.SlotStatus.ERROR -> "Fehler"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ProfileRow(
    profile: VocalProfile,
    onDelete: () -> Unit
) {
    val dimensions = LocalDimensions.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.paddingMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                val typeDesc = when {
                    profile.buttonAction is SpeakTextButtonAction -> "Text sprechen: \"${profile.spokenText}\""
                    profile.buttonAction is NavigateToPageButtonAction -> "Seite aufrufen"
                    else -> "Taster-Klick simulieren"
                }
                Text(typeDesc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun VocalSwitchTestDashboard(
    testState: VocalTrainingViewModel.TestScreenState,
    allProfiles: List<VocalProfile>,
    isRecordingBackgroundNoise: Boolean,
    backgroundNoiseStatus: String?,
    onStartTest: () -> Unit,
    onRecordBackgroundNoise: () -> Unit,
    onRegisterFalsePositive: (VocalProfile, List<Float>) -> Unit
) {
    val dimensions = LocalDimensions.current
    var showFPMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.paddingSmall),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)
    ) {
        Button(
            onClick = onStartTest,
            enabled = testState !is VocalTrainingViewModel.TestScreenState.Listening && !isRecordingBackgroundNoise,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when (testState) {
                    is VocalTrainingViewModel.TestScreenState.Listening -> "Höre zu..."
                    else -> "Live-Test starten (1 Sekunde)"
                }
            )
        }

        Button(
            onClick = onRecordBackgroundNoise,
            enabled = testState !is VocalTrainingViewModel.TestScreenState.Listening && !isRecordingBackgroundNoise,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isRecordingBackgroundNoise) "Aufnahme..." else "Hintergrundgeräusch aufnehmen"
            )
        }

        backgroundNoiseStatus?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        when (testState) {
            VocalTrainingViewModel.TestScreenState.Idle -> {
                Text(
                    text = "Bereit für Test. Klicke auf den Button oben und mache das Geräusch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            VocalTrainingViewModel.TestScreenState.Listening -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Bitte jetzt Laut machen...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is VocalTrainingViewModel.TestScreenState.Evaluated -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (testState.isMatch) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(dimensions.paddingMedium),
                        verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
                    ) {
                        Text(
                            text = if (testState.isMatch) "Erkannt!" else "Nicht erkannt / Blockiert",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (testState.isMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (testState.isMatch && testState.matchedProfileName != null) {
                            Text(
                                text = "Profil: ${testState.matchedProfileName}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Text(
                            text = "Ziel-Konfidenz: ${(testState.positiveConfidence * 100).toInt()}% | Noise-Konfidenz: ${(testState.negativeConfidence * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        // If not matched or matches a wrong profile, allow user to register this as false-positive (noise) for a profile
                        if (allProfiles.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.paddingSmall))
                            Text(
                                text = "War das ein Fehlalarm?",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Registriere dieses Geräusch als Fehlalarm für ein Profil, damit es künftig blockiert wird.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Box(modifier = Modifier.padding(top = dimensions.paddingSmall)) {
                                Button(
                                    onClick = { showFPMenu = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("Fehlalarm registrieren...")
                                }
                                DropdownMenu(
                                    expanded = showFPMenu,
                                    onDismissRequest = { showFPMenu = false }
                                ) {
                                    allProfiles.forEach { profile ->
                                        DropdownMenuItem(
                                            text = { Text(profile.name) },
                                            onClick = {
                                                onRegisterFalsePositive(profile, testState.rawFeatures)
                                                showFPMenu = false
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
}

