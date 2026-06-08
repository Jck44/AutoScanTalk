/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * NOTE: The following icons are part of the Material Design icon set and are 
 * redistributed here as ImageVector definitions to optimize build performance.
 */

package com.andreas_kratzer.ghosttalk.core.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector
import com.andreas_kratzer.ghosttalk.core.model.ActionCategory

/**
 * Local definitions for Icons that are normally only in material-icons-extended.
 * This allows us to remove the large dependency.
 */
@Suppress("unused")
object GhostTalkIcons {

    /**
     * Central provider for action icons based on the ActionCategory.
     * Ensures consistency across the entire app (Buttons, Editor, Dialogs).
     */
    fun getIconForCategory(category: ActionCategory): ImageVector {
        return when (category) {
            ActionCategory.SPEAK_TEXT -> Icons.Default.PlayArrow
            ActionCategory.PLAY_MEDIA -> MusicNote
            ActionCategory.NAVIGATE_PAGE -> ArrowForward
            ActionCategory.NAVIGATE_BACK -> ArrowBack
            ActionCategory.NAVIGATE_TO_START_PAGE -> Icons.Default.Home
            ActionCategory.GEMINI,
            ActionCategory.GEMINI_SEARCH,
            ActionCategory.GEMINI_NANO,
            ActionCategory.GEMINI_VISION -> AutoAwesome
            ActionCategory.WEATHER -> PartlyCloudy
            ActionCategory.SMART_HOME -> Icons.Default.Home
            ActionCategory.CONTROL_DEVICE -> Icons.Default.Settings
            ActionCategory.FREQUENT_ACTION,
            ActionCategory.PREVIOUS_ACTION,
            ActionCategory.SMART_PREDICTION -> History
            ActionCategory.MARK_ACCIDENTAL -> Backspace
        }
    }


    val MusicNote: ImageVector
        get() = materialIcon(name = "Filled.MusicNote") {
            materialPath {
                moveTo(12.0f, 3.0f)
                verticalLineToRelative(10.55f)
                curveToRelative(-0.59f, -0.34f, -1.27f, -0.55f, -2.0f, -0.55f)
                curveToRelative(-2.21f, 0.0f, -4.0f, 1.79f, -4.0f, 4.0f)
                reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
                reflectiveCurveToRelative(4.0f, -1.79f, 4.0f, -4.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(3.0f)
                horizontalLineToRelative(-6.0f)
                close()
            }
        }

    val Book: ImageVector
        get() = materialIcon(name = "Filled.Book") {
            materialPath {
                moveTo(18.0f, 2.0f)
                horizontalLineTo(6.0f)
                curveTo(4.9f, 2.0f, 4.0f, 2.9f, 4.0f, 4.0f)
                verticalLineToRelative(16.0f)
                curveTo(4.0f, 21.1f, 4.9f, 22.0f, 6.0f, 22.0f)
                horizontalLineToRelative(12.0f)
                curveTo(19.1f, 22.0f, 20.0f, 21.1f, 20.0f, 20.0f)
                verticalLineTo(4.0f)
                curveTo(20.0f, 2.9f, 19.1f, 2.0f, 18.0f, 2.0f)
                close()
                moveTo(6.0f, 4.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(8.0f)
                lineTo(8.5f, 10.5f)
                lineTo(6.0f, 12.0f)
                verticalLineTo(4.0f)
                close()
            }
        }

    val Sort: ImageVector
        get() = materialIcon(name = "AutoMirrored.Filled.Sort", autoMirror = true) {
            materialPath {
                moveTo(3.0f, 18.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(3.0f, 6.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(18.0f)
                verticalLineTo(6.0f)
                horizontalLineTo(3.0f)
                close()
                moveTo(3.0f, 13.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(2.0f)
                close()
            }
        }

    val DragHandle: ImageVector
        get() = materialIcon(name = "Filled.DragHandle") {
            materialPath {
                moveTo(20.0f, 9.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(16.0f)
                verticalLineTo(9.0f)
                close()
                moveTo(20.0f, 13.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(16.0f)
                verticalLineTo(13.0f)
                close()
            }
        }

    val Description: ImageVector
        get() = materialIcon(name = "Filled.Description") {
            materialPath {
                moveTo(14.0f, 2.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(4.0f, 20.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 1.99f, 2.0f)
                horizontalLineTo(18.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(8.0f)
                lineToRelative(-6.0f, -6.0f)
                close()
                moveTo(16.0f, 18.0f)
                horizontalLineTo(8.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(16.0f, 14.0f)
                horizontalLineTo(8.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(13.0f, 9.0f)
                verticalLineTo(3.5f)
                lineTo(18.5f, 9.0f)
                horizontalLineTo(13.0f)
                close()
            }
        }

    val GridView: ImageVector
        get() = materialIcon(name = "Filled.GridView") {
            materialPath {
                moveTo(3.0f, 3.0f)
                verticalLineToRelative(8.0f)
                horizontalLineToRelative(8.0f)
                verticalLineTo(3.0f)
                horizontalLineTo(3.0f)
                close()
                moveTo(9.0f, 9.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(9.0f)
                close()
                moveTo(3.0f, 13.0f)
                verticalLineToRelative(8.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(-8.0f)
                horizontalLineTo(3.0f)
                close()
                moveTo(9.0f, 19.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(19.0f)
                close()
                moveTo(13.0f, 3.0f)
                verticalLineToRelative(8.0f)
                horizontalLineToRelative(8.0f)
                verticalLineTo(3.0f)
                horizontalLineTo(13.0f)
                close()
                moveTo(19.0f, 9.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(9.0f)
                close()
                moveTo(13.0f, 13.0f)
                verticalLineToRelative(8.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(-8.0f)
                horizontalLineTo(13.0f)
                close()
                moveTo(19.0f, 19.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(19.0f)
                close()
            }
        }

    val Science: ImageVector
        get() = materialIcon(name = "Filled.Science") {
            materialPath {
                moveTo(13.0f, 11.33f)
                lineTo(18.0f, 18.0f)
                horizontalLineTo(6.0f)
                lineToRelative(5.0f, -6.67f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(11.33f)
                close()
                moveTo(15.96f, 19.0f)
                lineTo(19.0f, 19.0f)
                curveToRelative(0.81f, 0.0f, 1.25f, -0.95f, 0.74f, -1.58f)
                lineTo(15.0f, 11.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(6.0f)
                lineToRelative(-4.74f, 6.42f)
                curveTo(3.75f, 18.05f, 4.19f, 19.0f, 5.0f, 19.0f)
                horizontalLineToRelative(10.96f)
                close()
                moveTo(7.32f, 17.0f)
                horizontalLineToRelative(9.36f)
                lineToRelative(-4.68f, -6.24f)
                lineTo(7.32f, 17.0f)
                close()
            }
        }

    val AutoAwesome: ImageVector
        get() = materialIcon(name = "Filled.AutoAwesome") {
            materialPath {
                moveTo(19.0f, 9.0f)
                lineToRelative(1.25f, -2.75f)
                lineTo(23.0f, 5.0f)
                lineToRelative(-2.75f, -1.25f)
                lineTo(19.0f, 1.0f)
                lineToRelative(-1.25f, 2.75f)
                lineTo(15.0f, 5.0f)
                lineToRelative(2.75f, 1.25f)
                lineTo(19.0f, 9.0f)
                close()
                moveTo(11.5f, 9.5f)
                lineTo(9.0f, 4.0f)
                lineTo(6.5f, 9.5f)
                lineTo(1.0f, 12.0f)
                lineToRelative(5.5f, 2.5f)
                lineTo(9.0f, 20.0f)
                lineToRelative(2.5f, -5.5f)
                lineTo(17.0f, 12.0f)
                lineTo(11.5f, 9.5f)
                close()
                moveTo(19.0f, 15.0f)
                lineToRelative(-1.25f, 2.75f)
                lineTo(15.0f, 19.0f)
                lineToRelative(2.75f, 1.25f)
                lineTo(19.0f, 23.0f)
                lineToRelative(1.25f, -2.75f)
                lineTo(23.0f, 19.0f)
                lineToRelative(-2.75f, -1.25f)
                lineTo(19.0f, 15.0f)
                close()
            }
        }

    val SwitchAccessShortcut: ImageVector
        get() = materialIcon(name = "Filled.SwitchAccessShortcut") {
            materialPath {
                moveTo(15f, 22f)
                quadTo(11.83f, 20.8f, 9.91f, 18.05f)
                reflectiveQuadTo(8f, 11.9f)
                quadTo(8f, 9.63f, 8.9f, 7.59f)
                reflectiveQuadTo(11.45f, 4f)
                horizontalLineTo(8f)
                verticalLineTo(2f)
                horizontalLineToRelative(7f)
                verticalLineTo(9f)
                horizontalLineTo(13f)
                verticalLineTo(5.3f)
                quadTo(11.58f, 6.57f, 10.79f, 8.29f)
                reflectiveQuadTo(10f, 11.9f)
                quadToRelative(0f, 2.55f, 1.35f, 4.69f)
                quadTo(12.7f, 18.73f, 15f, 19.83f)
                verticalLineTo(22f)
                close()
            }
        }

    val RecordVoiceOver: ImageVector
        get() = materialIcon(name = "Filled.RecordVoiceOver") {
            materialPath {
                moveTo(9.0f, 9.0f)
                curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
                close()
                moveTo(9.0f, 15.0f)
                curveToRelative(-2.67f, 0.0f, -8.0f, 1.34f, -8.0f, 4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -2.66f, -5.33f, -4.0f, -8.0f, -4.0f)
                close()
                moveTo(16.76f, 5.39f)
                lineToRelative(-1.47f, 1.35f)
                curveToRelative(1.14f, 1.24f, 1.14f, 3.21f, 0.0f, 4.45f)
                lineToRelative(1.47f, 1.35f)
                curveToRelative(1.88f, -2.01f, 1.88f, -5.15f, 0.0f, -7.15f)
                close()
                moveTo(20.07f, 2.0f)
                lineToRelative(-1.46f, 1.35f)
                curveToRelative(2.99f, 3.19f, 2.99f, 8.11f, 0.0f, 11.3f)
                lineToRelative(1.46f, 1.35f)
                curveToRelative(3.72f, -3.98f, 3.72f, -10.03f, 0.0f, -14.0f)
                close()
            }
        }


        
    val ArrowForward: ImageVector
        get() = materialIcon(name = "AutoMirrored.Filled.ArrowForward", autoMirror = true) {
            materialPath {
                moveTo(12.0f, 4.0f)
                lineToRelative(-1.41f, 1.41f)
                lineTo(15.17f, 11.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(11.17f)
                lineToRelative(-4.58f, 4.59f)
                lineTo(12.0f, 20.0f)
                lineToRelative(8.0f, -8.0f)
                close()
            }
        }

    val ArrowBack: ImageVector
        get() = materialIcon(name = "AutoMirrored.Filled.ArrowBack", autoMirror = true) {
            materialPath {
                moveTo(20.0f, 11.0f)
                horizontalLineTo(7.83f)
                lineToRelative(5.59f, -5.59f)
                lineTo(12.0f, 4.0f)
                lineToRelative(-8.0f, 8.0f)
                lineToRelative(8.0f, 8.0f)
                lineToRelative(1.41f, -1.41f)
                lineTo(7.83f, 13.0f)
                horizontalLineTo(20.0f)
                verticalLineToRelative(-2.0f)
                close()
            }
        }

    val ManageAccounts: ImageVector
        get() = materialIcon(name = "Filled.ManageAccounts") {
            materialPath {
                moveTo(12.0f, 12.0f)
                curveToRelative(2.21f, 0.0f, 4.0f, -1.79f, 4.0f, -4.0f)
                reflectiveCurveToRelative(-1.79f, -4.0f, -4.0f, -4.0f)
                reflectiveCurveToRelative(-4.0f, 1.79f, -4.0f, 4.0f)
                reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
                close()
                moveTo(12.0f, 14.0f)
                curveToRelative(-2.67f, 0.0f, -8.0f, 1.34f, -8.0f, 4.0f)
                verticalLineToRelative(1.0f)
                horizontalLineToRelative(9.29f)
                curveToRelative(-0.19f, -0.64f, -0.29f, -1.31f, -0.29f, -2.0f)
                curveToRelative(0.0f, -1.0f, 0.22f, -1.94f, 0.6f, -2.79f)
                curveTo(13.16f, 14.08f, 12.59f, 14.0f, 12.0f, 14.0f)
                close()
                moveTo(20.0f, 17.5f)
                curveToRelative(0.0f, -0.28f, -0.03f, -0.54f, -0.07f, -0.8f)
                lineToRelative(0.87f, -0.76f)
                lineToRelative(-0.75f, -1.3f)
                lineToRelative(-1.1f, 0.37f)
                curveToRelative(-0.28f, -0.22f, -0.6f, -0.39f, -0.95f, -0.5f)
                lineTo(17.75f, 14.0f)
                horizontalLineToRelative(-1.5f)
                lineToRelative(-0.25f, 1.51f)
                curveToRelative(-0.35f, 0.11f, -0.67f, 0.28f, -0.95f, 0.5f)
                lineToRelative(-1.1f, -0.37f)
                lineToRelative(-0.75f, 1.3f)
                lineToRelative(0.87f, 0.76f)
                curveToRelative(-0.04f, 0.26f, -0.07f, 0.53f, -0.07f, 0.8f)
                reflectiveCurveToRelative(0.03f, 0.54f, 0.07f, 0.8f)
                lineToRelative(-0.87f, 0.76f)
                lineToRelative(0.75f, 1.3f)
                lineToRelative(1.1f, -0.37f)
                curveToRelative(0.28f, 0.22f, 0.6f, 0.39f, 0.95f, 0.5f)
                lineTo(16.25f, 21.0f)
                horizontalLineToRelative(1.5f)
                lineToRelative(0.25f, -1.51f)
                curveToRelative(0.35f, -0.11f, 0.67f, -0.28f, 0.95f, -0.5f)
                lineToRelative(1.1f, 0.37f)
                lineToRelative(0.75f, -1.3f)
                lineToRelative(-0.87f, -0.76f)
                curveToRelative(0.04f, -0.26f, 0.07f, -0.52f, 0.07f, -0.8f)
                close()
                moveTo(17.0f, 19.0f)
                curveToRelative(-0.83f, 0.0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveToRelative(0.67f, -1.5f, 1.5f, -1.5f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveToRelative(-0.67f, 1.5f, -1.5f, 1.5f)
                close()
            }
        }

    val Cloud: ImageVector
        get() = materialIcon(name = "Filled.Cloud") {
            materialPath {
                moveTo(19.35f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
                curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
                curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                horizontalLineToRelative(13.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                close()
            }
        }

    val CloudUpload: ImageVector
        get() = materialIcon(name = "Filled.CloudUpload") {
            materialPath {
                moveTo(19.35f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
                curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
                curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                horizontalLineToRelative(13.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                close()
                moveTo(14.0f, 13.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineTo(7.0f)
                lineToRelative(5.0f, -5.0f)
                lineToRelative(5.0f, 5.0f)
                horizontalLineToRelative(-3.0f)
                close()
            }
        }

    val CloudDownload: ImageVector
        get() = materialIcon(name = "Filled.CloudDownload") {
            materialPath {
                moveTo(19.35f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
                curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
                curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                horizontalLineToRelative(13.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                close()
                moveTo(17.0f, 13.0f)
                lineToRelative(-5.0f, 5.0f)
                lineToRelative(-5.0f, -5.0f)
                horizontalLineToRelative(3.0f)
                verticalLineTo(9.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(3.0f)
                close()
            }
        }

    val Notifications: ImageVector
        get() = materialIcon(name = "Filled.Notifications") {
            materialPath {
                moveTo(12.0f, 22.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                horizontalLineToRelative(-4.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                close()
                moveTo(18.0f, 16.0f)
                verticalLineToRelative(-5.0f)
                curveToRelative(0.0f, -3.07f, -1.63f, -5.64f, -4.5f, -6.32f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
                verticalLineToRelative(0.68f)
                curveTo(7.64f, 5.36f, 6.0f, 7.92f, 6.0f, 11.0f)
                verticalLineToRelative(5.0f)
                lineToRelative(-2.0f, 2.0f)
                verticalLineToRelative(1.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(-1.0f)
                lineToRelative(-2.0f, -2.0f)
                close()
            }
        }

    val VolumeUp: ImageVector
        get() = materialIcon(name = "Filled.VolumeUp") {
            materialPath {
                moveTo(3.0f, 9.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(5.0f, 5.0f)
                verticalLineTo(4.0f)
                lineTo(7.0f, 9.0f)
                horizontalLineTo(3.0f)
                close()
                moveTo(16.5f, 12.0f)
                curveToRelative(0.0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(8.05f)
                curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
                close()
                moveTo(14.0f, 3.23f)
                verticalLineToRelative(2.06f)
                curveToRelative(2.89f, 0.86f, 5.0f, 3.54f, 5.0f, 6.71f)
                reflectiveCurveToRelative(-2.11f, 5.85f, -5.0f, 6.71f)
                verticalLineToRelative(2.06f)
                curveToRelative(4.01f, -0.91f, 7.0f, -4.49f, 7.0f, -8.77f)
                reflectiveCurveToRelative(-2.99f, -7.86f, -7.0f, -8.77f)
                close()
            }
        }

    val VolumeDown: ImageVector
        get() = materialIcon(name = "Filled.VolumeDown") {
            materialPath {
                moveTo(18.5f, 12.0f)
                curveToRelative(0.0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(8.05f)
                curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
                close()
                moveTo(5.0f, 9.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(5.0f, 5.0f)
                verticalLineTo(4.0f)
                lineTo(9.0f, 9.0f)
                horizontalLineTo(5.0f)
                close()
            }
        }

    val VolumeOff: ImageVector
        get() = materialIcon(name = "Filled.VolumeOff") {
            materialPath {
                moveTo(16.5f, 12.0f)
                curveToRelative(0.0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(2.24f)
                lineToRelative(2.48f, 2.48f)
                curveToRelative(0.01f, -0.23f, 0.02f, -0.46f, 0.02f, -0.69f)
                close()
                moveTo(14.0f, 3.23f)
                verticalLineToRelative(2.06f)
                curveToRelative(2.89f, 0.86f, 5.0f, 3.54f, 5.0f, 6.71f)
                curveToRelative(0.0f, 2.2f, -0.9f, 4.18f, -2.36f, 5.61f)
                lineToRelative(1.44f, 1.44f)
                curveTo(20.25f, 17.0f, 21.0f, 14.6f, 21.0f, 12.0f)
                curveToRelative(0.0f, -4.28f, -2.99f, -7.86f, -7.0f, -8.77f)
                close()
                moveTo(12.0f, 4.0f)
                lineTo(9.91f, 6.09f)
                lineTo(12.0f, 8.18f)
                verticalLineTo(4.0f)
                close()
                moveTo(4.27f, 3.0f)
                lineTo(3.0f, 4.27f)
                lineTo(7.73f, 9.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(5.0f, 5.0f)
                verticalLineToRelative(-6.73f)
                lineToRelative(4.25f, 4.25f)
                curveToRelative(-0.67f, 0.52f, -1.42f, 0.93f, -2.25f, 1.18f)
                verticalLineToRelative(2.06f)
                curveToRelative(1.38f, -0.31f, 2.63f, -0.95f, 3.69f, -1.81f)
                lineTo(19.73f, 21.0f)
                lineTo(21.0f, 19.73f)
                lineTo(4.27f, 3.0f)
                close()
            }
        }

    val Vibration: ImageVector
        get() = materialIcon(name = "Filled.Vibration") {
            materialPath {
                moveTo(0.0f, 15.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(9.0f)
                horizontalLineTo(0.0f)
                verticalLineTo(15.0f)
                close()
                moveTo(3.0f, 17.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(7.0f)
                horizontalLineTo(3.0f)
                verticalLineTo(17.0f)
                close()
                moveTo(22.0f, 9.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(9.0f)
                horizontalLineTo(22.0f)
                close()
                moveTo(19.0f, 7.0f)
                verticalLineToRelative(10.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(7.0f)
                horizontalLineTo(19.0f)
                close()
                moveTo(16.5f, 3.0f)
                horizontalLineToRelative(-9.0f)
                curveTo(6.67f, 3.0f, 6.0f, 3.67f, 6.0f, 4.5f)
                verticalLineToRelative(15.0f)
                curveTo(6.0f, 20.33f, 6.67f, 21.0f, 7.5f, 21.0f)
                horizontalLineToRelative(9.0f)
                curveToRelative(0.83f, 0.0f, 1.5f, -0.67f, 1.5f, -1.5f)
                verticalLineTo(4.5f)
                curveTo(18.0f, 3.67f, 17.33f, 3.0f, 16.5f, 3.0f)
                close()
                moveTo(16.0f, 19.0f)
                horizontalLineTo(8.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(8.0f)
                verticalLineTo(19.0f)
                close()
            }
        }

    val BatteryFull: ImageVector
        get() = materialIcon(name = "Filled.BatteryFull") {
            materialPath {
                moveTo(17.0f, 5.0f)
                horizontalLineToRelative(-3.0f)
                verticalLineTo(3.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(7.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(15.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(6.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                close()
            }
        }

    val Folder: ImageVector
        get() = materialIcon(name = "Filled.Folder") {
            materialPath {
                moveTo(10.0f, 4.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(2.0f, 18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(8.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-8.0f)
                lineToRelative(-2.0f, -2.0f)
                close()
            }
        }

    val DateRange: ImageVector
        get() = materialIcon(name = "Filled.DateRange") {
            materialPath {
                moveTo(9.0f, 11.0f)
                horizontalLineTo(7.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(13.0f, 11.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(17.0f, 11.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(19.0f, 4.0f)
                horizontalLineToRelative(-1.0f)
                verticalLineTo(2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(8.0f)
                verticalLineTo(2.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(5.0f)
                curveToRelative(-1.11f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(3.0f, 20.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(19.0f, 20.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(9.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(11.0f)
                close()
            }
        }

    val AccessTime: ImageVector
        get() = materialIcon(name = "Filled.AccessTime") {
            materialPath {
                moveTo(11.99f, 2.0f)
                curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                close()
                moveTo(12.5f, 7.0f)
                horizontalLineTo(11.0f)
                verticalLineToRelative(6.0f)
                lineToRelative(5.25f, 3.15f)
                lineToRelative(0.75f, -1.23f)
                lineToRelative(-4.75f, -2.82f)
                close()
            }
        }

    val Edit: ImageVector
        get() = materialIcon(name = "Filled.Edit") {
            materialPath {
                moveTo(3.0f, 17.25f)
                verticalLineTo(21.0f)
                horizontalLineToRelative(3.75f)
                lineTo(17.81f, 9.94f)
                lineToRelative(-3.75f, -3.75f)
                lineTo(3.0f, 17.25f)
                close()
                moveTo(20.71f, 7.04f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineToRelative(-2.34f, -2.34f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineToRelative(-1.83f, 1.83f)
                lineToRelative(3.75f, 3.75f)
                lineToRelative(1.83f, -1.83f)
                close()
            }
        }

    val Security: ImageVector
        get() = materialIcon(name = "Filled.Security") {
            materialPath {
                moveTo(12.0f, 1.0f)
                lineTo(3.0f, 5.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 5.55f, 3.84f, 10.74f, 9.0f, 12.0f)
                curveToRelative(5.16f, -1.26f, 9.0f, -6.45f, 9.0f, -12.0f)
                verticalLineTo(5.0f)
                lineToRelative(-9.0f, -4.0f)
                close()
                moveTo(12.0f, 11.99f)
                horizontalLineToRelative(7.0f)
                curveToRelative(-0.53f, 4.12f, -3.28f, 7.79f, -7.0f, 8.94f)
                verticalLineTo(12.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(6.3f)
                lineToRelative(7.0f, -3.11f)
                verticalLineToRelative(8.8f)
                close()
            }
        }

    val Backspace: ImageVector
        get() = materialIcon(name = "AutoMirrored.Filled.Backspace", autoMirror = true) {
            materialPath {
                moveTo(22.0f, 3.0f)
                horizontalLineTo(7.0f)
                curveToRelative(-0.69f, 0.0f, -1.23f, 0.35f, -1.59f, 0.88f)
                lineTo(0.0f, 12.0f)
                lineToRelative(5.41f, 8.11f)
                curveTo(5.77f, 20.64f, 6.31f, 21.0f, 7.0f, 21.0f)
                horizontalLineToRelative(15.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(19.0f, 15.59f)
                lineTo(17.59f, 17.0f)
                lineTo(14.0f, 13.41f)
                lineTo(10.41f, 17.0f)
                lineTo(9.0f, 15.59f)
                lineTo(12.59f, 12.0f)
                lineTo(9.0f, 8.41f)
                lineTo(10.41f, 7.0f)
                lineTo(14.0f, 10.59f)
                lineTo(17.59f, 7.0f)
                lineTo(19.0f, 8.41f)
                lineTo(15.41f, 12.0f)
                lineTo(19.0f, 15.59f)
                close()
            }
        }

    val StarBorder: ImageVector
        get() = materialIcon(name = "Filled.StarBorder") {
            materialPath {
                moveTo(22.0f, 9.24f)
                lineToRelative(-7.19f, -0.62f)
                lineTo(12.0f, 2.0f)
                lineTo(9.19f, 8.63f)
                lineTo(2.0f, 9.24f)
                lineToRelative(5.46f, 4.73f)
                lineTo(5.82f, 21.0f)
                lineTo(12.0f, 17.27f)
                lineTo(18.18f, 21.0f)
                lineToRelative(-1.63f, -7.03f)
                lineTo(22.0f, 9.24f)
                close()
                moveTo(12.0f, 15.4f)
                lineToRelative(-3.76f, 2.27f)
                lineToRelative(1.0f, -4.28f)
                lineToRelative(-3.32f, -2.88f)
                lineToRelative(4.38f, -0.38f)
                lineTo(12.0f, 6.1f)
                lineToRelative(1.71f, 4.04f)
                lineToRelative(4.38f, 0.38f)
                lineToRelative(-3.32f, 2.88f)
                lineToRelative(1.0f, 4.28f)
                lineTo(12.0f, 15.4f)
                close()
            }
        }

    val History: ImageVector
        get() = materialIcon(name = "Filled.History") {
            materialPath {
                moveTo(13.0f, 3.0f)
                curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
                horizontalLineTo(1.0f)
                lineToRelative(3.89f, 3.89f)
                lineToRelative(0.07f, 0.14f)
                lineTo(9.0f, 12.0f)
                horizontalLineTo(6.0f)
                curveToRelative(0.0f, -3.87f, 3.13f, -7.0f, 7.0f, -7.0f)
                reflectiveCurveToRelative(7.0f, 3.13f, 7.0f, 7.0f)
                reflectiveCurveToRelative(-3.13f, 7.0f, -7.0f, 7.0f)
                curveToRelative(-1.93f, 0.0f, -3.68f, -0.79f, -4.94f, -2.06f)
                lineToRelative(-1.42f, 1.42f)
                curveTo(8.27f, 19.99f, 10.51f, 21.0f, 13.0f, 21.0f)
                curveToRelative(4.97f, 0.0f, 9.0f, -4.03f, 9.0f, -9.0f)
                reflectiveCurveToRelative(-4.03f, -9.0f, -9.0f, -9.0f)
                close()
                moveTo(12.0f, 8.0f)
                verticalLineToRelative(5.0f)
                lineToRelative(4.28f, 2.54f)
                lineToRelative(0.72f, -1.21f)
                lineToRelative(-3.5f, -2.08f)
                verticalLineTo(8.0f)
                horizontalLineTo(12.0f)
                close()
            }
        }

    val BarChart: ImageVector
        get() = materialIcon(name = "Filled.BarChart") {
            materialPath {
                moveTo(5.0f, 9.2f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(10.8f)
                horizontalLineTo(5.0f)
                close()
                moveTo(10.5f, 5.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(15.0f)
                horizontalLineToRelative(-3.0f)
                close()
                moveTo(16.0f, 13.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(7.0f)
                horizontalLineToRelative(-3.0f)
                close()
            }
        }

    val Copy: ImageVector
        get() = materialIcon(name = "Filled.ContentCopy") {
            materialPath {
                moveTo(16.0f, 1.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(3.0f)
                horizontalLineToRelative(12.0f)
                verticalLineTo(1.0f)
                close()
                moveTo(19.0f, 5.0f)
                horizontalLineTo(8.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(11.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(7.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(19.0f, 21.0f)
                horizontalLineTo(8.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(11.0f)
                verticalLineToRelative(14.0f)
                close()
            }
        }

    val Fingerprint: ImageVector
        get() = materialIcon(name = "Filled.Fingerprint") {
            materialPath {
                moveTo(17.81f, 4.47f)
                curveToRelative(-0.08f, 0.0f, -0.16f, -0.02f, -0.23f, -0.06f)
                curveTo(15.66f, 3.42f, 14.0f, 3.0f, 12.01f, 3.0f)
                curveToRelative(-1.98f, 0.0f, -3.86f, 0.47f, -5.57f, 1.41f)
                curveToRelative(-0.24f, 0.13f, -0.54f, 0.04f, -0.68f, -0.2f)
                curveToRelative(-0.13f, -0.24f, -0.04f, -0.55f, 0.2f, -0.68f)
                curveTo(7.82f, 2.52f, 9.86f, 2.0f, 12.01f, 2.0f)
                curveToRelative(2.13f, 0.0f, 3.99f, 0.47f, 6.03f, 1.52f)
                curveToRelative(0.25f, 0.13f, 0.34f, 0.43f, 0.21f, 0.67f)
                curveToRelative(-0.09f, 0.18f, -0.26f, 0.28f, -0.44f, 0.28f)
                close()
                moveTo(3.5f, 9.72f)
                curveToRelative(-0.1f, 0.0f, -0.2f, -0.03f, -0.29f, -0.09f)
                curveTo(-0.23f, -0.16f, -0.28f, -0.47f, -0.12f, -0.7f)
                curveToRelative(0.99f, -1.4f, 2.25f, -2.5f, 3.75f, -3.27f)
                curveTo(9.98f, 4.04f, 14.0f, 4.03f, 17.15f, 5.65f)
                curveToRelative(1.5f, 0.77f, 2.76f, 1.86f, 3.75f, 3.25f)
                curveToRelative(0.16f, 0.22f, 0.11f, 0.54f, -0.12f, 0.7f)
                curveToRelative(-0.23f, 0.16f, -0.54f, 0.11f, -0.7f, -0.12f)
                curveToRelative(-0.9f, -1.26f, -2.04f, -2.25f, -3.39f, -2.94f)
                curveToRelative(-2.87f, -1.47f, -6.54f, -1.47f, -9.4f, 0.01f)
                curveToRelative(-1.36f, 0.7f, -2.5f, 1.7f, -3.4f, 2.96f)
                curveToRelative(-0.08f, 0.14f, -0.23f, 0.21f, -0.39f, 0.21f)
                close()
                moveTo(9.75f, 21.79f)
                curveToRelative(-0.13f, 0.0f, -0.26f, -0.05f, -0.35f, -0.15f)
                curveToRelative(-0.87f, -0.87f, -1.43f, -1.43f, -2.01f, -2.64f)
                curveToRelative(-0.69f, -1.23f, -1.05f, -2.73f, -1.05f, -4.34f)
                curveToRelative(0.0f, -2.97f, 2.54f, -5.39f, 5.66f, -5.39f)
                reflectiveCurveToRelative(5.66f, 2.42f, 5.66f, 5.39f)
                curveToRelative(0.0f, 0.28f, -0.22f, 0.5f, -0.5f, 0.5f)
                reflectiveCurveToRelative(-0.5f, -0.22f, -0.5f, -0.5f)
                curveToRelative(0.0f, -2.42f, -2.09f, -4.39f, -4.66f, -4.39f)
                curveToRelative(-2.57f, 0.0f, -4.66f, 1.97f, -4.66f, 4.39f)
                curveToRelative(0.0f, 1.44f, 0.32f, 2.77f, 0.93f, 3.85f)
                curveToRelative(0.64f, 1.15f, 1.08f, 1.64f, 1.85f, 2.42f)
                curveToRelative(0.19f, 0.2f, 0.19f, 0.51f, 0.0f, 0.71f)
                curveToRelative(-0.11f, 0.1f, -0.24f, 0.15f, -0.37f, 0.15f)
                close()
                moveTo(16.92f, 19.94f)
                curveToRelative(-1.19f, 0.0f, -2.24f, -0.3f, -3.1f, -0.89f)
                curveToRelative(-1.49f, -1.01f, -2.38f, -2.65f, -2.38f, -4.39f)
                curveToRelative(0.0f, -0.28f, 0.22f, -0.5f, 0.5f, -0.5f)
                reflectiveCurveToRelative(0.5f, 0.22f, 0.5f, 0.5f)
                curveToRelative(0.0f, 1.41f, 0.72f, 2.74f, 1.94f, 3.56f)
                curveToRelative(0.71f, 0.48f, 1.54f, 0.71f, 2.54f, 0.71f)
                curveToRelative(0.24f, 0.0f, 0.64f, -0.03f, 1.04f, -0.1f)
                curveToRelative(0.27f, -0.05f, 0.53f, 0.13f, 0.58f, 0.41f)
                curveToRelative(0.05f, 0.27f, -0.13f, 0.53f, -0.41f, 0.58f)
                curveToRelative(-0.57f, 0.11f, -1.07f, 0.12f, -1.21f, 0.12f)
                close()
                moveTo(14.91f, 22.0f)
                curveToRelative(-0.04f, 0.0f, -0.09f, -0.01f, -0.13f, -0.02f)
                curveToRelative(-1.59f, -0.44f, -2.63f, -1.03f, -3.72f, -2.1f)
                curveToRelative(-1.4f, -1.39f, -2.17f, -3.24f, -2.17f, -5.22f)
                curveToRelative(0.0f, -1.62f, 1.38f, -2.94f, 3.08f, -2.94f)
                curveToRelative(1.7f, 0.0f, 3.08f, 1.32f, 3.08f, 2.94f)
                curveToRelative(0.0f, 1.07f, 0.93f, 1.94f, 2.08f, 1.94f)
                reflectiveCurveToRelative(2.08f, -0.87f, 2.08f, -1.94f)
                curveToRelative(0.0f, -3.77f, -3.25f, -6.83f, -7.25f, -6.83f)
                curveToRelative(-2.84f, 0.0f, -5.44f, 1.58f, -6.61f, 4.03f)
                curveToRelative(-0.39f, 0.81f, -0.59f, 1.76f, -0.59f, 2.8f)
                curveToRelative(0.0f, 0.78f, 0.07f, 2.01f, 0.67f, 3.61f)
                curveToRelative(0.1f, 0.26f, -0.03f, 0.55f, -0.29f, 0.64f)
                curveToRelative(-0.26f, 0.1f, -0.55f, -0.04f, -0.64f, -0.29f)
                curveToRelative(-0.49f, -1.31f, -0.73f, -2.61f, -0.73f, -3.96f)
                curveToRelative(0.0f, -1.2f, 0.23f, -2.29f, 0.68f, -3.24f)
                curveToRelative(1.33f, -2.79f, 4.28f, -4.6f, 7.51f, -4.6f)
                curveToRelative(4.55f, 0.0f, 8.25f, 3.51f, 8.25f, 7.83f)
                curveToRelative(0.0f, 1.62f, -1.38f, 2.94f, -3.08f, 2.94f)
                reflectiveCurveToRelative(-3.08f, -1.32f, -3.08f, -2.94f)
                curveToRelative(0.0f, -1.07f, -0.93f, -1.94f, -2.08f, -1.94f)
                reflectiveCurveToRelative(-2.08f, 0.87f, -2.08f, 1.94f)
                curveToRelative(0.0f, 1.71f, 0.66f, 3.31f, 1.87f, 4.51f)
                curveToRelative(0.95f, 0.94f, 1.86f, 1.46f, 3.27f, 1.85f)
                curveToRelative(0.27f, 0.07f, 0.42f, 0.35f, 0.35f, 0.61f)
                curveToRelative(-0.05f, 0.23f, -0.26f, 0.38f, -0.47f, 0.38f)
                close()
            }
        }

    val MoreVert: ImageVector
        get() = materialIcon(name = "Filled.MoreVert") {
            materialPath {
                moveTo(12.0f, 8.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                close()
                moveTo(12.0f, 10.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(12.0f, 16.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                close()
            }
        }

    val KeyboardArrowDown: ImageVector
        get() = materialIcon(name = "Filled.KeyboardArrowDown") {
            materialPath {
                moveTo(7.41f, 8.59f)
                lineTo(12.0f, 13.17f)
                lineToRelative(4.59f, -4.58f)
                lineTo(18.0f, 10.0f)
                lineToRelative(-6.0f, 6.0f)
                lineToRelative(-6.0f, -6.0f)
                close()
            }
        }

    val KeyboardArrowUp: ImageVector
        get() = materialIcon(name = "Filled.KeyboardArrowUp") {
            materialPath {
                moveTo(7.41f, 15.41f)
                lineTo(12.0f, 10.83f)
                lineToRelative(4.59f, 4.58f)
                lineTo(18.0f, 14.0f)
                lineToRelative(-6.0f, -6.0f)
                lineToRelative(-6.0f, 6.0f)
                close()
            }
        }

    val Stop: ImageVector
        get() = materialIcon(name = "Filled.Stop") {
            materialPath {
                moveTo(6.0f, 6.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(12.0f)
                horizontalLineTo(6.0f)
                close()
            }
        }

    val Pause: ImageVector
        get() = materialIcon(name = "Filled.Pause") {
            materialPath {
                moveTo(6.0f, 19.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(5.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(14.0f)
                close()
                moveTo(14.0f, 5.0f)
                verticalLineToRelative(14.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(-4.0f)
                close()
            }
        }

    val SkipNext: ImageVector
        get() = materialIcon(name = "Filled.SkipNext") {
            materialPath {
                moveTo(6.0f, 18.0f)
                lineToRelative(8.5f, -6.0f)
                lineTo(6.0f, 6.0f)
                verticalLineToRelative(12.0f)
                close()
                moveTo(16.0f, 6.0f)
                verticalLineToRelative(12.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(-2.0f)
                close()
            }
        }

    val SkipPrevious: ImageVector
        get() = materialIcon(name = "Filled.SkipPrevious") {
            materialPath {
                moveTo(6.0f, 6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(12.0f)
                horizontalLineTo(6.0f)
                close()
                moveTo(9.5f, 12.0f)
                lineToRelative(8.5f, 6.0f)
                verticalLineTo(6.0f)
                close()
            }
        }

    val PlayPause: ImageVector
        get() = materialIcon(name = "Filled.PlayPause") {
            materialPath {
                moveTo(4.0f, 6.0f)
                lineTo(10.0f, 12.0f)
                lineTo(4.0f, 18.0f)
                close()
                moveTo(14.0f, 6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(12.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(18.0f, 6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(12.0f)
                horizontalLineToRelative(-2.0f)
                close()
            }
        }

    val Phone: ImageVector
        get() = materialIcon(name = "Filled.Phone") {
            materialPath {
                moveTo(20.01f, 15.38f)
                curveToRelative(-1.23f, 0.0f, -2.42f, -0.2f, -3.53f, -0.57f)
                curveToRelative(-0.35f, -0.11f, -0.74f, -0.03f, -1.01f, 0.24f)
                lineToRelative(-2.2f, 2.2f)
                curveToRelative(-2.83f, -1.44f, -5.15f, -3.75f, -6.59f, -6.59f)
                lineToRelative(2.2f, -2.21f)
                curveToRelative(0.28f, -0.26f, 0.36f, -0.65f, 0.25f, -1.0f)
                curveToRelative(-0.37f, -1.11f, -0.57f, -2.3f, -0.57f, -3.53f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                curveToRelative(0.0f, 9.39f, 7.61f, 17.0f, 17.0f, 17.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-3.58f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                close()
            }
        }

    val Message: ImageVector
        get() = materialIcon(name = "Filled.Message") {
            materialPath {
                moveTo(20.0f, 2.0f)
                horizontalLineTo(4.0f)
                curveTo(2.9f, 2.0f, 2.01f, 2.9f, 2.01f, 4.0f)
                lineTo(2.0f, 22.0f)
                lineToRelative(4.0f, -4.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(4.0f)
                curveTo(22.0f, 2.9f, 21.1f, 2.0f, 20.0f, 2.0f)
                close()
                moveTo(6.0f, 9.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(6.0f)
                verticalLineTo(9.0f)
                close()
                moveTo(6.0f, 13.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(6.0f)
                verticalLineTo(13.0f)
                close()
            }
        }

    val Sunny: ImageVector
        get() = materialIcon(name = "Filled.WbSunny") {
            materialPath {
                // Top-left ray
                moveTo(6.76f, 4.84f)
                lineToRelative(-1.8f, -1.79f)
                lineToRelative(-1.41f, 1.41f)
                lineToRelative(1.79f, 1.79f)
                lineToRelative(1.42f, -1.41f)
                close()
                // Left ray
                moveTo(4.0f, 10.5f)
                horizontalLineTo(1.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(-2.0f)
                close()
                // Top ray
                moveTo(13.0f, 0.55f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(3.5f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(0.55f)
                close()
                // Top-right ray
                moveTo(20.45f, 4.46f)
                lineToRelative(-1.41f, -1.41f)
                lineToRelative(-1.79f, 1.79f)
                lineToRelative(1.41f, 1.41f)
                lineToRelative(1.79f, -1.79f)
                close()
                // Bottom-right ray
                moveTo(17.24f, 18.16f)
                lineToRelative(1.79f, 1.8f)
                lineToRelative(1.41f, -1.41f)
                lineToRelative(-1.8f, -1.79f)
                lineToRelative(-1.4f, 1.4f)
                close()
                // Right ray
                moveTo(20.0f, 10.5f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-3.0f)
                close()
                // Outer circle of the sun
                moveTo(12.0f, 5.5f)
                curveToRelative(-3.31f, 0.0f, -6.0f, 2.69f, -6.0f, 6.0f)
                reflectiveCurveToRelative(2.69f, 6.0f, 6.0f, 6.0f)
                reflectiveCurveToRelative(6.0f, -2.69f, 6.0f, -6.0f)
                reflectiveCurveToRelative(-2.69f, -6.0f, -6.0f, -6.0f)
                close()
                // Bottom ray
                moveTo(12.0f, 23.45f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(20.5f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.95f)
                close()
                // Bottom-left ray
                moveTo(7.16f, 19.56f)
                lineToRelative(-1.41f, 1.41f)
                lineToRelative(1.79f, 1.8f)
                lineToRelative(1.41f, -1.41f)
                lineToRelative(-1.79f, -1.8f)
                close()
            }
        }

    val PartlyCloudy: ImageVector
        get() = materialIcon(name = "Filled.PartlyCloudyDay") {
            materialPath {
                moveTo(11f, 5f)
                verticalLineTo(1f)
                horizontalLineToRelative(2f)
                verticalLineTo(5f)
                horizontalLineTo(11f)
                close()
                moveToRelative(6.65f, 2.75f)
                lineToRelative(-1.4f, -1.4f)
                lineTo(19.08f, 3.5f)
                lineToRelative(1.4f, 1.43f)
                lineTo(17.65f, 7.75f)
                close()
                moveTo(19f, 13f)
                verticalLineTo(11f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(2f)
                horizontalLineTo(19f)
                close()
                moveToRelative(0.07f, 7.48f)
                lineTo(16.25f, 17.65f)
                lineToRelative(1.4f, -1.4f)
                lineToRelative(2.85f, 2.8f)
                lineToRelative(-1.43f, 1.43f)
                close()
                moveTo(6.35f, 7.75f)
                lineTo(3.53f, 4.93f)
                lineTo(4.95f, 3.5f)
                lineToRelative(2.8f, 2.85f)
                lineToRelative(-1.4f, 1.4f)
                close()
                moveTo(6f, 19f)
                horizontalLineToRelative(4.5f)
                quadToRelative(0.63f, 0f, 1.06f, -0.44f)
                reflectiveQuadTo(12f, 17.5f)
                reflectiveQuadTo(11.58f, 16.44f)
                reflectiveQuadTo(10.53f, 16f)
                horizontalLineTo(9.25f)
                lineTo(8.75f, 14.8f)
                quadTo(8.4f, 13.98f, 7.65f, 13.49f)
                reflectiveQuadTo(6f, 13f)
                quadTo(4.75f, 13f, 3.88f, 13.88f)
                reflectiveQuadTo(3f, 16f)
                reflectiveQuadToRelative(0.88f, 2.13f)
                reflectiveQuadTo(6f, 19f)
                close()
                moveToRelative(0f, 2f)
                quadTo(3.93f, 21f, 2.46f, 19.54f)
                reflectiveQuadTo(1f, 16f)
                reflectiveQuadTo(2.46f, 12.46f)
                reflectiveQuadTo(6f, 11f)
                quadToRelative(1.5f, 0f, 2.74f, 0.81f)
                reflectiveQuadTo(10.58f, 14f)
                quadToRelative(1.45f, 0f, 2.44f, 1.07f)
                reflectiveQuadTo(14f, 17.65f)
                quadToRelative(-0.05f, 1.43f, -1.06f, 2.39f)
                reflectiveQuadTo(10.5f, 21f)
                horizontalLineTo(6f)
                close()
                moveToRelative(8f, -3.35f)
                quadToRelative(-0.13f, -0.5f, -0.25f, -0.97f)
                reflectiveQuadTo(13.5f, 15.7f)
                quadToRelative(1.13f, -0.47f, 1.81f, -1.47f)
                reflectiveQuadTo(16f, 12f)
                quadTo(16f, 10.35f, 14.83f, 9.17f)
                reflectiveQuadTo(12f, 8f)
                quadTo(10.5f, 8f, 9.38f, 8.98f)
                reflectiveQuadTo(8.05f, 11.45f)
                quadTo(7.55f, 11.33f, 7.03f, 11.23f)
                reflectiveQuadTo(6f, 11f)
                quadTo(6.35f, 8.8f, 8.06f, 7.4f)
                quadTo(9.78f, 6f, 12f, 6f)
                quadToRelative(2.5f, 0f, 4.25f, 1.75f)
                reflectiveQuadTo(18f, 12f)
                quadToRelative(0f, 1.92f, -1.1f, 3.46f)
                reflectiveQuadTo(14f, 17.65f)
                close()
                moveTo(12.03f, 12f)
                close()
            }
        }

    val Bluetooth: ImageVector
        get() = materialIcon(name = "Filled.Bluetooth") {
            materialPath {
                moveTo(17.71f, 7.71f)
                lineTo(12.00f, 2.00f)
                horizontalLineToRelative(-1.00f)
                verticalLineToRelative(7.59f)
                lineTo(6.41f, 4.70f)
                lineTo(5.00f, 6.11f)
                lineTo(10.89f, 12.00f)
                lineTo(5.00f, 17.89f)
                lineTo(6.41f, 19.30f)
                lineTo(11.00f, 14.41f)
                verticalLineTo(22.00f)
                horizontalLineToRelative(1.00f)
                lineTo(17.71f, 16.29f)
                lineTo(13.41f, 12.00f)
                lineTo(17.71f, 7.71f)
                close()
                moveTo(13.00f, 5.83f)
                lineToRelative(1.88f, 1.88f)
                lineTo(13.00f, 9.59f)
                verticalLineTo(5.83f)
                close()
                moveTo(13.00f, 18.17f)
                verticalLineToRelative(-3.76f)
                lineToRelative(1.88f, 1.88f)
                lineTo(13.00f, 18.17f)
                close()
            }
        }

    val Visibility: ImageVector
        get() = materialIcon(name = "Filled.Visibility") {
            materialPath {
                moveTo(12.0f, 4.5f)
                curveTo(7.0f, 4.5f, 2.73f, 7.61f, 1.0f, 12.0f)
                curveTo(2.73f, 16.39f, 7.0f, 19.5f, 12.0f, 19.5f)
                reflectiveCurveTo(21.27f, 16.39f, 23.0f, 12.0f)
                curveTo(21.27f, 7.61f, 17.0f, 4.5f, 12.0f, 4.5f)
                close()
                moveTo(12.0f, 17.0f)
                curveTo(9.24f, 17.0f, 7.0f, 14.76f, 7.0f, 12.0f)
                reflectiveCurveTo(9.24f, 7.0f, 12.0f, 7.0f)
                reflectiveCurveTo(17.0f, 9.24f, 17.0f, 12.0f)
                reflectiveCurveTo(14.76f, 17.0f, 12.0f, 17.0f)
                close()
                moveTo(12.0f, 9.0f)
                curveTo(10.34f, 9.0f, 9.0f, 10.34f, 9.0f, 12.0f)
                reflectiveCurveTo(10.34f, 15.0f, 12.0f, 15.0f)
                reflectiveCurveTo(15.0f, 13.66f, 15.0f, 12.0f)
                reflectiveCurveTo(13.66f, 9.0f, 12.0f, 9.0f)
                close()
            }
        }

    val VisibilityOff: ImageVector
        get() = materialIcon(name = "Filled.VisibilityOff") {
            materialPath {
                moveTo(12.0f, 7.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, 2.24f, 5.0f, 5.0f)
                curveToRelative(0.0f, 0.65f, -0.13f, 1.26f, -0.36f, 1.82f)
                lineToRelative(2.92f, 2.92f)
                curveToRelative(1.51f, -1.39f, 2.7f, -3.18f, 3.44f, -5.24f)
                curveToRelative(-1.73f, -4.39f, -6.0f, -7.5f, -11.0f, -7.5f)
                curveToRelative(-1.4f, 0.0f, -2.74f, 0.25f, -3.98f, 0.7f)
                lineToRelative(2.16f, 2.16f)
                curveToRelative(0.56f, -0.07f, 1.17f, -0.16f, 1.82f, -0.16f)
                close()
                moveTo(2.0f, 4.27f)
                lineToRelative(2.28f, 2.28f)
                lineToRelative(0.46f, 0.46f)
                curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1.0f, 12.0f)
                curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 12.0f, 7.5f)
                curveToRelative(1.55f, 0.0f, 3.03f, -0.3f, 4.38f, -0.84f)
                lineToRelative(0.42f, 0.42f)
                lineTo(19.73f, 22.0f)
                lineTo(21.0f, 20.73f)
                lineTo(3.27f, 3.0f)
                lineTo(2.0f, 4.27f)
                close()
                moveTo(7.53f, 9.8f)
                lineToRelative(1.55f, 1.55f)
                curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
                curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                curveToRelative(0.22f, 0.0f, 0.44f, -0.03f, 0.65f, -0.08f)
                lineToRelative(1.55f, 1.55f)
                curveToRelative(-0.67f, 0.33f, -1.41f, 0.53f, -2.2f, 0.53f)
                curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                curveToRelative(0.0f, -0.79f, 0.2f, -1.53f, 0.53f, -2.2f)
                close()
                moveTo(11.84f, 9.02f)
                lineToRelative(1.88f, 1.88f)
                curveToRelative(-0.07f, -0.45f, -0.43f, -0.81f, -0.88f, -0.88f)
                close()
            }
        }

    val Tablet: ImageVector
        get() = materialIcon(name = "Filled.Tablet") {
            materialPath {
                moveTo(21.0f, 4.0f)
                horizontalLineTo(3.0f)
                curveTo(1.9f, 4.0f, 1.0f, 4.9f, 1.0f, 6.0f)
                verticalLineToRelative(12.0f)
                curveTo(1.0f, 19.1f, 1.9f, 20.0f, 3.0f, 20.0f)
                horizontalLineTo(21.0f)
                curveTo(22.1f, 20.0f, 23.0f, 19.1f, 23.0f, 18.0f)
                verticalLineTo(6.0f)
                curveTo(23.0f, 4.9f, 22.1f, 4.0f, 21.0f, 4.0f)
                close()
                moveTo(19.0f, 18.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(6.0f)
                horizontalLineTo(19.0f)
                verticalLineTo(18.0f)
                close()
            }
        }
}

