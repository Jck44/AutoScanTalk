package com.andreas_kratzer.ghosttalk.ui.setup

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.setup.steps.*
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

private enum class SetupStep(val index: Int) {
    WELCOME(0),
    BASIC_PERMISSIONS(1),
    OVERLAY(2),
    NOTIFICATIONS(3),
    DIALER(4),
    DEVICE_SETTINGS(5),
    COMPLETED(6)
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun SetupScreen(
    viewModel: SettingsViewModel,
    onSetupFinished: () -> Unit,
    onRequestDefaultDialer: (Activity) -> Unit
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    val packageName = context.packageName
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(SetupStep.WELCOME) }

    // State trackers for permissions to display in the UI dynamically
    var cameraGranted by remember { mutableStateOf(false) }
    var locationGranted by remember { mutableStateOf(false) }
    var microphoneGranted by remember { mutableStateOf(false) }
    var contactsGranted by remember { mutableStateOf(false) }
    var smsGranted by remember { mutableStateOf(false) }
    var calendarGranted by remember { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(false) }
    var notificationListenerGranted by remember { mutableStateOf(false) }
    var isDefaultDialer by remember { mutableStateOf(false) }

    // Helper to refresh all permission states
    val checkPermissions = {
        cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        microphoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        smsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        calendarGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        
        overlayGranted = Settings.canDrawOverlays(context)
        notificationListenerGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)
        
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        isDefaultDialer = telecomManager.defaultDialerPackage == packageName
    }

    // Dynamic checks on app resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        checkPermissions()
    }

    // Launcher for standard runtime permissions
    val basicPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        checkPermissions()
    }

    // Launcher for local file import
    val localImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val fileName = uri.path?.lowercase() ?: ""
                    val isZip = fileName.endsWith(".zip") || context.contentResolver.getType(uri) == "application/zip"
                    if (isZip) {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            viewModel.importGlobalManualBackupZip(
                                inputStream = inputStream,
                                onSuccess = { _ ->
                                    Toast.makeText(context, "Profil erfolgreich geladen. Bitte schließe die Einrichtung ab.", Toast.LENGTH_SHORT).show()
                                    currentStep = SetupStep.BASIC_PERMISSIONS
                                },
                                onError = { error ->
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val reader = BufferedReader(InputStreamReader(inputStream))
                            val jsonContent = reader.readText()
                            viewModel.importGlobalManualBackup(
                                json = jsonContent,
                                onSuccess = { _ ->
                                    Toast.makeText(context, "Profil erfolgreich geladen. Bitte schließe die Einrichtung ab.", Toast.LENGTH_SHORT).show()
                                    currentStep = SetupStep.BASIC_PERMISSIONS
                                },
                                onError = { error ->
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Fehler beim Import: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingMedium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GhostTalk Setup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (currentStep != SetupStep.COMPLETED) {
                    TextButton(
                        onClick = onSetupFinished,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.outline)
                    ) {
                        Text(stringResource(R.string.setup_skip_all))
                    }
                }
            }
        },
        bottomBar = {
            if (currentStep != SetupStep.COMPLETED) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    LinearProgressIndicator(
                        progress = { (currentStep.index + 1).toFloat() / SetupStep.entries.size.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimensions.paddingLarge),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (currentStep != SetupStep.WELCOME) {
                            OutlinedButton(
                                onClick = {
                                    val steps = SetupStep.entries
                                    currentStep = steps[currentStep.index - 1]
                                },
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.setup_btn_back))
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = {
                                val steps = SetupStep.entries
                                if (currentStep.index < steps.size - 1) {
                                    currentStep = steps[currentStep.index + 1]
                                } else {
                                    onSetupFinished()
                                }
                            },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(stringResource(R.string.setup_btn_next))
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "setup_step_transition"
        ) { step ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = dimensions.paddingLarge)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (step) {
                    SetupStep.WELCOME -> WelcomeStepContent(
                        viewModel = viewModel,
                        onRestoreSuccess = { currentStep = SetupStep.BASIC_PERMISSIONS },
                        onLocalImportClick = { localImportLauncher.launch("*/*") }
                    )
                    SetupStep.BASIC_PERMISSIONS -> BasicPermissionsStepContent(
                        cameraGranted = cameraGranted,
                        locationGranted = locationGranted,
                        microphoneGranted = microphoneGranted,
                        contactsGranted = contactsGranted,
                        smsGranted = smsGranted,
                        calendarGranted = calendarGranted,
                        onRequestPermissions = {
                            val permissions = arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.READ_CALL_LOG,
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.READ_PHONE_STATE,
                                Manifest.permission.SEND_SMS,
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            basicPermissionsLauncher.launch(permissions)
                        }
                    )
                    SetupStep.OVERLAY -> OverlayStepContent(
                        isGranted = overlayGranted,
                        onRequest = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                "package:$packageName".toUri()
                            )
                            context.startActivity(intent)
                        }
                    )
                    SetupStep.NOTIFICATIONS -> NotificationListenerStepContent(
                        isGranted = notificationListenerGranted,
                        onRequest = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                    SetupStep.DIALER -> DialerStepContent(
                        isGranted = isDefaultDialer,
                        onRequest = {
                            val activity = context.findActivity()
                            if (activity != null) {
                                onRequestDefaultDialer(activity)
                            }
                        }
                    )
                    SetupStep.DEVICE_SETTINGS -> DeviceSettingsStepContent(
                        viewModel = viewModel
                    )
                    SetupStep.COMPLETED -> CompletedStepContent(
                        onFinish = onSetupFinished
                    )
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}
