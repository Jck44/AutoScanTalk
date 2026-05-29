package com.andreas_kratzer.ghosttalk.ui.setup

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR
import com.andreas_kratzer.ghosttalk.feature.settings.R as SettingsR

private enum class SetupStep(val index: Int) {
    WELCOME(0),
    BASIC_PERMISSIONS(1),
    OVERLAY(2),
    NOTIFICATIONS(3),
    DIALER(4),
    COMPLETED(5)
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun SetupScreen(
    onSetupFinished: () -> Unit,
    onRequestDefaultDialer: (Activity) -> Unit
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    val packageName = context.packageName

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
        contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
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
    ) { results ->
        checkPermissions()
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
                        progress = { (currentStep.index + 1).toFloat() / SetupStep.values().size.toFloat() },
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
                                    val steps = SetupStep.values()
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
                                val steps = SetupStep.values()
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
                    SetupStep.WELCOME -> WelcomeStepContent(dimensions)
                    SetupStep.BASIC_PERMISSIONS -> BasicPermissionsStepContent(
                        dimensions = dimensions,
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
                        dimensions = dimensions,
                        isGranted = overlayGranted,
                        onRequest = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            context.startActivity(intent)
                        }
                    )
                    SetupStep.NOTIFICATIONS -> NotificationListenerStepContent(
                        dimensions = dimensions,
                        isGranted = notificationListenerGranted,
                        onRequest = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                    SetupStep.DIALER -> DialerStepContent(
                        dimensions = dimensions,
                        isGranted = isDefaultDialer,
                        onRequest = {
                            val activity = context.findActivity()
                            if (activity != null) {
                                onRequestDefaultDialer(activity)
                            }
                        }
                    )
                    SetupStep.COMPLETED -> CompletedStepContent(
                        dimensions = dimensions,
                        onFinish = onSetupFinished
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStepContent(dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions) {
    val localDimensions = LocalDimensions.current
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
}

@Composable
private fun BasicPermissionsStepContent(
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions,
    cameraGranted: Boolean,
    locationGranted: Boolean,
    microphoneGranted: Boolean,
    contactsGranted: Boolean,
    smsGranted: Boolean,
    calendarGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    val localDimensions = LocalDimensions.current
    Text(
        text = stringResource(R.string.setup_basic_perms_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingSmall))
    Text(
        text = stringResource(R.string.setup_basic_perms_desc),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        PermissionStatusRow(
            label = stringResource(SettingsR.string.settings_permission_camera),
            isGranted = cameraGranted,
            description = stringResource(R.string.setup_permission_camera_desc)
        )
        PermissionStatusRow(
            label = stringResource(R.string.setup_permission_mic),
            isGranted = microphoneGranted,
            description = stringResource(R.string.setup_permission_mic_desc)
        )
        PermissionStatusRow(
            label = stringResource(R.string.setup_permission_contacts_phone),
            isGranted = contactsGranted,
            description = stringResource(R.string.setup_permission_contacts_phone_desc)
        )
        PermissionStatusRow(
            label = stringResource(R.string.setup_permission_sms),
            isGranted = smsGranted,
            description = stringResource(R.string.setup_permission_sms_desc)
        )
        PermissionStatusRow(
            label = stringResource(SettingsR.string.settings_permission_calendar),
            isGranted = calendarGranted,
            description = stringResource(R.string.setup_permission_calendar_desc)
        )
        PermissionStatusRow(
            label = stringResource(SettingsR.string.settings_permission_location),
            isGranted = locationGranted,
            description = stringResource(R.string.setup_permission_location_desc)
        )
    }

    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))

    Button(
        onClick = onRequestPermissions,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(stringResource(R.string.setup_btn_request_permissions))
    }
}

@Composable
private fun OverlayStepContent(
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    val localDimensions = LocalDimensions.current
    Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = null,
        tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.size(72.dp)
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingMedium))
    Text(
        text = stringResource(SettingsR.string.settings_permission_overlay),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingSmall))
    Text(
        text = stringResource(R.string.setup_permission_overlay_explanation),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))

    PermissionStatusCard(isGranted = isGranted)

    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))

    Button(
        onClick = onRequest,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        enabled = !isGranted
    ) {
        Text(if (isGranted) stringResource(R.string.setup_permission_granted) else stringResource(R.string.setup_btn_enable_overlay))
    }
}

@Composable
private fun NotificationListenerStepContent(
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    val localDimensions = LocalDimensions.current
    Icon(
        imageVector = GhostTalkIcons.Notifications,
        contentDescription = null,
        tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.size(72.dp)
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingMedium))
    Text(
        text = stringResource(SettingsR.string.settings_notifications_enable),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingSmall))
    Text(
        text = stringResource(R.string.setup_permission_notifications_explanation),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))

    PermissionStatusCard(isGranted = isGranted)

    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))

    Button(
        onClick = onRequest,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        enabled = !isGranted
    ) {
        Text(if (isGranted) stringResource(R.string.setup_permission_granted) else stringResource(R.string.setup_btn_enable_notifications))
    }
}

@Composable
private fun DialerStepContent(
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    val localDimensions = LocalDimensions.current
    Icon(
        imageVector = Icons.Default.Call,
        contentDescription = null,
        tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.size(72.dp)
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingMedium))
    Text(
        text = stringResource(SettingsR.string.settings_dialer_register_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingSmall))
    Text(
        text = stringResource(R.string.setup_permission_dialer_explanation),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))

    PermissionStatusCard(isGranted = isGranted)

    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))

    Button(
        onClick = onRequest,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        enabled = !isGranted
    ) {
        Text(if (isGranted) stringResource(SettingsR.string.settings_dialer_registered) else stringResource(R.string.setup_btn_register_dialer))
    }
}

@Composable
private fun CompletedStepContent(
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions,
    onFinish: () -> Unit
) {
    val localDimensions = LocalDimensions.current
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(96.dp)
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))
    Text(
        text = stringResource(R.string.setup_completed_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingMedium))
    Text(
        text = stringResource(R.string.setup_completed_desc),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = localDimensions.paddingMedium)
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingDoubleExtraLarge))

    Button(
        onClick = onFinish,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(stringResource(R.string.setup_btn_start))
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    isGranted: Boolean,
    description: String? = null
) {
    val localDimensions = LocalDimensions.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    var isExpanded by remember { mutableStateOf(false) }
    
    val chipBg = if (isGranted) {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveBgDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveBgLight
    } else {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveBgDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveBgLight
    }
    
    val chipContentColor = if (isGranted) {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveTextDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveTextLight
    } else {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveTextDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveTextLight
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .run {
                if (description != null) {
                    clickable { isExpanded = !isExpanded }
                } else this
            }
            .padding(horizontal = localDimensions.paddingLarge, vertical = localDimensions.paddingMedium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (description != null) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Show less" else "Show more",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
            Surface(
                color = chipBg,
                contentColor = chipContentColor,
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isGranted) stringResource(SettingsR.string.settings_permission_active) else stringResource(SettingsR.string.settings_permission_inactive),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        
        if (description != null) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(isGranted: Boolean) {
    val localDimensions = LocalDimensions.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    val cardBg = if (isGranted) {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveBgDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveBgLight
    } else {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveBgDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveBgLight
    }
    
    val contentColor = if (isGranted) {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveTextDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveTextLight
    } else {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveTextDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveTextLight
    }

    Surface(
        color = cardBg,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(localDimensions.paddingLarge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(localDimensions.paddingMedium))
            Text(
                text = if (isGranted) {
                    stringResource(R.string.setup_status_permission_granted)
                } else {
                    stringResource(R.string.setup_status_permission_missing)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
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
