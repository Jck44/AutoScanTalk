package com.andreas_kratzer.ghosttalk.core.di

import com.andreas_kratzer.ghosttalk.core.actions.ControlDeviceSettings
import com.andreas_kratzer.ghosttalk.core.actions.ControlDeviceTtsProxy
import com.andreas_kratzer.ghosttalk.core.actions.ScannerController
import com.andreas_kratzer.ghosttalk.core.data.AppStateRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationServiceEntryPoint {
    fun settings(): ControlDeviceSettings
    fun ttsProxy(): ControlDeviceTtsProxy
    fun scannerController(): ScannerController
    fun appStateRepository(): AppStateRepository
}
