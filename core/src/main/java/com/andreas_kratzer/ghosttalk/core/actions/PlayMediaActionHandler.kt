package com.andreas_kratzer.ghosttalk.core.actions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.MediaProvider
import com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction
import com.andreas_kratzer.ghosttalk.core.services.NotificationReaderService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayMediaActionHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val actionLogger: ActionLogger,
    private val settings: SpeechSettings,
    private val ttsProxyLazy: dagger.Lazy<ActionTtsProxy>
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is PlayMediaButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val mediaAction = action as PlayMediaButtonAction
        val provider = mediaAction.provider
        val contentUri = mediaAction.contentUri
        
        if (!android.provider.Settings.canDrawOverlays(context)) {
            val tts = ttsProxyLazy.get()
            val msg = "GhostTalk kann Medien nicht starten, da die Berechtigung zum Einblenden über anderen Apps fehlt."
            actionLogger.log("Overlay-Berechtigung fehlt. Abbruch vor dem Starten der App.", action, buttonConfig.label)
            if (tts.isReady) {
                val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
                    settings.cuesAudioDeviceAddress
                } else {
                    settings.ttsAudioDeviceAddress
                }
                tts.speakRouted(msg, targetDeviceAddress) {
                    onFinish(executionId)
                }
            } else {
                onFinish(executionId)
            }
            return
        }
        
        Log.d("PlayMediaActionHandler", "Handling media play for $provider, URI: $contentUri")
        
        val intent = when (provider) {
            MediaProvider.SPOTIFY -> {
                val baseUri = if (contentUri == "liked_songs") "spotify:collection:tracks" else contentUri
                val uriString = if (baseUri.startsWith("spotify:")) baseUri else "spotify:playlist:$baseUri"
                val playUri = when {
                    uriString.endsWith(":play") -> uriString
                    uriString == "spotify:collection:tracks" -> uriString // Liked Songs: no :play suffix
                    else -> "$uriString:play"
                }
                Intent(Intent.ACTION_VIEW, playUri.toUri()).apply {
                    `package` = "com.spotify.music"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            MediaProvider.YOUTUBE -> {
                val ytUri = when {
                    // Full playlist URL – pass through directly
                    contentUri.contains("list=") -> contentUri
                    // Full video URL with v= parameter
                    contentUri.contains("v=") -> {
                        val videoId = contentUri.substringAfter("v=").substringBefore("&")
                        "vnd.youtube:$videoId"
                    }
                    // Short share link
                    contentUri.contains("youtu.be/") -> {
                        val videoId = contentUri.substringAfter("youtu.be/").substringBefore("?")
                        "vnd.youtube:$videoId"
                    }
                    // Raw video ID (11 chars)
                    contentUri.length == 11 && !contentUri.startsWith("http") -> "vnd.youtube:$contentUri"
                    // Anything else (full URL etc.) – pass through
                    else -> contentUri
                }
                Intent(Intent.ACTION_VIEW, ytUri.toUri()).apply {
                    `package` = "com.google.android.youtube"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            MediaProvider.YOUTUBE_MUSIC -> {
                Intent(Intent.ACTION_VIEW, contentUri.toUri()).apply {
                    `package` = "com.google.android.apps.youtube.music"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            MediaProvider.AUDIBLE -> {
                val audibleUri = if (contentUri.startsWith("audible:") || contentUri.startsWith("http")) {
                    contentUri
                } else {
                    "audible://play?asin=$contentUri"
                }
                Intent(Intent.ACTION_VIEW, audibleUri.toUri()).apply {
                    `package` = "com.audible.application"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
        
        try {
            context.startActivity(intent)
            actionLogger.log("Medien gestartet (${mediaAction.contentName.ifBlank { provider.displayName }})", action, buttonConfig.label)
        } catch (e: Exception) {
            Log.e("PlayMediaActionHandler", "Failed to launch media app for $provider", e)
            actionLogger.log("Fehler beim Starten von ${provider.displayName}", action, buttonConfig.label)
            
            // Fallback: Just launch the app package directly
            try {
                val fallbackPackage = when (provider) {
                    MediaProvider.SPOTIFY -> "com.spotify.music"
                    MediaProvider.YOUTUBE -> "com.google.android.youtube"
                    MediaProvider.YOUTUBE_MUSIC -> "com.google.android.apps.youtube.music"
                    MediaProvider.AUDIBLE -> "com.audible.application"
                }
                val launchIntent = context.packageManager.getLaunchIntentForPackage(fallbackPackage)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }
            } catch (_: Exception) {}
        }

        if (mediaAction.forcePlayViaMediaSession) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                    val componentName = ComponentName(context, NotificationReaderService::class.java)
                    
                    val controllers = mediaSessionManager.getActiveSessions(componentName)
                    val targetPackage = when (provider) {
                        MediaProvider.SPOTIFY -> "com.spotify.music"
                        MediaProvider.YOUTUBE -> "com.google.android.youtube"
                        MediaProvider.YOUTUBE_MUSIC -> "com.google.android.apps.youtube.music"
                        MediaProvider.AUDIBLE -> "com.audible.application"
                    }
                    
                    val controller = controllers.find { it.packageName == targetPackage }
                    if (controller != null) {
                        controller.transportControls.play()
                        actionLogger.log("Wiedergabesteuerung über MediaSession gesendet an $targetPackage", action, buttonConfig.label)
                        
                        // Return to GoSTalk after successful play
                        if (mediaAction.returnToAppDelayMs > 0) {
                            val remainingDelay = (mediaAction.returnToAppDelayMs - 2000L).coerceAtLeast(0L)
                            returnToGoSTalk(remainingDelay, buttonConfig, mediaAction, executionId)
                        }
                        
                        onFinish(executionId)
                    } else {
                        Log.w("PlayMediaActionHandler", "No active media session found for package $targetPackage")
                        speakError(
                            text = "Medien-App ${provider.displayName} reagiert nicht. Bitte manuell starten.",
                            buttonConfig = buttonConfig,
                            action = action,
                            executionId = executionId,
                            onFinish = { execId ->
                                if (mediaAction.returnToAppDelayMs > 0) {
                                    returnToGoSTalk(0L, buttonConfig, mediaAction, execId)
                                }
                                onFinish(execId)
                            }
                        )
                    }
                } catch (e: SecurityException) {
                    Log.e("PlayMediaActionHandler", "SecurityException: Notification listener permission missing", e)
                    speakError(
                        text = "Benachrichtigungszugriff fehlt. Medien können nicht automatisch gestartet werden.",
                        buttonConfig = buttonConfig,
                        action = action,
                        executionId = executionId,
                        onFinish = { execId ->
                            if (mediaAction.returnToAppDelayMs > 0) {
                                returnToGoSTalk(0L, buttonConfig, mediaAction, execId)
                            }
                            onFinish(execId)
                        }
                    )
                } catch (e: Exception) {
                    Log.e("PlayMediaActionHandler", "Error querying active sessions", e)
                    if (mediaAction.returnToAppDelayMs > 0) {
                        returnToGoSTalk(0L, buttonConfig, mediaAction, executionId)
                    }
                    onFinish(executionId)
                }
            }, 2000L)
        } else {
            // Return to GoSTalk after delay if configured (no MediaSession control)
            if (mediaAction.returnToAppDelayMs > 0) {
                returnToGoSTalk(mediaAction.returnToAppDelayMs, buttonConfig, mediaAction, executionId)
            }
            onFinish(executionId)
        }
    }

    private fun returnToGoSTalk(
        delayMs: Long,
        buttonConfig: ButtonConfig,
        action: PlayMediaButtonAction,
        executionId: Int
    ) {
        if (!android.provider.Settings.canDrawOverlays(context)) {
            Log.w("PlayMediaActionHandler", "Overlay permission not granted. Cannot return to GoSTalk.")
            speakError(
                text = "GhostTalk kann nicht zurückkehren, da die Berechtigung zum Einblenden über anderen Apps fehlt.",
                buttonConfig = buttonConfig,
                action = action,
                executionId = executionId,
                onFinish = {}
            )
            return
        }

        if (delayMs <= 0) {
            performReturn()
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                performReturn()
            }, delayMs)
        }
    }

    private fun performReturn() {
        try {
            val returnIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (returnIntent != null) {
                returnIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                context.startActivity(returnIntent)
                Log.d("PlayMediaActionHandler", "Successfully returned to GoSTalk using direct startActivity")
            } else {
                val fallbackIntent = Intent().apply {
                    setClassName(context.packageName, "com.andreas_kratzer.ghosttalk.MainActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                context.startActivity(fallbackIntent)
                Log.d("PlayMediaActionHandler", "Successfully returned to GoSTalk using fallback startActivity")
            }
        } catch (e: Exception) {
            Log.e("PlayMediaActionHandler", "Failed to return to GoSTalk", e)
        }
    }

    private fun speakError(
        text: String,
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
            settings.cuesAudioDeviceAddress
        } else {
            settings.ttsAudioDeviceAddress
        }
        
        val tts = ttsProxyLazy.get()
        if (tts.isReady) {
            tts.speakRouted(
                text = text,
                deviceAddress = targetDeviceAddress,
                queueMode = 0,
                isForCues = buttonConfig.playActionAsAuditoryCue,
                onDone = { onFinish(executionId) }
            )
            actionLogger.log("TTS Fehlermeldung: \"$text\"", action, buttonConfig.label)
        } else {
            actionLogger.log("TTS Fehlermeldung (TTS nicht bereit): \"$text\"", action, buttonConfig.label)
            onFinish(executionId)
        }
    }
}
