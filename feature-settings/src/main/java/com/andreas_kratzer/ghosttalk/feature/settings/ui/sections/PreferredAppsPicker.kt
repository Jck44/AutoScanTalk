package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val icon: android.graphics.Bitmap?
)

fun android.graphics.drawable.Drawable.toBitmapOrNull(): android.graphics.Bitmap? {
    try {
        val bitmap = createBitmap(
            intrinsicWidth.coerceAtLeast(1),
            intrinsicHeight.coerceAtLeast(1)
        )
        val canvas = android.graphics.Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    } catch (_: Exception) {
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("QueryPermissionsNeeded", "LocalContextGetResourceValueCall")
@Composable
fun PreferredAppsPicker(
    monitoredApps: Set<String>,
    onToggleApp: (String, Boolean) -> Unit,
    onToggleAll: (Set<String>) -> Unit
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

    val currentLabel = remember(monitoredApps, installedApps, context) {
        if (monitoredApps.isEmpty()) {
            context.getString(R.string.settings_notifications_no_preferred_apps)
        } else if (installedApps.isEmpty()) {
            context.applicationContext.resources.getQuantityString(
                R.plurals.settings_notifications_apps_selected,
                monitoredApps.size,
                monitoredApps.size
            )
        } else {
            val selectedLabels = monitoredApps.mapNotNull { pkg ->
                installedApps.find { it.packageName == pkg }?.label
            }.filter { it.isNotEmpty() && !it.contains(".") }
            
            val displayLabels = if (selectedLabels.size == monitoredApps.size) {
                selectedLabels
            } else {
                monitoredApps.map { pkg ->
                    installedApps.find { it.packageName == pkg }?.label ?: pkg
                }
            }

            if (displayLabels.size <= 3) {
                displayLabels.joinToString(", ")
            } else {
                val firstThree = displayLabels.take(3).joinToString(", ")
                val remaining = displayLabels.size - 3
                context.applicationContext.resources.getQuantityString(
                    R.plurals.settings_notifications_apps_more_format,
                    remaining,
                    firstThree,
                    remaining
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        Text(stringResource(R.string.settings_notifications_manage_preferred_apps), style = MaterialTheme.typography.titleSmall)
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = currentLabel,
                onValueChange = { },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null
                    )
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (installedApps.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_notifications_loading_apps)) },
                        onClick = {}
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { onToggleAll(installedApps.map { it.packageName }.toSet()) }
                        ) {
                            Text(stringResource(R.string.settings_notifications_select_all))
                        }
                        TextButton(
                            onClick = { onToggleAll(emptySet()) }
                        ) {
                            Text(stringResource(R.string.settings_notifications_deselect_all))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(bottom = dimensions.paddingSmall))

                    installedApps.forEach { app ->
                        val isChecked = monitoredApps.contains(app.packageName)
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
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null
                                    )
                                }
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        onToggleApp(app.packageName, checked)
                                    }
                                )
                            },
                            onClick = {
                                onToggleApp(app.packageName, !isChecked)
                            }
                        )
                    }
                }
            }
        }
    }
}
