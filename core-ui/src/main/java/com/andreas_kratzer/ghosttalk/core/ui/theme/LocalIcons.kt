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

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Local definitions for Icons that are normally only in material-icons-extended.
 * This allows us to remove the large dependency.
 */
object GhostTalkIcons {

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

    val SettingsAccessibility: ImageVector
        get() = materialIcon(name = "Filled.SettingsAccessibility") {
            materialPath {
                moveTo(20.5f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(7.0f, 1.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, 0.9f, 2.0f, 2.0f)
                reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
                reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                close()
                moveTo(11.0f, 7.0f)
                verticalLineTo(6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineTo(5.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(1.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(12.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(-5.0f)
                horizontalLineToRelative(1.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(1.0f)
                close()
                moveTo(24.0f, 10.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(9.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(1.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(5.0f)
                horizontalLineToRelative(1.48f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineTo(21.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineToRelative(1.0f)
                verticalLineTo(10.0f)
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
                curveToRelative(-0.23f, -0.16f, -0.28f, -0.47f, -0.12f, -0.7f)
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
}
