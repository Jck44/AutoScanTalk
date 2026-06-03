package com.andreas_kratzer.ghosttalk.ui.pages.actions

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val icon: Bitmap?
)

private fun Drawable.toBitmapOrNull(): Bitmap? {
    try {
        val bitmap = createBitmap(
            intrinsicWidth.coerceAtLeast(1),
            intrinsicHeight.coerceAtLeast(1)
        )
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    } catch (_: Exception) {
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationAppPicker(
    selectedPackage: String?,
    selectedLabel: String?,
    onAppSelected: (String, String) -> Unit
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    var expanded by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val appsList = resolveInfos.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                val icon = try {
                    resolveInfo.loadIcon(pm)?.toBitmapOrNull()
                } catch (_: Exception) {
                    null
                }
                if (packageName.isNotEmpty()) InstalledAppInfo(packageName, label, icon) else null
            }
            val sortedApps = appsList.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
            withContext(Dispatchers.Main) {
                installedApps = sortedApps
            }
        }
    }

    val selectedPackages = remember(selectedPackage) {
        selectedPackage?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    val preferredAppsStr = stringResource(R.string.device_control_notifications_preferred_apps)
    val appsSelectedStr = pluralStringResource(
        R.plurals.device_control_notifications_apps_selected,
        selectedPackages.size,
        selectedPackages.size
    )

    val displayLabels = remember(selectedPackages, installedApps, selectedLabel) {
        if (selectedPackages.isEmpty() || installedApps.isEmpty()) {
            emptyList()
        } else {
            val selectedLabels = selectedPackages.mapNotNull { pkg ->
                installedApps.find { it.packageName == pkg }?.label
            }.filter { it.isNotEmpty() && !it.contains(".") }
            
            if (selectedLabels.size == selectedPackages.size) {
                selectedLabels
            } else {
                val splitLabels = selectedLabel?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && !it.contains(".") } ?: emptyList()
                if (splitLabels.size == selectedPackages.size) {
                    splitLabels
                } else {
                    selectedPackages.map { pkg ->
                        installedApps.find { it.packageName == pkg }?.label ?: pkg
                    }
                }
            }
        }
    }

    val firstThree = remember(displayLabels) {
        displayLabels.take(3).joinToString(", ")
    }
    val remaining = (displayLabels.size - 3).coerceAtLeast(0)

    val appsMoreFormatStr = pluralStringResource(
        R.plurals.device_control_notifications_apps_more_format,
        remaining,
        firstThree,
        remaining
    )

    val currentLabel = when {
        selectedPackages.isEmpty() -> preferredAppsStr
        installedApps.isEmpty() -> appsSelectedStr
        displayLabels.size <= 3 -> displayLabels.joinToString(", ")
        else -> appsMoreFormatStr
    }

    val singleSelectedApp = remember(selectedPackages, installedApps) {
        if (selectedPackages.size == 1) {
            installedApps.find { it.packageName == selectedPackages.first() }
        } else null
    }

    val onToggleApp: (String, String) -> Unit = { pkg, _ ->
        val updated = selectedPackages.toMutableSet()
        if (updated.contains(pkg)) {
            updated.remove(pkg)
        } else {
            updated.add(pkg)
        }
        if (updated.isEmpty()) {
            onAppSelected("", "")
        } else {
            val newPhone = updated.joinToString(",")
            val newName = updated.map { p ->
                installedApps.find { it.packageName == p }?.label ?: p
            }.joinToString(", ")
            onAppSelected(newName, newPhone)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        Text(stringResource(R.string.device_control_notifications_read_from_app_label), style = MaterialTheme.typography.labelMedium)
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = currentLabel,
                onValueChange = { },
                leadingIcon = {
                    if (singleSelectedApp?.icon != null) {
                        Image(
                            bitmap = singleSelectedApp.icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = GhostTalkIcons.Notifications,
                            contentDescription = null
                        )
                    }
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // Option for "Bevorzugte Apps (Einstellungen)"
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.device_control_notifications_preferred_apps)) },
                    leadingIcon = {
                        Icon(
                            imageVector = GhostTalkIcons.Notifications,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        Checkbox(
                            checked = selectedPackages.isEmpty(),
                            onCheckedChange = {
                                if (selectedPackages.isNotEmpty()) {
                                    onAppSelected("", "")
                                }
                            }
                        )
                    },
                    onClick = {
                        onAppSelected("", "")
                    }
                )
                
                if (installedApps.isNotEmpty()) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                val allPkgs = installedApps.map { it.packageName }.toSet()
                                val newPhone = allPkgs.joinToString(",")
                                val newName = allPkgs.map { p ->
                                    installedApps.find { it.packageName == p }?.label ?: p
                                }.joinToString(", ")
                                onAppSelected(newName, newPhone)
                            }
                        ) {
                            Text(stringResource(R.string.settings_notifications_select_all))
                        }
                        TextButton(
                            onClick = {
                                onAppSelected("", "")
                            }
                        ) {
                            Text(stringResource(R.string.settings_notifications_deselect_all))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(bottom = dimensions.paddingSmall))
                }
                
                installedApps.forEach { app ->
                    DropdownMenuItem(
                        text = { Text(app.label) },
                        leadingIcon = {
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = GhostTalkIcons.Notifications,
                                    contentDescription = null
                                )
                            }
                        },
                        trailingIcon = {
                            Checkbox(
                                checked = selectedPackages.contains(app.packageName),
                                onCheckedChange = { onToggleApp(app.packageName, app.label) }
                            )
                        },
                        onClick = {
                            onToggleApp(app.packageName, app.label)
                        }
                    )
                }
            }
        }
    }
}
